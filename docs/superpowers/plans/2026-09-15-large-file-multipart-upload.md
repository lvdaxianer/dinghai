# Large File Multipart Upload Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 Local、MinIO、RustFS 和阿里云 OSS 提供统一、可恢复、可直传的大文件分片上传能力。

**Architecture:** 在 `core` 增加可选的 `MultipartFileStorage` 能力接口和统一值模型；远端 provider 将会话和分片状态委托给对象存储原生 Multipart API，Local 使用临时分片目录和原子合并。Starter 提供 RESTful 会话管理和 Local 分片 PUT，远端分片数据通过短期预签名 URL直传。

**Tech Stack:** Java 21、Spring Boot 3.5、MinIO Java SDK 8.5、阿里云 OSS V2 SDK 0.5、JUnit 4、Spring MockMvc。

---

### Task 1: Core multipart contract

**Files:**
- Create: `dinghai-file-storage/core/src/main/java/io/github/lvdaxianer/dinghai/filestorage/core/MultipartFileStorage.java`
- Create: `dinghai-file-storage/core/src/main/java/io/github/lvdaxianer/dinghai/filestorage/core/MultipartUploadRequest.java`
- Create: `dinghai-file-storage/core/src/main/java/io/github/lvdaxianer/dinghai/filestorage/core/MultipartUploadSession.java`
- Create: `dinghai-file-storage/core/src/main/java/io/github/lvdaxianer/dinghai/filestorage/core/MultipartPart.java`
- Create: `dinghai-file-storage/core/src/main/java/io/github/lvdaxianer/dinghai/filestorage/core/MultipartPartRequest.java`
- Create: `dinghai-file-storage/core/src/main/java/io/github/lvdaxianer/dinghai/filestorage/core/MultipartCompletion.java`
- Create: `dinghai-file-storage/core/src/main/java/io/github/lvdaxianer/dinghai/filestorage/core/MultipartUploadResult.java`
- Modify: `dinghai-file-storage/core/src/main/java/io/github/lvdaxianer/dinghai/filestorage/core/FileStorageErrorCode.java`
- Test: `dinghai-file-storage/core/src/test/java/io/github/lvdaxianer/dinghai/filestorage/core/MultipartContractTest.java`

- [ ] **Step 1: Write the failing test** covering total-size/part-size validation, part number bounds, immutable part lists, and required lifecycle methods.
- [ ] **Step 2: Run `mvn -pl dinghai-file-storage/core -Dtest=MultipartContractTest test` and verify it fails because the types do not exist.**
- [ ] **Step 3: Add the minimal records, interface, and `MULTIPART_*` stable errors.**
- [ ] **Step 4: Re-run the focused test and the existing core tests.**
- [ ] **Step 5: Commit with `Refs: OpenSpec add-large-file-multipart-upload task 1.1`.**

### Task 2: Provider multipart adapters

**Files:**
- Modify: `dinghai-file-storage/local/src/main/java/io/github/lvdaxianer/dinghai/filestorage/local/LocalFileStorageAdapter.java`
- Modify: `dinghai-file-storage/minio/src/main/java/io/github/lvdaxianer/dinghai/filestorage/minio/MinioFileStorageAdapter.java`
- Modify: `dinghai-file-storage/rustfs/src/main/java/io/github/lvdaxianer/dinghai/filestorage/rustfs/RustfsFileStorageAdapter.java`
- Modify: `dinghai-file-storage/aliyun-oss/src/main/java/io/github/lvdaxianer/dinghai/filestorage/aliyunoss/AliyunOssFileStorageAdapter.java`
- Test: provider-specific multipart lifecycle tests under each provider `src/test` tree.

- [ ] **Step 1: Add RED tests for Local atomic part write/complete and MinIO/OSS request construction.**
- [ ] **Step 2: Run provider-focused tests and verify missing lifecycle methods fail.**
- [ ] **Step 3: Implement Local temporary sessions with bounded writes and atomic merge.**
- [ ] **Step 4: Implement MinIO/RustFS S3 multipart lifecycle and part presigning.**
- [ ] **Step 5: Implement OSS multipart lifecycle and signed part requests using the installed SDK API.**
- [ ] **Step 6: Run focused provider tests and commit with OpenSpec task references.**

### Task 3: Starter HTTP and configuration

**Files:**
- Modify: `dinghai-file-storage/spring-boot-starter/src/main/java/io/github/lvdaxianer/dinghai/filestorage/spring/FileStorageProperties.java`
- Modify: `dinghai-file-storage/spring-boot-starter/src/main/java/io/github/lvdaxianer/dinghai/filestorage/spring/FileStorageAutoConfiguration.java`
- Create: `dinghai-file-storage/spring-boot-starter/src/main/java/io/github/lvdaxianer/dinghai/filestorage/spring/MultipartUploadController.java`
- Create: `dinghai-file-storage/spring-boot-starter/src/main/java/io/github/lvdaxianer/dinghai/filestorage/spring/MultipartUploadService.java`
- Test: `FileStorageMultipartControllerTest.java`

- [ ] **Step 1: Add MockMvc RED tests for REST paths, authorization, 409 missing parts, 429 body rejection, and provider isolation.**
- [ ] **Step 2: Implement multipart properties, service validation, authorization and stable response mapping.**
- [ ] **Step 3: Add Local part PUT and scheduled expiry cleanup with bounded executors.**
- [ ] **Step 4: Run starter focused tests and commit with OpenSpec task references.**

### Task 4: Verification and documentation

**Files:**
- Modify: `dinghai-file-storage/README.md`
- Modify: `dinghai-file-storage/README_EN.md`
- Modify: `.env.example`
- Modify: OpenSpec `tasks.md`

- [ ] **Step 1: Add retry, duplicate completion, concurrent completion, recovery and opt-in integration coverage.**
- [ ] **Step 2: Update configuration and client flow documentation without exposing credentials or signed URLs.**
- [ ] **Step 3: Run `openspec validate add-large-file-multipart-upload --strict`.**
- [ ] **Step 4: Run focused module tests and the full reactor test suite.**
- [ ] **Step 5: Perform final consistency/code-quality review, mark task checkboxes, archive the change, and merge the task branches into `main`.**
