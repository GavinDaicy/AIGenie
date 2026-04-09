from abc import ABC, abstractmethod


class FileStore(ABC):

    @abstractmethod
    def save_upload(self, job_id: str, filename: str, data: bytes) -> str:
        """保存原始上传文件，返回内部存储路径（相对路径或对象存储 Key）。"""

    @abstractmethod
    def save_result(self, job_id: str, filename: str, data: bytes) -> str:
        """保存解析结果文件，返回内部存储路径。"""

    @abstractmethod
    def read(self, path: str) -> bytes:
        """读取文件内容，path 为 save_upload / save_result 返回的路径。"""

    @abstractmethod
    def get_download_url(self, path: str, expires_seconds: int = 3600) -> str:
        """
        获取文件访问 URL。
        - 本地存储：返回内部 API 接口地址（/api/v1/jobs/{id}/markdown 等）
        - 云存储：返回预签名 URL（含过期时间）
        """

    @abstractmethod
    def download_from_url(self, url: str) -> bytes:
        """从外部 URL 下载文件内容（用于 file_url 输入模式）。"""
