package io.github.lvdaxianer.dinghai.projectiq.config;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * 将环境变量转换为经过校验的 ProductIQ 运行时配置。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
@Configuration
public class ProductIqRuntimeConfiguration {
    /**
     * 创建唯一的运行时配置值对象；remote 模式的错误会在这里 fail-fast。
     *
     * @param environment Spring 环境配置
     * @return 已校验的运行时配置
     */
    @Bean
    ProductIqRuntimeProperties productIqRuntimeProperties(Environment environment) {
        return new ProductIqRuntimeProperties(ProductIqRuntimeProperties.Mode.parse(text(environment,
                "productiq.runtime.mode", "deterministic")), uri(text(environment, "productiq.gateway.base-url", "")),
                text(environment, "productiq.gateway.api-key", ""),
                text(environment, "productiq.gateway.embedding-model", ""),
                text(environment, "productiq.gateway.rerank-model", ""),
                text(environment, "productiq.gateway.answer-model", ""), integer(environment,
                        "productiq.embedding.dimensions", 0),
                uri(text(environment, "productiq.qdrant.url", "")),
                text(environment, "productiq.qdrant.collection", ""), duration(environment,
                        "productiq.embedding.timeout", Duration.ofSeconds(15)), duration(environment,
                        "productiq.rerank.timeout", Duration.ofSeconds(20)), duration(environment,
                        "productiq.qdrant.timeout", Duration.ofSeconds(5)));
    }

    /**
     * 将可为空白的配置文本转换为 URI，保留后续模式校验的错误语义。
     *
     * @param value URI 配置文本
     * @return URI 的显式可选值
     */
    private static Optional<URI> uri(String value) {
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(URI.create(value.trim()));
    }

    /**
     * 读取带默认值的文本配置。
     *
     * @param environment Spring 环境配置
     * @param key 配置键
     * @param fallback 缺失时的默认值
     * @return 配置文本或默认值
     */
    private static String text(Environment environment, String key, String fallback) {
        return environment.getProperty(key, fallback);
    }

    /**
     * 读取带默认值的整数配置。
     *
     * @param environment Spring 环境配置
     * @param key 配置键
     * @param fallback 缺失时的默认值
     * @return 配置整数或默认值
     */
    private static int integer(Environment environment, String key, int fallback) {
        return environment.getProperty(key, Integer.class, fallback);
    }

    /**
     * 读取带默认值的时间配置。
     *
     * @param environment Spring 环境配置
     * @param key 配置键
     * @param fallback 缺失时的默认值
     * @return 配置时间或默认值
     */
    private static Duration duration(Environment environment, String key, Duration fallback) {
        return environment.getProperty(key, Duration.class, fallback);
    }
}
