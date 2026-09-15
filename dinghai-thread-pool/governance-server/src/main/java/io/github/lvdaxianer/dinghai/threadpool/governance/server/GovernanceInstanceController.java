package io.github.lvdaxianer.dinghai.threadpool.governance.server;

import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 提供受宿主授权保护的总控实例注册和查询接口。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-15
 */
@RestController
@RequestMapping("/governance/instances")
public class GovernanceInstanceController {
    private final GovernanceStore store;
    private final GovernanceAccessPolicy accessPolicy;

    /**
     * 创建实例管理控制器。
     *
     * @param store 控制面存储
     * @param accessPolicy 宿主授权策略
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    public GovernanceInstanceController(GovernanceStore store, GovernanceAccessPolicy accessPolicy) {
        this.store = store;
        this.accessPolicy = accessPolicy;
    }

    /**
     * 注册或更新一个 HTTPS 子控实例。
     *
     * @param request 子控实例信息
     * @return 成功后的空响应
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    @PostMapping
    public ResponseEntity<Void> register(@RequestBody InstanceRegistrationRequest request) {
        rejectUnauthorizedRequest();
        validate(request);
        store.register(request.instanceId(), request.baseUrl(), request.environment());
        return ResponseEntity.noContent().build();
    }

    /**
     * 查询已注册实例。
     *
     * @return 当前实例列表
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    @GetMapping
    public List<GovernanceInstance> instances() {
        rejectUnauthorizedRequest();
        return store.instances();
    }

    /**
     * 拒绝未授权的总控管理请求。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    private void rejectUnauthorizedRequest() {
        if (!accessPolicy.isAllowed()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "governance access is denied");
        }
    }

    /**
     * 验证不可由客户端绕过的实例标识、环境和 TLS 端点约束。
     *
     * @param request 注册请求
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    private void validate(InstanceRegistrationRequest request) {
        if (request == null || isBlank(request.instanceId()) || isBlank(request.environment())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "instanceId and environment are required");
        }
        if (isInvalidHttpsUrl(request.baseUrl())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "baseUrl must be an HTTPS endpoint");
        }
    }

    /**
     * 判断 URL 是否不是可安全采集的 HTTPS 绝对地址。
     *
     * @param value 待验证 URL
     * @return 是否不合法
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    private boolean isInvalidHttpsUrl(String value) {
        try {
            URI uri = URI.create(value);
            return !"https".equalsIgnoreCase(uri.getScheme()) || isBlank(uri.getHost());
        } catch (IllegalArgumentException exception) {
            return true;
        }
    }

    /**
     * 判断字符串是否为空或仅包含空白。
     *
     * @param value 待判断值
     * @return 是否为空白
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    private boolean isBlank(String value) { return value == null || value.isBlank(); }
}
