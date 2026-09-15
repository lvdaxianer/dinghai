package io.github.lvdaxianer.dinghai.threadpool.spring;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 子控指标的 MyBatis-Plus 持久化实体。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-15
 */
@TableName("thread_pool_metric")
public class ThreadPoolMetricEntity {
    @TableId(value = "metric_id", type = IdType.AUTO)
    private Long metricId;
    private Long sampledAt;
    private String poolName;
    private Integer active;
    private Integer poolSize;
    private Integer queueSize;
    private Integer actualMax;
    private Long submitted;
    private Long completed;
    private Long rejected;
    private Long failed;
    private Long waitMs;
    private Long executionMs;
    private String priority;
    private String reliability;

    /** @return 指标主键 */ public Long getMetricId() { return metricId; }
    /** @param metricId 指标主键 */ public void setMetricId(Long metricId) { this.metricId = metricId; }
    /** @return 采样时刻 */ public Long getSampledAt() { return sampledAt; }
    /** @param sampledAt 采样时刻 */ public void setSampledAt(Long sampledAt) { this.sampledAt = sampledAt; }
    /** @return 线程池名称 */ public String getPoolName() { return poolName; }
    /** @param poolName 线程池名称 */ public void setPoolName(String poolName) { this.poolName = poolName; }
    /** @return 活跃任务数 */ public Integer getActive() { return active; }
    /** @param active 活跃任务数 */ public void setActive(Integer active) { this.active = active; }
    /** @return 当前线程数 */ public Integer getPoolSize() { return poolSize; }
    /** @param poolSize 当前线程数 */ public void setPoolSize(Integer poolSize) { this.poolSize = poolSize; }
    /** @return 队列任务数 */ public Integer getQueueSize() { return queueSize; }
    /** @param queueSize 队列任务数 */ public void setQueueSize(Integer queueSize) { this.queueSize = queueSize; }
    /** @return 生效最大线程数 */ public Integer getActualMax() { return actualMax; }
    /** @param actualMax 生效最大线程数 */ public void setActualMax(Integer actualMax) { this.actualMax = actualMax; }
    /** @return 已提交任务数 */ public Long getSubmitted() { return submitted; }
    /** @param submitted 已提交任务数 */ public void setSubmitted(Long submitted) { this.submitted = submitted; }
    /** @return 已完成任务数 */ public Long getCompleted() { return completed; }
    /** @param completed 已完成任务数 */ public void setCompleted(Long completed) { this.completed = completed; }
    /** @return 已拒绝任务数 */ public Long getRejected() { return rejected; }
    /** @param rejected 已拒绝任务数 */ public void setRejected(Long rejected) { this.rejected = rejected; }
    /** @return 已失败任务数 */ public Long getFailed() { return failed; }
    /** @param failed 已失败任务数 */ public void setFailed(Long failed) { this.failed = failed; }
    /** @return 累计等待毫秒 */ public Long getWaitMs() { return waitMs; }
    /** @param waitMs 累计等待毫秒 */ public void setWaitMs(Long waitMs) { this.waitMs = waitMs; }
    /** @return 累计执行毫秒 */ public Long getExecutionMs() { return executionMs; }
    /** @param executionMs 累计执行毫秒 */ public void setExecutionMs(Long executionMs) { this.executionMs = executionMs; }
    /** @return 调度优先级 */ public String getPriority() { return priority; }
    /** @param priority 调度优先级 */ public void setPriority(String priority) { this.priority = priority; }
    /** @return 可靠性策略 */ public String getReliability() { return reliability; }
    /** @param reliability 可靠性策略 */ public void setReliability(String reliability) { this.reliability = reliability; }
}
