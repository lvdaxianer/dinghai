## Context

`dinghai` 是 Java 21/Spring Boot 基础组件仓库，当前没有产品知识问答实现。产品方案要求在同一仓库新增 `dinghai-productiq` 子项目，同时保持后端、离线 Skills 和浏览器插件的依赖边界。第一期只面向单租户中文产品资料，知识发布由可信的 Markdown/JSON 文件对组成，SQLite 是权威事实源，Qdrant 只负责可重建的章节语义候选。

## Goals / Non-Goals

**Goals:**

- 建立可独立构建的 `dinghai-productiq/backend` Spring Boot 应用和稳定的领域包边界。
- 以 JSON Schema、SHA-256、原文证据和显式状态保证知识包可校验、可替换、可追溯。
- 提供不依赖外部服务即可运行的确定性测试实现，同时保留 OpenAI 兼容网关和 Qdrant 的生产适配接口。
- 完成知识导入、当前性检索、有界本体推理、证据重排序、拒答和 SSE 流式回答闭环。
- 提供不保存模型密钥的最小 Chrome 扩展及 Docker/运行验收资料。

**Non-Goals:**

- 不改造根 Maven 聚合，也不修改现有 `dinghai-*` 模块。
- 不实现多租户、SSO、OCR、上传后台、人工审核、多轮记忆、工单工具调用或 RDF/Neo4j。
- 不把 LLM 当作事实库或自由图谱推理器；模型只在受限协议边界内抽取、重排和回答。

## Decisions

### 1. 同仓库隔离的产品子项目

采用 `dinghai-productiq/backend`、`skills`、`plugins`、`docs`、`knowledge` 和 `docker-compose.yml` 的顶层布局。后端 POM 使用 `io.github.lvdaxianer.dinghai:knowledge-assistant`，Java 根包为 `io.github.lvdaxianer.dinghai.projectiq`。产品后端通过子目录 Maven 命令构建，不加入根 `<modules>`，避免影响基础组件发布。

备选方案是把后端并入根 Maven 多模块或创建独立 Git 仓库；前者会污染基础组件生命周期，后者不符合用户要求，均不采用。

### 2. 模块化单体与端口驱动适配

后端按 `knowledge`、`retrieval`、`chat`、`integration`、`config`、`shared` 分组；每个能力内部以 `api/application/domain/infrastructure/repository` 划分。SQLite、Qdrant、Embedding 和聊天网关均通过小接口接入，核心用例可以用内存 fake 做单元测试，生产环境再启用 JDBC/HTTP 实现。

备选方案是微服务拆分，鉴于单租户和第一期部署规模，会增加网络、事务和调试成本，因此采用模块化单体。

### 3. 知识包先校验后事务替换

导入流程先限制 `sourceKey` 为受信目录下的单层 `.md` 文件名，再读取同名 `.knowledge.json`，验证 Schema、Hash、章节锚点、概念 ID、关系白名单和逐字证据。通过后在一个 SQLite 事务中删除当前源拥有的 sections、映射和证据，写入新版本，清理无证据关系；以 `source_key + content_hash` 幂等。事务成功后只创建 Qdrant delete/upsert 任务，索引失败不会回滚事实。

这样可以保证替换一份文档不会撤销其他文档仍支撑的全局概念或关系，也能在 Qdrant 清空后从 SQLite 重建。

### 4. 召回与推理分层且有界

问题先经 Embedding 在 Qdrant 取 Top 20~30，再按 section ID 回 SQLite 验证当前 Hash。以命中 section 的概念为入口，只沿 `creates/requires/governed_by/exception_of/overrides` 走最多四跳、最多八个新增概念和三份关联文档；`parent_of/has_part/related_to` 只用于定位。候选摘要交给结构化 Reranker，Java 严格过滤模型返回的未知 ID，最终最多六节、三份文档、约 8,000 字输入 Answerer。

当索引待更新、没有有效候选或没有知识覆盖时直接返回稳定错误码，不调用回答模型。

### 5. SSE 与安全边界

`POST /api/chat/stream` 使用请求体传问题，响应只发送 `delta`、`sources`、`done` 或 `error` 事件。管理导入/删除端点与插件查询分离，管理端点要求内部服务密钥，插件只能携带可撤销的轻量客户端键。模型 API Key 只从环境变量读取。HTTP/模型/Qdrant 阻塞调用放在有界专用线程池，客户端断开时取消下游流。

### 6. 可观测性与敏感信息

统一 Filter 生成 requestId/traceId 并记录结构化审计；业务日志只记录 sourceKey、sectionId、模型 ID、耗时、状态和重试次数，不记录完整问题、正文或回答。日志级别、敏感字段掩码和 MDC 传播集中在配置/执行器层，避免 Controller 重复记录或线程复用串号。

## Risks / Trade-offs

- [本地没有 Qdrant/模型网关] → 提供内存向量/聊天 fake 和 HTTP 适配器契约测试；启动时明确依赖状态，不伪装成生产可用。
- [Markdown 与知识包不一致] → 导入前强制 Hash、Schema、锚点和 quote 校验；失败不触碰旧版本。
- [Qdrant 最终一致导致旧向量命中] → SQLite 当前性校验；索引 pending 时返回 `knowledge_updating`，禁止使用旧向量回答。
- [LLM 重排序返回越权 section] → Java 只接受原候选 ID 集合，并限制数量、字符数和文档数。
- [本体边过多造成上下文膨胀] → 强关系白名单、四跳/八概念/三文档硬上限，远跳只给 Reranker。
- [插件密钥被读取] → 客户端键仅作轻量筛选/统计，不承担认证；管理 API 使用独立服务身份。

## Migration Plan

1. 在当前 `main` 下创建任务 worktree，新增 `dinghai-productiq` 骨架和独立构建入口。
2. 先部署 SQLite schema、知识协议和内存测试适配器，再接入 Qdrant/网关 HTTP 适配器。
3. 通过样例知识包执行导入、替换、显式删除和索引重建验收。
4. 启动 Spring Boot 与插件联调，逐步将配置切换到真实 Qdrant/模型网关。
5. 任一阶段失败时保留旧可信包和旧 Qdrant collection；回滚应用版本即可恢复，禁止在原 collection 混写不同 Embedding 模型。

## Open Questions

- 真实部署使用的 OpenAI 兼容网关地址、聊天模型和中文 Embedding 模型由环境配置注入，代码不预设具体供应商。
- 首期插件采用 Chrome Manifest V3；后续若需要 Firefox/Edge，只复用 HTTP/SSE 契约，不改变后端检索链路。
