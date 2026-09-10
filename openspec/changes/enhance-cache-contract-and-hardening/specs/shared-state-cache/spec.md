# Delta Spec: shared-state-cache

## ADDED Requirements

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

## MODIFIED Requirements

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

### Requirement: 批量共享状态访问

模块 SHALL 提供批量读取、具有逐条 TTL 的批量写入和批量删除；批量读取 MUST 只返回命中的键，批量删除 MUST 返回实际删除的唯一键数量。批量操作不保证原子性；批量写入在任一条目失败时 MUST 抛出异常（fail-fast），调用方 MUST 能感知失败，失败后缓存可能处于部分写入状态，此语义 MUST 在契约文档中明示。

#### Scenario: 使用独立 TTL 批量写入

- **WHEN** 调用方使用 `CacheValue` 条目执行 `putAll`，其中一个键为永久、另一个键为临时
- **THEN** 两个值均可读取，且 TTL 查询分别返回永久和临时状态

#### Scenario: 批量写入失败可被感知

- **WHEN** `putAll` 提交过程中任一条目写入失败（如后端连接错误）
- **THEN** 操作以异常告终，不会向调用方报告成功
