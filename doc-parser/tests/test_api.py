"""
I1-B7 基础 API 测试。

测试覆盖：
1. binary 模式：multipart 上传 → job_id → 状态变 done
2. URL 模式：JSON body（file_url）→ job_id → 状态变 done
3. GET /result?mode=inline 返回 markdown/positions 字段
4. GET /result?mode=url 返回 markdown_url/positions_url 字段
"""
import json
import time
import pytest
from fastapi.testclient import TestClient
from unittest.mock import patch, MagicMock

from app.main import app
from app.core.job_store import set_job, get_job
from app.models.job import JobRecord, JobStatus


@pytest.fixture(autouse=True)
def mock_celery(monkeypatch):
    """拦截 Celery apply_async，改为直接同步执行 parse_document 逻辑，
    同时用 FakeParser 代替真实解析器，避免测试环境依赖 Docling/MinerU。"""
    from app.tasks import parse_task
    from app.parsers.base import ParseOutput
    from app.models.position import BlockPosition, BlockType, PositionsJson

    class _FakeParser:
        def parse(self, file_path, job_id):
            block = BlockPosition(
                block_id="b-0001",
                type=BlockType.paragraph,
                text="fake content",
                page=1,
                bbox=[0.0, 0.0, 100.0, 20.0],
            )
            positions = PositionsJson(
                source_file="test.txt",
                source_type="txt",
                parser="fake",
                total_pages=1,
                page_width=595.3,
                page_height=841.9,
                coordinate_system="top-left",
                blocks=[block],
            )
            return ParseOutput(
                markdown="<!-- @block:b-0001 -->\nfake content\n",
                positions=positions,
                blocks=[block],
            )

    monkeypatch.setattr("app.parsers.router.create_parser", lambda name: _FakeParser())

    def fake_apply_async(args=None, kwargs=None, queue=None, **kw):
        job_id = (args or [])[0] if args else (kwargs or {}).get("job_id")
        if job_id:
            parse_task.parse_document(job_id)

    monkeypatch.setattr(
        "app.api.routes.parse.parse_document.apply_async",
        fake_apply_async,
    )


@pytest.fixture
def tmp_storage(tmp_path, monkeypatch):
    """重定向存储到临时目录（用 monkeypatch 整体替换 get_file_store，绕过 lru_cache）。"""
    from app.core import storage_factory
    from app.storage.local_store import LocalFileStore

    store = LocalFileStore(str(tmp_path), "http://testserver")
    monkeypatch.setattr(storage_factory, "get_file_store", lambda: store)
    yield store


@pytest.fixture
def client(tmp_storage):
    return TestClient(app, raise_server_exceptions=True)


class TestBinaryUpload:
    def test_submit_txt_file_returns_202(self, client):
        content = b"Hello, this is a test document."
        resp = client.post(
            "/api/v1/parse",
            files={"file": ("sample.txt", content, "text/plain")},
        )
        assert resp.status_code == 202
        body = resp.json()
        assert "job_id" in body
        assert body["status"] == "queued"
        assert body["filename"] == "sample.txt"

    def test_binary_job_becomes_done(self, client):
        content = b"Test document content"
        resp = client.post(
            "/api/v1/parse",
            files={"file": ("doc.txt", content, "text/plain")},
        )
        job_id = resp.json()["job_id"]

        status_resp = client.get(f"/api/v1/jobs/{job_id}")
        assert status_resp.status_code == 200
        assert status_resp.json()["status"] == "done"

    def test_binary_with_language_and_parser(self, client):
        resp = client.post(
            "/api/v1/parse",
            files={"file": ("test.pdf", b"%PDF-1.4 test", "application/pdf")},
            data={"language": "zh", "parser": "auto"},
        )
        assert resp.status_code == 202
        assert resp.json()["filename"] == "test.pdf"

    def test_unsupported_extension_returns_415(self, client):
        resp = client.post(
            "/api/v1/parse",
            files={"file": ("virus.exe", b"MZ", "application/octet-stream")},
        )
        assert resp.status_code == 415

    def test_missing_file_returns_400(self, client):
        resp = client.post(
            "/api/v1/parse",
            data={"language": "zh"},
            headers={"Content-Type": "multipart/form-data"},
        )
        assert resp.status_code in (400, 422)


class TestUrlInput:
    def test_submit_url_returns_202(self, client, httpserver):
        httpserver.expect_request("/doc.txt").respond_with_data(b"doc content")
        resp = client.post(
            "/api/v1/parse",
            json={
                "file_url": httpserver.url_for("/doc.txt"),
                "filename": "doc.txt",
                "language": "auto",
            },
        )
        assert resp.status_code == 202
        body = resp.json()
        assert "job_id" in body
        assert body["filename"] == "doc.txt"

    def test_url_job_becomes_done(self, client, httpserver):
        httpserver.expect_request("/report.txt").respond_with_data(b"report content")
        resp = client.post(
            "/api/v1/parse",
            json={
                "file_url": httpserver.url_for("/report.txt"),
                "filename": "report.txt",
            },
        )
        job_id = resp.json()["job_id"]

        status_resp = client.get(f"/api/v1/jobs/{job_id}")
        assert status_resp.json()["status"] == "done"

    def test_missing_filename_returns_error(self, client):
        resp = client.post(
            "/api/v1/parse",
            json={"file_url": "http://example.com/file.pdf"},
        )
        assert resp.status_code == 422

    def test_invalid_language_returns_error(self, client, httpserver):
        httpserver.expect_request("/f.txt").respond_with_data(b"x")
        resp = client.post(
            "/api/v1/parse",
            json={
                "file_url": httpserver.url_for("/f.txt"),
                "filename": "f.txt",
                "language": "invalid",
            },
        )
        assert resp.status_code == 422


class TestGetResult:
    def _submit_and_wait(self, client, filename="sample.txt", content=b"test"):
        resp = client.post(
            "/api/v1/parse",
            files={"file": (filename, content, "text/plain")},
        )
        return resp.json()["job_id"]

    def test_result_inline_has_markdown_and_positions(self, client):
        job_id = self._submit_and_wait(client)
        resp = client.get(f"/api/v1/jobs/{job_id}/result?mode=inline")
        assert resp.status_code == 200
        body = resp.json()
        assert body["mode"] == "inline"
        assert "markdown" in body
        assert body["markdown"] is not None
        assert "positions" in body
        assert body["positions"] is not None

    def test_result_url_has_markdown_url_and_positions_url(self, client):
        job_id = self._submit_and_wait(client)
        resp = client.get(f"/api/v1/jobs/{job_id}/result?mode=url")
        assert resp.status_code == 200
        body = resp.json()
        assert body["mode"] == "url"
        assert "markdown_url" in body
        assert "positions_url" in body
        assert body["markdown_url"] is not None

    def test_get_markdown_inline_returns_file_stream(self, client):
        job_id = self._submit_and_wait(client)
        resp = client.get(f"/api/v1/jobs/{job_id}/markdown?mode=inline")
        assert resp.status_code == 200
        assert "text/markdown" in resp.headers.get("content-type", "")
        assert len(resp.content) > 0

    def test_get_positions_inline_returns_json(self, client):
        job_id = self._submit_and_wait(client)
        resp = client.get(f"/api/v1/jobs/{job_id}/positions?mode=inline")
        assert resp.status_code == 200
        assert "application/json" in resp.headers.get("content-type", "")
        data = json.loads(resp.content)
        assert "blocks" in data

    def test_get_markdown_url_returns_url_response(self, client):
        job_id = self._submit_and_wait(client)
        resp = client.get(f"/api/v1/jobs/{job_id}/markdown?mode=url")
        assert resp.status_code == 200
        assert "url" in resp.json()

    def test_get_positions_url_returns_url_response(self, client):
        job_id = self._submit_and_wait(client)
        resp = client.get(f"/api/v1/jobs/{job_id}/positions?mode=url")
        assert resp.status_code == 200
        assert "url" in resp.json()

    def test_result_not_found_returns_404(self, client):
        resp = client.get("/api/v1/jobs/nonexistent-job/result")
        assert resp.status_code == 404


class TestJobStatus:
    def test_health_endpoint(self, client):
        resp = client.get("/health")
        assert resp.status_code == 200
        assert resp.json()["status"] == "ok"

    def test_get_status_not_found(self, client):
        resp = client.get("/api/v1/jobs/no-such-job")
        assert resp.status_code == 404
