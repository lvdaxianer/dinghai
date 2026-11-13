package io.github.lvdaxianer.dinghai.dsl.testapp;

/**
 * 测试应用服务端身份或数据域缺失异常。
 */
public final class TestAuthorizationException extends RuntimeException {

    private final String code;

    /**
     * 创建安全权限异常。
     *
     * @param code 稳定错误码
     * @param message 脱敏消息
     */
    public TestAuthorizationException(String code, String message) {
        super(message);
        this.code = code;
    }

    /** @return 稳定错误码 */
    public String code() {
        return code;
    }
}
