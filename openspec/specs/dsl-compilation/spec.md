# DSL Compilation

## Purpose

为公共组件提供领域无关、可版本化且可扩展的 DSL 编译能力，使不同 DSL 能在不暴露原始 SQL 的前提下生成统一、可校验的执行计划。

## Requirements

### Requirement: DSL adapter selection is exact and versioned
系统 SHALL 根据请求中的 DSL 类型和版本选择唯一已启用的 DSL Adapter；未知类型、未启用类型、不支持版本或重复注册 SHALL 被拒绝，且不得回退到其他 Adapter。

#### Scenario: Registered DSL version is selected
- **WHEN** 请求包含已注册类型和已支持版本
- **THEN** 系统使用该类型和版本对应的 Adapter 继续解析

#### Scenario: Unknown DSL is rejected
- **WHEN** 请求包含未注册或未启用的 DSL 类型
- **THEN** 系统返回稳定的 DSL 类型错误，且不尝试执行请求内容

#### Scenario: Unsupported version is rejected
- **WHEN** 请求的 DSL 类型已注册但版本不受支持
- **THEN** 系统返回稳定的 DSL 版本错误，且不降级到其他版本

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

### Requirement: Compilation produces a backend-neutral execution plan
通过语法和语义校验的 DSL SHALL 被编译为不可变的 `ExecutionPlan`；计划 SHALL 只包含已解析的结构、类型、参数绑定和执行约束，不得包含原始 SQL 或未校验的 SQL 片段。

#### Scenario: Valid DSL compiles to an immutable plan
- **WHEN** DSL 通过语法、引用、类型和语义校验
- **THEN** 系统返回可被后端适配器消费的不可变 `ExecutionPlan`

#### Scenario: Raw SQL escape hatch is rejected
- **WHEN** DSL 提交 SQL 字符串、SQL 片段或未注册表达式
- **THEN** 系统拒绝该 DSL，且不把内容传递给后端编译器或数据库

### Requirement: Compilation diagnostics are stable and safe
DSL 解析和编译失败 SHALL 返回稳定错误码、可定位的 DSL 路径和安全诊断信息；诊断 SHALL 不包含密码、Token、完整敏感参数或数据库连接信息。

#### Scenario: Validation error identifies the DSL path
- **WHEN** DSL 的字段、引用或表达式不满足语义规则
- **THEN** 结果包含稳定错误码和对应节点路径，便于调用方修正请求

#### Scenario: Sensitive value is not exposed in diagnostics
- **WHEN** 解析或校验失败涉及敏感参数
- **THEN** 诊断只返回脱敏后的类型或参数名，不返回敏感值

### Requirement: Registered plan extensions are applied before backend compilation
通过基础解析和计划校验的 DSL SHALL 按稳定顺序应用适用的已注册计划扩展，并对扩展后的最终计划再次执行校验；未通过最终校验的计划 SHALL 不得进入 Data Adapter。

#### Scenario: Extension output reaches the selected backend
- **WHEN** DSL 解析成功且扩展增加了合法的类型化约束
- **THEN** 后端接收到包含该约束和绑定参数的最终执行计划

#### Scenario: Extension cannot bypass compilation validation
- **WHEN** 扩展返回原始 SQL、未解析标识符或不支持的计划能力
- **THEN** 编译流程拒绝请求，不把扩展内容交给后端编译器
