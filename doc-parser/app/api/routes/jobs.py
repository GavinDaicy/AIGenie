import json
import logging
from datetime import datetime, timezone, timedelta
from typing import Optional

from fastapi import APIRouter, HTTPException, Query
from fastapi.responses import Response

from app.core.job_store import get_job
from app.core import storage_factory
from app.models.job import JobStatus
from app.api.schemas.response import (
    JobStatusResponse,
    ParseResultInline,
    ParseResultUrl,
    FileUrlResponse,
)

router = APIRouter()
logger = logging.getLogger(__name__)


def _require_job(job_id: str):
    job = get_job(job_id)
    if job is None:
        raise HTTPException(status_code=404, detail=f"Job not found: {job_id}")
    return job


def _require_done(job):
    if job.status == JobStatus.failed:
        raise HTTPException(
            status_code=410,
            detail={"error": "PARSE_FAILED", "message": job.error, "job_id": job.job_id},
        )
    if job.status != JobStatus.done:
        raise HTTPException(
            status_code=202,
            detail={"error": "NOT_READY", "message": f"Job is {job.status}", "job_id": job.job_id},
        )


@router.get(
    "/jobs/{job_id}",
    response_model=JobStatusResponse,
    summary="查询解析任务状态",
)
def get_job_status(job_id: str):
    job = _require_job(job_id)
    return JobStatusResponse(
        job_id=job.job_id,
        status=job.status,
        progress=job.progress,
        filename=job.filename,
        parser_used=job.parser_used,
        page_count=job.page_count,
        block_count=job.block_count,
        error=job.error,
        created_at=job.created_at,
        finished_at=job.finished_at,
    )


@router.get(
    "/jobs/{job_id}/result",
    summary="获取解析结果（mode=inline 内嵌内容，mode=url 返回下载链接）",
)
def get_result(job_id: str, mode: str = Query(default="inline", pattern="^(inline|url)$")):
    job = _require_job(job_id)
    _require_done(job)

    file_store = storage_factory.get_file_store()

    if mode == "inline":
        md_bytes = file_store.read(job.markdown_path) if job.markdown_path else b""
        pos_bytes = file_store.read(job.positions_path) if job.positions_path else b""
        positions_obj = json.loads(pos_bytes) if pos_bytes else None
        return ParseResultInline(
            job_id=job_id,
            mode="inline",
            markdown=md_bytes.decode("utf-8") if md_bytes else None,
            positions=positions_obj,
        )
    else:
        md_url = file_store.get_download_url(job.markdown_path) if job.markdown_path else None
        pos_url = file_store.get_download_url(job.positions_path) if job.positions_path else None
        expires_at = None
        from app.core.config import settings as _settings
        if _settings.storage_type == "cloud":
            expires_at = datetime.now(tz=timezone.utc) + timedelta(seconds=_settings.cloud_url_expires)
        return ParseResultUrl(
            job_id=job_id,
            mode="url",
            markdown_url=md_url,
            positions_url=pos_url,
            url_expires_at=expires_at,
        )


@router.get(
    "/jobs/{job_id}/markdown",
    summary="下载 result.md（mode=inline 文件流，mode=url 返回下载链接）",
)
def get_markdown(job_id: str, mode: str = Query(default="inline", pattern="^(inline|url)$")):
    job = _require_job(job_id)
    _require_done(job)

    file_store = storage_factory.get_file_store()

    if mode == "inline":
        data = file_store.read(job.markdown_path) if job.markdown_path else b""
        return Response(
            content=data,
            media_type="text/markdown; charset=utf-8",
            headers={"Content-Disposition": f'inline; filename="result.md"'},
        )
    else:
        url = file_store.get_download_url(job.markdown_path) if job.markdown_path else None
        from app.core.config import settings as _settings
        expires_at = None
        if _settings.storage_type == "cloud":
            expires_at = datetime.now(tz=timezone.utc) + timedelta(seconds=_settings.cloud_url_expires)
        return FileUrlResponse(url=url, expires_at=expires_at)


@router.get(
    "/jobs/{job_id}/positions",
    summary="下载 positions.json（mode=inline 文件流，mode=url 返回下载链接）",
)
def get_positions(job_id: str, mode: str = Query(default="inline", pattern="^(inline|url)$")):
    job = _require_job(job_id)
    _require_done(job)

    file_store = storage_factory.get_file_store()

    if mode == "inline":
        data = file_store.read(job.positions_path) if job.positions_path else b""
        return Response(
            content=data,
            media_type="application/json",
            headers={"Content-Disposition": f'inline; filename="positions.json"'},
        )
    else:
        url = file_store.get_download_url(job.positions_path) if job.positions_path else None
        from app.core.config import settings as _settings
        expires_at = None
        if _settings.storage_type == "cloud":
            expires_at = datetime.now(tz=timezone.utc) + timedelta(seconds=_settings.cloud_url_expires)
        return FileUrlResponse(url=url, expires_at=expires_at)
