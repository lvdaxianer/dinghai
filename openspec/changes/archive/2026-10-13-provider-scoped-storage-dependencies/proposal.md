## Why

当前文件存储 Spring Boot Starter 无条件引入 MinIO、RustFS、阿里云 OSS 和华为云 OBS 的适配模块。运行时虽然通过 `dinghai.file-storage.provider` 只创建一个 Bean，但未选中的 SDK 仍然进入应用 classpath，增加安全扫描暴露面、依赖体积和供应链维护成本；RustFS 还直接复用 MinIO SDK，无法独立裁剪。

## What Changes

- **BREAKING** 将现有文件存储 Starter 调整为公共基础 Starter，只提供统一配置、Local 能力、Web 集成和公共生命周期能力，不再无条件传递远端 provider SDK。
- 为 MinIO、RustFS、阿里云 OSS、华为云 OBS 提供独立的 provider Starter，业务仅声明实际使用的 provider 依赖。
- 保留同一个 `dinghai.file-storage.provider` 配置变量和现有 Bean 选择语义；未声明对应 provider 模块时，启动失败必须明确提示缺少匹配的 provider 依赖。
- 将 RustFS 实现从 `dinghai-file-storage-minio` 和 `io.minio:minio` 中解耦，使用独立的 S3 兼容客户端完成对象、预签名和 multipart 能力。
- 将 provider 自动配置拆分为可按 classpath 条件加载的边界，避免基础 Starter 在缺少远端 SDK 时解析到供应商类型。
- 更新模块聚合、Maven 依赖、README、示例和测试，验证每个 provider 的最小依赖闭包不包含其他 provider SDK。

## Capabilities

### New Capabilities

- `provider-scoped-dependencies`: 文件存储 provider 依赖按模块隔离，应用只引入实际使用的 provider SDK。

### Modified Capabilities

- `file-storage`: 修改 Starter 依赖声明、provider 装配边界和缺少 provider 模块时的启动错误要求。
- `rustfs-storage-provider`: RustFS 不再复用 MinIO SDK，但保留统一对象契约、固定 bucket、超时、预签名、multipart 和错误映射行为。

## Impact

- Maven：根 POM、`dinghai-file-storage` 聚合 POM、公共 Starter、各 provider 模块及新增 provider Starter 模块。
- Java：`FileStorageAutoConfiguration` 的公共/供应商自动配置边界、RustFS 客户端和适配器实现、provider 缺失依赖校验。
- 测试：模块单测、Spring Boot 自动配置测试、Maven dependency tree/最小 classpath 验证、现有文件存储回归测试。
- 文档：文件存储 README、依赖示例、配置说明和 OpenSpec 规格；已有使用全量 Starter 的调用方需要显式添加实际 provider Starter。
- 安全：选择某个 provider 时，未选择的 MinIO、OSS、OBS 等 SDK 不得出现在最终运行时依赖树中。
