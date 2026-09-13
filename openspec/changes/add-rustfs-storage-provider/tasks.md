## 1. RustFS Adapter Module

- [x] 1.1 Add the RustFS Maven module with its explicitly named S3-compatible client factory, storage configuration, error mapper, `FileStorage` adapter, and focused contract tests for object operations and native presigning.

## 2. Starter Integration

- [ ] 2.1 Add the `RUSTFS` provider, isolated `rustfs.*` properties, validation, and auto-configuration selection while preserving Local and MinIO behavior.
- [ ] 2.2 Extend remote-cache conditions and starter tests so RustFS has the same non-Local cache behavior and configuration failures as MinIO.

## 3. Documentation and Compatibility Verification

- [ ] 3.1 Update Chinese and English module/configuration documentation and the environment-variable template with credential placeholders only.
- [ ] 3.2 Run the full reactor suite and an ephemeral RustFS integration lifecycle test against the supplied service, then remove all temporary remote objects and local secret material.
