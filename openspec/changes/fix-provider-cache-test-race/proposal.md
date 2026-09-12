## Why

`ProviderFileReadCacheTest` assumes the asynchronous cache fill remains active
until a second read begins. On a fast machine it can complete first, so the
second read legitimately hits disk cache and makes the assertion flaky.

## What Changes

- Make the in-flight cache-fill test coordinate the fill stream with latches.
- Preserve the current `ProviderFileReadCache` runtime behavior and public API.

## Capabilities

### New Capabilities

- `file-storage-cache-test`: 文件存储缓存并发测试的确定性契约。

### Modified Capabilities

None.

## Impact

- `ProviderFileReadCacheTest` gains a deterministic remote-storage test
  double behavior.
- No production classes, configuration, dependencies, APIs, or logs change.
