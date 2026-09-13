## MODIFIED Requirements

### Requirement: MinIO 固定 bucket 适配器
MinIO 适配器 SHALL 使用公共 `dinghai.file-storage.bucket` 指定的固定 bucket 执行对象操作，并通过可配置连接、读取与写入超时调用 MinIO。它 MUST NOT 自动创建 bucket；缺失 endpoint、公共 bucket、凭据或超时配置时 MUST 在启动时失败。SDK 的认证、服务端、超时和通用错误 MUST 映射为稳定的文件存储异常，不得暴露密钥或内部 endpoint。RustFS 适配器 SHALL 以相同约束使用公共 `dinghai.file-storage.bucket` 和可配置超时调用 RustFS 的 S3 兼容 API，且 MUST NOT 自动创建或变更 bucket。

#### Scenario: 完整 MinIO 配置时使用固定 bucket
- **WHEN** provider 配置为 `minio` 且 endpoint、公共 bucket、凭据和超时均有效
- **THEN** 所有对象操作仅针对该公共 bucket 执行

#### Scenario: 完整 RustFS 配置时使用固定 bucket
- **WHEN** provider 配置为 `rustfs` 且 endpoint、公共 bucket、凭据和超时均有效
- **THEN** 所有对象操作仅针对该公共 bucket 执行

#### Scenario: MinIO bucket 不存在
- **WHEN** 配置的 bucket 不存在
- **THEN** 操作返回可分类的存储失败，组件不会自动创建 bucket

#### Scenario: RustFS bucket 不存在
- **WHEN** RustFS 配置的 bucket 不存在
- **THEN** 操作返回可分类的存储失败，组件不会自动创建 bucket

### Requirement: 可扩展的后端选择
Spring Boot Starter SHALL 根据 `dinghai.file-storage.provider` 在 `local`、`minio` 与 `rustfs` 中选择且仅选择一个主 `FileStorage` Bean。每个 provider MUST 配置公共 bucket；未知 provider、公共 bucket 不安全或所选 provider 的配置不完整时 MUST fail fast；组件 MUST NOT 在远程 provider 失败时自动写入本地磁盘。

#### Scenario: 选择本地后端
- **WHEN** provider 为 `local`、本地根目录有效且公共 bucket 有效
- **THEN** 容器中注册以 `local.root / bucket` 为对象根的本地 `FileStorage` 主 Bean，且远程客户端不参与装配

#### Scenario: 选择 RustFS 后端
- **WHEN** provider 为 `rustfs`、RustFS endpoint、凭据、超时和公共 bucket 有效
- **THEN** 容器中注册 RustFS `FileStorage` 主 Bean，且 Local 和 MinIO 适配器不参与装配

#### Scenario: 拒绝未配置 provider
- **WHEN** provider 值未知、公共 bucket 缺失/不安全，或所选 provider 的必需属性缺失
- **THEN** 应用在启动期间以明确配置错误失败，而不是使用隐式开发默认值

### Requirement: Remote provider read cache
The Spring Boot starter SHALL enable the bounded disk read cache by default for every non-Local provider, currently MinIO and RustFS, unless `dinghai.file-storage.cache.enabled=false`. It SHALL reject a Local configuration that explicitly enables the second-level cache and SHALL require a cache root for every enabled remote-provider cache.

#### Scenario: RustFS uses the default cache
- **WHEN** `provider: rustfs` is configured and `cache.enabled` is omitted
- **THEN** the starter SHALL create the same remote read-cache layer used by MinIO

#### Scenario: Local cache is explicitly enabled
- **WHEN** `provider: local` is configured with `cache.enabled=true`
- **THEN** startup SHALL fail with the established invalid-configuration error
