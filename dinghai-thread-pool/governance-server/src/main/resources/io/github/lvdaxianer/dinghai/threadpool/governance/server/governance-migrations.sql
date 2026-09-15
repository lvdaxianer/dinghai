-- SQLite 的 ADD COLUMN 在既有版本字段上会报告重复列；ScriptRunner 保持继续执行。
alter table governance_instance add column version text not null default '';
