from contextlib import asynccontextmanager
import logging

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.core.config import settings
from app.core.database import engine, Base
from app.kafka.producer import start_producer, stop_producer
from app.routers import documents

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s : %(message)s"
)
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """
    Équivalent du @PostConstruct / @PreDestroy Spring.
    Tout ce qui est AVANT le yield = startup.
    Tout ce qui est APRÈS = shutdown.
    """
    logger.info("Démarrage du document-service...")

    # 1. Créer les tables PostgreSQL si elles n'existent pas
    #    (équivalent de spring.jpa.hibernate.ddl-auto=update)
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    logger.info("Tables PostgreSQL vérifiées/créées")

    # 2. Démarrer le producer Kafka
    await start_producer()

    yield  # ← l'app tourne ici

    # 3. Arrêt propre
    await stop_producer()
    await engine.dispose()
    logger.info("document-service arrêté proprement")


app = FastAPI(
    title="Document & OCR Service",
    description="Upload, stockage MinIO et vérification OCR des pièces KYC",
    version="1.0.0",
    lifespan=lifespan
)

# CORS (si le front appelle directement ce service)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # restreindre en prod
    allow_methods=["*"],
    allow_headers=["*"],
)

# Enregistrement des routes
app.include_router(documents.router)


@app.get("/actuator/health", tags=["Health"])
async def health():
    """Health check pour Kubernetes et Eureka."""
    return {"status": "UP", "service": settings.app_name}