import os
import sys

os.environ["PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK"] = "True"
os.environ.setdefault("OMP_NUM_THREADS", "1")
os.environ.setdefault("MKL_NUM_THREADS", "1")
os.environ.setdefault("OPENBLAS_NUM_THREADS", "1")
os.environ.setdefault("FLAGS_num_threads", "1")

_project_root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
if _project_root not in sys.path:
    sys.path.insert(0, _project_root)
_existing_pp = os.environ.get("PYTHONPATH", "")
if _project_root not in _existing_pp.split(os.pathsep):
    os.environ["PYTHONPATH"] = _project_root + (os.pathsep + _existing_pp if _existing_pp else "")

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


