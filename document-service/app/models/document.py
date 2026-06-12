from sqlalchemy import Integer, String, Boolean, DateTime, Text, Float
from sqlalchemy.orm import Mapped, mapped_column
from datetime import datetime, timezone
from app.core.database import Base

class DocumentKYC(Base):
    __tablename__ = "documents_kyc"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)

    # Lien vers DossierKYC dans customer-service (ID externe, pas de FK réelle inter-services)
    dossier_id: Mapped[int] = mapped_column(Integer, nullable=False, index=True)

    # Lien vers le client (pour traçabilité)
    client_id: Mapped[int] = mapped_column(Integer, nullable=True)

    # CNI | PASSEPORT | BULLETIN_SALAIRE | JUSTIFICATIF_DOMICILE
    type_document: Mapped[str] = mapped_column(String(50), nullable=False)

    # URL MinIO (ex: kyc/1/uuid-fichier.jpg)
    url_stockage: Mapped[str] = mapped_column(String(500), nullable=False)

    # Hash SHA-256 pour vérifier l'intégrité du fichier
    hash_sha256: Mapped[str] = mapped_column(String(64), nullable=False)

    # UPLOADED | EN_COURS_OCR | VERIFIE | INCOHERENT | ERREUR
    statut: Mapped[str] = mapped_column(String(30), nullable=False, default="UPLOADED")

    # Résultats OCR bruts
    texte_ocr: Mapped[str | None] = mapped_column(Text, nullable=True)
    nom_extrait: Mapped[str | None] = mapped_column(String(100), nullable=True)
    prenom_extrait: Mapped[str | None] = mapped_column(String(100), nullable=True)
    date_naissance_extraite: Mapped[str | None] = mapped_column(String(20), nullable=True)
    numero_document_extrait: Mapped[str | None] = mapped_column(String(50), nullable=True)

    # Score de confiance OCR (0.0 → 1.0)
    confiance_ocr: Mapped[float | None] = mapped_column(Float, nullable=True)

    # Résultat de la cohérence
    est_coherent: Mapped[bool | None] = mapped_column(Boolean, nullable=True)
    commentaire_coherence: Mapped[str | None] = mapped_column(String(500), nullable=True)

    date_upload: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        default=lambda: datetime.now(timezone.utc)
    )
    date_traitement: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)