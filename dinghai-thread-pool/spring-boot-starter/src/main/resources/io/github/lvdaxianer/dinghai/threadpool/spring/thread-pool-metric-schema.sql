create table if not exists thread_pool_metric (
    metric_id integer primary key autoincrement,
    sampled_at integer not null,
    pool_name text not null,
    active integer not null,
    pool_size integer not null,
    queue_size integer not null,
    actual_max integer not null,
    submitted integer not null,
    completed integer not null,
    rejected integer not null,
    failed integer not null,
    wait_ms integer not null,
    execution_ms integer not null,
    priority text not null,
    reliability text not null
);
create table if not exists thread_pool_metric_metadata (
    meta_key text primary key,
    meta_value text not null
);
