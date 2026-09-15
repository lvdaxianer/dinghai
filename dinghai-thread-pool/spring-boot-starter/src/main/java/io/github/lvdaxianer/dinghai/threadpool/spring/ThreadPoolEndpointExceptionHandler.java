package io.github.lvdaxianer.dinghai.threadpool.spring;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 将子控采集协议异常映射为稳定的 HTTP 响应。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-15
 */
@RestControllerAdvice(assignableTypes = ThreadPoolMetricController.class)
public final class ThreadPoolEndpointExceptionHandler {
    /**
     * 返回总控可识别的过期 cursor 信息。
     *
     * @param exception 过期 cursor 异常
     * @return 409 协议错误
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    @ExceptionHandler(CursorExpiredException.class)
    public ResponseEntity<Map<String, String>> handleCursorExpired(CursorExpiredException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "code", "CURSOR_EXPIRED", "oldestCursor", exception.oldestCursor()));
    }
}
