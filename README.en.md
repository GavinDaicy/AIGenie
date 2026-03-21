# QueryGenie

An engineering-oriented RAG project for enterprise knowledge bases.  
It delivers a full workflow from document ingestion to hybrid retrieval and streaming QA, with DDD layering for long-term maintainability.

[中文说明](./README.md)

## Why QueryGenie

Many RAG demos can run but are hard to evolve in production. QueryGenie focuses on practical engineering:

- End-to-end workflow: KB management -> parsing/chunking -> retrieval -> rerank -> streaming QA
- Evolvable architecture: clear Controller/Application/Domain/Infrastructure boundaries
- Replaceable infrastructure: model provider, vector retrieval, cache, and storage can be swapped in infrastructure layer

## Why It Is Valuable As Open Source

- Scenario value: not just a chatbot demo, but a full knowledge workflow
- Engineering value: DDD layering with architecture guard tests
- Product value: hybrid retrieval + rerank + streaming answer for practical UX
- Evolution value: abstraction boundaries make future replacement feasible

## Core Features

- Knowledge base management with configurable searchable fields
- Document ingestion from local files and remote sources (web/Yuque)
- Retrieval modes: keyword, vector, hybrid (RRF), with optional reranking, time Decay
- RAG QA with SSE streaming and multi-session management, time Decay

## Capability Matrix

| Area | Current capability | Open-source value |
|---|---|---|
| Ingestion | Local files + web/Yuque | Reusable data onboarding pipeline |
| Retrieval | Keyword / Vector / Hybrid (RRF) | Direct strategy comparison |
| Ranking | DashScope rerank integration | Better relevance and ranking quality |
| QA UX | SSE streaming + session history | Production-like user experience |
| Architecture | DDD + ArchUnit constraints | Easier extension and maintenance |

## Architecture Value

Backend follows DDD layering:

- `controller`: request validation and HTTP exposure
- `application`: use-case orchestration
- `domain`: core business rules and abstractions
- `infrastructure`: concrete implementations for MySQL/Redis/Elasticsearch/LLM

The project includes an ArchUnit test to enforce layering boundaries:

- `query-genie-server/src/test/java/com/genie/query/architecture/LlmLayerDependencyArchTest.java`

## Difference From Typical RAG Demos

- Focus on complete workflow, not only answer generation
- Keep domain logic out of infrastructure details
- Preserve extensibility for model/retrieval/cache replacements

## Quick Start (10 minutes)

### 1) Prerequisites

- JDK 17+
- Maven 3.6+
- Node.js 16+
- Docker / Docker Compose

### 2) Configure environment variables

```bash
cp .env.example .env
export DASHSCOPE_API_KEY=your-dashscope-api-key
```

If you enable query rewrite via OpenAI-compatible endpoint:

```bash
export OPENAI_API_KEY=your-openai-compatible-api-key
```

### 3) Start dependencies and init DB

```bash
./scripts/bootstrap.sh
```

### 4) Start backend

```bash
cd query-genie-server
mvn spring-boot:run
```

Default API base: `http://localhost:8090/genie/api`

### 5) Start frontend

```bash
cd query-genie-front
npm install
npm run serve
```

Open `http://localhost:8080`.

## Configuration

- Runtime config: `query-genie-server/src/main/resources/application.yml`
- Example config: `query-genie-server/src/main/resources/application.example.yml`
- DB init SQL: `query-genie-server/src/main/resources/sql/init.sql`
- Local dependencies: `docker-compose.yml`

## License

This project is licensed under the [MIT License](./LICENSE).
