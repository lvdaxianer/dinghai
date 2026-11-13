# Proposal

## Why

当前 DSL 引擎已经能够把受控 DSL 编译为执行计划，但外部系统无法以类型安全的方式扩展计划语义，例如租户隔离、逻辑删除和领域级数据范围；同时缺少覆盖真实 MySQL、Doris 和 PostgreSQL 的可运行集成测试工程。现在补齐扩展边界和真实测试入口，才能让 DSL 解析能力在不同宿主系统中可复用、可审计、可验证。

## What Changes

- 新增服务端 Plan Extension SPI，允许宿主系统通过代码注册有序的计划扩展，基于类型化执行计划和可信查询上下文进行约束注入或计划重写。
- 扩展执行前后增加计划校验，扩展不得接触原始 SQL、JDBC 连接、数据库凭据、Token 或未经信任的请求身份字段。
- 新增不可变的扩展注册表和 Spring Boot 启动装配，重复扩展、非法扩展和依赖缺失时 fail-fast。
- 为关系型计划提供参数化、表字段已解析的约束扩展能力，支持安全地表达租户范围、逻辑删除和 JOIN 条件，不允许拼接 SQL 片段。
- 新增独立的 Spring Boot 测试工程和真实数据库集成测试，验证 MySQL、Doris MySQL 协议复用 MySQL 适配器以及 PostgreSQL 适配器。
- 新增测试工程的 DSL 执行、校验和安全解释接口；接口只接受自定义 DSL，不提供原始 SQL 执行入口。
- 新增接口文档、Docker Compose 测试依赖、运行说明和示例扩展；测试凭据仅使用环境变量或本地占位配置，不提交真实密钥。

## Capabilities

### New Capabilities

- `dsl-plan-extensions`: 定义基于类型化执行计划和可信上下文的服务端扩展、顺序、约束注入和启动注册行为。
- `dsl-integration-test-runtime`: 定义真实数据库测试工程、DSL HTTP 测试入口、MySQL/Doris/PostgreSQL 适配器验证和安全响应边界。

### Modified Capabilities

- `dsl-compilation`: 编译流水线在基础解析和计划校验后应用已注册扩展，并再次校验最终计划；扩展失败或产出非法计划时不得进入后端执行。
- `adapter-bootstrap`: 启动阶段同时装配并冻结 DSL 计划扩展注册表，扩展重复注册或依赖配置不完整时启动失败。
- `dsl-runtime-controls`: 扩展阶段纳入计划边界、缓存版本、审计和可观测性约束，且扩展不能绕过既有运行时限制。

## Impact

- 影响 `dinghai-dsl-engine-spi`、`core`、`relational` 和 Spring Boot starter 的公共扩展契约及执行流水线。
- 新增 `dinghai-dsl-engine/test-spring-boot` 测试应用、真实数据库 Compose 配置和 `apis/` 模块接口文档。
- 外部宿主系统需要通过 Spring Bean 或等价 SPI 注册扩展；逻辑删除、租户范围等规则不再通过 DSL 请求或 YAML 传入。
- 不改变公共仓库对企业身份、权限和数据源凭据的所有权；企业仓库仍负责装配可信 `QueryContext` 和数据权限策略。
