package com.atguigu.gmall.sms.config;

import com.zaxxer.hikari.HikariDataSource;
import io.seata.rm.datasource.DataSourceProxy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * 数据源配置
 *
 * @author HelloWoodes
 */
@Configuration
public class DataSourceConfig {

//    @Bean
//    @ConfigurationProperties(prefix = "spring.datasource")
//    public HikariDataSource hikariDataSource() {
//        return hikariDataSource;
//    }

    /**
     * 需要将 DataSourceProxy 设置为主数据源，否则事务无法回滚
     *
     * @return The default datasource
     */
    @Primary   // 告诉spring容器 使用这个数据源
    @Bean("dataSource")
    // @ConfigurationProperties(prefix = "spring.datasource")
    // 从yml或者properties配置文件中读取spring.datasource
    public DataSource dataSource(
            @Value("${spring.datasource.url}")String url,
            @Value("${spring.datasource.driver-class-name}")String driver,
            @Value("${spring.datasource.username}")String username,
            @Value("${spring.datasource.password}")String password
    ) {
        HikariDataSource hikariDataSource = new HikariDataSource();
        hikariDataSource.setJdbcUrl(url);
        hikariDataSource.setDriverClassName(driver);
        hikariDataSource.setUsername(username);
        hikariDataSource.setPassword(password);
        return new DataSourceProxy(hikariDataSource);
    }
}
