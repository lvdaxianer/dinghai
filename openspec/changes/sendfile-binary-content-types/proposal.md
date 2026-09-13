# sendfile-binary-content-types

## Why

Browser requests normally carry `Accept-Encoding: gzip`. The current web
transfer gate therefore routes every local file through a JVM streaming copy,
including images, audio, video, archives, PDFs, and fonts that standard server
compression does not compress. This removes Tomcat's native `sendfile` path
from the most common large-download workload.

## What Changes

- Permit native Tomcat `sendfile` for recognized standard binary response
  Content-Types even when a request accepts compression.
- Keep text, SVG, absent, malformed, and unrecognized Content-Types on the
  bounded streaming path whenever compression is acceptable.
- Add `dinghai.file-storage.web.sendfile-binary-content-types-enabled`, default
  `true`, to disable this optimization where a host installs custom binary
  compression.
- Validate the behavior against an embedded Tomcat instance with compression
  enabled, not only request-attribute tests.
- Document the switch and its operational tradeoff in Chinese and English.

## Impact

The change is limited to `dinghai-file-storage/spring-boot-starter` web read
delivery. It adds a backward-compatible configuration property. No storage
provider API, object key, signed URL, authorization, cache, or upload behavior
changes.
