## 1. 安全引用与对象枚举契约

- [x] 1.1 添加 AES-SIV 依赖、稳定 `FileReference`/ID 编解码服务及篡改、稳定性、轮换失效的 RED/GREEN 测试。
- [x] 1.2 为 `FileStorage` 增加可关闭的惰性对象枚举，并实现 Local 与 MinIO 枚举及资源关闭测试。

## 2. 统一私有读取代理

- [x] 2.1 将 Web Controller 改为依赖通用 `FileStorage`，保留旧预签名和 direct access 行为的回归测试。
- [x] 2.2 新增不使用组件用户鉴权的稳定 ID read/preview/download 路由，覆盖 304、Range、篡改拒绝和 MinIO 流式读取测试。
- [x] 2.3 为非 local provider 默认启用磁盘二级缓存，覆盖远程 stat 校验、异步填充、更新/删除失效、有界资源和午夜空闲清理测试。
- [ ] 2.4 扩展 Starter 配置、自动装配、环境变量模板与中英文文档，生产配置缺失读取 ID key、迁移根目录或非 local 缓存目录时 fail fast。

## 3. 服务器目录离线迁移

- [ ] 3.1 实现严格 package name、NDJSON manifest、SHA-256 流式摘要与原子导出目录，覆盖 Local 导出测试。
- [ ] 3.2 实现服务器目录导入、目标同摘要幂等跳过、冲突拒绝、completed/failed 移动和 `errors.ndjson`，覆盖失败路径测试。
- [ ] 3.3 以 Docker MinIO 端到端验证 Local 导出到 MinIO 导入、稳定 ID 不变和私有读取代理。

## 4. 质量与集成

- [ ] 4.1 运行模块/全量测试、OpenSpec 严格校验、计划一致性审计与 code-review-spec 审查。
- [ ] 4.2 同步主规格、归档 change、提交每个已验证任务并合并至 `main`。
