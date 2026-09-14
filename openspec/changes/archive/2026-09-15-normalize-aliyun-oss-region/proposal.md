## Why

真实验收发现 OSS V2 SDK 会把 `oss-cn-beijing` 当作完整地域再次拼接，生成错误的 `oss-oss-cn-beijing` endpoint，导致 `UnknownHostException`。用户现有配置使用阿里云控制台地域 ID，适配器需要在 SDK 边界完成格式兼容。

## What Changes

- 在创建 OSS V2 client 前，将 `oss-` 前缀地域规范化为 SDK 所需的地域标识。
- 保持 `dinghai.file-storage.aliyun-oss.region` 对外继续接受 `oss-cn-beijing` 等控制台地域 ID。
- 增加单元测试覆盖带前缀和不带前缀地域，防止 endpoint 回归。
- 修正首次 OSS V2 列举请求：不向 SDK builder 传递空 continuation token。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

- `aliyun-oss-storage-provider`: 修正地域配置到 OSS V2 SDK 的边界映射，并保留原有配置格式。

## Impact

- `dinghai-file-storage/aliyun-oss` 客户端工厂及其测试。
- 阿里云 OSS V2 的实际网络 endpoint 解析；不影响其他 provider 或公共 API。
