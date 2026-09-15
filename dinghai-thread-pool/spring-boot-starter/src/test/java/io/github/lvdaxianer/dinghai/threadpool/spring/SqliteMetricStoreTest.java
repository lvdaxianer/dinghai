package io.github.lvdaxianer.dinghai.threadpool.spring;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import io.github.lvdaxianer.dinghai.threadpool.governance.api.MetricPage;
import io.github.lvdaxianer.dinghai.threadpool.governance.api.ThreadPoolMetric;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** 验证子控 SQLite 的 cursor 分页。 */
public class SqliteMetricStoreTest {

    /** 重启后的存储必须能从上次 cursor 续拉。 */
    @Test
    public void shouldPageMetricsAcrossStoreRestart() throws Exception {
        Path database = Files.createTempDirectory("thread-pool-store").resolve("metrics.db");
        SqliteMetricStore first = new SqliteMetricStore(database, 2);
        first.append(metric("first-work-h"));
        first.append(metric("second-work-m"));
        first.append(metric("third-work-l"));
        MetricPage page = first.page(null, 2);
        assertEquals(2, page.metrics().size());
        assertTrue(page.hasMore());
        SqliteMetricStore restarted = new SqliteMetricStore(database, 2);
        MetricPage next = restarted.page(page.nextCursor(), 2);
        assertEquals(1, next.metrics().size());
        assertFalse(next.hasMore());
        assertEquals("third-work-l", next.metrics().getFirst().poolName());
    }

    private ThreadPoolMetric metric(String name) {
        return new ThreadPoolMetric(0, System.currentTimeMillis(), name, 1, 1, 0, 1, 1, 1, 0, 0, 1, 1,
                "HIGH", "FAIL_FAST");
    }
}
