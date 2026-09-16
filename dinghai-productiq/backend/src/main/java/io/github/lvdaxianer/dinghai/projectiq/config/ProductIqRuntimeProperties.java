package io.github.lvdaxianer.dinghai.projectiq.config;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/**
 * ProductIQ 运行时依赖的显式配置，避免根据密钥是否存在猜测运行模式。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
public record ProductIqRuntimeProperties(
        Mode mode,
        Optional<URI> gatewayBaseUri,
        String gatewayApiKey,
        String embeddingModel,
        String rerankModel,
        String answerModel,
        int embeddingDimensions,
        Optional<URI> qdrantUri,
        String qdrantCollection,
        Duration embeddingTimeout,
        Duration rerankTimeout,
        Duration qdrantTimeout) {

    /** 支持的依赖装配模式。 */
    public enum Mode {
        /** 使用可重复的本地适配器。 */
        DETERMINISTIC,
        /** 使用部署侧模型网关和 Qdrant。 */
        REMOTE;

        /**
         * 从配置值解析运行模式。
         *
         * @param value 配置文本
         * @return 已识别的运行模式
         */
        public static Mode parse(String value) {
            try {
                return Mode.valueOf(value == null ? "deterministic" : value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("productiq.runtime.mode 仅支持 deterministic 或 remote", exception);
            }
        }
    }

    /**
     * 创建后校验模式与远程部署所需的依赖边界。
     */
    public ProductIqRuntimeProperties {
        mode = mode == null ? Mode.DETERMINISTIC : mode;
        gatewayBaseUri = gatewayBaseUri == null ? Optional.empty() : gatewayBaseUri;
        qdrantUri = qdrantUri == null ? Optional.empty() : qdrantUri;
        embeddingTimeout = requirePositive(embeddingTimeout, "Embedding timeout");
        rerankTimeout = requirePositive(rerankTimeout, "Rerank timeout");
        qdrantTimeout = requirePositive(qdrantTimeout, "Qdrant timeout");
        if (mode == Mode.REMOTE) {
            validateRemote(gatewayBaseUri, gatewayApiKey, embeddingModel, rerankModel, answerModel,
                    embeddingDimensions, qdrantUri, qdrantCollection);
        }
    }

    /**
     * 验证 remote 模式不能使用空白或非 HTTP 的部署配置。
     */
    private static void validateRemote(Optional<URI> gatewayBaseUri, String gatewayApiKey, String embeddingModel,
                                       String rerankModel, String answerModel, int embeddingDimensions,
                                       Optional<URI> qdrantUri, String qdrantCollection) {
        requireHttp(gatewayBaseUri, "gateway base URL");
        requireText(gatewayApiKey, "gateway API key");
        requireText(embeddingModel, "embedding model");
        requireText(rerankModel, "rerank model");
        requireText(answerModel, "answer model");
        requireHttp(qdrantUri, "Qdrant URL");
        requireText(qdrantCollection, "Qdrant collection");
        if (embeddingDimensions <= 0) {
            throw new IllegalArgumentException("embedding dimensions 必须为正数");
        }
    }

    /**
     * 约束一个外部 URI 必须为 HTTP(S)。
     *
     * @param uri 待校验 URI
     * @param name 配置名称
     */
    private static void requireHttp(Optional<URI> uri, String name) {
        if (uri.isEmpty() || !isHttp(uri.get())) {
            throw new IllegalArgumentException(name + " 必须是 HTTP(S) URL");
        }
    }

    /**
     * 判断 URI 是否使用可调用的 HTTP(S) 协议。
     *
     * @param uri 待检查地址
     * @return 是否可调用
     */
    private static boolean isHttp(URI uri) {
        return uri.getScheme() != null && ("http".equalsIgnoreCase(uri.getScheme())
                || "https".equalsIgnoreCase(uri.getScheme()));
    }

    /**
     * 约束文本配置不是空白。
     *
     * @param value 待校验文本
     * @param name 配置名称
     */
    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
    }

    /**
     * 约束超时为正数。
     *
     * @param timeout 待校验超时
     * @param name 配置名称
     * @return 已校验超时
     */
    private static Duration requirePositive(Duration timeout, String name) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException(name + " 必须为正数");
        }
        return timeout;
    }
}
