# ProductIQ 运行与回滚

## 本地启动

先构建后端 jar，再复制 `.env.example` 为部署侧 `.env`，至少注入管理密钥和真实模型网关配置：

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

Compose 会将 `PRODUCTIQ_SQLITE_PATH`、`PRODUCTIQ_TRUSTED_DIRECTORY` 和
`PRODUCTIQ_CHAT_TIMEOUT` 映射到后端实际配置；`PRODUCTIQ_ADMIN_KEY` 用于保护管理 API。

## 配置和超时

每个 Embedding 模型和维度必须使用独立的 Qdrant collection；切换模型时先创建新
collection，完成重建和验收后再切换 `PRODUCTIQ_QDRANT_COLLECTION`。建议初始预算：

| 调用 | 连接 | 读取 | 重试 |
| --- | ---: | ---: | ---: |
| Embedding | 3s | 15s | 2 次 |
| Rerank | 3s | 20s | 1 次 |
| Answer SSE | 3s | 90s 总时限 | 不自动重生成 |
| Qdrant | 2s | 5s | 由索引任务退避重试 |

阻塞模型调用使用专用有界线程池。客户端断开后由 SSE 控制器取消下游 Future；日志
只记录 requestId、traceId、状态、耗时、模型标识和来源数量，不记录问题全文、证据
正文、答案全文或密钥。

## 索引重建

1. 暂停新的知识导入，创建与目标 Embedding 模型匹配的新 collection。
2. 从 SQLite 当前 `sections` 读取正文，使用当前 Embedding 模型写入新 collection。
3. 运行 `node acceptance/run.mjs`，确认 Top-20、Top-3、拒答和来源一致性均无解释外失败。
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
