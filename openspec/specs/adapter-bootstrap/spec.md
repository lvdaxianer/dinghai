# Adapter Bootstrap

## Purpose

在应用启动阶段可靠地装配并校验 DSL 与数据后端适配器，确保运行期间只使用明确配置、完整注册且连接能力满足要求的不可变 Registry。

## Requirements

### Requirement: Enabled adapters are assembled at startup
系统 SHALL 在应用启动阶段加载已注册的 DSL Adapter 和 Data Adapter，并根据配置确定启用项和默认 Data Adapter；请求处理期间不得临时创建或选择未注册的 Adapter。

#### Scenario: Configured adapters are available before readiness
- **WHEN** 应用完成启动并进入可用状态
- **THEN** 所有启用 DSL 类型、默认 Data Adapter 和所需后端组件均已装配

#### Scenario: Unknown data adapter fails startup
- **WHEN** 配置选择未实现或未注册的数据适配器类型
- **THEN** 应用启动失败并返回明确配置错误

### Requirement: Required backend configuration is validated
系统 SHALL 校验启用 Data Adapter 所需的 DataSource、驱动、连接配置和能力配置；缺少必需配置时 SHALL fail-fast，不得使用开发默认值或静默降级。

#### Scenario: Missing DataSource fails startup
- **WHEN** 配置启用数据库适配器但没有对应 DataSource
- **THEN** 应用在 readiness 前启动失败

#### Scenario: Valid DataSource is accepted
- **WHEN** 配置的 DataSource 和适配器能力满足要求
- **THEN** 系统完成该后端适配器装配并允许进入可用状态

### Requirement: Registry is immutable after bootstrap
启动校验成功后，DSL Adapter Registry 和 Data Adapter Registry SHALL 被冻结；运行期间注册表内容、默认适配器和能力映射不得被请求参数修改。

#### Scenario: Request cannot replace the adapter
- **WHEN** 请求携带未注册或不同于服务配置的 Adapter 标识
- **THEN** 系统拒绝该请求或忽略非法选择，并继续使用服务端已配置的适配器策略

#### Scenario: Duplicate adapter registration fails
- **WHEN** 两个适配器声明相同类型和版本且同时启用
- **THEN** 启动校验失败，不生成部分可用的 Registry

### Requirement: Bootstrap does not silently downgrade
系统 SHALL 对适配器缺失、能力不匹配和连接配置错误显式失败；不得把 PostgreSQL 请求改用 MySQL，也不得把未知 DSL 当成 SQL 或其他已注册 DSL 执行。

#### Scenario: PostgreSQL is not downgraded
- **WHEN** 配置要求 PostgreSQL 但 PostgreSQL Adapter 不可用
- **THEN** 启动或执行失败，并明确报告 PostgreSQL Adapter 缺失

#### Scenario: Unknown DSL is not executed as SQL
- **WHEN** 请求的 DSL 类型没有对应 Adapter
- **THEN** 系统拒绝请求，且不调用任何 SQL 执行器

### Requirement: Plan extension registry is frozen with adapter registries
系统 SHALL 在应用 readiness 前装配并校验计划扩展注册表，并与 DSL Adapter、Data Adapter 注册表一起冻结；扩展标识重复、顺序不确定或所需宿主能力缺失时 SHALL 启动失败。

#### Scenario: Valid extensions are available before readiness
- **WHEN** 应用完成启动
- **THEN** 所有已启用的计划扩展已经完成注册、排序并可被执行流水线使用

#### Scenario: Missing extension dependency fails startup
- **WHEN** 扩展声明的必需计划能力或宿主装配不存在
- **THEN** 应用在 readiness 前失败，不以跳过扩展或使用默认规则继续运行
