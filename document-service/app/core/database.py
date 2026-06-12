from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker, AsyncSession
from sqlalchemy.orm import DeclarativeBase
from app.core.config import settings

# Le moteur async (équivalent du DataSource Spring)
engine = create_async_engine(
    settings.database_url,
    echo=False,       # True pour voir le SQL généré (dev only)
    pool_size=10,
    max_overflow=20,
)

# Factory de sessions (équivalent du SessionFactory)
AsyncSessionLocal = async_sessionmaker(engine, expire_on_commit=False)

# Classe de base pour tous les modèles
class Base(DeclarativeBase):
    pass

# Dependency FastAPI — injecté avec Depends(get_db) dans chaque route
async def get_db() -> AsyncSession:
    async with AsyncSessionLocal() as session:
        try:
            yield session
            await session.commit()
        except Exception:
            await session.rollback()
            raise