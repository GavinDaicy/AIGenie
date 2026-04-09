from functools import lru_cache
from app.core.config import settings
from app.storage.base import FileStore


@lru_cache(maxsize=1)
def get_file_store() -> FileStore:
    """根据 settings.storage_type 创建对应 FileStore 单例。"""
    if settings.storage_type == "local":
        from app.storage.local_store import LocalFileStore
        return LocalFileStore(
            storage_path=settings.storage_path,
            base_url=settings.service_base_url,
        )
    elif settings.storage_type == "cloud":
        import boto3
        from app.storage.cloud_store import CloudFileStore

        kwargs = dict(
            aws_access_key_id=settings.cloud_access_key,
            aws_secret_access_key=settings.cloud_secret_key,
            region_name=settings.cloud_region,
        )
        if settings.cloud_endpoint:
            kwargs["endpoint_url"] = settings.cloud_endpoint

        s3_client = boto3.client("s3", **kwargs)
        return CloudFileStore(
            bucket=settings.cloud_bucket,
            prefix=settings.cloud_prefix,
            s3_client=s3_client,
            url_expires=settings.cloud_url_expires,
        )
    else:
        raise ValueError(f"Unsupported storage_type: {settings.storage_type}")
