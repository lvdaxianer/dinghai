package io.github.lvdaxianer.dinghai.threadpool.governance.server;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 提供总控 SQLite 历史线程池指标的受保护只读查询接口。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-15
 */
@RestController
@RequestMapping("/governance/metrics")
public class GovernanceMetricsController {
    private final GovernanceStore store;
    private final GovernanceAccessPolicy accessPolicy;

    /**
     * 创建历史指标控制器。
     *
     * @param store 总控 SQLite 存储
     * @param accessPolicy 宿主授权策略
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    public GovernanceMetricsController(GovernanceStore store, GovernanceAccessPolicy accessPolicy) {
        this.store = store;
        this.accessPolicy = accessPolicy;
    }

    /**
     * 查询已持久化的采样指标。
     *
     * @param instanceId 可选子控实例筛选
     * @param poolName 可选线程池筛选
     * @param from 起始采样时间戳，包含边界
     * @param to 结束采样时间戳，包含边界
     * @return 升序历史指标
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    @GetMapping
    public List<GovernanceThreadPoolMetric> metrics(@RequestParam(required = false) String instanceId,
                                                     @RequestParam(required = false) String poolName,
                                                     @RequestParam(defaultValue = "0") long from,
                                                     @RequestParam(defaultValue = "9223372036854775807") long to) {
        rejectUnauthorizedRequest();
        try {
            return store.metrics(instanceId, poolName, from, to);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    /** 拒绝未授权的总控指标请求。 */
    private void rejectUnauthorizedRequest() {
        if (!accessPolicy.isAllowed()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "governance access is denied");
        }
    }
}
