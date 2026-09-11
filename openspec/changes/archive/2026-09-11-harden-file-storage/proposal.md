## Why

The file-storage implementation is delivered, but its archived delta specifications are not yet represented in the repository's main specification tree. The Web streaming endpoint also relies on Spring MVC's implicit async executor, which leaves concurrency limits and lifecycle behavior implicit.

## What Changes

- Synchronize the file-storage and file-storage-web-access requirements into the main OpenSpec tree.
- Configure a bounded, named executor for Web streaming responses when the file-storage Web integration is enabled.
- Add regression tests for executor bounds, lifecycle configuration, and existing streaming behavior.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `file-storage`: Preserve the delivered storage contract as a main specification.
- `file-storage-web-access`: Preserve the delivered Web access contract and make streaming executor bounds explicit.

## Impact

The Spring Boot Starter auto-configuration gains a Web MVC async executor configuration. The public file-storage endpoints and provider APIs remain compatible. OpenSpec main specs gain the already-delivered file-storage requirements plus the executor-bound requirement.
