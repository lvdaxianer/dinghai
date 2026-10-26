package io.github.lvdaxianer.dinghai.filestorage.spring;

/** 异步删除任务数据库类型。 */
public enum FileStorageDeleteDatabase {
    /** 单机 SQLite。 */
    SQLITE,
    /** 宿主应用提供的 JDBC 数据源。 */
    JDBC
}
