package io.github.lvdaxianer.dinghai.dsl.spi;

import java.util.Objects;

/**
 * 后端命令执行或结果映射失败异常。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
public final class BackendExecutionException extends RuntimeException {

    private final String code;

    /**
     * 创建后端执行异常。
     *
     * @param code 稳定错误码
     * @param message 脱敏错误消息
     * @param cause 原始异常
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public BackendExecutionException(String code, String message, Throwable cause) {
        super(Objects.requireNonNull(message, "message"), cause);
        this.code = requireText(code, "code");
    }

    /**
     * 返回稳定错误码。
     *
     * @return 错误码
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public String code() {
        return code;
    }

    /**
     * 校验文本字段。
     *
     * @param value 文本值
     * @param name 字段名
     * @return 非空文本
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static String requireText(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }
}
