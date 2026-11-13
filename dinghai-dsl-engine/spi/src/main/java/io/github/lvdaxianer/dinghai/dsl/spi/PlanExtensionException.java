package io.github.lvdaxianer.dinghai.dsl.spi;

import java.util.Objects;

/**
 * 计划扩展失败异常，向调用方提供稳定错误码。
 */
public final class PlanExtensionException extends RuntimeException {

    private final String code;

    /**
     * 创建扩展异常。
     *
     * @param code 稳定错误码
     * @param message 安全错误消息
     */
    public PlanExtensionException(String code, String message) {
        super(message);
        this.code = requireText(code, "code");
    }

    /**
     * 创建带原因的扩展异常。
     *
     * @param code 稳定错误码
     * @param message 安全错误消息
     * @param cause 原始异常
     */
    public PlanExtensionException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = requireText(code, "code");
    }

    /**
     * 返回稳定错误码。
     *
     * @return 错误码
     */
    public String code() {
        return code;
    }

    private static String requireText(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }
}
