# Tasks: extract-dinghai-shared-state

## 1. T1 项目基线与构建骨架

- [x] 1.1 运行 project-hygiene 基线同步：检测 Maven/Java 技术栈，生成 `.gitignore`/`.gitattributes`/`.editorconfig` 托管块，输出 diff 后落盘
- [x] 1.2 创建 parent pom：`io.github.lvdaxianer:dinghai-parent:1.0.0-SNAPSHOT`，Java 21、UTF-8、parameters=true；dependencyManagement import `spring-boot-dependencies:3.5.5`，显式钉住 caffeine 2.9.3、commons-codec 1.15、commons-lang3 3.12.0、junit 4.13.2、mockito 4.11.0
- [x] 1.3 创建 `dinghai-shared-state` 模块 pom 与目录骨架，parent pom 注册 module
- [x] 1.4 提交设计文档 `docs/superpowers/specs/2026-09-10-dinghai-shared-state-design.md`（随本任务首个提交携带 OpenSpec 规划文件）
- [x] 1.5 验证：`mvn -q validate` 通过，基线文件 `git check-ignore -v` / `git check-attr -a` 抽查生效

## 2. T2 SPI 契约层迁移

- [ ] 2.1 RED：迁移 common 侧 SPI 测试（`CacheKeyBuilderTest`、`SharedStateContractModelTest`）至新包 `io.github.lvdaxianer.dinghai.sharedstate`，确认编译失败（被测类不存在）
- [ ] 2.2 GREEN：迁移 13 个 SPI 类（`SharedStateCache`、`CacheKey`、`BuiltCacheKey`、`CacheKeyBuilder`、`CacheKeyNamespace`、`CacheEnvelope`、`CacheEnvelopeCodec`、`CacheExpiry`、`CompareAndSetCommand`、`NamespacePolicy`、`NamespacePolicyRegistry`、`NamespaceFallbackPolicy`、`SharedStateUnavailableException`），包名改为 `io.github.lvdaxianer.dinghai.sharedstate`，类名与字段名不变
- [ ] 2.3 验证：`mvn -pl dinghai-shared-state test` 通过 SPI 测试

## 3. T3 缓存后端实现迁移

- [ ] 3.1 RED：迁移后端相关测试（`CaffeineSharedStateCacheTest`、`RedisSharedStateCacheTest`、`RedisSharedStateCacheContractTest`、`SharedStateCacheContract`、`SharedStateCacheAtomicContract`、`SharedStateCacheIncrementContract`、`SharedStateCacheAtomicTestSupport`、`SharedStateCacheTestExecutor`、`RedisContractConnectionFactory`、`MutableCacheValue`）
- [ ] 3.2 GREEN：迁移 Caffeine 后端（`CaffeineSharedStateCache`、`CaffeineStoredEntry`、`CaffeineStoredEntryExpiry`）、Redis 后端（`RedisSharedStateCache`、`RedisBackendOperations`、`RedisLuaScripts`、`RedisBackendException`）、编解码器（`JacksonCacheEnvelopeCodec`）
- [ ] 3.3 验证：`mvn -pl dinghai-shared-state test` 通过，含无 Redis 环境下契约测试自动跳过；本机有 Redis 时契约测试真实执行

## 4. T4 容错与可观测性迁移

- [ ] 4.1 RED：迁移容错与可观测性测试（`ResilientSharedStateCacheTest`、`ResilientSharedStateCacheMirrorTest`、`ResilientLocalSharedStateCacheContractTest`、`ResilientStateTtlTest`、`RedisCircuitBreakerTest`、`SharedStateCacheExecutionRaceTest`、`SharedStateCacheObservabilityTest`、`SharedStateCacheBackendSelectorTest`）
- [ ] 4.2 GREEN：迁移容错层（`ResilientSharedStateCache`、`ResilientSharedStateCacheOptions`、`RedisCircuitBreaker`、`SharedStateCircuitState`、`SharedStateCacheExecution`、`SharedStateCacheOperationPlan`、`SharedStateCacheBackendSelector`）与可观测性（`SharedStateCacheObservability`、`SharedStateCacheMetrics`、`SharedStateCacheMetricTotals`、`SharedStateCacheMetricsProvider`、`SharedStateCacheHealth`、`SharedStateCacheHealthProvider`）
- [ ] 4.3 验证：`mvn -pl dinghai-shared-state test` 通过

## 5. T5 自动装配与配置属性迁移

- [ ] 5.1 RED：迁移配置与装配测试（`SharedStateCacheConfigurationTest`、`RedisConnectionPropertiesTest`）
- [ ] 5.2 GREEN：迁移配置层（`SharedStateCacheConfiguration`、`SharedStateCacheProperties`、`RedisConnectionProperties`、`RedisConfiguredCondition`、`SentinelProperties`、`RedisSentinelNode`）；`@ConfigurationProperties` 前缀改为 `dinghai.cache`；`AutoConfiguration.imports` 改为新全限定类名
- [ ] 5.3 验证：`mvn -pl dinghai-shared-state test` 全量通过

## 6. T6 构建验证与发布准备

- [ ] 6.1 全量 `mvn clean verify` 通过（无 Redis 环境）
- [ ] 6.2 编写 README.md：项目简介、引入方式、`dinghai.cache.*` 配置项表、后端选择与容错行为说明
- [ ] 6.3 确认首个版本号（倾向 1.0.0）与 LICENSE（倾向 MIT），与用户确认后落盘
- [ ] 6.4 `mvn install` 产出本地可用构件
