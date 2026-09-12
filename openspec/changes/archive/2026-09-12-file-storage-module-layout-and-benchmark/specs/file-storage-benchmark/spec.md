# file-storage-benchmark Specification

## ADDED Requirements

### Requirement: 可配置并发真实文件压测
The repository SHALL provide a benchmark entrypoint that streams a supplied source file through Local or MinIO storage using configurable positive concurrency and read rounds. It MUST upload distinct object keys, read each object concurrently, verify SHA-256 content integrity, clean created objects, and report aggregate throughput, latency percentiles, and errors without exposing credentials.

#### Scenario: Local 并发上传与读取
- **WHEN** the benchmark runs with a local provider, a valid source file, and concurrency greater than one
- **THEN** it writes to an isolated temporary root, verifies each concurrent upload and read, reports aggregate metrics, and removes temporary data

### Requirement: MinIO 冷热缓存压测
The benchmark SHALL support a MinIO cache mode that reports separate cold-read and hot-read measurements using the existing bounded disk cache. It MUST require endpoint, access key, secret key, and bucket via environment variables and MUST fail without printing secret values when any are unavailable.

#### Scenario: MinIO 缓存热读
- **WHEN** a configured MinIO benchmark completes its cold read and cache fill
- **THEN** the hot-read phase reads verified content from cache when remote metadata still matches
