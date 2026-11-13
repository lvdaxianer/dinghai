# DSL Plan Extensions

## Purpose

为 DSL 引擎提供宿主系统可安全扩展计划语义的公共能力，使租户隔离、逻辑删除和领域数据范围等规则能够通过代码注册并作用于类型化计划，而不把 SQL 拼接、凭据或请求身份交给扩展。

## Requirements

### Requirement: Plan extensions are registered and ordered at startup
系统 SHALL 在启动阶段装配已注册的计划扩展，要求每个扩展具有唯一标识、明确适用范围和稳定顺序；注册完成后扩展集合 SHALL 不可变。

#### Scenario: Duplicate extension identifiers fail startup
- **WHEN** 两个已启用扩展声明相同标识
- **THEN** 应用启动失败，且不生成部分可用的扩展注册表

#### Scenario: Extensions run in deterministic order
- **WHEN** 多个扩展适用于同一个计划
- **THEN** 系统按照注册的稳定顺序依次应用扩展，不依赖请求参数或线程调度顺序

### Requirement: Extensions receive only typed plans and trusted context
计划扩展 SHALL 只能接收类型化 `ExecutionPlan`、服务端构造的 `QueryContext` 和 DSL 元数据；扩展接口 SHALL 不提供原始 SQL、SQL 片段、JDBC 连接、DataSource、数据库凭据、Token、HTTP 请求对象或原始请求体。

#### Scenario: Extension rewrites a typed relational plan
- **WHEN** 扩展为已解析表字段增加租户约束
- **THEN** 系统将扩展结果作为类型化计划继续校验和编译

#### Scenario: Extension cannot access secret or transport data
- **WHEN** 扩展执行期间尝试读取数据库凭据、Token 或 HTTP 请求内容
- **THEN** 扩展契约不提供这些对象，且请求仍只能使用服务端可信上下文

### Requirement: Relational constraints are parameterized and identifier-safe
关系型计划扩展 SHALL 通过已解析的表、别名、字段、逻辑类型、绑定值和约束位置表达限制；扩展不得提交 SQL 字符串或未解析标识符。`WHERE` 约束和 `JOIN ON` 约束 SHALL 依据声明位置写入最终计划。

#### Scenario: Left join scope is placed in JOIN ON
- **WHEN** 扩展为左连接表增加逻辑删除条件并声明 `JOIN ON`
- **THEN** 条件写入该连接的 ON 条件并保持左连接语义，不被错误移动到 WHERE

#### Scenario: Constraint values are bound
- **WHEN** 扩展增加租户值或逻辑删除值
- **THEN** 最终计划包含新的参数绑定，后端只能通过参数化命令传递该值

### Requirement: Extension output is validated before backend execution
系统 SHALL 在扩展应用前后执行计划和运行时边界校验；扩展失败、产生不支持的计划、突破查询限制或引入冲突参数时 SHALL fail-closed，且不得调用 Data Adapter。

#### Scenario: Invalid extension output is rejected
- **WHEN** 扩展返回包含未解析字段、非法 JOIN 或超出边界的计划
- **THEN** 系统返回稳定扩展或计划校验错误，并且不获取数据库连接

#### Scenario: Extension failure is isolated
- **WHEN** 扩展抛出未预期异常
- **THEN** 请求失败并保留安全诊断信息，不跳过扩展、不执行原始计划，也不降级到其他扩展
