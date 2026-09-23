## Why

本地磁盘适配器把 Unicode 对象 key 直接转换为 `Path`，在 JDK 21 的本机文件名
编码为 ASCII 时会抛出不可映射字符错误，导致既有中文文件无法通过兼容 Web 地址读取。

## What Changes

- 以 UTF-8 `file:` URI 字节构造本地对象路径，避免对象 key 受本机编码影响。
- 保留对象 key URL 解码、目录穿越拒绝与符号链接逃逸保护。
- 为中文文件、编码 URL、中文目录和旧 Web 路由补充回归测试。

## Capabilities

### New Capabilities

- `local-unicode-object-paths`: 本地文件存储以 UTF-8 对象 key 安全解析已有非 ASCII 路径。

### Modified Capabilities

- `file-storage`: 扩展本地适配器的 Unicode 路径持久化契约。
- `file-storage-key-read`: 说明旧真实 key Web 读取地址的 Unicode 兼容性。

## Impact

影响 local 模块的对象路径解析和 local/starter 测试；不影响 MinIO、默认配置、
`qinghai-server` Controller 或已存在的本地文件布局。
