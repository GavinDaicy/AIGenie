# QueryGenie 前端

知识库 + 智能检索前端，技术栈：Vue 2、Element UI、Vue Router、Axios。

## 功能

- **首页检索**：按关键字、语义、混合模式检索，选择知识库、条数，查看分块结果与相关度
- **知识库管理**：新建、编辑、发布、删除知识库
- **文档管理**（在知识库详情内）：上传本地文档、添加远程文档（网页/语雀）、编辑文档信息、查看文档状态、查看分块、删除文档

## 开发

```bash
# 安装依赖
npm install

# 启动开发服务（默认 http://localhost:8080，代理 /genie/api 到后端）
npm run serve
```

确保后端已启动（默认 `http://localhost:8090`，context-path `/genie/api`）。

## 构建

```bash
npm run build
```

产出在 `dist/`，可部署到任意静态服务器；需配置反向代理将 `/genie/api` 转发到后端。
