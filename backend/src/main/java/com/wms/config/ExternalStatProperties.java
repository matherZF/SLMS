package com.wms.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 外部系统设备运行数据同步配置（WCS / WMS ...）。
 * 每个 source 是一个独立的只读数据源，暴露同名同构的按天汇总视图即可接入。
 */
@ConfigurationProperties(prefix = "ext-stat")
public class ExternalStatProperties {
    /** 总开关，false 时整条同步链路不装配 */
    private boolean enabled = false;
    /** 默认每天 01:30 跑前一天（01:30 跑比 23:00 跑当天更完整） */
    private String cron = "0 30 1 * * *";
    private String zone = "Asia/Shanghai";
    private List<Source> sources = new ArrayList<>();

    public static class Source {
        /** 来源系统标识，落表到 source_sys：WCS / WMS */
        private String code;
        private boolean enabled = true;
        /** 显示用的对端名称，写进接口日志 */
        private String peer;
        private String url;
        private String username;
        private String password;
        private String driverClassName = "oracle.jdbc.OracleDriver";
        /**
         * 两个日期参数的传参方式：
         * 留空 = 按 java.sql.Date 传（源库 stat_date 是 DATE 类型时用，Oracle/MySQL 均可）；
         * 填日期格式（如 yyyyMMdd）= 按字符串传，源库 stat_date 是 varchar 存 'yyyyMMdd' 时必填，
         * 否则 MySQL 无法把 varchar 隐式转成 DATE，条件恒不成立，会静默拉到 0 条。
         */
        private String dateParamFormat;
        /**
         * 拉取 SQL：固定列顺序 (device_type, device_code, running_hours, task_count, total_qty)
         * 两个参数分别是统计日零点、次日零点，即 [date, date+1)
         * 源库还没加件数列时，把 select 里的 total_qty 去掉即可（缺列按 0 处理）
         */
        private String query = "select device_type, device_code, running_hours, task_count, total_qty "
                + "from vw_sp_device_stat_daily where stat_date >= ? and stat_date < ?";
        private int queryTimeoutSeconds = 30;
        private int maximumPoolSize = 2;

        public String getCode() { return code; }
        public void setCode(String v) { code = v; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { enabled = v; }
        public String getPeer() { return peer; }
        public void setPeer(String v) { peer = v; }
        public String getUrl() { return url; }
        public void setUrl(String v) { url = v; }
        public String getUsername() { return username; }
        public void setUsername(String v) { username = v; }
        public String getPassword() { return password; }
        public void setPassword(String v) { password = v; }
        public String getDriverClassName() { return driverClassName; }
        public void setDriverClassName(String v) { driverClassName = v; }
        public String getQuery() { return query; }
        public void setQuery(String v) { query = v; }
        public String getDateParamFormat() { return dateParamFormat; }
        public void setDateParamFormat(String v) { dateParamFormat = v; }
        public int getQueryTimeoutSeconds() { return queryTimeoutSeconds; }
        public void setQueryTimeoutSeconds(int v) { queryTimeoutSeconds = v; }
        public int getMaximumPoolSize() { return maximumPoolSize; }
        public void setMaximumPoolSize(int v) { maximumPoolSize = v; }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { enabled = v; }
    public String getCron() { return cron; }
    public void setCron(String v) { cron = v; }
    public String getZone() { return zone; }
    public void setZone(String v) { zone = v; }
    public List<Source> getSources() { return sources; }
    public void setSources(List<Source> v) { sources = v; }
}
