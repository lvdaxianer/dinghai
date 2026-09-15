package io.github.lvdaxianer.dinghai.threadpool.governance.server;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 提供总控采集缺口和告警状态的受保护只读查询接口。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-15
 */
@RestController
@RequestMapping("/governance")
public class GovernanceStatusController {
    private final GovernanceStore store;
    private final GovernanceAccessPolicy accessPolicy;

    /**
     * 创建总控状态查询控制器。
     *
     * @param store 控制面 SQLite 存储
     * @param accessPolicy 宿主授权策略
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    public GovernanceStatusController(GovernanceStore store, GovernanceAccessPolicy accessPolicy) {
        this.store = store;
        this.accessPolicy = accessPolicy;
    }

    /**
     * 查询指定实例的采集缺口。
     *
     * @param instanceId 子控实例标识
     * @return 缺口列表
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    @GetMapping("/gaps")
    public List<GovernanceCollectionGap> gaps(@RequestParam String instanceId) {
        rejectUnauthorizedRequest();
        return store.gaps(instanceId);
    }

    /**
     * 查询所有持久化告警状态。
     *
     * @return 告警列表
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    @GetMapping("/alerts")
    public List<GovernanceAlertState> alerts() {
        rejectUnauthorizedRequest();
        return store.alerts();
    }

    /** 拒绝未授权的总控管理请求。 */
    private void rejectUnauthorizedRequest() {
        if (!accessPolicy.isAllowed()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "governance access is denied");
        }
    }
}
