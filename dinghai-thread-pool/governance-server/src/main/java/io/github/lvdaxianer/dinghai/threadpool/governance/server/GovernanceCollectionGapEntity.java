package io.github.lvdaxianer.dinghai.threadpool.governance.server;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 总控采集缺口持久化实体。 */
@TableName("governance_collection_gap")
public class GovernanceCollectionGapEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private String instanceId;
    private String requestedCursor;
    private String oldestCursor;
    private Long detectedAt;
    private String recoveryStatus;
    /** @return 主键 */ public Long getId() { return id; }
    /** @param id 主键 */ public void setId(Long id) { this.id = id; }
    /** @return 实例标识 */ public String getInstanceId() { return instanceId; }
    /** @param instanceId 实例标识 */ public void setInstanceId(String instanceId) { this.instanceId = instanceId; }
    /** @return 请求 cursor */ public String getRequestedCursor() { return requestedCursor; }
    /** @param requestedCursor 请求 cursor */ public void setRequestedCursor(String requestedCursor) { this.requestedCursor = requestedCursor; }
    /** @return 最早可用 cursor */ public String getOldestCursor() { return oldestCursor; }
    /** @param oldestCursor 最早可用 cursor */ public void setOldestCursor(String oldestCursor) { this.oldestCursor = oldestCursor; }
    /** @return 发现时间 */ public Long getDetectedAt() { return detectedAt; }
    /** @param detectedAt 发现时间 */ public void setDetectedAt(Long detectedAt) { this.detectedAt = detectedAt; }
    /** @return 恢复状态 */ public String getRecoveryStatus() { return recoveryStatus; }
    /** @param recoveryStatus 恢复状态 */ public void setRecoveryStatus(String recoveryStatus) { this.recoveryStatus = recoveryStatus; }
}
