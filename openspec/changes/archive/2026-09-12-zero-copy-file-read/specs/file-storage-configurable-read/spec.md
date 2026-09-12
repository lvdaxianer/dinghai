## MODIFIED Requirements

### Requirement: 可配置读取与缓存资源上限

Starter SHALL expose positive defaults and configuration bindings for Web read buffer size (`128KB`), maximum concurrent reads (`128`), cache fill buffer size (`128KB`), maximum concurrent fills (`4`), and cache queue capacity (`32`). Runtime components MUST use these values and MUST reject non-positive values during startup. These limits govern the streaming fallback and asynchronous cache fill; they MUST NOT require whole-file JVM buffering for zero-copy-capable local sources.

#### Scenario: 默认配置保护内存和并发

- **WHEN** no resource tuning values are configured
- **THEN** the starter uses `128KB`, `128`, `128KB`, `4`, and `32` respectively

#### Scenario: 零拷贝路径不按文件大小分配堆内存

- **WHEN** Local 或已完成缓存副本走本地文件传输
- **THEN** Web 层不按对象大小创建 JVM 缓冲区，流式配置仅用于回退路径
