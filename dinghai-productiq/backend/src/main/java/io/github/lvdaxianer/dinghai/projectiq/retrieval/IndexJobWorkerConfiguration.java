package io.github.lvdaxianer.dinghai.projectiq.retrieval;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 在运行时索引执行器可用后装配受限的后台同步 worker。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
@Configuration
public class IndexJobWorkerConfiguration {
    /**
     * 创建并启动单线程索引 worker；执行器在下一项完整运行时装配后提供。
     *
     * @param runner SQLite 索引执行器
     * @param batchSize 每轮最大任务数
     * @param interval 固定延迟间隔文本
     * @return 已启动的索引 worker
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    @Bean
    @ConditionalOnBean(IndexJobRunner.class)
    IndexJobWorker indexJobWorker(IndexJobRunner runner,
                                  @Value("${productiq.index-worker.batch-size:20}") int batchSize,
                                  @Value("${productiq.index-worker.interval:10s}") String interval) {
        IndexJobWorker worker = new IndexJobWorker(runner, batchSize, parseInterval(interval));
        worker.start();
        return worker;
    }

    /**
     * 解析 Spring Boot 标准格式的索引固定延迟。
     *
     * @param value 原始配置文本
     * @return 已解析时间间隔
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private static Duration parseInterval(String value) {
        return DurationStyle.detectAndParse(value);
    }
}
