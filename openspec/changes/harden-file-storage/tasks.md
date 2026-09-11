## 1. 规范同步

- [x] 1.1 将文件存储 Core/Local/MinIO/Starter 要求同步到主规范
- [x] 1.2 将 Web 预签名、预览、缓存与有界执行器要求同步到主规范

## 2. Web 执行器加固

- [x] 2.1 TDD：添加自动装配测试，先验证 Web 异步执行器存在且容量有界
- [x] 2.2 实现专用命名线程池并接入 Spring MVC 异步支持，配置优雅关闭
- [x] 2.3 重跑端点、Starter、全量 Maven 测试及 OpenSpec 严格校验
- [x] 2.4 完成 code-review-spec、backend-reliability 和 logging-boundaries 审计，提交并合并回 `main`
