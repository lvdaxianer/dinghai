# dinghai (定海神针 — the sea-pinning needle)

> The magic staff that pins the sea — steadying your state.

English | [中文](README.md)

dinghai is lvdaxianer's personal open-source base-component library, distilled and extracted from production projects. It has zero business coupling and focuses on providing directly reusable infrastructure capabilities.

## Module List

| Module | Version | Description | Docs |
| --- | --- | --- | --- |
| `dinghai-shared-state` | 1.0.0 | Shared state cache: dual Redis/Caffeine backends, circuit-breaker fault tolerance, observability, Spring Boot 3 auto-configuration | [Docs](dinghai-shared-state/README_EN.md) |
| `dinghai-file-storage-core` | 1.0.0 | Backend-neutral storage contracts, object models, presigned models, and stable error codes | [Docs](dinghai-file-storage/README_EN.md) |
| `dinghai-file-storage-local` | 1.0.0 | Local-disk adapter with Unicode/nested keys, HMAC presigning, and streaming I/O | [Docs](dinghai-file-storage/README_EN.md) |
| `dinghai-file-storage-minio` | 1.0.0 | Fixed-bucket MinIO adapter with native presigning | [Docs](dinghai-file-storage/README_EN.md) |
| `dinghai-file-storage-spring-boot-starter` | 1.0.0 | Provider auto-configuration and local Web presigned endpoints | [Docs](dinghai-file-storage/README_EN.md) |

See each module directory's README for full documentation (Maven coordinates, configuration, usage examples, testing).

## AI Agent Skill

This repository ships with an Agent Skill file, [`.agents/skills/dinghai/SKILL.md`](.agents/skills/dinghai/SKILL.md), aimed at AI coding assistants that support the Agent Skill mechanism: it describes each module's Maven coordinates, core capabilities, operation cheat sheet, and usage patterns, so that the AI proactively suggests adopting this library for needs such as caching, shared state, session renewal, and read-through loading — instead of reinventing the wheel.

Usage:

- **Automatic discovery**: tools that support Agent Skills (such as Kimi Code) automatically scan `.agents/skills/` after cloning the repository and load the skill based on trigger words (cache, shared state, distributed counter, etc.);
- **Manual invocation**: in Kimi Code, run `/skill:dinghai` to load the Skill directly.

## Release

Day-to-day builds (`mvn test` / `mvn package`) do not trigger signing or publishing plugins. To publish to Maven Central, use the `release` profile:

```bash
mvn -Prelease clean deploy
```

Prerequisites for releasing:

- A GPG signing key is configured on the local machine (release artifacts are mandatorily signed; for local artifact verification you can skip with `-Dgpg.skip=true`);
- `settings.xml` contains a server with id `central` carrying the Central Portal user token;
- The publishing plugin is `central-publishing-maven-plugin` with `autoPublish=false`; after upload, the release must be confirmed manually in the Central Portal.

## License

This project is open-sourced under the [MIT License](LICENSE).

---

This English version mirrors the Chinese README; in case of any divergence, the Chinese version prevails.
