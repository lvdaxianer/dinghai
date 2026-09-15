# dinghai-file-storage

English | [中文](README.md)

A unified file storage adapter layer: business code depends only on the `FileStorage` contract and can switch between local disk, MinIO, RustFS, and Alibaba Cloud OSS backends. Local supports object keys such as `1/1/1/1.png` and CJK directory names, and avoids loading whole files into memory through temporary files and streaming I/O.

Requirements: Java 21+, Spring Boot 3.x.

## Module Layout

| Module | Description |
| --- | --- |
| `dinghai-file-storage-core` | Backend-neutral `FileStorage` contract, object model, presign model, and stable error codes |
| `dinghai-file-storage-local` | Local disk adapter with support for CJK and multi-level object keys, HMAC presigning, and streaming I/O |
| `dinghai-file-storage-minio` | Fixed-bucket MinIO adapter with native presign capability |
| `dinghai-file-storage-rustfs` | Fixed-bucket RustFS S3-compatible adapter with native presign capability |
| `dinghai-file-storage-aliyun-oss` | Fixed-bucket Alibaba Cloud OSS V2 SDK adapter with native presign capability |
| `dinghai-file-storage-spring-boot-starter` | Auto-wires file storage by provider and provides local web presign endpoints |

The `benchmark` module is for development verification only and is not published (see "Benchmark" at the end).

## Quick Start

Add the Starter:

```xml
<dependency>
    <groupId>io.github.lvdaxianer</groupId>
    <artifactId>dinghai-file-storage-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

Minimal configuration (Local backend, sensitive values injected via environment variables):

```yaml
dinghai:
  file-storage:
    provider: local
    bucket: documents
    storage-alias: documents
    local:
      root: /var/lib/app/files
    security:
      signing-key: ${DINGHAI_FILE_STORAGE_SIGNING_KEY}
      stable-id-key: ${DINGHAI_FILE_STORAGE_STABLE_ID_KEY}
```

Once the dependency is added and auto-wiring completes, you can inject `FileStorage` directly. `web.enabled` defaults to on, automatically registering the `/files/**` endpoint family. All configuration keys are validated at startup; any illegal value fails the startup immediately with `INVALID_CONFIGURATION` — there is no silent fallback.

## Configuration

The configuration prefix is `dinghai.file-storage`. All validation is implemented in code (executed at startup) and does not rely on Bean Validation annotations.

### Top-Level Keys

| Key | Type | Default | Description |
| --- | --- | --- | --- |
| `provider` | `local` / `minio` / `rustfs` / `aliyun-oss` | none (required) | Storage backend, one of four choices. Missing, illegal, or legacy `oss` values fail startup directly; **no automatic detection, fallback, or compatibility alias** |
| `bucket` | `String` | none (required) | Logical bucket name shared by all backends. Must not contain `.`, `..`, `/`, `\`, or control characters; under Local it maps to a single-level directory `root/<bucket>/` |
| `storage-alias` | `String` | none (required) | Logical storage alias that does not change with the physical backend; it participates in stable file ID computation and cache file name digests. Keep the alias unchanged when migrating from Local to MinIO, RustFS, or Alibaba Cloud OSS so that already-issued stable IDs remain valid |

### local (Local Backend)

| Key | Type | Default | Description |
| --- | --- | --- | --- |
| `local.root` | `Path` | none (required when provider=local) | Root directory for local objects; missing value fails startup |

### minio (MinIO Backend)

| Key | Type | Default | Description |
| --- | --- | --- | --- |
| `minio.endpoint` | `String` | none (required when provider=minio) | MinIO server endpoint |
| `minio.access-key` | `String` | none (required) | Access key |
| `minio.secret-key` | `String` | none (required) | Secret key |
| `minio.connect-timeout` | `Duration` | `10s` | Connect timeout (OkHttp client); must be positive |
| `minio.read-timeout` | `Duration` | `10s` | Read timeout; must be positive |
| `minio.write-timeout` | `Duration` | `10s` | Write timeout; must be positive |

The MinIO adapter **does not create or modify buckets automatically**; create them yourself beforehand.

### rustfs (RustFS Backend)

| Key | Type | Default | Description |
| --- | --- | --- | --- |
| `rustfs.endpoint` | `String` | none (required when provider=rustfs) | RustFS S3 API endpoint, for example `http://localhost:9100` |
| `rustfs.access-key` | `String` | none (required) | Access key, injected by the deployment environment |
| `rustfs.secret-key` | `String` | none (required) | Secret access key, injected by the deployment environment |
| `rustfs.connect-timeout` | `Duration` | `10s` | Connect timeout (OkHttp client); must be positive |
| `rustfs.read-timeout` | `Duration` | `10s` | Read timeout; must be positive |
| `rustfs.write-timeout` | `Duration` | `10s` | Write timeout; must be positive |

RustFS uses its S3-compatible API. Its bucket should remain private and must be pre-created by operations. The adapter **does not create buckets or change bucket policies**; upload and download presigns are issued natively by RustFS.

### aliyun-oss (Alibaba Cloud OSS Backend)

| Key | Type | Default | Description |
| --- | --- | --- | --- |
| `aliyun-oss.region` | `String` | none (required when provider=aliyun-oss) | OSS region ID, for example `oss-cn-beijing` |
| `aliyun-oss.connect-timeout` | `Duration` | `10s` | SDK connection timeout; must be positive |
| `aliyun-oss.read-timeout` | `Duration` | `10s` | SDK read/write timeout; must be positive |
| `aliyun-oss.write-timeout` | `Duration` | `10s` | SDK write timeout; must be positive |

Alibaba Cloud OSS uses `com.aliyun:alibabacloud-oss-v2:0.5.1`. Its bucket should remain private and must be pre-created by operations. The adapter **does not create buckets or change ACL, CORS, or bucket policy**; uploads use native `PUT` presigning and downloads use native `GET` presigning. AccessKey values are read only from `OSS_ACCESS_KEY_ID` and `OSS_ACCESS_KEY_SECRET`; **STS temporary credentials are not supported**.

### security (Security)

| Key | Type | Default | Description |
| --- | --- | --- | --- |
| `security.signing-key` | `String` | none (required only when provider=local) | HMAC-SHA-256 key for local presigned URLs, used directly as UTF-8 bytes; missing value fails startup. Not used by the MinIO, RustFS, or Alibaba Cloud OSS backends, which use native presigning |
| `security.stable-id-key` | `String` | none (**required for all backends**) | Key material for stable file IDs; at least 32 bytes after UTF-8 encoding, derived via SHA-512 into a 64-byte AES-SIV key. Missing or too-short values fail startup. Rotating this key invalidates all previously issued stable IDs |
| `security.maximum-presign-expiry` | `Duration` | `1h` | Maximum validity period of local presigned URLs; enforced at both signing and verification time; must be positive |

### web (Web Endpoints)

| Key | Type | Default | Description |
| --- | --- | --- | --- |
| `web.enabled` | `boolean` | `true` | Whether to register the file web endpoints, the async executor, and the MVC configuration |
| `web.path-prefix` | `String` | `/files` | Path prefix of the file endpoints; also used as the prefix of local presigned URLs. Must be non-empty, start with `/`, and contain no `?`, `#`, `\r`, or `\n`. Note: the migration management endpoints are fixed at `/files/migration/*` and **do not follow this prefix** |
| `web.direct-access` | `boolean` | `false` | Whether to allow Web uploads without presign credentials. Even when enabled, the host must provide a `FileStorageAccessPolicy` Bean and it must return allow; Web reads do not require component credentials but remain concurrency-limited |
| `web.sendfile-binary-content-types-enabled` | `boolean` | `true` | Whether confirmed standard binary MIME types may keep Tomcat `sendfile` when gzip or another compression capability is negotiated. Text, SVG, unknown, and invalid MIME types always stream; set `false` when the host applies custom compression to binary content |
| `web.read-buffer-size` | `DataSize` | `128KB` | Copy buffer size per response stream; this is a per-copy buffer, not whole-file memory. Must be positive and not exceed `Integer.MAX_VALUE` |
| `web.max-concurrent-reads` | `int` | `128` | Maximum concurrent Web response reads; when exhausted, the request returns HTTP 429 instead of queueing. Covers streaming, cached-file, and zero-copy transfers |
| `web.max-concurrent-uploads` | `int` | `16` | Maximum concurrent Web uploads; when exhausted, the request returns HTTP 429 before reading its body. SDK uploads are not affected |
| `web.max-concurrent-transfers-per-key` | `int` | `0` (disabled) | Per-host-resolver-key upload or read concurrency limit; must be at least 0. Uploads and reads use separate counters, exhaustion returns HTTP 429, and idle key state is removed immediately |

### cache (Two-Level Disk Read Cache)

| Key | Type | Default | Description |
| --- | --- | --- | --- |
| `cache.enabled` | `boolean` | see description | Enabled by default for MinIO, RustFS, and Alibaba Cloud OSS; set it to `false` to disable. Local does not use a second-level cache, and explicit `true` fails startup (remove the old setting) |
| `cache.root` | `Path` | `${java.io.tmpdir}/dinghai-file-storage-cache` | Cache root directory; null or creation failure fails startup. **Production deployments should explicitly configure a persistent directory** — the default location sits under the system temp directory and may be cleaned up by the OS |
| `cache.max-object-size` | `long` (bytes) | `50MB` | Objects larger than this are not asynchronously persisted to disk; read requests pass through to the remote directly |
| `cache.idle-ttl` | `Duration` | `7d` | How long an idle replica is retained, judged by the last-access time recorded in each replica's sidecar file |
| `cache.buffer-size` | `DataSize` | `128KB` | Per-copy buffer for persisting cache entries to disk; must be positive and not exceed `Integer.MAX_VALUE` |
| `cache.max-concurrent-fills` | `int` | `4` | Size of the fixed thread pool for asynchronous persistence; must be positive |
| `cache.queue-capacity` | `int` | `32` | Capacity of the fill-task waiting queue; when the queue is full, the fill is abandoned (without affecting the current read request) instead of blocking business |

### upload (Upload)

| Key | Type | Default | Description |
| --- | --- | --- | --- |
| `upload.max-size` | `long` (bytes) | `100MB` | Size limit shared by plain and presigned uploads; exceeding it throws `OBJECT_TOO_LARGE` |
| `upload.content-type-whitelist` | `Set<String>` | empty set (no restriction) | Content type whitelist; when non-empty, uploads whose content type is not in the list throw `CONTENT_TYPE_NOT_ALLOWED` |

### multipart (Large-File Multipart Upload)

| Key | Type | Default | Description |
| --- | --- | --- | --- |
| `multipart.part-size` | `DataSize` | `10MB` | Target part size: every non-final part must equal it and the final part may be smaller. It must be at least `5MB` to satisfy S3 and OSS requirements |
| `multipart.max-parts` | `int` | `10000` | Maximum number of parts in one object; valid range is `1` through `10000` |
| `multipart.session-ttl` | `Duration` | `24h` | Maximum lifetime of an incomplete session; a client `expiresIn` cannot exceed it |
| `multipart.local-root` | `Path` | `${java.io.tmpdir}/dinghai-file-storage-multipart` | Root for incomplete Local parts and session metadata; configure a persistent directory in production |
| `multipart.max-concurrent-uploads` | `int` | `8` | Limit for Local parts written through the application; exhaustion returns `429` immediately without reading the rejected request body |
| `multipart.cleanup-interval` | `Duration` | `PT5M` | Fixed-delay interval for scheduled expired-session cleanup |

### migration (Offline Migration)

| Key | Type | Default | Description |
| --- | --- | --- | --- |
| `migration.enabled` | `boolean` | `false` | Whether to enable the migration service and migration management endpoints. Endpoint registration additionally requires the host to provide a `FileStorageMigrationAccessPolicy` Bean; without the policy, the endpoints are not registered |
| `migration.export-root` | `Path` | none (required when enabled=true) | Root directory where export packages are written |
| `migration.import-root` | `Path` | none (required) | Root directory scanned for packages to import |
| `migration.completed-root` | `Path` | none (required) | Archive directory for successfully imported packages |
| `migration.failed-root` | `Path` | none (required) | Archive directory for failed packages and their `errors.ndjson` |

Full configuration example:

```yaml
dinghai:
  file-storage:
    provider: local
    bucket: documents
    storage-alias: documents
    local:
      root: /var/lib/app/files
    security:
      signing-key: ${DINGHAI_FILE_STORAGE_SIGNING_KEY}
      stable-id-key: ${DINGHAI_FILE_STORAGE_STABLE_ID_KEY}
    web:
      enabled: true
      path-prefix: /files
      direct-access: false
      sendfile-binary-content-types-enabled: true
      read-buffer-size: 128KB
      max-concurrent-reads: 128
      max-concurrent-uploads: 16
      max-concurrent-transfers-per-key: 4
    cache:
      max-object-size: 50MB
      buffer-size: 128KB
      max-concurrent-fills: 4
      queue-capacity: 32
    upload:
      max-size: 100MB
    multipart:
      part-size: 10MB
      max-parts: 10000
      session-ttl: 24h
      local-root: /var/lib/app/multipart
      max-concurrent-uploads: 8
      cleanup-interval: PT5M
```

## Web Endpoints

With `web.enabled=true`, the following endpoints are registered (default prefix `/files`, controlled by `web.path-prefix`):

| Endpoint | Description |
| --- | --- |
| `PUT /upload/{*key}` | Local presigned upload (HMAC); unsigned requests use direct-upload authorization and are limited by `web.max-concurrent-uploads`. Successful uploads return `201 Created`, `Location`, and confirmed object metadata |
| `GET /download/{*key}` | Download; forces `attachment` and is limited by `web.max-concurrent-reads` |
| `GET /preview/{*key}` | Preview; forces `inline` and is limited by `web.max-concurrent-reads` |
| `GET /read/key/{*key}` plus `/read/key/preview/{*key}` and `/read/key/download/{*key}` | Anonymous read by the real object key; no component read credential required, but limited by `web.max-concurrent-reads` |
| `GET /read/v1/{id}` plus `/read/v1/preview/{id}` and `/read/v1/download/{id}` | Anonymous read by stable file ID — no user authentication and no signature required (see next section) |

### Large-File Multipart Upload

Multipart endpoints also require `web.enabled=true` and use `web.path-prefix`, but they are registered only when the host provides a `FileStorageAccessPolicy` Bean. That policy still decides `MULTIPART_UPLOAD` for the current principal and object key on every request. Without the Bean these endpoints are not registered; this is independent of normal `web.direct-access` authorization.

| Endpoint | Description |
| --- | --- |
| `POST /multipart` | Creates a session and returns `201 Created` with `uploadId`, part sizing, expiry, and the confirmed-parts snapshot |
| `POST /multipart/{uploadId}/parts/{partNumber}/presign` | Returns the upload method, expiry, and required headers for one part |
| `GET /multipart/{uploadId}/parts` | Returns provider-confirmed parts sorted by `partNumber`; this is the authority for resuming |
| `PUT /multipart/{uploadId}/parts/{partNumber}` | **Local only**: the application streams this part; remote providers return `MULTIPART_INVALID` from this route |
| `POST /multipart/{uploadId}/complete` | Submits the part manifest and completes the upload, returning confirmed `ObjectMetadata` |
| `DELETE /multipart/{uploadId}` | Aborts the session and returns `204 No Content` |

This example creates a 20 MiB object with fixed 10 MiB parts. `partSize` must equal `multipart.part-size`, `size` must not exceed `upload.max-size`, and `expiresIn` must not exceed `multipart.session-ttl`:

```bash
curl --request POST https://app.example/files/multipart \
  --header 'Content-Type: application/json' \
  --data '{
    "key":"uploads/video.mp4",
    "size":20971520,
    "partSize":10485760,
    "contentType":"video/mp4",
    "expiresIn":"PT30M"
  }'
```

A typical creation response contains the following fields (`uploadId` is opaque):

```json
{
  "uploadId": "opaque-upload-id",
  "key": {"value": "uploads/video.mp4"},
  "totalSize": 20971520,
  "partSize": 10485760,
  "partCount": 2,
  "expiresAt": "2026-09-15T12:30:00Z",
  "parts": []
}
```

- **Local**: optionally call `POST /multipart/opaque-upload-id/parts/1/presign` to obtain the application-declared `PUT` method, expiry, and required headers, then stream the part to `PUT /files/multipart/opaque-upload-id/parts/1`. Every part must have exactly its expected length. The application limits these writes with `multipart.max-concurrent-uploads`; exhaustion returns `429` with `Retry-After: 1` before the body is read.
- **MinIO / RustFS / Alibaba Cloud OSS**: after calling the same `presign` endpoint, the client must perform the provider-native `PUT` directly using the returned `url`, `method`, and `requiredHeaders`; part content does not pass through the application. Never log, cache, or commit a complete presigned URL.
- For a failed part or network interruption, upload the **same partNumber** again; the later upload replaces the earlier one. Before resuming, call `GET /multipart/{uploadId}/parts` and build completion only from its `partNumber`, `size`, and `etag` values. Do not infer state from a local client cache.

Completion must contain every provider-confirmed part. `idempotencyKey` is optional and can be used for client-side call tracking:

```bash
curl --request POST https://app.example/files/multipart/opaque-upload-id/complete \
  --header 'Content-Type: application/json' \
  --data '{
    "parts":[
      {"partNumber":1,"size":10485760,"etag":"provider-etag-1"},
      {"partNumber":2,"size":10485760,"etag":"provider-etag-2"}
    ],
    "idempotencyKey":"client-completion-token"
  }'
```

During the session TTL, repeating completion for the same session returns the first confirmed metadata; repeating abort also retains `204` success semantics. The scheduled cleanup reclaims incomplete sessions after expiry. An expired session cannot resume and must be created again.

#### Frontend Integration Example

The following zero-dependency browser function covers session creation, concurrent part uploads, resumability (authoritative server state), and completion. Copy and use it directly:

```js
/**
 * Large-file multipart upload (dinghai-file-storage).
 * Prerequisite: the page already carries the application's login
 * credentials (for example, a Cookie).
 *
 * @param {File} file the file picked from an input element
 * @param {string} key object key, such as "uploads/video.mp4"
 * @returns {Promise<object>} confirmed object metadata
 */
async function uploadLargeFile(file, key, {
  baseUrl = '/files',                // must equal the server web.path-prefix
  partSize = 10 * 1024 * 1024,       // must equal the server multipart.part-size
  expiresIn = 'PT30M',               // must not exceed the server multipart.session-ttl
  concurrency = 3,
  resume = null,                     // resume: { uploadId, partSize, partCount }; see notes below
  onProgress,
} = {}) {
  async function request(path, init = {}) {
    const response = await fetch(baseUrl + path, { credentials: 'same-origin', ...init });
    if (!response.ok) {
      const body = await response.json().catch(() => ({}));
      throw new Error(`${response.status} ${body.code ?? ''} ${body.message ?? ''}`);
    }
    return response;
  }
  const post = (path, body) => request(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  // 1. Create the session; with resume, pull the confirmed parts from the server instead
  const session = resume
    ? {
        uploadId: resume.uploadId,
        partSize: resume.partSize,
        partCount: resume.partCount,
        parts: await (await request(`/multipart/${resume.uploadId}/parts`)).json(),
      }
    : await (await post('/multipart', {
        key,
        size: file.size,
        partSize,
        contentType: file.type || 'application/octet-stream',
        expiresIn,
      })).json();
  const confirmed = new Map((session.parts ?? []).map((p) => [p.partNumber, p]));
  let uploadedBytes = [...confirmed.values()].reduce((sum, p) => sum + p.size, 0);

  // 2. Presign and upload each part
  async function uploadPart(partNumber) {
    if (confirmed.has(partNumber)) return;
    const start = (partNumber - 1) * session.partSize;
    const blob = file.slice(start, Math.min(start + session.partSize, file.size));
    const presign = await (await post(
      `/multipart/${session.uploadId}/parts/${partNumber}/presign`)).json();

    // presign.requiredHeaders must be echoed back verbatim
    const response = await fetch(presign.url, {
      method: presign.method,
      headers: presign.requiredHeaders ?? {},
      body: blob,
      credentials: 'same-origin',
    });
    if (!response.ok) throw new Error(`part ${partNumber} failed: ${response.status}`);

    // Local returns the etag in the response JSON; MinIO/RustFS/OSS return it
    // in the ETag response header
    const etagHeader = response.headers.get('ETag');
    const part = etagHeader
      ? { partNumber, size: blob.size, etag: etagHeader.replaceAll('"', '') }
      : await response.json();
    confirmed.set(partNumber, part);
    uploadedBytes += part.size;
    onProgress?.(uploadedBytes, file.size);
  }
  const queue = Array.from({ length: session.partCount }, (_, i) => i + 1);
  await Promise.all(Array.from({ length: concurrency }, async () => {
    while (queue.length > 0) await uploadPart(queue.shift());
  }));

  // 3. Submit every server-confirmed part and complete the upload
  const parts = [...confirmed.values()].sort((a, b) => a.partNumber - b.partNumber);
  return (await post(`/multipart/${session.uploadId}/complete`, { parts })).json();
}
```

Usage:

```js
const metadata = await uploadLargeFile(fileInput.files[0], 'uploads/video.mp4', {
  onProgress: (done, total) => console.log(`${((done / total) * 100).toFixed(1)}%`),
});
console.log('uploaded', metadata.key.value, metadata.etag);
```

Notes:

- When a part fails, simply retry the same `partNumber`; the later upload replaces the earlier one.
- To resume across page reloads, persist `{ uploadId, partSize, partCount }` (for example in `localStorage`) after session creation, then pass it as `resume` next time; confirmed parts are skipped automatically. Always trust the server `GET /multipart/{uploadId}/parts` list, never local client state.
- With MinIO / RustFS / Aliyun OSS the browser uploads parts directly to the provider, so the bucket needs a CORS rule: allow the page origin, the `PUT` method, and every header appearing in `presign.requiredHeaders`, and expose `ETag` (Expose-Headers); otherwise the browser cannot read the etag.
- When any step returns `429`, wait for the `Retry-After` interval and retry. `MULTIPART_EXPIRED` (410) means the session has expired: discard the `resume` data and create a new session.

The migration management endpoints are fixed at `POST /files/migration/export` and `POST /files/migration/import` (parameter `packageName`) and do not follow `web.path-prefix`.

A successful `PUT /upload/{*key}` response keeps the SDK `FileStorage.put` `ObjectMetadata` field names and adds `previewUrl`:

```json
{
  "key": {"value": "1/1/1/1.png"},
  "contentLength": 5,
  "contentType": "image/png",
  "etag": "provider-confirmed-etag",
  "lastModified": "2026-09-12T15:37:23Z",
  "cacheControl": {"value": "private, max-age=0"},
  "previewUrl": "/files/read/key/preview/1/1/1/1.png"
}
```

The response status is `201 Created`, and `Location` equals `previewUrl`. `key`, length, type, ETag, modification time, and cache control all come from the provider-confirmed persisted result, not client-declared values. `previewUrl` is a root-relative address under the configured `web.path-prefix`; it uses the real object key, preserves `/` hierarchy, and percent-encodes path characters. Browsers can use it with the component's anonymous real-key preview route. It is not a Local disk path or a raw MinIO/OSS URL, and it does not contain a deployment host; a reverse proxy or application boundary can prepend its public origin when needed.

Preview/download supports `Content-Type`, ETag, Last-Modified, Cache-Control, Expires, 304 (If-None-Match / If-Modified-Since), and single-range Range 206/416. Local primary files and validated remote hot-cache files prefer native `sendfile` on plain embedded Tomcat: with the default enabled, confirmed common images, audio/video, archives, PDFs, and fonts may retain that path even when the request accepts gzip; text, SVG, unknown, and invalid MIME types remain bounded streaming. TLS, other containers, or a rejected container registration automatically fall back without changing caller-visible semantics.

## Presigning and Access Authorization

- **Local backend**: the component implements its own HMAC-SHA-256 presigning. Signed URLs carry query parameters such as `expires` (Unix seconds) and `signature` (Base64URL); upload signatures can additionally bind `contentType`, `maxSize`, and `cacheControl`, while download signatures bind the `inline` or `attachment` response mode. The validity period is limited by `security.maximum-presign-expiry`.
- **MinIO / RustFS backends**: use each S3-compatible service's native presigning (PostPolicy for uploads, `getPresignedObjectUrl` for downloads). Local signature verification is only supported by the Local adapter; presenting a local-style signed URL to a non-Local backend throws `INVALID_PRESIGN`.
- **Alibaba Cloud OSS backend**: uses V2 native presigning (`PUT` uploads with `requiredHeaders` and `GET` downloads). It supports only fixed AccessKey values injected through `OSS_ACCESS_KEY_ID` and `OSS_ACCESS_KEY_SECRET`, not STS temporary credentials. Local signature verification is only supported by the Local adapter; presenting a local-style signed URL to Alibaba Cloud OSS throws `INVALID_PRESIGN`.
- **Direct upload**: unsigned uploads are denied by default; they require both `web.direct-access=true` and a host-provided `FileStorageAccessPolicy` Bean that allows UPLOAD. Web downloads, previews, and reads do not require component credentials, but remain protected by read concurrency limits.
- All Beans carry `@ConditionalOnMissingBean`, so the host can replace any default implementation (including `FileStorage` itself).

### Host Key-Based Transfer Isolation

After setting a positive `web.max-concurrent-transfers-per-key`, the host can register a `WebTransferRateLimitKeyResolver` Bean that returns a key from verified user, tenant, or API-client context:

```java
@Bean
WebTransferRateLimitKeyResolver webTransferRateLimitKeyResolver() {
    return request -> currentTenantId();
}
```

The component does not derive a key from IP addresses, JWTs, cookies, forwarded headers, or API keys, and never logs or echoes it. If no resolver is registered, it returns `null`/blank, or it throws a runtime exception, the request falls back to global upload/read limits only. The per-key limit is combined with global limits; upload and read quotas for the same key are independent, and SDK `FileStorage.put/get` calls never consume Web permits.

## Stable File ID

A stable ID is the persistent read address of a private object (the `/files/read/v1/{id}` family). The ID is produced by deterministic authenticated encryption of the length-prefixed `storage-alias + object key` using AES-SIV (Tink), output as a Base64URL string with a `v1.` prefix. Therefore:

- The ID of the same object stays unchanged after migration across Local / MinIO / RustFS / Alibaba Cloud OSS (provided `storage-alias` is unchanged);
- Rotating `security.stable-id-key` invalidates all previously issued IDs;
- This route **does not perform component-internal user authentication** — the embedding application is responsible for access control in front of the browser.

## Read Cache Behavior

MinIO, RustFS, and Alibaba Cloud OSS enable by default a two-level disk read cache validated against remote `stat`; Local creates neither a second-level cache nor a cleaner:

1. Every read first `stat`s the remote metadata; a local replica only hits when its ETag / length / modification time all match the remote, and on hit the last-access time is refreshed;
2. On a miss, when the object does not exceed `cache.max-object-size`, the current request streams directly from the remote while a second stream asynchronously persists to disk (temporary file + atomic move to publish);
3. `put` / `delete` immediately invalidate the corresponding replica;
4. Every day at 00:00 (server local timezone), replicas that have not been accessed for longer than `cache.idle-ttl` are cleaned up; cleanup failures are silently left for the next round;
5. Cache file names are the hex digest of `SHA-256(alias + key)` and do not leak the raw object key.

## Zero-Copy and Concurrency

- core exposes an optional `LocalFileSource` interface (`Optional<Path> localFile(ObjectKey)`): the Local adapter and the remote read cache (on a validated cache hit) implement it to hand the Web layer a root-validated local file path; providers without this capability stay on the streaming path.
- Local primary files and validated remote cache hits prefer native Tomcat `sendfile` for plain requests without allocating JVM heap proportional to file size. With the default `web.sendfile-binary-content-types-enabled=true`, confirmed standard binary MIME types can retain this path under gzip and other compression negotiation.
- Text, SVG, unknown, and invalid MIME types always fall back to bounded streaming under compression negotiation. Set the switch to `false` to restore conservative streaming for every compression-capable request when the host intentionally applies custom compression to binary content.
- TLS, non-Tomcat containers, temporarily unavailable files, and rejected registrations automatically fall back to bounded streaming while preserving content, conditional-request, and Range semantics.
- Both the global read limit and optional per-key read limit cover streaming fallbacks, cached files, and Tomcat `sendfile`; permits remain held until the response completes or fails, and saturated requests immediately return HTTP 429.
- When upgrading a Local deployment, remove `dinghai.file-storage.cache.enabled=true`. The setting is removed and retaining it fails startup.

## Offline Migration

Offline migration only handles server-directory packages; large files are not uploaded over HTTP. Export produces `manifest.ndjson` (containing the SHA-256 and length of each object) and an `objects/` directory; before import, the manifest is fully validated to avoid partial imports. Successfully imported packages are moved to completed; failed packages and their `errors.ndjson` are moved to failed. The management endpoints are registered only when `migration.enabled=true` and the host provides `FileStorageMigrationAccessPolicy`; the policy must authorize the `EXPORT` and `IMPORT` operations separately.

## Error Codes

Unified error codes are defined in `FileStorageErrorCode`: `INVALID_REQUEST`, `OBJECT_NOT_FOUND`, `INVALID_OBJECT_KEY`, `OBJECT_TOO_LARGE`, `CONTENT_TYPE_NOT_ALLOWED`, `AUTHENTICATION_FAILED`, `STORAGE_UNAVAILABLE`, `INVALID_PRESIGN`, `INVALID_CONFIGURATION`, `RATE_LIMITED`, `MULTIPART_INVALID`, `MULTIPART_NOT_FOUND`, `MULTIPART_EXPIRED`, `MULTIPART_CONFLICT`, `INTERNAL_ERROR`.

Web-layer HTTP mapping: `OBJECT_NOT_FOUND` / `MULTIPART_NOT_FOUND` → 404, `MULTIPART_EXPIRED` → 410, `MULTIPART_CONFLICT` → 409, `OBJECT_TOO_LARGE` → 413, `INVALID_PRESIGN` / `AUTHENTICATION_FAILED` → 403, `STORAGE_UNAVAILABLE` → 503, `RATE_LIMITED` → 429 (with a `Retry-After: 1` header), and `MULTIPART_INVALID` plus other request errors → 400. The response body is `{code, message}`.

## Design Notes

- **No log-redaction concerns**: the component code emits no logs at all, and every exception message is a fixed safe text that never echoes keys, signed URLs, or configuration values.
- **Streaming I/O**: Local writes via temporary file + atomic move; Web reads prefer Tomcat `sendfile` when available and otherwise use bounded streaming — files are never loaded into memory as a whole; `web.read-buffer-size` and `cache.buffer-size` are only per-copy buffers.
- **Dedicated async executor**: streaming responses use a bounded executor dedicated to file storage (core=4 / max=16 / queue=64, thread prefix `file-storage-web-`), avoiding reliance on the unbounded default executor. Note that it is registered as Spring MVC's global async executor and therefore affects the host's other `StreamingResponseBody` usages; the host may override it as needed.
- **Explicit over implicit**: no automatic bucket creation, no automatic fallback between backends, unsigned access denied by default, and any illegal configuration fails startup.

## Benchmark

The `benchmark` module is for development verification only. It can run concurrent uploads, cold reads, MinIO cache fill/hot reads, and SHA-256 verification against real files:

```bash
# Local: upload 4 concurrent copies, read each copy for 2 rounds
mvn -pl dinghai-file-storage/benchmark -am -DskipTests install
mvn -f dinghai-file-storage/benchmark/pom.xml exec:java \
  -Dexec.mainClass=io.github.lvdaxianer.dinghai.filestorage.spring.FileStorageBenchmark \
  -Dexec.args="--provider=local --file=$HOME/Downloads/982-合0820.FBX --concurrency=4 --read-rounds=2"

# MinIO: reads endpoint, access key, secret key, and bucket from environment variables at runtime
export DINGHAI_FILE_STORAGE_MINIO_ENDPOINT=http://localhost:9000
export DINGHAI_FILE_STORAGE_MINIO_ACCESS_KEY=CHANGE_ME
export DINGHAI_FILE_STORAGE_MINIO_SECRET_KEY=CHANGE_ME
export DINGHAI_FILE_STORAGE_MINIO_BUCKET=dinghai-benchmark
mvn -f dinghai-file-storage/benchmark/pom.xml exec:java \
  -Dexec.mainClass=io.github.lvdaxianer.dinghai.filestorage.spring.FileStorageBenchmark \
  -Dexec.args="--provider=minio --file=$HOME/Downloads/doc.zip --concurrency=4 --read-rounds=2"
```

The benchmark uses independent random object keys and best-effort deletes the objects on completion; the output includes per-phase duration, throughput, mean/P95/P99 latency, and error count, and never prints keys. Local has been measured with `982-合0820.FBX` (412,202,320 bytes) under the parameters above: 4 concurrent uploads totaling 1.53 GiB, mean 1298 ms, P95 1406 ms, throughput 1117 MiB/s; followed by 8 concurrent cold reads totaling 3.07 GiB, mean 200 ms, P95 230 ms, throughput 7765 MiB/s. Docker MinIO (mapped port 9100) has been measured with `doc.zip` (about 61 MiB) at concurrency 4: upload 48 MiB/s, cold read 371 MiB/s, cache fill 180 MiB/s, and hot read 6079 MiB/s, with zero errors in every phase. All SHA-256 verifications passed. These figures are affected by OS and MinIO caches and must not be treated as raw physical-disk throughput. Do not commit credentials or full presigned URLs to the repository.

## Testing

```bash
mvn test
```

Multipart lifecycle acceptance tests are skipped by default and run only with explicit temporary test credentials. The MinIO test creates a temporary bucket; the RustFS test also creates and deletes a temporary bucket:

```bash
export DINGHAI_FILE_STORAGE_MINIO_IT=true
export DINGHAI_FILE_STORAGE_MINIO_ENDPOINT=http://localhost:9000
export DINGHAI_FILE_STORAGE_MINIO_ACCESS_KEY=CHANGE_ME
export DINGHAI_FILE_STORAGE_MINIO_SECRET_KEY=CHANGE_ME
mvn -pl minio -am -Dtest=MinioMultipartLifecycleIntegrationTest test

export DINGHAI_FILE_STORAGE_RUSTFS_IT=true
export DINGHAI_FILE_STORAGE_RUSTFS_ENDPOINT=http://localhost:9100
export DINGHAI_FILE_STORAGE_RUSTFS_ACCESS_KEY=CHANGE_ME
export DINGHAI_FILE_STORAGE_RUSTFS_SECRET_KEY=CHANGE_ME
mvn -pl rustfs -am -Dtest=RustfsLifecycleIntegrationTest test
```

The Alibaba Cloud OSS acceptance test runs when credentials are injected. It uses random
`__dinghai_it__/` objects in the `dinghai` bucket and deletes them on completion:

```bash
export OSS_ACCESS_KEY_ID=CHANGE_ME
export OSS_ACCESS_KEY_SECRET=CHANGE_ME
mvn -pl aliyun-oss -am -Dtest=AliyunOssLifecycleIntegrationTest test
```
