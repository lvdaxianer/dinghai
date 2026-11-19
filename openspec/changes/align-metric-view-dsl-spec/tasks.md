# Tasks

## 1. Public DSL contract

- [x] 1.1 Complete `dinghai-dsl-engine/docs/dsl-syntax.md` with the canonical generator vocabulary, supported operators, temporal window convention, and version/validation boundary; verify the document is internally consistent.

## 2. Machine-readable schema

- [x] 2.1 Add `dinghai-dsl-engine/docs/schemas/relational-query-v1.schema.json` and verify it loads, accepts the documented v1 request, and rejects unknown fields.

## 3. Skill synchronization

- [x] 3.1 Copy the canonical syntax document and schema into `metric-view-lifecycle/references`, add the MetricView binding note, and align the skill's DSL/type references; verify copied files and active instructions agree.
- [x] 3.2 Align the skill type compatibility gate, generated fixtures, and tests with the engine logical types and supported operators; verify the local test suite passes.

## 4. Integration verification

- [ ] 4.1 Run cross-repository consistency checks, the existing skill unit tests, OpenSpec strict validation, and a final diff audit; verify no pre-existing user changes are overwritten.
