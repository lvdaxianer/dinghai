## Why

当前阿里云实现使用泛化的 `oss` 作为模块、Java 类型、provider 和配置前缀；该名称会与未来华为云等其他对象存储厂商冲突，也无法让使用者从配置中识别实际云厂商。现在在公开使用前完成一次破坏性命名修正，可以避免继续扩大错误命名的兼容负担。

## What Changes

- 将阿里云实现的 Maven 模块和 artifact 从 `oss` / `dinghai-file-storage-oss` 重命名为 `aliyun-oss` / `dinghai-file-storage-aliyun-oss`。**BREAKING**
- 将阿里云 Java package、配置类型、客户端工厂、适配器、异常映射器和测试类型统一改为 `aliyunoss` 与 `AliyunOss...` 命名。**BREAKING**
- 将 provider 值从 `oss` 改为 `aliyun-oss`，旧值 `oss` 不再接受；同步 Starter 自动装配条件和缓存路由。**BREAKING**
- 将配置前缀从 `dinghai.file-storage.oss.*` 改为 `dinghai.file-storage.aliyun-oss.*`，同步环境变量和中英文文档。**BREAKING**
- 将旧版 `aliyun-sdk-oss` 替换为官方 `com.aliyun:alibabacloud-oss-v2:0.5.1`，并改用其原生预签名 `PUT` 上传和 `GET` 下载。**BREAKING**
- 将 AccessKey 从应用属性移除；运行时仅从 `OSS_ACCESS_KEY_ID` 与 `OSS_ACCESS_KEY_SECRET` 环境变量读取，缺失时启动失败。**BREAKING**
- 保持固定 bucket、对象生命周期、错误映射和远端读取缓存行为不变。
- 不在本次变更中增加华为云实现、STS 临时凭证、旧命名兼容别名或默认构建中的真实云账号测试。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

- `aliyun-oss-storage-provider`: 将阿里云 provider 的模块、类型、配置和公开 provider 标识改为厂商明确的 `aliyun-oss` 命名，行为保持不变。
- `file-storage`: 将 Starter 支持的 provider 集合、自动装配条件和远端缓存场景中的阿里云标识从 `oss` 更新为 `aliyun-oss`，并明确拒绝旧值。

## Impact

- 修改 `dinghai-file-storage` 聚合 POM、阿里云模块目录和 Starter 依赖。
- 修改阿里云适配器源码、测试、Spring Boot 属性绑定、自动装配和 provider 枚举，并替换 OSS SDK。
- 修改 `README.md`、`README_EN.md`、`.env.example` 及主 OpenSpec 规格。
- 现有使用 `oss` artifact、package、provider 或配置的应用必须在升级时完成一次配置和依赖迁移；预签名上传调用方必须从 `POST` 表单改为带必需请求头的 `PUT`；对象数据和 `FileStorage` 契约不变。
