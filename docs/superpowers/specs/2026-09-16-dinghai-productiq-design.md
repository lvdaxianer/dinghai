# dinghai-productiq 设计说明

`dinghai-productiq` 是当前 `dinghai` 仓库中的独立产品子项目，负责把产品 Markdown 资料加工为可验证知识包，并通过 SQLite、Qdrant、有界本体推理和 SSE 回答 API 提供中文产品问答。它不改变现有 `dinghai-*` 基础组件的构建和发布边界。

## 方案

顶层目录固定为 `backend/`、`skills/`、`plugins/`、`docs/`、`knowledge/` 和 `docker-compose.yml`。后端是按能力分包的 Spring Boot 模块化单体，使用 `io.github.lvdaxianer.dinghai:knowledge-assistant` 和 `io.github.lvdaxianer.dinghai.projectiq`。Skills 只通过知识包协议协作，插件只通过 HTTP/SSE 协作。

知识导入严格执行文件对、SHA-256、Schema、章节锚点和证据 quote 校验；通过后以 `source_key + content_hash` 幂等替换 SQLite 当前事实，随后异步同步 Qdrant。SQLite 是唯一事实源，Qdrant 可从 SQLite 重建。

问答依次执行 Embedding 召回、SQLite 当前性水合、强关系四跳扩展、结构化重排序和证据约束回答。无覆盖、索引更新或下游失败时返回稳定错误事件，不调用或不复用不安全的旧证据。统一入口生成 requestId/traceId，日志不保存完整问题、正文和回答。

## 阶段边界

1. 工程骨架、协议校验、Skills 模板、插件壳和构建测试基线。
2. SQLite 事实模型、导入替换/删除和 Qdrant 同步任务。
3. 召回、本体推理、重排序、Answerer 和 SSE API。
4. 插件交互、Docker 编排、观测配置和验收基线。

每一阶段先写失败测试，再以最小实现通过，独立验证后合并回 `main`。
