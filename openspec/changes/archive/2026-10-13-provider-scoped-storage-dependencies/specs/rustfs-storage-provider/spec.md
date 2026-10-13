## MODIFIED Requirements

### Requirement: Explicit RustFS provider selection
The file-storage base starter SHALL support `dinghai.file-storage.provider=rustfs` only when the dedicated RustFS provider starter is present. RustFS selection SHALL remain distinct from Local and MinIO selection, and the selected RustFS implementation SHALL be the primary `FileStorage` bean.

#### Scenario: RustFS provider starter is selected
- **WHEN** an application declares the base and RustFS provider starters and configures `provider: rustfs` with valid common bucket and RustFS connection properties
- **THEN** the starter SHALL expose a RustFS-backed `FileStorage` implementation as the primary storage bean

#### Scenario: RustFS starter is absent
- **WHEN** an application configures `provider: rustfs` without the dedicated RustFS provider starter
- **THEN** application startup SHALL fail with a stable missing-provider configuration error before serving requests

### Requirement: RustFS private-bucket object operations
The RustFS adapter SHALL implement the existing `FileStorage` and `MultipartFileStorage` contracts for the configured common bucket using an independent S3-compatible client that is not supplied by `io.minio:minio`. It SHALL support put, get, stat, list, delete, native PUT presigned upload/download, multipart initiate, part presigning, part upload, part listing, completion and abort, without automatically creating or changing the bucket.

#### Scenario: RustFS runtime has no MinIO SDK
- **WHEN** a caller uses only the base and RustFS provider starters
- **THEN** RustFS object operations SHALL resolve without `io.minio:minio` on the runtime classpath

#### Scenario: RustFS multipart lifecycle succeeds
- **WHEN** a caller creates a RustFS multipart session, uploads or presigns all parts, lists confirmed parts, and completes the manifest
- **THEN** the adapter SHALL return confirmed metadata and preserve the existing multipart error and expiry semantics

#### Scenario: RustFS bucket does not exist
- **WHEN** a caller uses a RustFS configuration whose common bucket does not exist
- **THEN** the adapter SHALL surface a mapped storage failure and SHALL NOT create the bucket
