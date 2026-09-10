## ADDED Requirements

### Requirement: 常用原子状态操作
模块 SHALL 在 `SharedStateCache` 中提供条件删除和原子读取替换操作；Redis 实现 MUST 在服务器端完成这些操作的读取、比较和写入或删除，Caffeine 实现 MUST 在单进程内原子完成它们。

#### Scenario: 条件删除匹配的值
- **WHEN** 调用方对存有与期望值相等内容的键执行 `compareAndDelete`
- **THEN** 方法返回 `true`，并且该键不再存在

#### Scenario: 条件删除不匹配的值
- **WHEN** 调用方对存有不同内容或不存在的键执行 `compareAndDelete`
- **THEN** 方法返回 `false`，并且已有值保持不变

#### Scenario: 原子读取并替换
- **WHEN** 调用方对一个键执行 `getAndSet` 并提供新值和到期策略
- **THEN** 方法返回替换前的值（不存在时为空），且键保存新值并使用提供的到期策略

### Requirement: TTL 查询与生命周期管理
模块 SHALL 提供剩余 TTL 查询、设置 TTL 和取消 TTL 的操作；不存在键的 TTL 查询 MUST 返回空，永久键 MUST 映射为 `CacheExpiry.eternal()`，强制 TTL 命名空间 MUST 拒绝 `persist`。

#### Scenario: 查询临时值剩余 TTL
- **WHEN** 调用方读取一个尚未过期的临时键的 `remainingTtl`
- **THEN** 方法返回正的 `CacheExpiry`，且其 TTL 不大于写入时声明的 TTL

#### Scenario: 设置和取消普通键的 TTL
- **WHEN** 调用方对普通命名空间中的已有键先执行 `expire` 再执行 `persist`
- **THEN** `expire` 和 `persist` 均返回 `true`，并且最终 TTL 查询返回 `CacheExpiry.eternal()`

#### Scenario: 强制 TTL 状态不能取消过期
- **WHEN** 调用方对 `isTtlRequired()` 为真的命名空间执行 `persist`
- **THEN** 操作抛出 `IllegalArgumentException`，键的既有过期时间不变

### Requirement: 批量共享状态访问
模块 SHALL 提供批量读取、具有逐条 TTL 的批量写入和批量删除；批量读取 MUST 只返回命中的键，批量删除 MUST 返回实际删除的键数量。

#### Scenario: 按命中键批量读取
- **WHEN** 调用方以包含存在键和不存在键的集合执行 `getAll`
- **THEN** 返回 Map 只包含存在键及其类型化值

#### Scenario: 使用独立 TTL 批量写入
- **WHEN** 调用方使用 `CacheValue` 条目执行 `putAll`，其中一个键为永久、另一个键为临时
- **THEN** 两个值均可读取，且 TTL 查询分别返回永久和临时状态

#### Scenario: 批量删除返回实际数量
- **WHEN** 调用方对包含多个存在键、一个不存在键和重复键的集合执行 `deleteAll`
- **THEN** 返回值等于实际被删除的唯一存在键数量，所有存在键随后不可读取

### Requirement: 专用 Redis 连接隔离
Spring Boot 自动装配 SHALL 为显式配置的共享状态 Redis 创建并使用专用、具名的 `RedisConnectionFactory`；应用中已有业务 Redis 连接工厂 MUST 不改变共享状态 Redis 的连接配置。

#### Scenario: 业务 Redis 工厂与共享 Redis 配置共存
- **WHEN** 容器已有业务 `RedisConnectionFactory` 且 `dinghai.cache.redis.*` 已显式配置
- **THEN** 共享状态 Redis 模板绑定专用共享状态连接工厂，且其主机、端口、数据库和认证参数来自共享状态配置
