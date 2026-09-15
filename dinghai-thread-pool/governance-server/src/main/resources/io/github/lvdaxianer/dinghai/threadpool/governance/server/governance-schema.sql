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
