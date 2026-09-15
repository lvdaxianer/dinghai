package io.github.lvdaxianer.dinghai.filestorage.core;

import java.io.InputStream;
import java.util.List;

/** 可选的大文件分片上传能力。 */
public interface MultipartFileStorage {

    /** 创建分片上传会话。 */
    MultipartUploadSession initiate(MultipartUploadRequest request);

    /** 生成指定分片的上传授权。 */
    PresignedUpload presignPart(MultipartUploadSession session, MultipartPartRequest request);

    /** 上传一个分片，Local provider 使用该方法接收组件内分片请求。 */
    MultipartPart uploadPart(MultipartUploadSession session, MultipartPartRequest request,
                             InputStream content, long contentLength);

    /** 查询对象存储中已确认的分片。 */
    List<MultipartPart> listParts(MultipartUploadSession session);

    /** 按指定分片清单完成上传。 */
    ObjectMetadata complete(MultipartUploadSession session, MultipartCompletion completion);

    /** 取消分片上传，会话不存在时保持幂等。 */
    void abort(MultipartUploadSession session);
}
