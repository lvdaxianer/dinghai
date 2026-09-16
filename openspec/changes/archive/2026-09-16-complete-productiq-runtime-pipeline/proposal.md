## Why

ProductIQ 已具备可独立测试的导入、向量、推理和回答组件，但运行时仍装配为默认拒答网关，
没有执行文档定义的“Embedding -> Qdrant -> SQLite -> 本体 -> Reranker -> Answerer”闭环。
需要将已有端口接入生产配置，并用可重复的端到端测试证明真实请求不再停在默认拒答路径。

## What Changes

- 新增受配置驱动的运行时检索问答编排器，向 `/api/chat/stream` 提供当前性校验后的证据回答。
- 新增 OpenAI 兼容 Embedding 和结构化 Reranker HTTP 适配，以及 deterministic 与 remote 两种明确运行模式。
- 新增有界、可恢复的索引任务调度，pending/failed 索引任务使相关请求稳定返回 `knowledge_updating`。
- 扩展 Docker/环境变量与运维文档，使运行时配置、模型端点和 Collection 约束可部署。
- 新增 SQLite + 向量索引 + SSE 的端到端测试和验收命令，禁止仅用 fixture 自检声明检索闭环成功。

## Capabilities

### New Capabilities

- `runtime-retrieval-pipeline`: 将问题嵌入、向量召回、SQLite 当前性水合、本体扩展、重排和回答连接为实际 SSE 查询链路。
- `runtime-model-and-indexing`: 提供确定性/远程运行模式、OpenAI 兼容模型适配、Qdrant 索引任务调度和安全配置校验。
- `runtime-e2e-acceptance`: 以真实后端依赖组合验证导入、索引、回答、拒答和索引更新状态。

### Modified Capabilities

无。归档 change 的 delta specs 仅作为历史记录，当前主 specs 中没有对应 capability。

## Impact

- 影响 `dinghai-productiq/backend` 的 chat、retrieval、knowledge configuration 和测试。
- 影响 `dinghai-productiq/.env.example`、`docker-compose.yml` 与运行文档。
- `/api/chat/stream` 的事件契约保持不变，但真实部署会依运行模式回答而不再固定拒答。
- 不增加外部运行时依赖；仍使用 JDK HTTP 客户端、SQLite 和 Qdrant REST API。
