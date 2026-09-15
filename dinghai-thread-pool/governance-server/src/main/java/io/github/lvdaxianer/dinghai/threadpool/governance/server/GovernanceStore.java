package io.github.lvdaxianer.dinghai.threadpool.governance.server;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.lvdaxianer.dinghai.threadpool.governance.api.ThreadPoolMetric;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.ExecutorType;

/**
 * 使用 MyBatis-Plus 保存单实例总控 SQLite 控制面状态。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-15
 */
public final class GovernanceStore {
    private static final String OPEN = "OPEN";
    private static final String RESOLVED = "RESOLVED";
    private static final String RECOVERED = "RECOVERED";
    private final SqlSessionFactory sessionFactory;

    /** 创建并初始化 SQLite 控制面存储。 */
    public GovernanceStore(Path path) {
        Objects.requireNonNull(path, "path");
        createParent(path);
        sessionFactory = GovernanceSqliteSessionFactory.create(path);
    }

    /** 注册或更新一个子控实例。 */
    public void register(String instanceId, String baseUrl, String environment) {
        register(instanceId, baseUrl, environment, "legacy");
    }

    /** 注册或更新带实例世代的子控，并在世代变化时丢弃旧 cursor。 */
    public void register(String instanceId, String baseUrl, String environment, String version) {
        withMappers(mappers -> {
            GovernanceInstanceEntity entity = mappers.instance().selectById(instanceId);
            if (entity == null) {
                mappers.instance().insert(instance(instanceId, baseUrl, environment, version));
            } else {
                updateInstance(entity, baseUrl, environment, version, mappers);
            }
            return null;
        });
    }

    /**
     * 观察到子控实例世代时更新控制面，并在变化后清理旧 cursor。
     *
     * @param instanceId 已注册子控实例标识
     * @param version 子控 SQLite 持久化卷世代
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    public void observeInstanceVersion(String instanceId, String version) {
        if (version == null || version.isBlank()) {
            return;
        }
        withMappers(mappers -> {
            GovernanceInstanceEntity entity = mappers.instance().selectById(instanceId);
            if (entity == null) {
                throw new IllegalArgumentException("governance instance is not registered: " + instanceId);
            }
            if (!Objects.equals(entity.getVersion(), version)) {
                mappers.cursor().deleteById(instanceId);
                entity.setVersion(version); entity.setUpdatedAt(System.currentTimeMillis());
                mappers.instance().updateById(entity);
            }
            return null;
        });
    }

    /** 返回当前启用实例，供单实例轮询器和管理 API 查询。 */
    public List<GovernanceInstance> instances() {
        return withMappers(mappers -> mappers.instance().selectList(new LambdaQueryWrapper<GovernanceInstanceEntity>()
                .orderByAsc(GovernanceInstanceEntity::getInstanceId)).stream().map(this::instance).toList());
    }

    /** 读取某实例已确认的 cursor。 */
    public Optional<String> cursor(String instanceId) {
        return withMappers(mappers -> Optional.ofNullable(mappers.cursor().selectById(instanceId))
                .map(GovernanceCursorEntity::getCursor));
    }

    /**
     * 在总控 SQLite 中批量保存子控采样，写入失败时不允许调用方推进 cursor。
     *
     * @param instanceId 子控实例标识
     * @param metrics 子控指标页
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    public void appendMetrics(String instanceId, List<ThreadPoolMetric> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return;
        }
        try (SqlSession session = sessionFactory.openSession(ExecutorType.BATCH, false)) {
            GovernanceMappers.Metric mapper = session.getMapper(GovernanceMappers.Metric.class);
            metrics.stream().map(metric -> metric(instanceId, metric)).forEach(entity -> insertIfAbsent(mapper, entity));
            session.flushStatements();
            session.commit();
        } catch (RuntimeException exception) {
            throw new IllegalStateException("cannot persist collected governance metrics", exception);
        }
    }

    /**
     * 查询指定条件的历史指标，供总控只读分析 API 使用。
     *
     * @param instanceId 实例筛选，可为空
     * @param poolName 线程池筛选，可为空
     * @param from 包含的起始时刻
     * @param to 包含的结束时刻
     * @return 已按采样时间升序排列的指标
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    public List<GovernanceThreadPoolMetric> metrics(String instanceId, String poolName, long from, long to) {
        if (from < 0 || to < from) {
            throw new IllegalArgumentException("invalid governance metric time range");
        }
        return withMappers(mappers -> mappers.metric().selectList(metricQuery(instanceId, poolName, from, to))
                .stream().map(this::metric).toList());
    }

    /**
     * 删除超过总控保留期的历史指标。
     *
     * @param retention 正数保留期
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    public void purgeMetrics(Duration retention) {
        if (retention == null || retention.isNegative() || retention.isZero()) {
            throw new IllegalArgumentException("governance metric retention must be positive");
        }
        long threshold = System.currentTimeMillis() - retention.toMillis();
        withMappers(mappers -> mappers.metric().delete(new LambdaQueryWrapper<GovernanceThreadPoolMetricEntity>()
                .lt(GovernanceThreadPoolMetricEntity::getSampledAt, threshold)));
    }

    /** 在聚合成功后提交一个实例 cursor。 */
    public void commitCursor(String instanceId, String cursor) {
        withMappers(mappers -> {
            GovernanceCursorEntity entity = mappers.cursor().selectById(instanceId);
            if (entity == null) {
                mappers.cursor().insert(cursor(instanceId, cursor));
            } else {
                entity.setCursor(cursor); entity.setUpdatedAt(System.currentTimeMillis());
                mappers.cursor().updateById(entity);
            }
            return null;
        });
    }

    /** 记录一次无法补齐的指标采集缺口。 */
    public void recordGap(String instanceId, String requestedCursor, String oldestCursor) {
        withMappers(mappers -> {
            GovernanceCollectionGapEntity entity = new GovernanceCollectionGapEntity();
            entity.setInstanceId(instanceId); entity.setRequestedCursor(requestedCursor);
            entity.setOldestCursor(oldestCursor); entity.setDetectedAt(System.currentTimeMillis());
            entity.setRecoveryStatus(OPEN); mappers.gap().insert(entity);
            return null;
        });
    }

    /** 返回一个实例全部采集缺口，供管理查询和告警恢复使用。 */
    public List<GovernanceCollectionGap> gaps(String instanceId) {
        return withMappers(mappers -> mappers.gap().selectList(new LambdaQueryWrapper<GovernanceCollectionGapEntity>()
                .eq(GovernanceCollectionGapEntity::getInstanceId, instanceId)
                .orderByAsc(GovernanceCollectionGapEntity::getId)).stream().map(this::gap).toList());
    }

    /** 将尚未恢复的最新缺口标记为已用快照恢复。 */
    public void markLatestGapRecovered(String instanceId) {
        withMappers(mappers -> {
            GovernanceCollectionGapEntity gap = mappers.gap().selectPage(Page.of(1, 1, false), new LambdaQueryWrapper<GovernanceCollectionGapEntity>()
                    .eq(GovernanceCollectionGapEntity::getInstanceId, instanceId)
                    .eq(GovernanceCollectionGapEntity::getRecoveryStatus, OPEN)
                    .orderByDesc(GovernanceCollectionGapEntity::getId)).getRecords().stream().findFirst().orElse(null);
            if (gap != null) {
                gap.setRecoveryStatus(RECOVERED); mappers.gap().updateById(gap);
            }
            return null;
        });
    }

    /** 创建或刷新一个开放告警，保留首次触发时间。 */
    public void fireAlert(String alertKey, String value) {
        withMappers(mappers -> {
            GovernanceAlertStateEntity entity = mappers.alert().selectById(alertKey);
            if (entity == null) {
                mappers.alert().insert(alert(alertKey, value));
            } else {
                entity.setStatus(OPEN); entity.setLastFiredAt(System.currentTimeMillis());
                entity.setLastValue(value); entity.setResolvedAt(null); mappers.alert().updateById(entity);
            }
            return null;
        });
    }

    /** 将一个已恢复的告警标记为已解决。 */
    public void resolveAlert(String alertKey) {
        withMappers(mappers -> {
            mappers.alert().update(null, new LambdaUpdateWrapper<GovernanceAlertStateEntity>()
                    .eq(GovernanceAlertStateEntity::getAlertKey, alertKey)
                    .eq(GovernanceAlertStateEntity::getStatus, OPEN)
                    .set(GovernanceAlertStateEntity::getStatus, RESOLVED)
                    .set(GovernanceAlertStateEntity::getResolvedAt, System.currentTimeMillis()));
            return null;
        });
    }

    /** 读取一个告警的当前持久化状态。 */
    public Optional<GovernanceAlertState> alert(String alertKey) {
        return withMappers(mappers -> Optional.ofNullable(mappers.alert().selectById(alertKey)).map(this::alert));
    }

    /** 返回全部持久化告警状态，供受保护的管理查询使用。 */
    public List<GovernanceAlertState> alerts() {
        return withMappers(mappers -> mappers.alert().selectList(new LambdaQueryWrapper<GovernanceAlertStateEntity>()
                .orderByAsc(GovernanceAlertStateEntity::getAlertKey)).stream().map(this::alert).toList());
    }

    /** 更新已有实例记录。 */
    private void updateInstance(GovernanceInstanceEntity entity, String baseUrl, String environment, String version, Mappers mappers) {
        if (!Objects.equals(entity.getVersion(), version)) {
            mappers.cursor().deleteById(entity.getInstanceId());
        }
        entity.setBaseUrl(baseUrl); entity.setEnvironment(environment); entity.setEnabled(true);
        entity.setVersion(version);
        entity.setUpdatedAt(System.currentTimeMillis()); mappers.instance().updateById(entity);
    }

    /** 创建实例实体。 */
    private GovernanceInstanceEntity instance(String instanceId, String baseUrl, String environment, String version) {
        GovernanceInstanceEntity entity = new GovernanceInstanceEntity();
        entity.setInstanceId(instanceId); entity.setBaseUrl(baseUrl); entity.setEnvironment(environment);
        entity.setEnabled(true); entity.setVersion(version); entity.setUpdatedAt(System.currentTimeMillis());
        return entity;
    }

    /** 创建 cursor 实体。 */
    private GovernanceCursorEntity cursor(String instanceId, String cursor) {
        GovernanceCursorEntity entity = new GovernanceCursorEntity();
        entity.setInstanceId(instanceId); entity.setCursor(cursor); entity.setUpdatedAt(System.currentTimeMillis());
        return entity;
    }

    /** 创建开放告警实体。 */
    private GovernanceAlertStateEntity alert(String alertKey, String value) {
        long now = System.currentTimeMillis();
        GovernanceAlertStateEntity entity = new GovernanceAlertStateEntity();
        entity.setAlertKey(alertKey); entity.setStatus(OPEN); entity.setFirstFiredAt(now);
        entity.setLastFiredAt(now); entity.setLastValue(value);
        return entity;
    }

    /** 将实例实体投影为公开状态。 */
    private GovernanceInstance instance(GovernanceInstanceEntity entity) {
        return new GovernanceInstance(entity.getInstanceId(), entity.getBaseUrl(), entity.getEnvironment(),
                entity.getEnabled(), entity.getVersion(), entity.getUpdatedAt());
    }

    /** 将缺口实体投影为公开状态。 */
    private GovernanceCollectionGap gap(GovernanceCollectionGapEntity entity) {
        return new GovernanceCollectionGap(entity.getId(), entity.getInstanceId(), entity.getRequestedCursor(),
                entity.getOldestCursor(), entity.getDetectedAt(), entity.getRecoveryStatus());
    }

    /** 将告警实体投影为公开状态。 */
    private GovernanceAlertState alert(GovernanceAlertStateEntity entity) {
        return new GovernanceAlertState(entity.getAlertKey(), entity.getStatus(), entity.getFirstFiredAt(),
                entity.getLastFiredAt(), entity.getLastValue(), entity.getResolvedAt());
    }

    /** 创建按可选实例和池名称过滤的时间范围查询。 */
    private LambdaQueryWrapper<GovernanceThreadPoolMetricEntity> metricQuery(String instanceId, String poolName,
                                                                                long from, long to) {
        LambdaQueryWrapper<GovernanceThreadPoolMetricEntity> query = new LambdaQueryWrapper<GovernanceThreadPoolMetricEntity>()
                .ge(GovernanceThreadPoolMetricEntity::getSampledAt, from)
                .le(GovernanceThreadPoolMetricEntity::getSampledAt, to);
        if (instanceId != null && !instanceId.isBlank()) {
            query.eq(GovernanceThreadPoolMetricEntity::getInstanceId, instanceId);
        }
        if (poolName != null && !poolName.isBlank()) {
            query.eq(GovernanceThreadPoolMetricEntity::getPoolName, poolName);
        }
        return query.orderByAsc(GovernanceThreadPoolMetricEntity::getSampledAt)
                .orderByAsc(GovernanceThreadPoolMetricEntity::getId);
    }

    /** 将子控指标页投影为总控持久化实体。 */
    private GovernanceThreadPoolMetricEntity metric(String instanceId, ThreadPoolMetric metric) {
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(metric, "metric");
        GovernanceThreadPoolMetricEntity entity = new GovernanceThreadPoolMetricEntity();
        entity.setInstanceId(instanceId); entity.setSourceMetricId(sourceMetricId(metric));
        entity.setSampledAt(metric.sampledAt()); entity.setPoolName(metric.poolName());
        entity.setActive(metric.active()); entity.setPoolSize(metric.poolSize()); entity.setQueueSize(metric.queueSize());
        entity.setActualMaximum(metric.actualMaximum()); entity.setSubmitted(metric.submitted());
        entity.setCompleted(metric.completed()); entity.setRejected(metric.rejected()); entity.setFailed(metric.failed());
        entity.setWaitMillis(metric.waitMillis()); entity.setExecutionMillis(metric.executionMillis());
        entity.setPriority(metric.priority()); entity.setReliability(metric.reliability());
        return entity;
    }

    /** 将总控实体投影为不暴露 SQLite 主键的只读指标。 */
    private GovernanceThreadPoolMetric metric(GovernanceThreadPoolMetricEntity entity) {
        return new GovernanceThreadPoolMetric(entity.getInstanceId(), entity.getSampledAt(), entity.getPoolName(),
                entity.getActive(), entity.getPoolSize(), entity.getQueueSize(), entity.getActualMaximum(),
                entity.getSubmitted(), entity.getCompleted(), entity.getRejected(), entity.getFailed(),
                entity.getWaitMillis(), entity.getExecutionMillis(), entity.getPriority(), entity.getReliability());
    }

    /** 使用子控指标主键去重，确保重试采集不会重复累积历史记录。 */
    private void insertIfAbsent(GovernanceMappers.Metric mapper, GovernanceThreadPoolMetricEntity entity) {
        if (entity.getSourceMetricId() == null || mapper.selectCount(new LambdaQueryWrapper<GovernanceThreadPoolMetricEntity>()
                .eq(GovernanceThreadPoolMetricEntity::getInstanceId, entity.getInstanceId())
                .eq(GovernanceThreadPoolMetricEntity::getSourceMetricId, entity.getSourceMetricId())) == 0) {
            mapper.insert(entity);
        }
    }

    /** 仅让子控 SQLite 已分配的正数主键参与跨重试幂等控制。 */
    private Long sourceMetricId(ThreadPoolMetric metric) {
        return metric.metricId() > 0 ? metric.metricId() : null;
    }

    /** 执行 MyBatis-Plus 操作并将同一会话作为事务边界。 */
    private <T> T withMappers(MapperOperation<T> operation) {
        try (SqlSession session = sessionFactory.openSession(false)) {
            T result = operation.execute(new Mappers(session));
            session.commit();
            return result;
        } catch (RuntimeException exception) {
            throw new IllegalStateException("cannot access governance SQLite store", exception);
        }
    }

    /** 创建 SQLite 父目录。 */
    private void createParent(Path path) {
        try {
            Files.createDirectories(path.toAbsolutePath().getParent());
        } catch (Exception exception) {
            throw new IllegalArgumentException("cannot create governance database directory", exception);
        }
    }

    /** 总控 Mapper 会话视图。 */
    private record Mappers(SqlSession session) {
        /** @return 实例 Mapper */ GovernanceMappers.Instance instance() { return session.getMapper(GovernanceMappers.Instance.class); }
        /** @return cursor Mapper */ GovernanceMappers.Cursor cursor() { return session.getMapper(GovernanceMappers.Cursor.class); }
        /** @return 缺口 Mapper */ GovernanceMappers.Gap gap() { return session.getMapper(GovernanceMappers.Gap.class); }
        /** @return 告警 Mapper */ GovernanceMappers.Alert alert() { return session.getMapper(GovernanceMappers.Alert.class); }
        /** @return 线程池指标 Mapper */ GovernanceMappers.Metric metric() { return session.getMapper(GovernanceMappers.Metric.class); }
    }

    /** 总控 Mapper 操作函数。 */
    @FunctionalInterface
    private interface MapperOperation<T> {
        /** 执行 Mapper 操作。 */ T execute(Mappers mappers);
    }
}
