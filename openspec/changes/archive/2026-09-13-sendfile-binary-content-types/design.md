# Design: Binary Sendfile Eligibility

## Decision

`TomcatSendfileTransfer` remains the sole owner of native-transfer
registration. The controller supplies the raw provider-confirmed
`ObjectMetadata.contentType()` and the transfer gate decides whether a request
is safe for native delivery.

When `Accept-Encoding` is compression-capable, native transfer is allowed only
when all existing local-file and range preconditions are met and either:

1. `web.sendfile-binary-content-types-enabled` is `true` and the raw media type
   is recognized as an eligible standard binary type; or
2. the request accepts only `identity` (or has no compression capability),
   preserving current behavior.

The new property defaults to `true`. Setting it to `false` restores the prior
conservative behavior for every compression-capable request. This is the
operational escape hatch for hosts that configure binary MIME types in a custom
compression filter.

## Media-Type Classification

The classifier uses the raw string rather than Spring's response-header parser:
an invalid provider value must not be converted to `application/octet-stream`.
It strips parameters and performs case-insensitive matching only after strict
validation of exactly one RFC token `type/subtype` pair. Empty values,
whitespace-only values, extra slashes, invalid tokens, and unknown types return
`false`.

Eligible types are a deliberately small standard set (`application/octet-stream`,
PDF, common archives and fonts), plus `image/*` except SVG, `video/*`,
`audio/*`, and `font/*`. The name represents sendfile eligibility under standard
container compression defaults, not a claim that every selected format is
mathematically incompressible. Unrecognized types, including WebAssembly, stay
on the safe streaming path unless later evidence justifies an explicit policy.

## Operational Safety

The property is bound through existing `FileStorageProperties.Web`, so invalid
configuration continues through the ordinary Spring Boot binding lifecycle.
No additional threads, remote calls, logs, response bodies, or user-controlled
classification values are introduced. Existing bounded streaming and read-permit
paths remain the fallback.

## Test Strategy

- Unit-test strict media-type recognition and the configuration-aware transfer
  gate.
- Test controller forwarding of raw provider metadata without trusting a
  response-header fallback.
- Run embedded Tomcat with `server.compression.enabled=true` and a gzip request:
  binary bytes must return correctly without `Content-Encoding`; text must take
  the compression-compatible streaming route and retain exact decoded bytes;
  disabling the switch must route binary through streaming.
