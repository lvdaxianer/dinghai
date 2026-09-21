## ADDED Requirements

### Requirement: OBS provider exposes the common object contract
The OBS provider SHALL implement the existing `FileStorage` contract for a pre-created fixed bucket and SHALL map upload, download, metadata, existence, deletion, and lazy paginated listing to OBS operations without creating or modifying the bucket.

#### Scenario: Upload and read an object
- **WHEN** a caller uploads an object with a valid object key and then reads it
- **THEN** the provider stores the content and returns equivalent key, size, content type, ETag, last-modified, and cache-control metadata

#### Scenario: Missing object is read safely
- **WHEN** a caller reads or stats an object that OBS reports as missing
- **THEN** the provider returns an empty result and does not expose the provider exception

#### Scenario: Listing is paginated and lazy
- **WHEN** a caller consumes the provider listing stream
- **THEN** the provider requests OBS pages only as needed, emits object metadata, and closes the underlying response resources when the stream is closed

### Requirement: OBS provider supports native presigned access
The OBS provider SHALL generate native PUT upload and GET download presigned requests bound to the configured bucket and object key, preserving content type, cache-control, disposition, expiration, and any required temporary-credential signed headers.

#### Scenario: Presigned upload is bound to request constraints
- **WHEN** a caller requests an upload signature for a key, content type, cache policy, and expiration
- **THEN** the provider returns a PUT request whose URL and signed headers are valid only for those OBS request constraints until expiration

#### Scenario: Presigned download carries response behavior
- **WHEN** a caller requests a download signature with attachment or inline disposition
- **THEN** the provider returns a GET request whose OBS response parameters preserve the requested disposition and cache-control policy

### Requirement: OBS provider supports long-lived and temporary credentials
The provider SHALL support long-lived AK/SK credentials, STS AK/SK/Security Token credentials, and an application-supplied refreshable credentials Provider; credentials and security tokens MUST NOT be included in logs or mapped exception messages.

#### Scenario: Static credentials initialize the client
- **WHEN** the provider is configured with non-blank endpoint, bucket, and AK/SK
- **THEN** startup creates an OBS client using those credentials and configured network timeouts

#### Scenario: Temporary credentials initialize the client
- **WHEN** the provider is configured with AK, SK, and Security Token
- **THEN** OBS API requests and presigned requests include the temporary credential security context

#### Scenario: Refresh failure is not silently downgraded
- **WHEN** a refreshable credentials Provider cannot obtain valid credentials
- **THEN** the storage operation fails with a stable authentication or remote-storage error and does not retry with expired credentials

### Requirement: OBS provider supports multipart large-file lifecycle
The provider SHALL implement `MultipartFileStorage` using OBS multipart initiate, upload part, list parts, complete, and abort operations, and SHALL support both server-side streaming part uploads and presigned browser part uploads.

#### Scenario: Create and complete multipart upload
- **WHEN** a caller initiates a session, uploads one or more parts, and submits their part numbers and ETags
- **THEN** the provider completes the OBS upload and returns confirmed final object metadata

#### Scenario: Resume from uploaded parts
- **WHEN** a caller lists parts for an existing upload session
- **THEN** the provider returns the committed parts sorted by part number with their sizes and ETags

#### Scenario: Abort is idempotent
- **WHEN** a caller aborts an existing or already missing multipart session
- **THEN** the provider leaves no active upload for that session and treats a missing session as successful cleanup

### Requirement: Spring Boot selects and validates the OBS provider
The starter SHALL recognize `provider=huaweicloud-obs`, bind endpoint, region, bucket, timeout, and credential configuration, fail fast for missing or invalid required values, and expose the adapter as the application's `FileStorage` bean without automatic provider fallback.

#### Scenario: Valid OBS configuration is auto-configured
- **WHEN** the application selects `huaweicloud-obs` and supplies valid endpoint, region, bucket, and credentials
- **THEN** the starter creates one OBS-backed `FileStorage` bean and exposes multipart capability when requested by the application

#### Scenario: Invalid OBS configuration fails startup
- **WHEN** the selected provider lacks a required endpoint, region, bucket, credential, or positive timeout
- **THEN** startup fails with a configuration validation error that does not reveal secret values

#### Scenario: Other providers remain unchanged
- **WHEN** the application selects local, minio, rustfs, or aliyun-oss
- **THEN** the starter uses the existing provider path and does not initialize an OBS client
