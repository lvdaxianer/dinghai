## Context

The file-storage aggregate currently supports Local disk and MinIO. RustFS runs as a separate S3-compatible service and exposes its API at the configured endpoint, but callers must be able to select it as `rustfs` rather than disguising it as MinIO. Buckets remain private and are an application-managed prerequisite.

## Goals / Non-Goals

**Goals:**

- Keep the backend-neutral `FileStorage` API and stable ID/Web read routes unchanged.
- Provide a standalone RustFS module, configuration namespace, adapter name, and provider enum value.
- Match MinIO object lifecycle, metadata, error mapping, and native presigned upload/download behavior.
- Include RustFS in the existing non-Local disk cache and sendfile-on-cache-hit behavior.
- Prove compatibility with focused tests and the supplied RustFS instance without recording credentials.

**Non-Goals:**

- Do not add generic AWS S3, Aliyun OSS, or Huawei OBS support in this change.
- Do not create buckets or change their access policy.
- Do not add a second cache, read protocol, or public object URL contract.
- Do not expose endpoint credentials in tests, examples, logs, or committed configuration.

## Decisions

### Use a distinct RustFS module backed by the existing S3-compatible SDK

`dinghai-file-storage-rustfs` will depend on `io.minio:minio`, like the MinIO module, but owns `RustfsFileStorageAdapter`, client factory, error mapper, and configuration validation. This gives RustFS an explicit public identity while reusing a proven client protocol.

Using `provider=minio` alone was rejected because it obscures operational configuration and prevents future RustFS-specific behavior. Generalizing both adapters into a shared S3 abstraction was rejected because it expands the refactor and regression surface without a current third S3 provider.

### Preserve the established private-bucket contract

The common `dinghai.file-storage.bucket` remains the only bucket selector. Startup validates the configured value and credentials, but neither creates nor modifies buckets. The adapter delegates PUT, GET, STAT, LIST, DELETE, and presigning to RustFS through the S3 client.

### Treat RustFS as a remote provider for cache behavior

Starter conditions that currently apply only to `minio` will include `rustfs`. RustFS reads first validate remote metadata, can asynchronously fill the bounded disk cache, and permit native sendfile only after a validated local cache hit. Cache invalidation and scheduled cleanup remain shared behavior.

## Risks / Trade-offs

- [S3 API compatibility differs by RustFS version] -> Verify the required object lifecycle and presigning flow against the supplied instance before completion.
- [A copied MinIO implementation can drift] -> Keep the RustFS package and class names explicit, use the same contract tests, and retain focused unit tests around the wrapper behavior.
- [Credentials leak through test configuration] -> Read any integration credentials only from ephemeral local/remote process state; examples use placeholders and no logs include them.
- [Remote cache behavior is missed by conditions] -> Add starter configuration tests that assert RustFS receives the same cache wrapper and invalid configuration treatment as MinIO.

## Migration Plan

1. Publish the RustFS module and starter support with existing providers untouched.
2. Pre-create a private RustFS bucket and configure `provider: rustfs`, the common bucket, and `rustfs.*` credentials.
3. Preserve `storage-alias` and `security.stable-id-key` when moving objects so existing stable IDs remain valid after offline migration.
4. Roll back by changing the provider configuration to the prior Local or MinIO backend after restoring the corresponding object data; no schema migration is required.

## Open Questions

None. The supplied instance establishes the endpoint shape and compatibility test target; production deployments supply their own endpoint and non-default credentials.
