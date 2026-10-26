package io.github.lvdaxianer.dinghai.filestorage.spring;

/** 文件删除执行模式。 */
public enum FileStorageDeleteMode {
    /** 保持当前同步删除。 */
    SYNC,
    /** 登记任务并异步删除。 */
    ASYNC
}
