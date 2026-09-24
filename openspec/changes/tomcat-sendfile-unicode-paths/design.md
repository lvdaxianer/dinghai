## Context

`FileStorageWebController` already chooses Tomcat `sendfile` for a local file before it opens a
bounded `StreamingResponseBody`. `TomcatSendfileTransfer` passes the absolute filename through
Tomcat request attributes. That native path works for ASCII filenames but can produce an empty
body for non-ASCII paths in the affected Linux/JDK deployment.

## Goals / Non-Goals

**Goals:**

- Preserve zero-copy transfers for eligible ASCII ordinary files.
- Detect sendfile safety from the final absolute local `Path`, regardless of the object key.
- Route unsafe paths through the existing bounded, range-aware stream path without changing HTTP
  response semantics or the binary-content-type sendfile configuration.

**Non-Goals:**

- Altering object-key encoding, URL shape, Tomcat global sendfile settings, or the local provider
  path resolver.
- Loading complete files into memory or adding a second streaming implementation.

## Decisions

### Keep the decision at the Tomcat boundary

Add `isSendfilePathSafe(Path)` to `TomcatSendfileTransfer` and invoke it before registering
Tomcat attributes. It requires a non-null regular file without following links and verifies every
character of `path.toAbsolutePath().toString()` is ASCII. The helper is package-visible for focused
tests. Keeping this check at the register boundary protects Local and disk-cache callers equally.

An object-key based check was rejected because a safe ASCII key can resolve under a non-ASCII root,
and a Unicode key can resolve to an ASCII cache path. Encoding the filename for Tomcat was rejected
because the native transfer endpoint expects the actual filesystem pathname.

### Reuse the established stream fallback

`register` returns `false` for an unsafe path. The controller already responds by opening a
`DownloadObject` and copying only the requested byte range through its bounded buffer, preserving
the read permit lifecycle, `Content-Length`, and Range behavior. No controller branch or new
configuration is needed.

### Prove behavior at both boundaries

Unit tests assert ASCII registration and Unicode rejection. A real embedded-Tomcat regression test
uses the supplied nested Unicode GLTF-like key and verifies a non-empty response, exact length,
correct Range bytes, and absent sendfile attributes; the existing ASCII integration test continues
to prove zero-copy registration.

## Risks / Trade-offs

- [A filesystem path contains a non-ASCII directory even when its key is ASCII] → Inspect the
  absolute resolved path, not the key.
- [Path checks race with filesystem changes] → Retain the existing no-follow regular-file and size
  checks immediately before Tomcat registration; the fallback remains safe if eligibility changes.
- [Unicode files use JVM heap] → The fallback copies through the existing configured fixed-size
  buffer and never materializes the complete object.

## Migration Plan

Deploy as a backward-compatible code change. Existing sendfile configuration continues to gate
eligible binary requests. Roll back by reverting this change; no stored objects or URLs require
migration.

## Open Questions

None.
