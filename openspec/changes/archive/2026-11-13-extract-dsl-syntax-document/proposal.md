# Proposal

## Why

当前 README 只提供 `relational-query v1` 的最小示例，完整语法规则分散在实现和测试 API 文档中。使用者无法从一个稳定入口了解 DSL 的请求封装、字段表达、参数类型、连接条件和限制，后续新增 DSL 也缺少文档边界。

## What Changes

- 新增独立的 DSL 语法规范文档，覆盖通用 `DslRequest` 封装和当前 `relational-query v1` 语法。
- 将字段、条件、JOIN、聚合、排序、分页、参数绑定、白名单和限制整理为可独立阅读的规范。
- README 保留架构定位和最小示例，并链接到独立语法文档。
- 测试 Spring Boot 的 REST API 文档引用语法规范，避免重复维护 DSL 语义。
- 不改变解析器、编译器、HTTP 接口或运行时行为。

## Capabilities

### New Capabilities

本变更为文档整理，不新增可执行系统能力；根据 OpenSpec 规则跳过行为规格文件。

### Modified Capabilities

无。

## Impact

- 新增 `dinghai-dsl-engine/docs/dsl-syntax.md`。
- 修改 `dinghai-dsl-engine/README.md` 和 `dinghai-dsl-engine/test-spring-boot/apis/dsl.md` 的文档导航与重复内容。
- 不新增依赖，不修改 Java 源码、配置或数据库。
