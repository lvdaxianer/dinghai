package io.github.lvdaxianer.dinghai.threadpool.spring;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

import io.github.lvdaxianer.dinghai.threadpool.governance.api.MetricPage;
import io.github.lvdaxianer.dinghai.threadpool.governance.api.ThreadPoolMetric;
import io.github.lvdaxianer.dinghai.threadpool.core.ThreadPoolDefinition;
import io.github.lvdaxianer.dinghai.threadpool.core.ThreadPoolGovernor;
import java.time.Duration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.Test;

/** 验证子控 SQLite 的 cursor 分页。 */
public class SqliteMetricStoreTest {

    private static final int CONCURRENT_METRIC_COUNT = 100;

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

    /** 同一子控 SQLite 重启后必须保留实例世代标识。 */
    @Test
    public void shouldRetainInstanceVersionAcrossStoreRestart() throws Exception {
        Path database = Files.createTempDirectory("thread-pool-store").resolve("metrics.db");
        String version = new SqliteMetricStore(database, 10).instanceVersion();

        assertEquals(version, new SqliteMetricStore(database, 10).instanceVersion());
    }

    /** 保留清理产生数据缺口时，旧 cursor 不能静默跳过缺失指标。 */
    @Test
    public void shouldRejectExpiredCursorAfterRetentionPurge() throws Exception {
        Path database = Files.createTempDirectory("thread-pool-store").resolve("metrics.db");
        SqliteMetricStore store = new SqliteMetricStore(database, 10);
        store.append(metric("expired-work-h", 0));
        store.append(metric("expired-work-h", 0));
        String expiredCursor = store.page(null, 1).nextCursor();
        store.purge(Duration.ofMillis(1));
        store.append(metric("expired-work-h", System.currentTimeMillis()));

        CursorExpiredException exception = assertThrows(CursorExpiredException.class,
                () -> store.page(expiredCursor, 10));

        assertFalse(exception.oldestCursor().matches("\\d+"));
        assertThrows(IllegalArgumentException.class, () -> store.page("1", 10));
    }

    /** 采样器必须持久化受控执行器的真实任务计数。 */
    @Test
    public void shouldPersistManagedExecutorCounters() throws Exception {
        Path database = Files.createTempDirectory("thread-pool-store").resolve("metrics.db");
        SqliteMetricStore store = new SqliteMetricStore(database, 10);
        try (ThreadPoolGovernor governor = new ThreadPoolGovernor(1)) {
            governor.register(new ThreadPoolDefinition("sample-work-h", 1, 1, 1,
                    ThreadPoolDefinition.Priority.HIGH, ThreadPoolDefinition.Reliability.FAIL_FAST, 10));
            CountDownLatch completed = new CountDownLatch(1);
            governor.executor("sample-work-h").execute(completed::countDown);
            assertTrue(completed.await(5, TimeUnit.SECONDS));

            new ThreadPoolMetricsSampler(governor, store, Duration.ofHours(1)).sample();

            ThreadPoolMetric metric = store.page(null, 10).metrics().getFirst();
            assertEquals(1, metric.submitted());
            assertEquals(1, metric.completed());
        }
    }

    /** Micrometer 绑定器必须从运行快照读取最新受管池指标。 */
    @Test
    public void shouldBindManagedPoolMetrics() {
        try (ThreadPoolGovernor governor = new ThreadPoolGovernor(1)) {
            governor.register(new ThreadPoolDefinition("meter-work-h", 1, 1, 1,
                    ThreadPoolDefinition.Priority.HIGH, ThreadPoolDefinition.Reliability.FAIL_FAST, 10));
            SimpleMeterRegistry registry = new SimpleMeterRegistry();
            new ThreadPoolMeterBinder(governor).bindTo(registry);

            assertEquals(0D, registry.get("dinghai.thread.pool.active").tag("pool", "meter-work-h")
                    .gauge().value(), 0D);
        }
    }

    /** SQLite 指标持久化必须委托 MyBatis-Plus，禁止在存储类中直接使用 JDBC。 */
    @Test
    public void shouldDelegateSqlitePersistenceToMybatisPlus() throws Exception {
        Path source = Path.of("src/main/java/io/github/lvdaxianer/dinghai/threadpool/spring/SqliteMetricStore.java");
        String content = Files.readString(source);

        assertFalse(content.contains("java.sql."));
        assertFalse(content.contains("DriverManager"));
        assertFalse(content.contains("PreparedStatement"));
    }

    /** SQLite 在 WAL 模式下必须允许指标写入与分页读取并发执行。 */
    @Test
    public void shouldReadAndWriteMetricsConcurrently() throws Exception {
        Path database = Files.createTempDirectory("thread-pool-store").resolve("metrics.db");
        SqliteMetricStore store = new SqliteMetricStore(database, 10);
        CountDownLatch started = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Void> writer = executor.submit(metricWriter(store, started));
            Future<Void> reader = executor.submit(metricReader(store, started));
            writer.get(10, TimeUnit.SECONDS);
            reader.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertEquals(CONCURRENT_METRIC_COUNT, allMetrics(store).size());
    }

    /** 返回持续写入固定数量指标的并发任务。 */
    private Callable<Void> metricWriter(SqliteMetricStore store, CountDownLatch started) {
        return () -> {
            started.countDown();
            started.await(5, TimeUnit.SECONDS);
            for (int index = 0; index < CONCURRENT_METRIC_COUNT; index++) {
                store.append(metric("concurrent-work-h"));
            }
            return null;
        };
    }

    /** 返回在写入期间持续读取分页数据的并发任务。 */
    private Callable<Void> metricReader(SqliteMetricStore store, CountDownLatch started) {
        return () -> {
            started.countDown();
            started.await(5, TimeUnit.SECONDS);
            for (int index = 0; index < CONCURRENT_METRIC_COUNT; index++) {
                store.page(null, 10);
            }
            return null;
        };
    }

    /** 读取全部指标以验证并发写入没有丢失。 */
    private List<ThreadPoolMetric> allMetrics(SqliteMetricStore store) {
        List<ThreadPoolMetric> metrics = new ArrayList<>(CONCURRENT_METRIC_COUNT);
        MetricPage page = store.page(null, 10);
        while (true) {
            metrics.addAll(page.metrics());
            if (!page.hasMore()) {
                return metrics;
            }
            page = store.page(page.nextCursor(), 10);
        }
    }

    private ThreadPoolMetric metric(String name) {
        return metric(name, System.currentTimeMillis());
    }

    private ThreadPoolMetric metric(String name, long sampledAt) {
        return new ThreadPoolMetric(0, sampledAt, name, 1, 1, 0, 1, 1, 1, 0, 0, 1, 1,
                "HIGH", "FAIL_FAST");
    }
}
