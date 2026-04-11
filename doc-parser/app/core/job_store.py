import json
import redis
from typing import Optional
from app.core.config import settings
from app.models.job import JobRecord, JobStatus

_redis_client: Optional[redis.Redis] = None


def get_redis() -> redis.Redis:
    global _redis_client
    if _redis_client is None:
        _redis_client = redis.from_url(settings.redis_url, decode_responses=True)
    return _redis_client


def _job_key(job_id: str) -> str:
    return f"job:{job_id}"


def set_job(job: JobRecord) -> None:
    r = get_redis()
    r.hset(_job_key(job.job_id), mapping={"data": job.model_dump_json()})
    r.expire(_job_key(job.job_id), settings.job_ttl_seconds)


def get_job(job_id: str) -> Optional[JobRecord]:
    r = get_redis()
    raw = r.hget(_job_key(job_id), "data")
    if raw is None:
        return None
    return JobRecord.model_validate_json(raw)


def update_job(job_id: str, **kwargs) -> Optional[JobRecord]:
    job = get_job(job_id)
    if job is None:
        return None
    updated = job.model_copy(update=kwargs)
    set_job(updated)
    return updated
