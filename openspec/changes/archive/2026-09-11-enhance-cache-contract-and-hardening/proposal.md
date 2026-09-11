# Proposal: enhance-cache-contract-and-hardening

## Why

代码审查发现 `dinghai-shared-state` 存在三类真实缺口：自动装配中 `cacheEnvelopeCodec` Bean 实际不可被应用替换且缺少 `ObjectMapper` 前置条件防护；批量写入的部分失败语义未定义（Redis pipeline 忽略单条结果、Caffeine 中途异常留下部分写入）；缓存键无长度上限。同时契约缺少两个高频操作——读穿加载（cache-aside 样板代码与缓存击穿问题）与滑动续期（会话类状态刚需，现有 `get`+`expire` 两步存在非原子窗口）。此外库缺少 Micrometer/Actuator 标准可观测性出口、Maven Central 发布配置，且依赖的 Caffeine 2.9.3 与 Java 21/Boot 3 技术栈代际不符。

## What Changes

- 新增 `SharedStateCache.getOrLoad(key, expiry, loader)`：读穿加载，未命中时调用回源函数并写入；契约不保证跨进程单飞，Caffeine 后端保证单进程内按键合并回源。
- 新增 `SharedStateCache.getAndExpire(key, expiry)`：原子读取值并重置 TTL（滑动续期）；键不存在时返回空且不创建键。
- 修复自动装配：`cacheEnvelopeCodec` 增加 `@ConditionalOnMissingBean`，并仅在容器存在 `ObjectMapper` Bean 时装配；`JacksonCacheEnvelopeCodec` 依赖的 `ObjectMapper` 缺失时给出明确的装配失败信息。
- 键长度约束：`CacheKeyBuilder` 对 module/resource/identifier 片段及拼接后的完整键增加长度上限校验，超限抛 `IllegalArgumentException`。
- `putAll` 部分失败语义：Redis 后端在 pipeline 提交后校验执行结果，任何条目失败时抛出异常（fail-fast），不再静默忽略；契约文档明确批量操作不保证原子性、失败后状态为部分写入。
- 新增可选可观测性集成：存在 `MeterRegistry` 时自动注册指标（熔断状态 gauge 与七类计数器），存在 Actuator 时暴露 `HealthIndicator`；均为 `@ConditionalOnClass` 软依赖，不引入强制依赖。
- 增加 Maven Central 发布配置：POM 元数据（license/developers/scm）、sources/javadoc jar、GPG 签名与发布插件（release profile 隔离，日常构建不受影响）。
- 升级 Caffeine 2.9.3 → 3.x（Java 11+ 基线，与 Java 21/Boot 3 对齐）。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `shared-state-cache`：契约新增 `getOrLoad`/`getAndExpire` 需求；批量写入部分失败语义需求；键长度约束需求；自动装配可替换性与条件防护需求；可观测性标准集成需求。

## Impact

- 代码：`SharedStateCache` 接口（新增 2 个方法，属接口演进，调用方不实现该接口，非破坏性）、`CaffeineSharedStateCache`、`RedisSharedStateCache`、`ResilientSharedStateCache`、`SharedStateCacheConfiguration`、`CacheKeyBuilder`、可观测性相关类、README。
- 依赖：新增可选依赖 `micrometer-core`、可选依赖 `spring-boot-actuator`（provided/optional 语义）；Caffeine 2.9.3 → 3.x；构建插件新增 source/javadoc/gpg/发布插件。
- 兼容性：接口新增方法对注入使用者无破坏；自定义实现 `SharedStateCache` 的外部代码需要补实现（README 已声明实际注入类型为 `ResilientSharedStateCache`）。
- Redis 实现：`getAndExpire` 使用 Lua 脚本（GET+EXPIRE/PERSIST 单次原子执行），不依赖 Redis 6.2+ 的 `GETEX`，无服务端版本要求。
