## ADDED Requirements

### Requirement: 公共 bucket 配置和统一对象定位
Starter SHALL require a non-blank, single-segment `dinghai.file-storage.bucket` for every
configured provider. It MUST reject a bucket containing a path separator, `.` or `..`, or control
characters during startup. The configured bucket MUST determine physical object placement: Local
objects are stored under `local.root / bucket / objectKey`, while MinIO objects are stored in
`bucket / objectKey`.

#### Scenario: Local 使用公共 bucket 子目录
- **WHEN** the provider is `local`, `local.root` is `/data/files`, the bucket is `documents`, and
  an application writes object key `1/1/1/1.png`
- **THEN** the object is persisted at `/data/files/documents/1/1/1/1.png`

#### Scenario: MinIO 使用同一个公共 bucket
- **WHEN** the provider is `minio`, the bucket is `documents`, and an application writes object
  key `1/1/1/1.png`
- **THEN** MinIO operations target bucket `documents` and object `1/1/1/1.png`

#### Scenario: 拒绝不安全或缺失的公共 bucket
- **WHEN** the selected provider has no public bucket or the bucket contains `../documents` or
  `documents/archive`
- **THEN** application startup fails with a safe configuration error before any storage operation

### Requirement: 移除 provider 专属 MinIO bucket 配置
The Starter SHALL obtain the MinIO bucket exclusively from `dinghai.file-storage.bucket` and MUST
NOT bind or use `dinghai.file-storage.minio.bucket` as a fallback. Existing deployments MUST move
the value to the public property before upgrading.

#### Scenario: MinIO 配置迁移后正常装配
- **WHEN** a MinIO deployment provides endpoint, access key, secret key, and the public bucket
  without `minio.bucket`
- **THEN** the Starter creates the MinIO adapter using the public bucket
