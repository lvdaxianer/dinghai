# 本地文件存储 Unicode 路径 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让本地文件存储在 JDK 21 非 UTF-8 原生文件名编码环境中安全读写 UTF-8 对象 key。

**Architecture:** `ObjectKey` 继续承担 URL UTF-8 解码和逻辑 key 校验。`LocalObjectPathResolver` 将经验证 key 编码为 `file:` URI 原始 UTF-8 字节后再构造 `Path`，并复用现有目录封闭和符号链接逃逸检查。

**Tech Stack:** Java 21、`java.nio.file`、JUnit 4、Spring MVC Test。

---

### Task 1: 本地 UTF-8 路径构造与兼容读取

**Files:**
- Modify: `dinghai-file-storage/local/src/main/java/io/github/lvdaxianer/dinghai/filestorage/local/LocalObjectPathResolver.java`
- Test: `dinghai-file-storage/local/src/test/java/io/github/lvdaxianer/dinghai/filestorage/local/LocalFileStorageAdapterTest.java`
- Test: `dinghai-file-storage/spring-boot-starter/src/test/java/io/github/lvdaxianer/dinghai/filestorage/spring/FileStorageWebControllerTest.java`

- [ ] **Step 1: 写入失败测试**

新增 UTF-8 原始 URI 断言、中文文件与目录读取、百分号编码 key 读取和旧
`/rsm/origin/**` URL 读取断言。

- [ ] **Step 2: 执行定向测试并确认失败**

Run: `mvn -pl dinghai-file-storage/local -am -Dtest=LocalFileStorageAdapterTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: 新增 URI 断言在直接字符串 `Path.resolve` 实现下失败。

- [ ] **Step 3: 实现 UTF-8 URI 路径转换**

添加一个仅接受已验证 `ObjectKey` 的私有转换方法；保留原有的根目录、父目录
真实路径与符号链接校验，不添加 JDK 内部 API 或修改全局编码属性。

- [ ] **Step 4: 执行定向与 Web 回归测试**

Run: `mvn -pl dinghai-file-storage/spring-boot-starter -am -Dtest=LocalFileStorageAdapterTest,FileStorageWebControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: 全部通过，旧 `/rsm/origin/**` 路由无变更。

- [ ] **Step 5: 审查、构建与提交**

Run: `mvn -pl dinghai-file-storage/spring-boot-starter -am package`

Expected: local 与 Spring Boot starter JAR 均产出且测试通过。
