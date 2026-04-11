import json
import logging
import os
import tempfile
from datetime import datetime, timezone
from pathlib import Path

from celery import Task

from app.core.celery_app import celery_app
from app.core.job_store import get_job, update_job
from app.models.job import JobStatus

logger = logging.getLogger(__name__)


class ParseTask(Task):
    abstract = True

    def on_failure(self, exc, task_id, args, kwargs, einfo):
        job_id = args[0] if args else kwargs.get("job_id")
        if job_id:
            update_job(
                job_id,
                status=JobStatus.failed,
                error=str(exc),
                finished_at=datetime.now(timezone.utc),
            )
        logger.error("parse_document failed job_id=%s: %s", job_id, exc)


@celery_app.task(bind=True, base=ParseTask, name="app.tasks.parse_task.parse_document", queue="parse")
def parse_document(self: Task, job_id: str) -> dict:
    """
    Celery Task：
    1. 下载/读取原始文件
    2. ParserRouter 选择引擎
    3. parser.parse() → ParseOutput
    4. 保存 result.md + positions.json
    5. 更新 JobRecord
    6. 发送回调（若有）
    """
    logger.info("parse_document start job_id=%s", job_id)

    job = get_job(job_id)
    if job is None:
        logger.error("Job not found: %s", job_id)
        return {"error": "job_not_found"}

    update_job(job_id, status=JobStatus.processing, progress=0.1)

    from app.core import storage_factory
    file_store = storage_factory.get_file_store()

    try:
        # 1. URL 输入模式：先下载原始文件并保存
        if job.input_mode == "url" and job.file_url:
            logger.info("Downloading file from url: %s", job.file_url)
            data = file_store.download_from_url(job.file_url)
            upload_path = file_store.save_upload(job_id, job.filename, data)
            job = update_job(job_id, upload_path=upload_path)

        update_job(job_id, progress=0.2)

        # 2. 读取文件内容，写入临时文件供解析器使用
        file_bytes = file_store.read(job.upload_path)
        ext = Path(job.filename).suffix
        tmp_fd, tmp_path = tempfile.mkstemp(suffix=ext)
        try:
            os.write(tmp_fd, file_bytes)
            os.close(tmp_fd)

            update_job(job_id, progress=0.3)

            # 3. 选择解析引擎
            from app.parsers.router import select_parser, create_parser
            engine = select_parser(job.filename, job.language)
            logger.info("Selected parser=%s for job_id=%s filename=%s", engine, job_id, job.filename)

            update_job(job_id, progress=0.4)

            # 4. 执行解析
            parser = create_parser(engine)
            output = parser.parse(tmp_path, job_id)

        finally:
            try:
                os.unlink(tmp_path)
            except OSError:
                pass

        update_job(job_id, progress=0.8)

        # 5. 持久化结果
        md_bytes = output.markdown.encode("utf-8")
        pos_bytes = output.positions.model_dump_json(indent=2).encode("utf-8")

        md_path = file_store.save_result(job_id, "result.md", md_bytes)
        pos_path = file_store.save_result(job_id, "positions.json", pos_bytes)

        # 6. 更新 Job 状态
        update_job(
            job_id,
            status=JobStatus.done,
            progress=1.0,
            markdown_path=md_path,
            positions_path=pos_path,
            parser_used=output.positions.parser,
            page_count=output.positions.total_pages,
            block_count=len(output.blocks),
            finished_at=datetime.now(timezone.utc),
        )

        _send_callback(job_id)

        logger.info(
            "parse_document done job_id=%s parser=%s blocks=%d",
            job_id, output.positions.parser, len(output.blocks),
        )
        return {"job_id": job_id, "status": "done"}

    except Exception as exc:
        update_job(
            job_id,
            status=JobStatus.failed,
            error=str(exc),
            finished_at=datetime.now(timezone.utc),
        )
        logger.exception("parse_document error job_id=%s", job_id)
        raise


def _send_callback(job_id: str) -> None:
    """解析完成后异步通知 callback_url（若有配置）。"""
    job = get_job(job_id)
    if job is None or not job.callback_url:
        return

    try:
        import httpx
        from app.core.storage_factory import get_file_store

        file_store = get_file_store()
        markdown_url = (
            file_store.get_download_url(job.markdown_path) if job.markdown_path else None
        )
        positions_url = (
            file_store.get_download_url(job.positions_path) if job.positions_path else None
        )

        payload = {
            "job_id": job_id,
            "status": job.status,
            "markdown_url": markdown_url,
            "positions_url": positions_url,
        }
        httpx.post(job.callback_url, json=payload, timeout=10)
        logger.info("Callback sent to %s for job_id=%s", job.callback_url, job_id)
    except Exception as exc:
        logger.warning("Callback failed job_id=%s: %s", job_id, exc)
