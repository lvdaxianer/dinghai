## Context

The file-storage modules already provide Local, MinIO, Core, and Starter implementations. Local Web responses use `StreamingResponseBody`; without explicit MVC configuration Spring may use an unbounded simple async executor. The original change is archived, while its delta specifications are absent from `openspec/specs/`.

## Goals / Non-Goals

**Goals:**

- Make the delivered file-storage requirements part of the repository's main specifications.
- Bound Web streaming concurrency with a dedicated executor, a finite queue, named threads, and graceful shutdown.
- Preserve endpoint status codes, streaming semantics, cache headers, Range handling, and security defaults.

**Non-Goals:**

- No new storage provider, endpoint, or public API.
- No change to object-key validation, signing payloads, or cache policy semantics.
- No application-wide executor replacement.

## Decisions

- Use a dedicated `ThreadPoolTaskExecutor` configured through Starter auto-configuration and applied through `WebMvcConfigurer#configureAsyncSupport`. This keeps MVC streaming isolated from unrelated async work and makes pool limits explicit.
- Use finite core/max pool sizes and queue capacity, with a meaningful thread-name prefix and graceful shutdown. A bounded queue provides predictable saturation instead of unbounded memory growth.
- Keep the existing `StreamingResponseBody` contract. Replacing it with whole-body buffering would violate the streaming requirement; changing the endpoint contract would be unnecessary.
- Add the missing main specs as new capability files during spec synchronization. The archived delta is the source for existing requirements; the new delta adds only the executor constraint.

## Risks / Trade-offs

- [Risk] A finite queue can reject work during a traffic spike. -> [Mitigation] Keep the pool and queue configurable through constants for this version, expose a stable rejection behavior through the server, and avoid unbounded growth.
- [Risk] A dedicated pool consumes resources even when endpoints are unused. -> [Mitigation] Register it only when the provider is local and Web integration is enabled.
- [Risk] Spec synchronization can duplicate requirements. -> [Mitigation] Create main capability files only when absent and validate the change strictly before archive.

## Migration Plan

Deploy the Starter with the new auto-configuration. Existing endpoint URLs and configuration continue to work. Rolling back the task commit removes only the explicit executor configuration; the endpoint implementation remains compatible.

## Open Questions

None for this bounded maintenance change.
