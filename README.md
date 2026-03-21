# QueryGenie

面向企业知识库和数据库场景的 AI问答 工程项目：提供从文档入库、混合检索到流式问答的完整闭环，并以 DDD 分层保证长期可扩展性。

[English README](./README.en.md)

## 为什么做这个项目

很多 RAG 示例能“跑起来”，但难以直接用于业务演进。QueryGenie 关注的是工程落地：

- 功能闭环：知识库管理 -> 文档解析/分块 -> 检索召回 -> Rerank -> 流式问答
- 架构可演进：Controller/Application/Domain/Infrastructure 清晰分层
- 能力可替换：模型、向量检索、缓存、中间件可按基础设施层替换

## 这个项目的价值（适合开源）

- 场景价值：不是单点聊天 Demo，而是覆盖知识库生产链路的完整系统
- 工程价值：DDD 分层 + 架构守卫测试，便于团队协作与长期重构
- 业务价值：支持混合召回、Rerank、流式回答，能够覆盖真实问答体验
- 演进价值：基础设施抽象清晰，便于扩展模型供应商和检索后端

## 核心能力

- 知识库管理：创建、编辑、发布、删除知识库，自定义字段检索能力
- 文档接入：本地文件 + 远程文档（网页/语雀）解析、切块、向量化入库
- 检索策略：关键字、向量、混合（RRF）三种模式，可选 Rerank 精排，时间衰减
- 智能问答：基于检索结果的 RAG 问答，支持 SSE 流式返回与会话管理，支持时间衰减

## 能力矩阵

| 维度 | 当前能力 | 开源价值 |
|---|---|---|
| 文档入库 | 本地文件 + 网页/语雀 | 便于复用到不同业务知识源 |
| 召回策略 | Keyword / Vector / Hybrid(RRF) | 可直接对比不同召回策略效果 |
| 结果优化 | DashScope Rerank | 提升答案相关性与排序稳定性 |
| 问答体验 | SSE 流式 + 多轮会话 | 接近生产态的人机交互体验 |
| 架构设计 | DDD 分层 + ArchUnit 约束 | 便于二次开发与长期维护 |

## 架构设计价值

后端采用 DDD 分层，强调“业务逻辑不被基础设施反向污染”：

- `controller`：参数校验与接口编排入口
- `application`：跨领域流程编排
- `domain`：核心业务规则与抽象接口
- `infrastructure`：MySQL/Redis/Elasticsearch/LLM 等具体实现

项目已包含 ArchUnit 架构约束测试，确保 `domain/application` 不直接依赖 `infrastructure.llm`：

- `query-genie-server/src/test/java/com/genie/query/architecture/LlmLayerDependencyArchTest.java`

## 与常见 RAG 示例的区别

- 不只关注“能回答”，还覆盖“如何接入、如何检索、如何持续演进”
- 不把业务规则耦合在控制层，降低维护成本
- 不绑定单一实现，方便未来替换 ES、模型或缓存方案

## 10 分钟快速体验

### 1) 准备环境

- JDK 17+
- Maven 3.6+
- Node.js 16+
- Docker / Docker Compose

### 2) 配置环境变量

复制示例：

```bash
cp .env.example .env
```

后端已支持自动加载 `.env`（在仓库根目录或 `query-genie-server` 目录均可）。

至少设置：

```bash
export DASHSCOPE_API_KEY=your-dashscope-api-key
```

如启用 query rewrite 的 OpenAI 兼容调用，可额外设置：

```bash
export OPENAI_API_KEY=your-openai-compatible-api-key
```

### 3) 拉起依赖并初始化数据库

```bash
./scripts/bootstrap.sh
```

### 4) 启动后端

```bash
cd query-genie-server
mvn spring-boot:run
```

默认地址：`http://localhost:8090/genie/api`

### 5) 启动前端

```bash
cd query-genie-front
npm install
npm run serve
```

访问：`http://localhost:8080`

## 配置说明

- 运行配置：`query-genie-server/src/main/resources/application.yml`
- 示例配置：`query-genie-server/src/main/resources/application.example.yml`
- 数据初始化：`query-genie-server/src/main/resources/sql/init.sql`
- 中间件编排：`docker-compose.yml`

## 开源许可

本项目使用 [MIT License](./LICENSE)。
