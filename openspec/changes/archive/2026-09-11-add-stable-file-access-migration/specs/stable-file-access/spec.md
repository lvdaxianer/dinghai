## ADDED Requirements

### Requirement: 确定性且不可解码的稳定文件 ID
组件 SHALL 使用配置的 AES-SIV 密钥对规范化的 `storageAlias` 与 `ObjectKey`
组成的逻辑对象引用执行确定性认证加密，并生成版本化稳定文件 ID。ID MUST 不
包含明文 object key、provider、bucket、磁盘路径或密钥；相同逻辑引用和同一
活动密钥 MUST 生成相同 ID；被篡改、格式错误或使用已轮换密钥生成的 ID MUST
被拒绝。密钥轮换后组件 MUST 仅接受活动密钥，旧 ID 可以整体失效。

#### Scenario: 重新生成相同对象的稳定 ID
- **WHEN** 调用方使用同一 storage alias、对象 key 和活动读取 ID 密钥两次生成 ID
- **THEN** 两次结果完全相同，且从 ID 文本无法读取对象 key

#### Scenario: 拒绝篡改或旧密钥 ID
- **WHEN** 客户端请求包含已篡改、无效格式或由已轮换密钥生成的稳定 ID
- **THEN** 组件拒绝请求且不读取任何存储对象

### Requirement: 无组件登录鉴权的私有对象流式读取
Starter SHALL 提供以稳定文件 ID 为路径参数的读取、预览和下载端点。端点 MUST
在不调用组件用户鉴权策略的前提下解密 ID、从当前 `FileStorage` provider 流式
读取私有对象，并且不得暴露 bucket 地址、对象 key 或存储密钥。读取端点 MUST
支持 ETag、Last-Modified、Cache-Control、304 和单 Range 响应；预览 MUST 为
`inline`，下载 MUST 为 `attachment`。

#### Scenario: 私有 MinIO 对象通过稳定 ID 预览
- **WHEN** 浏览器请求有效稳定 ID 对应的私有 MinIO 对象预览地址
- **THEN** Starter 以 `inline` 流式返回内容和缓存/Range 响应头，浏览器不直接访问 bucket

#### Scenario: 稳定读取地址不使用组件用户授权
- **WHEN** 嵌入平台将有效稳定读取地址交给浏览器且请求不带组件认证主体
- **THEN** Starter 仅校验稳定 ID 后读取对象，不调用 `FileStorageAccessPolicy`

### Requirement: 按 provider 默认启用二级磁盘读取缓存
Starter SHALL 按 provider 默认启用本地二级磁盘缓存：仅 `local` provider 默认关闭，
所有非 `local` provider 默认开启；显式 `cache.enabled` MUST 能覆盖这两个默认值。缓存命中时 MUST 先向对象存储查询元数据，并且仅在
ETag、长度和最后修改时间均匹配时从磁盘副本返回；对象不存在或元数据不匹配时
MUST 删除副本并从 provider 流式读取。未命中且对象大小不超过配置阈值时，当前
请求 MUST 直接流式返回 provider 内容，同时由独立、有界执行器重新打开对象流
异步写入临时缓存文件，校验后原子发布。缓存键和 sidecar MUST 不暴露对象 key。

#### Scenario: 按 provider 应用缓存默认开关
- **WHEN** 应用使用默认缓存配置
- **THEN** Local/磁盘 provider 不创建或使用二级读取缓存，MinIO 及其他非 Local provider 创建并使用二级读取缓存

#### Scenario: MinIO 缓存命中通过元数据校验
- **WHEN** 有效稳定 ID 对应的 MinIO 对象存在匹配 ETag、长度和最后修改时间的磁盘缓存副本
- **THEN** 组件先完成 MinIO 元数据校验，再从缓存文件流式返回内容而不下载对象内容

#### Scenario: MinIO 对象更新或删除使缓存失效
- **WHEN** MinIO `stat` 显示对象不存在，或其 ETag、长度、最后修改时间与缓存 sidecar 不匹配
- **THEN** 组件删除旧缓存副本，并从当前 provider 返回对象或按不存在语义失败

### Requirement: 二级缓存的空闲过期清理
Starter SHALL 每日服务端本地时间 00:00 扫描非本地 provider 的缓存目录，并删除
最后访问时间超过可配置 idle TTL 的完整缓存副本及其 sidecar。清理错误 MUST
保持副本以便下次清理重试，且 MUST NOT 阻断读取请求或记录存储密钥、完整 URL、
签名 URL 或对象内容。

#### Scenario: 清理超出空闲期限的缓存副本
- **WHEN** 每日 00:00 清理任务发现缓存副本的最后访问时间已超过 idle TTL
- **THEN** 任务删除该副本和 sidecar，且保留尚未到期的副本
