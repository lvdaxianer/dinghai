## Why

Tomcat 的 `sendfile` 请求属性在当前 Linux/JDK 环境不能可靠传递包含中文或其他非 ASCII
字符的本地文件名。响应仍可能返回成功状态和长度，却没有传输正文，导致浏览器无法预览
Unicode 名称的本地对象。

## What Changes

- 仅允许绝对路径全为 ASCII 的普通本地文件注册 Tomcat `sendfile`。
- 对非 ASCII 本地路径自动回退到既有有界流式读取，保留内容长度、Range、读取许可和
  UTF-8 object key 语义。
- 为 ASCII 与 Unicode 路径补充注册、流式回退和真实嵌入式 Tomcat HTTP 回归测试。

## Capabilities

### New Capabilities

- `tomcat-sendfile-path-safety`: 为 Tomcat 零拷贝注册定义本地路径字符集安全边界。

### Modified Capabilities

- `file-storage-web-access`: 本地文件传输自动回退条件增加非 ASCII 绝对路径。

## Impact

- `TomcatSendfileTransfer` 的 sendfile 资格判断。
- FileStorage Web 的本地与远程缓存本地文件读取路径。
- Starter 模块的单元及嵌入式 Tomcat 集成测试。
