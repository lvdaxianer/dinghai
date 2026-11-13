# Tasks

## 1. 公共扩展 SPI 与冻结注册表

- [x] 1.1 在 `spi` 中新增 `PlanExtension`、`PlanExtensionContext`、`PlanExtensionException` 和不可变 `PlanExtensionRegistry`，实现唯一标识、显式顺序、适用性判断、异常 fail-closed，并用注册表单元测试验证重复标识、稳定顺序和空注册表行为。
- [x] 1.2 在 `QueryExecutionEngine` 中接入扩展注册表，增加不执行后端的安全编译入口；按“基础 PlanPolicy/边界 → 扩展 → 最终 PlanPolicy/边界”编排，并用引擎测试验证扩展计划确实传入 Data Adapter、扩展失败不触发后端。

## 2. 关系型类型化计划重写

- [x] 2.1 为关系 AST 增加不可变 JOIN ON 附加谓词表达，并同步 MySQL/PostgreSQL 编译器以参数化方式编译 WHERE 与 JOIN ON 扩展条件；先写并运行失败的编译器测试，再实现通过。
- [x] 2.2 新增 `RelationalPlanExtension`、`RelationalConstraint`、位置枚举和安全重写器，校验白名单别名/字段、内部参数命名、逻辑类型和值、元数据与计划边界；用关系模块测试覆盖逻辑删除、租户条件、LEFT JOIN ON、参数冲突和非法字段。

## 3. Spring Boot 启动装配

- [x] 3.1 在 starter 中收集 `PlanExtension` Bean、构建并冻结注册表，将其注入执行引擎，保持既有 DSL/Data Adapter/PlanPolicy fail-fast；补充 ApplicationContextRunner 测试验证 Bean 装配、重复扩展和启动前失败。
- [x] 3.2 更新 starter 配置元数据与公共 README，明确扩展通过代码 Bean 注册、请求不传 adapter/SQL、Doris 复用 MySQL；运行 starter 模块测试和文档示例校验。

## 4. Spring Boot 真实测试应用与 HTTP 契约

- [x] 4.1 新增 `dinghai-dsl-engine/test-spring-boot` Maven 模块，提供启动类、可信测试上下文、演示 schema 白名单和逻辑删除/租户扩展示例；通过模块编译和 Spring 启动 smoke test 验证无秘密默认值。
- [x] 4.2 提供 `/api/test/dsl/execute`、`/validate`、`/explain`，统一处理 DSL 请求、稳定错误码/traceId 和安全计划摘要；添加不接受原始 SQL、请求体身份字段和 adapter 选择的 HTTP 测试，并编写 `apis/` 模块接口文档及可运行 curl 示例。

## 5. 真实数据库协议验收

- [x] 5.1 添加固定版本的 MySQL、Doris FE/BE、PostgreSQL Compose 配置、健康检查、初始化脚本和无密钥环境变量模板，验证 `docker compose config` 通过且测试数据不会指向生产。
- [x] 5.2 添加真实 JDBC/HTTP 集成测试与有界运行脚本，分别验证 MySQL、Doris MySQL 协议复用 MySQL Data Adapter、PostgreSQL 使用 PostgreSQL Data Adapter、JOIN/逻辑删除/租户范围/结果映射；测试必须连接真实数据库，不用 Mock JDBC，并记录 Docker 不可用时的明确失败信息。

## 6. 跨模块验证与交付

- [ ] 6.1 执行 OpenSpec 严格校验、DSL 全模块单元测试、模块编译、代码审查和安全日志审查，修复发现的问题并核对公共/企业职责边界。
- [ ] 6.2 启动真实数据库并执行三个后端 profile 的集成测试，检查 HTTP 文档命令与实际响应一致；保留测试结果、Docker 健康状态和无敏感信息的审计证据。

## Workflow follow-up

- 按项目分支规范为每个独立任务从 `main` 创建任务工作树，完成 TDD、审查和中文 Conventional Commit 后合并回 `main`。
- 完成全部任务后归档 OpenSpec change，同步主规格并执行最终工作区卫生检查。
