# ProductIQ 运行与回滚

## 本地启动

先构建后端 jar，再复制 `.env.example` 为部署侧 `.env`。生产环境固定使用
`PRODUCTIQ_RUNTIME_MODE=remote`，并注入管理密钥和完整模型网关配置：

```bash
cd dinghai-productiq/backend
mvn -q package -DskipTests
cd ..
docker compose up -d --build
curl http://localhost:8080/actuator/health
```

SQLite、可信知识目录和 Qdrant 数据分别挂载在 `productiq-data`、
`productiq-knowledge`、`productiq-qdrant` 卷中。`.env` 只存在于部署环境，模型密钥
不能写入插件、知识包或 Qdrant payload。

Compose 会将 `PRODUCTIQ_SQLITE_PATH`、`PRODUCTIQ_TRUSTED_DIRECTORY`、
`PRODUCTIQ_CHAT_TIMEOUT`、模型网关、Qdrant 和索引 worker 配置映射到后端实际配置；
`PRODUCTIQ_ADMIN_KEY` 用于保护管理 API。remote 配置中下列任何必填项为空都会 fail-fast：

| 分组 | 必填变量 |
| --- | --- |
| 运行模式 | `PRODUCTIQ_RUNTIME_MODE=remote` |
| 模型网关 | `PRODUCTIQ_GATEWAY_BASE_URL`、`PRODUCTIQ_GATEWAY_API_KEY`、`PRODUCTIQ_EMBEDDING_MODEL`、`PRODUCTIQ_RERANK_MODEL`、`PRODUCTIQ_ANSWER_MODEL`、`PRODUCTIQ_EMBEDDING_DIMENSIONS` |
| 向量索引 | `PRODUCTIQ_QDRANT_URL`、`PRODUCTIQ_QDRANT_COLLECTION` |

`PRODUCTIQ_GATEWAY_BASE_URL` 是网关根路径，例如 `https://gateway.example.internal/v1/`；后端分别
追加 `embeddings` 和 `chat/completions`。不要配置为某个完整 API 端点。

## 配置和超时

每个 Embedding 模型和维度必须使用独立的 Qdrant collection；切换模型时先创建新
collection，完成重建和验收后再切换 `PRODUCTIQ_QDRANT_COLLECTION`。建议初始预算：

| 调用 | 连接 | 读取 | 重试 |
| --- | ---: | ---: | ---: |
| Embedding | 受 JDK HttpClient 限制 | `PRODUCTIQ_EMBEDDING_TIMEOUT=15s` | 索引任务后续重试 |
| Rerank | 受 JDK HttpClient 限制 | `PRODUCTIQ_RERANK_TIMEOUT=20s` | 当前请求不自动重排 |
| Answer SSE | 3s | 90s 总时限 | 不自动重生成 |
| Qdrant | 受 JDK HttpClient 限制 | `PRODUCTIQ_QDRANT_TIMEOUT=5s` | 由索引任务后续重试 |

索引 worker 使用 `PRODUCTIQ_INDEX_WORKER_BATCH_SIZE=20` 和
`PRODUCTIQ_INDEX_WORKER_INTERVAL=10s`。batch size 必须在 1-1000，interval 至少为 1ms；只要
SQLite 中有 pending、processing 或 failed 任务，查询都返回 `knowledge_updating`，不会使用旧向量。

阻塞模型调用使用专用有界线程池。客户端断开后由 SSE 控制器取消下游 Future；日志
只记录 requestId、traceId、状态、耗时、模型标识和来源数量，不记录问题全文、证据
正文、答案全文或密钥。

## 索引重建

1. 暂停新的知识导入，创建与目标 Embedding 模型匹配的新 collection。
2. 从 SQLite 当前 `sections` 读取正文，使用当前 Embedding 模型写入新 collection。
3. 运行 `npm test` 和 `node run.mjs --output reports/latest.json`，确认 Top-20、Top-3、拒答和来源一致性均无解释外失败。
4. 原子切换 `PRODUCTIQ_QDRANT_COLLECTION`，保留旧 collection 直到回滚窗口结束。

不得把不同模型的向量写入同一 collection，也不得把 Qdrant 当作事实源。

## 故障恢复和回滚

- `knowledge_updating`：等待索引任务完成，不使用旧向量回答新版本。
- `answer_failed`：检查网关超时、专用线程池和 SSE 客户端断开，再按需重试请求。
- Qdrant 数据损坏：保留 SQLite，创建新 collection 并执行完整重建。
- 发布回滚：切回上一版本镜像和旧 collection；SQLite 事实只通过已验证的知识包替换，禁止手工改库。

## 验收基线

```bash
cd dinghai-productiq/acceptance
npm test
node run.mjs --output reports/latest.json
```

基线包含 30 道中文问题，覆盖直接命中、口语别名、跨文档关系、`overrides`、无覆盖
拒答、来源替换和向量恢复。报告保留模型标识、知识 hash、选中 section ID 和每层结果，
可用于模型或 Prompt 变更后的回归对比。
