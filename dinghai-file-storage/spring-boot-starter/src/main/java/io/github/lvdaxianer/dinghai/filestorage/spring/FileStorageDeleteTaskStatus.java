package io.github.lvdaxianer.dinghai.filestorage.spring;

/** 删除任务生命周期状态。 */
public enum FileStorageDeleteTaskStatus {
    /** 已登记，等待领取。 */
    PENDING,
    /** 已被 Worker 租约领取。 */
    RUNNING,
    /** 本次失败，等待下次重试。 */
    RETRY_WAIT,
    /** 删除完成。 */
    SUCCESS,
    /** 超过最大尝试次数。 */
    FAILED
}
