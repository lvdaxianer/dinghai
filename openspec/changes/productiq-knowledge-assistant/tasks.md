## 1. 工程骨架与协议基线

- [x] 1.1 创建 `dinghai-productiq` 顶层布局、独立 Spring Boot Maven 后端、根包入口、基础配置和构建测试基线
- [x] 1.2 实现知识包/Checker Schema 校验、哈希与证据规则，并补齐四个离线 Skill 模板与协议资源

## 2. 知识事实与导入

- [x] 2.1 创建 SQLite schema、领域记录和仓储，实现文档/章节/概念/别名/关系/证据/索引任务持久化
- [ ] 2.2 实现可信目录导入、sourceKey 安全校验、幂等事务替换、显式删除和管理 API

## 3. 检索与本体

- [ ] 3.1 实现 Embedding、Qdrant HTTP/内存适配、索引任务执行与重建能力
- [ ] 3.2 实现 SQLite 当前性水合、Top-K 候选、四跳强关系本体推理和覆盖排除

## 4. 回答与观测

- [ ] 4.1 实现结构化 Reranker、证据上下文限制、OpenAI 兼容 Answerer 和拒答状态
- [ ] 4.2 实现 `/api/chat/stream` SSE、请求校验、取消传播、requestId/traceId/MDC 和敏感日志边界

## 5. 浏览器交付与运行

- [ ] 5.1 实现 Manifest V3 Chrome 插件 Popup、Options、Service Worker 的提问/流式/取消/来源交互
- [ ] 5.2 提供 Docker Compose、配置样例、运维/重建/回滚文档和 30~50 题验收基线工具

## 6. 集成验收

- [ ] 6.1 执行 OpenSpec、模块测试、后端构建、插件检查和验收集，完成计划一致性与代码审查
- [ ] 6.2 归档 OpenSpec change，确认所有任务合并回 `main` 且工作区无产品实现遗漏
