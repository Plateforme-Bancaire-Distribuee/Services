import os

from dotenv import load_dotenv
from motor.motor_asyncio import AsyncIOMotorClient

load_dotenv()

MONGO_URL = os.getenv("MONGO_URL", "mongodb://mongo-notification:27017")
DATABASE_NAME = os.getenv("DATABASE_NAME", "notification_db")

client = AsyncIOMotorClient(MONGO_URL)
database = client[DATABASE_NAME]
notifications_collection = database["notifications"]


async def close_mongo_connection() -> None:
    client.close()
