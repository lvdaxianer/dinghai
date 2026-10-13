# Dinghai 本地删除空目录回收 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复本地对象删除后的空目录残留，并提供 root 受限、幂等、可配置的本地前缀删除能力。

**Architecture:** 保持公共 `FileStorage` 和远端适配器不变。在 `LocalObjectPathResolver` 中集中完成前缀的规范化和 root/符号链接校验，在 `LocalFileStorageAdapter` 中完成对象删除、前缀扫描、元数据删除和深度优先空目录回收；Starter 只负责绑定开关并传入 Local 构造器。

**Tech Stack:** Java 21、NIO `Path`/`Files`、Spring Boot 3 properties、JUnit 4、Maven reactor、OpenSpec。

---

### Task 1: 本地删除行为的 RED 测试

**Files:**
- Modify: `dinghai-file-storage/local/src/test/java/io/github/lvdaxianer/dinghai/filestorage/local/LocalFileStorageAdapterTest.java`
- Modify: `dinghai-file-storage/spring-boot-starter/src/test/java/io/github/lvdaxianer/dinghai/filestorage/spring/FileStorageAutoConfigurationTest.java`
- Test: `dinghai-file-storage/local/src/test/java/io/github/lvdaxianer/dinghai/filestorage/local/LocalFileStorageAdapterTest.java`

- [ ] **Step 1: 写新增失败测试**

  覆盖单文件空目录、多级前缀、非空目录、重复删除、中文/URL 编码、路径穿越、
  符号链接、并发删除、开关关闭以及 prefix 不存在；使用真实临时目录和真实文件，
  不 mock `Files`。

- [ ] **Step 2: 运行 RED 测试**

  Run: `mvn -pl dinghai-file-storage/local -am -Dtest=LocalFileStorageAdapterTest test`

  Expected: 新增测试因 `deletePrefix` 或目录回收行为尚不存在而失败，既有测试保持可定位。

- [ ] **Step 3: 提交测试前状态检查**

  Run: `git diff --check && git status --short`

  Expected: 只包含本地适配器测试和必要 starter 配置测试，不包含生产实现改动。

### Task 2: 路径安全与空目录回收实现

**Files:**
- Modify: `dinghai-file-storage/local/src/main/java/io/github/lvdaxianer/dinghai/filestorage/local/LocalObjectPathResolver.java`
- Modify: `dinghai-file-storage/local/src/main/java/io/github/lvdaxianer/dinghai/filestorage/local/LocalFileStorageAdapter.java`

- [ ] **Step 1: 增加前缀解析**

  将 URL 百分号编码 prefix 规范化为 `ObjectKey`，拒绝空 prefix、绝对路径、`..`、
  反斜杠和控制字符；解析后检查 `normalize().startsWith(root)`、禁止 root 本身，
  并检查已有父路径不越过 root、不跟随符号链接。

- [ ] **Step 2: 改造单对象删除**

  保持 `true`/`false` 返回语义；文件缺失和并发消失返回 false 并幂等清理元数据，
  成功删除后按开关从父目录向上清理空目录。目录失败仅 WARN，不回滚已完成文件删除。

- [ ] **Step 3: 实现本地专用前缀删除**

  使用不跟随符号链接的 `Files.walk` 查找匹配普通文件，删除文件和 sidecar；对象
  失败计数并继续，其余完成后按目录深度倒序删除空目录。目录不存在视为成功，root
  本身永不删除，目录失败计数并 WARN；对象失败完成后抛稳定存储异常。

- [ ] **Step 4: 增加汇总日志**

  使用一次 INFO 汇总输出 `prefix`、`deletedFiles`、`deletedDirectories`、
  `skippedDirectories`、`failedDeletions`、`durationMs`；失败 WARN 只输出限长安全标识、
  异常类型和安全原因，不输出密钥、完整配置、文件内容或无界用户数据。

- [ ] **Step 5: 运行 GREEN 测试**

  Run: `mvn -pl dinghai-file-storage/local -am -Dtest=LocalFileStorageAdapterTest test`

  Expected: 本地新增与既有测试全部通过。

### Task 3: Starter 配置与远端回归

**Files:**
- Modify: `dinghai-file-storage/spring-boot-starter/src/main/java/io/github/lvdaxianer/dinghai/filestorage/spring/FileStorageProperties.java`
- Modify: `dinghai-file-storage/spring-boot-starter/src/main/java/io/github/lvdaxianer/dinghai/filestorage/spring/FileStorageAutoConfiguration.java`
- Modify: `dinghai-file-storage/README.md`
- Modify: `openspec/changes/local-delete-empty-directories/tasks.md`

- [ ] **Step 1: 绑定开关并保持构造兼容**

  新增 `local.cleanup-empty-directories`，默认 true；Starter 仅在创建 Local 适配器时
  传值，MinIO、RustFS、OSS、OBS 分支不读取该属性。

- [ ] **Step 2: 补充配置测试和文档**

  验证默认 true、显式 false，以及远端 provider 的适配器创建行为无变化；README 说明
  配置含义和发布后业务侧无需修改代码。

- [ ] **Step 3: 运行完整文件存储测试**

  Run: `mvn -pl dinghai-file-storage/local,dinghai-file-storage/spring-boot-starter -am test`

  Expected: 本地、core、MinIO、RustFS、OSS、OBS、Starter 测试全部通过，远端测试无新增失败。

### Task 4: 审查、验证、提交与集成

**Files:**
- Review all changed files and OpenSpec artifacts.

- [ ] **Step 1: 运行 OpenSpec 和静态检查**

  Run: `openspec validate local-delete-empty-directories --strict && git diff --check`

  Expected: 规格验证通过且无空白错误。

- [ ] **Step 2: 完成需求逐项审计**

  对照 proposal、design、spec、tasks 和用户需求检查 root 边界、NOFOLLOW、并发幂等、
  日志字段、非本地不变、starter 发布说明，确认没有遗漏。

- [ ] **Step 3: 使用中文 Conventional Commit 提交**

  使用 `commit --style=full` 生成包含 body 和 `Refs: OpenSpec local-delete-empty-directories task ...`
  footer 的修复提交。

- [ ] **Step 4: 合并回 main 并复核**

  在干净的 `main` worktree 执行 `git merge --no-ff`，再运行最终文件存储测试和状态检查，
  确认任务分支已集成并清理 worktree。
