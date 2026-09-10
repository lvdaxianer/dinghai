# Design: extract-dinghai-shared-state

## Context

`dt-workspace-sharedstate`（位于公司仓库 dt-workspace_dev-v2）是一套成熟的共享状态缓存实现：主代码 28 类约 3800 行、测试 20 个文件约 3200 行，支撑登录态、会话许可、限流、分布式任务锁等场景。经核实的关键事实：

- SPI 契约层（13 个类）位于 `dt-workspace-common` 的 `com.uino.industry.common.cache.sharedstate` 包，import 仅含 JDK，完全自洽；common 内无其他包引用它
- 实现层对仓库内的唯一引用就是上述 SPI 包，无任何业务反向依赖
- `JacksonCacheEnvelopeCodec` 为字段级 JSON 序列化，不内嵌多态类型名 → 改包名不影响 Redis 存量数据
- Redis 契约测试自带可达性探测（100ms TCP 超时，不可达自动跳过），可在无 Redis 的 CI 环境跑绿
- 已是 Spring Boot 3 标准自动装配（`AutoConfiguration.imports`）
- dt-workspace 根 pom 已有消费 `io.github.lvdaxianer` 个人坐标的先例（breakpoint-transfer-spring-boot3-starter）

## Goals / Non-Goals

**Goals:**

- 建立 dinghai 多模块仓库：parent pom + `dinghai-shared-state` 模块，`io.github.lvdaxianer` 坐标，Java 21
- 完整迁移 SPI + 实现 + 全部测试，包名统一为 `io.github.lvdaxianer.dinghai.sharedstate`
- 依赖管理完全独立于 dt-workspace / uino-plate-parent
- `mvn clean verify` 在无 Redis 环境下全绿；有 Redis 时契约测试真实执行
- 保持类名、配置前缀、Bean 名称、信封 JSON 字段结构不变，为 dt-workspace 侧平滑切换留好接口

**Non-Goals:**

- 不修改 dt-workspace 仓库的任何文件（其切换是后续独立 change）
- 不做功能增强：不升级 caffeine 3.x、不迁移 JUnit 5、不加新后端
- 不配置 Maven Central 发布（后续迭代）
- 不拆分 api/impl 双 artifact（单 jar 即可，当前无多实现并存需求）

## Decisions

### D1: 单 jar 合并 SPI 与实现，而非 api/impl 双模块

现有"接口在 common、实现在 sharedstate"的分离是 dt-workspace 仓库结构所迫，不是设计意图。 dinghai 中合并为单一 `dinghai-shared-state` jar：消费方引一个依赖即可用。备选方案（api/impl 拆分）被否决——当前只有一个实现，拆分只增加发布复杂度；未来若出现第二实现再拆不迟。

### D2: 包名 `io.github.lvdaxianer.dinghai.sharedstate`，品牌层入包名

`io.github.lvdaxianer` 是 Maven Central 认可的个人 groupId（对应 GitHub 账号）。包名带 `dinghai` 品牌层，避免同 groupId 下未来不同组件家族撞包。备选（保留旧包名减少消费方改动）被否决——个人开源作品不应保留公司域名包名，IDE 重构改 import 是一次性成本。

### D3: 依赖管理用 spring-boot-dependencies BOM + 显式钉版

parent pom import `spring-boot-dependencies:3.5.5`（管理 jackson、spring-data-redis、lettuce、spring-test、mockito），但 **caffeine 显式钉 2.9.3**（不用 BOM 的 3.x，避免抽离时夹带行为变化）、commons-codec 1.15、commons-lang3 3.12.0、junit 4.13.2 与 dt-workspace 现状对齐。备选（全部跟 BOM 走）被否决——caffeine 2→3 是独立决策，不应混入抽离变更。

### D4: 迁移式 TDD

本 change 是代码迁移而非新开发。每个迁移任务的 TDD 解释为：先将既有测试文件迁入新包结构（RED——因被测类尚不存在而编译失败），再迁入被测生产类（GREEN——测试通过）。测试内容本身不改写逻辑，仅改包名与 import。

### D5: 任务切分按代码分层

T1 基线与构建骨架 → T2 SPI 契约层 → T3 缓存后端（Caffeine/Redis/Codec/Lua）→ T4 容错与可观测性 → T5 自动装配与配置 → T6 构建验证与 README。每层可被独立验证（编译 + 对应测试集），任务间存在依赖链，顺序执行不并行。

### D6: 序列化契约不变；配置前缀去公司化

`CacheEnvelope` 字段名、`sharedStateRedisOperations` 等 Bean 名称原样保留，保证 Redis 存量数据可读、Bean 覆盖语义不变。但 `@ConfigurationProperties` 前缀从 `idt.cache` 改为 `dinghai.cache`——`idt` 是公司项目命名，出现在开源库里对外无意义。该前缀只影响配置绑定、不影响已存数据，dt-workspace 侧切换时同步改 application.yml 键名即可（属后续 change）。

## Risks / Trade-offs

- [SPI 测试依赖 common 侧测试基类或遗漏文件] → 迁移前用 grep 全量枚举两个源码目录的文件清单，迁移后逐文件核对
- [BOM 管理的 jackson 版本与原 uino-plate-parent 传递版本不一致导致序列化行为差异] → 契约测试（`RedisSharedStateCacheContractTest` 等）覆盖信封编解码，跑绿即证明行为一致
- [改包名时 sed 误伤注释/字符串中的合法文本] → 迁移后全量编译 + 测试验证；review 时抽查 Javadoc
- [JUnit 4 + BOM 管理的 mockito 版本组合兼容性问题] → mockito 显式钉 4.11.0 与原仓库一致，不跟 BOM

## Migration Plan

1. T1 在 dinghai 仓库建立基线与构建骨架（不触碰源码）
2. T2-T5 按层迁移源码与测试，每层迁移后 `mvn test` 验证
3. T6 全量 `mvn clean verify` + README
4. dinghai `mvn install` 到本地仓库，产出 `io.github.lvdaxianer:dinghai-shared-state:1.0.0-SNAPSHOT`
5. dt-workspace 侧切换（删除旧模块、改 import）为后续独立 change，需 dinghai 先发 1.0.0 正式版

回滚策略：dinghai 是新仓库，任何阶段失败直接删分支/仓库重来即可，无线上影响。

## Open Questions

- 首个发布版本号：倾向 `1.0.0`（代码已在生产验证），T6 时确认
- LICENSE 选型（MIT/Apache-2.0）：T6 时确认，倾向 MIT
