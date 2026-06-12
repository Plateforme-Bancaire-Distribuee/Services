import io
from minio import Minio
from minio.error import S3Error
from app.core.config import settings
import logging

logger = logging.getLogger(__name__)

class MinioService:
    def __init__(self):
        self.client = Minio(
            settings.minio_endpoint,
            access_key=settings.minio_access_key,
            secret_key=settings.minio_secret_key,
            secure=settings.minio_secure
        )
        self._ensure_bucket()

    def _ensure_bucket(self):
        """Crée le bucket s'il n'existe pas — comme @PostConstruct en Spring."""
        try:
            if not self.client.bucket_exists(settings.minio_bucket):
                self.client.make_bucket(settings.minio_bucket)
                logger.info(f"Bucket '{settings.minio_bucket}' créé")
        except S3Error as e:
            logger.error(f"Erreur MinIO bucket : {e}")

    def upload(self, object_name: str, data: bytes, content_type: str) -> str:
        """
        Upload un fichier et retourne l'URL d'accès interne.
        object_name ex : "kyc/1/uuid-cni.jpg"
        """
        self.client.put_object(
            bucket_name=settings.minio_bucket,
            object_name=object_name,
            data=io.BytesIO(data),
            length=len(data),
            content_type=content_type
        )
        # URL interne (accessible par les autres services)
        return f"minio://{settings.minio_bucket}/{object_name}"

    def download(self, object_name: str) -> bytes:
        """Télécharge un fichier depuis MinIO et retourne les bytes."""
        # Extraire le chemin depuis l'URL minio://bucket/path
        path = object_name.replace(f"minio://{settings.minio_bucket}/", "")
        response = self.client.get_object(settings.minio_bucket, path)
        try:
            return response.read()
        finally:
            response.close()
            response.release_conn()

    def get_presigned_url(self, object_name: str, expires_hours: int = 1) -> str:
        """Génère une URL temporaire de téléchargement (pour le front)."""
        from datetime import timedelta
        path = object_name.replace(f"minio://{settings.minio_bucket}/", "")
        return self.client.presigned_get_object(
            settings.minio_bucket,
            path,
            expires=timedelta(hours=expires_hours)
        )

# Singleton — instancié une fois au démarrage
minio_service = MinioService()