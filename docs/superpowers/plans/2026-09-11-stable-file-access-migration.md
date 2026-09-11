# Stable File Access Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Provide encrypted stable file URLs for private providers and server-directory migration packages that preserve those URLs across Local-to-MinIO moves.

**Architecture:** Core owns `FileReference`, deterministic AES-SIV encoding, object iteration, and migration model types. Local and MinIO implement lazy iteration; the Starter owns provider-neutral read proxying, non-local-provider disk caching, configuration, and server-side package import/export orchestration. Stable IDs encode only `storageAlias + objectKey`, so a deployment changes the provider mapping without changing published URLs.

**Tech Stack:** Java 21, Spring Boot 3.5, Google Tink AES-SIV, MinIO SDK, JUnit 4, MockMvc, Docker MinIO.

---

### Task 1: Stable Reference and Iteration Contract

**Files:**
- Modify: `pom.xml`
- Modify: `dinghai-file-storage-core/pom.xml`
- Create: `dinghai-file-storage-core/src/main/java/.../FileReference.java`
- Create: `dinghai-file-storage-core/src/main/java/.../StableFileIdCodec.java`
- Modify: `dinghai-file-storage-core/src/main/java/.../FileStorage.java`
- Modify: `dinghai-file-storage-local/src/main/java/.../LocalFileStorageAdapter.java`
- Modify: `dinghai-file-storage-minio/src/main/java/.../MinioFileStorageAdapter.java`
- Test: `dinghai-file-storage-core/src/test/java/.../StableFileIdCodecTest.java`
- Test: `dinghai-file-storage-local/src/test/java/.../LocalFileStorageAdapterTest.java`
- Test: `dinghai-file-storage-minio/src/test/java/.../MinioFileStorageAdapterTest.java`

- [ ] Write failing tests proving equal alias/key yields the same opaque ID, altered IDs fail, and a new key rejects old IDs.
- [ ] Implement a Google Tink AES-SIV codec with strict URL-safe versioned encoding and no plaintext key leakage.
- [ ] Write failing tests proving `FileStorage#list()` is closable and yields metadata without loading all entries.
- [ ] Add the default list contract and Local/MinIO lazy implementations; run Core/Local/MinIO tests.
- [ ] Commit the validated contract task with its OpenSpec assets.

### Task 2: Provider-Neutral Stable Read Endpoint

**Files:**
- Modify: `dinghai-file-storage-spring-boot-starter/src/main/java/.../FileStorageWebController.java`
- Modify: `dinghai-file-storage-spring-boot-starter/src/main/java/.../FileStorageAutoConfiguration.java`
- Modify: `dinghai-file-storage-spring-boot-starter/src/main/java/.../FileStorageProperties.java`
- Create: `dinghai-file-storage-spring-boot-starter/src/main/java/.../StableFileAccessService.java`
- Create: `dinghai-file-storage-spring-boot-starter/src/main/java/.../ProviderFileReadCache.java`
- Test: `dinghai-file-storage-spring-boot-starter/src/test/java/.../FileStorageWebControllerTest.java`
- Test: `dinghai-file-storage-spring-boot-starter/src/test/java/.../FileStorageAutoConfigurationTest.java`

- [ ] Write failing MockMvc tests for `/files/read/v1/{id}`, preview, download, Range, 304, and malformed ID rejection without a principal.
- [ ] Refactor shared streaming response code to use `FileStorage`, retaining legacy signed/direct endpoint authorization unchanged.
- [ ] Add stable read service and routes; run Starter tests and verify MinIO-backed read with a real private object.
- [ ] Add fail-fast config tests for storage alias and stable ID key.
- [ ] Commit the validated endpoint task.

### Task 3: Non-Local Provider Disk Read Cache

**Files:**
- Create: `dinghai-file-storage-spring-boot-starter/src/main/java/.../ProviderFileReadCache.java`
- Create: `dinghai-file-storage-spring-boot-starter/src/main/java/.../ProviderFileReadCacheCleaner.java`
- Modify: `dinghai-file-storage-spring-boot-starter/src/main/java/.../FileStorageWebController.java`
- Modify: `dinghai-file-storage-spring-boot-starter/src/main/java/.../FileStorageAutoConfiguration.java`
- Modify: `dinghai-file-storage-spring-boot-starter/src/main/java/.../FileStorageProperties.java`
- Test: `dinghai-file-storage-spring-boot-starter/src/test/java/.../ProviderFileReadCacheTest.java`

- [ ] Write failing tests that local caching is disabled by default and every non-Local provider, including MinIO, is enabled by default when a cache root is configured.
- [ ] Implement cache sidecars and metadata comparison; prove a matching MinIO `stat` returns the disk copy and a changed/deleted object removes it.
- [ ] Write failing tests for first-read stream pass-through, a separate asynchronous provider stream filling the cache, threshold skip, queue saturation, and no shared response stream.
- [ ] Implement an independent bounded cache executor with per-key single-flight and atomic temporary-file publication.
- [ ] Write failing clock-controlled cleanup tests for midnight expiration and retained active files; implement scheduled cleanup without blocking read requests.
- [ ] Run focused Starter tests and real Docker MinIO read/cache invalidation checks, then commit the task.

### Task 4: Server Directory Migration Package

**Files:**
- Create: `dinghai-file-storage-spring-boot-starter/src/main/java/.../FileStorageMigrationService.java`
- Create: `dinghai-file-storage-spring-boot-starter/src/main/java/.../MigrationManifestEntry.java`
- Create: `dinghai-file-storage-spring-boot-starter/src/main/java/.../FileStorageMigrationController.java`
- Modify: `dinghai-file-storage-spring-boot-starter/src/main/java/.../FileStorageProperties.java`
- Test: `dinghai-file-storage-spring-boot-starter/src/test/java/.../FileStorageMigrationServiceTest.java`

- [ ] Write failing tests for strict package names, manifest SHA-256 generation, and Local export directory layout.
- [ ] Implement temporary-directory export and atomic package publication using NDJSON manifest and streaming copy/hash.
- [ ] Write failing tests for Local package import, identical-object skip, collision failure, `errors.ndjson`, and completed/failed relocation.
- [ ] Implement import validation and stream upload; run migration service tests.
- [ ] Run an end-to-end Local-export-to-Docker-MinIO-import test and assert same stable ID resolves after provider switch.
- [ ] Commit the validated migration task.

### Task 5: Configuration Documentation and Completion

**Files:**
- Modify: `.env.example`
- Modify: `README.md`
- Modify: `README_EN.md`
- Modify: `openspec/specs/file-storage/spec.md`
- Modify: `openspec/specs/file-storage-web-access/spec.md`

- [ ] Update managed environment template with placeholder stable ID key, migration roots, and non-local cache root; run project-hygiene checks and secret-leak scan.
- [ ] Document logical alias consistency, key rotation invalidation, read URLs, migration directory workflow, completed/failed behavior, and no-HTTP large-file transfer.
- [ ] Synchronize specs, run `openspec validate add-stable-file-access-migration --strict`, `mvn test`, review the full diff, then archive and commit.
