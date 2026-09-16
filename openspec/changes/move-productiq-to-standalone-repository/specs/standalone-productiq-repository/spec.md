## ADDED Requirements

### Requirement: ProductIQ SHALL be published as an independent repository
The system SHALL publish the current `dinghai-productiq/` directory to
`https://gitee.com/lvdaxianer/product-iq.git` as the root tree of the target repository's `main` branch.
The published history SHALL retain the commits that changed the ProductIQ directory, and no unrelated dinghai
component files SHALL appear in the target repository.

#### Scenario: Publish and clone the independent repository
- **WHEN** the verified ProductIQ directory is exported to an empty target repository
- **THEN** a fresh clone of target `main` has a root tree identical to the source ProductIQ directory tree and has no
  `dinghai-productiq/` wrapper directory

### Requirement: Independent repository SHALL preserve the verified delivery surface
The target repository SHALL continue to provide the backend, Chrome plugin, acceptance baseline, environment template,
and Compose configuration without behavior changes introduced by the migration.

#### Scenario: Verify the migrated delivery surface
- **WHEN** the target repository is freshly cloned after publication
- **THEN** its backend tests and package, plugin tests and check, acceptance tests and baseline runner, and Compose
  configuration validation all succeed

### Requirement: Dinghai SHALL no longer contain the ProductIQ source directory
After the target repository has passed migration verification, the current dinghai branch SHALL not contain the
top-level `dinghai-productiq/` directory. Existing dinghai history and archived OpenSpec records SHALL remain intact.

#### Scenario: Remove the source copy after target validation
- **WHEN** the target repository tree and delivery surface have been verified
- **THEN** dinghai removes only `dinghai-productiq/` through a normal reversible Git commit and leaves other modules
  unchanged
