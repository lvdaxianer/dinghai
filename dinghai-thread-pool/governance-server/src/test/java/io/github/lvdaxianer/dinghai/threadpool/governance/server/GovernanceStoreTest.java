package io.github.lvdaxianer.dinghai.threadpool.governance.server;

import static org.junit.Assert.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import io.github.lvdaxianer.dinghai.threadpool.governance.api.MetricPage;
import org.junit.Test;

/** 验证单实例总控的 cursor 持久化。 */
public class GovernanceStoreTest {

    /** 总控重启后必须保留已提交的 cursor。 */
    @Test
    public void shouldRetainCursorAfterRestart() throws Exception {
        Path database = Files.createTempDirectory("governance-store").resolve("governance.db");
        GovernanceStore first = new GovernanceStore(database);
        first.register("app-1", "https://localhost:8080", "test");
        first.commitCursor("app-1", "12");
        GovernanceStore restarted = new GovernanceStore(database);
        assertEquals("12", restarted.cursor("app-1").orElseThrow());
    }

    /** 聚合失败时不得推进 cursor。 */
    @Test
    public void shouldNotAdvanceCursorWhenConsumerFails() throws Exception {
        Path database = Files.createTempDirectory("governance-failure").resolve("governance.db");
        GovernanceStore store = new GovernanceStore(database);
        store.register("app-1", "https://localhost:8080", "test");
        AtomicInteger calls = new AtomicInteger();
        GovernanceCollector collector = new GovernanceCollector(store);
        try {
            collector.collect("app-1", ignored -> new MetricPage("1", false, "1", List.of()),
                    ignored -> { calls.incrementAndGet(); throw new IllegalStateException("aggregate failed"); });
        } catch (IllegalStateException expected) {
            assertEquals(1, calls.get());
        }
        assertEquals(false, store.cursor("app-1").isPresent());
    }

    /** 注册实例必须在 SQLite 重启后仍可查询，供总控轮询器恢复工作。 */
    @Test
    public void shouldPersistRegisteredInstancesAcrossRestart() throws Exception {
        Path database = Files.createTempDirectory("governance-store").resolve("governance.db");
        GovernanceStore first = new GovernanceStore(database);
        first.register("app-1", "https://app-1.internal", "production");

        GovernanceStore restarted = new GovernanceStore(database);

        assertEquals(1, restarted.instances().size());
        assertEquals("app-1", restarted.instances().getFirst().instanceId());
        assertEquals("https://app-1.internal", restarted.instances().getFirst().baseUrl());
    }
}
