## ADDED Requirements

### Requirement: Knowledge packages SHALL be validated against the published contract
The system SHALL accept only schema version `1.0`, Chinese `zh-CN` source metadata, valid snake_case document/concept/section IDs, non-empty sections with concepts, and the documented concept and relation type allowlists. A relation evidence quote SHALL occur verbatim in its referenced section content.

#### Scenario: Validate a correct knowledge package
- **WHEN** a package has matching source metadata, sections, concepts, relations, and evidence quotes
- **THEN** validation succeeds and returns no blocking issue

#### Scenario: Reject an invalid relation quote
- **WHEN** a relation quote is absent from the referenced section content
- **THEN** validation fails with `INVALID_EVIDENCE_QUOTE` and does not publish the package

### Requirement: Knowledge imports SHALL preserve source integrity
The system SHALL calculate SHA-256 over the original UTF-8 Markdown bytes and SHALL require `source.key` and `source.hash` to match the requested file. It SHALL reject absolute paths, parent traversal, separators, non-Markdown extensions, missing file pairs, and unsupported schema versions.

#### Scenario: Reject a tampered source pair
- **WHEN** the Markdown hash differs from the package `source.hash`
- **THEN** the import fails with `SOURCE_HASH_MISMATCH` and the previously published pair remains unchanged

### Requirement: Offline Skills SHALL keep role boundaries
The `skills/` tree SHALL provide `knowledge-loop-protocol`, `knowledge-producer`, `knowledge-checker`, and `knowledge-orchestrator` instructions. Producer SHALL emit complete candidates, Checker SHALL emit PASS/FAIL/ERROR verdicts, and Orchestrator SHALL publish only PASS candidates after at most five repair rounds.

#### Scenario: Process a directory in deterministic order
- **WHEN** the orchestrator receives a directory containing Markdown files
- **THEN** it recursively processes files in dictionary order, carries only approved graph context forward, and reports per-file success or failure totals

#### Scenario: Preserve an old package after checker failure
- **WHEN** Checker returns FAIL or ERROR for a replacement candidate
- **THEN** Orchestrator does not write the candidate into the trusted directory and returns the failure reason
