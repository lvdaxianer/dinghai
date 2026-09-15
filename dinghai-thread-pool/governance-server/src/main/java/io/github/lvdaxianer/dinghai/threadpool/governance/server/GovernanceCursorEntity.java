package io.github.lvdaxianer.dinghai.threadpool.governance.server;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 总控 cursor 持久化实体。 */
@TableName("governance_cursor")
public class GovernanceCursorEntity {
    @TableId("instance_id") private String instanceId;
    private String cursor;
    private Long updatedAt;
    /** @return 实例标识 */ public String getInstanceId() { return instanceId; }
    /** @param instanceId 实例标识 */ public void setInstanceId(String instanceId) { this.instanceId = instanceId; }
    /** @return 签名 cursor */ public String getCursor() { return cursor; }
    /** @param cursor 签名 cursor */ public void setCursor(String cursor) { this.cursor = cursor; }
    /** @return 更新时间 */ public Long getUpdatedAt() { return updatedAt; }
    /** @param updatedAt 更新时间 */ public void setUpdatedAt(Long updatedAt) { this.updatedAt = updatedAt; }
}
