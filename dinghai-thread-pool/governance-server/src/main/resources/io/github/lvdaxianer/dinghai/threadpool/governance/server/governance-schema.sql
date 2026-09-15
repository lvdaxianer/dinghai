create table if not exists governance_instance (
    instance_id text primary key,
    base_url text not null,
    environment text not null,
    enabled integer not null,
    version text not null default '',
    updated_at integer not null
);
create table if not exists governance_cursor (
    instance_id text primary key,
    cursor text not null,
    updated_at integer not null
);
create table if not exists governance_collection_gap (
    id integer primary key autoincrement,
    instance_id text not null,
    requested_cursor text,
    oldest_cursor text,
    detected_at integer not null,
    recovery_status text not null
);
create table if not exists governance_alert_state (
    alert_key text primary key,
    status text not null,
    first_fired_at integer not null,
    last_fired_at integer not null,
    last_value text,
    resolved_at integer
);
create table if not exists governance_thread_pool_metric (
    id integer primary key autoincrement,
    instance_id text not null,
    source_metric_id integer,
    sampled_at integer not null,
    pool_name text not null,
    active integer not null,
    pool_size integer not null,
    queue_size integer not null,
    actual_maximum integer not null,
    submitted integer not null,
    completed integer not null,
    rejected integer not null,
    failed integer not null,
    wait_millis integer not null,
    execution_millis integer not null,
    priority text not null,
    reliability text not null
);
create unique index if not exists uk_governance_metric_source
    on governance_thread_pool_metric(instance_id, source_metric_id);
create index if not exists idx_governance_metric_instance_time
    on governance_thread_pool_metric(instance_id, sampled_at);
create index if not exists idx_governance_metric_instance_pool_time
    on governance_thread_pool_metric(instance_id, pool_name, sampled_at);
