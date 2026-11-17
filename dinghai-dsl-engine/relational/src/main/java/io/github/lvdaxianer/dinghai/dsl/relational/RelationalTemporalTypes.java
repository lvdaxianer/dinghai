package io.github.lvdaxianer.dinghai.dsl.relational;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * 关系 DSL 的时间逻辑类型规范化工具。
 *
 * <p>逻辑类型和请求参数在计划层统一转换，避免后端适配器各自解释时间字符串，
 * 同时保证带时区的时间不会被当前 JVM 默认时区隐式改写。</p>
 */
public final class RelationalTemporalTypes {

    /** 日期类型，不包含时间和时区。 */
    public static final String DATE = "date";

    /** 本地日期时间类型，不包含时区。 */
    public static final String LOCAL_DATETIME = "local_datetime";

    /** 时间线瞬时类型，输入必须携带 offset 或 Z。 */
    public static final String INSTANT = "instant";

    /** 兼容历史 DSL 的 datetime 类型名。 */
    public static final String LEGACY_DATETIME = "datetime";

    private RelationalTemporalTypes() {
    }

    /**
     * 规范化逻辑类型名称。
     *
     * @param logicalType 原始逻辑类型
     * @return 小写规范类型；历史 datetime 统一映射为 local_datetime
     */
    public static String normalize(String logicalType) {
        String value = logicalType == null ? null : logicalType.trim().toLowerCase(Locale.ROOT);
        if (LEGACY_DATETIME.equals(value)) {
            return LOCAL_DATETIME;
        }
        return value;
    }

    /**
     * 按逻辑类型规范化参数值。
     *
     * <p>local_datetime 只接受无 offset 的 ISO 本地时间，instant 只接受带 offset 或 Z
     * 的 ISO 时间；两者不会通过默认时区相互转换。</p>
     *
     * @param logicalType 逻辑类型
     * @param value 原始值
     * @return 适合计划和 JDBC 适配器使用的类型化值
     * @throws IllegalArgumentException 时间格式或类型不符合逻辑类型
     */
    public static Object normalizeValue(String logicalType, Object value) {
        String type = normalize(logicalType);
        if (value instanceof Collection<?> collection) {
            return List.copyOf(collection.stream()
                    .map(item -> {
                        if (item == null) {
                            throw new IllegalArgumentException("temporal collection item must not be null");
                        }
                        return normalizeValue(type, item);
                    })
                    .toList());
        }
        if (value == null || !isTemporal(type)) {
            return value;
        }
        try {
            return switch (type) {
                case DATE -> value instanceof LocalDate
                        ? value
                        : LocalDate.parse(requireText(value), DateTimeFormatter.ISO_LOCAL_DATE);
                case LOCAL_DATETIME -> value instanceof LocalDateTime
                        ? value
                        : LocalDateTime.parse(requireText(value), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                case INSTANT -> toInstant(value);
                default -> value;
            };
        } catch (DateTimeParseException | ClassCastException exception) {
            throw new IllegalArgumentException("value does not match temporal logical type", exception);
        }
    }

    /**
     * 判断是否为内置时间逻辑类型。
     *
     * @param logicalType 逻辑类型
     * @return 是否为时间类型
     */
    public static boolean isTemporal(String logicalType) {
        String type = normalize(logicalType);
        return DATE.equals(type) || LOCAL_DATETIME.equals(type) || INSTANT.equals(type);
    }

    private static Instant toInstant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (value instanceof ZonedDateTime zonedDateTime) {
            return zonedDateTime.toInstant();
        }
        return OffsetDateTime.parse(requireText(value), DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant();
    }

    private static String requireText(Object value) {
        if (!(value instanceof CharSequence text)) {
            throw new IllegalArgumentException("temporal value must be a java.time value or ISO text");
        }
        return text.toString();
    }
}
