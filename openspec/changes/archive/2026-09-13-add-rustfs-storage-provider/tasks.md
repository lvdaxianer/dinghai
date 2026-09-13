## 1. RustFS Adapter Module

- [x] 1.1 Add the RustFS Maven module with its explicitly named S3-compatible client factory, storage configuration, error mapper, `FileStorage` adapter, and focused contract tests for object operations and native presigning.

## 2. Starter Integration

- [x] 2.1 Add the `RUSTFS` provider, isolated `rustfs.*` properties, validation, auto-configuration selection, remote-cache conditions, and tests while preserving Local and MinIO behavior.

## 3. Documentation and Compatibility Verification

- [x] 3.1 Update Chinese and English module/configuration documentation and the environment-variable template with credential placeholders only.
- [x] 3.2 Run the full reactor suite and an ephemeral RustFS integration lifecycle test against the supplied service, then remove all temporary remote objects and local secret material.
