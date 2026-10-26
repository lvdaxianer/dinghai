package io.github.lvdaxianer.dinghai.filestorage.spring;

import java.util.Optional;

/** 文件删除的异步提交与状态查询接口。 */
public interface FileStorageDeleteService {
    /** 登记一个幂等删除任务。 */
    FileStorageDeleteTaskView submit(FileStorageDeleteRequest request);

    /** 查询任务状态；不存在时返回空。 */
    Optional<FileStorageDeleteTaskView> status(String taskId);
}
