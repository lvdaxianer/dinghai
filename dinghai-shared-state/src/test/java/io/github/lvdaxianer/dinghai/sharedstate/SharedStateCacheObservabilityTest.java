package io.github.lvdaxianer.dinghai.sharedstate;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;

/**
 * 共享状态缓存结构化日志测试。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-07-17
 */
public class SharedStateCacheObservabilityTest {

    /** 临时状态命名空间。 */
    private static final String STATE_NAMESPACE = "observability-state";

    /** 测试缓存值。 */
    private static final String SENSITIVE_VALUE = "sensitive-value";

    /**
     * 降级日志不得包含完整键或缓存值。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Test
    public void shouldLogFallbackWithoutRawKeyOrValue() {
        LogCapture capture = new LogCapture();
        SharedStateCacheObservability observability =
                new SharedStateCacheObservability(capture.logger());
        CacheKey<String> key = createSensitiveKey();

        observability.fallback(key);

        String message = capture.messages.get(0);
        Assert.assertTrue(message.startsWith("[共享状态缓存]"));
        Assert.assertTrue(message.contains("namespace=" + STATE_NAMESPACE));
        Assert.assertTrue(message.contains("policy=STATE_FALLBACK"));
        Assert.assertFalse(message.contains(key.value()));
        Assert.assertFalse(message.contains(SENSITIVE_VALUE));
    }

    /**
     * 创建包含敏感标识的缓存键。
     *
     * @return 敏感缓存键
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private CacheKey<String> createSensitiveKey() {
        CacheKeyNamespace keys =
                new CacheKeyNamespace(STATE_NAMESPACE, "auth", "oauth-state");
        NamespacePolicy policy = NamespacePolicy.stateFallback(STATE_NAMESPACE);
        return new CacheKeyBuilder(NamespacePolicyRegistry.of(policy))
                .build(keys, "sensitive-identifier", String.class);
    }

    /**
     * SLF4J 日志模板和参数捕获器。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private static final class LogCapture implements InvocationHandler {

        /** 捕获后的格式化消息。 */
        private final List<String> messages = new ArrayList<>(1);

        /**
         * 创建 SLF4J 日志代理。
         *
         * @return 日志代理
         * @author lvdaxianer@yeah.net
         * @date 2026-07-17
         */
        private Logger logger() {
            return (Logger) Proxy.newProxyInstance(Logger.class.getClassLoader(),
                    new Class<?>[]{Logger.class}, this);
        }

        /**
         * 捕获 WARN 方法的模板和参数。
         *
         * @param proxy 日志代理
         * @param method 调用方法
         * @param args 调用参数
         * @return 默认返回值
         * @author lvdaxianer@yeah.net
         * @date 2026-07-17
         */
        @Override
        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
            // 只捕获 warn 级别日志，其余级别调用直接忽略。
            if ("warn".equals(method.getName())) {
                messages.add(format(args));
            }
            return method.getReturnType() == boolean.class ? false : null;
        }

        /**
         * 将 SLF4J 占位符替换为测试参数。
         *
         * @param args 日志调用参数
         * @return 格式化消息
         * @author lvdaxianer@yeah.net
         * @date 2026-07-17
         */
        private String format(Object[] args) {
            String result = String.valueOf(args[0]);
            Object[] values = args.length == 2 && args[1] instanceof Object[] nested
                    ? nested : java.util.Arrays.copyOfRange(args, 1, args.length);
            for (Object value : values) {
                result = result.replaceFirst("\\{}", String.valueOf(value));
            }
            return result;
        }
    }
}
