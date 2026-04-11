import os
import httpx
from app.storage.base import FileStore


class LocalFileStore(FileStore):
    """
    本地文件系统存储实现。
    文件组织结构：{storage_path}/{job_id}/{filename}
    """

    def __init__(self, storage_path: str, base_url: str):
        self.storage_path = storage_path
        self.base_url = base_url.rstrip("/")

    def _full_path(self, relative_path: str) -> str:
        return os.path.join(self.storage_path, relative_path)

    def save_upload(self, job_id: str, filename: str, data: bytes) -> str:
        relative = f"{job_id}/upload/{filename}"
        full = self._full_path(relative)
        os.makedirs(os.path.dirname(full), exist_ok=True)
        with open(full, "wb") as f:
            f.write(data)
        return relative

    def save_result(self, job_id: str, filename: str, data: bytes) -> str:
        relative = f"{job_id}/{filename}"
        full = self._full_path(relative)
        os.makedirs(os.path.dirname(full), exist_ok=True)
        with open(full, "wb") as f:
            f.write(data)
        return relative

    def read(self, path: str) -> bytes:
        with open(self._full_path(path), "rb") as f:
            return f.read()

    def get_download_url(self, path: str, expires_seconds: int = 3600) -> str:
        parts = path.split("/", 1)
        if len(parts) < 2:
            return f"{self.base_url}/api/v1/files/{path}"
        job_id, filename = parts[0], parts[1]
        if filename == "result.md":
            return f"{self.base_url}/api/v1/jobs/{job_id}/markdown"
        if filename == "positions.json":
            return f"{self.base_url}/api/v1/jobs/{job_id}/positions"
        return f"{self.base_url}/api/v1/jobs/{job_id}/files/{filename}"

    def download_from_url(self, url: str) -> bytes:
        resp = httpx.get(url, timeout=120, follow_redirects=True)
        resp.raise_for_status()
        return resp.content
