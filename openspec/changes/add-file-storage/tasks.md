## 1. Maven 模块与项目基线

- [x] 1.1 在父 `pom.xml` 注册 core、local、minio、spring-boot-starter 四个模块，集中管理 Java 21、Spring Boot 与 MinIO SDK 版本
- [x] 1.2 创建四个模块 POM、包结构和自动装配资源；按模块隔离 MinIO、Spring Web 与测试依赖
- [x] 1.3 执行 project-hygiene：扫描新增 Maven/Java/YAML/环境变量，更新托管基线块与 `.env.example` 占位符；运行 `git ls-files` 密钥检查和 `git check-ignore/check-attr` 抽查

## 2. Core 契约与安全模型

- [x] 2.1 TDD：先添加 `ObjectKeyTest`、请求模型校验测试，覆盖中文、多级 key、空值、绝对路径、`..`、反斜杠、控制字符和 UTF-8/段长度边界
- [x] 2.2 实现 `ObjectKey`、`UploadRequest`、`ObjectMetadata`、`DownloadObject`、缓存策略和上传限制模型；所有新类/方法补齐项目约定的类级与方法级 Javadoc 和 author
- [x] 2.3 TDD：先添加 `FileStorageContractTest`，覆盖 put/get/stat/exists/delete 统一返回语义、覆盖写入和稳定异常分类
- [x] 2.4 实现 `FileStorage`、预签名请求/结果模型、`FileStorageException` 错误码和输入校验；不引用任何供应商 SDK
- [x] 2.5 运行 core focused tests 与 `mvn -pl dinghai-file-storage-core test`，确认无 null 返回、参数数量和魔法常量违规

## 3. Local 适配器

- [x] 3.1 TDD：先添加本地契约测试，覆盖 `1/1/1/1.png`、中文 key、覆盖写入、父目录创建、缺失对象、删除幂等和路径穿越拒绝
- [x] 3.2 实现 root resolve/normalize 封闭校验、临时文件流式写入、大小计数、SHA-256 ETag、同文件系统原子移动和元数据原子持久化
- [x] 3.3 TDD：先添加本地签名测试，覆盖有效/过期/篡改/错误方法/错误 key 签名及上传大小、类型约束
- [x] 3.4 实现 HMAC-SHA-256 本地预签名载荷、常量时间验签和本地适配器 URL 委托；签名有效期受上限约束
- [x] 3.5 运行 `mvn -pl dinghai-file-storage-local -am test`，补充失败流、元数据缺失安全默认值和 Windows/Unix 路径兼容验证

## 4. MinIO 适配器

- [x] 4.1 TDD：先添加 MinIO 配置验证和异常映射测试，覆盖必需属性缺失、bucket 不自动创建、不存在对象、认证失败和超时分类
- [x] 4.2 实现固定 bucket 的 `MinioFileStorageAdapter`，设置连接/读取/写入超时，映射内容类型、缓存控制、ETag 和最后修改时间
- [x] 4.3 TDD：先添加 MinIO 预签名测试，验证 GET URL 与 POST policy 绑定 key、过期时间、大小、内容类型、inline/attachment 和缓存响应参数
- [x] 4.4 实现 MinIO 原生预签名上传/下载，并确保 secret/access key、完整签名 URL 和 SDK 原始异常不进入日志或公共错误
- [x] 4.5 运行 `mvn -pl dinghai-file-storage-minio -am test`；真实 MinIO/Testcontainers 契约测试仅在显式环境开关启用

## 5. Spring Boot Starter 与后端路由

- [ ] 5.1 TDD：先添加属性绑定/校验和自动装配测试，覆盖 provider local/minio、未知 provider、缺失生产签名密钥、仅注册一个主 Bean 和 direct access 默认关闭
- [ ] 5.2 实现 `FileStorageProperties`、provider 路由、条件 Bean、启动 fail-fast 校验和 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- [ ] 5.3 TDD：先添加 `FileStorageAccessPolicy` 测试，覆盖策略允许/拒绝下载、预览、上传及无策略时 direct access 拒绝
- [ ] 5.4 实现可选 `FileStorageAccessPolicy` 扩展点、`web.enabled`、默认 `/files` 与可配置 `path-prefix`；Controller 只转发，不记录业务日志
- [ ] 5.5 运行 starter focused tests 与 `mvn -pl dinghai-file-storage-spring-boot-starter -am test`

## 6. Web 预签名、预览与 HTTP 缓存

- [ ] 6.1 TDD：先添加本地端点测试，覆盖签名上传/下载/预览、默认无签名拒绝、inline/attachment、中文 URL 编码、过期拒绝和最大上传限制
- [ ] 6.2 实现 `/files/upload/{*key}`、`/files/download/{*key}`、`/files/preview/{*key}` 端点及签名验证；处理流式请求、Content-Type 和稳定 HTTP 错误响应
- [ ] 6.3 TDD：先添加缓存和范围请求测试，覆盖 ETag、Last-Modified、Cache-Control、Expires、If-None-Match/If-Modified-Since 304、单字节 Range 206/416
- [ ] 6.4 实现 HTTP/CDN 缓存头、条件请求和单范围流式响应；默认 `private, max-age=0`，公开/immutable 只接受显式策略
- [ ] 6.5 按 logging-boundaries 检查 HTTP 日志：不在 Controller 打业务日志，不记录 token/签名/完整 URL/请求体；确认异常包含安全上下文但不泄密
- [ ] 6.6 运行 `mvn -pl dinghai-file-storage-spring-boot-starter -am test` 与端点集成测试，确认无内存全量缓存和无阻塞无界线程池

## 7. 文档、集成验证与审计

- [ ] 7.1 更新中文 README、README_EN 和 `.agents/skills/dinghai/SKILL.md`：模块坐标、配置、key 规则、预签名、预览、缓存、安全默认值和后续云适配边界保持一致
- [ ] 7.2 添加/更新模块契约与配置文档测试，验证默认 provider、100 MB 上限、`/files` 默认路径和敏感配置仅由环境注入
- [ ] 7.3 运行 `openspec validate add-file-storage --strict`、`mvn -q test`、必要的 `mvn -q verify`，并记录 MinIO 集成测试是否跳过及原因
- [ ] 7.4 执行 plan-implementation consistency audit、backend-reliability checklist、logging-boundaries 和 code-review-spec 全量审查，修复所有应修问题后重跑 focused/broader verification
- [ ] 7.5 通过门禁后逐项勾选任务，按任务分支提交带 OpenSpec footer 的原子提交，合并回 `main`，最终审计并归档 `add-file-storage`
