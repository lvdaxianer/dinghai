## Context

`FileStorage` 对业务暴露的是 `objectKey`，但 Starter 目前将 Local 定位为
`local.root / objectKey`，将 MinIO 定位为 `minio.bucket / objectKey`。这使 bucket
只在一种 provider 的配置中存在。稳定 `fileId` 已由 `storage-alias + objectKey`
计算，不能复用为物理桶名。

## Goals / Non-Goals

**Goals:**
- 通过唯一的 `dinghai.file-storage.bucket` 让 Local 与 MinIO 共享对象定位语义。
- Local 物理路径为 `local.root / bucket / objectKey`，MinIO 物理路径为
  `bucket / objectKey`。
- 在启动阶段拒绝空白、不安全的公共 bucket 和遗留的仅 MinIO bucket 配置。
- 保持 `FileStorage` 方法签名、`objectKey`、预签名地址和稳定 `fileId` 算法不变。

**Non-Goals:**
- 不在本次新增多桶路由、每次调用指定 bucket 或 Aliyun/华为 provider。
- 不自动迁移或双读旧 Local 目录；离线迁移与运维移动负责已有数据。
- 不修改独立 `LocalFileStorageAdapter` 和 `MinioFileStorageAdapter` 的公开构造器。

## Decisions

### 公共 bucket 由 Starter 绑定并 fail-fast

在 `FileStorageProperties` 顶层新增 `bucket`，由自动装配在创建任一 provider 前校验。
bucket 使用单路径段约束：非空、无 `/`、`\\`、`.`、`..` 或控制字符。这样它既能安全
作为 Local 子目录，也保持对未来对象存储 bucket 的可移植性。

备选方案是把 bucket 保留在每个 provider 子配置中。该方案继续让 Local 缺少同一概念，且
未来新增 OSS/OBS 时会重复语义，故不采用。

### Starter 负责 Local 桶目录映射

`LocalFileStorageAdapter` 保持将其 `root` 与 `objectKey` 相拼的通用职责。Starter 在创建
Local 适配器时传入 `local.root.resolve(bucket)`；路径先规范化并检查仍在 `local.root` 内。
这样直接使用 adapter 的现有用户不受构造器 API 变更影响。

### 移除 `minio.bucket`

Starter 不再声明或读取 `minio.bucket`，构建 `MinioStorageConfiguration` 时传入顶层 bucket。
保留该字段作为隐式兼容回退会允许部署在未检查配置的情况下继续使用不同语义，故明确作为
breaking change 移除并在文档中给出上提迁移方式。

## Risks / Trade-offs

- [旧 Local 对象在根目录下] → 升级后不会被读取；升级前使用既有导出/导入能力或将对象目录
  移至 `local.root / bucket`，并在发布说明中明确此步骤。
- [错误 bucket 造成目录逃逸] → 启动期单段校验并使用规范化后的 `resolve` + `startsWith`。
- [旧 MinIO YAML 被忽略] → 顶层 bucket 为必填，遗留配置不能满足启动条件，因此会快速失败。
- [未来云厂商 bucket 规则更严格] → 当前约束取 Local 安全与对象存储可移植性的交集；provider
  特有规则在对应 adapter 加入时补充校验。

## Migration Plan

1. 先为每个环境增加 `dinghai.file-storage.bucket=<原 MinIO bucket 或计划目录名>`。
2. MinIO 删除 `dinghai.file-storage.minio.bucket`，其余 endpoint 与凭据配置不变。
3. Local 将已有对象移动到 `local.root / bucket /` 下，或用离线导出/导入迁移到目标 provider。
4. 若需回滚应用版本，恢复旧 YAML；Local 已迁移的数据需先移动回旧根目录布局或用迁移包恢复。
