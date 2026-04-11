from typing import Optional, Any
from datetime import datetime
from pydantic import BaseModel
from app.models.job import JobStatus


class ParseJobResponse(BaseModel):
    job_id: str
    status: JobStatus
    filename: str
    estimated_seconds: int = 30


class JobStatusResponse(BaseModel):
    job_id: str
    status: JobStatus
    progress: float = 0.0
    filename: str
    parser_used: Optional[str] = None
    page_count: Optional[int] = None
    block_count: Optional[int] = None
    error: Optional[str] = None
    created_at: datetime
    finished_at: Optional[datetime] = None


class ParseResultInline(BaseModel):
    job_id: str
    mode: str = "inline"
    markdown: Optional[str] = None
    positions: Optional[Any] = None     # PositionsJson dict


class ParseResultUrl(BaseModel):
    job_id: str
    mode: str = "url"
    markdown_url: Optional[str] = None
    positions_url: Optional[str] = None
    url_expires_at: Optional[datetime] = None


class FileUrlResponse(BaseModel):
    url: str
    expires_at: Optional[datetime] = None
