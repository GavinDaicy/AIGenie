from pydantic_settings import BaseSettings
from pydantic import field_validator
from typing import Optional


class Settings(BaseSettings):
    # Redis
    redis_url: str = "redis://localhost:6379/0"

    # 存储策略
    storage_type: str = "local"          # local | cloud
    storage_path: str = "/data/jobs"     # 仅 local 模式使用
    service_base_url: str = "http://localhost:8100"  # 仅 local 模式使用

    # 云存储配置（storage_type=cloud 时生效）
    cloud_endpoint: str = ""             # MinIO: http://minio:9000 / S3: 留空
    cloud_bucket: str = ""
    cloud_prefix: str = "doc-parser-jobs"
    cloud_access_key: str = ""
    cloud_secret_key: str = ""
    cloud_region: str = "us-east-1"
    cloud_url_expires: int = 3600        # 预签名 URL 有效期（秒）

    # 文件限制
    max_file_size_mb: int = 200

    # 允许的文件扩展名白名单
    allowed_extensions: list[str] = [
        ".pdf", ".docx", ".doc", ".pptx", ".ppt",
        ".html", ".htm", ".md", ".adoc",
        ".xlsx", ".xls", ".csv",
        ".png", ".jpg", ".jpeg", ".tiff", ".bmp", ".webp",
        ".txt",
    ]

    # Celery 任务队列名称
    celery_task_queue: str = "parse"

    # Job 状态在 Redis 中的 TTL（秒），默认 24 小时
    job_ttl_seconds: int = 86400

    # Worker 模式标识（docker-compose 用）
    worker_mode: bool = False

    @field_validator("storage_type")
    @classmethod
    def validate_storage_type(cls, v: str) -> str:
        if v not in ("local", "cloud"):
            raise ValueError("storage_type must be 'local' or 'cloud'")
        return v

    model_config = {"env_file": ".env", "env_file_encoding": "utf-8", "extra": "ignore"}


settings = Settings()
