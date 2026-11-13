package io.github.lvdaxianer.dinghai.dsl.core;

import java.util.Map;
import java.util.Objects;

/**
 * DSL 编译阶段使用的服务端上下文。
 *
 * @param traceId 链路标识
 * @param attributes 由宿主应用注入的非敏感上下文属性
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
public record DslContext(String traceId, Map<String, String> attributes) {

    /**
     * 校验并冻结上下文。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public DslContext {
        traceId = requireTraceId(traceId);
        attributes = Map.copyOf(Objects.requireNonNull(attributes, "attributes"));
    }

    /**
     * 校验链路标识。
     *
     * @param value 链路标识
     * @return 非空链路标识
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static String requireTraceId(String value) {
        String traceIdValue = Objects.requireNonNull(value, "traceId").trim();
        // 链路标识为空时无法关联编译日志和审计事件。
        if (traceIdValue.isEmpty()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }
        return traceIdValue;
    }
}
