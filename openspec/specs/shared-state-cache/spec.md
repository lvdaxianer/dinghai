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

模块 SHALL 通过 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册自动装配：创建信封编解码器、Caffeine 后端、条件化 Redis 连接工厂与 Redis 后端、容错主缓存 Bean（`@Primary`）、健康与指标快照提供器；配置属性 SHALL 以前缀 `dinghai.cache` 绑定。

#### Scenario: 引入依赖即自动装配

- **WHEN** Spring Boot 3 应用将本模块加入类路径且未自定义相关 Bean
- **THEN** 容器中存在 `SharedStateCache` 主 Bean 及健康、指标快照提供器 Bean

#### Scenario: 配置属性绑定

- **WHEN** 应用在 `application.yml` 中配置 `dinghai.cache.redis.*` 等属性
- **THEN** 属性绑定到共享状态缓存配置对象并驱动后端选择与连接参数
