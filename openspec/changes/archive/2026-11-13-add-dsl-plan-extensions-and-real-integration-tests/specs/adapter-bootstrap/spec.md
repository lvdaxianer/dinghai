# Spec Delta

## ADDED Requirements

### Requirement: Plan extension registry is frozen with adapter registries
系统 SHALL 在应用 readiness 前装配并校验计划扩展注册表，并与 DSL Adapter、Data Adapter 注册表一起冻结；扩展标识重复、顺序不确定或所需宿主能力缺失时 SHALL 启动失败。

#### Scenario: Valid extensions are available before readiness
- **WHEN** 应用完成启动
- **THEN** 所有已启用的计划扩展已经完成注册、排序并可被执行流水线使用

#### Scenario: Missing extension dependency fails startup
- **WHEN** 扩展声明的必需计划能力或宿主装配不存在
- **THEN** 应用在 readiness 前失败，不以跳过扩展或使用默认规则继续运行
