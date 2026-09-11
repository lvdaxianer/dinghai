# File Storage Adapter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a backend-neutral file storage capability with local-disk and MinIO adapters, presigned upload/download, secure preview endpoints, and HTTP/CDN caching semantics.

**Architecture:** Four Maven modules isolate the pure core contract, local adapter, MinIO adapter, and Spring Boot integration. The starter selects exactly one configured provider, exposes local presigning through protected `/files` endpoints, and leaves authorization to a host-provided policy. File bytes are streamed; only HTTP cache metadata is implemented.

**Tech Stack:** Java 21, Maven, Spring Boot 3.5.x, Spring MVC, MinIO Java SDK, JUnit 4, Mockito, MockMvc, optional Testcontainers.

---

## Scope and File Map

New modules and their responsibilities:

```text
dinghai-file-storage-core/
  src/main/java/io/github/lvdaxianer/dinghai/filestorage/core/
    FileStorage.java, ObjectKey.java, UploadRequest.java
    DownloadObject.java, ObjectMetadata.java, CacheControl.java
    PresignUploadRequest.java, PresignDownloadRequest.java
    PresignedUpload.java, PresignedDownload.java
    FileStorageException.java, FileStorageErrorCode.java
  src/test/java/.../core/...

dinghai-file-storage-local/
  src/main/java/io/github/lvdaxianer/dinghai/filestorage/local/
    LocalFileStorageAdapter.java, LocalFileStorageSigner.java
    LocalObjectPathResolver.java, LocalMetadataStore.java
  src/test/java/.../local/...

dinghai-file-storage-minio/
  src/main/java/io/github/lvdaxianer/dinghai/filestorage/minio/
    MinioFileStorageAdapter.java, MinioStorageProperties.java
    MinioExceptionMapper.java
  src/test/java/.../minio/...

dinghai-file-storage-spring-boot-starter/
  src/main/java/io/github/lvdaxianer/dinghai/filestorage/spring/
    FileStorageProperties.java, FileStorageAutoConfiguration.java
    FileStorageAccessPolicy.java, FileStorageWebController.java
    PresignRequestSigner.java, FileStorageHttpResponseWriter.java
  src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
  src/test/java/.../spring/...
```

Existing files to modify only after module tests are green: root `pom.xml`, `.env.example`, `README.md`, `README_EN.md`, `.agents/skills/dinghai/SKILL.md`, and project-hygiene managed blocks when the detector reports new entries.

Every new Java class and method must follow the repository's Javadoc/author convention, method-size limit, null-safety rule, stable exception rule, and no-secret logging rule. No task may modify `dinghai-shared-state` behavior.

## Task Dispatch Policy

The implementation is L-tier. Use one bounded implementer at a time for the shared parent POM, core contract, and starter; local and MinIO adapters may be delegated only after core is merged and each agent owns disjoint module paths. Every delegated task must return focused test output, broader test output, changed-file list, and known risks. The main agent reviews the merged result and sends fixes to the same agent before marking the task complete.

### Task 1: Maven Modules and Baseline Hygiene

**Agent:** `module-bootstrap-agent`

**Boundary:** Own root `pom.xml`, the four new module POMs, module directory skeletons, `.env.example`, and project-hygiene managed blocks. Out of scope: Java implementation, README prose, and OpenSpec files.

**Dependencies:** None.

**Focused verification:** `mvn -q validate`; `git check-ignore -v target/`; `git check-attr -a pom.xml`; `git ls-files | rg '(^|/)(\.env(\.|$)|.*credentials.*|.*\.(pem|key)$)'`.

**Broader verification:** `mvn -q -DskipTests package`.

- [ ] **Step 1: Write the failing module validation test/check**

  Add the new module declarations and empty module POMs on the task branch, then run `mvn -q validate` from the root. The precondition is that Maven must fail before the module POMs exist; capture the missing-module failure as RED evidence.

- [ ] **Step 2: Add module manifests and dependency boundaries**

  Add the four `<module>` entries to the root POM and create each module POM with these boundaries: core has no Spring/MinIO dependency; local depends only on core; minio depends on core and the pinned MinIO SDK; starter depends on core/local/minio plus Spring Boot autoconfigure/web. Keep test dependencies scoped to `test`.

- [ ] **Step 3: Synchronize baseline files**

  Run the project-hygiene detector after the POMs and planned `${MINIO_ACCESS_KEY}`, `${MINIO_SECRET_KEY}`, `${DINGHAI_FILE_STORAGE_SIGNING_KEY}` references exist. Update only managed blocks, create `.env.example` with placeholders if absent, and do not commit credentials. Verify `.claude/worktrees/` remains ignored.

- [ ] **Step 4: Verify and commit**

  Run the focused and broader commands, inspect `git diff --check`, and commit only the parent POM, module POMs, skeleton metadata, and approved baseline updates. The commit footer must reference `OpenSpec add-file-storage task 1`.

### Task 2: Core Contract and Validation

**Agent:** `file-storage-core-agent`

**Boundary:** Own `dinghai-file-storage-core/src/**` only. Out of scope: filesystem access, MinIO SDK, Spring Boot, HTTP endpoints, and README files.

**Dependencies:** Task 1 merged.

**Focused verification:** `mvn -pl dinghai-file-storage-core -am -Dtest=ObjectKeyTest,FileStorageContractModelTest test`.

**Broader verification:** `mvn -pl dinghai-file-storage-core -am test`.

- [ ] **Step 1: Write RED tests**

  Add tests equivalent to:

  ```java
  @Test public void acceptsChineseNestedKey() {
      assertEquals("用户资料/头像/1.png", ObjectKey.of("用户资料/头像/1.png").value());
  }

  @Test public void rejectsTraversalAfterDecoding() {
      assertThrows(IllegalArgumentException.class, () -> ObjectKey.of("tenant/../secret.txt"));
  }
  ```

  Also test empty/absolute/backslash/control keys, 255-byte segment and 1024-byte full-key boundaries, missing-object Optional semantics, delete idempotency, and `AutoCloseable` download ownership. Run the focused command and confirm failure because the new types do not exist.

- [ ] **Step 2: Implement core value objects**

  Implement immutable records/classes for `ObjectKey`, upload/download requests, metadata, cache control, presign requests/results, and error code. Normalize Unicode before validation, reject unsafe path segments, use `Optional` for absent results, and enforce a default 100 MB upload limit through a reusable validator. Do not return `null`.

- [ ] **Step 3: Implement the public interface and stable exception**

  Define:

  ```java
  ObjectMetadata put(UploadRequest request);
  Optional<DownloadObject> get(ObjectKey key);
  Optional<ObjectMetadata> stat(ObjectKey key);
  boolean exists(ObjectKey key);
  boolean delete(ObjectKey key);
  PresignedUpload presignUpload(PresignUploadRequest request);
  PresignedDownload presignDownload(PresignDownloadRequest request);
  ```

  `FileStorageException` must carry a safe error code and cause without exposing credentials, endpoint URLs, or local absolute paths in its public message.

- [ ] **Step 4: Verify, review, and commit**

  Re-run focused and broader tests, inspect public Javadocs and method lengths, then commit core production code and tests with footer `Refs: OpenSpec add-file-storage task 2`.

### Task 3: Local Disk Adapter

**Agent:** `file-storage-local-agent`

**Boundary:** Own `dinghai-file-storage-local/src/**` only. Out of scope: root POM, core API, MinIO, Spring controllers, and README.

**Dependencies:** Task 2 merged.

**Focused verification:** `mvn -pl dinghai-file-storage-local -am -Dtest=LocalFileStorageAdapterTest,LocalFileStorageSignerTest test`.

**Broader verification:** `mvn -pl dinghai-file-storage-local -am test`.

- [ ] **Step 1: Write RED adapter tests**

  Use `@Rule TemporaryFolder` and assert that writing `1/1/1/1.png` creates nested directories, writing `用户资料/头像.png` round-trips bytes and metadata, overwriting a key changes its ETag, deleting a missing key returns false, and `../secret.txt`, `/tmp/a`, and `a\\b` fail before filesystem access. Run the focused command and capture the missing-adapter failure.

- [ ] **Step 2: Implement root-safe path resolution**

  Resolve `root.toRealPath()` at construction, resolve the validated key, normalize it, and require `candidate.startsWith(root)` before every read/write/delete. Keep all path conversion in `LocalObjectPathResolver`; adapters must not concatenate strings to form paths.

- [ ] **Step 3: Implement streaming atomic writes and metadata**

  Stream into a sibling temporary file while counting bytes and computing SHA-256. Reject over-limit content before replacement, create parent directories, atomically move the temporary content, then atomically replace metadata. On failure, delete only task-owned temporary files. Missing metadata uses `application/octet-stream` and `private, max-age=0`.

- [ ] **Step 4: Implement HMAC presigning**

  Sign a versioned canonical payload containing method, normalized key, expiry, response mode, content type, max length, and cache policy. Use `MessageDigest.isEqual` for verification, reject expired or mismatched signatures, and cap expiry. Keep signing key in constructor input; never log it.

- [ ] **Step 5: Verify, review, and commit**

  Run focused and broader local tests, then commit only local files with footer `Refs: OpenSpec add-file-storage task 3`.

### Task 4: MinIO Adapter

**Agent:** `file-storage-minio-agent`

**Boundary:** Own `dinghai-file-storage-minio/src/**` and its module test resources. Out of scope: local adapter, Spring routing, core changes, and credentials in files.

**Dependencies:** Task 2 merged; Task 1 MinIO dependency available.

**Focused verification:** `mvn -pl dinghai-file-storage-minio -am -Dtest=MinioStoragePropertiesTest,MinioExceptionMapperTest,MinioPresignTest test`.

**Broader verification:** `mvn -pl dinghai-file-storage-minio -am test`.

- [ ] **Step 1: Write RED configuration and mapping tests**

  Test missing endpoint/bucket/access key/secret key/timeout validation, missing object to `Optional.empty()`, authentication failure to a stable error code, and assertion that bucket creation is never invoked. Run the focused command and confirm RED.

- [ ] **Step 2: Implement fixed-bucket adapter**

  Build the MinIO client from validated properties with explicit connect/read/write timeouts. Map object size, content type, ETag, last-modified and cache-control headers into core metadata. Use `statObject`/`getObject`/`putObject`/`removeObject`; do not call `makeBucket`.

- [ ] **Step 3: Write and implement presign policy tests**

  Assert generated GET URLs contain fixed response disposition/cache parameters, and generated POST policy binds key, expiry, content type and content-length range. Use a mock web server or SDK request capture, never a production endpoint.

- [ ] **Step 4: Verify, review, and commit**

  Run focused/broader tests. Run Testcontainers only when `DINGHAI_FILE_STORAGE_MINIO_IT=true` and endpoint variables are present; otherwise record the intentional skip. Commit with footer `Refs: OpenSpec add-file-storage task 4`.

### Task 5: Spring Boot Configuration and Provider Routing

**Agent:** `file-storage-starter-agent`

**Boundary:** Own starter configuration, auto-configuration, policy interfaces, and imports under `dinghai-file-storage-spring-boot-starter/src/**`. Out of scope: adapter internals and documentation.

**Dependencies:** Tasks 2, 3, and 4 merged.

**Focused verification:** `mvn -pl dinghai-file-storage-spring-boot-starter -am -Dtest=FileStorageAutoConfigurationTest,FileStoragePropertiesTest,FileStorageAccessPolicyTest test`.

**Broader verification:** `mvn -pl dinghai-file-storage-spring-boot-starter -am test`.

- [ ] **Step 1: Write RED context tests**

  Add Spring context tests for `provider=local`, `provider=minio`, unknown provider, missing production signing key, only one `@Primary FileStorage`, `web.path-prefix=/files`, and `direct-access=false`. Run focused tests and confirm missing beans/configuration failure.

- [ ] **Step 2: Implement validated properties and auto-configuration**

  Bind `dinghai.file-storage` properties, validate provider-specific required fields and presign max expiry, register exactly one backend, and list the auto-configuration class in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. Do not silently choose a development path or public bucket.

- [ ] **Step 3: Implement access policy extension**

  Define `FileStorageAccessPolicy` with an operation enum for upload/download/preview and a subject/key decision method. Direct access must fail closed when no policy exists; presigned requests must not bypass signature validation.

- [ ] **Step 4: Verify and commit**

  Run focused and broader starter tests, inspect bean conditions and startup error messages, then commit with footer `Refs: OpenSpec add-file-storage task 5`.

### Task 6: Web Endpoints, Preview, and HTTP Cache

**Agent:** `file-storage-web-agent`

**Boundary:** Own starter web controller, signer endpoint integration, response writer, and MockMvc tests. Out of scope: core models, adapter internals, provider selection, and README.

**Dependencies:** Task 5 merged.

**Focused verification:** `mvn -pl dinghai-file-storage-spring-boot-starter -am -Dtest=FileStorageWebControllerTest,FileStorageHttpResponseWriterTest test`.

**Broader verification:** `mvn -pl dinghai-file-storage-spring-boot-starter -am test`.

- [ ] **Step 1: Write RED endpoint tests**

  Add MockMvc tests for `PUT /files/upload/{key}`, `GET /files/download/{key}`, and `GET /files/preview/{key}` covering default unsigned rejection, valid signed upload/download, UTF-8 key encoding, expired signature, upload size failure, `inline`/`attachment`, and stable error bodies. Confirm RED before implementation.

- [ ] **Step 2: Implement signed local endpoints**

  Bind the configured path prefix safely, route only validated decoded keys, verify method/key/expiry/constraints before opening a stream, stream request/response bodies without buffering the whole file, and return stable errors without tokens, absolute paths, or full signed URLs.

- [ ] **Step 3: Write RED cache/range tests**

  Test ETag and Last-Modified headers, default `private, max-age=0`, explicit public/immutable policies, matching `If-None-Match` and `If-Modified-Since` returning 304, valid single-range 206, invalid/multi-range 416, and correct Content-Length/Content-Range.

- [ ] **Step 4: Implement response semantics**

  Set Content-Type, ETag, Last-Modified, Cache-Control, Expires and Content-Disposition; use `inline` for preview and `attachment` for download. Support one byte range only and avoid any process-local byte cache.

- [ ] **Step 5: Apply logging review and verify**

  Keep Controller logs empty; if framework access logging is enabled, ensure trace ID propagation and redaction of token, signature, credentials, complete URL query and request body. Run focused and broader tests, then commit with footer `Refs: OpenSpec add-file-storage task 6`.

### Task 7: Documentation and Final Verification

**Agent:** `file-storage-docs-agent` for documentation only; the main agent owns final audit.

**Boundary:** Own README files, `.agents/skills/dinghai/SKILL.md`, and documentation/configuration tests. Out of scope: production Java implementation and baseline managed blocks.

**Dependencies:** Tasks 1-6 merged.

**Focused verification:** `openspec validate add-file-storage --strict`; Markdown link/section checks; configuration examples contain placeholders only.

**Broader verification:** `mvn -q test`; `mvn -q verify`; `git diff --check`; `git ls-files --eol | rg 'crlf'` must return no tracked text files.

- [ ] **Step 1: Update bilingual module documentation**

  Add the four coordinates, provider configuration, 100 MB default limit, UTF-8 key examples such as `1/1/1/1.png`, presigned upload/download examples, `/files` defaults, direct-access security behavior, preview/cache semantics, MinIO bucket rule, and explicit non-goals to both README languages.

- [ ] **Step 2: Update the Agent Skill**

  Add the new module capabilities and usage triggers to `.agents/skills/dinghai/SKILL.md`; keep paths repository-relative and ensure it matches both README files.

- [ ] **Step 3: Run final audits**

  Validate OpenSpec, inspect every requirement against tests and implementation, run backend reliability checks for validation, idempotency, external timeouts, secret handling and recovery, run logging-boundaries checks, and run code-review-spec against the complete diff. Fix issues and rerun all focused/broader commands.

- [ ] **Step 4: Mark, commit, merge, and archive**

  Mark exactly one OpenSpec task checkbox after its gates pass, create the required Chinese Conventional Commit with non-empty body/footer, merge each task branch into `main`, verify ancestry and clean state, perform final audit, archive `add-file-storage`, and verify `main` contains all task commits.

## Verification Matrix

| Requirement | Focused evidence | Broader evidence |
| --- | --- | --- |
| Core key/security contract | `ObjectKeyTest`, model tests | core module test suite |
| Local nested/Unicode paths and atomic writes | local adapter tests | local module suite |
| MinIO fixed bucket, timeouts, policy | properties/mapping/presign tests | optional integration test |
| Provider selection and fail-fast config | Spring context tests | starter suite |
| Presigned upload/download and direct-access default | MockMvc signed/unsigned tests | starter suite |
| Preview, cache headers, 304, ranges | response writer tests | starter suite + full Maven test |
| Baseline and secret hygiene | project-hygiene checks | `git ls-files`, `git diff --check` |
| Plan/spec consistency and code quality | audit reports | `openspec validate --strict`, full review |
