# Design

## Context

The engine already documents `relational-query` v1, including its request
envelope, relational payload, parameter binding, logical types, limits, and
adapter versioning. The consumer skill currently describes a different
vocabulary and a broader operator set. See `proposal.md` for the motivation.

## Goals / Non-Goals

**Goals:**

- Make the engine document the single source of truth for the public DSL
  envelope, payload fields, operators, logical types, temporal semantics, and
  resource limits.
- Provide a JSON Schema that validates the structural part of a v1 request.
- Make the skill consume a copied, clearly versioned snapshot of that document.
- Keep the MetricView management API, Python stdout contract, output mapping,
  Mock policy, and save lifecycle owned by the skill references.

**Non-Goals:**

- No Java parser, adapter, endpoint, or runtime behavior changes.
- No attempt to make the engine own MetricView fields such as `returnDef`,
  `status`, or `operation`.
- No support claim for operators or types that the current engine document and
  implementation do not define.

## Decisions

1. **Keep engine and MetricView contracts separate.** The engine document will
   define only the public DSL. The skill will receive a copied syntax document
   plus a binding note explaining how generated Python embeds the DSL and how
   MetricView `typeContext` maps to engine logical types. This avoids a reverse
   dependency from the public engine to an enterprise lifecycle API.

2. **Use the engine's actual vocabulary.** Generated queries use `from`,
   `select`, `joins`, `where`, `groupBy`, `orderBy`, and `limit`. The skill will
   stop presenting `sources`, `dimensions`, `measures`, `filters`, and `sort`
   as if they were engine fields.

3. **Use explicit temporal semantics.** New generated windows use `gte` on the
   start and `lt` on the end. `date`, `local_datetime`, and `instant` retain
   their documented distinctions; `datetime` remains a compatibility alias.

4. **Validate structure with JSON Schema, semantics in prose/runtime.** The
   schema will cover required envelopes, known payload keys, safe identifier
   shapes, operator branches, and basic parameter value shapes. Schema policy,
   field existence, type compatibility, limits, and authorization remain
   semantic/runtime checks.

5. **Snapshot the canonical document into the skill.** The copied reference
   will contain a provenance header with the source path and DSL version. A
   future engine protocol change must update both the source document and the
   skill snapshot in one change.

## Risks / Trade-offs

- [Risk] The JSON Schema cannot express all cross-field semantic constraints.
  → Keep the prose rules and runtime validators authoritative for joins,
  parameter references, schema policy, and adapter-specific limits.
- [Risk] The skill snapshot can drift from the engine source.
  → Add an explicit source/version header and a verification command that
  compares the copied document and schema byte-for-byte with the engine files.
- [Risk] Existing skill changes are uncommitted in the integration checkout.
  → Add only new reference artifacts where possible and preserve the existing
  working tree; integrate existing-file edits only after a clean, non-overlap
  check.

## Migration Plan

1. Update the engine syntax document and add the v1 JSON Schema.
2. Copy both artifacts into the skill references and add the skill-side binding
   note.
3. Align the skill's DSL planning/type mapping text with the copied source.
4. Run documentation/schema consistency checks and the existing local skill
   tests.
5. If a rollback is needed, remove the new references and revert only the
   documentation alignment commit; runtime code is unaffected.
