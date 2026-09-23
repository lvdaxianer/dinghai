## 1. UTF-8 本地路径兼容

- [x] 1.1 为中文文件、URL 编码 key、中文目录、UTF-8 路径字节和目录穿越补充失败测试。
- [x] 1.2 将已验证 object key 转换为 UTF-8 `file:` URI 路径，并保留本地安全校验。
- [x] 1.3 为 `/rsm/origin/**` URL 编码中文文件名补充 Web 兼容回归测试。
- [x] 1.4 执行 local/starter 测试、starter 构建、OpenSpec 验证和审查。
