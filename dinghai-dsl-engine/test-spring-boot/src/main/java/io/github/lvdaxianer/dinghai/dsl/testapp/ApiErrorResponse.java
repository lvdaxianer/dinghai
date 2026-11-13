package io.github.lvdaxianer.dinghai.dsl.testapp;

import java.util.List;

/**
 * 测试接口统一错误响应。
 *
 * @param code 稳定错误码
 * @param message 安全错误消息
 * @param details 结构化诊断
 * @param traceId 链路标识
 */
public record ApiErrorResponse(
        String code, String message, List<ErrorDetail> details, String traceId) {

    /** 错误详情。 */
    public record ErrorDetail(String field, String reason) {
    }
}
