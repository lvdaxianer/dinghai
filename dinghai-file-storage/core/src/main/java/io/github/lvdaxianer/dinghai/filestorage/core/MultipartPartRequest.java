package io.github.lvdaxianer.dinghai.filestorage.core;

import java.util.Objects;

/** 指定 multipart 会话分片的请求。 */
public record MultipartPartRequest(String uploadId, int partNumber, int partCount) {

    /** 使用默认最大分片数创建请求。 */
    public MultipartPartRequest(String uploadId, int partNumber) {
        this(uploadId, partNumber, Integer.MAX_VALUE);
    }

    /** 校验会话标识和分片编号边界。 */
    public MultipartPartRequest {
        uploadId = Objects.requireNonNull(uploadId, "uploadId").trim();
        if (uploadId.isEmpty() || partCount < 1 || partNumber < 1 || partNumber > partCount) {
            throw new IllegalArgumentException("multipart part request is invalid");
        }
    }
}
