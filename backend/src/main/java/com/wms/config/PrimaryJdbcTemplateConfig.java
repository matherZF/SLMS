package com.wms.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * 主库 JdbcTemplate。
 * <p>
 * 项目里除主库外还有两个"只读外部数据源"（设备运行视图、WCS/WMS 日汇总同步），
 * 它们各自提供了本地的 {@code JdbcTemplate}。原来业务代码直接按类型注入 {@code JdbcTemplate}，
 * 一旦有人打开 {@code device.datasource.enabled}，就会出现多个候选 bean，注入结果取决于
 * Spring 的兜底匹配规则——极容易把 SQL 打到外部库上。
 * <p>
 * 这里显式提供一个 {@code @Primary} 的主库模板，把"默认打主库"变成确定行为；
 * 需要外部库的地方必须写上 {@code @Qualifier}。
 */
@Configuration
public class PrimaryJdbcTemplateConfig {

    @Bean("primaryJdbcTemplate")
    @Primary
    public JdbcTemplate primaryJdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
