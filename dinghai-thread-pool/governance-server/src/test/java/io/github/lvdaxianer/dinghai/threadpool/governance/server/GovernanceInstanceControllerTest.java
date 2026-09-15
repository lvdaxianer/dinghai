package io.github.lvdaxianer.dinghai.threadpool.governance.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** 验证总控实例管理接口的授权与输入边界。 */
public class GovernanceInstanceControllerTest {

    /** 默认策略必须拒绝读取总控实例。 */
    @Test
    public void shouldRejectUnauthorizedRequests() throws Exception {
        GovernanceInstanceController controller = controller(() -> false);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, controller::instances);

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    /** 仅 HTTPS 子控端点可以写入总控注册表。 */
    @Test
    public void shouldRejectInsecureInstanceUrl() throws Exception {
        GovernanceInstanceController controller = controller(() -> true);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller.register(new InstanceRegistrationRequest("app-1", "http://localhost", "test")));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    /** 授权请求能够注册并读取 HTTPS 子控。 */
    @Test
    public void shouldRegisterAuthorizedHttpsInstance() throws Exception {
        GovernanceInstanceController controller = controller(() -> true);
        controller.register(new InstanceRegistrationRequest("app-1", "https://localhost", "test"));

        assertEquals(1, controller.instances().size());
    }

    /** 总控缺口与告警查询同样必须使用默认拒绝授权边界。 */
    @Test
    public void shouldRejectUnauthorizedStatusQueries() throws Exception {
        Path database = Files.createTempDirectory("governance-status").resolve("governance.db");
        GovernanceStatusController controller = new GovernanceStatusController(new GovernanceStore(database), () -> false);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                controller::alerts);

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    private GovernanceInstanceController controller(GovernanceAccessPolicy policy) throws Exception {
        Path database = Files.createTempDirectory("governance-controller").resolve("governance.db");
        return new GovernanceInstanceController(new GovernanceStore(database), policy);
    }
}
