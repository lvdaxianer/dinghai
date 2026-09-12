## Purpose

定义 Web 上传成功后与 SDK 对齐的已确认对象元数据和浏览器预览地址契约，
保证直接上传与预签名上传在不同存储 provider 下具有一致、可持久化的响应语义。

## Requirements

### Requirement: Web 上传返回统一对象结果
Successful Web uploads SHALL return HTTP `201 Created` with a JSON object containing the SDK `ObjectMetadata` field names `key`, `contentLength`, `contentType`, `etag`, `lastModified`, and `cacheControl`, plus `previewUrl`. The returned metadata MUST be the value confirmed by the selected storage provider after persistence, not the client-declared upload values.

#### Scenario: 直接上传成功后返回结果
- **WHEN** an authorized direct Web upload persists an object successfully
- **THEN** the endpoint returns HTTP 201, the seven response fields, and a `Location` header equal to `previewUrl`

#### Scenario: 预签名上传成功后返回结果
- **WHEN** a valid pre-signed Web upload persists an object successfully
- **THEN** the endpoint returns the same HTTP 201 response model as a direct upload

### Requirement: Web 上传返回真实 key 预览地址
The `previewUrl` and HTTP `Location` header SHALL use the configured `web.path-prefix` followed by `/read/key/preview/` and the URL path encoded real object key. The URL MUST preserve nested key segments and MUST be readable through the existing real-key preview endpoint without a component credential.

#### Scenario: 嵌套对象 key 的预览地址
- **WHEN** a Web upload succeeds for object key `1/1/1/1.png`
- **THEN** `previewUrl` is a root-relative URL ending in `/read/key/preview/1/1/1/1.png`
