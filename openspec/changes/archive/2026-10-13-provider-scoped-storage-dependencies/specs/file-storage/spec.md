## MODIFIED Requirements

### Requirement: 可扩展的后端选择
Spring Boot 文件存储能力 SHALL 根据 `dinghai.file-storage.provider` 在 `local`、`minio`、`rustfs`、`aliyun-oss` 与 `huaweicloud-obs` 中选择且仅选择一个主 `FileStorage` Bean。Local provider SHALL be supplied by the base starter; every remote provider SHALL be supplied by its dedicated provider starter. Unknown provider values, legacy value `oss`, missing or unsafe common bucket, incomplete selected-provider configuration, or a selected provider without its dedicated starter MUST fail fast with a stable configuration error. The component MUST NOT use implicit provider detection, automatic fallback, or write to local storage after a remote failure.

#### Scenario: Local backend uses only the base starter
- **WHEN** an application declares the base starter, configures `provider: local`, and supplies valid Local properties
- **THEN** the container SHALL register the Local `FileStorage` primary bean and SHALL not require any remote provider SDK or adapter module

#### Scenario: Remote backend requires its dedicated starter
- **WHEN** an application configures `provider: rustfs`, `provider: minio`, `provider: aliyun-oss`, or `provider: huaweicloud-obs`
- **THEN** the matching dedicated provider starter MUST be present before a primary `FileStorage` bean can be created

#### Scenario: Selected provider remains singular
- **WHEN** the selected provider starter and valid provider configuration are present, regardless of whether other provider starters are also present
- **THEN** exactly one selected provider SHALL be instantiated and registered as the primary `FileStorage`, and unselected provider clients SHALL not be created

### Requirement: Remote provider read cache
The Spring Boot starter SHALL enable the bounded disk read cache by default for every non-Local provider, including MinIO, RustFS, Alibaba Cloud OSS, and Huawei Cloud OBS, unless `dinghai.file-storage.cache.enabled=false`. It SHALL reject a Local configuration that explicitly enables the second-level cache and SHALL require a cache root for every enabled remote-provider cache.

#### Scenario: Selected remote provider uses the shared cache
- **WHEN** any dedicated remote provider starter is present, the corresponding provider is selected, and cache is not explicitly disabled
- **THEN** the base starter SHALL create the same bounded remote read-cache layer without importing another provider SDK
