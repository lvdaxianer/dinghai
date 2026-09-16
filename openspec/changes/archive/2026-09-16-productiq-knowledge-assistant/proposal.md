## Why

`dinghai` 目前只有通用基础组件，缺少文档中定义的产品知识本体问答助手实现。需要在同一仓库下增加隔离的 `dinghai-productiq` 子项目，把可信 Markdown 知识加工、可重建事实存储、语义检索、本体扩展、流式回答和浏览器插件形成可运行、可测试的最小产品闭环。

## What Changes

- 新增 `dinghai-productiq/` 产品子项目，不改变现有 `dinghai-*` 组件模块。
- 新增 Java/Spring Boot 后端，固定 Maven 坐标和 `io.github.lvdaxianer.dinghai.projectiq` 根包。
- 实现知识包与 Checker 判定的 JSON Schema 校验、Markdown/知识包一致性校验和可信目录导入。
- 实现 SQLite 权威事实模型、幂等替换/显式删除、关系证据保留和 Qdrant 最终一致性同步任务。
- 实现 Embedding 召回、当前性水合、有界本体推理、结构化重排序、证据约束回答和 SSE 错误状态。
- 新增四个 Codex/Claude Skill 模板，遵循总控、生产者、检查者和共享协议边界。
- 新增最小 Chrome/Chromium 插件，支持配置、单轮提问、SSE 流式展示、来源和取消/错误状态。
- 新增 Docker Compose、运行文档、结构化观测约定和验收样例工具。

## Capabilities

### New Capabilities

- `productiq-layout`: 产品子项目目录、构件坐标、包边界和构建入口。
- `knowledge-processing`: 知识包协议、Schema 校验、离线 Skill 模板和可信目录约束。
- `knowledge-import`: SQLite 事实库、知识导入替换/删除和 Qdrant 同步任务。
- `retrieval-ontology`: Embedding/Qdrant 召回、SQLite 水合和有界本体推理。
- `chat-stream`: 重排序、证据约束回答、SSE API、错误状态和请求观测。
- `browser-extension`: Chrome 插件配置、提问、流式渲染、来源和操作状态。
- `operations-acceptance`: Docker 编排、配置、安全边界、重建索引和验收基线。

### Modified Capabilities

无。

## Impact

- 新增 `dinghai-productiq` 目录及其 Maven、JavaScript、Markdown 和部署文件。
- 根 Maven 聚合不自动引入产品后端，避免改变现有组件发布生命周期；产品后端在子目录独立构建。
- 后端新增 SQLite JDBC、Jackson JSON Schema 校验、Spring Web/SSE、HTTP 客户端等运行依赖；Qdrant 和 OpenAI 兼容网关通过配置适配。
- 新增 `/api/admin/knowledge-imports`、`/api/admin/knowledge-sources/{sourceKey}`、`/api/chat/stream` 契约及插件调用方式。
- 运行时需要持久化 SQLite、Qdrant 数据、可信知识目录和任务状态；模型密钥只从环境变量读取。
