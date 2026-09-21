## Why

dinghai-file-storage 已支持本地磁盘、MinIO、RustFS 和阿里云 OSS，但华为云 OBS 用户仍无法复用统一的文件存储契约。新增独立 OBS provider 可以在不改变现有业务接口的前提下，提供华为云对象读写、预签名访问和大文件分片上传能力。

## What Changes

- 新增 `dinghai-file-storage-huaweicloud-obs` Maven 模块，使用华为云官方 `esdk-obs-java` SDK。
- 支持 OBS 对象上传、下载、元数据查询、存在判断、删除和分页列举。
- 支持 OBS 原生 PUT/GET 预签名 URL。
- 支持 multipart 初始化、分片上传、分片列举、完成和取消，并兼容现有大文件上传 REST 流程。
- 支持长期 AK/SK，以及 AK/SK/Security Token 临时凭证；保留可刷新凭证 Provider 的扩展点。
- 将 `huaweicloud-obs` 接入 Spring Boot starter 的 provider 选择与启动期配置校验。
- 更新中英文文件存储文档、Maven 模块清单和测试说明。
- 不创建或修改 OBS bucket、ACL、CORS 或 bucket policy；不改变现有 `FileStorage` 和 `MultipartFileStorage` 公共契约。

## Capabilities

### New Capabilities

- `huaweicloud-obs-storage`: 提供华为云 OBS 固定 bucket 文件存储、预签名访问、长期/临时凭证和 multipart 大文件上传能力。

### Modified Capabilities

无。

## Impact

- 代码：`dinghai-file-storage` 聚合 POM、新增 OBS 适配器模块、Spring Boot starter 自动装配与配置属性。
- 依赖：新增华为云官方 OBS Java SDK，独立于 Huawei Cloud SDK Bundle。
- 配置：新增 `dinghai.file-storage.provider=huaweicloud-obs` 及 endpoint、region、凭证和超时配置。
- 测试：新增配置、凭证、异常映射、适配器和可选真实 OBS 生命周期测试。
- 文档：更新 provider 列表、配置表、环境变量和 multipart 接入说明。
