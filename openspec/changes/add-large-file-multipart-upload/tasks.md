## 1. Core contract and validation

- [x] 1.1 Add multipart request/result models, stable error codes, lifecycle interface, validation rules, and contract tests.
- [x] 1.2 Define configuration for part size, max parts, session TTL, local multipart root, and bounded concurrency.

## 2. Provider implementations

- [x] 2.1 Implement Local temporary-part storage, atomic merge, restart-safe metadata, expiry cleanup, and focused tests.
- [x] 2.2 Implement MinIO S3 multipart lifecycle and per-part presigning with focused tests.
- [x] 2.3 Extend RustFS S3-compatible delegation for multipart lifecycle and native per-part presigning.
- [x] 2.4 Implement Alibaba Cloud OSS multipart lifecycle and per-part presigning with SDK request tests.

## 3. Starter HTTP integration

- [ ] 3.1 Add multipart REST controller, authorization, validation, stable errors, idempotency and Local part PUT handling.
- [ ] 3.2 Add bounded upload permits, session cleanup scheduling, provider selection and configuration validation.

## 4. Verification and documentation

- [ ] 4.1 Add provider isolation, duplicate/retry/concurrency/recovery tests and opt-in integration lifecycle tests.
- [ ] 4.2 Update Chinese and English README/configuration examples and run focused plus full reactor verification.
