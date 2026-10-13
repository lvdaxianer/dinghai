## Context

当前公共文件存储 Starter 无条件依赖全部 provider 模块，运行时配置只能控制 Bean 选择，不能控制 Maven classpath。RustFS 还通过 MinIO 适配器实现，导致 RustFS 无法独立裁剪 MinIO SDK。本 change 需要同时调整 Maven 模块边界、Spring Boot 自动配置边界和 RustFS 的 S3 客户端实现。

## Goals / Non-Goals

**Goals:**

- 公共 Starter 不传递远端 provider SDK。
- 为 MinIO、RustFS、阿里云 OSS、华为云 OBS 提供独立 provider Starter。
- 继续使用同一个 `dinghai.file-storage.provider` 配置选择唯一主 Bean。
- RustFS 完全脱离 MinIO 模块和 `io.minio:minio`。
- 通过最小依赖树、自动配置和完整测试验证隔离效果。

**Non-Goals:**

- 不修改 `FileStorage`、`MultipartFileStorage` 公共契约。
- 不修改现有 Web API、对象 key 规则、缓存协议和迁移格式。
- 不做运行时自动探测 provider 或隐式回退。

## Decisions

### 公共 Starter 与 provider Starter

公共 Starter 只保留公共自动配置、Local 适配器和 Web/缓存/迁移能力。每个远端 provider 新增独立 Spring Boot Starter，依赖公共 Starter 和对应的底层适配器。该方案比在一个 Starter 中声明大量 optional 依赖更清晰，因为公共自动配置不会直接引用缺失的 SDK 类型，安全扫描边界也能通过 Maven 坐标直接验证。

### provider-factory SPI

公共 Starter 定义 `FileStorageProviderFactory`，公共自动配置通过 `ObjectProvider` 选择与配置值匹配的 factory。Local factory 在公共 Starter 中提供，远端 factory 由 provider Starter 条件注册。相比让公共配置通过反射加载实现类，SPI 让依赖关系和测试边界显式，同时避免反射字符串和类加载失败。

### RustFS 客户端

RustFS 适配器使用 AWS SDK for Java v2 S3 client/presigner，配置自定义 endpoint、path-style access、静态凭据和明确超时。它直接实现当前对象和 multipart 契约，并将 AWS SDK 异常映射到现有稳定错误码。相比继续复用 MinIO SDK，这能满足 RustFS 不带 MinIO JAR 的安全边界；相比自写 HTTP 签名，实现成本和签名正确性风险更低。

### 兼容策略

这是依赖声明层面的 BREAKING CHANGE：只声明公共 Starter 的远端应用必须增加匹配的 provider Starter。配置键和业务接口保持兼容，缺少 provider Starter 时以启动失败替代模糊的类缺失异常。

## Risks / Trade-offs

- RustFS 从 MinIO SDK 切换到 AWS SDK 后，异常、预签名和 multipart 语义需要契约测试覆盖。
- 同时声明多个 provider Starter 会增加依赖树，但不会创建未选中的客户端；最小依赖示例和 dependency tree 检查负责约束发布方式。
- provider 自动配置类需要保持 Spring Boot 条件顺序稳定；使用配置条件、classpath 条件和 `@ConditionalOnMissingBean`，并增加应用上下文测试。
- 现有公共自动配置文件较大，重构时只移动 provider 创建职责，保留现有公共 Web/缓存/迁移逻辑，避免无关行为变化。

## Migration Plan

1. 建立 provider-factory SPI 和公共 Starter 的 Local factory。
2. 拆出四个 provider Starter 及对应自动配置，删除公共 Starter 对远端模块的直接依赖。
3. 用 AWS S3 SDK 重写 RustFS client/adapter，并覆盖对象、预签名和 multipart 契约。
4. 更新根模块、文档、依赖示例和 OpenSpec；验证各 provider 最小依赖树。
5. 在集成分支上运行完整 Maven 测试、依赖树检查和 OpenSpec 校验。

回滚时恢复公共 Starter 的旧依赖和旧 provider 选择代码即可；公共配置键、业务接口和存储数据格式无需回滚迁移。

## Open Questions

无。RustFS 的独立客户端统一采用 AWS SDK for Java v2 S3，具体版本沿用根 POM 的集中版本属性并在构建验证阶段锁定为可解析版本。
