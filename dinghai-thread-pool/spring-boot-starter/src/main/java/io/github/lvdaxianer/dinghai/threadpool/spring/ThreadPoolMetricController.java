package io.github.lvdaxianer.dinghai.threadpool.spring;

import io.github.lvdaxianer.dinghai.threadpool.governance.api.MetricPage;
import io.github.lvdaxianer.dinghai.threadpool.core.ThreadPoolGovernor;
import io.github.lvdaxianer.dinghai.threadpool.core.ThreadPoolSnapshot;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/**
 * 子控的只读指标采集端点。
 *
 * 部署方必须在 Spring Security 或网关层为该路径配置服务身份认证。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-15
 */
@RestController
@RequestMapping("/actuator/dinghai-thread-pool")
public class ThreadPoolMetricController {
    private final SqliteMetricStore store;
    private final ThreadPoolGovernor governor;
    private final ThreadPoolMetricsAccessPolicy accessPolicy;

    /** 创建指标查询控制器。 */
    public ThreadPoolMetricController(SqliteMetricStore store, ThreadPoolGovernor governor,
                                      ThreadPoolMetricsAccessPolicy accessPolicy) {
        this.store = store;
        this.governor = governor;
        this.accessPolicy = accessPolicy;
    }

    /** 返回一个受限大小的 cursor 指标页。 */
    @GetMapping("/metrics")
    public MetricPage metrics(@RequestParam(required = false) String cursor,
                              @RequestParam(defaultValue = "1000") int limit) {
        rejectUnauthorizedRequest();
        return store.page(cursor, limit);
    }

    /** 返回全部受管池的当前快照，供 cursor 过期后的总控恢复。 */
    @GetMapping("/snapshot")
    public List<ThreadPoolSnapshot> snapshot() {
        rejectUnauthorizedRequest();
        return governor.snapshots();
    }

    private void rejectUnauthorizedRequest() {
        if (!accessPolicy.isAllowed()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "thread pool metrics access is denied");
        }
    }
}
