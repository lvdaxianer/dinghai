package io.github.lvdaxianer.dinghai.dsl.spring;

import io.github.lvdaxianer.dinghai.dsl.mysql.MysqlDataAdapter;
import io.github.lvdaxianer.dinghai.dsl.spi.DataAdapter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

/**
 * MySQL/Doris 公共协议数据适配器启动装配。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
@AutoConfiguration(after = DslEngineAutoConfiguration.class)
@ConditionalOnClass(MysqlDataAdapter.class)
@ConditionalOnProperty(
        prefix = "dinghai.data-query", name = "default-data-adapter", havingValue = "mysql", matchIfMissing = true)
public class MysqlDataAdapterAutoConfiguration {

    /**
     * 创建 MySQL 数据适配器。
     *
     * @param dataSources Spring 管理的数据源
     * @return MySQL 数据适配器
     */
    @Bean(name = "dinghaiMysqlDataAdapter")
    @ConditionalOnMissingBean(name = "dinghaiMysqlDataAdapter")
    public DataAdapter mysqlDataAdapter(ObjectProvider<DataSource> dataSources) {
        DataSource dataSource = dataSources.getIfAvailable();
        if (dataSource == null) {
            throw new DslBootstrapException(
                    "mysql data adapter requires a server-managed DataSource bean");
        }
        return new MysqlDataAdapter(dataSource);
    }
}
