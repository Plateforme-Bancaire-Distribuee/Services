from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from jose import jwt, JWTError
from app.core.config import settings
import logging

logger = logging.getLogger(__name__)
bearer_scheme = HTTPBearer()

def get_current_user(
    credentials: HTTPAuthorizationCredentials = Depends(bearer_scheme)
) -> dict:
    token = credentials.credentials
    try:
        # Log temporaire pour déboguer
        logger.info(f"Token reçu : {token[:50]}...")
        logger.info(f"Secret utilisé : {settings.jwt_secret[:20]}...")
        logger.info(f"Algorithme attendu : {settings.jwt_algorithm}")

        header = jwt.get_unverified_header(token)
        logger.info(f"JWT header : {header}")

        payload = jwt.decode(
            token,
            settings.jwt_secret,
            algorithms=settings.jwt_algorithms
        )
        logger.info(f"Payload décodé : {payload}")
        return payload

    except JWTError as e:
        logger.error(f"Erreur JWT : {e}")
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail=f"Token invalide : {str(e)}"
        )