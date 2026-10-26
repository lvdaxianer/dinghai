package io.github.lvdaxianer.dinghai.filestorage.spring;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorage;
import io.github.lvdaxianer.dinghai.filestorage.core.ObjectKey;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 异步删除生命周期日志与 MDC 测试。 */
public class FileStorageDeleteLoggingTest {

    /** 测试数据库临时目录。 */
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    /** 登记和幂等命中日志应包含任务标识且不包含原始请求字段。 */
    @Test
    public void shouldLogRegistrationWithoutSensitiveRequestFields() throws Exception {
        ListAppender<ILoggingEvent> appender = appender(AsyncFileStorageDeleteService.class);
        FileStorageDeleteTaskRepository repository = repository();
        AsyncFileStorageDeleteService service = new AsyncFileStorageDeleteService(
                mock(FileStorage.class), repository, properties(), clock());

        FileStorageDeleteTaskView task = service.submit(
                new FileStorageDeleteRequest(ObjectKey.of("private/a.txt"), "secret-request-key"));
        service.submit(new FileStorageDeleteRequest(ObjectKey.of("other.txt"), "secret-request-key"));

        List<String> messages = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(messages).anyMatch(message -> message.contains(task.taskId())
                && message.contains("registered"));
        assertThat(messages).anyMatch(message -> message.contains(task.taskId())
                && message.contains("idempotent-hit"));
        assertThat(messages).noneMatch(message -> message.contains("private/a.txt")
                || message.contains("secret-request-key"));
        detach(AsyncFileStorageDeleteService.class, appender);
    }

    /** Worker 日志应带本次执行 trace，并在任务结束后清理 MDC。 */
    @Test
    public void shouldLogWorkerLifecycleWithTraceAndSafeResult() throws Exception {
        ListAppender<ILoggingEvent> appender = appender(FileStorageDeleteWorker.class);
        FileStorageDeleteTaskRepository repository = repository();
        repository.insert(FileStorageDeleteTask.pending("task-log", "documents", "private/a.txt",
                "secret-request-key", 1_000L));
        AtomicReference<String> observedTrace = new AtomicReference<>();
        FileStorage storage = mock(FileStorage.class);
        when(storage.delete(any(ObjectKey.class))).thenAnswer(invocation -> {
            observedTrace.set(MDC.get("traceId"));
            return false;
        });

        FileStorageDeleteWorker worker = new FileStorageDeleteWorker(
                storage, repository, properties(), clock());
        worker.processOnce(1_000L);

        assertThat(observedTrace.get()).startsWith("file-delete-");
        assertThat(MDC.get("traceId")).isNull();
        assertThat(appender.list).anyMatch(event -> event.getFormattedMessage().contains("task-log")
                && event.getFormattedMessage().contains("result=missing")
                && event.getFormattedMessage().contains("durationMs=")
                && event.getMDCPropertyMap().containsKey("traceId"));
        assertThat(appender.list).noneMatch(event -> event.getFormattedMessage().contains("private/a.txt")
                || event.getFormattedMessage().contains("secret-request-key"));
        detach(FileStorageDeleteWorker.class, appender);
    }

    /** 重试与最终失败日志应包含安全错误类型和任务标识。 */
    @Test
    public void shouldLogRetryAndTerminalFailureWithoutProviderMessage() throws Exception {
        ListAppender<ILoggingEvent> appender = appender(FileStorageDeleteWorker.class);
        FileStorageDeleteTaskRepository repository = repository();
        repository.insert(FileStorageDeleteTask.pending("task-retry-log", "documents", "private/a.txt",
                "secret-request-key", 1_000L));
        FileStorage storage = mock(FileStorage.class);
        when(storage.delete(any(ObjectKey.class))).thenThrow(new IllegalStateException("secret-provider-token"));
        FileStorageProperties properties = properties();
        properties.getDelete().setMaxAttempts(1);

        FileStorageDeleteWorker worker = new FileStorageDeleteWorker(storage, repository, properties, clock());
        worker.processOnce(1_000L);

        assertThat(appender.list).anyMatch(event -> event.getFormattedMessage().contains("task-retry-log")
                && event.getFormattedMessage().contains("task failed")
                && event.getFormattedMessage().contains("errorType=IllegalStateException"));
        assertThat(appender.list).noneMatch(event -> event.getFormattedMessage().contains("secret-provider-token")
                || event.getFormattedMessage().contains("private/a.txt")
                || event.getFormattedMessage().contains("secret-request-key"));
        detach(FileStorageDeleteWorker.class, appender);
    }

    /** 可恢复 provider 异常应记录下一次调度信息。 */
    @Test
    public void shouldLogRetrySchedulingWithoutProviderMessage() throws Exception {
        ListAppender<ILoggingEvent> appender = appender(FileStorageDeleteWorker.class);
        FileStorageDeleteTaskRepository repository = repository();
        repository.insert(FileStorageDeleteTask.pending("task-retry-wait", "documents", "private/a.txt",
                "secret-request-key", 1_000L));
        FileStorage storage = mock(FileStorage.class);
        when(storage.delete(any(ObjectKey.class))).thenThrow(new IllegalStateException("secret-provider-token"));
        FileStorageProperties properties = properties();
        properties.getDelete().setMaxAttempts(3);

        FileStorageDeleteWorker worker = new FileStorageDeleteWorker(storage, repository, properties, clock());
        worker.processOnce(1_000L);

        assertThat(appender.list).anyMatch(event -> event.getFormattedMessage().contains("task-retry-wait")
                && event.getFormattedMessage().contains("task retry-scheduled")
                && event.getFormattedMessage().contains("errorType=IllegalStateException")
                && event.getFormattedMessage().contains("nextRunAt="));
        assertThat(appender.list).noneMatch(event -> event.getFormattedMessage().contains("secret-provider-token")
                || event.getFormattedMessage().contains("private/a.txt")
                || event.getFormattedMessage().contains("secret-request-key"));
        detach(FileStorageDeleteWorker.class, appender);
    }

    /** Worker 启停日志应记录资源边界。 */
    @Test
    public void shouldLogWorkerStartAndStop() throws Exception {
        ListAppender<ILoggingEvent> appender = appender(FileStorageDeleteWorker.class);
        FileStorageDeleteWorker worker = new FileStorageDeleteWorker(
                mock(FileStorage.class), repository(), properties(), clock());

        worker.start();
        worker.close();

        assertThat(appender.list).anyMatch(event -> event.getFormattedMessage().contains("worker started")
                && event.getFormattedMessage().contains("queueCapacity="));
        assertThat(appender.list).anyMatch(event -> event.getFormattedMessage().contains("worker stopped"));
        detach(FileStorageDeleteWorker.class, appender);
    }

    private ListAppender<ILoggingEvent> appender(Class<?> type) {
        Logger logger = (Logger) LoggerFactory.getLogger(type);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private void detach(Class<?> type, ListAppender<ILoggingEvent> appender) {
        Logger logger = (Logger) LoggerFactory.getLogger(type);
        logger.detachAppender(appender);
        appender.stop();
    }

    private FileStorageDeleteTaskRepository repository() throws Exception {
        Path database = temporaryFolder.newFile("delete-log-" + System.nanoTime() + ".db").toPath();
        return FileStorageDeleteTaskRepository.sqlite(database);
    }

    private FileStorageProperties properties() {
        FileStorageProperties properties = new FileStorageProperties();
        properties.setStorageAlias("documents");
        properties.getDelete().setMode(FileStorageDeleteMode.ASYNC);
        return properties;
    }

    private Clock clock() {
        return Clock.fixed(Instant.ofEpochMilli(1_000L), ZoneOffset.UTC);
    }
}
