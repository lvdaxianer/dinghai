# dinghai-shared-state 抽离设计（2026-09-10）

> 本文是 brainstorming 阶段经用户批准的设计文档。OpenSpec 变更资产见
> `openspec/changes/extract-dinghai-shared-state/`（proposal/design/specs/tasks），
> 二者内容一致，OpenSpec 为后续执行的权威追踪载体。

## 背景

dt-workspace 仓库的 `dt-workspace-sharedstate` 模块是一套零业务耦合的共享状态缓存能力
（Redis/Caffeine 双后端 + 熔断容错 + 可观测性 + Spring Boot 3 自动装配），
SPI 接口位于 `dt-workspace-common`，坐标挂在 `com.uino.industry` 下，无法独立复用。

已核实事实：

- SPI 13 个类 import 仅含 JDK，完全自洽；common 内无其他包引用它
- 实现层对仓库内的唯一引用即该 SPI 包
- 信封编解码为字段级 JSON、不内嵌类名，改包名不影响 Redis 存量数据
- Redis 契约测试自带可达性探测，无 Redis 环境自动跳过
- dt-workspace 已有消费 `io.github.lvdaxianer` 坐标的先例

## 设计决策

1. **单 jar**：SPI 与实现合并为 `dinghai-shared-state` 单一构件，不拆 api/impl
2. **坐标与包名**：`io.github.lvdaxianer:dinghai-shared-state`，
   包名 `io.github.lvdaxianer.dinghai.sharedstate`（品牌层入包名）
3. **依赖独立**：parent pom import `spring-boot-dependencies:3.5.5` BOM；
   caffeine 2.9.3 / commons-codec 1.15 / commons-lang3 3.12.0 / junit 4.13.2 /
   mockito 4.11.0 显式钉版，与来源仓库一致，不夹带升级
4. **迁移式 TDD**：每任务先迁测试（RED=编译失败），再迁生产类（GREEN=测试通过）
5. **契约保持**：类名、Bean 名称、信封 JSON 字段结构不变；
   配置前缀 `idt.cache` → `dinghai.cache`（去公司命名，仅影响配置绑定）
6. **仓库形态**：dinghai 多模块仓库，shared-state 为首个模块，家族扩容直接加 module

## 范围

- 含：仓库骨架、基线文件、SPI+实现+全部测试迁移、README、本地 install
- 不含：dt-workspace 侧切换（后续独立 change）、caffeine 3.x 升级、JUnit 5 迁移、
  Maven Central 发布配置

## 兼容性

- Redis 存量数据：信封 JSON 字段名不变 → 可读
- dt-workspace 切换成本：import 包名替换 + application.yml 配置键前缀替换，编译器兜底
