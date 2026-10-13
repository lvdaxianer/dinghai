## 1. 公共依赖边界与 provider SPI

- [x] 1.1 在根 POM 和文件存储聚合 POM 中登记公共 Starter 与四个 provider Starter 模块，集中管理 RustFS 独立 S3 SDK 版本。
- [x] 1.2 先写公共 Starter 的依赖树和 Local 自动配置回归测试，证明公共 Starter 不解析远端 SDK 类型，运行时只选 Local。
- [x] 1.3 新增 `FileStorageProviderFactory` SPI，并将公共自动配置改为通过 factory 选择远端 provider；缺少匹配 factory 时抛出稳定配置错误。
- [x] 1.4 删除公共 Starter 对 MinIO、RustFS、OSS、OBS 适配器和 SDK 的直接依赖，运行公共模块测试并提交。

## 2. Provider 专用 Spring Boot Starter

- [x] 2.1 为 MinIO、RustFS、阿里云 OSS、华为云 OBS 创建独立 Starter POM、自动配置资源和 provider factory Bean。
- [x] 2.2 将 OSS/OBS 客户端生命周期和 provider 配置校验迁移到各自 Starter，保持原有 timeout、凭据和固定 bucket 约束。
- [x] 2.3 为每个 provider 增加 Spring 容器测试，验证匹配 provider 只创建一个主 `FileStorage`，未选 provider 不创建客户端。
- [x] 2.4 为每个最小依赖组合增加 Maven dependency tree 验证，确认 provider Starter 不通过公共 Starter传递其他 provider SDK。

## 3. RustFS 脱离 MinIO SDK

- [x] 3.1 先写 RustFS 独立客户端和适配器失败测试，覆盖 `io.minio:minio` 不在 RustFS 依赖树、配置超时传递、对象生命周期、PUT 预签名和 multipart 生命周期。
- [x] 3.2 使用 AWS SDK for Java v2 S3 client/presigner 实现 RustFS 客户端工厂与适配器，配置自定义 endpoint、path-style、静态凭据和显式超时。
- [x] 3.3 实现 RustFS S3 异常到 `FileStorageErrorCode` 的稳定映射，隐藏 endpoint、凭据、签名 URL 和底层异常消息。
- [x] 3.4 运行 RustFS 模块契约测试、provider Starter Spring 测试和 RustFS 最小依赖树检查，确认不需要 MinIO 类即可初始化。

## 4. 文档、规格与发布验证

- [ ] 4.1 更新中文/英文文件存储 README，说明公共 Starter、provider Starter、配置变量和 BREAKING 迁移方式。
- [ ] 4.2 增加 Local、MinIO、RustFS、OSS、OBS 的最小 Maven 依赖示例和安全扫描边界说明。
- [ ] 4.3 执行 `openspec validate provider-scoped-storage-dependencies --strict`，核对所有需求都有实现和测试映射。
- [ ] 4.4 执行完整 Maven reactor 测试、包构建、provider dependency tree 验证，并完成代码审查和最终集成。
