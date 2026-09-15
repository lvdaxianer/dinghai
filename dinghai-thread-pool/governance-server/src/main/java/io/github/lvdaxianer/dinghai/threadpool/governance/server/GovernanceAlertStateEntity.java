package io.github.lvdaxianer.dinghai.threadpool.governance.server;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 总控告警状态持久化实体。 */
@TableName("governance_alert_state")
public class GovernanceAlertStateEntity {
    @TableId("alert_key") private String alertKey;
    private String status;
    private Long firstFiredAt;
    private Long lastFiredAt;
    private String lastValue;
    private Long resolvedAt;
    /** @return 告警键 */ public String getAlertKey() { return alertKey; }
    /** @param alertKey 告警键 */ public void setAlertKey(String alertKey) { this.alertKey = alertKey; }
    /** @return 告警状态 */ public String getStatus() { return status; }
    /** @param status 告警状态 */ public void setStatus(String status) { this.status = status; }
    /** @return 首次触发时间 */ public Long getFirstFiredAt() { return firstFiredAt; }
    /** @param firstFiredAt 首次触发时间 */ public void setFirstFiredAt(Long firstFiredAt) { this.firstFiredAt = firstFiredAt; }
    /** @return 最近触发时间 */ public Long getLastFiredAt() { return lastFiredAt; }
    /** @param lastFiredAt 最近触发时间 */ public void setLastFiredAt(Long lastFiredAt) { this.lastFiredAt = lastFiredAt; }
    /** @return 最后安全摘要 */ public String getLastValue() { return lastValue; }
    /** @param lastValue 最后安全摘要 */ public void setLastValue(String lastValue) { this.lastValue = lastValue; }
    /** @return 解决时间 */ public Long getResolvedAt() { return resolvedAt; }
    /** @param resolvedAt 解决时间 */ public void setResolvedAt(Long resolvedAt) { this.resolvedAt = resolvedAt; }
}
