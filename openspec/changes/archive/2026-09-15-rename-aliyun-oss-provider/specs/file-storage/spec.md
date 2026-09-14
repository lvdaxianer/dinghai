## MODIFIED Requirements

### Requirement: 可扩展的后端选择
Spring Boot Starter SHALL 根据 `dinghai.file-storage.provider` 在 `local`、`minio`、`rustfs` 与 `aliyun-oss` 中选择且仅选择一个主 `FileStorage` Bean。每个 provider MUST 配置公共 bucket；未知 provider、公共 bucket不安全或所选 provider 的配置不完整时 MUST fail fast；组件 MUST NOT 在远程 provider 失败时自动写入本地磁盘。旧值 `oss` MUST 被视为未知 provider 并拒绝启动。

#### Scenario: 选择本地后端
- **WHEN** provider 为 `local`、本地根目录有效且公共 bucket 有效
- **THEN** 容器中注册以 `local.root / bucket` 为对象根的本地 `FileStorage` 主 Bean，且远程客户端不参与装配

#### Scenario: 选择 RustFS 后端
- **WHEN** provider 为 `rustfs`、RustFS endpoint、凭据、超时和公共 bucket 有效
- **THEN** 容器中注册 RustFS `FileStorage` 主 Bean，且 Local 和 MinIO 适配器不参与装配

#### Scenario: 选择阿里云 OSS 后端
- **WHEN** provider 为 `aliyun-oss`、阿里云 OSS region、环境凭据、超时和公共 bucket 有效
- **THEN** 容器中注册阿里云 OSS `FileStorage` 主 Bean，且其他 provider 的适配器不参与装配

#### Scenario: 拒绝旧 OSS provider
- **WHEN** provider 值为 `oss`
- **THEN** 应用在启动期间以明确配置错误失败，且不装配阿里云 OSS client 或其他远程 provider

#### Scenario: 拒绝未配置 provider
- **WHEN** provider 值未知、公共 bucket 缺失/不安全，或所选 provider 的必需属性缺失
- **THEN** 应用在启动期间以明确配置错误失败，而不是使用隐式开发默认值

### Requirement: Remote provider read cache
The Spring Boot starter SHALL enable the bounded disk read cache by default for every non-Local provider, currently MinIO, RustFS, and Alibaba Cloud OSS, unless `dinghai.file-storage.cache.enabled=false`. It SHALL reject a Local configuration that explicitly enables the second-level cache and SHALL require a cache root for every enabled remote-provider cache.

#### Scenario: RustFS uses the default cache
- **WHEN** `provider: rustfs` is configured and `cache.enabled` is omitted
- **THEN** the starter SHALL create the same remote read-cache layer used by MinIO

#### Scenario: Alibaba Cloud OSS uses the default cache
- **WHEN** `provider: aliyun-oss` is configured and `cache.enabled` is omitted
- **THEN** the starter SHALL create the same remote read-cache layer used by MinIO

#### Scenario: Local cache is explicitly enabled
- **WHEN** `provider: local` is configured with `cache.enabled=true`
- **THEN** startup SHALL fail with the established invalid-configuration error
