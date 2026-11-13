# Design

## Context

当前项目已经实现通用 `DslRequest`、`DslAdapter` 和 `relational-query v1`，README 负责项目总览，`test-spring-boot/apis/dsl.md` 负责 REST 接口契约。语法规范应独立于 Spring Boot 测试应用，方便公共 DSL 引擎使用者阅读和后续扩展其他 DSL。

## Goals / Non-Goals

**Goals:**

- 建立一个公共、稳定、与后端数据库无关的 DSL 语法入口。
- 同时说明通用请求外层和当前关系 DSL 的完整结构。
- 让 README 与 REST API 文档通过链接复用该规范，而不是复制规则。
- 文档示例必须符合当前解析器、白名单和参数绑定行为。

**Non-Goals:**

- 不定义原始 SQL 接口。
- 不修改 DSL parser、AST、QueryPlan 或 DataAdapter 实现。
- 不把企业表权限、Token 白名单或数据库连接配置写入公共语法规范。

## Decisions

- **文档位置**：使用 `dinghai-dsl-engine/docs/dsl-syntax.md`，与 README 同属公共引擎文档，但不绑定测试应用。
- **内容分层**：先说明 `DslRequest` 通用 envelope，再说明 `relational-query v1` 的 payload 语法，最后说明安全边界和扩展约定。
- **引用关系**：README 只保留最小入口示例并链接语法文档；REST API 文档保留 HTTP 请求/响应契约，并链接语法文档作为字段语义来源。
- **示例来源**：使用当前代码和真实测试数据中已经支持的字段、操作符和结构，避免文档承诺未实现能力。

## Risks / Trade-offs

- [文档与实现可能漂移] → 在验收中使用当前解析器测试请求逐项核对，并在文档中明确版本号 `relational-query v1`。
- [README 信息变少后不熟悉项目的读者需要跳转] → README 保留调用链、最小 JSON 示例和明确的语法文档链接。
- [公共语法与企业 schema 白名单混淆] → 将逻辑字段规则与宿主白名单职责分开描述，不写入企业表清单。
