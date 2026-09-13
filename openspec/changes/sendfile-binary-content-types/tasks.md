## 1. 核心实现

- [x] 1.1 新增包私有 `SendfileContentTypes` 类（`spring-boot-starter` 模块，与 `TomcatSendfileTransfer` 同包），实现 `isIncompressible(String)`：忽略参数、大小写不敏感；精确集合（`application/octet-stream`、`application/pdf`、压缩包、字体等）+ 通配规则（`image/*` 排除 `image/svg+xml`、`video/*`、`audio/*`、`font/*`）；空/非法输入返回 false
- [x] 1.2 `TomcatSendfileTransfer.register(...)` 签名增加 `String contentType` 参数，回退条件由 `acceptsCompression(request)` 收窄为 `acceptsCompression(request) && !SendfileContentTypes.isIncompressible(contentType)`
- [x] 1.3 `FileStorageWebController.stream(...)` 增加 `String contentType` 参数并传入 `register(...)`；`rangedResponse(...)` 两处调用传入 `metadata.contentType()`

## 2. 测试

- [x] 2.1 调整既有用例 `shouldFallBackToStreamingWhenTomcatRequestAcceptsCompression`（text/plain + gzip 仍回退，行为不变）
- [x] 2.2 新增用例：二进制类型（`application/octet-stream`、`image/png`、`application/pdf`）+ `Accept-Encoding: gzip` 仍注册 sendfile（请求属性 `org.apache.tomcat.sendfile.filename` 存在且 asyncNotStarted）
- [x] 2.3 新增用例：`image/svg+xml` + gzip 回退流式（通配排除规则生效）
- [x] 2.4 新增用例：未声明/非法 Content-Type + gzip 回退流式
- [x] 2.5 新增用例：`Accept-Encoding: identity` + 文本类型仍注册 sendfile（行为不变）
- [x] 2.6 新增用例：非本地 provider（远程缓存未命中或远程对象）始终流式
- [x] 2.7 新增 `SendfileContentTypes` 单元测试：精确集合、通配、排除、大小写、参数忽略、非法输入

## 3. 文档

- [x] 3.1 更新 `dinghai-file-storage/README.md` 设计要点/下载段落：说明零拷贝生效条件收窄为"可压缩文本类型才回退"
- [x] 3.2 更新 `dinghai-file-storage/README_EN.md` 对应段落

## 4. 验证

- [x] 4.1 `mvn -f dinghai-file-storage/pom.xml test` 全模块通过
- [x] 4.2 `openspec status --change sendfile-binary-content-types` 与 `openspec validate sendfile-binary-content-types` 通过
- [x] 4.3 安装 `mvn -f pom.xml install -DskipTests -q`，重建并重启 demo，实测 500MB 对象在 `Accept-Encoding: gzip` 下命中零拷贝（吞吐对齐基线），文本文件回退后字节正确
