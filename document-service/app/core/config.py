from pydantic_settings import BaseSettings

class Settings(BaseSettings):
    # Base de données
    database_url: str = "postgresql+asyncpg://postgres:postgres@localhost:5433/document_db"

    # MinIO (stockage des fichiers)
    minio_endpoint: str = "localhost:9000"
    minio_access_key: str = "minioadmin"
    minio_secret_key: str = "minioadmin"
    minio_bucket: str = "kyc-documents"
    minio_secure: bool = False  # True en prod (HTTPS)

    # Kafka
    kafka_bootstrap_servers: str = "localhost:9092"

    # JWT — doit être le MÊME secret que dans customer-service
    jwt_secret: str = "4a8f2e1b9d3c7f5a6e2d8b4c1f9e3a7d2b5c8f4e1a6d9b3c7f2e5a8d1b4c9f3e"
    jwt_algorithm: str = "HS256"

    # Eureka (optionnel pour dev local)
    eureka_url: str = "http://localhost:8761/eureka"
    app_name: str = "document-service"
    app_port: int = 8082

    class Config:
        env_file = ".env"

# Instance unique importée partout
settings = Settings()