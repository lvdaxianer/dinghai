package io.github.lvdaxianer.projectiq.knowledge.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lvdaxianer.projectiq.knowledge.application.KnowledgeImportService;
import io.github.lvdaxianer.projectiq.knowledge.application.KnowledgePackageValidator;
import io.github.lvdaxianer.projectiq.knowledge.infrastructure.sqlite.SqliteKnowledgeStore;
import io.github.lvdaxianer.projectiq.knowledge.repository.KnowledgeStore;
import java.nio.file.Path;
import java.nio.file.Files;
import java.sql.SQLException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

/**
 * 装配本地可信目录导入能力，不引入根 Maven 模块依赖。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
@Configuration
public class KnowledgeImportConfiguration {
    /** 创建知识包校验器。 */
    @Bean
    KnowledgePackageValidator knowledgePackageValidator(ObjectMapper objectMapper) {
        return new KnowledgePackageValidator(objectMapper);
    }

    /** 创建启用外键约束的 SQLite 事实仓储。 */
    @Bean
    KnowledgeStore knowledgeStore(@Value("${productiq.sqlite-path:./data/productiq.db}") String sqlitePath)
            throws SQLException {
        try {
            Path database = Path.of(sqlitePath).toAbsolutePath().normalize();
            if (database.getParent() != null) {
                Files.createDirectories(database.getParent());
            }
        } catch (java.io.IOException exception) {
            throw new SQLException("无法创建 SQLite 数据目录", exception);
        }
        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        SQLiteDataSource dataSource = new SQLiteDataSource(config);
        dataSource.setUrl("jdbc:sqlite:" + sqlitePath);
        return new SqliteKnowledgeStore(dataSource);
    }

    /** 创建可信目录导入服务。 */
    @Bean
    KnowledgeImportService knowledgeImportService(
            @Value("${productiq.trusted-directory:./knowledge/trusted}") String trustedDirectory,
            KnowledgePackageValidator validator, KnowledgeStore store) {
        return new KnowledgeImportService(Path.of(trustedDirectory), validator, store);
    }
}
