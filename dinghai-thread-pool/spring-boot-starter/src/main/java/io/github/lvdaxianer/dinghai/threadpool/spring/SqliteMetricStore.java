package io.github.lvdaxianer.dinghai.threadpool.spring;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.lvdaxianer.dinghai.threadpool.governance.api.MetricPage;
import io.github.lvdaxianer.dinghai.threadpool.governance.api.ThreadPoolMetric;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

/**
 * 用 MyBatis-Plus 保存子控指标并按签名 cursor 分页读取。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-15
 */
public final class SqliteMetricStore {
    private static final String CURSOR_SECRET_KEY = "cursor_secret";
    private static final String INSTANCE_VERSION_KEY = "instance_version";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private final SqlSessionFactory sessionFactory;
    private final int maximumPageSize;
    private final byte[] cursorSecret;
    private final String instanceVersion;

    /** 创建并初始化本地 SQLite 指标库。 */
    public SqliteMetricStore(Path path, int maximumPageSize) {
        validate(path, maximumPageSize);
        createParent(path);
        sessionFactory = ThreadPoolSqliteSessionFactory.create(path);
        this.maximumPageSize = maximumPageSize;
        cursorSecret = loadCursorSecret();
        instanceVersion = loadInstanceVersion();
    }

    /** 持久化一个采样指标。 */
    public void append(ThreadPoolMetric metric) {
        appendAll(List.of(metric));
    }

    /** 在同一 MyBatis 批处理事务中持久化一个采样周期的全部指标。 */
    public void appendAll(List<ThreadPoolMetric> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return;
        }
        try (SqlSession session = sessionFactory.openSession(ExecutorType.BATCH, false)) {
            ThreadPoolMetricMapper mapper = session.getMapper(ThreadPoolMetricMapper.class);
            metrics.stream().map(this::entity).forEach(mapper::insert);
            session.flushStatements();
            session.commit();
        } catch (RuntimeException exception) {
            throw new IllegalStateException("cannot persist thread pool metric", exception);
        }
    }

    /** 按 cursor 返回有界的一页指标。 */
    public MetricPage page(String cursor, int requestedLimit) {
        long after = decodeCursor(cursor);
        rejectExpiredCursor(after);
        int limit = boundedLimit(requestedLimit);
        List<ThreadPoolMetric> metrics = query(after, limit + 1);
        return pageOf(after, limit, metrics);
    }

    /** 删除超过保留期的指标。 */
    public void purge(Duration retention) {
        Objects.requireNonNull(retention, "retention");
        long threshold = System.currentTimeMillis() - retention.toMillis();
        withMetricMapper(mapper -> mapper.delete(new LambdaQueryWrapper<ThreadPoolMetricEntity>()
                .lt(ThreadPoolMetricEntity::getSampledAt, threshold)));
    }

    /**
     * 返回由本地 SQLite 持久化卷决定的子控实例世代。
     *
     * @return 稳定的子控实例世代
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    public String instanceVersion() {
        return instanceVersion;
    }

    /** 读取指定 cursor 后的有界指标列表。 */
    private List<ThreadPoolMetric> query(long after, int limit) {
        return withMetricMapper(mapper -> mapper.selectPage(Page.of(1, limit, false), new LambdaQueryWrapper<ThreadPoolMetricEntity>()
                .gt(ThreadPoolMetricEntity::getMetricId, after)
                .orderByAsc(ThreadPoolMetricEntity::getMetricId)).getRecords().stream().map(this::metric).toList());
    }

    /** 生成协议页并维持服务端签名 cursor。 */
    private MetricPage pageOf(long after, int limit, List<ThreadPoolMetric> metrics) {
        boolean hasMore = metrics.size() > limit;
        List<ThreadPoolMetric> page = hasMore ? metrics.subList(0, limit) : metrics;
        long lastMetricId = page.isEmpty() ? after : page.getLast().metricId();
        return new MetricPage(encodeCursor(lastMetricId), hasMore, oldestCursor(), List.copyOf(page));
    }

    /** 返回当前最早可读取的签名 cursor。 */
    private String oldestCursor() {
        return encodeCursor(oldestMetricId());
    }

    /** 返回当前最早可读取的内部自增主键。 */
    private long oldestMetricId() {
        return withMetricMapper(mapper -> mapper.selectPage(Page.of(1, 1, false), new LambdaQueryWrapper<ThreadPoolMetricEntity>()
                .orderByAsc(ThreadPoolMetricEntity::getMetricId)).getRecords().stream()
                .findFirst().map(ThreadPoolMetricEntity::getMetricId).orElse(0L));
    }

    /** 拒绝会跳过已清理指标的增量读取。 */
    private void rejectExpiredCursor(long after) {
        long oldest = oldestMetricId();
        if (after > 0 && oldest > 0 && after < oldest - 1) {
            throw new CursorExpiredException(encodeCursor(oldest));
        }
    }

    /** 从 SQLite 元数据读取或创建跨重启稳定的 cursor 签名密钥。 */
    private byte[] loadCursorSecret() {
        return Base64.getDecoder().decode(loadOrCreateMetadata(CURSOR_SECRET_KEY,
                () -> Base64.getEncoder().encodeToString(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8))));
    }

    /** 读取或创建本地 SQLite 持久化卷的稳定实例世代。 */
    private String loadInstanceVersion() {
        return loadOrCreateMetadata(INSTANCE_VERSION_KEY, () -> UUID.randomUUID().toString());
    }

    /** 在 SQLite metadata 表中读取或原子创建指定值。 */
    private String loadOrCreateMetadata(String key, MetadataValueFactory valueFactory) {
        try (SqlSession session = sessionFactory.openSession(false)) {
            ThreadPoolMetricMetadataMapper mapper = session.getMapper(ThreadPoolMetricMetadataMapper.class);
            ThreadPoolMetricMetadataEntity metadata = mapper.selectById(key);
            if (metadata != null) {
                return metadata.getMetaValue();
            }
            ThreadPoolMetricMetadataEntity created = metadata(key, valueFactory.create());
            mapper.insert(created);
            session.commit();
            return created.getMetaValue();
        } catch (RuntimeException exception) {
            return readPersistedMetadata(key, exception);
        }
    }

    /** 在首次密钥写入竞争后读取已持久化值。 */
    private String readPersistedMetadata(String key, RuntimeException cause) {
        try (SqlSession session = sessionFactory.openSession()) {
            ThreadPoolMetricMetadataEntity metadata = session.getMapper(ThreadPoolMetricMetadataMapper.class)
                    .selectById(key);
            if (metadata != null) {
                return metadata.getMetaValue();
            }
        }
        throw new IllegalStateException("cannot initialize thread pool SQLite metadata", cause);
    }

    /** 创建尚未持久化的 cursor 密钥元数据。 */
    private ThreadPoolMetricMetadataEntity metadata(String key, String value) {
        ThreadPoolMetricMetadataEntity metadata = new ThreadPoolMetricMetadataEntity();
        metadata.setMetaKey(key);
        metadata.setMetaValue(value);
        return metadata;
    }

    /** 生成无法由调用方伪造的 metric_id cursor。 */
    private String encodeCursor(long metricId) {
        String payload = Long.toString(metricId);
        return Base64.getUrlEncoder().withoutPadding().encodeToString((payload + "." + signature(payload))
                .getBytes(StandardCharsets.UTF_8));
    }

    /** 校验并解析服务端签名 cursor。 */
    private long decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\\.", 2);
            if (parts.length != 2 || !MessageDigest.isEqual(signature(parts[0]).getBytes(StandardCharsets.UTF_8),
                    parts[1].getBytes(StandardCharsets.UTF_8))) {
                throw new IllegalArgumentException("invalid thread pool metric cursor");
            }
            return Long.parseLong(parts[0]);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("invalid thread pool metric cursor", exception);
        }
    }

    /** 使用只存于本地 SQLite 的密钥计算 cursor 签名。 */
    private String signature(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(cursorSecret, HMAC_ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("cannot sign thread pool cursor", exception);
        }
    }

    /** 执行单次 Mapper 查询或更新并安全关闭会话。 */
    private <T> T withMetricMapper(MetricMapperOperation<T> operation) {
        try (SqlSession session = sessionFactory.openSession(true)) {
            return operation.execute(session.getMapper(ThreadPoolMetricMapper.class));
        }
    }

    /** 将 API 指标投影转换为持久化实体。 */
    private ThreadPoolMetricEntity entity(ThreadPoolMetric metric) {
        Objects.requireNonNull(metric, "metric");
        ThreadPoolMetricEntity entity = new ThreadPoolMetricEntity();
        entity.setSampledAt(metric.sampledAt()); entity.setPoolName(metric.poolName());
        entity.setActive(metric.active()); entity.setPoolSize(metric.poolSize()); entity.setQueueSize(metric.queueSize());
        entity.setActualMax(metric.actualMaximum()); entity.setSubmitted(metric.submitted());
        entity.setCompleted(metric.completed()); entity.setRejected(metric.rejected()); entity.setFailed(metric.failed());
        entity.setWaitMs(metric.waitMillis()); entity.setExecutionMs(metric.executionMillis());
        entity.setPriority(metric.priority()); entity.setReliability(metric.reliability());
        return entity;
    }

    /** 将持久化实体转换为 API 指标投影。 */
    private ThreadPoolMetric metric(ThreadPoolMetricEntity entity) {
        return new ThreadPoolMetric(entity.getMetricId(), entity.getSampledAt(), entity.getPoolName(),
                entity.getActive(), entity.getPoolSize(), entity.getQueueSize(), entity.getActualMax(),
                entity.getSubmitted(), entity.getCompleted(), entity.getRejected(), entity.getFailed(),
                entity.getWaitMs(), entity.getExecutionMs(), entity.getPriority(), entity.getReliability());
    }

    /** 限制请求页大小。 */
    private int boundedLimit(int requestedLimit) {
        return Math.min(Math.max(1, requestedLimit), maximumPageSize);
    }

    /** 创建 SQLite 父目录。 */
    private void createParent(Path path) {
        try {
            Files.createDirectories(path.toAbsolutePath().getParent());
        } catch (Exception exception) {
            throw new IllegalArgumentException("cannot create SQLite parent directory", exception);
        }
    }

    /** 验证 SQLite 指标库配置。 */
    private void validate(Path path, int maximumPageSize) {
        if (path == null || maximumPageSize < 1) {
            throw new IllegalArgumentException("invalid SQLite metric store configuration");
        }
    }

    /** 子控指标 Mapper 操作函数。 */
    @FunctionalInterface
    private interface MetricMapperOperation<T> {
        /** 执行 Mapper 操作。 */
        T execute(ThreadPoolMetricMapper mapper);
    }

    /** SQLite metadata 默认值创建函数。 */
    @FunctionalInterface
    private interface MetadataValueFactory {
        /** @return 待持久化的 metadata 值 */
        String create();
    }
}
