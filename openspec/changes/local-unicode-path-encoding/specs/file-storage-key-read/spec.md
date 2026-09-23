## MODIFIED Requirements

### Requirement: 真实 object key 读取地址
Starter SHALL 在 `{path-prefix}{read-path}/{*key}` 及其配置读取路径下的预览、下载路由
提供显式 object-key 读取，同时保留稳定 file ID 路由。默认路径 MUST 为
`/files/read/key/{*key}`、`/files/read/key/preview/{*key}` 与
`/files/read/key/download/{*key}`。Object-key 路由和可选历史直读路由 MUST 复用现有
授权、流式传输、缓存头、条件请求和单 Range 行为，并以 UTF-8 解码百分号编码的
Unicode object key。

#### Scenario: 旧地址读取 URL 编码中文 key
- **WHEN** `path-prefix=/rsm`、历史直读已启用且客户端访问
  `/rsm/origin/uuid/%E6%9D%BF%E5%8D%A1.gltf`
- **THEN** Starter 以 key `origin/uuid/板卡.gltf` 读取根目录内的既有对象
