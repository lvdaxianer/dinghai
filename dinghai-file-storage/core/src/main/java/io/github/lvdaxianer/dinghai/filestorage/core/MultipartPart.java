package io.github.lvdaxianer.dinghai.filestorage.core;

import java.util.Objects;

/** 已确认的 multipart 分片。 */
public record MultipartPart(int partNumber, long size, String etag) {

    /** 校验分片编号、长度和实体标签。 */
    public MultipartPart {
        if (partNumber < 1 || size < 0) {
            throw new IllegalArgumentException("multipart part is invalid");
        }
        etag = Objects.requireNonNull(etag, "etag").trim();
        if (etag.isEmpty()) {
            throw new IllegalArgumentException("etag must not be blank");
        }
    }
}
