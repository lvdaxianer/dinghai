## Context

DSL 的逻辑类型是后端无关的语义，不应直接暴露 MySQL 或 PostgreSQL 类型名称。时间类型还涉及本地时间、绝对时间和时区转换；若把所有值都作为字符串交给 JDBC，驱动和数据库 session 时区会产生不一致结果。

## Decisions

### 1. 时间类型语义

- `date` 使用 `LocalDate`，格式为 `yyyy-MM-dd`，不包含时间和时区。
- `local_datetime` 使用 `LocalDateTime`，格式为 ISO 本地日期时间，不携带 offset。
- `instant` 使用 `Instant`，请求必须包含 `Z` 或明确 offset，内部按 UTC 绝对时间点处理。
- `datetime` 作为旧版本兼容别名，规范化为 `local_datetime`。

### 2. 解析与绑定边界

关系计划构建器负责将 HTTP JSON 中的字符串规范化为 Java 时间对象；Data Adapter 只负责将已经类型化的值绑定到 JDBC，并从 ResultSet 读取为统一 Java 时间对象。未知类型、错误格式和带错时区语义的输入必须失败。

### 3. 后端映射

- MySQL/Doris `DATE` → `date`。
- MySQL/Doris `DATETIME` → `local_datetime`。
- MySQL/Doris `TIMESTAMP` → `instant`，连接 session 时区必须由部署显式固定。
- PostgreSQL `DATE` → `date`。
- PostgreSQL `TIMESTAMP WITHOUT TIME ZONE` → `local_datetime`。
- PostgreSQL `TIMESTAMP WITH TIME ZONE` → `instant`。

适配器保留各自 JDBC 差异，但不把原生类型暴露给 DSL 请求。

### 4. 兼容与缓存

已有 `datetime` 请求继续可用，但计划和输出列使用规范化的 `local_datetime`。时间类型和时区策略属于计划语义，后续计划/编译缓存键必须包含对应版本信息，不能跨时区语义复用。

## Alternatives Considered

- 继续用单一 `datetime`：无法区分 MySQL `DATETIME`、MySQL `TIMESTAMP` 和 PostgreSQL `timestamptz`，不采用。
- 让 Controller 或业务端自行转换时间：会绕过公共 DSL 计划校验，并导致不同宿主行为不一致，不采用。
- 在 SQL 中调用 `DATE()`、`CONVERT_TZ()` 处理所有时间：容易破坏索引并依赖厂商语法，不作为本次基础类型实现。

## Risks and Mitigations

- JDBC 驱动返回类型存在差异：适配器同时支持标准 `java.time` 和兼容的 `java.sql` 类型，并以真实数据库测试验证。
- 旧 `datetime` 输出名称发生规范化：保留输入兼容，并在文档明确迁移到 `local_datetime`。
- session 时区未固定会影响 `instant`：启动配置和数据源部署必须显式指定时区，适配器不得读取 JVM 默认时区作为隐式策略。
