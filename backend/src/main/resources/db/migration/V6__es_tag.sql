-- V6: 电子标签（墨水屏）基础信息表
-- 存储 WMS 管理的电子标签清单，MAC 为业务主键，可关联到库位(Location.device_no)。
-- 电量、在线状态、固件版本等运行时信息可通过 EslService 从墨水屏服务器同步更新。
CREATE TABLE IF NOT EXISTS wms_es_tag (
  id               BIGINT        AUTO_INCREMENT PRIMARY KEY,
  mac              VARCHAR(64)   NOT NULL COMMENT '标签 MAC 地址（例 99.26.17.85），业务唯一',
  name             VARCHAR(128)  NULL     COMMENT '标签备注名称（例：AGV 区拣货标签 01）',
  type             VARCHAR(32)   NOT NULL DEFAULT 'LIGHT_LED' COMMENT '标签类型：LIGHT_LED 等',
  firmware_version VARCHAR(64)   NULL     COMMENT '固件版本号，从墨水屏服务器同步',
  power            INT           NULL     COMMENT '剩余电量百分比 0~100',
  online           TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '在线状态 0=离线 1=在线',
  last_seen_at     DATETIME      NULL     COMMENT '最后一次与墨水屏服务器通信的时间',
  location_id      BIGINT        NULL     COMMENT '绑定的库位 ID（= wms_location.id）',
  remark           VARCHAR(255)  NULL     COMMENT '备注',
  status           VARCHAR(32)   NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / DISABLED',
  created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_es_tag_mac (mac),
  KEY idx_es_tag_location (location_id),
  KEY idx_es_tag_online (online)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='电子标签基础信息';
