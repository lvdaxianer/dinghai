# MetricView DSL 规范对齐设计

## 目标

让 `dinghai-dsl-engine/docs/dsl-syntax.md` 成为公共 DSL 的 canonical
规范，并让 `metric-view-lifecycle` 通过复制快照消费同一套结构、操作符、
逻辑类型、时间和版本规则。

## 边界

- DSL 引擎仓只拥有 DSL envelope、relational-query v1、参数绑定、类型和
  运行限制。
- MetricView 的 `returnDef`、Python、Mock、`test/save/queryById` 和发布流程
  仍由 skill references 拥有。
- 不改 Java 运行时代码和接口行为。

## 方案

补充引擎文档中生成器需要的缺口，并新增结构 JSON Schema；skill 侧复制
`dsl-syntax.md` 和 schema，附加 MetricView-to-DSL binding 说明，再修正
skill 原有的节点名称和类型映射。时间窗口统一采用 `gte(start)` +
`lt(end)`，不把引擎未定义的 `not_in`/`between` 宣称为可用操作符。

## 验证

- JSON Schema 可被本地 JSON Schema 校验器加载。
- 引擎文档、skill 快照和 schema 的 DSL version 一致。
- skill 中不再把 `sources/dimensions/measures/filters/sort` 当作引擎 v1
  字段，也不再把 MySQL `TIMESTAMP` 统一映射为 `datetime`。
- 现有 skill 本地测试继续通过。
