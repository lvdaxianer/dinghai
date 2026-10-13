## ADDED Requirements

### Requirement: Provider dependencies are isolated by starter
The file-storage distribution SHALL provide a public base starter plus one dedicated Spring Boot starter for each remote provider: MinIO, RustFS, Alibaba Cloud OSS, and Huawei Cloud OBS. The base starter MUST NOT transitively depend on any remote provider adapter or SDK, and each dedicated provider starter MUST depend only on the base starter, its own adapter module, and its own SDK closure.

#### Scenario: RustFS minimal dependency set
- **WHEN** an application declares only the base starter and the RustFS provider starter
- **THEN** its runtime dependency tree MUST contain the RustFS S3 client closure and MUST NOT contain `io.minio:minio`, Alibaba OSS SDK, or Huawei OBS SDK artifacts

#### Scenario: MinIO minimal dependency set
- **WHEN** an application declares only the base starter and the MinIO provider starter
- **THEN** its runtime dependency tree MUST contain `io.minio:minio` and MUST NOT contain RustFS-only, Alibaba OSS, or Huawei OBS provider SDK artifacts through the Dinghai starters

#### Scenario: Local minimal dependency set
- **WHEN** an application declares only the base starter and configures `provider: local`
- **THEN** its runtime dependency tree MUST NOT contain any remote provider SDK artifact through the Dinghai file-storage modules

### Requirement: Provider selection remains explicit and singular
The base starter SHALL select exactly one `FileStorage` implementation using `dinghai.file-storage.provider`, and a provider factory SHALL be registered only when its dedicated provider starter is present and the matching provider value is selected. The base starter MUST NOT infer a provider from arbitrary SDKs on the classpath.

#### Scenario: Selected provider factory creates the primary bean
- **WHEN** the matching provider starter is present and `dinghai.file-storage.provider` is configured with valid provider settings
- **THEN** exactly one primary `FileStorage` bean SHALL be created for the selected provider

#### Scenario: Missing provider starter fails fast
- **WHEN** a remote provider value is selected without its dedicated provider starter
- **THEN** startup SHALL fail with a stable configuration error identifying the missing provider dependency without exposing endpoint, access key, secret key, token, or signed URL values

### Requirement: Provider SDK classes are isolated from the base auto-configuration
The base starter auto-configuration SHALL compile and initialize without loading remote provider SDK classes. Provider-specific auto-configuration SHALL guard its SDK-dependent configuration with classpath and matching-provider conditions.

#### Scenario: Base starter starts with Local only
- **WHEN** only the base starter is present and `provider: local` is selected
- **THEN** Spring configuration parsing SHALL succeed without MinIO, RustFS, Alibaba OSS, or Huawei OBS classes on the classpath

