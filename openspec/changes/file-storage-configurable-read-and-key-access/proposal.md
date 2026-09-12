# Proposal: 可配置文件读取与真实对象地址

当前文件存储 Web 读取缓冲区、并发读取数和二级缓存填充参数存在硬编码，调用方无法根据部署资源进行调节；同时稳定 `fileId` 读取地址不能替代需要直接使用 object key 的集成场景。

本变更将这些参数纳入 `dinghai.file-storage.web` 和 `dinghai.file-storage.cache` 配置，并保留稳定 ID 路由，新增显式 object key 读取、预览和下载路由。真实 key 路由继续遵守现有预签名/宿主直接访问策略，不改变稳定 ID 匿名读取语义。

## Scope

- 增加读取缓冲区、最大并发读取、缓存缓冲区、缓存填充并发和队列容量配置及默认值。
- Web 读取和缓存填充使用配置值，并通过有界并发与有界队列保护资源。
- 增加 `/files/read/key/{*key}`、`/files/read/key/preview/{*key}`、`/files/read/key/download/{*key}`。
- 增加配置校验、单元测试和中英文使用说明。
