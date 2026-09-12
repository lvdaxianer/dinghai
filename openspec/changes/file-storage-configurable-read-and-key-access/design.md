# Design

## 配置

`FileStorageProperties.Web` 增加 `readBufferSize`（默认 `128KB`）和 `maxConcurrentReads`（默认 `128`）；`Cache` 增加 `bufferSize`（默认 `128KB`）、`maxConcurrentFills`（默认 `4`）和 `queueCapacity`（默认 `32`）。使用 Spring Boot `DataSize` 绑定字节数，启动时拒绝非正数值。

## 读取控制

`FileStorageWebController` 接收配置后的缓冲区大小和并发上限。每个实际响应体写出期间占用一个读取许可，复制完成或失败后释放；无法取得许可时返回服务不可用，避免无限堆积。读取仍保持流式传输，不创建整文件 byte 数组。

## 缓存控制

`ProviderFileReadCache` 使用配置的填充线程数、缓冲区大小和 `ArrayBlockingQueue` 容量。队列满时拒绝异步填充，当前用户读取继续直通 provider。单 key 的 single-flight 行为保持不变。

## Object key 路由

新增 `read/key` 命名空间，避免与现有 `/read/{identifier}` 稳定 ID 路由产生歧义。三个路由均解析规范化 object key 后复用现有读取流程，因此继续支持 ETag、304、Range、缓存头和二级磁盘缓存。object key 路由不绕过现有签名或宿主直接访问授权。

## 验证

- 属性绑定和默认值测试。
- 缓存执行器参数与队列满时直通行为测试。
- object key 读取、预览、下载路由测试。
- `mvn test`、OpenSpec 严格校验和差异检查。
