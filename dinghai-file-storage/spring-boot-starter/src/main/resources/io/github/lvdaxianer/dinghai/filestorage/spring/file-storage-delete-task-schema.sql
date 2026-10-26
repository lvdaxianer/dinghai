CREATE TABLE IF NOT EXISTS dinghai_file_storage_delete_task (
    task_id VARCHAR(64) NOT NULL PRIMARY KEY,
    storage_alias VARCHAR(255) NOT NULL,
    object_key VARCHAR(2048) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempt_count INTEGER NOT NULL,
    next_run_at BIGINT NOT NULL,
    worker_id VARCHAR(128),
    lease_token VARCHAR(128),
    lease_until BIGINT,
    last_error TEXT,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    completed_at BIGINT,
    CONSTRAINT uk_dinghai_delete_task_idempotency UNIQUE (storage_alias, idempotency_key)
);
