## 1. Temporal logical model

- [x] 1.1 为 `date`、`local_datetime`、`instant` 和 `datetime` 兼容别名补充 RED/GREEN 测试。
- [x] 1.2 在关系计划构建阶段规范化逻辑类型和时间参数，拒绝错误格式、缺失 offset 和未知类型。

## 2. JDBC temporal adapters

- [x] 2.1 为 MySQL/Doris 和 PostgreSQL 适配器补充时间绑定、结果映射和失败测试。
- [x] 2.2 实现 `DATE`、本地日期时间、绝对时间的类型化 JDBC 处理，不依赖 JVM 默认时区。

## 3. Documentation and integration

- [ ] 3.1 更新 DSL 语法、README 和测试 API 文档，说明时间格式和时区语义。
- [ ] 3.2 使用真实 MySQL、Doris MySQL 协议和 PostgreSQL 验证时间结果，完成审计、归档和合并。
