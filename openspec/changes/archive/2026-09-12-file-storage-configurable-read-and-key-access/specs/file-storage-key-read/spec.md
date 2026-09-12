# file-storage-key-read Specification

## ADDED Requirements

### Requirement: 真实 object key 读取地址
Starter SHALL provide explicit object-key routes under `/files/read/key/{*key}`, `/files/read/key/preview/{*key}`, and `/files/read/key/download/{*key}` while retaining stable file ID routes. Object-key routes MUST reuse the existing authorization, streaming, cache headers, conditional requests, and single-range behavior.

#### Scenario: 通过真实 key 读取对象
- **WHEN** a caller presents valid direct-access authorization or a valid presigned request for `/files/read/key/{*key}`
- **THEN** the object identified by the normalized key is returned inline without requiring a stable ID

#### Scenario: 稳定 ID 路由保持兼容
- **WHEN** a caller requests `/files/read/v1/{identifier}`
- **THEN** the existing deterministic ID resolution and anonymous read behavior remain unchanged
