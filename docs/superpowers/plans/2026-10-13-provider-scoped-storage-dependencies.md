# Provider-Scoped Storage Dependencies Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将文件存储依赖从全量 Starter 拆分为公共 Starter 和 provider 专用 Starter，使未选 provider 的 SDK 不进入应用运行时依赖树，同时让 RustFS 完全脱离 MinIO SDK。

**Architecture:** 公共 Starter 保留配置、Local、Web、缓存和迁移能力，并通过 `FileStorageProviderFactory` SPI 选择 provider。四个 provider Starter 各自注册条件 factory；RustFS 改用 AWS SDK for Java v2 S3 client/presigner，公共自动配置不再 import 远端 SDK 类型。

**Tech Stack:** Java 21、Maven reactor、Spring Boot 3.5.5 auto-configuration、AWS SDK for Java v2 S3、JUnit 4、Mockito。

---

### Task 1: 公共依赖边界与 provider factory

**Files:**
- Modify: `pom.xml`
- Modify: `dinghai-file-storage/pom.xml`
- Modify: `dinghai-file-storage/spring-boot-starter/pom.xml`
- Create: `dinghai-file-storage/spring-boot-starter/src/main/java/io/github/lvdaxianer/dinghai/filestorage/spring/FileStorageProviderFactory.java`
- Modify: `dinghai-file-storage/spring-boot-starter/src/main/java/io/github/lvdaxianer/dinghai/filestorage/spring/FileStorageAutoConfiguration.java`
- Modify: `dinghai-file-storage/spring-boot-starter/src/test/...` provider selection tests

- [ ] **Step 1: Write the failing dependency and context tests**

  Add tests that build the base starter context with `provider: local`, assert one `FileStorage` bean, and assert the base POM has no dependencies on `dinghai-file-storage-minio`, `dinghai-file-storage-rustfs`, `dinghai-file-storage-aliyun-oss`, or `dinghai-file-storage-huaweicloud-obs`. Add a test for remote provider selection without its factory that expects the stable missing-provider error.

- [ ] **Step 2: Run focused tests to verify the new contract fails**

  Run:

  ```bash
  mvn -pl dinghai-file-storage/spring-boot-starter -am -Dtest='*FileStorage*Test' test
  ```

  Expected: the new dependency/context assertions fail because the public auto-configuration still imports all provider implementations and the base POM still declares all provider modules.

- [ ] **Step 3: Add the factory SPI and refactor the public auto-configuration**

  Define `FileStorageProviderFactory` with `FileStorageProvider provider()` and `FileStorage create(FileStorageProperties properties, String bucket)`. Replace direct MinIO/RustFS/OSS/OBS imports and `Optional` SDK parameters in `FileStorageAutoConfiguration` with `ObjectProvider<FileStorageProviderFactory>`. Keep Local creation in the base module. For a remote provider, select the matching factory; if none exists, throw `FileStorageException` with `INVALID_CONFIGURATION` and a message containing only the provider name and required starter artifact.

- [ ] **Step 4: Remove remote dependencies from the base starter**

  Keep only core, local, Spring Boot, Web, thread-pool, and test dependencies in the public Starter. Add four provider Starter modules to the aggregator and add the independent RustFS SDK version to root dependency management.

- [ ] **Step 5: Run focused tests and inspect the dependency tree**

  Run:

  ```bash
  mvn -pl dinghai-file-storage/spring-boot-starter -am test
  mvn -pl dinghai-file-storage/spring-boot-starter dependency:tree -Dscope=runtime
  ```

  Expected: tests pass; the public Starter runtime tree contains no MinIO, RustFS, Alibaba OSS, or Huawei OBS SDK.

- [ ] **Step 6: Review and commit Task 1**

  Review `git diff` against `code-review-spec`, run the focused tests again, then commit the plan assets and public dependency/SPI changes with a Chinese full-style commit whose footer references `OpenSpec provider-scoped-storage-dependencies task 1`.

### Task 2: Provider-specific Spring Boot Starters

**Files:**
- Create: `dinghai-file-storage/{minio,rustfs,aliyun-oss,huaweicloud-obs}-spring-boot-starter/pom.xml`
- Create: provider-specific `*StorageAutoConfiguration.java` and `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Modify: provider adapter/configuration classes only where factory construction requires it
- Create: provider-specific Spring context tests

- [ ] **Step 1: Write failing provider context tests**

  For each provider, create a context test that supplies the matching provider properties, imports the dedicated Starter, and asserts exactly one primary `FileStorage`. Add a negative test that starts the base Starter with a remote provider but no provider Starter and asserts the stable missing-provider error.

- [ ] **Step 2: Run the tests to observe failure**

  Run each provider module test with `mvn -pl <provider-starter> -am test`. Expected: the new provider Starter classes and factory beans do not exist.

- [ ] **Step 3: Implement provider factories and conditional auto-configurations**

  Add `@AutoConfiguration`, `@ConditionalOnClass`, and matching `@ConditionalOnProperty` to each provider configuration. Register only the selected provider factory. Move OSS/OBS SDK client lifecycle into their own Starter configurations. Keep all credential and timeout validation at the provider boundary.

- [ ] **Step 4: Run provider context tests**

  Run the four provider module test suites and the full file-storage reactor tests. Expected: each selected provider creates one primary bean, unselected provider clients are absent, and existing Web/cache/migration tests remain green.

- [ ] **Step 5: Verify provider dependency closures**

  Run `dependency:tree` for each provider Starter and assert that only its provider SDK closure is present. Save command output as gate evidence; do not add generated dependency files to Git.

- [ ] **Step 6: Review and commit Task 2**

  Run the focused and broader tests, review the diff, mark OpenSpec task 2 complete, and commit with a footer referencing task 2.

### Task 3: RustFS independent S3 implementation

**Files:**
- Modify: `dinghai-file-storage/rustfs/pom.xml`
- Replace: `dinghai-file-storage/rustfs/src/main/java/.../RustfsClientFactory.java`
- Replace: `dinghai-file-storage/rustfs/src/main/java/.../RustfsFileStorageAdapter.java`
- Modify: `dinghai-file-storage/rustfs/src/main/java/.../RustfsExceptionMapper.java`
- Modify/Create: RustFS unit and contract tests

- [ ] **Step 1: Write failing RustFS independence and behavior tests**

  Assert the RustFS POM has no MinIO dependency, the client factory builds an endpoint-overridden S3 client, object operations map metadata correctly, PUT/GET presigns have the expected method and expiry, and multipart requests preserve part number, upload ID, ETag, and completion order.

- [ ] **Step 2: Run the RustFS tests before implementation**

  Run:

  ```bash
  mvn -pl dinghai-file-storage/rustfs -am test
  ```

  Expected: the new tests fail because the current implementation uses `MinioClient` and `MinioFileStorageAdapter`.

- [ ] **Step 3: Implement the independent AWS S3 client boundary**

  Configure `S3Client` and `S3Presigner` with RustFS endpoint override, path-style access, static credentials, fixed S3 region, connection timeout, read/socket timeout, and API call timeout. Keep the existing `RustfsStorageConfiguration` validation and do not create buckets.

- [ ] **Step 4: Implement object and multipart operations**

  Map `putObject`, `getObject`, `headObject`, `listObjectsV2`, and `deleteObject` to the common contract. Map `createMultipartUpload`, `uploadPart`, `listParts`, `completeMultipartUpload`, and `abortMultipartUpload` to `MultipartFileStorage`. Use the S3 presigner for regular PUT/GET and multipart part PUT URLs.

- [ ] **Step 5: Implement safe RustFS error mapping**

  Map missing objects/uploads, authentication failures, invalid requests, presign failures, service failures, and unknown SDK failures to the existing error enum. Public messages must not contain endpoint, credentials, query signatures, paths, or raw SDK details.

- [ ] **Step 6: Run RustFS tests and dependency verification**

  Run module tests, package the module, and run:

  ```bash
  mvn -pl dinghai-file-storage/rustfs -am dependency:tree -Dincludes=io.minio:minio -Dscope=runtime
  ```

  Expected: RustFS tests pass and the filtered dependency tree has no `io.minio:minio` entry.

- [ ] **Step 7: Review and commit Task 3**

  Review SDK timeout, resource-closing, error mapping, and presign behavior; mark OpenSpec task 3 complete and commit with a task 3 footer.

### Task 4: Documentation and final verification

**Files:**
- Modify: `dinghai-file-storage/README.md`
- Modify: `dinghai-file-storage/README_EN.md`
- Modify: root README files if dependency examples are present
- Modify: `openspec/changes/provider-scoped-storage-dependencies/tasks.md`

- [ ] **Step 1: Update dependency and migration documentation**

  Document the public Starter plus one provider Starter model, provide Local/MinIO/RustFS/OSS/OBS Maven snippets, state that `provider` remains the runtime selector, and explain that missing provider modules fail fast.

- [ ] **Step 2: Run OpenSpec validation**

  Run:

  ```bash
  openspec validate provider-scoped-storage-dependencies --strict
  ```

  Expected: validation succeeds with no missing requirements or malformed scenarios.

- [ ] **Step 3: Run full verification**

  Run `mvn clean verify` in the task worktree, plus each minimal provider dependency tree command. Confirm no `io.minio:minio` appears in the RustFS-only tree and no provider SDK appears in the base-only tree.

- [ ] **Step 4: Perform final audit and integrate**

  Re-read all OpenSpec checkboxes, inspect `git diff`, run the final tests after the last edit, complete the code-review report, merge each verified task branch into `main`, archive the completed OpenSpec change, and verify the final integration checkout is clean.

