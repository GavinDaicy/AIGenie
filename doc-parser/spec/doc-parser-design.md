# doc-parser 服务设计文档

## 1. 概述

`doc-parser` 是一个独立的 Python 微服务，基于 **MinerU + Docling 双引擎**，面向 Java 应用提供文档解析能力。

**输入**：PDF、Word、PPT、图片等原始文档  
**输出**：带 block_id 锚点的 Markdown 文本 + 独立的位置元数据 JSON  
**职责边界**：仅负责解析，不做分块和向量化

核心设计目标：解析结果携带每个文本块在原始文档中的**精确位置**（页码 + 像素坐标），供后续 RAG 召回后能反查到原始文档的对应位置并高亮展示。

---

## 2. 整体架构

```
┌──────────────────────────────────────────────────────────┐
│                    Java Spring Boot                       │
│  上传文件  POST /api/v1/parse                             │
│  轮询状态  GET  /api/v1/jobs/{job_id}                     │
│  取结果    GET  /api/v1/jobs/{job_id}/result              │
└───────────────────────┬──────────────────────────────────┘
                        │ HTTP (REST)
┌───────────────────────▼──────────────────────────────────┐
│                  FastAPI 网关层                            │
│  - 文件接收 & 校验                                        │
│  - 入队 Celery Task                                       │
│  - 状态查询（读 Redis）                                   │
│  - 结果返回（读文件存储）                                 │
└───────────────────────┬──────────────────────────────────┘
                        │ Celery Task
┌───────────────────────▼──────────────────────────────────┐
│                   Celery Worker                           │
│  ┌─────────────┐    ┌──────────────┐    ┌─────────────┐  │
│  │ ParserRouter│───▶│ MinerU Parser│    │Docling Parse│  │
│  │ 路由选择    │    │ 中文PDF/图片 │    │ Word/PPT/表 │  │
│  └─────────────┘    └──────────────┘    └─────────────┘  │
│                    ┌──────────────┐                       │
│                    │ Excel Parser │ (pandas)              │
│                    └──────────────┘                       │
└───────────────────────┬──────────────────────────────────┘
                        │
┌──────────┐   ┌────────▼─────────┐
│  Redis   │   │   文件存储        │
│ 任务状态 │   │ 原始文件          │
│ 任务队列 │   │ result.md         │
└──────────┘   │ positions.json   │
               └──────────────────┘
```

---

## 3. 文档解析引擎路由策略

### 3.1 路由规则

```
文件扩展名          语言提示        → 选用引擎
─────────────────────────────────────────────
.pdf               zh / auto       → MinerU
.pdf               en              → Docling
.docx / .doc                       → Docling
.pptx / .ppt                       → Docling
.html / .htm                       → Docling
.md / .adoc                        → Docling
.xlsx / .xls / .csv                → Excel Parser (pandas)
.png / .jpg / .jpeg / .tiff        zh / auto → MinerU
.png / .jpg / .jpeg / .tiff        en        → Docling
其他                                → Docling (兜底)
```

### 3.2 各引擎优势说明

| 引擎 | 擅长场景 | GPU 需求 |
|------|---------|---------|
| **MinerU** | 中文 PDF、扫描件、中文图片 OCR | 强依赖（CPU 极慢）|
| **Docling** | 表格提取(TableFormer)、Word/PPT、英文 PDF、公式 | CPU 可用（较慢）|
| **Excel Parser** | 多 Sheet 表格、数值型数据 | 无 |

### 3.3 路由逻辑（伪代码）

```python
def select_parser(filename: str, language_hint: str = "auto") -> str:
    ext = Path(filename).suffix.lower()

    if ext in (".docx", ".doc", ".pptx", ".ppt", ".html", ".htm", ".md"):
        return "docling"

    if ext in (".xlsx", ".xls", ".csv"):
        return "excel"

    if ext == ".pdf":
        return "mineru" if language_hint in ("zh", "auto") else "docling"

    if ext in (".png", ".jpg", ".jpeg", ".tiff", ".bmp", ".webp"):
        return "mineru" if language_hint in ("zh", "auto") else "docling"

    return "docling"  # 兜底
```

---

## 4. API 设计

### 4.1 提交解析任务

支持两种文件输入方式：

#### 方式 A：二进制上传（本地文件）

```
POST /api/v1/parse
Content-Type: multipart/form-data
```

| 参数 | 类型 | 必填 | 说明 |
|-----|------|------|------|
| `file` | binary | ✅（与 file_url 二选一）| 原始文档文件二进制内容 |
| `language` | string | ❌ | 语言提示：`zh`/`en`/`auto`（默认 `auto`）|
| `parser` | string | ❌ | 强制指定引擎：`auto`/`mineru`/`docling`（默认 `auto`）|
| `callback_url` | string | ❌ | 完成后回调通知调用方的 URL |

#### 方式 B：URL 输入（云存储文件）

```
POST /api/v1/parse
Content-Type: application/json
```

```json
{
  "file_url": "https://oss.example.com/bucket/2024年报.pdf",
  "filename": "2024年报.pdf",
  "language": "zh",
  "parser": "auto",
  "callback_url": "https://api.example.com/kb/parse-callback"
}
```

| 参数 | 类型 | 必填 | 说明 |
|-----|------|------|------|
| `file_url` | string | ✅（与 file 二选一）| 文件可访问的 HTTP/HTTPS URL（预签名 URL 或公开 URL）|
| `filename` | string | ✅ | 文件名（含扩展名，用于路由引擎和存储）|
| `language` | string | ❌ | 同方式 A |
| `parser` | string | ❌ | 同方式 A |
| `callback_url` | string | ❌ | 同方式 A |

**Response 202（两种方式相同）：**

```json
{
  "job_id": "job-a1b2c3d4",
  "status": "queued",
  "filename": "2024年报.pdf",
  "estimated_seconds": 30
}
```

### 4.2 查询任务状态

```
GET /api/v1/jobs/{job_id}
```

**Response 200：**

```json
{
  "job_id": "job-a1b2c3d4",
  "status": "queued | processing | done | failed",
  "progress": 0.65,
  "filename": "2024年报.pdf",
  "parser_used": "mineru",
  "page_count": 45,
  "block_count": 312,
  "error": null,
  "created_at": "2024-04-07T10:00:00Z",
  "finished_at": "2024-04-07T10:00:42Z"
}
```

### 4.3 获取解析结果（完整 JSON）

```
GET /api/v1/jobs/{job_id}/result?mode=inline
```

通过 `mode` 查询参数控制结果内容的返回方式：

| `mode` 值 | 说明 | 适用场景 |
|-----------|------|---------|
| `inline`（默认）| 将 markdown 文本和 positions JSON 直接内嵌在响应体中 | 本地存储、小文件 |
| `url` | 仅返回文件的下载 URL（预签名 URL 或内部接口地址）| 云对象存储、大文件 |

**mode=inline 响应（直接内嵌内容）：**

```json
{
  "job_id": "job-a1b2c3d4",
  "mode": "inline",
  "markdown": "<!-- @block:b-0001 -->\n# 第一章 财务概览\n\n<!-- @block:b-0002 -->\n本季度营收...",
  "positions": {
    "source_file": "2024年报.pdf",
    "source_type": "pdf",
    "parser": "mineru",
    "total_pages": 45,
    "page_width": 595.3,
    "page_height": 841.9,
    "blocks": [ ... ]
  }
}
```

**mode=url 响应（返回下载链接）：**

```json
{
  "job_id": "job-a1b2c3d4",
  "mode": "url",
  "markdown_url": "https://oss.example.com/jobs/job-a1b2c3d4/result.md?X-Signature=...",
  "positions_url": "https://oss.example.com/jobs/job-a1b2c3d4/positions.json?X-Signature=...",
  "url_expires_at": "2024-04-07T11:00:00Z"
}
```

> 本地存储模式下，`markdown_url` / `positions_url` 为内部接口地址（`/api/v1/jobs/{id}/markdown`），无过期时间。

### 4.4 分别下载文件

同样支持两种响应方式，通过 `mode` 参数控制：

```
GET /api/v1/jobs/{job_id}/markdown?mode=inline   -> 直接返回 result.md 文件流（Content-Type: text/markdown）
GET /api/v1/jobs/{job_id}/markdown?mode=url      -> 返回 {"url": "...", "expires_at": "..."}

GET /api/v1/jobs/{job_id}/positions?mode=inline  -> 直接返回 positions.json 文件流（Content-Type: application/json）
GET /api/v1/jobs/{job_id}/positions?mode=url     -> 返回 {"url": "...", "expires_at": "..."}
```

默认 `mode=inline`，兼容不传参数的客户端。

### 4.5 错误响应

```json
{
  "error": "PARSE_FAILED",
  "message": "MinerU 解析超时，文件页数过多",
  "job_id": "job-a1b2c3d4"
}
```

---

## 5. 输出格式规范

### 5.1 Markdown 格式（result.md）

每个文本块前插入 HTML 注释锚点 `<!-- @block:{block_id} -->`，锚点与正文之间无空行：

```markdown
<!-- @block:b-0001 -->
# 第一章 财务概览

<!-- @block:b-0002 -->
本季度营收同比增长25%，达到历史新高。各业务线均实现正增长，其中云计算业务增速最快。

<!-- @block:b-0003 -->
## 1.1 核心财务指标

<!-- @block:b-0004 -->
| 指标 | Q1 2024 | Q2 2024 | Q3 2024 | 同比变化 |
|------|---------|---------|---------|---------|
| 营收（亿元）| 100.2 | 112.5 | 125.8 | +25.5% |
| 净利润（亿元）| 18.3 | 21.0 | 24.6 | +34.4% |

<!-- @block:b-0005 -->
![图1-1 营收趋势图](images/job-a1b2c3d4/img-0001.png)
```

**设计要点：**
- `block_id` 格式：`b-{序号:04d}`，全局唯一，从 `b-0001` 顺序递增
- 跨页内容：block_id 在 positions.json 中对应多个 page/bbox 条目
- 图片：保存至 `images/{job_id}/img-{n}.png`，MD 中引用相对路径
- 表格：整张表格作为一个 block，不对每行单独标注

### 5.2 位置元数据格式（positions.json）

```json
{
  "source_file": "2024年报.pdf",
  "source_type": "pdf",
  "parser": "mineru",
  "total_pages": 45,
  "page_width": 595.3,
  "page_height": 841.9,
  "coordinate_system": "top-left",
  "blocks": [
    {
      "block_id": "b-0001",
      "type": "heading",
      "level": 1,
      "text": "第一章 财务概览",
      "page": 3,
      "bbox": [72.0, 120.5, 540.0, 145.0],
      "heading_path": "第一章 财务概览"
    },
    {
      "block_id": "b-0002",
      "type": "paragraph",
      "text": "本季度营收同比增长25%，达到历史新高。",
      "page": 3,
      "bbox": [72.0, 155.0, 540.0, 195.0],
      "heading_path": "第一章 财务概览"
    },
    {
      "block_id": "b-0003",
      "type": "heading",
      "level": 2,
      "text": "1.1 核心财务指标",
      "page": 3,
      "bbox": [72.0, 210.0, 540.0, 230.0],
      "heading_path": "第一章 财务概览"
    },
    {
      "block_id": "b-0004",
      "type": "table",
      "text": "| 指标 | Q1 2024 | ...",
      "page": 3,
      "bbox": [72.0, 245.0, 540.0, 420.0],
      "heading_path": "第一章 财务概览 > 1.1 核心财务指标"
    },
    {
      "block_id": "b-0005",
      "type": "image",
      "text": "图1-1 营收趋势图",
      "page": 4,
      "bbox": [100.0, 80.0, 480.0, 350.0],
      "image_path": "images/job-a1b2c3d4/img-0001.png",
      "heading_path": "第一章 财务概览 > 1.1 核心财务指标"
    }
  ]
}
```

### 5.3 Block type 枚举

| type | 含义 |
|------|------|
| `heading` | 标题，附 `level` 字段（1~6）|
| `paragraph` | 正文段落 |
| `table` | 表格，text 为 Markdown 表格文本 |
| `image` | 图片，附 `image_path` 字段 |
| `list` | 列表项（有序/无序）|
| `code` | 代码块 |
| `formula` | 数学公式，text 为 LaTeX |

### 5.4 坐标系说明

- 统一使用**左上角为原点**的坐标系（top-left）
- `bbox` 格式：`[x0, y0, x1, y1]`，单位为**像素点（pt）**
- MinerU 原生坐标为左上角原点，直接使用
- Docling `BoundingBox` 原生坐标为左下角（PDF 标准），**在 Parser 内统一转换**：
  ```python
  y0_converted = page_height - bbox.b  # 原 bottom → 新 top
  y1_converted = page_height - bbox.t  # 原 top → 新 bottom
  ```

---

## 6. RAG 召回反查链路设计

### 6.1 完整数据流

```
原始文档
    │
    ▼ doc-parser（Python 微服务）
┌─────────────────────────────┐
│ result.md   (含 block 锚点) │
│ positions.json (块坐标信息) │
└──────────────┬──────────────┘
               │ query-genie-server 接收解析结果
               ▼
┌─────────────────────────────────────────┐
│  query-genie-server 文档存储层           │  ← ★ 持久化
│  knowledge_document 表：                │
│  - original_file（对象存储）            │
│  - result_md（对象存储）                │
│  - positions_json（对象存储）  ★        │
│  - parser / total_pages / status        │
└──────────────┬──────────────────────────┘
               │ 分块处理（读 result_md + positions_json）
               ▼
┌─────────────────────────────┐
│ Chunk（存入 ES 向量库）      │
│ {                           │
│   chunk_id: "ck-001",       │
│   content:  "...",          │
│   source_file_id: "file-x", │
│   heading_path: "第一章...", │
│   position: {               │  ← 坐标直写（方案 C）
│     representative_page: 3, │
│     representative_bbox:[], │
│     bboxes: [...]           │
│   }                         │
│ }                           │
└──────────────┬──────────────┘
               │ RAG 召回
               ▼
┌─────────────────────────────┐
│ 命中 Chunk → 直接取位置     │
│ position.representative_    │
│ page=3, bbox=[72,155,...]   │
└──────────────┬──────────────┘
               │ 前端展示
               ▼
┌─────────────────────────────┐
│ PDF.js 打开原始 PDF          │
│ 跳转到 page=3               │
│ 叠加 Canvas 高亮 bbox 区域  │
└─────────────────────────────┘
```

**分块策略变更时**：无需重新解析原始文档，直接读取已持久化的 `result_md` + `positions_json`，重新执行分块入库即可。

### 6.2 Java 侧分块时如何提取 block_id

Java 侧读取 `result.md` 时，通过正则提取 block_id 与文本内容的对应关系：

```java
// 正则：匹配 <!-- @block:b-XXXX --> 标记
Pattern BLOCK_PATTERN = Pattern.compile("<!--\\s*@block:(b-\\d+)\\s*-->");

// 按 block 锚点切分 MD，每个 block 携带 block_id
// 若多个相邻 block 合并为一个 chunk，则 chunk.block_ids 包含多个 block_id
```

### 6.3 位置元数据存储方案对比

有三种存储策略，各有取舍：

#### 方案 A：独立 ES 索引（按 block_id 存储）

```
doc_blocks 索引（单独）
{ block_id: "b-0002", file_id: "x", page: 3, bbox: [...], ... }

chunk ES 文档
{ chunk_id: "ck-001", content: "...", block_ids: ["b-0002", "b-0004"] }

查询链路：命中 chunk → 用 block_ids 查 doc_blocks → 得到位置（2次查询）
```

#### 方案 B：关系型 DB 表

```sql
CREATE TABLE doc_block_position (
  file_id VARCHAR, block_id VARCHAR, page INT,
  bbox VARCHAR, type VARCHAR, heading_path VARCHAR
);

查询链路：命中 chunk → 用 block_ids 查 DB → 得到位置（跨系统 2次查询）
```

#### 方案 C：分块时计算坐标，直接写入 Chunk ES 文档 ✅ 推荐

分块入库时，一次性从 `positions.json` 中解析出该 chunk 覆盖的所有 block 位置，直接存入 chunk 文档，**RAG 召回后无需任何额外查询**。

```json
{
  "chunk_id": "ck-001",
  "content": "本季度营收同比增长25%，达到历史新高...",
  "source_file_id": "file-xyz",
  "source_file_name": "2024年报.pdf",
  "heading_path": "第一章 财务概览",
  "position": {
    "representative_page": 3,
    "representative_bbox": [72.0, 155.0, 540.0, 195.0],
    "page_start": 3,
    "page_end": 3,
    "bboxes": [
      { "page": 3, "bbox": [72.0, 155.0, 540.0, 195.0], "block_id": "b-0002" },
      { "page": 3, "bbox": [72.0, 245.0, 540.0, 420.0], "block_id": "b-0004" }
    ]
  },
  "embedding": [...]
}
```

- `representative_page` + `representative_bbox`：用于 PDF.js 跳转（取 chunk 第一个 block 的位置）
- `bboxes`：完整列表，用于高亮 chunk 内所有文本块区域

#### 三方案横向对比

| 维度 | 方案 A（独立 ES）| 方案 B（关系型 DB）| 方案 C（坐标写入 Chunk）|
|-----|---------------|-----------------|----------------------|
| **RAG 召回后查询步骤** | 2 步 | 2 步（跨系统）| **1 步** |
| **响应延迟** | 中 | 高（跨系统）| **最低** |
| **实现复杂度** | 中 | 中 | **最低** |
| **数据冗余** | 无 | 无 | 有（可忽略，每 chunk 仅几个 bbox）|
| **分块策略变更** | 无需重建 block 索引 | 无需重建 | 重新分块入库（无需重新解析 ★）|
| **依赖组件** | 双 ES 索引 | ES + DB | **单 ES 索引** |
| **位置精确度** | block 级 | block 级 | chunk 级（含所有 block）|

> ★ 前提：`positions.json` 已由 `query-genie-server` 持久化存储（见第 6.4 节），变更分块策略时直接读取已存储的 `positions.json` 重新分块，**无需重新调用 doc-parser 解析**。

#### 选型结论

**推荐方案 C + positions.json 持久化**，原因：

1. **RAG 场景下单次召回即可获得位置**，无需二次查询，高亮响应最快
2. 实现最简单，不引入额外存储依赖
3. 数据冗余量极小（每个 chunk 通常 1~3 个 block，bbox 数据量字节级）
4. **持久化 positions.json 后，分块策略变更的代价仅为重新入库 chunk**，不触发重新解析（解析是最耗时的步骤）
5. `representative_page + representative_bbox` 满足 90% 的跳转场景，`bboxes` 覆盖跨块高亮

**例外情况**：若业务需要按原始文档 block 粒度检索（如"找所有含某表格的 chunk"），可同时维护方案 A 的 block 索引作为补充，但非必须。

### 6.4 query-genie-server 文档存储模型（★ positions.json 持久化）

`query-genie-server` 接收 doc-parser 解析结果后，需将以下三份文件**全部持久化**，作为知识库文档的完整存档：

```
对象存储（MinIO / 本地 objectStore）
└── kb-documents/
    └── {file_id}/
        ├── original.pdf          ← 原始上传文件
        ├── result.md             ← 带 block 锚点的 Markdown
        └── positions.json        ← ★ 块坐标元数据（必须保留）
```

#### DB 表设计（knowledge_document）

```sql
CREATE TABLE knowledge_document (
    id              VARCHAR(36) PRIMARY KEY,
    kb_id           VARCHAR(36)  NOT NULL,           -- 所属知识库
    file_name       VARCHAR(255) NOT NULL,           -- 原始文件名
    file_type       VARCHAR(20)  NOT NULL,           -- pdf/docx/pptx/xlsx/image
    original_path   VARCHAR(500) NOT NULL,           -- 原始文件对象存储路径
    markdown_path   VARCHAR(500),                    -- result.md 对象存储路径
    positions_path  VARCHAR(500),                    -- positions.json 对象存储路径 ★
    parser          VARCHAR(20),                     -- mineru/docling/excel
    total_pages     INT,
    block_count     INT,
    parse_status    VARCHAR(20)  NOT NULL DEFAULT 'pending',
                                                     -- pending/parsing/done/failed
    chunk_status    VARCHAR(20)  NOT NULL DEFAULT 'pending',
                                                     -- pending/chunking/done/failed
    parse_job_id    VARCHAR(64),                     -- doc-parser 返回的 job_id
    created_at      DATETIME     NOT NULL,
    updated_at      DATETIME     NOT NULL
);
```

#### 处理状态流转

```
上传文件
    │
    ▼ parse_status = parsing
调用 doc-parser → 轮询/回调
    │
    ▼ parse_status = done
持久化 result.md + positions.json → 更新 markdown_path / positions_path
    │
    ▼ chunk_status = chunking
读取 result.md + positions.json → 分块 → 写入 ES
    │
    ▼ chunk_status = done
知识库可用
```

#### 重新分块接口（分块策略变更时使用）

```
POST /knowledge/{kb_id}/documents/{doc_id}/rechunk
Body: { "chunk_size": 512, "chunk_overlap": 50, "strategy": "semantic" }
```

处理逻辑：
1. 读取 `knowledge_document.positions_path` → 下载 `positions.json`
2. 读取 `knowledge_document.markdown_path` → 下载 `result.md`
3. 按新策略重新分块，计算每个 chunk 的 `position`（从 positions.json 查询 block 坐标）
4. 删除该文档旧 chunk（按 `source_file_id` 过滤），重新写入 ES
5. 更新 `chunk_status = done`

**全程不调用 doc-parser**，解析结果复用。

---

## 7. 与 Java 侧的集成

### 7.1 Java 调用示例（Spring Boot）

**场景 A：本地存储 — binary 上传 + inline 结果（开发/私有化部署）**

```java
// 1. binary 上传
MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
body.add("file", new FileSystemResource(uploadedFile));
body.add("language", "zh");

String jobId = restTemplate.postForObject(
    docParserUrl + "/api/v1/parse",
    new HttpEntity<>(body, multipartHeaders),
    ParseJobResponse.class
).getJobId();

// 2. 轮询状态
// ... 见 7.2

// 3. inline 模式直接拿到内容
ParseResultInline result = restTemplate.getForObject(
    docParserUrl + "/api/v1/jobs/" + jobId + "/result?mode=inline",
    ParseResultInline.class
);
String markdown = result.getMarkdown();          // 直接使用 markdown 文本
PositionsJson positions = result.getPositions(); // 直接使用 positions 对象
```

**场景 B：云存储 — URL 输入 + URL 结果（云部署 / 大文件）**

```java
// 1. 上传原始文件到 OSS，获取预签名 URL（Java 侧通过 ObjectStore 完成）
String fileUrl = objectStore.generatePresignedUrl("kb-documents/" + docId + "/original.pdf");

// 2. 提交 URL 给 doc-parser
ParseUrlRequest req = new ParseUrlRequest();
req.setFileUrl(fileUrl);
req.setFilename("2024年报.pdf");
req.setLanguage("zh");
req.setCallbackUrl(callbackBaseUrl + "/kb/parse-callback");

String jobId = restTemplate.postForObject(
    docParserUrl + "/api/v1/parse",
    new HttpEntity<>(req, jsonHeaders),
    ParseJobResponse.class
).getJobId();

// 3. 回调通知到达后，通过 url 模式获取结果 URL
ParseResultUrl result = restTemplate.getForObject(
    docParserUrl + "/api/v1/jobs/" + jobId + "/result?mode=url",
    ParseResultUrl.class
);
// result.getMarkdownUrl()  → 预签名 URL，直接存入 knowledge_document.markdown_path
// result.getPositionsUrl() → 预签名 URL，直接存入 knowledge_document.positions_path
// 本地存储模式下这两个 URL 是内部接口地址，需调用接口下载后再存储
```

### 7.2 推荐的回调模式（避免长轮询）

配置 `callback_url` 参数，解析完成后 `doc-parser` 主动回调 Java 接口：

```
POST {callback_url}
{
  "job_id": "job-a1b2c3d4",
  "status": "done",
  "markdown_url": "http://doc-parser/api/v1/jobs/job-a1b2c3d4/markdown",
  "positions_url": "http://doc-parser/api/v1/jobs/job-a1b2c3d4/positions"
}
```

> 云存储模式下 `markdown_url` / `positions_url` 为 OSS 预签名 URL，Java 侧无需再访问 doc-parser，直接将 URL 存入 DB 即可（有效期内）。

---

## 8. 项目目录结构

```
doc-parser/
├── spec/
│   └── doc-parser-design.md        # 本文档
├── app/
│   ├── main.py                     # FastAPI 入口
│   ├── api/
│   │   ├── routes/
│   │   │   ├── parse.py            # POST /api/v1/parse
│   │   │   └── jobs.py             # GET  /api/v1/jobs/{id}
│   │   └── schemas/
│   │       ├── request.py          # 请求 DTO
│   │       └── response.py         # 响应 DTO
│   ├── core/
│   │   ├── config.py               # 配置（从环境变量读取）
│   │   └── celery_app.py           # Celery + Redis 初始化
│   ├── parsers/
│   │   ├── base.py                 # 抽象 Parser 接口
│   │   ├── router.py               # 引擎路由逻辑
│   │   ├── mineru_parser.py        # MinerU 封装
│   │   ├── docling_parser.py       # Docling 封装
│   │   └── excel_parser.py         # pandas 封装
│   ├── tasks/
│   │   └── parse_task.py           # Celery 任务定义
│   ├── models/
│   │   ├── job.py                  # Job 状态模型
│   │   └── position.py             # Block / Position Pydantic 模型
│   └── storage/
│       ├── base.py                 # FileStore 抽象接口
│       ├── local_store.py          # 本地文件系统实现
│       └── cloud_store.py          # 云对象存储实现（S3/OSS/MinIO）
├── tests/
│   ├── test_router.py
│   ├── test_mineru_parser.py
│   ├── test_docling_parser.py
│   └── test_api.py
├── Dockerfile                      # GPU 版（含 CUDA）
├── Dockerfile.cpu                  # CPU 版（Docling only）
├── docker-compose.yml              # 本地开发：FastAPI + Celery + Redis
├── requirements.txt
├── requirements.cpu.txt            # CPU 精简版依赖
└── README.md
```

---

## 9. 存储策略兼容设计

### 9.1 两种策略对比

| 维度 | 本地存储（LocalStore）| 云对象存储（CloudStore）|
|-----|---------------------|----------------------|
| **适用场景** | 开发环境、私有化部署 | 云部署、分布式多实例 |
| **文件输入** | binary 上传（multipart）| URL 输入（file_url）|
| **结果获取** | inline 模式（内嵌内容）| url 模式（预签名 URL）|
| **Worker 访问文件** | 直接读本地路径 | 从 URL 下载 |
| **Java 侧获取结果** | 调用接口下载文件字节 | 直接存储预签名 URL |
| **依赖组件** | 无额外依赖 | S3 / 阿里云 OSS / MinIO |
| **多 Worker 扩展** | ⚠️ 需挂载共享存储卷 | ✅ 天然支持 |

### 9.2 FileStore 抽象接口（Python）

```python
# app/storage/base.py
from abc import ABC, abstractmethod

class FileStore(ABC):

    @abstractmethod
    def save_upload(self, job_id: str, filename: str, data: bytes) -> str:
        """保存原始上传文件，返回内部存储路径"""

    @abstractmethod
    def save_result(self, job_id: str, filename: str, data: bytes) -> str:
        """保存解析结果文件，返回内部存储路径"""

    @abstractmethod
    def read(self, path: str) -> bytes:
        """读取文件内容"""

    @abstractmethod
    def get_download_url(self, path: str, expires_seconds: int = 3600) -> str:
        """
        获取文件访问 URL。
        本地存储：返回内部接口地址 /api/v1/jobs/{id}/markdown
        云存储：返回预签名 URL（含过期时间）
        """

    @abstractmethod
    def download_from_url(self, url: str) -> bytes:
        """从 URL 下载文件内容（用于 file_url 输入模式）"""
```

### 9.3 本地存储实现（LocalStore）

```python
# app/storage/local_store.py
class LocalFileStore(FileStore):

    def __init__(self, storage_path: str, base_url: str):
        self.storage_path = storage_path  # 本地目录，如 /data/jobs
        self.base_url = base_url          # 服务自身 URL，如 http://localhost:8100

    def save_result(self, job_id, filename, data):
        path = f"{self.storage_path}/{job_id}/{filename}"
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "wb") as f:
            f.write(data)
        return f"{job_id}/{filename}"   # 相对路径作为内部 path

    def get_download_url(self, path, expires_seconds=3600):
        # 返回内部接口 URL，由 FastAPI /files/{path} 端点提供服务
        # 无过期时间（服务存活期间永久有效）
        job_id, filename = path.split("/", 1)
        if filename == "result.md":
            return f"{self.base_url}/api/v1/jobs/{job_id}/markdown"
        return f"{self.base_url}/api/v1/jobs/{job_id}/positions"

    def download_from_url(self, url):
        import httpx
        return httpx.get(url, timeout=60).content
```

### 9.4 云对象存储实现（CloudStore）

```python
# app/storage/cloud_store.py
class CloudFileStore(FileStore):
    """
    兼容 S3 协议的云存储实现（AWS S3 / 阿里云 OSS / 华为云 OBS / MinIO）
    使用 boto3 客户端，MinIO 只需配置 endpoint_url 即可
    """

    def __init__(self, bucket: str, prefix: str, s3_client):
        self.bucket = bucket
        self.prefix = prefix        # 如 "doc-parser-jobs"
        self.s3 = s3_client

    def save_result(self, job_id, filename, data):
        key = f"{self.prefix}/{job_id}/{filename}"
        self.s3.put_object(Bucket=self.bucket, Key=key, Body=data)
        return key

    def get_download_url(self, path, expires_seconds=3600):
        # 生成预签名 URL（含签名和过期时间）
        return self.s3.generate_presigned_url(
            "get_object",
            Params={"Bucket": self.bucket, "Key": path},
            ExpiresIn=expires_seconds,
        )

    def download_from_url(self, url):
        import httpx
        return httpx.get(url, timeout=120).content
```

### 9.5 存储策略选择配置

```python
# app/core/config.py
class Settings(BaseSettings):
    storage_type: str = "local"           # local | cloud
    storage_path: str = "/data/jobs"      # 仅 local 模式使用
    service_base_url: str = "http://localhost:8100"  # 仅 local 模式使用

    # 云存储配置（storage_type=cloud 时生效）
    cloud_endpoint: str = ""              # MinIO: http://minio:9000 / S3: 留空
    cloud_bucket: str = ""
    cloud_prefix: str = "doc-parser-jobs"
    cloud_access_key: str = ""
    cloud_secret_key: str = ""
    cloud_region: str = "us-east-1"
    cloud_url_expires: int = 3600         # 预签名 URL 有效期（秒）
```

环境变量示例：

```bash
# 本地存储（默认）
STORAGE_TYPE=local
STORAGE_PATH=/data/jobs
SERVICE_BASE_URL=http://doc-parser:8100

# MinIO（私有化云存储）
STORAGE_TYPE=cloud
CLOUD_ENDPOINT=http://minio:9000
CLOUD_BUCKET=doc-parser
CLOUD_ACCESS_KEY=minioadmin
CLOUD_SECRET_KEY=minioadmin

# 阿里云 OSS
STORAGE_TYPE=cloud
CLOUD_ENDPOINT=https://oss-cn-hangzhou.aliyuncs.com
CLOUD_BUCKET=my-kb-bucket
CLOUD_ACCESS_KEY=xxx
CLOUD_SECRET_KEY=xxx
CLOUD_REGION=cn-hangzhou
```

### 9.6 两种策略的完整调用链路

```
┌──────────────────────────────────────────────────────────┐
│               本地存储策略（开发/私有化）                  │
│                                                           │
│  Java → binary 上传 → doc-parser 保存到本地磁盘           │
│  解析完成 → callback → Java 调 /result?mode=inline         │
│  Java 收到 markdown 文本 → 直接写 ObjectStore             │
└──────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────┐
│               云存储策略（云部署/生产）                    │
│                                                           │
│  Java → 上传文件到 OSS → 生成预签名 URL                   │
│       → 提交 file_url 给 doc-parser                       │
│  Worker → 从 URL 下载文件 → 解析 → 结果上传 OSS           │
│  解析完成 → callback（含 markdown_url/positions_url）      │
│  Java → 直接将两个预签名 URL 存入 DB（无需下载内容）      │
└──────────────────────────────────────────────────────────┘
```

---

## 10. 部署方案

### 10.1 docker-compose（本地存储 / CPU 模式）

```yaml
version: "3.9"
services:
  redis:
    image: redis:7-alpine
    ports: ["6379:6379"]

  doc-parser-api:
    build:
      context: .
      dockerfile: Dockerfile.cpu
    ports: ["8100:8100"]
    environment:
      - REDIS_URL=redis://redis:6379/0
      - STORAGE_PATH=/data/jobs
      - WORKER_MODE=false
    volumes:
      - ./data:/data
    depends_on: [redis]

  doc-parser-worker:
    build:
      context: .
      dockerfile: Dockerfile.cpu
    environment:
      - REDIS_URL=redis://redis:6379/0
      - STORAGE_PATH=/data/jobs
      - WORKER_MODE=true
    volumes:
      - ./data:/data
    depends_on: [redis]
    command: celery -A app.core.celery_app worker --loglevel=info -Q parse
```

### 10.2 GPU 生产部署（MinerU 场景）

```yaml
  doc-parser-worker-gpu:
    build:
      context: .
      dockerfile: Dockerfile        # 含 CUDA 11.8
    deploy:
      resources:
        reservations:
          devices:
            - driver: nvidia
              count: 1
              capabilities: [gpu]
    environment:
      - CUDA_VISIBLE_DEVICES=0
      - REDIS_URL=redis://redis:6379/0
    command: celery -A app.core.celery_app worker -Q parse -c 1
```

### 10.3 资源需求参考

| 部署模式 | CPU | 内存 | GPU |
|---------|-----|------|-----|
| CPU-only（Docling）| 4 核 | 8GB | 无 |
| GPU（MinerU + Docling）| 8 核 | 16GB | T4 16GB × 1 |
| 生产推荐（混合）| 8 核 | 32GB | T4 × 1（MinerU Worker）+ CPU Worker（Docling）|

### 10.4 云存储模式附加 docker-compose 配置

在 `doc-parser-api` 和 `doc-parser-worker` 的 `environment` 中增加：

```yaml
  environment:
    - STORAGE_TYPE=cloud
    - CLOUD_ENDPOINT=http://minio:9000
    - CLOUD_BUCKET=doc-parser
    - CLOUD_ACCESS_KEY=minioadmin
    - CLOUD_SECRET_KEY=minioadmin
```

若使用真实云服务（OSS/S3/OBS），去掉 `CLOUD_ENDPOINT`，配置对应 `CLOUD_REGION`。

### 10.5 Java 侧配置

```yaml
# application.yml
app:
  doc-parser:
    url: ${DOC_PARSER_URL:http://localhost:8100}
    timeout-seconds: 300
    poll-interval-ms: 2000
    storage-mode: ${DOC_PARSER_STORAGE_MODE:local}  # local | cloud
```

---

## 11. 依赖清单

### requirements.txt（GPU 版）

```
# Web 框架 & 任务队列
fastapi==0.111.0
uvicorn[standard]==0.29.0
celery==5.4.0
redis==5.0.4
python-multipart==0.0.9

# 文档解析
magic-pdf[full]==0.9.3          # MinerU
docling==2.5.0                  # Docling
pandas==2.2.2                   # Excel
openpyxl==3.1.2                 # Excel .xlsx
tabulate==0.9.0                 # DataFrame.to_markdown()

# 工具
pydantic==2.7.0
pydantic-settings==2.2.1
httpx==0.27.0                   # callback_url 回调 / file_url 下载
python-magic==0.4.27            # MIME 类型检测
boto3==1.34.0                   # 云存储（S3/OSS/MinIO，storage_type=cloud 时使用）
```

### requirements.cpu.txt（CPU 精简版，仅 Docling）

```
fastapi==0.111.0
uvicorn[standard]==0.29.0
celery==5.4.0
redis==5.0.4
python-multipart==0.0.9
docling==2.5.0
pandas==2.2.2
openpyxl==3.1.2
tabulate==0.9.0
pydantic==2.7.0
pydantic-settings==2.2.1
httpx==0.27.0
python-magic==0.4.27
boto3==1.34.0
```

---

## 12. 关键设计决策记录

| 决策 | 选择 | 原因 |
|-----|------|------|
| 接口协议 | REST (FastAPI) | Java 调用简单，无需 protobuf |
| 处理模式 | 异步队列（Celery + Redis）| 大文件处理耗时长，避免 HTTP 超时 |
| 职责范围 | 仅解析 | 分块/向量化策略由业务侧决定，解耦更灵活 |
| 元数据格式 | 独立 positions.json | MD 保持可读性，JSON 便于程序解析位置信息 |
| 坐标系 | 统一左上角原点 | PDF.js 渲染坐标系，前端直接使用无需转换 |
| block_id 格式 | HTML 注释锚点 | 不破坏 MD 语法，任何 MD 渲染器均兼容 |
| 引擎路由 | 按扩展名 + 语言提示 | 简单可靠，支持强制覆盖 |
| MinerU 坐标依赖 | bbox 原生输出 | MinerU JSON 输出直接含 bbox，无需额外处理 |
| Docling 坐标转换 | bbox.b/t → top-left | Docling BoundingBox 用 PDF 坐标系，需在 Parser 层统一转换 |
| 文件输入方式 | binary 上传 + file_url 双支持 | binary 适合本地/私有化，file_url 适合云存储避免大文件二次传输 |
| 结果返回方式 | inline + url 双模式（mode 参数）| inline 适合小文件直接消费，url 适合大文件减少传输，云存储预签名 URL 直接落库 |
| 存储抽象 | FileStore 接口 + Local/Cloud 两种实现 | 开发用本地，生产切换云存储只需改环境变量，代码零改动 |
| 云存储协议 | S3 兼容协议（boto3）| AWS S3 / 阿里云 OSS / 华为云 OBS / MinIO 均兼容，一套代码多云可用 |
