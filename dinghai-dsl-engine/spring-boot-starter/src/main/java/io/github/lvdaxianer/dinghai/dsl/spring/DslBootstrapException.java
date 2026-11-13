package io.github.lvdaxianer.dinghai.dsl.spring;

/**
 * DSL 引擎启动装配失败异常。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
public final class DslBootstrapException extends IllegalStateException {

    /**
     * 创建启动装配失败异常。
     *
     * @param message 脱敏错误消息
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public DslBootstrapException(String message) {
        super(message);
    }
}
