## MODIFIED Requirements

### Requirement: 默认拒绝直接 Web 访问
Starter SHALL 默认以 `web.enabled=true`、`web.path-prefix=/files`、`web.direct-access=false` 注册文件端点。Web 上传无有效预签名凭据时 MUST 被拒绝；只有 `direct-access=true` 且应用提供 `FileStorageAccessPolicy` 时，上传端点才能根据宿主应用认证主体允许直接上传。成功的直接或预签名 Web 上传 MUST 返回 HTTP `201 Created`、所持久对象的 `key`、`contentLength`、`contentType`、`etag`、`lastModified`、`cacheControl` 和 `previewUrl`，并将 `Location` 设为 `previewUrl`。`previewUrl` MUST 指向配置路径前缀下的真实 key 预览端点。Web 下载、预览及真实 key 读取不要求组件用户凭证或直接访问授权，但仍受 Web 读取并发限制。

#### Scenario: 默认配置拒绝无签名预览
- **WHEN** 客户端在默认配置下无预签名凭据访问 `/files/preview/{key}`
- **THEN** 请求被拒绝，且不会公开对象内容

#### Scenario: 应用策略允许直接下载
- **WHEN** 应用显式启用 direct access、提供 `FileStorageAccessPolicy`，且该策略允许当前认证主体下载对象
- **THEN** 下载端点返回对象；策略拒绝时端点拒绝请求

#### Scenario: 默认配置拒绝无签名上传
- **WHEN** 客户端在默认配置下无预签名凭据访问 `/files/upload/{key}`
- **THEN** 请求被拒绝，且不会公开或写入对象

#### Scenario: 匿名读取真实 object key
- **WHEN** 客户端不携带读取签名或认证主体访问 `/files/read/key/{key}`、预览或下载端点
- **THEN** 端点按 object key 读取对象，并不调用 `FileStorageAccessPolicy`
