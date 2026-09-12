package io.github.lvdaxianer.dinghai.filestorage.core;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Objects;

/**
 * 已规范化且经过安全校验的对象 key。
 *
 * @param value 相对对象 key
 * @author lvdaxianer@yeah.net
 * @date 2026-09-11
 */
public record ObjectKey(String value) {

    /** 完整 key 的最大 UTF-8 字节数。 */
    public static final int MAX_UTF8_BYTES = 1024;
    /** 单个路径段的最大 UTF-8 字节数。 */
    public static final int MAX_SEGMENT_UTF8_BYTES = 255;

    /** 规范化并创建对象 key。 */
    public ObjectKey {
        value = validate(value);
    }

    /**
     * 创建对象 key。
     *
     * @param raw 原始 key，可包含 URL 百分号编码
     * @return 已验证 key
     */
    public static ObjectKey of(String raw) {
        return new ObjectKey(raw);
    }

    private static String validate(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("object key must not be null");
        }
        String decoded = decode(raw);
        String normalized = Normalizer.normalize(decoded, Normalizer.Form.NFC);
        if (normalized.isEmpty() || normalized.startsWith("/") || normalized.indexOf('\\') >= 0
                || normalized.indexOf('\0') >= 0 || hasControl(normalized)) {
            throw new IllegalArgumentException("object key is unsafe");
        }
        byte[] complete = normalized.getBytes(StandardCharsets.UTF_8);
        if (complete.length > MAX_UTF8_BYTES) {
            throw new IllegalArgumentException("object key exceeds UTF-8 byte limit");
        }
        String[] segments = normalized.split("/", -1);
        for (String segment : segments) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("object key contains unsafe path segment");
            }
            if (segment.getBytes(StandardCharsets.UTF_8).length > MAX_SEGMENT_UTF8_BYTES) {
                throw new IllegalArgumentException("object key segment exceeds UTF-8 byte limit");
            }
        }
        return normalized;
    }

    private static boolean hasControl(String value) {
        return value.codePoints().anyMatch(Character::isISOControl);
    }

    private static String decode(String raw) {
        try {
            return URLDecoder.decode(raw.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("object key encoding is invalid", exception);
        }
    }
}
