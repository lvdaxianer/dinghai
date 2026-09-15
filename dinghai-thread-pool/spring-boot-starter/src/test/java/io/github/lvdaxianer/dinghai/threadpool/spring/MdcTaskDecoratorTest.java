package io.github.lvdaxianer.dinghai.threadpool.spring;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.slf4j.MDC;

/**
 * 验证异步任务 MDC 上下文传播与恢复。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-15
 */
public class MdcTaskDecoratorTest {

    /** 提交上下文必须在任务中可见，并在任务结束后恢复原工作线程上下文。 */
    @Test
    public void shouldPropagateAndRestoreMdcContext() {
        MdcTaskDecorator decorator = new MdcTaskDecorator();
        AtomicReference<String> observed = new AtomicReference<>();
        MDC.put("traceId", "submitted-trace");
        Runnable decorated = decorator.decorate(() -> observed.set(MDC.get("traceId")));
        MDC.clear();

        decorated.run();

        assertEquals("submitted-trace", observed.get());
        assertNull(MDC.get("traceId"));
    }
}
