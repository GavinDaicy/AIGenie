import pytest
import fakeredis
from pytest_httpserver import HTTPServer


@pytest.fixture(autouse=True)
def fake_redis(monkeypatch):
    """用 fakeredis 替换 job_store 中的真实 Redis 客户端，测试无需启动真实 Redis。"""
    fake = fakeredis.FakeRedis(decode_responses=True)

    import app.core.job_store as job_store_module
    monkeypatch.setattr(job_store_module, "_redis_client", fake)
    yield fake
    fake.flushall()


@pytest.fixture
def httpserver():
    with HTTPServer(host="127.0.0.1") as server:
        yield server
