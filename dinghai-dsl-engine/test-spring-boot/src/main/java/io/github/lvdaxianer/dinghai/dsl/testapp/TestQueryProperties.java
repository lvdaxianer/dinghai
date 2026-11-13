package io.github.lvdaxianer.dinghai.dsl.testapp;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 测试应用的服务端可信上下文。生产应用应由认证和数据权限系统提供同等信息。
 */
@ConfigurationProperties("dinghai.test")
public class TestQueryProperties {

    private String dataSourceId = "integration";

    private String userId = "test-user";

    private String domainId = "test-domain";

    /** @return 服务端数据源标识 */
    public String getDataSourceId() {
        return dataSourceId;
    }

    /** @param dataSourceId 服务端数据源标识 */
    public void setDataSourceId(String dataSourceId) {
        this.dataSourceId = dataSourceId;
    }

    /** @return 服务端用户标识 */
    public String getUserId() {
        return userId;
    }

    /** @param userId 服务端用户标识 */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    /** @return 服务端数据域标识 */
    public String getDomainId() {
        return domainId;
    }

    /** @param domainId 服务端数据域标识 */
    public void setDomainId(String domainId) {
        this.domainId = domainId;
    }
}
