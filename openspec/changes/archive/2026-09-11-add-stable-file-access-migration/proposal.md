## Why

私有 bucket 不能让浏览器直接访问，但临时预签名 URL 不能作为业务中长期保存
的文件地址。文件还需要能从本地磁盘以目录包方式安全迁移至 MinIO，而不通过
HTTP 搬运大文件。

## What Changes

- 提供无数据库、不可解码、确定性的稳定文件读取 ID 和匿名读取代理地址。
- 使 Web 流式读取基于统一 `FileStorage` 契约，支持 Local、MinIO 及后续
  provider，并保留缓存、Range、预览和下载行为。
- 按 provider 默认启用二级磁盘读取缓存：仅本地磁盘 provider 默认关闭，所有
  非本地 provider 默认开启，并在每天 00:00 清理长期未访问的缓存副本。
- 提供服务器目录导出/导入：以 SHA-256 manifest 校验、流式复制、完成目录与
  固定失败目录处理迁移包。
- 增加逻辑 storage alias，使相同对象从磁盘迁移到 MinIO 后可保持稳定读取 ID。
- **BREAKING**：读取 ID 密钥轮换后，使用旧密钥生成的稳定读取地址失效。

## Capabilities

### New Capabilities

- `stable-file-access`: 私有对象的确定性加密读取 ID 和匿名流式读取地址。
- `file-storage-offline-migration`: 服务器目录中的校验导入导出迁移包。

### Modified Capabilities

- `file-storage`: 添加后端中立的对象枚举契约和逻辑 storage alias。
- `file-storage-web-access`: 统一所有 provider 的受控流式读取响应。

## Impact

影响 Core 存储契约、本地/MinIO 适配器、Spring Boot Starter 配置、Web
Controller 与缓存清理任务，并新增 Google Tink 认证加密依赖。迁移在服务器
本地目录完成，不接受完整外部 URL 或经 API 上传大文件。
