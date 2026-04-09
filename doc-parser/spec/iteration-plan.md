# doc-parser 迭代实施计划

## 总览

| 迭代 | 范围 | 服务 | 交付物 |
|-----|------|------|--------|
| **I1** | Python 服务基础框架 | doc-parser | FastAPI + Celery + Redis 可运行骨架 |
| **I2** | Docling 解析器（CPU）| doc-parser | Word/PPT/英文PDF → MD + positions.json |
| **I3** | MinerU 解析器（GPU）| doc-parser | 中文PDF/图片解析 + 双引擎路由 |
| **I4** | Java 文档管理层 | query-genie-server | 上传/解析/持久化三份文件 |
| **I5** | 分块入库与位置写入 | query-genie-server | Chunk ES 文档含 position，支持重新分块 |
| **I6** | 前端 PDF 定位高亮 | query-genie-front | 点击引用 → PDF.js 跳转 + bbox 高亮 |

---

## 迭代 1：Python 服务基础框架

**目标**：搭建可运行的 FastAPI + Celery + Redis 骨架，文件上传接口可接收文件并返回 job_id，任务状态可查询，无需实际解析。

### I1-B1 项目初始化

- 创建 `doc-parser/` 目录结构（按设计文档第 8 节）
- 创建 `requirements.txt`（基础依赖：fastapi/uvicorn/celery/redis/pydantic/python-multipart）
- 创建 `requirements.cpu.txt`（精简依赖，不含 MinerU/torch）
- 创建 `app/core/config.py`：`Settings` 类，从环境变量读取（REDIS_URL / STORAGE_PATH / MAX_FILE_SIZE_MB）
- 创建 `.env.example`

### I1-B2 Pydantic 模型定义

- `app/models/job.py`：`JobStatus`（枚举：queued/processing/done/failed）、`JobRecord`（新增 `input_mode: str`（binary/url）字段）
- `app/api/schemas/request.py`：
  - `ParseMultipartRequest`（multipart 方式，含 file 字段）
  - `ParseUrlRequest`（JSON 方式，含 `file_url` + `filename` 字段）
  - 两者共享公共字段：`language / parser / callback_url`
- `app/api/schemas/response.py`：
  - `ParseJobResponse`（提交响应，job_id/status/filename）
  - `JobStatusResponse`（状态查询响应）
  - `ParseResultInline`（mode=inline 响应，含 markdown 文本和 positions 对象）
  - `ParseResultUrl`（mode=url 响应，含 markdown_url/positions_url/url_expires_at）
  - `FileUrlResponse`（单文件 URL 响应，url/expires_at）

### I1-B3 Celery + Redis 初始化

- `app/core/celery_app.py`：初始化 Celery，broker/backend 均指向 Redis
- `app/tasks/parse_task.py`：占位 Celery Task `parse_document(job_id)`，仅更新 status=done，不做实际解析
- Job 状态读写封装：`app/core/job_store.py`（set_job / get_job / update_job，基于 Redis Hash，TTL 24h）

### I1-B4 文件存储抽象（双策略）

- `app/storage/base.py`：`FileStore` 抽象基类，定义五个抽象方法：`save_upload / save_result / read / get_download_url / download_from_url`
- `app/storage/local_store.py`：`LocalFileStore`，存储到 `STORAGE_PATH/{job_id}/`，`get_download_url` 返回内部接口地址 `/api/v1/jobs/{id}/markdown` 等
- `app/storage/cloud_store.py`：`CloudFileStore`，基于 boto3（兼容 S3/OSS/OBS/MinIO），`get_download_url` 生成预签名 URL，`save_upload` 接收 URL 时调用 `download_from_url` 先下载再上传
- `app/core/config.py`：`Settings` 新增 `storage_type`（local/cloud）及云存储相关配置（endpoint/bucket/access_key/secret_key/region/url_expires）
- `app/core/storage_factory.py`：根据 `settings.storage_type` 创建对应 `FileStore` 单例（依赖注入入口）

### I1-B5 API 路由实现

- `app/api/routes/parse.py`：`POST /api/v1/parse`
  - 检测 `Content-Type`：`multipart/form-data` → 读取 `file` 二进制；`application/json` → 读取 `file_url` + `filename`
  - 统一校验文件类型（扩展名白名单）和大小限制
  - binary 模式：调用 `file_store.save_upload(job_id, filename, data)`
  - URL 模式：将 `file_url` 写入 JobRecord，Worker 中延迟下载（避免 API 进程阻塞）
  - 创建 JobRecord（含 `input_mode`）→ 入队 Celery Task → 返回 202
- `app/api/routes/jobs.py`：
  - `GET /api/v1/jobs/{job_id}`：返回 `JobStatusResponse`
  - `GET /api/v1/jobs/{job_id}/result?mode=inline|url`：
    - `inline`：读取文件内容内嵌到 `ParseResultInline`
    - `url`：调用 `file_store.get_download_url()` 生成 URL，返回 `ParseResultUrl`
  - `GET /api/v1/jobs/{job_id}/markdown?mode=inline|url`：inline 返回文件流；url 返回 `FileUrlResponse`
  - `GET /api/v1/jobs/{job_id}/positions?mode=inline|url`：同上
- `app/main.py`：FastAPI 实例，注册路由，`/health` 端点

### I1-B6 Docker 配置

- `Dockerfile.cpu`：基于 `python:3.11-slim`，安装 requirements.cpu.txt
- `docker-compose.yml`：redis + doc-parser-api + doc-parser-worker 三个服务
- Worker 启动命令：`celery -A app.core.celery_app worker -Q parse -c 2 --loglevel=info`

### I1-B7 基础测试

- `tests/test_api.py`：
  - binary 模式：`POST /api/v1/parse`（multipart 上传 txt 文件）→ job_id → 状态变 done
  - URL 模式：`POST /api/v1/parse`（JSON body，file_url 指向本地 mock HTTP 服务）→ job_id → 状态变 done
  - `GET /result?mode=inline` 返回 markdown/positions 字段
  - `GET /result?mode=url` 返回 markdown_url/positions_url 字段
- `tests/test_storage.py`：`LocalFileStore` 存取文件、`get_download_url` 返回正确内部地址

**验收标准**：两种输入方式均可提交任务并查询状态；两种 mode 均返回正确响应结构（无实际解析内容）。

---

## 迭代 2：Docling 解析器（CPU 可用）

**目标**：接入 Docling，支持 PDF/DOCX/PPTX/HTML 解析，输出规范的 `result.md`（含 block 锚点）和 `positions.json`（左上角坐标系）。同时支持 Excel（pandas）。

### I2-B1 抽象 Parser 基类

- `app/parsers/base.py`：
  ```python
  class BaseParser(ABC):
      @abstractmethod
      def parse(self, file_path: str, job_id: str) -> ParseOutput:
          ...
  ```
- `app/models/position.py`：`BlockType`枚举、`BlockPosition`（block_id/type/level/text/page/bbox/heading_path/image_path）、`PositionsJson`（source_file/source_type/parser/total_pages/page_width/page_height/coordinate_system/blocks）

### I2-B2 Docling Parser 封装

- `app/parsers/docling_parser.py`：`DoclingParser(BaseParser)`
  - `parse()` 调用 `DocumentConverter().convert(file_path)`
  - 遍历 `result.document.iterate_items()`，提取 text/type/prov
  - `prov.bbox` 坐标转换（PDF 坐标系 → top-left）：`y0 = page_height - bbox.b; y1 = page_height - bbox.t`
  - 分配递增 `block_id`（`b-{n:04d}`）
  - 维护 `heading_path`（遍历时追踪标题层级栈）
  - 构建 `PositionsJson` 对象

### I2-B3 Markdown 生成（含 block 锚点）

- `app/parsers/md_builder.py`：`MarkdownBuilder`
  - 接收 blocks 列表，在每个 block 内容前插入 `<!-- @block:{block_id} -->\n`
  - 表格类型：直接输出 block.text（已是 Markdown 表格格式，Docling 原生支持）
  - 图片类型：输出 `![{caption}]({image_path})`
  - 拼接为完整 Markdown 文本

### I2-B4 Excel Parser

- `app/parsers/excel_parser.py`：`ExcelParser(BaseParser)`
  - 用 `pandas.read_excel(sheet_name=None)` 读取所有 Sheet
  - 每个 Sheet 作为一个 `table` 类型 block
  - `page` 对应 sheet 序号（从 1 开始）
  - `bbox` 设为 `[0, 0, 0, 0]`（Excel 无坐标概念，标注为占位）
  - 输出 `DataFrame.to_markdown()`

### I2-B5 Parser 路由策略

- `app/parsers/router.py`：`ParserRouter`
  - `select(filename, language_hint) → str`（返回引擎名称）
  - 按设计文档第 3.1 节路由规则实现
  - `create_parser(engine_name) → BaseParser`（工厂方法）
  - `language_hint` 为 `auto` 时：检测文件名是否含中文字符，自动判断

### I2-B6 Celery Task 接入真实解析

- 更新 `app/tasks/parse_task.py`：
  1. 从 Redis 取 JobRecord，更新 `status=processing`
  2. 从文件存储读取原始文件路径
  3. `ParserRouter.select()` 选引擎
  4. `parser.parse()` → `ParseOutput`（含 blocks + markdown）
  5. `FileStore.save_result()` 保存 `result.md` + `positions.json`
  6. 更新 JobRecord（status=done/failed，parser_used/page_count/block_count）
  7. 若配置了 `callback_url`：异步 POST 回调

### I2-B7 单元测试

- `tests/test_docling_parser.py`：
  - 解析 `tests/fixtures/sample.pdf` → 验证 positions.json 含 page/bbox 字段
  - 解析 `tests/fixtures/sample.docx` → 验证 result.md 含 `<!-- @block:b-0001 -->` 锚点
  - 坐标转换：验证 top-left 坐标系正确性（`y0 < y1`，`y0 + y1 ≈ page_height`）
- `tests/test_excel_parser.py`：多 Sheet Excel → 每个 Sheet 生成一个 table block
- `tests/test_md_builder.py`：blocks 列表 → 验证 MD 输出中锚点顺序和格式
- `tests/test_router.py`：各扩展名/语言提示 → 验证路由选择结果

**验收标准**：上传 Word/PPT/PDF/Excel 文件，解析完成后 `/result` 接口返回含 block 锚点的 MD 和含 bbox 的 positions.json。

---

## 迭代 3：MinerU 解析器（GPU）

**目标**：接入 MinerU，支持中文 PDF 和图片解析，输出与 Docling 相同的规范格式，完成双引擎路由。

### I3-B1 MinerU Parser 封装

- `app/parsers/mineru_parser.py`：`MinerUParser(BaseParser)`
  - 调用 `magic_pdf` pipeline（`auto` 模式，兼容数字 PDF 和扫描件）
  - MinerU 输出 JSON 中提取每个 block 的 `bbox`（格式：`[x0, y0, x1, y1]`，已是左上角原点）
  - 映射 MinerU `type` 字段 → `BlockType`：`text→paragraph`、`title→heading`、`table→table`、`figure→image`、`equation→formula`
  - 从 `title` block 的层级信息推断 `level`（MinerU 输出含 level 字段）
  - 构建 `heading_path`（同 Docling parser 的标题栈逻辑）
  - 提取嵌入图片并保存到 `{storage_path}/{job_id}/images/`

### I3-B2 MinerU 输出格式适配

- MinerU 页面尺寸：从 `page_info` 取 `page_width`/`page_height`
- 多页文档：遍历所有页，跨页 block_id 全局递增
- 图片 block：`image_path` 设为相对路径 `images/{img_name}`
- 公式：text 存为 LaTeX 字符串（MinerU 原生输出 LaTeX）

### I3-B3 路由策略更新

- `ParserRouter` 中补全 MinerU 分支（I2-B5 中 MinerU 分支为占位）
- 增加运行时检查：若 `import magic_pdf` 失败，MinerU 路由降级到 Docling（CPU 环境兼容）

### I3-B4 GPU Docker 配置

- `Dockerfile`：基于 `nvidia/cuda:11.8.0-runtime-ubuntu22.04`，安装 `requirements.txt`（含 magic-pdf[full]）
- `docker-compose.gpu.yml`：覆盖 worker 服务，添加 `deploy.resources.reservations.devices` GPU 配置
- `docker-compose.yml` 中 worker 默认仍使用 CPU 镜像（开发环境）

### I3-B5 集成测试

- `tests/test_mineru_parser.py`（标注 `@pytest.mark.skipif(no_gpu, ...)`）：
  - 中文 PDF → 验证中文字符正确提取，bbox 合理
  - 扫描件图片 → 验证 OCR 文字提取
- `tests/test_router.py` 补充：中文 PDF 路由到 mineru，英文 PDF 路由到 docling

**验收标准**：中文 PDF 通过 MinerU 解析，英文 PDF/Word 通过 Docling 解析，输出格式统一。

---

## 迭代 4：query-genie-server 文档管理层（Java）

**目标**：在 `query-genie-server` 中实现知识库文档的上传、解析调度、三份文件持久化，提供文档管理 CRUD 接口。

### I4-B1 数据库建表

- 新增 SQL 脚本 `sql/v4_knowledge_document.sql`：
  ```sql
  CREATE TABLE knowledge_document (
      id             VARCHAR(36)  PRIMARY KEY,
      kb_id          VARCHAR(36)  NOT NULL,
      file_name      VARCHAR(255) NOT NULL,
      file_type      VARCHAR(20)  NOT NULL,
      original_path  VARCHAR(500) NOT NULL,
      markdown_path  VARCHAR(500),
      positions_path VARCHAR(500),
      parser         VARCHAR(20),
      total_pages    INT,
      block_count    INT,
      parse_status   VARCHAR(20)  NOT NULL DEFAULT 'pending',
      chunk_status   VARCHAR(20)  NOT NULL DEFAULT 'pending',
      parse_job_id   VARCHAR(64),
      created_at     DATETIME     NOT NULL,
      updated_at     DATETIME     NOT NULL
  );
  ```
- `KnowledgeDocument.java`（domain entity）
- `KnowledgeDocumentMapper.java` + `KnowledgeDocumentMapper.xml`

### I4-B2 对象存储封装（双策略）

- `domain/storage/ObjectStore.java`（接口）：
  - `upload(path, data) → String`（返回存储后的访问路径/URL）
  - `download(path) → byte[]`
  - `getUrl(path) → String`（本地返回下载接口地址，云存储返回预签名 URL）
  - `exists(path) → boolean`
- `infrastructure/storage/LocalObjectStore.java`：存储到 `objectStore/kb-documents/{file_id}/`，`getUrl` 返回本地 HTTP 接口路径
- `infrastructure/storage/S3ObjectStore.java`：基于 AWS SDK v2（兼容阿里 OSS / 华为 OBS / MinIO），`getUrl` 生成预签名 URL
- `ObjectStoreProperties.java`：`@ConfigurationProperties(prefix="app.object-store")`，type（local/s3）/ endpoint / bucket / access-key / secret-key / region / presign-expires-seconds
- `ObjectStoreConfig.java`：`@Configuration`，按 `type` 注册 `ObjectStore` Bean
- 存储路径规则：`kb-documents/{doc_id}/original.{ext}`、`kb-documents/{doc_id}/result.md`、`kb-documents/{doc_id}/positions.json`

### I4-B3 DocParserClient（HTTP 调用封装）

- `infrastructure/docparser/DocParserClient.java`：
  - `submitBinary(file, language, callbackUrl) → String jobId`：multipart 上传二进制文件
  - `submitUrl(fileUrl, filename, language, callbackUrl) → String jobId`：JSON body 提交 file_url
  - `getJobStatus(jobId) → JobStatusResponse`
  - `getResultInline(jobId) → ParseResultInline`：`GET /result?mode=inline`，直接返回 markdown 文本和 positions 内容
  - `getResultUrls(jobId) → ParseResultUrl`：`GET /result?mode=url`，返回 markdown_url / positions_url
  - `downloadMarkdown(jobId) → byte[]`：`GET /markdown?mode=inline`，兜底直接下载（本地存储模式使用）
  - `downloadPositions(jobId) → byte[]`：`GET /positions?mode=inline`，兜底直接下载
- `DocParserProperties.java`：`@ConfigurationProperties(prefix="app.doc-parser")`，url / timeout-seconds / storage-mode（local/cloud）
- `application.yml` 新增：
  ```yaml
  app:
    doc-parser:
      url: ${DOC_PARSER_URL:http://localhost:8100}
      timeout-seconds: 300
      storage-mode: ${DOC_PARSER_STORAGE_MODE:local}
  ```

### I4-B4 文档解析调度服务

- `application/DocumentParseApplication.java`：
  - `submitDocument(kbId, file) → String docId`：
    - **本地存储模式**：直接 binary 上传文件到 doc-parser（`submitBinary`）
    - **云存储模式**：先将文件上传到 OSS → 生成预签名 URL → 调用 `submitUrl(fileUrl, filename, ...)`（doc-parser 从 URL 下载，Java 侧无需传输文件内容）
    - 创建 DB 记录（parse_status=parsing）→ 保存 job_id → 启动异步轮询
  - `pollParseResult(docId)`：`@Async`，定时轮询 job 状态，done 后：
    - **本地存储模式**：调用 `downloadMarkdown(jobId)` + `downloadPositions(jobId)` 获取文件内容 → 上传到本地 ObjectStore → 更新 `markdown_path` / `positions_path`
    - **云存储模式**：调用 `getResultUrls(jobId)` 获取预签名 URL → **直接**将 URL 写入 `markdown_path` / `positions_path`（无需下载内容）
    - 更新 parse_status=done → 触发分块
  - `handleParseCallback(jobId, status, markdownUrl, positionsUrl)`：处理 doc-parser 回调（替代轮询），回调体已含 URL，逻辑同轮询的 done 分支

### I4-B5 文档管理 Controller

- `controller/KnowledgeDocumentController.java`：
  - `POST /kb/{kbId}/documents`：上传文件，触发解析
  - `GET  /kb/{kbId}/documents`：文档列表（含 parse_status/chunk_status）
  - `GET  /kb/{kbId}/documents/{docId}`：文档详情
  - `DELETE /kb/{kbId}/documents/{docId}`：删除文档（含 ES chunk 清理）
  - `GET  /kb/{kbId}/documents/{docId}/original`：下载原始文件
- `dto/`：`DocumentUploadResponse`、`DocumentItem`、`DocumentDetail`

### I4-B6 单元测试

- `KnowledgeDocumentMapperTest.java`：CRUD 测试
- `DocumentParseApplicationTest.java`：Mock DocParserClient，验证状态流转和文件持久化路径

**验收标准**：上传 PDF → `parse_status` 流转到 `done` → 对象存储中可找到 `original.pdf` + `result.md` + `positions.json`。

---

## 迭代 5：分块入库与位置写入（Java）

**目标**：读取持久化的 `result.md` + `positions.json`，按分块策略生成携带 `position` 字段的 Chunk，写入 ES 向量库；提供重新分块接口。

### I5-B1 Block 提取工具类

- `domain/knowledge/chunking/BlockExtractor.java`：
  - 正则解析 `result.md`，按 `<!-- @block:{block_id} -->` 切分
  - 返回 `List<BlockContent>`（block_id + text + type 推断）
- 单元测试：各种 block 类型（heading/paragraph/table/image）均正确切分

### I5-B2 Positions.json 读取模型

- `domain/knowledge/chunking/PositionsJson.java`（反序列化模型，与 doc-parser 输出对齐）
- `domain/knowledge/chunking/BlockPosition.java`
- `domain/knowledge/chunking/PositionResolver.java`：`resolve(blockIds, positionsJson) → ChunkPosition`
  - 取所有 block 的 bbox，计算 `representative_page`（第一个 block 页码）、`representative_bbox`（第一个 block bbox）
  - 构建 `bboxes` 完整列表

### I5-B3 分块策略

- `domain/knowledge/chunking/ChunkStrategy.java`（接口）：`List<Chunk> chunk(List<BlockContent> blocks, ChunkOptions options)`
- `domain/knowledge/chunking/FixedSizeChunkStrategy.java`：按字符数分块（含 overlap），跨 block 合并
- `domain/knowledge/chunking/BlockChunkStrategy.java`：每个 block 一个 chunk（最细粒度，适合精确定位）
- `ChunkOptions.java`：`chunkSize`（默认 512）/ `chunkOverlap`（默认 50）/ `strategy`

### I5-B4 Chunk ES 文档模型

- 更新 ES `knowledge_chunk` index mapping，新增 `position` 字段（nested object）：
  ```json
  "position": {
    "representative_page": "integer",
    "representative_bbox": "float[]",
    "page_start": "integer",
    "page_end": "integer",
    "bboxes": [{"page": "integer", "bbox": "float[]", "block_id": "keyword"}]
  }
  ```
- `domain/knowledge/KnowledgeChunk.java`：新增 `ChunkPosition position` 字段
- `ChunkPosition.java`

### I5-B5 分块入库服务

- `application/ChunkingApplication.java`：
  - `chunkAndIndex(docId)`：读取 `result.md` + `positions.json` → `BlockExtractor.extract()` → `ChunkStrategy.chunk()` → 为每个 chunk 调用 `PositionResolver.resolve()` → 向量化 → 写 ES
  - 更新 `chunk_status`：chunking → done / failed
  - 供 I4-B4 的 `submitDocument` 在 parse_status=done 后自动调用

### I5-B6 重新分块接口

- `controller/KnowledgeDocumentController.java` 新增：
  - `POST /kb/{kbId}/documents/{docId}/rechunk`：Body `{ "chunk_size", "chunk_overlap", "strategy" }`
  - 逻辑：删除该 doc 旧 chunk（ES delete by query `source_file_id`）→ 重新 `chunkAndIndex(docId, newOptions)` → 更新 chunk_status

### I5-B7 CitationItem 位置字段扩展

- `domain/agent/citation/CitationItem.java` 补充字段：
  ```java
  private Integer pageNumber;       // representative_page
  private String bbox;              // representative_bbox JSON
  private String sourceFileId;      // 用于下载原始文件
  ```
- `RagSearchTool.java`：召回 chunk 后，将 `chunk.position.representative_page` + `representative_bbox` 填入 `CitationItem`

### I5-B8 单元测试

- `BlockExtractorTest.java`：各种锚点格式、跨行段落、表格块
- `PositionResolverTest.java`：多 block 合并位置计算
- `FixedSizeChunkStrategyTest.java`：分块大小/overlap 边界
- `ChunkingApplicationTest.java`：端到端 mock 测试（mock 向量化 + ES 写入）

**验收标准**：知识库文档入库后，ES 中 chunk 文档含正确 `position` 字段（page/bbox）；`POST /rechunk` 后旧 chunk 被替换，位置信息更新。

---

## 迭代 6：前端 PDF 定位高亮

**目标**：用户在 AgentChat 中点击 KB 类型引用角标，弹出 PDF 查看器，自动跳转到对应页码并高亮显示 bbox 区域。

### I6-F1 原始文件下载接口（Java）

- `GET /kb/{kbId}/documents/{docId}/original`（I4-B5 已预留）
- 新增 `GET /kb/files/{docId}`：按 `CitationItem.sourceFileId` 直接获取文件（跨 KB 通用），返回文件流 + Content-Type

### I6-F2 PdfViewer 组件

- `src/components/PdfViewer.vue`：
  - Props：`fileUrl`（PDF 文件 URL）/ `page`（跳转页码）/ `bboxes`（高亮区域列表）/ `pageWidth` / `pageHeight`
  - 使用 `PDF.js`（`pdfjs-dist`）渲染指定页
  - Canvas 叠加层：遍历 `bboxes`，绘制半透明黄色矩形（`fillStyle: rgba(255,220,0,0.3)`，`strokeStyle: #f59e0b`）
  - bbox 坐标系：positions.json 已统一为 top-left，直接用于 Canvas 绘制（需按渲染缩放比例换算像素）

### I6-F3 CitationDrawer 扩展（KB 类型）

- `src/components/CitationDrawer.vue` 中 KB 类型新增"查看原文"按钮
- 点击后：
  1. 调用 `/kb/files/{sourceFileId}` 获取文件 URL
  2. 打开 `PdfViewer` 对话框，传入 `page` + `bboxes`

### I6-F4 多块高亮支持

- `PdfViewer` 支持传入 `bboxes` 数组（跨多块）
- 若 chunk 的 `bboxes` 中含多页 block，显示首页高亮并提供"下一处"翻页按钮

### I6-F5 非 PDF 文件兜底处理

- `file_type` 为 docx/pptx 时：提示"该文件类型暂不支持定位，可下载查看"，提供下载按钮
- `file_type` 为 xlsx 时：显示 Sheet 名（`heading_path` 中含 Sheet 信息）

**验收标准**：
1. AgentChat 中点击 KB 引用角标 → CitationDrawer 展示来源段落文字
2. 点击"查看原文" → 弹出 PDF 查看器，跳转正确页码
3. bbox 区域呈现半透明高亮框，位置与文档内容匹配

---

## 依赖关系

```
I1 → I2 → I3
I1 → I2 → I4 → I5 → I6
                I5 依赖 I4 的持久化三份文件
                I6 依赖 I5 的 CitationItem 位置字段
```

## 开发建议

- **I1~I3 优先用 CPU + Docling 验证端到端链路**，MinerU 在 I3 单独接入，不阻塞 I4/I5
- **I4 可与 I3 并行**：Java 侧 Mock DocParserClient 即可开始开发，不依赖真实解析
- **I5-B7（CitationItem 扩展）** 是唯一需要同时改动 domain 层和 RAG 工具的节点，需注意不破坏现有引用功能
- **I6 的 PDF.js 集成**建议用 `pdfjs-dist` npm 包直接集成，避免 iframe 跨域问题
