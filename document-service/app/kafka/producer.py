import json
import logging
from aiokafka import AIOKafkaProducer
from app.core.config import settings

logger = logging.getLogger(__name__)

# Instance globale du producer (initialisée au démarrage dans main.py)
_producer: AIOKafkaProducer | None = None

async def start_producer():
    """Appelé au démarrage de l'app (lifespan)."""
    global _producer
    _producer = AIOKafkaProducer(
        bootstrap_servers=settings.kafka_bootstrap_servers,
        value_serializer=lambda v: json.dumps(v).encode("utf-8"),
        key_serializer=lambda k: k.encode("utf-8") if k else None
    )
    await _producer.start()
    logger.info("Kafka producer démarré")

async def stop_producer():
    """Appelé à l'arrêt de l'app (lifespan)."""
    global _producer
    if _producer:
        await _producer.stop()
        logger.info("Kafka producer arrêté")

async def publish_kyc_processed(
    dossier_id: int,
    document_id: int,
    coherent: bool,
    commentaire: str,
    texte_ocr: str = ""
):
    """
    Publie sur kyc.document.processed.
    customer-service consomme ce topic via KycEventConsumer.java
    """
    if not _producer:
        logger.error("Producer Kafka non initialisé")
        return

    payload = {
        "dossier_id": dossier_id,
        "document_id": document_id,
        "coherent": coherent,
        "commentaire": commentaire,
        "texte_ocr": texte_ocr[:500]  # on limite pour ne pas surcharger Kafka
    }

    await _producer.send_and_wait(
        topic="kyc.document.processed",
        key=str(dossier_id),
        value=payload
    )
    logger.info(f"[KAFKA] kyc.document.processed publié — dossier={dossier_id}, coherent={coherent}")