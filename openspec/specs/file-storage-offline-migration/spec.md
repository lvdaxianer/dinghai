# file-storage-offline-migration Specification

## Purpose

通过服务器目录包在不同文件存储 provider 之间安全迁移大文件，
在校验完整性和元数据后完成导入，并且不经 HTTP 搬运对象内容。

## Requirements

### Requirement: 服务器目录导出迁移包
组件 SHALL 将当前 provider 的对象导出至配置的服务器 export root 下的调用方指定 package name 目录。导出包 MUST 包含版本化 manifest 和对象文件树；每项 MUST 记录逻辑 storage alias、对象 key、相对文件路径、长度、内容类型、缓存策略和 SHA-256。对象内容 MUST 以流式方式复制和计算摘要，不得经 HTTP 上传或将完整对象加载到 JVM 内存。

#### Scenario: 从本地磁盘导出可校验包
- **WHEN** 调用方请求导出一个包含对象的 local provider 至合法 package name
- **THEN** 组件在 export root 创建 manifest 与文件树，且每一项的 SHA-256 和长度匹配源对象

### Requirement: 服务器目录导入迁移包
组件 SHALL 仅接受 package name 作为导入接口输入，并从 import root 下的同名目录读取迁移包。组件 MUST 校验 package name、manifest 版本、所有相对路径、对象 key、长度和 SHA-256 后才流式写入当前 provider。导入相同 key 且相同摘要的对象 MUST 幂等跳过；相同 key 且不同摘要 MUST 失败，且不得静默覆盖。

#### Scenario: 导入本地导出包至 MinIO
- **WHEN** 运维将本地 provider 导出的合法包放入 MinIO 环境的 import root 并仅提交 package name
- **THEN** 组件校验 manifest 后将对象流式写入私有 MinIO bucket，保留内容类型和缓存策略

#### Scenario: 重复导入相同包
- **WHEN** 相同迁移包被再次导入且目标对象摘要匹配
- **THEN** 组件跳过已存在对象并报告成功，且不重写对象

### Requirement: 导入包终态目录和错误清单
导入成功后组件 SHALL 将整个 package 目录移动至配置的 completed root；导入失败时 SHALL 将整个 package 目录移动至固定 failed root，并在包内写入 UTF-8 `errors.ndjson`。错误记录 MUST 包含对象相对路径或 manifest 行号、稳定错误码和安全摘要；MUST NOT 包含密钥、完整存储 URL、签名 URL 或对象内容。

#### Scenario: 校验失败后保留可诊断包
- **WHEN** 导入包中对象的 SHA-256 与 manifest 不匹配
- **THEN** 组件不写入该对象，将包移动至 failed root，并写入包含校验错误码的 `errors.ndjson`
