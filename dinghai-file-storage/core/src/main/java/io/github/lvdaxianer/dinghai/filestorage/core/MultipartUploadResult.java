package io.github.lvdaxianer.dinghai.filestorage.core;

import java.util.Objects;

/** multipart 完成后的统一结果。 */
public record MultipartUploadResult(MultipartUploadSession session, ObjectMetadata metadata) {

    /** 校验完成结果。 */
    public MultipartUploadResult {
        session = Objects.requireNonNull(session, "session");
        metadata = Objects.requireNonNull(metadata, "metadata");
    }
}
