## Context

DSL 引擎的核心结果模型目前使用 `columns + rows` 表达查询结果。该结构适合驱动程序和表格渲染，但外部调用方需要按下标解释值，不能直接从响应看出字段含义。本次变更只改善结果表达，不改变 DSL 解析、计划编译、数据库适配器或执行边界。

## Decisions

### 1. 在核心结果模型提供派生的对象记录视图

`QueryResult` 保持现有四个 record 组件和构造方式不变，并增加 `records()` 方法。该方法以 `QueryColumn.name` 为键、以对应行值为值，返回不可变的 `List<Map<String, Object>>`。核心模块不依赖 Jackson 或 Spring。

每行必须与列数量一致；不一致时在构造结果时立即失败，避免生成部分映射。空值仍以 `null` 作为 Map 值保留。

如果输出列名重复，`records` 使用确定性的 `name`、`name#2`、`name#3` 后缀保证字段不会被后一个值覆盖；原始 `rows` 和 `columns` 不变。编译器正常生成的输出标签仍应保持唯一。

### 2. HTTP 层使用专用可读响应视图

测试 Spring Boot 工程新增响应 DTO，将核心 `QueryResult` 转为：

`columns`、`rows`、`records`、`rowCount`、`truncated`。

这样核心模块不引入 Web 序列化依赖，同时 HTTP 契约可以明确控制新增字段。保留 `rows` 是为了兼容已经按数组消费结果的调用方。

### 3. 不新增日志或敏感数据输出

本次只增加响应字段，不增加 SQL、绑定参数、密码、Token 或完整敏感值日志。既有 traceId 和审计边界保持不变。

## Alternatives Considered

- 只把 `rows` 改成对象数组：会破坏现有调用方，不采用。
- 让核心模型依赖 Jackson 注解直接序列化：会把 Web 框架耦合引入公共核心，不采用。
- 仅在 Controller 中临时组装 Map：会使核心结果映射逻辑无法复用，且容易绕过列和值一致性校验，不采用。

## Risks and Mitigations

- 结果行宽度不一致会从“可能返回错位数据”变为明确失败；通过核心回归测试覆盖，并确保现有适配器映射保持列数一致。
- 重复列名可能导致对象字段覆盖；通过确定性后缀处理，并保留原始数组结果。
