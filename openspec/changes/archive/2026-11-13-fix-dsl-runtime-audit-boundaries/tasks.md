# Tasks

## 1. HTTP 审计与错误边界

- [x] 1.1 增加统一 trace/MDC Filter 和一次性 HTTP 完成审计，验证成功、校验失败和未预期异常均有安全日志且响应不泄露内部错误。
- [x] 1.2 增加可插拔 DSL 执行任务上下文装饰器，验证异步任务获得 traceId 且线程复用后上下文被清理。

## 2. DSL 参数与运行时边界

- [x] 2.1 在关系计划构建阶段校验参数逻辑类型与字段类型一致，并限制参数总量和 `IN` 元素数量；同时让不同关系结构产生不同计划摘要；补充红绿单元测试。
- [x] 2.2 明确 `dataSourceId` 为服务端上下文标识，补充文档和测试证明请求不能覆盖 adapter、数据源或身份。

## 3. 真实接口验收与文档

- [x] 3.1 补充 HTTP 契约测试，覆盖顶层 `sql`、`adapter`、`dataSourceId`、`userId`、`domainId` 和 `tenantId`。
- [x] 3.2 加强真实 MySQL、Doris、PostgreSQL HTTP/JDBC 脚本断言，验证行数、结果值、逻辑删除和域隔离；同步 API 文档、配置元数据和 README。

## 4. 集成验证

- [x] 4.1 执行严格 OpenSpec 校验、DSL 全模块测试、代码审查和安全日志审查，修复发现的问题并记录公共/企业职责边界。
- [x] 4.2 运行真实数据库验收脚本、检查工作区卫生、归档 OpenSpec 并确认变更已合并到 `main`。

## Workflow follow-up

- 按项目分支规范为每个任务从 `main` 创建任务工作树，完成测试、审查和中文 Conventional Commit 后合并回 `main`。
- 完成全部任务后同步主规格并归档本 change。
