package io.github.lvdaxianer.dinghai.threadpool.governance.server;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 总控实例持久化实体。 */
@TableName("governance_instance")
public class GovernanceInstanceEntity {
    @TableId("instance_id") private String instanceId;
    private String baseUrl;
    private String environment;
    private Boolean enabled;
    private String version;
    private Long updatedAt;
    /** @return 实例标识 */ public String getInstanceId() { return instanceId; }
    /** @param instanceId 实例标识 */ public void setInstanceId(String instanceId) { this.instanceId = instanceId; }
    /** @return 子控地址 */ public String getBaseUrl() { return baseUrl; }
    /** @param baseUrl 子控地址 */ public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    /** @return 运行环境 */ public String getEnvironment() { return environment; }
    /** @param environment 运行环境 */ public void setEnvironment(String environment) { this.environment = environment; }
    /** @return 是否启用 */ public Boolean getEnabled() { return enabled; }
    /** @param enabled 是否启用 */ public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    /** @return 子控实例世代版本 */ public String getVersion() { return version; }
    /** @param version 子控实例世代版本 */ public void setVersion(String version) { this.version = version; }
    /** @return 更新时间 */ public Long getUpdatedAt() { return updatedAt; }
    /** @param updatedAt 更新时间 */ public void setUpdatedAt(Long updatedAt) { this.updatedAt = updatedAt; }
}
