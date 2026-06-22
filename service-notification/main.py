import py_eureka_client.eureka_client as eureka_client
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from database import close_mongo_connection
from routes import router


app = FastAPI(
    title="service-notification",
    description="Banking platform notification microservice",
    version="1.0.0",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(router)


@app.on_event("startup")
async def startup_event() -> None:
    eureka_client.init(
        eureka_server="http://service-registry:8761/eureka",
        app_name="service-notification",
        instance_port=8085,
    )


@app.on_event("shutdown")
async def shutdown_event() -> None:
    await close_mongo_connection()


@app.get("/")
async def root() -> dict[str, str]:
    return {"service": "service-notification", "status": "UP"}
