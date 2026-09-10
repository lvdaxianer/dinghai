# Tasks: enhance-cache-contract-and-hardening

每个顶层任务对应一个独立任务分支/工单（CHG-002-Tn），按顺序执行。

## 1. 装配条件修复（fixes/✅CHG-002-T1--装配条件修复）

- [x] 1.1 TDD：`SharedStateCacheConfigurationTest` 新增"应用自定义 CacheEnvelopeCodec 时默认实现退位""无 ObjectMapper 时默认 codec 不装配"场景
- [x] 1.2 `cacheEnvelopeCodec` Bean 增加 `@ConditionalOnMissingBean(CacheEnvelopeCodec.class)` 与 `@ConditionalOnBean(ObjectMapper.class)`
- [x] 1.3 README 补充 codec 可替换说明

## 2. 键长度约束（features/✅CHG-002-T2--缓存键长度约束）

- [x] 2.1 TDD：`CacheKeyBuilderTest` 新增片段超长、完整键超长、边界值（128/512）场景
- [x] 2.2 `CacheKeyBuilder` 增加片段 ≤128、完整键 ≤512 校验
- [x] 2.3 README 键格式章节补充长度约束

## 3. 批量写入 fail-fast（fixes/✅CHG-002-T3--批量写入快速失败）

- [x] 3.1 TDD：Redis 后端 `putAll` 条目失败时抛异常；契约 javadoc 语义固化
- [x] 3.2 `RedisSharedStateCache.putAll` 校验 pipeline 每条结果，失败抛 `RedisBackendException`
- [x] 3.3 `SharedStateCache` javadoc 与 README 明示批量非原子、失败可能部分写入

## 4. 读穿加载 getOrLoad（features/✅CHG-002-T4--读穿加载方法）

- [x] 4.1 TDD：契约测试覆盖未命中回源写入、命中不回源、loader 返回 null 拒绝、Caffeine 并发回源合并
- [x] 4.2 `SharedStateCache` 新增 `getOrLoad`；`CaffeineSharedStateCache`（computeIfAbsent 合并）、`RedisSharedStateCache`（get→loader→putIfAbsent→重读）、`ResilientSharedStateCache`（路由/降级）实现
- [x] 4.3 README 操作列表与示例补充

## 5. 滑动续期 getAndExpire（features/✅CHG-002-T5--滑动续期方法）

- [ ] 5.1 TDD：契约测试覆盖读取并重置 TTL、不存在键返回空、永久键续期、强制 TTL 命名空间续期合法
- [ ] 5.2 `SharedStateCache` 新增 `getAndExpire`；Redis 用 Lua（GET+EXPIRE/PERSIST）原子实现，Caffeine 与 Resilient 层实现
- [ ] 5.3 README 操作列表与 TTL 章节补充

## 6. 标准可观测性集成（features/✅CHG-002-T6--标准可观测性集成）

- [ ] 6.1 TDD：存在/缺失 MeterRegistry、存在 Actuator 时装配行为；指标注册与计数递增；HealthIndicator 输出
- [ ] 6.2 pom 增加 optional 依赖 micrometer-core、spring-boot-actuator；`SharedStateCacheObservability` 暴露事件订阅钩子
- [ ] 6.3 Micrometer 指标注册类（熔断 gauge + 事件 counter）与 Actuator HealthIndicator 配置类，均软条件装配
- [ ] 6.4 README 可观测性章节补充

## 7. Maven Central 发布配置（features/✅CHG-002-T7--发布配置补齐）

- [ ] 7.1 父 pom 增加 licenses/developers/scm/url 元数据
- [ ] 7.2 release profile：sources/javadoc/gpg/central-publishing 插件
- [ ] 7.3 验证 `mvn -q -Prelease -DskipTests -Dgpg.skip=true package` 产物齐全；README 记录发布前置条件

## 8. Caffeine 3.x 升级（features/✅CHG-002-T8--Caffeine版本升级）

- [ ] 8.1 升级 Caffeine 至 3.x 并消除 API 差异
- [ ] 8.2 全量测试回归（含契约与容错测试）
- [ ] 8.3 README 依赖说明同步

## 9. 收尾

- [ ] 9.1 最终审计：全部任务门禁通过、验收标准满足、无实现漂移
- [ ] 9.2 归档 OpenSpec 变更并同步主规格
