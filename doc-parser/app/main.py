import logging
import os
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from app.api.routes import parse, jobs

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
)
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    from app.core.config import settings
    try:
        os.makedirs(settings.storage_path, exist_ok=True)
    except OSError:
        pass
    logger.info(
        "doc-parser started | storage_type=%s | storage_path=%s",
        settings.storage_type,
        settings.storage_path,
    )
    yield
    logger.info("doc-parser shutdown")


app = FastAPI(
    title="doc-parser",
    description="文档解析微服务：PDF/Word/PPT/图片 → Markdown + positions.json",
    version="0.1.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(parse.router, prefix="/api/v1", tags=["parse"])
app.include_router(jobs.router, prefix="/api/v1", tags=["jobs"])


@app.get("/health", tags=["health"])
def health():
    return {"status": "ok", "service": "doc-parser"}


@app.exception_handler(Exception)
async def global_exception_handler(request, exc):
    logger.exception("Unhandled exception: %s", exc)
    return JSONResponse(
        status_code=500,
        content={"error": "INTERNAL_ERROR", "message": str(exc)},
    )

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8100)

# 生产运行命令
# uvicorn app.main:app --host 0.0.0.0 --port 8100 --reload

# uvicorn app.main:app   # 加载 app/main.py 里的 app 对象
#   --host 0.0.0.0       # 监听所有网卡（不加这个只有 localhost 能访问，Docker 内必填）
#   --port 8100          # 端口
#   --reload             # 开发模式：代码改变自动重启（生产环境不加）

# worker启动命令
# celery -A app.core.celery_app worker --loglevel=info -Q parse -c 2
# celery -A app.core.celery_app worker --loglevel=info -Q parse -c 1

# 参数	含义
# -A app.core.celery_app	指定 Celery 应用
# -Q parse	监听 parse 队列（与 config.py 中 celery_task_queue 一致）
# -c 2	并发 worker 数量（2个）
# --loglevel=info	日志级别