# Design

## Context

当前 `dinghai-file-storage-spring-boot-starter` 已装配同步 `FileStorage` provider，并为 Web 流式读取配置了独立执行器；核心 `FileStorage` 契约不应因异步任务能力而改变。项目已有 SQLite 本地持久化模式，但异步删除还需要支持宿主应用提供的关系型 `DataSource`，并且默认配置不能引入数据库启动要求。

## Goals / Non-Goals

**Goals:**

- 通过 Spring Boot 配置选择同步模式、SQLite 单机异步模式或宿主 `DataSource` 异步模式。
- 将任务登记、状态查询、租约抢占、有限重试和最终状态更新封装在独立服务中。
- 使单机重启恢复和多实例故障接管使用同一任务状态模型。
- 保持删除 provider 与 Web 读取线程池隔离，并让异步能力未启用时零运行时影响。

**Non-Goals:**

- 不改变 `FileStorage.delete(ObjectKey)` 的同步返回语义。
- 不把异步删除扩展为对象前缀批量删除、消息队列或跨服务分布式事务。
- 不把 SQLite 数据库文件放到共享网络文件系统上实现高可用。
- 不在本次变更中引入 JPA、Quartz 或 MQ。

## Decisions

### 1. 使用可选的删除模式自动装配

新增删除配置，默认 `sync`。只有 `mode=async` 时才创建任务存储、worker、删除线程池和调度器。`mode=async` 时通过 `database=sqlite` 创建本地 SQLite 数据源，或通过 `database=jdbc` 获取宿主 `DataSource`；异步模式配置不完整时启动失败，避免任务被登记到不可用的存储中。

现有同步 `FileStorage` Bean 不包装成异步实现，避免调用方在升级后无意间得到 `202` 或任务 ID。异步能力以独立的 Spring Bean 提供。

### 2. 使用 Spring JDBC 而不是完整 ORM

删除任务只有一张小型状态表，关键操作是带租约条件的 `UPDATE`，需要保留 SQL 影响行数来判断抢占是否成功。使用 `JdbcTemplate`、`DataSourceTransactionManager` 和参数化 SQL 可以共用 Java 任务模型，避免 ORM session factory、实体扫描和宿主 MyBatis 配置冲突；SQLite 和 MySQL/PostgreSQL 仅保留 schema 与必要的 SQL 方言差异。

候选任务先按状态和时间查询，再执行带 `status`、`lease_until` 和 `lease_token` 条件的更新。只有影响行数为 1 的实例获得任务，不依赖 `SKIP LOCKED`，因此 SQLite 单机和关系型数据库多实例均可工作。应用生成 UUID 作为任务 ID、租约 token 和 worker ID，时间以 Unix 毫秒写入，避免依赖数据库自增、枚举和时间函数。

### 3. 任务状态使用租约和 fencing token

任务记录包含 task id、storage alias、object key、状态、幂等 key、尝试次数、下一次执行时间、worker id、lease token、lease expiry、最后错误和时间戳。worker 抢占时生成新的 lease token；成功、重试和失败更新必须同时匹配 task id 与 lease token，防止旧 worker 在租约失效后覆盖新 worker 的结果。

worker 在数据库事务中只完成抢占，事务提交后才调用 provider；provider 调用完成后再用短事务更新状态。provider 删除成功以及返回对象不存在均标记 `SUCCESS`；超时、临时网络错误和存储不可用按退避策略重试，超过最大次数进入 `FAILED`。

### 4. 使用独立有界删除执行器

worker 轮询器只负责从任务表领取不超过执行器可用容量的任务，并将实际 provider 调用提交到带业务线程名的有界 `ThreadPoolExecutor`。队列满时不丢弃已经登记的任务，保留任务为可再次轮询状态。删除线程池的并发、队列、轮询间隔、租约时长和重试上限均可配置并做正数/范围校验。

### 5. 对外只新增异步任务服务 API

新增服务接口返回 task id 和任务状态查询结果；既有同步 provider API 不变。HTTP Controller 不纳入本次实现，避免在没有明确授权策略和 REST 模块文档边界时扩大公共接口；宿主应用可以直接注入服务或自行封装 HTTP 路由。

### Alternatives considered

- **MyBatis-Plus**：可复用项目中的 ORM 经验，但不能消除租约抢占 SQL 和 SQLite/MySQL schema 差异，还会引入额外 session factory 与宿主配置耦合；本变更的单表场景使用 Spring JDBC 更简单。
- **JPA/Hibernate**：实体 CRUD 方便，但 SQLite 方言、租约更新和 schema 生成仍需定制，且对 starter 的自动配置侵入更大。
- **MQ/Quartz/Redis Streams**：适合更大吞吐或已有基础设施，但本需求需要可查询的持久任务状态，额外引入这些组件会增加部署和恢复复杂度。

## Risks / Trade-offs

- [SQLite 只有单机写入能力] → 只允许 SQLite 作为显式单机模式，并在文档中说明不可放到共享文件系统；多实例使用宿主关系型数据库。
- [provider 删除成功但任务状态更新失败] → 删除操作按幂等语义重试，任务最终通过后续执行标记成功。
- [旧 worker 在租约过期后继续运行] → 所有结果更新匹配 fencing token，旧 worker 的更新影响行数为 0 时不得覆盖新状态。
- [数据库暂时不可用] → 已登记任务保留在数据库中；worker 记录安全告警并退避，未登记成功的请求明确失败，不宣称异步已接受。
- [删除任务占满线程池] → 使用独立有界线程池、租约超时、最大重试次数和队列容量校验。

## Migration Plan

1. 默认升级不改变任何行为，删除模式为 `sync`。
2. 单机部署显式配置 `mode=async` 和 SQLite 文件路径，启动时自动创建任务表。
3. 高可用部署显式配置 `mode=async`、`database=jdbc` 并复用宿主数据库连接，启动时执行幂等 schema 初始化。
4. 关闭异步模式前等待任务达到终态，或由运维保留任务表后切回同步模式；切回不会删除任务记录。
5. 回滚代码时保持任务表不删除，未完成任务可由兼容 worker 继续处理或由运维确认后清理。

## Open Questions

无。HTTP 路由、MQ 和批量前缀删除均明确不在本次变更范围内。
