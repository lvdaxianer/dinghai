package io.github.lvdaxianer.dinghai.threadpool.governance.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import io.github.lvdaxianer.dinghai.threadpool.governance.api.MetricPage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

/** 验证总控轮询器对正常与不可达子控的隔离行为。 */
public class GovernancePollerTest {

    /** 成功消费指标页后，轮询器必须持久化 cursor 并发布记录数指标。 */
    @Test
    public void shouldPersistCursorAfterSuccessfulPoll() throws Exception {
        GovernanceStore store = store();
        store.register("app-1", "https://app-1.internal", "test");
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        GovernancePoller poller = new GovernancePoller(store, new GovernanceCollector(store),
                ignored -> pageClient(new MetricPage("3", false, "1", List.of())),
                new GovernancePollingMetrics(registry), 100);

        poller.poll();

        assertEquals("3", store.cursor("app-1").orElseThrow());
        assertEquals(1D, registry.get("dinghai.thread.pool.governance.metrics.pages")
                .tag("instance", "app-1").counter().count(), 0D);
    }

    /** 一个子控不可达时，轮询器必须记录失败但不推进 cursor。 */
    @Test
    public void shouldNotAdvanceCursorWhenChildIsUnavailable() throws Exception {
        GovernanceStore store = store();
        store.register("app-1", "https://app-1.internal", "test");
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        GovernancePoller poller = new GovernancePoller(store, new GovernanceCollector(store),
                ignored -> failingClient(), new GovernancePollingMetrics(registry), 100);

        poller.poll();

        assertEquals(false, store.cursor("app-1").isPresent());
        assertEquals(1D, registry.get("dinghai.thread.pool.governance.collection.failures")
                .tag("instance", "app-1").counter().count(), 0D);
    }

    /** 子控 SQLite 世代变化时，总控必须在采集前清除旧 cursor。 */
    @Test
    public void shouldResetCursorWhenChildInstanceVersionChanges() throws Exception {
        GovernanceStore store = store();
        store.register("app-1", "https://app-1.internal", "test", "generation-1");
        store.commitCursor("app-1", "legacy-cursor");
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AtomicReference<String> requestedCursor = new AtomicReference<>();
        GovernancePoller poller = new GovernancePoller(store, new GovernanceCollector(store),
                ignored -> versionedPageClient("generation-2", new MetricPage("3", false, "1", List.of()), requestedCursor),
                new GovernancePollingMetrics(registry), 100);

        poller.poll();

        assertEquals("generation-2", store.instances().getFirst().version());
        assertEquals("3", store.cursor("app-1").orElseThrow());
        assertNull(requestedCursor.get());
    }

    private GovernanceStore store() throws Exception {
        Path database = Files.createTempDirectory("governance-poller").resolve("governance.db");
        return new GovernanceStore(database);
    }

    private ChildControllerClient pageClient(MetricPage page) {
        return new ChildControllerClient() {
            @Override public MetricPage fetch(String cursor, int limit) { return page; }
            @Override public List<java.util.Map<String, Object>> fetchSnapshot() { return List.of(); }
        };
    }

    private ChildControllerClient failingClient() {
        return new ChildControllerClient() {
            @Override public MetricPage fetch(String cursor, int limit) { throw new IllegalStateException("unavailable"); }
            @Override public List<java.util.Map<String, Object>> fetchSnapshot() { return List.of(); }
        };
    }

    private ChildControllerClient versionedPageClient(String version, MetricPage page,
                                                      AtomicReference<String> requestedCursor) {
        return new ChildControllerClient() {
            @Override public MetricPage fetch(String cursor, int limit) {
                requestedCursor.set(cursor);
                return page;
            }
            @Override public Optional<String> fetchInstanceVersion() { return Optional.of(version); }
            @Override public List<java.util.Map<String, Object>> fetchSnapshot() { return List.of(); }
        };
    }
}
