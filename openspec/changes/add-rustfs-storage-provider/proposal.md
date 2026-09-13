## Why

RustFS provides an S3-compatible private object store alongside the existing Local and MinIO backends. Applications need to select RustFS explicitly without changing the backend-neutral `FileStorage` contract or browser read addresses.

## What Changes

- Add a separately named `dinghai-file-storage-rustfs` adapter module backed by the S3-compatible MinIO Java SDK.
- Add `provider: rustfs` and a dedicated `rustfs.*` configuration group to the Spring Boot starter.
- Apply the existing non-Local read cache and zero-copy cache-hit path to RustFS.
- Document RustFS configuration and verify the object lifecycle and presigning behavior against the supplied RustFS service without persisting its credentials.

## Capabilities

### New Capabilities

- `rustfs-storage-provider`: Explicit RustFS selection, S3 object operations, native presigning, and Spring Boot configuration.

### Modified Capabilities

- `file-storage`: Provider selection, the fixed-bucket remote adapter contract, and the remote-provider cache requirement extend to RustFS.

## Impact

- Affected modules: `dinghai-file-storage`, a new `rustfs` module, `spring-boot-starter`, documentation, and tests.
- The public provider enum gains `RUSTFS`; existing `local` and `minio` configuration remains unchanged.
- No new external SDK is introduced because the existing MinIO Java SDK is the compatible S3 client.
