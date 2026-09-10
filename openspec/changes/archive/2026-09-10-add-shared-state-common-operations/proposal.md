## Why

`SharedStateCache` 目前覆盖单键读写、递增和比较更新，但缺少安全释放、原子替换、TTL 管理和批量访问能力。调用方为实现分布式锁、会话续期和批量缓存访问而自行组合多次调用时，会引入竞争窗口和多次 Redis 往返。

本次变更补齐这些通用能力，并确保 Redis、Caffeine 与容错门面对外保持一致的语义。

## What Changes

- 扩展 `SharedStateCache`：增加条件删除、原子读取替换、剩余 TTL 查询、设置 TTL、取消 TTL、批量读取、批量写入和批量删除。
- 新增 `CacheValue<T>`，让批量写入的每个键携带自己的值和到期策略。
- 为 Redis 后端增加原子 Lua 脚本，使用原生 TTL/MGET/DEL 及批量写入机制减少往返。
- 为 Caffeine 后端实现等价的单进程原子与 TTL 语义。
- 让容错门面将新操作纳入熔断、回退和本地镜像协调；TTL 状态按 Redis 的实际剩余 TTL 镜像。
- 修复自动装配中共享状态 Redis 可能复用其他业务 Redis 连接工厂的隔离缺口。

**BREAKING**：`SharedStateCache` 是公开接口，新增抽象方法会要求自定义实现补齐这些方法。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

- `shared-state-cache`: 扩展共享状态缓存公开契约、后端原子语义、TTL 行为、批量访问和独立 Redis 自动装配要求。

## Impact

- 修改 `dinghai-shared-state` 的公开 Java API、Redis Lua 脚本、Caffeine 条目模型、容错协调器和 Spring Boot 自动装配。
- 修改共享契约、Redis、Caffeine、容错和自动装配测试，并增加真实 Redis 契约覆盖。
- 不新增 Maven 依赖，也不改变既有单键方法的签名或语义。
