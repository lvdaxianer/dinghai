## Why

Dinghai 本地存储删除对象后只删除文件和元数据，模型或场景资源留下空的多级目录，
造成磁盘目录持续膨胀。需要在本地适配器内部安全回收空目录，同时保持删除操作幂等，
不改变任何远端对象存储实现。

## What Changes

- 修复 `LocalFileStorageAdapter.delete(ObjectKey)`，删除成功后回收 root 内空父目录。
- 为 `LocalFileStorageAdapter` 增加本地专用 `deletePrefix(String prefix)`，删除前缀下对象及元数据后回收多级空目录。
- 增加 `dinghai.file-storage.local.cleanup-empty-directories` 配置开关，默认开启，关闭时保留文件删除但不回收目录。
- 增加 root 边界、路径穿越、符号链接、并发删除和失败日志保护。
- 增加本地存储与远端适配器回归测试，并补充文件存储规格。

## Capabilities

### New Capabilities
- `local-empty-directory-cleanup`: 本地对象删除后的安全空目录回收、前缀删除和可配置行为。

### Modified Capabilities
- `file-storage`: 增加本地删除幂等、安全边界和空目录回收要求。

## Impact

- 代码：`dinghai-file-storage/local` 的 `LocalFileStorageAdapter`、`LocalObjectPathResolver` 及本地测试。
- 配置：`dinghai-file-storage/spring-boot-starter` 的 `FileStorageProperties`、自动装配和配置文档。
- 公共 `FileStorage` 接口、MinIO、RustFS、阿里云 OSS、华为云 OBS 适配器不变。
- 不修改业务项目 `qinghai-server`；发布新 Dinghai starter 后，业务只需按需设置新开关，默认无需改代码。
