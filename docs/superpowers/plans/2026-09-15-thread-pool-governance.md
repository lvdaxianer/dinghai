# 线程池治理实施计划

## 任务

- [ ] 新增 Maven 聚合模块与 SQLite 依赖，验证全仓构建可解析。
- [ ] 先写 core 配额和全局许可 RED 测试，再实现受控执行器。
- [ ] 先写 starter SQLite cursor RED 测试，再实现持久化、配置与自动装配。
- [ ] 先写总控 cursor 续拉 RED 测试，再实现 SQLite 存储和单实例采集器。
- [ ] 接入 file-storage Web 执行器，并完成模块及全仓验证。
- [ ] 完成可靠性、日志与代码规范审查，提交原子变更。
