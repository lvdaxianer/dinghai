package io.github.lvdaxianer.dinghai.dsl.spi;

import io.github.lvdaxianer.dinghai.dsl.core.DslRequest;
import io.github.lvdaxianer.dinghai.dsl.core.ExecutionPlan;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

/**
 * 生成不包含参数值的稳定计划摘要。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
public final class PlanFingerprint {

    private PlanFingerprint() {
    }

    /**
     * 计算请求结构摘要，参数值不会参与摘要。
     *
     * @param request DSL 请求
     * @return SHA-256 摘要
     */
    public static String request(DslRequest request) {
        String canonical = request.dslType() + "|" + request.version() + "|" + canonical(request.payload());
        return sha256(canonical);
    }

    /**
     * 计算执行计划摘要。
     *
     * @param plan 执行计划
     * @return SHA-256 摘要
     */
    public static String plan(ExecutionPlan plan) {
        String canonical = plan.planType() + "|" + plan.planVersion() + "|" + canonical(plan.metadata());
        return sha256(canonical);
    }

    private static String canonical(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            map.forEach((key, item) -> sorted.put(String.valueOf(key), item));
            return sorted.entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + canonical(entry.getValue()))
                    .collect(java.util.stream.Collectors.joining("{", "", "}"));
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(PlanFingerprint::canonical)
                    .collect(java.util.stream.Collectors.joining(",", "[", "]"));
        }
        return String.valueOf(value);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
