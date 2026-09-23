# file-storage-key-read Specification

## Purpose
TBD - created by archiving change file-storage-configurable-read-and-key-access. Update Purpose after archive.
## Requirements
### Requirement: 真实 object key 读取地址
Starter SHALL 在 `{path-prefix}{read-path}/{*key}` 及其配置读取路径下的预览、下载路由提供显式 object-key 读取，同时保留稳定 file ID 路由。默认路径 MUST 为 `/files/read/key/{*key}`、`/files/read/key/preview/{*key}` 与 `/files/read/key/download/{*key}`。Object-key 路由和可选历史直读路由 MUST 复用现有授权、流式传输、缓存头、条件请求和单 Range 行为，并以 UTF-8 解码百分号编码的 Unicode object key。

#### Scenario: 通过真实 key 读取对象
- **WHEN** 调用方访问配置的 `{path-prefix}{read-path}/{*key}`
- **THEN** 组件以规范化 key 返回对象且不要求稳定 ID

#### Scenario: 配置短读取路径
- **WHEN** `path-prefix=/rsm` 且 `read-path=/read`
- **THEN** `/rsm/read/model/123/model.gltf` 使用 `ObjectKey("model/123/model.gltf")` 读取对象

#### Scenario: 稳定 ID 路由保持兼容
- **WHEN** 调用方请求默认 `/files/read/{identifier}` 或其配置后的稳定 ID 路径
- **THEN** 现有确定性 ID 解析和匿名读取行为保持不变

#### Scenario: 旧地址读取 URL 编码中文 key
- **WHEN** `path-prefix=/rsm`、历史直读已启用且客户端访问
  `/rsm/origin/uuid/%E6%9D%BF%E5%8D%A1.gltf`
- **THEN** Starter 以 key `origin/uuid/板卡.gltf` 读取根目录内的既有对象
