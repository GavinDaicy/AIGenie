import httpx
from app.storage.base import FileStore


class CloudFileStore(FileStore):
    """
    兼容 S3 协议的云存储实现（AWS S3 / 阿里云 OSS / 华为云 OBS / MinIO）。
    使用 boto3 客户端，MinIO 只需配置 endpoint_url 即可。
    """

    def __init__(self, bucket: str, prefix: str, s3_client, url_expires: int = 3600):
        self.bucket = bucket
        self.prefix = prefix.rstrip("/")
        self.s3 = s3_client
        self.url_expires = url_expires

    def _key(self, job_id: str, filename: str) -> str:
        return f"{self.prefix}/{job_id}/{filename}"

    def save_upload(self, job_id: str, filename: str, data: bytes) -> str:
        key = self._key(job_id, f"upload/{filename}")
        self.s3.put_object(Bucket=self.bucket, Key=key, Body=data)
        return key

    def save_result(self, job_id: str, filename: str, data: bytes) -> str:
        key = self._key(job_id, filename)
        self.s3.put_object(Bucket=self.bucket, Key=key, Body=data)
        return key

    def read(self, path: str) -> bytes:
        response = self.s3.get_object(Bucket=self.bucket, Key=path)
        return response["Body"].read()

    def get_download_url(self, path: str, expires_seconds: int = 3600) -> str:
        return self.s3.generate_presigned_url(
            "get_object",
            Params={"Bucket": self.bucket, "Key": path},
            ExpiresIn=expires_seconds or self.url_expires,
        )

    def download_from_url(self, url: str) -> bytes:
        resp = httpx.get(url, timeout=120, follow_redirects=True)
        resp.raise_for_status()
        return resp.content
