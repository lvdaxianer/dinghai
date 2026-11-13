package io.github.lvdaxianer.dinghai.dsl.spi;

import java.util.Objects;

/**
 * DSL 执行边界拒绝异常。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
public final class ExecutionLimitException extends RuntimeException {

    private final String code;

    /**
     * 创建执行边界异常。
     *
     * @param code 稳定错误码
     * @param message 脱敏错误消息
     */
    public ExecutionLimitException(String code, String message) {
        super(Objects.requireNonNull(message, "message"));
        this.code = requireText(code);
    }

    /**
     * 返回稳定错误码。
     *
     * @return 错误码
     */
    public String code() {
        return code;
    }

    private static String requireText(String value) {
        String normalized = Objects.requireNonNull(value, "code").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        return normalized;
    }
}
