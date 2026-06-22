from datetime import datetime
from typing import Any

from bson import ObjectId
from fastapi import APIRouter, HTTPException, status
from pymongo import ReturnDocument

from database import notifications_collection
from models import NotificationCreate, NotificationResponse

router = APIRouter()


def serialize_notification(notification: dict[str, Any]) -> NotificationResponse:
    return NotificationResponse(
        id=str(notification["_id"]),
        userId=notification["userId"],
        type=notification["type"],
        title=notification["title"],
        message=notification["message"],
        channel=notification["channel"],
        isRead=notification.get("isRead", False),
        createdAt=notification["createdAt"],
    )


def parse_object_id(notification_id: str) -> ObjectId:
    if not ObjectId.is_valid(notification_id):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Invalid notification id",
        )
    return ObjectId(notification_id)


@router.post(
    "/api/v1/notifications",
    response_model=NotificationResponse,
    status_code=status.HTTP_201_CREATED,
)
async def createNotification(notification: NotificationCreate) -> NotificationResponse:
    notification_document = notification.model_dump()
    notification_document["isRead"] = False
    notification_document["createdAt"] = datetime.utcnow()

    result = await notifications_collection.insert_one(notification_document)
    created_notification = await notifications_collection.find_one({"_id": result.inserted_id})
    if created_notification is None:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Notification could not be created",
        )

    print(
        "Notification sent "
        f"type={created_notification['type']} "
        f"channel={created_notification['channel']} "
        f"userId={created_notification['userId']} "
        f"title={created_notification['title']}"
    )
    return serialize_notification(created_notification)


@router.get("/api/v1/notifications/user/{userId}", response_model=list[NotificationResponse])
async def getNotificationsByUser(userId: int) -> list[NotificationResponse]:
    cursor = notifications_collection.find({"userId": userId}).sort("createdAt", -1)
    notifications = await cursor.to_list(length=None)
    return [serialize_notification(notification) for notification in notifications]


@router.get("/api/v1/notifications/health")
async def health() -> dict[str, str]:
    return {"status": "UP"}


@router.get("/api/v1/notifications/{id}", response_model=NotificationResponse)
async def getNotificationById(id: str) -> NotificationResponse:
    object_id = parse_object_id(id)
    notification = await notifications_collection.find_one({"_id": object_id})
    if notification is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Notification not found",
        )
    return serialize_notification(notification)


@router.put("/api/v1/notifications/{id}/read", response_model=NotificationResponse)
async def markAsRead(id: str) -> NotificationResponse:
    object_id = parse_object_id(id)
    updated_notification = await notifications_collection.find_one_and_update(
        {"_id": object_id},
        {"$set": {"isRead": True}},
        return_document=ReturnDocument.AFTER,
    )
    if updated_notification is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Notification not found",
        )
    return serialize_notification(updated_notification)


@router.delete("/api/v1/notifications/{id}", status_code=status.HTTP_204_NO_CONTENT)
async def deleteNotification(id: str) -> None:
    object_id = parse_object_id(id)
    result = await notifications_collection.delete_one({"_id": object_id})
    if result.deleted_count == 0:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Notification not found",
        )
