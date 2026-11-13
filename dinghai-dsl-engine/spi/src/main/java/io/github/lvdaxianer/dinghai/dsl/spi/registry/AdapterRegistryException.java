package io.github.lvdaxianer.dinghai.dsl.spi.registry;

import java.util.Objects;

/**
 * 适配器注册和解析失败异常。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
public final class AdapterRegistryException extends RuntimeException {

    /** DSL 类型不存在错误码。 */
    public static final String DSL_TYPE_NOT_FOUND = "DSL_TYPE_NOT_FOUND";

    /** DSL 版本不支持错误码。 */
    public static final String DSL_VERSION_NOT_SUPPORTED = "DSL_VERSION_NOT_SUPPORTED";

    /** DSL 请求未被适配器接受错误码。 */
    public static final String DSL_REQUEST_NOT_SUPPORTED = "DSL_REQUEST_NOT_SUPPORTED";

    /** 数据适配器类型不存在错误码。 */
    public static final String DATA_ADAPTER_TYPE_NOT_FOUND = "DATA_ADAPTER_TYPE_NOT_FOUND";

    /** 数据适配器不支持计划类型错误码。 */
    public static final String DATA_ADAPTER_PLAN_NOT_SUPPORTED = "DATA_ADAPTER_PLAN_NOT_SUPPORTED";

    /** 适配器重复注册错误码。 */
    public static final String DUPLICATE_REGISTRATION = "DUPLICATE_REGISTRATION";

    /** 适配器声明非法错误码。 */
    public static final String INVALID_ADAPTER = "INVALID_ADAPTER";

    private final String code;

    /**
     * 创建适配器注册异常。
     *
     * @param code 稳定错误码
     * @param message 脱敏异常消息
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public AdapterRegistryException(String code, String message) {
        super(Objects.requireNonNull(message, "message"));
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
     * @return 非空文本值
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
