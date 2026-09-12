package io.github.lvdaxianer.dinghai.filestorage.core;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * 后端中立的文件存储契约。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-11
 */
public interface FileStorage {

    /** 写入或覆盖对象。 */
    ObjectMetadata put(UploadRequest request);

    /** 获取对象流；对象不存在时返回空。 */
    Optional<DownloadObject> get(ObjectKey key);

    /** 获取对象元数据；对象不存在时返回空。 */
    Optional<ObjectMetadata> stat(ObjectKey key);

    /** 判断对象是否存在。 */
    boolean exists(ObjectKey key);

    /** 删除对象；对象不存在时返回 false。 */
    boolean delete(ObjectKey key);

    /** 惰性枚举当前存储中的对象元数据；调用方必须关闭返回流。 */
    Stream<ObjectMetadata> list();

    /** 生成预签名上传地址。 */
    PresignedUpload presignUpload(PresignUploadRequest request);

    /** 生成预签名下载地址。 */
    PresignedDownload presignDownload(PresignDownloadRequest request);
}
