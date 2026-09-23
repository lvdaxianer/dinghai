## Context

JDK 21 将默认 Java 字符集固定为 UTF-8，但 Unix NIO 对 `Path` 字符串的系统调用
编码仍使用启动时的 `sun.jnu.encoding`。当该编码为 ASCII 时，`Path.resolve(String)`
无法把中文 key 编为文件系统字节。

## Decision

仅在 `LocalObjectPathResolver` 的对象 key 到 `Path` 边界使用标准 `file:` URI。
key 的 UTF-8 字节按 RFC 3986 百分号编码，根目录 URI 的原始路径与 key 拼接后使用
`Path.of(URI)` 解析。JDK Unix provider 对 URI 的 `%xx` 原始字节直接构造路径字节，
不调用 `sun.jnu.encoding`。

## Safety

此转换只接收已通过 `ObjectKey` 校验的 key。解析后的候选路径继续进行根目录包含、
已有父目录真实路径和最终对象符号链接检查；不接受绝对路径、`.`、`..`、反斜杠或
控制字符。

## Compatibility

存储布局、路径前缀与 Controller 均不变。既有 `/rsm/origin/**` 地址在 Spring 解码后
进入同一 `ObjectKey` 与本地解析器链路，因此无需业务项目改动。
