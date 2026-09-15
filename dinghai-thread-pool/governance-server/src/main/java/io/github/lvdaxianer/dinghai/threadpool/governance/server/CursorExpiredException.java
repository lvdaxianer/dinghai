package io.github.lvdaxianer.dinghai.threadpool.governance.server;

/**
 * 表示子控已删除总控 cursor 对应的历史指标。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-15
 */
public final class CursorExpiredException extends Exception {
    private final String oldestCursor;

    /**
     * 创建带有恢复起点的 cursor 过期异常。
     *
     * @param oldestCursor 子控仍保留的最早 cursor
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    public CursorExpiredException(String oldestCursor) {
        super("CURSOR_EXPIRED");
        this.oldestCursor = oldestCursor;
    }

    /**
     * 返回子控仍可读取的最早 cursor。
     *
     * @return 最早 cursor
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    public String oldestCursor() { return oldestCursor; }
}
