import hashlib
import os
import tempfile
import uuid
from datetime import datetime, timezone

from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.security import get_current_user
from app.kafka.producer import publish_kyc_processed
from app.models.document import DocumentKYC
from app.schemas.document import DocumentDetailResponse, DocumentResponse, OcrTriggerRequest, OcrResponse
from app.services.coherence import coherence_service
from app.services.minio_service import minio_service
from app.services.ocr_service import ocr_service

router = APIRouter(prefix="/api/v1/documents", tags=["Documents KYC"])

# Types MIME acceptés
ALLOWED_TYPES = {"image/jpeg", "image/png", "image/webp", "application/pdf"}
MAX_FILE_SIZE = 10 * 1024 * 1024  # 10 MB


@router.get("/upload", include_in_schema=False)
async def upload_document_method_not_allowed():
    raise HTTPException(
        status_code=status.HTTP_405_METHOD_NOT_ALLOWED,
        detail="Use POST /api/v1/documents/upload with multipart/form-data to upload a document."
    )


@router.post("/upload", response_model=DocumentResponse, status_code=status.HTTP_201_CREATED)
async def upload_document(
    dossier_id: int = Form(...),
    type_document: str = Form(..., description="CNI | PASSEPORT | BULLETIN_SALAIRE | JUSTIFICATIF_DOMICILE"),
    file: UploadFile = File(...),
    db: AsyncSession = Depends(get_db),
    current_user: dict = Depends(get_current_user)
):
    """
    Reçoit un fichier, calcule son SHA-256, l'upload dans MinIO,
    et persiste les métadonnées en base.
    Appelé directement par le client (front-end).
    """
    # ── Validation ─────────────────────────────────────────────────
    if file.content_type not in ALLOWED_TYPES:
        raise HTTPException(
            status_code=status.HTTP_415_UNSUPPORTED_MEDIA_TYPE,
            detail=f"Format non supporté : {file.content_type}. Acceptés : {ALLOWED_TYPES}"
        )

    content = await file.read()

    if len(content) > MAX_FILE_SIZE:
        raise HTTPException(
            status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
            detail="Fichier trop volumineux (max 10 MB)"
        )

    # ── Hash SHA-256 (intégrité) ───────────────────────────────────
    sha256 = hashlib.sha256(content).hexdigest()

    # ── Upload MinIO ───────────────────────────────────────────────
    # Nom unique : kyc/{dossier_id}/{uuid}.{extension}
    extension = file.filename.rsplit(".", 1)[-1] if "." in file.filename else "bin"
    object_name = f"kyc/{dossier_id}/{uuid.uuid4()}.{extension}"
    url_stockage = minio_service.upload(object_name, content, file.content_type)

    # ── Persistance ────────────────────────────────────────────────
    doc = DocumentKYC(
        dossier_id=dossier_id,
        client_id=current_user.get("userId"),
        type_document=type_document.upper(),
        url_stockage=url_stockage,
        hash_sha256=sha256,
        statut="UPLOADED"
    )
    db.add(doc)
    await db.flush()   # obtenir l'ID sans committer (le commit est dans get_db)
    await db.refresh(doc)

    return DocumentResponse.model_validate(doc)


@router.post("/{document_id}/ocr", response_model=OcrResponse, response_model_by_alias=True)
async def trigger_ocr(
    document_id: int,
    request: OcrTriggerRequest,
    db: AsyncSession = Depends(get_db),
    # Cet endpoint est appelé par customer-service (Feign), pas directement par le client
    # On accepte CLIENT et AGENT (customer-service s'authentifie avec un token SERVICE)
    current_user: dict = Depends(get_current_user)
):
    """
    Déclenche l'OCR sur un document uploadé.
    Appelé par customer-service via Feign après soumission du dossier KYC.

    Flux :
    1. Télécharge le fichier depuis MinIO dans un fichier temporaire
    2. Lance EasyOCR
    3. Compare les résultats avec les données client (cohérence)
    4. Persiste les résultats
    5. Publie sur Kafka (kyc.document.processed)
    """
    # ── Récupérer le document ──────────────────────────────────────
    doc = await db.get(DocumentKYC, document_id)
    if not doc:
        raise HTTPException(status_code=404, detail=f"Document {document_id} introuvable")

    if doc.statut not in ("UPLOADED", "ERREUR"):
        raise HTTPException(
            status_code=400,
            detail=f"Document déjà traité (statut actuel : {doc.statut})"
        )

    # Marquer en cours
    doc.statut = "EN_COURS_OCR"
    await db.flush()

    # ── Téléchargement temporaire depuis MinIO ─────────────────────
    # On écrit dans un fichier temporaire car EasyOCR travaille sur des chemins fichier
    suffix = "." + doc.url_stockage.rsplit(".", 1)[-1]
    tmp_path = None
    try:
        with tempfile.NamedTemporaryFile(suffix=suffix, delete=False) as tmp:
            content = minio_service.download(doc.url_stockage)
            tmp.write(content)
            tmp_path = tmp.name

        # ── OCR ───────────────────────────────────────────────────
        ocr_result = ocr_service.extract_from_file(tmp_path)

        # ── Cohérence ─────────────────────────────────────────────
        coherent, commentaire = coherence_service.verifier(
            ocr_nom=ocr_result.nom_extrait,
            ocr_prenom=ocr_result.prenom_extrait,
            ocr_date=ocr_result.date_naissance_extraite,
            client_nom=request.client_nom,
            client_prenom=request.client_prenom,
            client_date=request.client_date_naissance
        )

        # ── Mise à jour en base ───────────────────────────────────
        doc.statut = "VERIFIE" if coherent else "INCOHERENT"
        doc.texte_ocr = ocr_result.texte_brut[:2000]
        doc.nom_extrait = ocr_result.nom_extrait
        doc.prenom_extrait = ocr_result.prenom_extrait
        doc.date_naissance_extraite = (
            ocr_result.date_naissance_extraite.isoformat()
            if ocr_result.date_naissance_extraite else None
        )
        doc.numero_document_extrait = ocr_result.numero_document
        doc.confiance_ocr = ocr_result.confiance
        doc.est_coherent = coherent
        doc.commentaire_coherence = commentaire
        doc.date_traitement = datetime.now(timezone.utc)
        await db.flush()

        # ── Publication Kafka ─────────────────────────────────────
        # customer-service reçoit ça dans KycEventConsumer.java
        await publish_kyc_processed(
            dossier_id=doc.dossier_id,
            document_id=document_id,
            coherent=coherent,
            commentaire=commentaire,
            texte_ocr=ocr_result.texte_brut
        )

        return OcrResponse(
            document_id=document_id,
            statut=doc.statut,
            coherent=coherent,
            commentaire=commentaire,
            confiance=ocr_result.confiance,
            nom_extrait=ocr_result.nom_extrait,
            prenom_extrait=ocr_result.prenom_extrait,
            date_naissance_extraite=(
                ocr_result.date_naissance_extraite.isoformat()
                if ocr_result.date_naissance_extraite else None
            ),
            numero_document=ocr_result.numero_document
        )

    except Exception as e:
        # En cas d'erreur OCR, on marque le document et on remonte l'exception
        doc.statut = "ERREUR"
        doc.commentaire_coherence = str(e)[:500]
        await db.flush()
        raise HTTPException(status_code=500, detail=f"Erreur OCR : {str(e)}")

    finally:
        # Toujours nettoyer le fichier temporaire
        if tmp_path and os.path.exists(tmp_path):
            os.unlink(tmp_path)


@router.get("/{document_id}", response_model=DocumentDetailResponse)
async def get_document(
    document_id: int,
    db: AsyncSession = Depends(get_db),
    current_user: dict = Depends(get_current_user)
):
    """Récupère les détails complets d'un document (résultats OCR inclus)."""
    doc = await db.get(DocumentKYC, document_id)
    if not doc:
        raise HTTPException(status_code=404, detail="Document introuvable")
    return DocumentDetailResponse.model_validate(doc)


@router.get("/dossier/{dossier_id}", response_model=list[DocumentResponse])
async def get_documents_by_dossier(
    dossier_id: int,
    db: AsyncSession = Depends(get_db),
    current_user: dict = Depends(get_current_user)
):
    """Liste tous les documents d'un dossier KYC."""
    result = await db.execute(
        select(DocumentKYC).where(DocumentKYC.dossier_id == dossier_id)
    )
    docs = result.scalars().all()
    return [DocumentResponse.model_validate(d) for d in docs]