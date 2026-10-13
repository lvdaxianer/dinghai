# rustfs-storage-provider Specification

## Purpose

Provide an explicit RustFS S3-compatible provider while preserving the backend-neutral
file-storage contract and private-bucket boundary.

## Requirements

### Requirement: Explicit RustFS provider selection
The file-storage base starter SHALL support `dinghai.file-storage.provider=rustfs` only when the dedicated RustFS provider starter is present. RustFS selection SHALL remain distinct from Local and MinIO selection, and the selected RustFS implementation SHALL be the primary `FileStorage` bean.

#### Scenario: RustFS provider starter is selected
- **WHEN** an application declares the base and RustFS provider starters and configures `provider: rustfs` with valid common bucket and RustFS connection properties
- **THEN** the starter SHALL expose a RustFS-backed `FileStorage` implementation as the primary storage bean

#### Scenario: RustFS starter is absent
- **WHEN** an application configures `provider: rustfs` without the dedicated RustFS provider starter
- **THEN** application startup SHALL fail with a stable missing-provider configuration error before serving requests

### Requirement: RustFS configuration isolation
The starter SHALL bind RustFS endpoint, access key, secret key, and connection/read/write timeouts under `dinghai.file-storage.rustfs`, and SHALL fail startup with a safe fixed configuration error when a required RustFS value is absent or invalid.

#### Scenario: Required RustFS endpoint is absent
- **WHEN** `provider: rustfs` is configured without `rustfs.endpoint`
- **THEN** application startup SHALL fail before serving requests without echoing configuration values

#### Scenario: Valid RustFS timeouts are configured
- **WHEN** positive RustFS connection, read, and write timeouts are configured
- **THEN** the RustFS client SHALL use those timeouts for remote calls

### Requirement: RustFS private-bucket object operations
The RustFS adapter SHALL implement the existing `FileStorage` and `MultipartFileStorage` contracts for the configured common bucket using an independent S3-compatible client that is not supplied by `io.minio:minio`. It SHALL support put, get, stat, list, delete, native PUT presigned upload/download, multipart initiate, part presigning, part upload, part listing, completion and abort, without automatically creating or changing the bucket.

#### Scenario: Object lifecycle succeeds
- **WHEN** a caller stores an object in a pre-existing private RustFS bucket
- **THEN** the caller SHALL be able to retrieve confirmed metadata and content and delete the object through the common contract

#### Scenario: RustFS runtime has no MinIO SDK
- **WHEN** a caller uses only the base and RustFS provider starters
- **THEN** RustFS object operations SHALL resolve without `io.minio:minio` on the runtime classpath

#### Scenario: RustFS multipart lifecycle succeeds
- **WHEN** a caller creates a RustFS multipart session, uploads or presigns all parts, lists confirmed parts, and completes the manifest
- **THEN** the adapter SHALL return confirmed metadata and preserve the existing multipart error and expiry semantics

#### Scenario: Bucket does not exist
- **WHEN** a caller uses a RustFS configuration whose common bucket does not exist
- **THEN** the adapter SHALL surface the mapped storage failure and SHALL NOT create the bucket

### Requirement: RustFS uses remote read caching
The starter SHALL apply the existing non-Local disk read cache, cache invalidation, scheduled cleanup, and validated cache-hit local-file behavior when `provider: rustfs` is selected.

#### Scenario: RustFS cache is enabled by default
- **WHEN** RustFS is selected and cache is not explicitly disabled
- **THEN** the starter SHALL create the remote read cache using the configured bounded cache settings

#### Scenario: RustFS cache is disabled
- **WHEN** RustFS is selected with `cache.enabled=false`
- **THEN** the starter SHALL serve through the remote adapter without creating the read cache
