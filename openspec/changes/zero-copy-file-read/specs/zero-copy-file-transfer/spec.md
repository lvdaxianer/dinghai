## ADDED Requirements

### Requirement: 本地文件零拷贝优先传输

Web 读取 SHALL 在对象来源为 Local 主文件或已校验的远程缓存文件、响应条件允许文件传输且运行容器支持时优先使用零拷贝或等价的内核文件传输。容器不支持、连接或响应需要用户态处理时 MUST 回退到现有有界流式复制，并保持内容、Range、条件响应和错误语义一致。

#### Scenario: Local 完整对象读取

- **WHEN** Local provider 返回完整普通文件且请求不是复杂处理场景
- **THEN** Web 层优先使用本地文件传输，且不把整个文件读入 JVM 堆

#### Scenario: 零拷贝不可用时回退

- **WHEN** 容器、协议或响应包装不支持本地文件传输
- **THEN** 请求使用有界流式复制返回相同对象内容

#### Scenario: 本地单区间读取

- **WHEN** 请求包含合法单区间 Range 且对象来源是本地文件
- **THEN** 响应只传输指定范围并保留 `206`、`Content-Range` 和长度语义
