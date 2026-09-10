# Design: enhance-cache-contract-and-hardening

## Context

`dinghai-shared-state` 提供后端中立的类型化缓存契约（`SharedStateCache`，16 个方法），双后端 Caffeine + Redis（Lettuce），外层 `ResilientSharedStateCache` 提供代次化熔断与本地镜像降级。一次全模块审查发现若干缺口：装配条件、批量失败语义、键长度约束、缺少读穿与滑动续期操作、缺少标准可观测性出口、发布配置缺失、Caffeine 版本代际不符。本设计覆盖这些项的技术决策。

## Goals / Non-Goals

**Goals:**

- 契约新增 `getOrLoad`（读穿）与 `getAndExpire`（滑动续期），两个后端与容错包装层全部实现。
- 修复自动装配的 codec 可替换性与 `ObjectMapper` 前置条件。
- `putAll` 失败可感知（fail-fast），语义文档化。
- 键长度上限校验。
- Micrometer / Actuator 软依赖集成。
- Maven Central 发布配置（release profile）。
- Caffeine 升级 3.x。

**Non-Goals:**

- 不引入 null 值缓存/防穿透哨兵（保持禁止存 null 的契约；防穿透由调用方或后续 `getOrLoad` 策略扩展解决）。
- 不引入异步/响应式 API、分布式锁、Redis 数据结构操作、`keys()`/`scan()`。
- 不改变信封格式、键结构、熔断器语义。
- GPG 签名密钥与 Sonatype 凭据的配置属发布操作，不在本变更内完成（仅提供构建配置）。

## Decisions

### D1: `getOrLoad` 签名与并发语义

签名：`<T> T getOrLoad(CacheKey<T> key, CacheExpiry expiry, Supplier<? extends T> loader)`。

- 返回值用 `T` 而非 `Optional<T>`：loader 禁止返回 null（违反抛 `IllegalArgumentException`），因此结果恒非空，避免调用方二次拆包。
- 跨进程单飞不做：分布式单飞需要锁，超出缓存契约边界。Caffeine 后端用 `asMap().computeIfAbsent` 实现单进程按键合并（天然原子）；Redis 后端用 `get` → loader → `putIfAbsent` → 命中冲突时重读 的顺序，保证最终返回值一致且以先写者为准。
- 备选方案（拒绝）：返回 `Optional` 允许空回源——会重新引入"无法区分空值与未缓存"的问题；契约级分布式锁——超范围。
- loader 抛异常：直接上抛，不计入熔断失败（属调用方/数据错误，与既有"数据错误不熔断"设计一致）。Resilient 层路由键存在时优先走主后端，Redis 故障时按命名空间策略降级或抛出。

### D2: `getAndExpire` 实现路径

签名：`<T> Optional<T> getAndExpire(CacheKey<T> key, CacheExpiry expiry)`。

- Redis：使用 `GETEX key EX seconds`（Redis 6.2+）；`CacheExpiry.eternal()` 时退化为 `GET`+`PERSIST` 的 Lua 原子脚本（GETEX PERSIST 亦可，统一用 GETEX PERSIST）。README 声明 Redis ≥ 6.2 要求。Lua 脚本一并处理键不存在的分支，保证原子性。
- Caffeine：`get` 后按新 expiry 重放 `put`（既有 per-key 过期映射机制），在单进程内可接受；接口文档声明"原子性以后端声明为准"的既有口径覆盖。
- 强制 TTL 命名空间：续期满足"始终有 TTL"，合法；`eternal()` 对强制 TTL 命名空间仍被拒绝（沿用 `put` 的既有校验路径）。
- 备选方案（拒绝）：`get`+`expire` 两步暴露给调用方——存在"读完即过期"窗口，正是本操作要消除的。

### D3: 装配条件修复

- `cacheEnvelopeCodec` bean 方法加 `@ConditionalOnMissingBean(CacheEnvelopeCodec.class)` 与 `@ConditionalOnBean(ObjectMapper.class)`。Jackson 已在依赖中，无需 `@ConditionalOnClass`。
- 风险点：容器无 `ObjectMapper` 时 codec 缺失会导致后端 bean 装配失败——`JacksonCacheEnvelopeCodec` 的注入点在后端构造器；Boot 3 + spring-web/jackson 场景必有 ObjectMapper，纯无 web 场景用户应自定义 codec 或引入 ObjectMapper bean，README 补充说明。

### D4: `putAll` fail-fast

- Redis：pipeline 提交后检查 `executePipelined` 回调中每条 `set` 的结果/异常，任一失败抛 `RedisBackendException`（计入熔断）。
- Caffeine：逐条 put 本身不会失败（校验已前置），语义不变。
- 文档：`SharedStateCache` javadoc 与 README 明示"批量操作非原子，失败后可能部分写入"。

### D5: 键长度上限

- 片段 ≤ 128 字符、完整键 ≤ 512 字符，在 `CacheKeyBuilder` 校验（与既有"非空白、不含冒号"同处），抛 `IllegalArgumentException`。上限参考 Redis 键实践（512MB 硬上限无指导意义；512 字符足够覆盖 `dinghai:` 前缀 + 三个合理片段）。

### D6: Micrometer / Actuator 软集成

- `micrometer-core` 与 `spring-boot-actuator` 以 `<optional>true</optional>` 引入。
- 新内部配置类 `@ConditionalOnClass(MeterRegistry.class)` + `@ConditionalOnBean(MeterRegistry)`：注册熔断状态 `Gauge`（CLOSED=0/HALF_OPEN=1/OPEN=2）与七类事件 `Counter`，数据源复用 `SharedStateCacheMetricsProvider` 快照差值（gauge 直读快照，counter 按快照差值累加，由 provider 轮询或事件钩子驱动——采用"指标注册器持有 observability 引用、counter 惰性绑定到事件回调"方案：直接让 `SharedStateCacheObservability` 暴露注册钩子，Micrometer 集成类订阅事件递增 counter，避免轮询）。
- Actuator：`@ConditionalOnClass(HealthIndicator.class)` 注册 `sharedStateCache` HealthIndicator，映射 `SharedStateCacheHealth`（degraded → `DOWN`？否——降级是可工作态，映射 `UP` 并在 details 中标注 `degraded=true`；仅在未配置 Redis 且 Caffeine 异常等无可用后端时 DOWN。简化：始终 UP，details 携带全部快照字段）。
- 备选方案（拒绝）：让 observability 直接依赖 Micrometer——破坏零强制依赖原则。

### D7: 发布配置

- 父 pom 增加 `licenses`/`developers`/`scm`/`url` 元数据；`release` profile 内含 `maven-source-plugin`、`maven-javadoc-plugin`、`maven-gpg-plugin`、`central-publishing-maven-plugin`（或 nexus-staging）。日常 `mvn test`/`package` 不触发签名。

### D8: Caffeine 3.x

- 升级至 Caffeine 3.1.x（Java 11+ 基线，jakarta 对齐）。API 兼容本项目用法（`Cache`/`asMap`/`expireAfter` 变体），升级后全量测试验证。若 3.x 与既有行为有出入（如过期语义），以测试为准修正。

## Risks / Trade-offs

- [接口新增方法破坏外部自定义实现] → 公开文档已声明注入类型为 `ResilientSharedStateCache`；README 变更日志注明实现者需补两个方法。
- [GETEX 要求 Redis 6.2+] → README 声明版本要求；低于 6.2 时 `getAndExpire` 用 Lua（GET+EXPIRE 单脚本）等价实现，运行时探测无必要——直接用 Lua 实现主路径，避免版本分支。修正 D2：Redis 统一使用 Lua 脚本实现 `getAndExpire`，无版本要求。
- [Micrometer counter 与内部计数器双份状态漂移] → Micrometer 侧只做事件订阅递增，不做快照回写；快照提供器仍是唯一查询入口。
- [release profile 未经验证的签名配置] → 仅保证 `-Prelease -DskipTests package` 在配置了 GPG 的环境中可用；README 记录发布前置条件。
- [Caffeine 3.x 行为差异] → 全量契约测试 + 容错测试作为回归网。

## Open Questions

（无）
