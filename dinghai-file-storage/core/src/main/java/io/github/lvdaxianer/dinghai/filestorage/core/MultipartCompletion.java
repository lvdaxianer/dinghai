package io.github.lvdaxianer.dinghai.filestorage.core;

import java.util.List;
import java.util.Objects;

/** multipart 完成请求提交的分片清单。 */
public record MultipartCompletion(List<MultipartPart> parts) {

    /** 校验并冻结分片清单。 */
    public MultipartCompletion {
        parts = List.copyOf(Objects.requireNonNull(parts, "parts"));
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("multipart completion parts must not be empty");
        }
    }
}
