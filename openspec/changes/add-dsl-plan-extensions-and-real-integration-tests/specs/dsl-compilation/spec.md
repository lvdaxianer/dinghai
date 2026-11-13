# Spec Delta

## ADDED Requirements

### Requirement: Registered plan extensions are applied before backend compilation
通过基础解析和计划校验的 DSL SHALL 按稳定顺序应用适用的已注册计划扩展，并对扩展后的最终计划再次执行校验；未通过最终校验的计划 SHALL 不得进入 Data Adapter。

#### Scenario: Extension output reaches the selected backend
- **WHEN** DSL 解析成功且扩展增加了合法的类型化约束
- **THEN** 后端接收到包含该约束和绑定参数的最终执行计划

#### Scenario: Extension cannot bypass compilation validation
- **WHEN** 扩展返回原始 SQL、未解析标识符或不支持的计划能力
- **THEN** 编译流程拒绝请求，不把扩展内容交给后端编译器
