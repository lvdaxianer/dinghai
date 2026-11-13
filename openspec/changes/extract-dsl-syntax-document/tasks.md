# Tasks

## 1. DSL 语法规范

- [x] 1.1 新增 `docs/dsl-syntax.md`，完整描述通用 `DslRequest`、`relational-query v1` payload、字段/条件/JOIN/聚合/排序/分页、参数类型、限制和非法示例；通过实现字段核对验证文档不承诺未实现语法。
- [x] 1.2 在语法文档中补充 DSL 与 QueryPlan/DataAdapter 的边界、白名单和企业权限职责；通过检索确认文档不允许原始 SQL、数据库凭据或客户端 adapter 选择。

## 2. 文档导航与一致性

- [ ] 2.1 精简 README 中重复的完整语法内容，保留架构定位和最小示例并链接独立语法文档；通过 Markdown 链接和示例检查验证入口可用。
- [ ] 2.2 在测试 API 文档中引用独立语法规范，保留 HTTP 请求/响应和认证边界；通过全文检索确认语法规则只有一个公共规范来源。

## 3. 集成验证

- [ ] 3.1 执行 OpenSpec strict 校验、Markdown 链接检查、工作区卫生检查，并审查文档与当前 parser/test fixture 的一致性。

## Workflow follow-up

- 通过文档审查后提交任务分支并合并回 `main`。
- 归档本次 OpenSpec change，并确认主分支包含独立语法文档。
