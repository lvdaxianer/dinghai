# shared-state-cache Specification

## Purpose

dinghai-shared-state 模块提供后端中立的共享状态缓存能力：统一的类型化缓存契约、Caffeine 与 Redis 双后端、Redis 故障熔断与本地镜像容错、命名空间回退策略、信封序列化契约、可观测性快照与 Spring Boot 3 自动装配。

## Requirements

### Requirement: 后端中立的共享状态缓存契约

模块 SHALL 提供 `SharedStateCache` 接口，以 `CacheKey<T>` 类型化键抽象共享状态访问，支持 `get`、`put`、`putIfAbsent`、`getAndDelete`、`increment`、`compareAndSet`、`delete`、`exists` 操作；原子操作的一致性范围 SHALL 由后端实现声明（Caffeine 仅保证单进程原子性，Redis 通过 Lua 脚本保证服务端原子性）。

#### Scenario: 写入并读取缓存值

- **WHEN** 调用方以某个 `CacheKey<T>` 执行 `put` 后执行 `get`
- **THEN** `get` 返回包含该值的 `Optional`，且值经信封编解码往返后与写入值相等

#### Scenario: putIfAbsent 的排他写入

- **WHEN** 同一键已存在值时调用 `putIfAbsent`
- **THEN** 返回 `false` 且既有值不被覆盖

#### Scenario: 原子自增

- **WHEN** 对同一 `CacheKey<Long>` 并发执行多次 `increment`
- **THEN** 每次调用返回单调递增至最终值序列中的唯一值，最终值等于全部增量之和（一致性范围以该后端声明为准）

#### Scenario: compareAndSet 条件更新

- **WHEN** 持有旧值快照的调用方执行 `compareAndSet`，且期间键已被其他调用方修改
- **THEN** 返回 `false`，键保持其他调用方写入的值

### Requirement: 可插拔双后端

模块 SHALL 提供 Caffeine（单进程本地）与 Redis（standalone/sentinel）两种后端实现，并提供后端选择器根据配置决定主后端：仅当共享状态专用 Redis 显式配置时启用 Redis 后端，其他业务 Redis Bean 不得隐式启用共享状态 Redis。

#### Scenario: 未配置 Redis 时使用 Caffeine

- **WHEN** 应用未配置共享状态专用 Redis 连接信息
- **THEN** 主后端为 Caffeine 后端，缓存操作在单进程内完成

#### Scenario: 配置 Redis 后使用 Redis

- **WHEN** 应用配置了共享状态专用 Redis（standalone 或 sentinel）
- **THEN** 主后端为 Redis 后端，缓存操作经由 Lettuce 连接执行

### Requirement: Redis 故障容错

模块 SHALL 提供容错包装后端：当 Redis 后端不可用时通过熔断器快速失败，并回退到本地镜像后端继续提供服务；熔断器 SHALL 在经历冷却窗口后允许探测性恢复。

#### Scenario: Redis 故障时回退本地镜像

- **WHEN** Redis 后端连续失败达到熔断阈值
- **THEN** 熔断器打开，后续读写操作由本地镜像后端承接，不再访问 Redis

#### Scenario: 熔断后的探测恢复

- **WHEN** 熔断器打开且冷却窗口已过
- **THEN** 允许一次对 Redis 的探测性调用，成功则关闭熔断器恢复 Redis 主后端

### Requirement: 命名空间回退策略

模块 SHALL 支持命名空间回退策略：当 Redis 不可用且所访问命名空间被策略标记为不允许本地回退时，操作 SHALL 抛出 `SharedStateUnavailableException` 而非静默使用本地镜像。

#### Scenario: 禁止回退的命名空间

- **WHEN** Redis 熔断打开，且操作的命名空间被策略注册为禁止本地回退
- **THEN** 操作抛出 `SharedStateUnavailableException`

### Requirement: 信封序列化契约

缓存值 SHALL 以 `CacheEnvelope` 信封结构序列化为字段级 JSON（不内嵌多态类型名），信封字段名在版本演进中保持向后兼容，保证旧版本写入的数据可被新版本读取。

#### Scenario: 信封 JSON 往返兼容

- **WHEN** 将包含值与元数据的信封编码为 JSON 后再按原值类型解码
- **THEN** 解码结果的值与元数据字段与编码前一致

### Requirement: 可观测性快照

模块 SHALL 提供健康快照与指标快照提供器，暴露当前主后端类型、熔断器状态、操作计数与失败计数，供应用健康检查与监控端点消费。

#### Scenario: 查询健康快照

- **WHEN** 应用调用健康快照提供器
- **THEN** 返回包含当前主后端标识与熔断器状态的快照对象

### Requirement: Spring Boot 自动装配

模块 SHALL 通过 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册自动装配：创建信封编解码器、Caffeine 后端、条件化 Redis 连接工厂与 Redis 后端、容错主缓存 Bean（`@Primary`）、健康与指标快照提供器；配置属性 SHALL 以前缀 `dinghai.cache` 绑定。信封编解码器 Bean MUST 声明 `@ConditionalOnMissingBean` 以允许应用提供自定义 `CacheEnvelopeCodec` 替换默认实现；默认 `JacksonCacheEnvelopeCodec` Bean MUST 仅在容器中存在 `ObjectMapper` Bean 时装配。

#### Scenario: 引入依赖即自动装配

- **WHEN** Spring Boot 3 应用将本模块加入类路径且未自定义相关 Bean
- **THEN** 容器中存在 `SharedStateCache` 主 Bean 及健康、指标快照提供器 Bean

#### Scenario: 配置属性绑定

- **WHEN** 应用在 `application.yml` 中配置 `dinghai.cache.redis.*` 等属性
- **THEN** 属性绑定到共享状态缓存配置对象并驱动后端选择与连接参数

#### Scenario: 应用自定义信封编解码器

- **WHEN** 应用注册了自定义 `CacheEnvelopeCodec` Bean
- **THEN** 自动装配不再创建默认编解码器，全部后端使用应用提供的实现

#### Scenario: 缺少 ObjectMapper 时跳过默认编解码器

- **WHEN** 容器中不存在 `ObjectMapper` Bean
- **THEN** 默认 `JacksonCacheEnvelopeCodec` 不参与装配，而非以莫名依赖错误失败

### Requirement: 常用原子状态操作

模块 SHALL 在 `SharedStateCache` 中提供条件删除和原子读取替换操作；Redis 实现 MUST 在服务器端完成这些操作，Caffeine 实现 MUST 在单进程内原子完成它们。

#### Scenario: 条件删除与原子替换

- **WHEN** 调用方以期望值执行 `compareAndDelete`，或以新值和到期策略执行 `getAndSet`
- **THEN** 条件删除仅在值匹配时删除，原子替换返回替换前的值并应用新到期策略

### Requirement: TTL 查询与生命周期管理

模块 SHALL 提供剩余 TTL 查询、设置 TTL 和取消 TTL 的操作；不存在键的 TTL 查询 MUST 返回空，永久键 MUST 映射为 `CacheExpiry.eternal()`，强制 TTL 命名空间 MUST 拒绝 `persist`。

#### Scenario: 管理普通键 TTL

- **WHEN** 调用方对普通命名空间已有键先执行 `expire` 再执行 `persist`
- **THEN** 两个操作均返回 `true`，且最终 TTL 查询返回 `CacheExpiry.eternal()`

#### Scenario: 强制 TTL 状态不能取消过期

- **WHEN** 调用方对 `isTtlRequired()` 为真的命名空间执行 `persist`
- **THEN** 操作抛出 `IllegalArgumentException`，键的既有过期时间不变

### Requirement: 批量共享状态访问

模块 SHALL 提供批量读取、具有逐条 TTL 的批量写入和批量删除；批量读取 MUST 只返回命中的键，批量删除 MUST 返回实际删除的唯一键数量。批量操作不保证原子性；批量写入在任一条目失败时 MUST 抛出异常（fail-fast），调用方 MUST 能感知失败，失败后缓存可能处于部分写入状态，此语义 MUST 在契约文档中明示。

#### Scenario: 使用独立 TTL 批量写入

- **WHEN** 调用方使用 `CacheValue` 条目执行 `putAll`，其中一个键为永久、另一个键为临时
- **THEN** 两个值均可读取，且 TTL 查询分别返回永久和临时状态

#### Scenario: 批量写入失败可被感知

- **WHEN** `putAll` 提交过程中任一条目写入失败（如后端连接错误）
- **THEN** 操作以异常告终，不会向调用方报告成功

### Requirement: 专用 Redis 连接隔离

Spring Boot 自动装配 SHALL 为显式配置的共享状态 Redis 创建并使用具名的 `sharedStateRedisConnectionFactory`；应用中已有业务 Redis 连接工厂 MUST 不改变共享状态 Redis 的连接配置。

#### Scenario: 业务 Redis 工厂与共享 Redis 配置共存

- **WHEN** 容器已有业务 `RedisConnectionFactory` 且 `dinghai.cache.redis.*` 已显式配置
- **THEN** `sharedStateRedisOperations` 绑定专用共享状态连接工厂，而非业务工厂

### Requirement: 读穿加载

模块 SHALL 在 `SharedStateCache` 中提供 `getOrLoad(key, expiry, loader)` 读穿操作：命中时直接返回缓存值且不调用 loader；未命中时调用 loader 回源并将非空结果按给定到期策略写入后返回。loader MUST NOT 返回 null，返回 null 时操作 SHALL 抛出 `IllegalArgumentException` 且不写入缓存。契约不保证跨进程单飞（loader 可能被并发调用多次）；Caffeine 后端 MUST 在单进程内按键合并并发回源，同一键同一时刻至多执行一次 loader。

#### Scenario: 未命中时回源并写入

- **WHEN** 调用方对不存在的键执行 `getOrLoad` 且 loader 返回非空值
- **THEN** 返回 loader 的值，且后续 `get` 命中该值并携带传入的到期策略

#### Scenario: 命中时不回源

- **WHEN** 调用方对已有值的键执行 `getOrLoad`
- **THEN** 返回缓存值，loader 不被调用

#### Scenario: Caffeine 后端并发回源合并

- **WHEN** 多个线程对同一缺失键并发执行 `getOrLoad`
- **THEN** Caffeine 后端内 loader 仅执行一次，全部调用方获得相同值

#### Scenario: loader 返回 null

- **WHEN** loader 返回 null
- **THEN** 操作抛出 `IllegalArgumentException`，缓存中不产生该键

### Requirement: 滑动续期

模块 SHALL 在 `SharedStateCache` 中提供 `getAndExpire(key, expiry)` 操作：原子地读取键的当前值并将其 TTL 重置为给定到期策略；键不存在时 MUST 返回空且不创建键；对永久键调用时 MUST 返回值并将键转为给定到期策略。强制 TTL 命名空间的键同样适用本操作（续期不违反强制 TTL 约束）。

#### Scenario: 读取并重置 TTL

- **WHEN** 调用方对已有键执行 `getAndExpire` 并传入新的到期时长
- **THEN** 返回原值，且剩余 TTL 更新为新时长

#### Scenario: 不存在键的滑动续期

- **WHEN** 调用方对不存在的键执行 `getAndExpire`
- **THEN** 返回空，键仍不存在

### Requirement: 缓存键长度约束

`CacheKeyBuilder` SHALL 拒绝超长键：module、resource、identifier 各片段长度 MUST 不超过 128 字符，拼接后的完整键长度 MUST 不超过 512 字符，超限 MUST 抛出 `IllegalArgumentException`（长度按原始片段计，先于 SHA-256 哈希替换判断）。

#### Scenario: 超长片段被拒绝

- **WHEN** 任一键片段超过 128 字符或完整键超过 512 字符
- **THEN** 键构建抛出 `IllegalArgumentException` 且不产生 `CacheKey`

### Requirement: 标准可观测性集成

模块 SHALL 提供可选的标准可观测性出口：类路径存在 Micrometer 且容器存在 `MeterRegistry` Bean 时，自动装配 MUST 注册共享状态缓存指标（熔断器状态 gauge 与操作事件计数器）；类路径存在 Spring Boot Actuator 时，自动装配 MUST 暴露基于健康快照的 `HealthIndicator`。上述集成 MUST 为软依赖，缺失时不影响其余 Bean 装配。

#### Scenario: 存在 MeterRegistry 时注册指标

- **WHEN** 应用类路径包含 Micrometer 且容器中存在 `MeterRegistry`
- **THEN** 共享状态缓存的熔断状态与操作计数指标注册到该 registry

#### Scenario: 缺失 Micrometer 时其余装配不受影响

- **WHEN** 应用类路径不包含 Micrometer 或容器中无 `MeterRegistry`
- **THEN** 缓存与健康/指标快照提供器 Bean 正常装配，不抛出异常
