# Proposal: extract-dinghai-shared-state

## Why

dt-workspace 项目中的 `dt-workspace-sharedstate` 模块是一套与业务完全无关的通用共享状态缓存能力（Redis/Caffeine 双后端 + 熔断兜底 + 可观测性 + Spring Boot 自动装配），但它被困在公司项目的多模块仓库里：SPI 接口散落在 `dt-workspace-common`，坐标挂在 `com.uino.industry` 下，无法被其他项目复用。本次变更将其抽离为 lvdaxianer 个人开源组件库 dinghai 的首个模块，使其成为零业务耦合、可独立发布、任意 Spring Boot 项目引入即用的公共组件。

## What Changes

- 创建 dinghai 多模块仓库骨架：parent pom（`io.github.lvdaxianer:dinghai-parent`）+ 首个模块 `dinghai-shared-state`
- 将 SPI 契约层（`SharedStateCache`、`CacheKey`、`CacheEnvelope` 等 13 个类，现位于 `dt-workspace-common` 的 `cache.sharedstate` 包）迁入新模块
- 将实现层（Redis/Caffeine 后端、容错包装、熔断器、可观测性、自动装配等 28 个类）从 `dt-workspace-sharedstate` 迁入新模块
- 包名统一为 `io.github.lvdaxianer.dinghai.sharedstate`（SPI 与实现合并为单一 jar）
- 依赖管理独立：import `spring-boot-dependencies:3.5.5` BOM，单独钉住 caffeine 2.9.3、commons-codec 1.15、commons-lang3 3.12.0、junit 4.13.2
- 迁移全部既有测试（20 个测试文件 + common 侧 2 个 SPI 测试），保持契约测试的 Redis 可达性自动跳过机制
- **保持不变**：类名、Bean 名称、信封 JSON 字段结构（保证 dt-workspace 侧切换时 Redis 存量数据零影响）；`@ConfigurationProperties` 前缀由 `idt.cache` 更名为 `dinghai.cache`（去公司命名，仅影响配置绑定）
- 建立项目基线文件（`.gitignore`/`.gitattributes`/`.editorconfig` 托管块）与 README

**BREAKING**：对 dt-workspace 而言，`com.uino.industry.common.cache.sharedstate` 与 `com.uino.industry.sharedstate` 两个包的类将迁至 `io.github.lvdaxianer.dinghai.sharedstate`，消费方需改 import（dt-workspace 侧切换属于后续独立变更，不在本 change 范围内）。

## Capabilities

### New Capabilities

- `shared-state-cache`: 共享状态缓存能力——统一的 `SharedStateCache` 契约（get/put/compareAndSet/increment/remove 等）、Redis 与 Caffeine 双后端、熔断器与本地镜像容错、命名空间回退策略、健康与指标快照、Spring Boot 3 自动装配

### Modified Capabilities

（无——dinghai 仓库暂无既有 specs）

## Impact

- **新仓库**：`/Users/lvdaxianer/workspace/my/project/dinghai`（本仓库）
- **源码来源**（只读，不在本 change 中修改）：
  - `dt-workspace_dev-v2/dt-workspace-common/src/main/java/com/uino/industry/common/cache/sharedstate/`（13 类 + 2 测试）
  - `dt-workspace_dev-v2/dt-workspace-sharedstate/src/`（28 主类 + 20 测试）
- **技术栈**：Java 21、Spring Boot 3.5.5、spring-data-redis 3.5.3、lettuce 6.6.0、caffeine 2.9.3、jackson-databind、junit 4.13.2、mockito 4.11.0
- **兼容性**：信封 JSON 为字段级序列化、不内嵌类名，包名迁移不影响 Redis 存量数据可读性
