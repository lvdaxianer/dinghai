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

#### Scenario: 传输中途失败

- **WHEN** 本地文件传输已开始，中途发生 IO 失败、文件被并发删除或客户端断连
- **THEN** 响应立即终止，不重试、不切换流式重发，并保留可诊断上下文

### Requirement: 零拷贝传输并发计量

流式回退 MUST 使用最大并发读取限制，permit 自异步复制任务开始持有，至任务完成或失败时释放。原生 Tomcat sendfile 由于公开 Servlet API 不提供传输完成、失败或断连回调，MUST 使用容器的连接与执行器配置作为独立并发边界，且不得宣称受 `web.max-concurrent-reads` 控制。

#### Scenario: 流式并发耗尽时读取失败

- **WHEN** 流式回退读取的并发 permit 耗尽
- **THEN** 该次读取失败且不排队等待

#### Scenario: Tomcat sendfile 使用容器级边界

- **WHEN** Local 主文件或已校验缓存文件在 Tomcat 明文未压缩请求中注册 sendfile
- **THEN** 请求不获取应用级读取 permit，并由 Tomcat 的连接与执行器配置限制传输资源
