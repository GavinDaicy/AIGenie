from enum import Enum
from typing import Optional
from datetime import datetime, timezone
from pydantic import BaseModel


class JobStatus(str, Enum):
    queued = "queued"
    processing = "processing"
    done = "done"
    failed = "failed"


class JobRecord(BaseModel):
    job_id: str
    status: JobStatus = JobStatus.queued
    filename: str
    input_mode: str  # binary | url
    language: str = "auto"
    parser: str = "auto"
    callback_url: Optional[str] = None

    # URL 输入模式时存放原始 URL（Worker 延迟下载用）
    file_url: Optional[str] = None

    # 本地存储路径（binary 模式保存后填入）
    upload_path: Optional[str] = None

    # 解析结果存储路径
    markdown_path: Optional[str] = None
    positions_path: Optional[str] = None

    # 解析元数据
    parser_used: Optional[str] = None
    page_count: Optional[int] = None
    block_count: Optional[int] = None
    progress: float = 0.0

    # 错误信息
    error: Optional[str] = None

    # 时间戳
    created_at: datetime = datetime.now(timezone.utc)
    finished_at: Optional[datetime] = None
