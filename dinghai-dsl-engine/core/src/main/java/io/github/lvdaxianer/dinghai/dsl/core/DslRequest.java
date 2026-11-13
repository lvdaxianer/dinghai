package io.github.lvdaxianer.dinghai.dsl.core;

import java.util.Map;
import java.util.Objects;

/**
 * DSL Engine 的统一请求信封。
 *
 * @param dslType DSL 类型
 * @param version DSL 版本
 * @param payload DSL 载荷
 * @param parameters 参数值
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
public record DslRequest(
        String dslType,
        int version,
        Map<String, Object> payload,
        Map<String, Object> parameters) {

    /**
     * 校验并冻结请求内容。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public DslRequest {
        dslType = requireType(dslType);
        // 版本必须显式有效，避免未知版本被当作当前版本解析。
        if (version < 1) {
            throw new IllegalArgumentException("DSL version must be positive");
        }
        payload = ImmutableValues.copyMap(Objects.requireNonNull(payload, "payload"));
        parameters = ImmutableValues.copyMap(Objects.requireNonNull(parameters, "parameters"));
    }

    /**
     * 校验 DSL 类型。
     *
     * @param type DSL 类型
     * @return 非空 DSL 类型
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static String requireType(String type) {
        String value = Objects.requireNonNull(type, "dslType").trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("dslType must not be blank");
        }
        return value;
    }
}
