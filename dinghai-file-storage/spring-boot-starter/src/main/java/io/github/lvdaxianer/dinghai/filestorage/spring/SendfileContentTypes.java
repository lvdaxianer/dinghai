package io.github.lvdaxianer.dinghai.filestorage.spring;

import java.util.Locale;
import java.util.Set;

/**
 * 判定响应内容类型是否属于压缩中间件不会处理的不可压缩二进制类型。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-13
 */
final class SendfileContentTypes {

    /** 不可压缩的精确内容类型（压缩包、PDF、字体、WebAssembly、通用二进制）。 */
    private static final Set<String> INCOMPRESSIBLE = Set.of(
            "application/octet-stream",
            "application/pdf",
            "application/zip",
            "application/gzip",
            "application/x-gzip",
            "application/x-tar",
            "application/x-7z-compressed",
            "application/x-rar-compressed",
            "application/vnd.rar",
            "application/x-bzip2",
            "application/x-xz",
            "application/zstd",
            "application/x-brotli",
            "application/epub+zip",
            "application/wasm",
            "application/font-woff",
            "application/x-font-ttf",
            "application/x-font-opentype",
            "application/vnd.ms-fontobject");

    /** 可整体判定为不可压缩的顶层类型。 */
    private static final Set<String> INCOMPRESSIBLE_TOP_LEVEL = Set.of(
            "video", "audio", "font");

    /** 显式排除的可压缩例外（SVG 为 XML 文本，压缩收益高）。 */
    private static final Set<String> COMPRESSIBLE_EXCEPTIONS = Set.of(
            "image/svg+xml");

    private SendfileContentTypes() { }

    /**
     * 判断内容类型是否不可压缩；空值或无法识别的类型一律返回 false（保守回退）。
     *
     * @param contentType 响应内容类型（可携带参数，如 charset）
     * @return 不可压缩二进制类型返回 true
     */
    static boolean isIncompressible(String contentType) {
        if (contentType == null) {
            return false;
        }
        final int parameterIndex = contentType.indexOf(';');
        final String base = (parameterIndex >= 0 ? contentType.substring(0, parameterIndex) : contentType)
                .trim().toLowerCase(Locale.ROOT);
        final int slashIndex = base.indexOf('/');
        if (slashIndex <= 0 || slashIndex == base.length() - 1) {
            return false;
        }
        if (COMPRESSIBLE_EXCEPTIONS.contains(base)) {
            return false;
        }
        return INCOMPRESSIBLE.contains(base)
                || INCOMPRESSIBLE_TOP_LEVEL.contains(base.substring(0, slashIndex))
                || "image".equals(base.substring(0, slashIndex));
    }
}
