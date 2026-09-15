package io.github.lvdaxianer.dinghai.threadpool.spring;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import org.springframework.web.server.ResponseStatusException;

/**
 * 验证子控身份端点的世代暴露与访问控制。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-15
 */
public class ThreadPoolMetricControllerTest {

    /** 授权请求必须获得 SQLite 持久化的实例世代。 */
    @Test
    public void shouldExposePersistedInstanceVersion() throws Exception {
        SqliteMetricStore store = store();
        ThreadPoolMetricController controller = new ThreadPoolMetricController(store, null, () -> true);

        assertEquals(store.instanceVersion(), controller.identity().version());
    }

    /** 未授权请求不能读取实例世代。 */
    @Test
    public void shouldRejectUnauthorizedIdentityRequest() throws Exception {
        ThreadPoolMetricController controller = new ThreadPoolMetricController(store(), null, () -> false);

        assertThrows(ResponseStatusException.class, controller::identity);
    }

    private SqliteMetricStore store() throws Exception {
        Path database = Files.createTempDirectory("thread-pool-controller").resolve("metrics.db");
        return new SqliteMetricStore(database, 10);
    }
}
