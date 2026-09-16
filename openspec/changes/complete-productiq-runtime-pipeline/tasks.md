## 1. 运行时模型与索引基础

- [x] 1.1 增加运行模式配置、远程 OpenAI 兼容 Embedding/Reranker 适配器和远程配置 fail-fast 测试
- [x] 1.2 扩展 SQLite 索引任务状态查询，装配有界定时索引 worker 并验证失败任务可恢复

## 2. 检索问答闭环

- [ ] 2.1 实现运行时 ChatAnswerGateway，将嵌入、向量召回、当前性水合、本体扩展、重排和证据回答连接起来
- [ ] 2.2 装配 deterministic/remote 模式的完整 Spring Bean，并验证无知识、更新中和过期向量拒答边界

## 3. 可运行交付与验收

- [ ] 3.1 对齐 Compose、环境变量和运维文档中的 runtime 配置、超时和模型约束
- [ ] 3.2 增加 SQLite + 内存向量 + SSE 端到端测试，执行后端、插件、验收、OpenSpec 与构建校验
- [ ] 3.3 完成计划一致性与代码审查，归档 OpenSpec change 并确认所有任务合并回 `main`
