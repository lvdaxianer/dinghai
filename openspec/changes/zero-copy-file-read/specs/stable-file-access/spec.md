## MODIFIED Requirements

### Requirement: 按 provider 默认启用二级磁盘读取缓存

Starter SHALL 按 provider 默认启用二级磁盘读取缓存：`local` provider 不得创建或使用二级读取缓存，所有非 `local` provider 默认开启。对于 `local` provider，显式 `cache.enabled=true` MUST 使应用启动失败并提示移除该配置；对于非 `local` provider，显式 `cache.enabled=false` MUST 禁用缓存。缓存命中时 MUST 先向对象存储查询元数据，并且仅在 ETag、长度和最后修改时间均匹配时从磁盘副本返回；对象不存在或元数据不匹配时 MUST 删除副本并从 provider 流式读取。未命中且对象大小不超过配置阈值时，当前请求 MUST 直接流式返回 provider 内容，同时由独立、有界执行器重新打开对象流异步写入临时缓存文件，校验后原子发布。缓存键和 sidecar MUST 不暴露对象 key。

#### Scenario: 按 provider 应用缓存默认开关
- **WHEN** 应用使用默认缓存配置
- **THEN** Local/磁盘 provider 不创建或使用二级读取缓存，MinIO 及其他非 Local provider 创建并使用二级读取缓存

#### Scenario: 拒绝 Local 显式开启缓存
- **WHEN** provider 为 `local` 且配置显式设置 `cache.enabled=true`
- **THEN** 应用启动失败并提示删除已移除的 Local 二级缓存配置

#### Scenario: MinIO 缓存命中通过元数据校验
- **WHEN** 有效稳定 ID 对应的 MinIO 对象存在匹配 ETag、长度和最后修改时间的磁盘缓存副本
- **THEN** 组件先完成 MinIO 元数据校验，再从缓存文件流式返回内容而不下载对象内容

#### Scenario: MinIO 对象更新或删除使缓存失效
- **WHEN** MinIO `stat` 显示对象不存在，或其 ETag、长度、最后修改时间与缓存 sidecar 不匹配
- **THEN** 组件删除旧缓存副本，并从当前 provider 返回对象或按不存在语义失败
