# rustfs-storage-provider Specification

## Purpose

Provide an explicit RustFS S3-compatible provider while preserving the backend-neutral
file-storage contract and private-bucket boundary.

## Requirements

### Requirement: Explicit RustFS provider selection
The file-storage starter SHALL support `dinghai.file-storage.provider=rustfs` as a distinct provider selection and SHALL retain the existing Local and MinIO provider selections unchanged.

#### Scenario: RustFS provider is selected
- **WHEN** an application configures `provider: rustfs` with a valid common bucket and RustFS connection properties
- **THEN** the starter SHALL expose a RustFS-backed `FileStorage` implementation as the primary storage bean

#### Scenario: Existing provider is selected
- **WHEN** an application configures `provider: local` or `provider: minio`
- **THEN** the starter SHALL preserve its existing adapter selection behavior

### Requirement: RustFS configuration isolation
The starter SHALL bind RustFS endpoint, access key, secret key, and connection/read/write timeouts under `dinghai.file-storage.rustfs`, and SHALL fail startup with a safe fixed configuration error when a required RustFS value is absent or invalid.

#### Scenario: Required RustFS endpoint is absent
- **WHEN** `provider: rustfs` is configured without `rustfs.endpoint`
- **THEN** application startup SHALL fail before serving requests without echoing configuration values

#### Scenario: Valid RustFS timeouts are configured
- **WHEN** positive RustFS connection, read, and write timeouts are configured
- **THEN** the RustFS client SHALL use those timeouts for remote calls

### Requirement: RustFS private-bucket object operations
The RustFS adapter SHALL implement the existing `FileStorage` object lifecycle contract for the configured common bucket, including put, get, stat, list, delete, and native S3 presigned upload/download operations, without automatically creating or changing the bucket.

#### Scenario: Object lifecycle succeeds
- **WHEN** a caller stores an object in a pre-existing private RustFS bucket
- **THEN** the caller SHALL be able to retrieve confirmed metadata and content and delete the object through the common contract

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
