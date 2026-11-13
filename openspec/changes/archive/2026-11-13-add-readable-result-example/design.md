# Design

## Scope

在 DSL 请求示例之后增加“执行结果”小节，使用与真实 MySQL HTTP 验证一致的列名和字段结构。示例保留 `rows` 以说明兼容性，并将 `records` 标为推荐读取方式。

## Verification

- 检查示例字段与当前 `/api/test/dsl/execute` 响应模型一致。
- 检查 Markdown 链接、代码块和差异内容。
- 运行 OpenSpec 严格校验并确认主分支合并后工作树干净。
