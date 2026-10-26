package io.github.lvdaxianer.dinghai.filestorage.spring;

import io.github.lvdaxianer.dinghai.filestorage.core.ObjectKey;
import java.util.Objects;

/** 异步删除提交请求。 */
public record FileStorageDeleteRequest(ObjectKey key, String idempotencyKey) {
    /** 校验删除请求的必要字段。 */
    public FileStorageDeleteRequest {
        Objects.requireNonNull(key, "key");
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotency key must not be blank");
        }
    }
}
