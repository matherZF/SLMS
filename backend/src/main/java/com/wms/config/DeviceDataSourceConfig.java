package com.wms.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@ConditionalOnProperty(prefix = "device.datasource", name = "enabled", havingValue = "true")
public class DeviceDataSourceConfig {
    @Bean(destroyMethod = "close")
    HikariDataSource deviceDataSource(
            @Value("${device.datasource.url}") String url,
            @Value("${device.datasource.username}") String username,
            @Value("${device.datasource.password}") String password,
            @Value("${device.datasource.driver-class-name}") String driverClassName) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setPoolName("DeviceViewReadOnlyPool");
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setDriverClassName(driverClassName);
        dataSource.setReadOnly(true);
        dataSource.setMaximumPoolSize(3);
        dataSource.setConnectionTimeout(5_000);
        dataSource.setValidationTimeout(3_000);
        return dataSource;
    }

    @Bean
    JdbcTemplate deviceJdbcTemplate(HikariDataSource deviceDataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(deviceDataSource);
        jdbcTemplate.setQueryTimeout(10);
        return jdbcTemplate;
    }
}
