# 本地文件存储 Unicode 路径设计

## 目标

在 JDK 21 的本机文件名编码不是 UTF-8 时，本地文件存储仍能以 UTF-8
对象 key 读取和写入既有的中文及其他非 ASCII 文件名；不改变 Web 路由、
默认配置或业务项目 Controller。

## 方案

对象 key 继续由 `ObjectKey` 使用 UTF-8 解码、NFC 规范化和路径安全校验。
`LocalObjectPathResolver` 不再把 key 直接传给 `Path.resolve(String)`，因为
Unix NIO 会按 `sun.jnu.encoding` 把该字符串编码为本机字节。解析器改为：

1. 将已验证 key 的每个 UTF-8 字节编码为 `file:` URI 的原始路径字节；
2. 基于已规范化根目录的 `toUri()` 拼接绝对 `file:` URI；
3. 使用 `Path.of(URI)` 获得路径。Unix NIO 对 `file:` URI 的 `%xx` 原始字节
   不经本机字符集重编码；
4. 继续执行 `startsWith(root)`、既有父目录 `toRealPath()` 检查和最终符号链接
   检查。

这样 Linux 文件系统内的 UTF-8 字节名与 URL/`ObjectKey` 的 UTF-8 语义一致，
且 `..`、反斜杠、NUL 与符号链接逃逸仍在原边界被拒绝。

## 测试

测试覆盖直接中文文件名、百分号编码 key、中文目录、UTF-8 字节路径构造和
目录穿越/符号链接保护。Web MVC 回归测试验证旧 `/rsm/origin/**` 地址以编码的
中文文件名返回既有对象。
