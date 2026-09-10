## Context

`dinghai-shared-state` 已提供类型化单键操作、Redis/Caffeine 双后端、Redis 故障回退和 Spring Boot 自动装配。当前公开契约缺少安全释放、原子替换、TTL 查询和批量操作，业务方只能通过多个单键调用组合这些行为。

所有新增行为必须在 Redis 的跨节点场景与 Caffeine 的单进程场景中保持可解释的等价语义，并纳入现有 `ResilientSharedStateCache` 熔断和命名空间回退策略。Redis 专用连接配置也必须与应用中其他业务 Redis 连接隔离。

## Goals / Non-Goals

**Goals:**

- 提供条件删除、原子读取替换、TTL 查询与管理、批量读写删除的后端中立 API。
- 让每个批量写入条目具有独立的值和 TTL。
- 保证 Redis 条件删除和读取替换为服务器端原子操作。
- 让容错镜像可以使用 Redis 返回的真实剩余 TTL。
- 确保显式配置的共享状态 Redis 不会复用业务 `RedisConnectionFactory`。

**Non-Goals:**

- 不引入扫描、按前缀删除、全量清理等可能阻塞 Redis 的 API。
- 不新增分布式锁、限流器或会话管理领域抽象；调用方可用底层原子 API 组合它们。
- 不改变既有八个单键操作的签名或既有缓存键、信封 JSON 格式。
- 不新增 Redis、Caffeine 或 Spring Boot 依赖。

## Decisions

### D1: 直接扩展 `SharedStateCache`，而不是在容错实现上增加专有 API

新增 `compareAndDelete`、`getAndSet`、`remainingTtl`、`expire`、`persist`、`getAll`、`putAll` 和 `deleteAll`。这使业务可继续只依赖后端中立接口，并要求三个现有实现共同维护语义。备选方案是只扩展 `ResilientSharedStateCache`；该方案会迫使应用依赖具体实现，予以拒绝。

### D2: 用 `CacheValue<T>` 表达批量条目

`putAll` 使用 `Map<CacheKey<T>, CacheValue<T>>`，每个键独立持有值和 `CacheExpiry`。该模型避免为批量操作错误地设计单个共享 TTL，也拒绝空值和空过期策略。备选的三元组集合不如 Map 能显式拒绝重复键，予以拒绝。

### D3: TTL 对外统一映射为 `Optional<CacheExpiry>`

`remainingTtl` 在键不存在时返回空，在永久键时返回 `CacheExpiry.eternal()`，在临时键时返回正 TTL。Redis 通过 `PTTL` 映射 `-2/-1/正值`；Caffeine 按存储条目的绝对到期纳秒计算。该选择复用已有 TTL 值对象，避免暴露 Redis 的负数哨兵值。

### D4: Redis 仅将必须原子的操作放入 Lua

`compareAndDelete` 和 `getAndSet` 使用 Lua，确保比较/删除与获取/替换不出现客户端竞争窗口。批量读取使用 `MGET`、删除使用单个 `DEL`，批量写入按照永久/TTL 条目分组并通过 Redis pipeline 执行。备选的所有批量操作 Lua 化会增加脚本复杂度且不能提升独立键批处理的业务语义，予以拒绝。

### D5: 容错层始终以 Redis 成功结果校准本地镜像

Redis 成功的读、替换、TTL 变更和批量操作将同步、更新或删除对应 Caffeine 镜像。TTL 状态使用 `remainingTtl` 的真实剩余值预热，无法确认的镜像直接删除。Redis 故障继续通过现有命名空间策略决定本地回退或 fail-closed。

### D6: 使用具名的专用 Redis 连接工厂

自动装配创建名为 `sharedStateRedisConnectionFactory` 的 `LettuceConnectionFactory`，共享模板用 `@Qualifier` 注入它。该工厂仅取 `dinghai.cache.redis.*` 参数，不受业务工厂是否存在影响。备选的 `@ConditionalOnMissingBean(RedisConnectionFactory.class)` 会在业务工厂存在时复用其连接，违反组件隔离要求，予以拒绝。

## Risks / Trade-offs

- [公开接口新增抽象方法会影响自定义实现] → 在版本说明和 Javadoc 中标注破坏性 API 变更；三个内置实现和契约测试同时更新。
- [Caffeine TTL 为近似剩余时间] → 用单调 `Ticker` 计算，返回值只表示调用瞬间的剩余有效期；测试采用可控 ticker。
- [批量写入部分 Redis 命令失败] → Redis pipeline 不提供跨键事务；对外明确每个键独立生效，失败由 `RedisBackendException` 进入现有熔断策略。
- [大批量占用 Redis 或内存] → 不增加隐式分页或扫描；调用方负责合理批次，测试确保实现没有逐键远程调用。

## Migration Plan

1. 发布前让所有内置实现与测试适配扩展后的接口。
2. Spring Boot 应用升级后，已配置 `dinghai.cache.redis.*` 的实例会改用专用命名连接工厂；无配置的实例继续使用 Caffeine。
3. 若升级需回退，应用可回退到旧构件版本；本次不修改 Redis 键格式、信封格式或现有数据，因此无需数据迁移。

## Open Questions

无。批量写入的独立 TTL、TTL 返回模型和强制 TTL 命名空间的 `persist` 拒绝行为均已确认。
