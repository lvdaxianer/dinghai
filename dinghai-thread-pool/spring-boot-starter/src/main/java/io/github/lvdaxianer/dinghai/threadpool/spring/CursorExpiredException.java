package io.github.lvdaxianer.dinghai.threadpool.spring;

/**
 * 表示客户端 cursor 指向的指标已被保留策略清理。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-15
 */
public final class CursorExpiredException extends RuntimeException {
    private final String oldestCursor;

    /**
     * 创建携带恢复起点的过期 cursor 异常。
     *
     * @param oldestCursor 当前最早可读取的 cursor
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    public CursorExpiredException(String oldestCursor) {
        super("CURSOR_EXPIRED");
        this.oldestCursor = oldestCursor;
    }

    /**
     * 返回总控恢复采集所需的最早 cursor。
     *
     * @return 最早 cursor
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    public String oldestCursor() {
        return oldestCursor;
    }
}
