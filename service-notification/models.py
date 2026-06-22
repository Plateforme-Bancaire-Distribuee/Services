from datetime import datetime
from typing import Literal

from pydantic import BaseModel, Field


class NotificationCreate(BaseModel):
    userId: int
    type: Literal["EMAIL", "SMS", "PUSH"]
    title: str
    message: str
    channel: str


class NotificationResponse(BaseModel):
    id: str
    userId: int
    type: str
    title: str
    message: str
    channel: str
    isRead: bool = Field(default=False)
    createdAt: datetime
