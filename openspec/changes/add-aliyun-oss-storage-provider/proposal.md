## Why

现有文件存储组件只能接入本地磁盘、MinIO 和 RustFS，无法直接使用阿里云 OSS 的原生 API、签名协议和错误语义。需要在不改变后端中立 `FileStorage` 契约的前提下，允许应用按配置切换到固定 bucket 的 OSS 后端。

## What Changes

- 新增基于阿里云 OSS Java SDK 的独立 `dinghai-file-storage-oss` 模块。
- 支持固定 bucket 的对象写入、流式读取、元数据查询、惰性枚举、幂等删除，以及 OSS 原生 POST/GET 预签名。
- 在 Starter 中增加 `provider=oss`、`dinghai.file-storage.oss` 配置绑定、启动期安全校验和远端读取缓存支持。
- 更新中英文使用文档，并增加适配器与自动装配测试。

## Capabilities

### New Capabilities

- `aliyun-oss-storage-provider`: 使用阿里云 OSS 原生 SDK 的固定 bucket 文件存储 provider。

### Modified Capabilities

- `file-storage`: 扩展 provider 选择、远端缓存和统一文件存储契约的 OSS 行为。

## Impact

- 新增 `dinghai-file-storage-oss` Maven 模块和阿里云 OSS SDK 依赖。
- 修改文件存储聚合 POM、Starter 依赖、配置、自动装配及对应测试。
- 修改 `README.md` 和 `README_EN.md` 的支持后端、配置项和预签名说明。
