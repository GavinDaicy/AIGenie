import os
import pytest
import tempfile
from app.storage.local_store import LocalFileStore


@pytest.fixture
def tmp_store(tmp_path):
    return LocalFileStore(
        storage_path=str(tmp_path),
        base_url="http://localhost:8100",
    )


class TestLocalFileStore:
    def test_save_and_read_upload(self, tmp_store):
        data = b"hello upload"
        path = tmp_store.save_upload("job-001", "test.pdf", data)
        assert tmp_store.read(path) == data

    def test_save_and_read_result(self, tmp_store):
        data = b"# Hello\nworld"
        path = tmp_store.save_result("job-001", "result.md", data)
        assert tmp_store.read(path) == data

    def test_save_result_creates_dirs(self, tmp_store, tmp_path):
        data = b"{}"
        tmp_store.save_result("job-xyz", "positions.json", data)
        assert os.path.exists(str(tmp_path / "job-xyz" / "positions.json"))

    def test_get_download_url_markdown(self, tmp_store):
        tmp_store.save_result("job-001", "result.md", b"md")
        url = tmp_store.get_download_url("job-001/result.md")
        assert url == "http://localhost:8100/api/v1/jobs/job-001/markdown"

    def test_get_download_url_positions(self, tmp_store):
        tmp_store.save_result("job-001", "positions.json", b"{}")
        url = tmp_store.get_download_url("job-001/positions.json")
        assert url == "http://localhost:8100/api/v1/jobs/job-001/positions"

    def test_upload_path_structure(self, tmp_store, tmp_path):
        tmp_store.save_upload("job-abc", "doc.docx", b"data")
        assert os.path.exists(str(tmp_path / "job-abc" / "upload" / "doc.docx"))

    def test_download_from_url(self, tmp_store, httpserver):
        content = b"file content from url"
        httpserver.expect_request("/file.txt").respond_with_data(content)
        result = tmp_store.download_from_url(httpserver.url_for("/file.txt"))
        assert result == content
