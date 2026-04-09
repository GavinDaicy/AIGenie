# doc-parser

文档解析微服务，基于 **MinerU + Docling 双引擎**，将 PDF/Word/PPT/图片等原始文档解析为：
- `result.md`：带 `<!-- @block:{block_id} -->` 锚点的 Markdown 文本
- `positions.json`：每个文本块的精确位置元数据（页码 + 像素坐标）

## 快速开始（本地开发，CPU 模式）

```bash
# 1. 复制环境变量配置
cp .env.example .env

# 2. 启动服务（FastAPI + Celery Worker + Redis）
docker-compose up --build

# 3. 测试上传文件
curl -X POST http://localhost:8100/api/v1/parse \
  -F "file=@/path/to/your/document.pdf" \
  -F "language=zh"

# 4. 查询任务状态
curl http://localhost:8100/api/v1/jobs/{job_id}

# 5. 获取解析结果（inline 模式）
curl "http://localhost:8100/api/v1/jobs/{job_id}/result?mode=inline"
```

## API 接口

| 方法 | 路径 | 说明 |
|-----|------|------|
| POST | `/api/v1/parse` | 提交解析任务（binary 或 file_url）|
| GET | `/api/v1/jobs/{job_id}` | 查询任务状态 |
| GET | `/api/v1/jobs/{job_id}/result?mode=inline\|url` | 获取完整结果 |
| GET | `/api/v1/jobs/{job_id}/markdown?mode=inline\|url` | 下载 result.md |
| GET | `/api/v1/jobs/{job_id}/positions?mode=inline\|url` | 下载 positions.json |
| GET | `/health` | 健康检查 |

## 两种文件输入方式

### 方式 A：binary 上传（本地/私有化部署）

```bash
curl -X POST http://localhost:8100/api/v1/parse \
  -F "file=@document.pdf" \
  -F "language=zh" \
  -F "parser=auto"
```

### 方式 B：URL 输入（云存储部署）

```bash
curl -X POST http://localhost:8100/api/v1/parse \
  -H "Content-Type: application/json" \
  -d '{
    "file_url": "https://oss.example.com/bucket/document.pdf",
    "filename": "document.pdf",
    "language": "zh",
    "callback_url": "https://api.example.com/parse-callback"
  }'
```

## 两种结果返回方式

```bash
# mode=inline（默认）：内嵌内容，适合小文件
curl "http://localhost:8100/api/v1/jobs/{job_id}/result?mode=inline"

# mode=url：返回下载链接，适合大文件/云存储
curl "http://localhost:8100/api/v1/jobs/{job_id}/result?mode=url"
```

## 存储策略

| 策略 | 适用场景 | 配置 |
|-----|---------|------|
| `local`（默认）| 开发环境、私有化部署 | `STORAGE_TYPE=local` |
| `cloud` | 云部署、分布式 Worker | `STORAGE_TYPE=cloud` |

云存储支持 AWS S3 / 阿里云 OSS / 华为云 OBS / MinIO（S3 兼容协议）。

## GPU 部署（MinerU 场景）

```bash
# 使用 GPU override 文件覆盖 worker 配置
docker-compose -f docker-compose.yml -f docker-compose.gpu.yml up --build
```

## 运行测试

```bash
pip install -r requirements-dev.txt
pytest tests/ -v
```

## 迭代状态

| 迭代 | 状态 | 说明 |
|-----|------|------|
| I1 | ✅ 完成 | FastAPI + Celery + Redis 骨架，双存储策略，双输入方式，双结果模式 |
| I2 | 🔲 待实现 | Docling 解析器（CPU），Word/PPT/PDF/Excel |
| I3 | 🔲 待实现 | MinerU 解析器（GPU），中文 PDF/图片 |
