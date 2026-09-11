## Why

真实 MinIO 服务会拒绝当前适配器生成的预签名 POST 上传，因为返回值缺少
policy 强制要求的对象和元数据表单字段。调用方即使提交了全部返回字段也
无法完成上传。

## What Changes

- 让 MinIO 预签名上传结果包含固定 bucket、对象 key、内容类型和缓存策略，
  与生成的 POST policy 保持一致。
- 添加回归测试，验证 SDK 只返回签名字段时，适配器仍返回完整可提交表单。
- 以独立临时 bucket 对本地 Docker MinIO 验证预签名 POST 上传端到端可用。

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `file-storage`: MinIO 预签名上传返回调用方提交 policy 所需的完整表单字段。

## Impact

影响 `dinghai-file-storage-minio` 的 `MinioFileStorageAdapter` 及其测试。不会
改变 Core API、bucket 管理策略、其他 provider 或运行时配置。
