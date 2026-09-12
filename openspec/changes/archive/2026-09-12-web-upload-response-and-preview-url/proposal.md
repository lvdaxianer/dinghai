## Why

Web upload currently returns `204 No Content`, while the SDK returns the saved object's metadata. Callers therefore cannot persist an ETag, confirmed size, or a browser-ready preview address after a successful upload.

## What Changes

- Change successful Web upload responses from `204 No Content` to `201 Created` with the six `ObjectMetadata` fields.
- Add `previewUrl`, constructed from the configured Web path prefix and the real object key, for the existing anonymous real-key preview route.
- Set the HTTP `Location` header to that same preview URL.

## Capabilities

### New Capabilities

- `web-upload-result`: A provider-neutral, metadata-aligned result for successful Web uploads.

### Modified Capabilities

- `file-storage-web-access`: Successful upload HTTP response semantics and real-key preview URL contract.

## Impact

Affected code is limited to the Spring Boot starter's upload controller, response model, tests, README API documentation, and OpenSpec specifications. No provider API, bucket layout, or object key semantics changes.
