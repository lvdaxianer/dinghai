package io.github.lvdaxianer.dinghai.threadpool.governance.server;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 总控 SQLite 线程池采样实体。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-15
 */
@TableName("governance_thread_pool_metric")
public class GovernanceThreadPoolMetricEntity {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String instanceId;
    private Long sourceMetricId;
    private Long sampledAt;
    private String poolName;
    private Integer active;
    private Integer poolSize;
    private Integer queueSize;
    private Integer actualMaximum;
    private Long submitted;
    private Long completed;
    private Long rejected;
    private Long failed;
    private Long waitMillis;
    private Long executionMillis;
    private String priority;
    private String reliability;

    /** @return 主键 */ public Long getId() { return id; }
    /** @param id 主键 */ public void setId(Long id) { this.id = id; }
    /** @return 子控实例标识 */ public String getInstanceId() { return instanceId; }
    /** @param instanceId 子控实例标识 */ public void setInstanceId(String instanceId) { this.instanceId = instanceId; }
    /** @return 子控原始指标标识 */ public Long getSourceMetricId() { return sourceMetricId; }
    /** @param sourceMetricId 子控原始指标标识 */ public void setSourceMetricId(Long sourceMetricId) { this.sourceMetricId = sourceMetricId; }
    /** @return 子控采样时刻 */ public Long getSampledAt() { return sampledAt; }
    /** @param sampledAt 子控采样时刻 */ public void setSampledAt(Long sampledAt) { this.sampledAt = sampledAt; }
    /** @return 线程池名称 */ public String getPoolName() { return poolName; }
    /** @param poolName 线程池名称 */ public void setPoolName(String poolName) { this.poolName = poolName; }
    /** @return 活跃任务数 */ public Integer getActive() { return active; }
    /** @param active 活跃任务数 */ public void setActive(Integer active) { this.active = active; }
    /** @return 当前线程数 */ public Integer getPoolSize() { return poolSize; }
    /** @param poolSize 当前线程数 */ public void setPoolSize(Integer poolSize) { this.poolSize = poolSize; }
    /** @return 队列深度 */ public Integer getQueueSize() { return queueSize; }
    /** @param queueSize 队列深度 */ public void setQueueSize(Integer queueSize) { this.queueSize = queueSize; }
    /** @return 生效最大线程数 */ public Integer getActualMaximum() { return actualMaximum; }
    /** @param actualMaximum 生效最大线程数 */ public void setActualMaximum(Integer actualMaximum) { this.actualMaximum = actualMaximum; }
    /** @return 累计提交数 */ public Long getSubmitted() { return submitted; }
    /** @param submitted 累计提交数 */ public void setSubmitted(Long submitted) { this.submitted = submitted; }
    /** @return 累计完成数 */ public Long getCompleted() { return completed; }
    /** @param completed 累计完成数 */ public void setCompleted(Long completed) { this.completed = completed; }
    /** @return 累计拒绝数 */ public Long getRejected() { return rejected; }
    /** @param rejected 累计拒绝数 */ public void setRejected(Long rejected) { this.rejected = rejected; }
    /** @return 累计失败数 */ public Long getFailed() { return failed; }
    /** @param failed 累计失败数 */ public void setFailed(Long failed) { this.failed = failed; }
    /** @return 累计等待耗时 */ public Long getWaitMillis() { return waitMillis; }
    /** @param waitMillis 累计等待耗时 */ public void setWaitMillis(Long waitMillis) { this.waitMillis = waitMillis; }
    /** @return 累计执行耗时 */ public Long getExecutionMillis() { return executionMillis; }
    /** @param executionMillis 累计执行耗时 */ public void setExecutionMillis(Long executionMillis) { this.executionMillis = executionMillis; }
    /** @return 调度优先级 */ public String getPriority() { return priority; }
    /** @param priority 调度优先级 */ public void setPriority(String priority) { this.priority = priority; }
    /** @return 可靠性策略 */ public String getReliability() { return reliability; }
    /** @param reliability 可靠性策略 */ public void setReliability(String reliability) { this.reliability = reliability; }
}
