# 下载文件名响应头修复设计

## 背景

`FileStorageWebController.responseHeaders()` 当前固定返回
`Content-Disposition: <mode>; filename="file"`。由于对象元数据已经携带经过
`ObjectKey` 校验的真实 key，修复应复用该值，仅提取最后的文件名。

## 方案

在统一的 `responseHeaders` 方法中使用 `metadata.key().value()` 的最后一个路径
段，并通过 Spring `ContentDisposition.builder(...).filename(..., UTF_8)` 生成响应
头。这样普通预览、下载、legacy 直读和稳定读取都共享同一实现；不会把完整 key
暴露给浏览器，也不会改变现有读取与流式传输逻辑。

## 验证

使用已有 MockMvc 测试基础设施覆盖多级 key、`/rsm/**` legacy inline、普通
attachment/inline、中文文件名和路径穿越输入，并运行 starter 单元测试与 Maven
编译。qinghai-server 不在当前仓库，发布后需在其仓库升级 starter 版本并执行外部
集成编译。
