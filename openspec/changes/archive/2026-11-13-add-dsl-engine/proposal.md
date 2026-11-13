# Proposal

## Why

当前公共组件库没有通用的 DSL 解析、语义校验和执行计划编译能力，业务系统只能自行把 DSL、SQL 生成、数据库连接和结果映射揉在一起，导致 DSL 扩展困难且存在原始 SQL 绕过风险。

现在需要沉淀一个领域无关的 DSL Engine，使任意已注册 DSL 可以编译为统一执行计划，并通过独立的 MySQL、PostgreSQL 等后端适配器接入目标系统，同时保持公共仓库不依赖企业业务实现。

## What Changes

- 新增 `dinghai-dsl-engine` 聚合模块及 core、SPI、关系型 DSL、MySQL、PostgreSQL 和 Spring Boot starter 模块。
- 提供 DSL 请求模型、版本化 Adapter Registry、AST/语义模型、统一 `ExecutionPlan`、诊断错误和不可变计划契约。
- 提供首个 `relational-query` v1 JSON DSL 作为参考 DSL，支持受限的关系查询表达，但不接受原始 SQL。
- 提供 MySQL/Doris 公共能力的参数化 SQL 编译与 JDBC 执行，以及 PostgreSQL 参数化 SQL 编译与 JDBC 执行。
- 将 DSL 编译、目标语法转换、JDBC 执行和结果映射分层，允许后续接入其他 DSL 或后端系统而不修改已有核心。
- 应用启动时根据配置校验 DSL/Data Adapter、DataSource 和能力声明，构建并冻结 Registry；禁止静默降级或请求端指定未注册 Adapter。
- 提供有界的 AST/计划/编译结果缓存、查询复杂度与执行资源限制、Metrics、Trace、审计和安全摘要日志扩展点。
- 不新增企业 Controller、身份权限、服务 Token 白名单、本体语义或企业数据源配置；这些由企业仓库装配。

## Capabilities

### New Capabilities

- `dsl-compilation`: 版本化 DSL 注册、解析、AST/语义校验、规范化和统一执行计划生成。
- `backend-adapters`: 统一执行计划到 MySQL/Doris、PostgreSQL 目标命令的编译、JDBC 执行和统一结果映射。
- `adapter-bootstrap`: 基于 Spring Boot 配置的 Adapter 装配、DataSource 校验、启动失败策略和不可变 Registry。
- `dsl-runtime-controls`: 计划复杂度限制、执行超时、缓存、指标、Trace、审计扩展和敏感信息保护。

### Modified Capabilities

无。

## Impact

影响根 `pom.xml` 的模块列表和版本管理，新增 Java 21 模块、JDBC/Spring JDBC、MySQL Connector/J 和 PostgreSQL JDBC 的 provider-scoped 依赖，以及对应 README、OpenSpec 规格和测试。

公共核心只定义通用契约和可插拔 SPI；企业系统继续负责 HTTP 接口、认证授权、userId/domainId、Token 白名单、业务白名单和环境连接配置。Doris 通过 MySQL 协议复用 MySQL Adapter，不新增 Doris 反向依赖或独立 Doris Adapter。
