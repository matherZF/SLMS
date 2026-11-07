CREATE TABLE `wms_interface_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `direction` varchar(16) NOT NULL,
  `interface_code` varchar(64) NOT NULL,
  `interface_name` varchar(128) DEFAULT NULL,
  `channel` varchar(16) DEFAULT NULL,
  `peer` varchar(128) DEFAULT NULL,
  `request_body` longtext,
  `response_body` longtext,
  `success` bit(1) NOT NULL,
  `error_message` varchar(500) DEFAULT NULL,
  `trace_id` varchar(64) DEFAULT NULL,
  `duration_ms` bigint DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_interface_direction_time` (`direction`,`created_at`),
  KEY `idx_interface_code` (`interface_code`),
  KEY `idx_interface_trace` (`trace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
