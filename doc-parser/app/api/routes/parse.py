import uuid
import logging
from pathlib import Path
from typing import Optional

from fastapi import APIRouter, File, Form, HTTPException, Request, UploadFile, status
from fastapi.responses import JSONResponse

from app.core.config import settings
from app.core.job_store import set_job
from app.core import storage_factory
from app.models.job import JobRecord, JobStatus
from app.api.schemas.request import ParseUrlRequest
from app.api.schemas.response import ParseJobResponse
from app.tasks.parse_task import parse_document

router = APIRouter()
logger = logging.getLogger(__name__)

ALLOWED_EXTENSIONS = set(settings.allowed_extensions)
MAX_BYTES = settings.max_file_size_mb * 1024 * 1024


def _validate_filename(filename: str) -> str:
    ext = Path(filename).suffix.lower()
    if ext not in ALLOWED_EXTENSIONS:
        raise HTTPException(
            status_code=status.HTTP_415_UNSUPPORTED_MEDIA_TYPE,
            detail=f"Unsupported file type: {ext}",
        )
    return ext


@router.post(
    "/parse",
    response_model=ParseJobResponse,
    status_code=status.HTTP_202_ACCEPTED,
    summary="提交文档解析任务（支持 binary 上传和 file_url 两种方式）",
)
async def submit_parse(request: Request):
    content_type = request.headers.get("content-type", "")

    if "multipart/form-data" in content_type:
        return await _handle_multipart(request)
    elif "application/json" in content_type:
        return await _handle_json_url(request)
    else:
        raise HTTPException(
            status_code=status.HTTP_415_UNSUPPORTED_MEDIA_TYPE,
            detail="Content-Type must be multipart/form-data or application/json",
        )


async def _handle_multipart(request: Request) -> ParseJobResponse:
    form = await request.form()
    file: Optional[UploadFile] = form.get("file")
    if file is None:
        raise HTTPException(status_code=400, detail="Missing 'file' field in multipart form")

    filename = file.filename or "upload.bin"
    _validate_filename(filename)

    data = await file.read()
    if len(data) > MAX_BYTES:
        raise HTTPException(
            status_code=413,
            detail=f"File too large. Max allowed: {settings.max_file_size_mb} MB",
        )

    language = form.get("language", "auto")
    parser = form.get("parser", "auto")
    callback_url = form.get("callback_url", None)

    job_id = f"job-{uuid.uuid4().hex[:12]}"
    file_store = storage_factory.get_file_store()
    upload_path = file_store.save_upload(job_id, filename, data)

    job = JobRecord(
        job_id=job_id,
        filename=filename,
        input_mode="binary",
        language=language,
        parser=parser,
        callback_url=callback_url or None,
        upload_path=upload_path,
    )
    set_job(job)

    parse_document.apply_async(args=[job_id], queue=settings.celery_task_queue)
    logger.info("Queued parse job job_id=%s filename=%s mode=binary", job_id, filename)

    return ParseJobResponse(
        job_id=job_id,
        status=JobStatus.queued,
        filename=filename,
        estimated_seconds=_estimate_seconds(len(data)),
    )


async def _handle_json_url(request: Request) -> ParseJobResponse:
    body = await request.json()
    try:
        req = ParseUrlRequest(**body)
    except Exception as exc:
        raise HTTPException(status_code=422, detail=str(exc))

    _validate_filename(req.filename)

    job_id = f"job-{uuid.uuid4().hex[:12]}"

    job = JobRecord(
        job_id=job_id,
        filename=req.filename,
        input_mode="url",
        language=req.language,
        parser=req.parser,
        callback_url=req.callback_url,
        file_url=req.file_url,
    )
    set_job(job)

    parse_document.apply_async(args=[job_id], queue=settings.celery_task_queue)
    logger.info("Queued parse job job_id=%s filename=%s mode=url", job_id, req.filename)

    return ParseJobResponse(
        job_id=job_id,
        status=JobStatus.queued,
        filename=req.filename,
        estimated_seconds=30,
    )


def _estimate_seconds(file_size_bytes: int) -> int:
    mb = file_size_bytes / (1024 * 1024)
    return max(10, int(mb * 3))
