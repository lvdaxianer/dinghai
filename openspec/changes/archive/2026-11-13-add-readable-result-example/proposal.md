# Proposal

## Why

DSL 引擎已经支持更易读的 `records` 查询结果，但模块主 README 仍只展示 DSL 请求和数组形式的 `rows`，用户无法从入口文档直观看到推荐的响应结构。现在补充统一返回示例，降低接入和调试成本。

## What Changes

- 在 `dinghai-dsl-engine/README.md` 增加执行结果响应示例。
- 同时展示 `columns`、兼容性的 `rows` 和推荐使用的 `records`。
- 明确数据库 `NULL` 在 `records` 中保留为 JSON `null`。
- 不修改代码、接口行为、配置、依赖或数据库连接方式。

## Capabilities

### New Capabilities

无。本次仅补充已有行为的使用文档。

### Modified Capabilities

无。本次不改变系统需求或运行时行为，使用 `skip_specs: true`。

## Impact

- 仅影响 `dinghai-dsl-engine/README.md` 和本次变更的文档追踪文件。
- 不影响构建产物、运行时接口和已有调用方。
