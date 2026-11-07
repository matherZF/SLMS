-- 系统配置项：把原先写在 application.yml 里的可调参数搬到数据库，运营可在「系统管理 → 系统配置」里直接改。
-- 代码里仍保留同名环境变量作为出厂默认值，表里没有记录时回落到它。
create table wms_system_config (
    id            bigint       not null auto_increment,
    config_group  varchar(32)  not null                comment '分组：SPARE_PART=备件与预警',
    config_key    varchar(64)  not null                comment '配置键，与代码里的常量一致',
    label         varchar(128) not null                comment '界面显示名',
    config_value  varchar(255) not null                comment '当前值，按 value_type 解析',
    default_value varchar(255) not null                comment '出厂默认值，用于「恢复默认」',
    value_type    varchar(16)  not null default 'INT'  comment 'INT / STRING / BOOL',
    unit          varchar(16)                          comment '单位，如 天',
    sort_order    int          not null default 0,
    remark        varchar(255)                         comment '作用与改动后果说明',
    enabled       tinyint      not null default 1,
    updated_at    datetime,
    primary key (id),
    unique key uk_sysconfig_key (config_key),
    key idx_sysconfig_group (config_group, enabled)
) engine = InnoDB default charset = utf8mb4 comment = '系统配置项';

-- 寿命预警阈值从「百分比」改为「提前天数」。
-- 百分比判据对长短寿命的备件提前量相差几十倍（一年寿命提前 73 天、10 天寿命只提前 2 天），按天数才稳定。
-- 旧列 life_warning_percent / warning_percent 保留不删：历史上已写入台账快照，删了旧数据读不出来。
alter table wms_product
    add column life_warning_days int not null default 3 comment '寿命预警提前天数：预计更换日临近这么多天就开始提醒' after life_warning_percent;

alter table wms_spare_part_usage
    add column warning_days int not null default 3 comment '寿命预警提前天数，建档时取自备件档案' after warning_percent;

-- 已有台账沿用备件档案上的新阈值
update wms_spare_part_usage u
    join wms_product p on p.id = u.product_id
    set u.warning_days = p.life_warning_days;
