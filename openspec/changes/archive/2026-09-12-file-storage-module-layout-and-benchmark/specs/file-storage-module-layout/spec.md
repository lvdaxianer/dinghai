# file-storage-module-layout Specification

## ADDED Requirements

### Requirement: 文件存储模块目录聚合
The Maven reactor SHALL locate Core, Local, MinIO, and Spring Boot Starter modules beneath `dinghai-file-storage/`, while preserving each module's artifactId, Java packages, dependencies, and public API.

#### Scenario: Reactor 通过新目录构建
- **WHEN** Maven builds from the repository root
- **THEN** all four file storage modules resolve from their grouped paths and the full reactor tests run successfully
