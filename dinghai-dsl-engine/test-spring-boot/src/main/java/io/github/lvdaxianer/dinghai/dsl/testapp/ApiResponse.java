package io.github.lvdaxianer.dinghai.dsl.testapp;

/**
 * 测试接口统一成功响应。
 *
 * @param <T> 数据类型
 * @param code 稳定结果码
 * @param message 安全提示
 * @param data 业务数据
 * @param traceId 链路标识
 */
public record ApiResponse<T>(String code, String message, T data, String traceId) {

    /**
     * 创建成功响应。
     *
     * @param data 业务数据
     * @param traceId 链路标识
     * @param <T> 数据类型
     * @return 成功响应
     */
    public static <T> ApiResponse<T> success(T data, String traceId) {
        return new ApiResponse<>("OK", "success", data, traceId);
    }
}
