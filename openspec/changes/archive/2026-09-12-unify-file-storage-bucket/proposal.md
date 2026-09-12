## Why

当前 MinIO 使用 `minio.bucket`，而 Local 仅使用 `local.root`，同一个
`objectKey` 在两个 provider 下不具有相同的存储定位语义。业务在切换 provider
或迁移对象时无法从一份统一配置中确定对象所在的逻辑桶。

## What Changes

- 在 `dinghai.file-storage` 增加必填公共 `bucket` 配置。
- **BREAKING** 移除 Starter 的 `dinghai.file-storage.minio.bucket` 配置；MinIO 改用公共
  `dinghai.file-storage.bucket`。
- Local Starter 将对象根目录固定映射为 `local.root / bucket`；MinIO 继续映射为
  `bucket / objectKey`。
- 启动时校验公共 bucket 对 Local 和对象存储均安全有效，避免路径穿越或 provider 间不一致。
- 更新中英文 README、Agent Skill 和环境变量示例，明确 `storage-alias` 只参与稳定
  `fileId`，不参与物理存储定位。

## Capabilities

### New Capabilities
- `file-storage-bucket`: 公共 bucket 配置及 Local/MinIO 的一致存储定位。

### Modified Capabilities
- `file-storage`: 更新 Starter provider 选择和 Local 磁盘映射要求。
- `repository-tooling`: 更新 README、环境变量示例和 Agent Skill 的文件存储配置说明。

## Impact

- 影响 `dinghai-file-storage-spring-boot-starter` 的配置绑定和自动装配。
- 已部署 Local 数据从旧的 `local.root / objectKey` 改为 `local.root / bucket / objectKey`；
  升级前需通过既有离线迁移流程或运维移动完成数据迁移。
- MinIO 使用方必须将 `minio.bucket` 上提为公共 `bucket`。
