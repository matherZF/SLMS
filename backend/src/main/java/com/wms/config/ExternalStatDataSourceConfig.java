package com.wms.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 为每个外部系统（WCS / WMS ...）创建独立的只读数据源。
 * key 是来源系统标识 source_sys，同步任务按这个标识把数据写到本地表。
 */
@Configuration
@ConditionalOnProperty(prefix = "ext-stat", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(ExternalStatProperties.class)
public class ExternalStatDataSourceConfig implements DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(ExternalStatDataSourceConfig.class);

    private final List<HikariDataSource> pools = new ArrayList<>();

    @Bean("externalStatJdbcTemplates")
    public Map<String, JdbcTemplate> externalStatJdbcTemplates(ExternalStatProperties props) {
        Map<String, JdbcTemplate> templates = new LinkedHashMap<>();
        for (ExternalStatProperties.Source source : props.getSources()) {
            if (!source.isEnabled() || !StringUtils.hasText(source.getUrl())) continue;
            if (!StringUtils.hasText(source.getPassword())) {
                // 密码故意不给默认值（避免明文密码随 jar 分发），部署时漏配就在启动日志里说清楚，
                // 别等到凌晨定时任务跑的时候才静默失败。
                log.error("[ExtStat] 数据源 {} 跳过：密码未配置。请设置环境变量 EXT_STAT_{}_PASSWORD。",
                        source.getCode(), source.getCode());
                continue;
            }
            HikariDataSource ds = new HikariDataSource();
            ds.setPoolName("ExtStat-" + source.getCode() + "-Pool");
            ds.setJdbcUrl(source.getUrl());
            ds.setUsername(source.getUsername());
            ds.setPassword(source.getPassword());
            try {
                // 驱动类缺失时 HikariConfig 抛 RuntimeException。这里只跳过这一个源，
                // 不能让一个可选外部源的驱动问题把整个应用启动搞崩（曾经因此启动失败）。
                ds.setDriverClassName(source.getDriverClassName());
            } catch (RuntimeException e) {
                log.error("[ExtStat] 数据源 {} 跳过：无法加载驱动 {}（{}）。"
                                + "该源本次同步会显示为\"未配置或数据源未启用\"，其余源不受影响。",
                        source.getCode(), source.getDriverClassName(), e.getMessage());
                closeQuietly(ds);
                continue;
            }
            ds.setReadOnly(true);
            ds.setMaximumPoolSize(Math.max(1, source.getMaximumPoolSize()));
            ds.setConnectionTimeout(10_000);
            ds.setValidationTimeout(3_000);
            ds.setIdleTimeout(600_000);
            pools.add(ds);
            JdbcTemplate template = new JdbcTemplate(ds);
            template.setQueryTimeout(source.getQueryTimeoutSeconds());
            templates.put(source.getCode(), template);
        }
        return templates;
    }

    @Override
    public void destroy() {
        for (HikariDataSource ds : pools) {
            closeQuietly(ds);
        }
    }

    private static void closeQuietly(HikariDataSource ds) {
        try {
            ds.close();
        } catch (RuntimeException ignored) {
            // 关闭失败不影响 JVM 退出
        }
    }
}
