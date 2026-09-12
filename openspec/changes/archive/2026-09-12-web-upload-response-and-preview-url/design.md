## Context

The SDK's `FileStorage.put` returns `ObjectMetadata`, but `PUT /upload/{key}` discards that value and returns `204 No Content`. The starter already exposes an anonymous, real-key preview route at `/read/key/preview/{*key}` under the configured Web path prefix.

## Goals / Non-Goals

**Goals:**
- Return confirmed storage metadata from successful direct and presigned Web uploads.
- Return a path-safe, browser-usable preview URL based on the persisted object's real key.
- Keep response fields stable across Local and MinIO providers.

**Non-Goals:**
- Do not change SDK return types, provider presigning behavior, authorization, or stable file IDs.
- Do not synthesize a host-specific absolute URL, because reverse-proxy host and scheme are deployment concerns.

## Decisions

1. **Use a dedicated flat response record.** It mirrors the six `ObjectMetadata` components at the top level and adds `previewUrl`, rather than nesting metadata. This gives Web clients the same field names as SDK callers while retaining a clear HTTP-only field.
2. **Return a root-relative real-key URL.** `previewUrl` is `web.path-prefix + /read/key/preview/ + percent-encoded key segments`. It remains browser-accessible behind any host, context path, CDN, or reverse proxy and does not expose physical Local or provider URLs.
3. **Return `201 Created` and `Location`.** Both direct and validated pre-signed uploads produce the same response after `storage.put` succeeds. The `Location` header equals `previewUrl`.

## Risks / Trade-offs

- [Real object key appears in the response URL] → This is intentional for the existing anonymous real-key route; callers needing opaque addressing continue to use stable file IDs.
- [Relative URL requires the caller's current origin] → A root-relative URL is portable across reverse-proxy deployments; applications that require an absolute public host can prefix it at their boundary.
- [Existing clients expect 204] → This is a compatible HTTP success transition for clients that ignore successful response bodies, but API documentation records the new response contract.
