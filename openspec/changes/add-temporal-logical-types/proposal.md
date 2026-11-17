## Why

当前关系 DSL 只有 `date` 和含义模糊的 `datetime`，HTTP 字符串可能直接进入 JDBC `setObject`；MySQL 与 PostgreSQL 的本地时间、时间戳和带时区时间因此无法稳定区分。需要建立后端无关的时间语义，让日期、本地日期时间和绝对时间在解析、绑定、结果映射和 JSON 输出中保持一致。

## What Changes

- 增加 `date`、`local_datetime`、`instant` 三种明确时间逻辑类型。
- 将旧 `datetime` 作为兼容别名规范化为 `local_datetime`。
- 在 DSL 计划构建阶段把时间字符串解析为 `LocalDate`、`LocalDateTime` 或 `Instant`，不再把时间字符串直接作为 JDBC 参数。
- 为 MySQL/Doris 和 PostgreSQL 增加时间参数类型化绑定及结果类型映射。
- 明确 `DATE`、`DATETIME`、`TIMESTAMP`、`TIMESTAMP WITHOUT TIME ZONE` 和 `TIMESTAMP WITH TIME ZONE` 的对应关系。
- 未知逻辑类型或时间格式错误时返回稳定编译/结果映射错误。
- 补充时间类型 DSL 文档、单元测试和真实数据库集成测试。

## Capabilities

### New Capabilities

- `temporal-logical-types`: 定义 DSL 时间逻辑类型、格式、时区语义和跨后端映射规则。

### Modified Capabilities

- `dsl-compilation`: 计划构建阶段执行时间类型规范化和严格参数解析。
- `backend-adapters`: MySQL/PostgreSQL Data Adapter 按逻辑时间类型绑定 JDBC 参数并映射结果。

## Impact

- 代码：`relational` 计划构建、`mysql`/`postgresql` 数据适配器和相关测试。
- 公共 DSL：新增 `local_datetime`、`instant`；旧 `datetime` 保持兼容但规范化为 `local_datetime`。
- 数据库：不改变表结构；要求数据源时区策略由服务端显式配置，不能依赖 JVM 默认时区。
- 文档：更新 `docs/dsl-syntax.md`、模块 README 和测试应用 API 说明。
