## 1. Implementation and Tests

- [x] 1.1 Add strict raw MIME eligibility classification with unit tests for
  exact types, wildcards, SVG exclusion, parameters, casing, invalid forms, and
  unknown types.
- [x] 1.2 Add the default-enabled web configuration switch and wire it into the
  Tomcat sendfile gate; add focused transfer and controller tests for enabled,
  disabled, text, binary, and raw metadata forwarding behavior.
- [x] 1.3 Add compression-enabled embedded-Tomcat integration tests proving
  binary delivery, text streaming/compression compatibility, and switch-off
  fallback without conflicting response encoding or byte corruption.

## 2. Documentation and Verification

- [x] 2.1 Update Chinese and English README configuration and zero-copy
  sections, including the default, benefit, and custom-compression escape hatch.
- [ ] 2.2 Run focused tests, the complete `dinghai-file-storage` Maven test
  suite, strict OpenSpec validation, plan-implementation audit, and full
  code-review-spec review; resolve every finding.
