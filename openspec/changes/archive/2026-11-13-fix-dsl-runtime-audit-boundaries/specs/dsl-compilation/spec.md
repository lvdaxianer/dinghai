# Spec Delta

## MODIFIED Requirements

### Requirement: DSL input is parsed into a typed representation
每个 DSL Adapter SHALL 将请求内容解析为自己的类型化 AST 或语义模型；解析过程 SHALL 检查输入结构、字段类型、参数数量、`IN` 集合规模、大小和嵌套深度，并 SHALL 拒绝未知或不允许的结构。

#### Scenario: Valid DSL produces a typed model
- **WHEN** 请求结构、字段类型、参数规模和嵌套深度均符合 DSL 版本规范
- **THEN** Adapter 生成可供语义分析使用的类型化模型

#### Scenario: Malformed DSL is rejected before execution
- **WHEN** 请求包含缺失字段、错误类型、未知字段、过多绑定参数或超出输入边界的结构
- **THEN** 系统返回带有路径和稳定错误码的诊断结果，且不调用任何 Data Adapter

#### Scenario: Parameter type matches the resolved field
- **WHEN** 关系 DSL 的参数引用声明逻辑类型与白名单字段类型不一致
- **THEN** 计划构建阶段拒绝请求，不生成 JDBC 命令
