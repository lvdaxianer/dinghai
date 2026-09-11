## MODIFIED Requirements

### Requirement: 后端中立的文件存储契约
组件 SHALL 在 `dinghai-file-storage-core` 提供不含供应商 SDK 类型的 `FileStorage` 契约，支持对象覆盖写入、流式读取、元数据查询、存在判断、幂等删除、预签名上传和预签名下载。读取和元数据查询不存在对象时 MUST 返回 `Optional.empty()`；删除不存在对象时 MUST 返回 `false`；写入相同 key 时 MUST 覆盖既有对象。MinIO 预签名 POST 上传 MUST 在 `PresignedUpload.formFields` 返回服务端 policy 所要求的固定 bucket、对象 key、内容类型、缓存策略及 SDK 生成的签名字段；调用方提交全部表单字段时，服务端 MUST 能验证该 policy。

#### Scenario: 通过统一契约覆盖并读取对象
- **WHEN** 调用方通过 `FileStorage` 将对象写入一个 key 后再读取该 key
- **THEN** 读取结果包含可关闭的流和与写入匹配的统一元数据，且不需要使用本地或 MinIO SDK 类型

#### Scenario: 删除缺失对象
- **WHEN** 调用方删除一个不存在的对象 key
- **THEN** 操作返回 `false`，且不会将对象不存在作为后端故障抛出

#### Scenario: 提交 MinIO 预签名 POST 表单
- **WHEN** 调用方使用 MinIO 适配器为对象生成预签名 POST 上传并提交返回的全部表单字段和文件内容
- **THEN** 表单包含 bucket、key、内容类型、缓存策略与 SDK 签名字段，且 MinIO 接受上传并保存匹配的对象元数据
