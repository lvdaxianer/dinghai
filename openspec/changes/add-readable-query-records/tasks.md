## 1. Core result mapping

- [x] 1.1 为 `QueryResult.records()` 和行列宽度约束补充 RED/GREEN 测试，覆盖多行、空值和重复列名。
- [x] 1.2 实现不可变的对象化记录映射，并确保现有 `rows` 构造和适配器测试继续通过。

## 2. HTTP response contract

- [ ] 2.1 为测试 Spring Boot 执行响应补充 RED/GREEN 测试，验证 `records`、空值保留和 `rows` 兼容。
- [ ] 2.2 实现响应 DTO 和控制器转换，不改变校验、解释接口和安全边界。

## 3. Documentation and verification

- [ ] 3.1 更新 `test-spring-boot/apis/dsl.md` 的成功响应、字段说明和空值示例。
- [ ] 3.2 运行严格 OpenSpec 校验、模块测试和真实 MySQL HTTP 验证，完成代码审查和任务审计。
