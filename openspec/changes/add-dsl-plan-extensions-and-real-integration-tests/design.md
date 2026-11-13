# Design

## Context

现有引擎已经把 `DslRequest` 解析为不可变 `ExecutionPlan`，并由 `QueryExecutionEngine` 统一调用 `PlanPolicy`、运行时边界和 `DataAdapter`。关系型计划的 AST 能表达 WHERE 和字段等值 JOIN ON，但还不能表达扩展注入的“字段与绑定参数”JOIN 条件；Spring starter 也只冻结 DSL/Data Adapter 注册表。

本变更必须继续保持公共仓库与企业仓库边界：公共模块只提供扩展契约、类型化计划重写和通用后端能力，宿主应用负责注册企业权限扩展并构造可信 `QueryContext`。测试应用只用于真实协议验收，不代表生产认证实现。

## Goals / Non-Goals

**Goals:**

- 提供可冻结、可排序、可校验的通用 `PlanExtension` SPI，扩展输入不包含原始请求、SQL、凭据或传输层对象。
- 在解析后的计划与后端编译之间应用扩展，并对最终计划重新执行授权策略和运行时边界校验。
- 为关系查询提供 `WHERE` 与 `JOIN_ON` 的类型化约束和参数绑定能力，支持安全实现逻辑删除、租户范围等规则。
- 通过 Spring Boot Bean 装配扩展注册表，并提供真实 MySQL、Doris MySQL 协议、PostgreSQL 的可重复测试工程和 HTTP 联调入口。
- 保留既有无扩展调用方的构造和执行行为，扩展注册表为空时行为与当前版本一致。

**Non-Goals:**

- 不在公共仓库实现企业用户认证、Token 白名单、userId/domainId 权限判定或生产数据源配置。
- 不提供原始 SQL、SQL AST 逃生口、厂商专有 SQL 扩展或跨数据源 JOIN。
- 不在扩展中执行 JDBC 查询、访问 DataSource、修改 QueryResult 或绕过 Data Adapter。
- 不把查询结果缓存作为本变更目标；仅保持现有受版本约束的编译缓存边界。

## Decisions

### 1. 通用扩展接口返回新的不可变计划

新增 `PlanExtension`、`PlanExtensionContext` 和 `PlanExtensionRegistry`。扩展通过 `id()`、`order()`、`supports()` 和 `apply()` 声明行为；上下文只含 DSL 类型/版本、扩展标识和已冻结的 `QueryContext`。注册表在构造时校验唯一标识、排序并冻结，运行期间只按确定顺序应用。

选择“返回新计划”而不是暴露可变 Builder，是为了让扩展不能在并发请求间共享可变状态，也使扩展后的计划可以继续经过统一校验和指纹计算。未预期异常由注册表转换为稳定的扩展失败异常并 fail-closed。

### 2. 关系扩展使用约束值而不是 SQL 片段

新增 `RelationalPlanExtension`、`RelationalConstraint`、`ConstraintPlacement` 和 `RelationalPlanRewriter`。关系扩展只返回已解析字段、比较运算符、逻辑类型、值和 `WHERE`/`JOIN_ON` 位置。重写器校验别名和白名单字段，生成保留前缀的内部参数名，构造新的 `ComparisonPredicate` 和 `ParameterBinding`。

为保持左连接语义，关系 JOIN 增加不可变的“附加谓词”集合，并由 MySQL/PostgreSQL 编译器统一以参数化方式写入 ON；原有字段等值 JOIN 条件继续保留。扩展约束使用内部参数名并避开已有参数，用户无法通过请求参数覆盖或引用系统绑定。

选择独立重写器而不是让扩展直接构造 AST，是为了把表/字段解析、参数命名、JOIN 位置和元数据更新集中到一个安全边界，避免每个企业扩展重复实现 SQL 安全规则。

### 3. 执行流水线双重校验

流水线固定为：

```text
DslAdapterRegistry.resolve
  -> DslAdapter.parse
  -> PlanPolicy.validate
  -> 基础计划边界
  -> PlanExtensionRegistry.apply
  -> PlanPolicy.validate
  -> 最终计划边界
  -> DataAdapterRegistry.resolve
  -> DataAdapter.execute
```

`compile()` 复用前半段并返回最终类型化计划，供测试应用的 validate/explain 使用；explain 只输出受控摘要，不输出 SQL。旧的无扩展构造函数委托空注册表，避免破坏已有 SPI 使用方。

### 4. Spring starter 只做 Bean 装配，不做业务规则配置

starter 收集 `List<PlanExtension>`，构造单例 `PlanExtensionRegistry`，并将其传入执行引擎。逻辑删除、租户字段和条件值由宿主代码实现为扩展 Bean；不新增将这些规则表达为 YAML SQL 的配置项。注册表与现有 DSL/Data Adapter 注册表一起在 readiness 前校验。

### 5. 真实数据库测试采用配置切换和外部 Compose

新增 `test-spring-boot` Maven 模块，应用只接受结构化 `relational-query` DSL，服务端从配置构造演示 schema 白名单和可信租户上下文。MySQL、Doris、PostgreSQL 使用三个明确的测试 profile；每次 profile 只创建一个服务端 DataSource，Doris profile 仍选择 `mysql` adapter。Docker Compose 固定镜像版本、端口和健康检查，测试脚本先启动服务再逐 profile 运行 HTTP 集成测试。

选择 profile 切换而不是一个进程内创建三个引擎，是为了复用 starter 的单 DataSource 启动校验，并能直接证明默认 adapter 的 fail-fast 行为。集成测试只使用真实 JDBC 和真实 HTTP，不使用 Mock JDBC；快速单元测试仍可保留在公共模块中。

### 6. API 只提供执行、校验和安全解释

测试应用暴露 `POST /api/test/dsl/execute`、`/validate`、`/explain`。三个接口都接收 `DslRequest`，服务端统一构造 `DslContext`/`QueryContext`；请求体没有 userId、domainId 或 adapter 选择权。执行返回统一 `QueryResult`，校验/解释返回计划类型、版本、扩展标识、参数数量和后端能力等安全摘要，不返回可执行 SQL。

## Risks / Trade-offs

- [风险] 扩展顺序错误可能改变权限语义 → 注册表按显式 `order` 和稳定 `id` 排序，并覆盖顺序测试；企业扩展应为权限范围建立固定顺序。
- [风险] 扩展增加条件后超过计划边界 → 应用前后都执行 PlanPolicy 和运行时边界校验，超限时在获取连接前失败。
- [风险] JOIN ON 扩展破坏 LEFT JOIN 结果 → 关系重写器按 `JOIN_ON` 写入被连接表的附加谓词，真实 MySQL/Doris/PostgreSQL 测试覆盖保留空侧结果。
- [风险] 真实 Doris 镜像启动慢或协议差异导致测试不稳定 → Compose 提供健康检查和有界等待脚本；测试失败保留服务日志，不在代码中降级为 MySQL 模拟。
- [风险] 扩展上下文中的属性被宿主错误地信任 → SPI 只保证不接收请求体；宿主必须在入口认证后构造 `QueryContext`，测试应用使用固定服务端租户配置并文档化这一边界。
- [风险] 编译缓存复用旧扩展结果 → 计划指纹/模板缓存加入扩展版本摘要，扩展修改时由宿主递增版本或禁用缓存；本变更不缓存查询结果。

## Migration Plan

1. 先发布包含扩展 SPI 和空注册表兼容构造的公共构件。
2. 宿主应用将逻辑删除、租户范围等现有查询拦截逻辑迁移为 Bean 扩展，并为每个扩展设置稳定标识和顺序。
3. 启动时校验扩展注册表、schema policy、PlanPolicy 和 DataSource；所有依赖缺失均在 readiness 前修复。
4. 使用 Compose 真实数据库 profile 执行 HTTP 集成测试，再逐步接入企业环境。
5. 回滚时移除扩展 Bean 或回退到空扩展注册表；基础 DSL 解析和后端适配器行为保持不变。

## Open Questions

无。扩展版本如何参与企业缓存键由宿主实现的 `PlanExtension` 版本策略在后续独立变更中补充，不影响本次接口和安全边界。
