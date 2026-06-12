from pydantic import BaseModel
from datetime import datetime, date
from typing import Optional

# ── Requêtes ──────────────────────────────────────────────

class OcrTriggerRequest(BaseModel):
    """Reçu depuis customer-service via Feign pour déclencher l'OCR."""
    client_nom: str
    client_prenom: str
    client_date_naissance: date

# ── Réponses ──────────────────────────────────────────────

class DocumentResponse(BaseModel):
    """Retourné après upload."""
    id: int
    dossier_id: int
    type_document: str
    url_stockage: str
    hash_sha256: str
    statut: str
    date_upload: datetime

    model_config = {"from_attributes": True}  # équivalent de @JsonProperty


class OcrResponse(BaseModel):
    """Retourné après traitement OCR."""
    document_id: int
    statut: str
    coherent: bool
    commentaire: str
    confiance: float
    nom_extrait: Optional[str] = None
    prenom_extrait: Optional[str] = None
    date_naissance_extraite: Optional[str] = None
    numero_document: Optional[str] = None


class DocumentDetailResponse(DocumentResponse):
    """Détail complet incluant les données OCR."""
    texte_ocr: Optional[str] = None
    nom_extrait: Optional[str] = None
    prenom_extrait: Optional[str] = None
    date_naissance_extraite: Optional[str] = None
    numero_document_extrait: Optional[str] = None
    confiance_ocr: Optional[float] = None
    est_coherent: Optional[bool] = None
    commentaire_coherence: Optional[str] = None
    date_traitement: Optional[datetime] = None