from celery import Celery
from app.core.config import settings

celery_app = Celery(
    "doc_parser",
    broker=settings.redis_url,
    backend=settings.redis_url,
    include=["app.tasks.parse_task"],
)

celery_app.conf.update(
    task_serializer="json",
    result_serializer="json",
    accept_content=["json"],
    timezone="UTC",
    enable_utc=True,
    task_routes={
        "app.tasks.parse_task.parse_document": {"queue": settings.celery_task_queue},
    },
    worker_prefetch_multiplier=1,
    task_acks_late=True,
)
