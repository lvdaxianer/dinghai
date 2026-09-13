package io.github.lvdaxianer.dinghai.filestorage.spring;

import java.util.Locale;
import java.util.Set;

/**
 * 判定标准响应 MIME 类型是否可绕过常规压缩而使用 sendfile。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-13
 */
final class SendfileContentTypes {
    private static final Set<String> EXACT_BINARY_TYPES = Set.of(
            "application/octet-stream", "application/pdf", "application/zip",
            "application/gzip", "application/x-tar", "application/x-7z-compressed",
            "application/x-rar-compressed", "application/x-bzip2", "application/x-xz",
            "application/zstd", "application/vnd.rar", "application/epub+zip",
            "application/font-woff", "application/x-font-ttf", "application/x-font-opentype");

    private SendfileContentTypes() { }

    /**
     * 根据原始 MIME 类型判定是否属于标准二进制 sendfile 白名单。
     *
     * @param contentType provider 确认的原始 MIME 类型
     * @return 合法且在白名单中时返回 true
     */
    static boolean isEligible(String contentType) {
        String mediaType = mediaType(contentType);
        if (mediaType == null) return false;
        if (EXACT_BINARY_TYPES.contains(mediaType)) return true;

        int separator = mediaType.indexOf('/');
        String type = mediaType.substring(0, separator);
        return ("image".equals(type) && !"image/svg+xml".equals(mediaType))
                || "video".equals(type) || "audio".equals(type) || "font".equals(type);
    }

    private static String mediaType(String contentType) {
        if (contentType == null) return null;
        int parameterStart = contentType.indexOf(';');
        String value = (parameterStart >= 0 ? contentType.substring(0, parameterStart) : contentType).trim();
        int separator = value.indexOf('/');
        if (separator <= 0 || separator != value.lastIndexOf('/') || separator == value.length() - 1) {
            return null;
        }
        String type = value.substring(0, separator);
        String subtype = value.substring(separator + 1);
        if (!isToken(type) || !isToken(subtype)) return null;
        return value.toLowerCase(Locale.ROOT);
    }

    private static boolean isToken(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!(character >= 'a' && character <= 'z')
                    && !(character >= 'A' && character <= 'Z')
                    && !(character >= '0' && character <= '9')
                    && "!#$%&'*+-.^_`|~".indexOf(character) < 0) {
                return false;
            }
        }
        return !value.isEmpty();
    }
}
