## ADDED Requirements

### Requirement: Temporal logical types are explicit and backend-neutral
DSL SHALL 使用后端无关的时间逻辑类型；`date` 表示日期，`local_datetime` 表示不带时区的本地日期时间，`instant` 表示带明确时区语义的绝对时间点；旧 `datetime` SHALL 作为兼容别名规范化为 `local_datetime`。

#### Scenario: Date is parsed without timezone
- **WHEN** 请求使用 `date` 并传入 `2026-11-13`
- **THEN** 计划绑定 `LocalDate`，不应用 JVM 默认时区或业务时区偏移

#### Scenario: Local datetime rejects timezone offsets
- **WHEN** 请求使用 `local_datetime` 并传入不带 offset 的 ISO 本地日期时间
- **THEN** 计划绑定 `LocalDateTime`；带 `Z` 或 offset 的值被拒绝

#### Scenario: Instant requires explicit offset semantics
- **WHEN** 请求使用 `instant` 并传入带 `Z` 或明确 offset 的 ISO 时间
- **THEN** 计划将值规范化为 UTC `Instant`

#### Scenario: Legacy datetime remains compatible
- **WHEN** 已有请求使用逻辑类型 `datetime`
- **THEN** 系统按 `local_datetime` 解析和校验，不回退到数据库特定类型

#### Scenario: Invalid temporal value is rejected safely
- **WHEN** 时间格式错误、逻辑类型未知或时区语义不匹配
- **THEN** 系统返回稳定校验错误，不生成 JDBC 命令，也不暴露参数原值

### Requirement: Temporal output has canonical JSON semantics
统一查询结果 SHALL 按逻辑类型输出稳定的 JSON 时间格式：`date` 使用 `yyyy-MM-dd`，`local_datetime` 使用不带 offset 的 ISO 本地时间，`instant` 保留 `Z` 或明确 offset 语义；数据库原生类型名称不得出现在 DSL 结果类型中。

#### Scenario: Date output is canonical
- **WHEN** Data Adapter 读取 `DATE` 值
- **THEN** QueryResult 的逻辑类型为 `date`，HTTP 值为 `yyyy-MM-dd`

#### Scenario: Instant output preserves absolute time
- **WHEN** Data Adapter 读取带时区语义的时间戳
- **THEN** QueryResult 的逻辑类型为 `instant`，HTTP 值保留 UTC 或明确 offset，不转换为无时区字符串
