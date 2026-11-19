# Proposal

## Why

`metric-view-lifecycle` needs a stable, executable description of the Dinghai
relational DSL, but the current skill vocabulary diverges from the engine's
implemented request shape and logical type names. The existing engine document
already describes most of the protocol; this change makes the remaining
generator-critical semantics explicit and distributes the same canonical
document to the skill references without moving MetricView lifecycle ownership
into the public DSL engine.

## What Changes

- Clarify the canonical relational-query v1 envelope and payload vocabulary.
- Document the supported operator set and the `gte` + `lt` half-open time-window
  convention used by generated metric queries.
- Align logical type names and database mappings with the engine's
  `integer`/`long`/`local_datetime`/`instant` model.
- Add a machine-readable JSON Schema for the public DSL request.
- Copy the canonical syntax document and schema into the
  `metric-view-lifecycle` skill references.
- Add a skill-side binding note for the boundary between the DSL request and
  MetricView's Python/API/output contracts; the public engine remains
  independent of MetricView management APIs.

## Capabilities

### New Capabilities

None. This is a documentation and contract-alignment change; it does not
change runtime behavior.

### Modified Capabilities

None. `skip_specs: true` is used because no implementation requirement changes.

## Impact

- Public documentation under `dinghai-dsl-engine/docs`.
- A new JSON Schema under the same docs tree.
- New copied reference artifacts under
  `metric-view-lifecycle/references`.
- Minimal updates to the skill's DSL planning and type-mapping references.
- No production Java, Python runtime, database, or HTTP endpoint changes.
