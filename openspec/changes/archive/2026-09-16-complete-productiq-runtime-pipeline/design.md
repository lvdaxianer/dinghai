## Context

现有 ProductIQ 组件已经定义 `EmbeddingClient`、`VectorIndex`、`CurrentCandidateHydrator`、
`OntologyReasoner`、`AnsweringService` 与 SSE 入口，但 Spring 仅注册一个总是拒答的
`ChatAnswerGateway`。`IndexJobRunner` 只能由测试手动调用，remote OpenAI 配置对象也没有
Spring Bean。结果是单元测试通过而部署服务不能完成真实问答。

## Goals / Non-Goals

**Goals:**

- 在一个受限同步编排器中完成问题嵌入、Top-20 向量候选、水合、本体扩展、重排和回答。
- 用模式化配置在 deterministic 本地模式和 remote 部署模式之间明确选择；remote 缺失必要配置时失败启动。
- 让 SQLite 持久化索引任务由受限后台执行器重试，并将任何未完成任务暴露为检索保护状态。
- 使用实际 Spring Bean、SQLite 和内存向量实现端到端行为测试，不依赖外部模型或 Qdrant。

**Non-Goals:**

- 不改变现有 SSE 事件名称、插件 API 或可信知识包协议。
- 不实现多租户认证、模型流式 token 转发、Qdrant collection 自动迁移或分布式任务队列。
- 不在 deterministic 模式伪装为远程模型质量；它仅是可重复的离线运行/测试适配器。

## Decisions

### 1. 以 `RuntimeChatAnswerGateway` 连接既有端口

网关先检查 SQLite 是否有任何当前文档和是否存在 pending/processing/failed 索引任务；无知识返回
`no_knowledge`，未完成索引返回 `knowledge_updating`。其余请求依次执行 Embedding、最多 20 个
向量匹配、SQLite 当前性水合、四跳本体扩展，再转换为 `EvidenceCandidate` 并交给
`AnsweringService`。这样所有事实仍回查 SQLite，Qdrant 从不成为权威内容。

替代方案是把这些步骤散落到 Controller 或直接让模型从 Qdrant payload 回答；前者破坏 SSE
与领域边界，后者违反权威事实和更新隔离要求，因此不采用。

### 2. 配置显式模式，而不是根据密钥静默猜测

`productiq.runtime.mode=deterministic|remote` 决定依赖装配。deterministic 使用内存向量、稳定
Embedding/Reranker/Answerer，适用于本地和测试；remote 使用 OpenAI 兼容 Embedding、结构化
Reranker、Answerer 与 Qdrant。remote 必须同时有安全 API key、网关 URL、模型 ID、维度与
collection，缺失时 Spring 启动失败。

替代方案是无密钥时静默回退 deterministic；这会将部署配置错误伪装为成功，因此不采用。

### 3. 索引调度与可见状态由 SQLite 驱动

`IndexJobRunner` 以单独有界执行器定期领取 SQLite 的 pending 或可重试 failed job。每次 job
状态变更持久化；查询链路只要检测到未完成索引 job，便拒绝使用可能过期的向量。启动时不执行
collection 破坏性重建，重建仍由显式运维操作负责。

### 4. 远程模型适配各自独立，但共享安全 HTTP 基础设施

Embedding 使用 `/embeddings`，返回 float 数组并校验维度；Reranker 调用 `/chat/completions`，
要求 JSON object 内的 `rankedSectionIds`，Java 再执行现有候选白名单过滤；Answerer 沿用现有
适配器。HTTP 连接与请求时限可共享，业务请求/正文不记录到日志。

## Risks / Trade-offs

- [远程模型返回非结构化 JSON] -> 将其视为无有效重排结果，返回 `no_relevant_knowledge`，不接受任意 section ID。
- [索引 worker 暂时失败] -> 记录失败状态并保留安全拒答；后续调度重试，不回滚 SQLite 事实。
- [本地 deterministic 排序与真实语义质量不同] -> 只用于确定性测试，生产必须显式选择 remote。
- [任务状态查询引入额外 SQLite 访问] -> 仅执行 bounded existence query，优先保证资料更新期间不答旧内容。

## Migration Plan

1. 增加运行时配置及 Bean，默认 deterministic 以保持现有本地启动可用。
2. 部署 remote 前设置完整 `.env`，创建模型/维度独立的 Qdrant collection，并导入知识包。
3. 等索引任务完成后运行真实后端验收；失败时保持 `knowledge_updating` 并修复配置或重试任务。
4. 回滚时切回上一应用镜像和 collection；SQLite 事实不依赖向量内容，必要时重建索引。

## Open Questions

无。具体模型名称和网关地址由部署环境传入，不进入版本库。
