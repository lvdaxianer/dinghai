-- SQLite 的 ADD COLUMN 在既有版本字段上会报告重复列；ScriptRunner 保持继续执行。
alter table governance_instance add column version text not null default '';
alter table governance_thread_pool_metric add column source_metric_id integer not null default 0;
create unique index if not exists uk_governance_metric_source
    on governance_thread_pool_metric(instance_id, source_metric_id);
