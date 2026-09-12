package io.github.lvdaxianer.dinghai.filestorage.core;

import java.util.Objects;

/**
 * HTTP 缓存策略值对象。
 *
 * @param value Cache-Control 头值
 * @author lvdaxianer@yeah.net
 * @date 2026-09-11
 */
public record CacheControl(String value) {

    /** 创建并校验缓存策略。 */
    public CacheControl {
        value = Objects.requireNonNull(value, "value").trim();
        if (value.isEmpty() || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("cache control must not be blank or contain CR/LF");
        }
    }

    /** 私有且默认不缓存策略。 */
    public static CacheControl privateNoCache() {
        return new CacheControl("private, max-age=0");
    }
}
