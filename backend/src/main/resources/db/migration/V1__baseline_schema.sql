-- SP 备品备件系统 MySQL 8.0 基线结构。
-- 仅包含建表结构，不包含 DROP、建库语句或业务数据。
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wms_approval_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `applicant` varchar(64) NOT NULL,
  `approver` varchar(64) DEFAULT NULL,
  `business_id` bigint NOT NULL,
  `business_no` varchar(64) NOT NULL,
  `business_type` varchar(32) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `handled_at` datetime(6) DEFAULT NULL,
  `opinion` varchar(500) DEFAULT NULL,
  `round_no` int NOT NULL,
  `status` varchar(16) NOT NULL,
  `submitted_at` datetime(6) NOT NULL,
  `approver_type` varchar(16) DEFAULT NULL,
  `approver_value` varchar(64) DEFAULT NULL,
  `node_name` varchar(64) DEFAULT NULL,
  `node_sequence` int DEFAULT NULL,
  `workflow_definition_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_approval_business` (`business_type`,`business_id`),
  KEY `idx_approval_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wms_batch` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `item_key` varchar(64) NOT NULL,
  `manufacturer` varchar(128) DEFAULT NULL,
  `product_id` bigint NOT NULL,
  `product_name` varchar(128) DEFAULT NULL,
  `production_date` date DEFAULT NULL,
  `shelf_life_days` int DEFAULT NULL,
  `sku` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKdqkikjmrs6ildnctswkffi2eo` (`item_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wms_data_dictionary` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `dict_type` varchar(32) NOT NULL,
  `enabled` bit(1) NOT NULL DEFAULT b'1',
  `item_code` varchar(64) NOT NULL,
  `item_label` varchar(128) NOT NULL,
  `remark` varchar(255) DEFAULT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dict_type_code` (`dict_type`,`item_code`),
  KEY `idx_dict_type_enabled` (`dict_type`,`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wms_equipment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(64) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `location` varchar(128) DEFAULT NULL,
  `manufacturer` varchar(128) DEFAULT NULL,
  `model` varchar(128) DEFAULT NULL,
  `name` varchar(128) NOT NULL,
  `remark` varchar(255) DEFAULT NULL,
  `status` varchar(32) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKii35n9ly9sj4md6aja7c7qhev` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wms_equipment_part` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `consumption_cycle_days` int DEFAULT NULL,
  `installed_qty` int NOT NULL,
  `last_used_date` date DEFAULT NULL,
  `life_cycle_days` int DEFAULT NULL,
  `next_expected_use_date` date DEFAULT NULL,
  `procurement_lead_days` int DEFAULT NULL,
  `safety_stock` int NOT NULL,
  `equipment_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKb49p67c3lh030bcspmvmtqbkh` (`equipment_id`,`product_id`),
  KEY `FKmlupqoniyql2vydyhk195k40c` (`product_id`),
  CONSTRAINT `FKb8u9a3ky1vfd1tbeveovp49tk` FOREIGN KEY (`equipment_id`) REFERENCES `wms_equipment` (`id`),
  CONSTRAINT `FKmlupqoniyql2vydyhk195k40c` FOREIGN KEY (`product_id`) REFERENCES `wms_product` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wms_equipment_usage` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `life_cycle_days` int DEFAULT NULL,
  `next_expected_use_date` date DEFAULT NULL,
  `outbound_order_no` varchar(64) DEFAULT NULL,
  `qty` int NOT NULL,
  `used_at` datetime(6) DEFAULT NULL,
  `equipment_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKo2sj3mms9wg4n9ysb6as4t9tw` (`equipment_id`),
  KEY `FK2vqiwy4a9d0w5q3i69ll83ko7` (`product_id`),
  CONSTRAINT `FK2vqiwy4a9d0w5q3i69ll83ko7` FOREIGN KEY (`product_id`) REFERENCES `wms_product` (`id`),
  CONSTRAINT `FKo2sj3mms9wg4n9ysb6as4t9tw` FOREIGN KEY (`equipment_id`) REFERENCES `wms_equipment` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wms_inventory` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `qty` int NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `version` bigint DEFAULT NULL,
  `batch_id` bigint DEFAULT NULL,
  `location_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKbowodpvnyh9eitpdf22oy9ng5` (`product_id`,`location_id`,`batch_id`),
  KEY `FKepl74i5dx321e7nkjrpl9vor4` (`batch_id`),
  KEY `FK48jdknmsvf49rfdf1fobm0xl9` (`location_id`),
  CONSTRAINT `FK48jdknmsvf49rfdf1fobm0xl9` FOREIGN KEY (`location_id`) REFERENCES `wms_location` (`id`),
  CONSTRAINT `FKepl74i5dx321e7nkjrpl9vor4` FOREIGN KEY (`batch_id`) REFERENCES `wms_batch` (`id`),
  CONSTRAINT `FKhil9t35uu585c2dhrxghghbim` FOREIGN KEY (`product_id`) REFERENCES `wms_product` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wms_inventory_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `after_qty` int NOT NULL,
  `before_qty` int NOT NULL,
  `change_qty` int NOT NULL,
  `change_type` varchar(16) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `item_key` varchar(64) DEFAULT NULL,
  `location_code` varchar(64) DEFAULT NULL,
  `location_id` bigint DEFAULT NULL,
  `manufacturer` varchar(128) DEFAULT NULL,
  `operator` varchar(64) DEFAULT NULL,
  `product_id` bigint NOT NULL,
  `product_name` varchar(128) DEFAULT NULL,
  `production_date` date DEFAULT NULL,
  `ref_no` varchar(64) DEFAULT NULL,
  `ref_type` varchar(32) DEFAULT NULL,
  `remark` varchar(255) DEFAULT NULL,
  `shelf_life_days` int DEFAULT NULL,
  `sku` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_log_product` (`product_id`),
  KEY `idx_log_location` (`location_id`),
  KEY `idx_log_change_type` (`change_type`),
  KEY `idx_log_created` (`created_at`),
  KEY `idx_log_ref_no` (`ref_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wms_location` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `area` varchar(64) DEFAULT NULL,
  `code` varchar(64) NOT NULL,
  `device_no` varchar(64) DEFAULT NULL,
  `remark` varchar(255) DEFAULT NULL,
  `type` varchar(64) DEFAULT NULL,
  `enabled` bit(1) NOT NULL DEFAULT b'1',
  `level_no` int DEFAULT NULL,
  `position_no` int DEFAULT NULL,
  `rack_no` int DEFAULT NULL,
  `storage_mode` varchar(16) DEFAULT NULL,
  `warehouse_name` varchar(128) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKt7ltcg81yu0xpkshltiqt7b4` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wms_operation_audit_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `action` varchar(32) NOT NULL,
  `business_id` bigint NOT NULL,
  `business_no` varchar(64) DEFAULT NULL,
  `business_type` varchar(32) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `detail` varchar(1000) DEFAULT NULL,
  `from_status` varchar(32) DEFAULT NULL,
  `operator` varchar(64) DEFAULT NULL,
  `to_status` varchar(32) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_audit_business` (`business_type`,`business_id`),
  KEY `idx_audit_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wms_outbound_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `picked_qty` int NOT NULL,
  `requested_qty` int NOT NULL,
  `batch_id` bigint DEFAULT NULL,
  `location_id` bigint DEFAULT NULL,
  `outbound_order_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  `scan_verified` bit(1) NOT NULL,
  `scan_verified_at` datetime(6) DEFAULT NULL,
  `returned_qty` int NOT NULL DEFAULT '0',
  `product_name_snapshot` varchar(128) DEFAULT NULL,
  `product_sku_snapshot` varchar(64) DEFAULT NULL,
  `product_spec_snapshot` varchar(255) DEFAULT NULL,
  `product_unit_snapshot` varchar(32) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKd5ij6k0l9j4dur0gufdltcinh` (`batch_id`),
  KEY `FKcerdnjsnr4tg7beurs1xljtiy` (`location_id`),
  KEY `FK4vk10sqf41qyxwwrwp66nulh` (`outbound_order_id`),
  KEY `FKc3992brxr0tv0o7ob5u0e2jqk` (`product_id`),
  CONSTRAINT `FK4vk10sqf41qyxwwrwp66nulh` FOREIGN KEY (`outbound_order_id`) REFERENCES `wms_outbound_order` (`id`),
  CONSTRAINT `FKc3992brxr0tv0o7ob5u0e2jqk` FOREIGN KEY (`product_id`) REFERENCES `wms_product` (`id`),
  CONSTRAINT `FKcerdnjsnr4tg7beurs1xljtiy` FOREIGN KEY (`location_id`) REFERENCES `wms_location` (`id`),
  CONSTRAINT `FKd5ij6k0l9j4dur0gufdltcinh` FOREIGN KEY (`batch_id`) REFERENCES `wms_batch` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wms_outbound_order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `order_no` varchar(64) NOT NULL,
  `status` varchar(32) NOT NULL,
  `equipment_id` bigint DEFAULT NULL,
  `applicant` varchar(64) DEFAULT NULL,
  `purpose` varchar(500) DEFAULT NULL,
  `review_opinion` varchar(500) DEFAULT NULL,
  `reviewed_at` datetime(6) DEFAULT NULL,
  `reviewed_by` varchar(64) DEFAULT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  `completed_by` varchar(64) DEFAULT NULL,
  `contact_phone` varchar(32) DEFAULT NULL,
  `delivery_location` varchar(128) DEFAULT NULL,
  `department` varchar(64) DEFAULT NULL,
  `picked_at` datetime(6) DEFAULT NULL,
  `picked_by` varchar(64) DEFAULT NULL,
  `recipient` varchar(64) DEFAULT NULL,
  `required_date` date DEFAULT NULL,
  `work_order_no` varchar(64) DEFAULT NULL,
  `equipment_code_snapshot` varchar(64) DEFAULT NULL,
  `equipment_name_snapshot` varchar(128) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK82ukd380lqixi8jxn3oe5ljvg` (`order_no`),
  KEY `FKl78yn918c7l4js082di9qde45` (`equipment_id`),
  CONSTRAINT `FKl78yn918c7l4js082di9qde45` FOREIGN KEY (`equipment_id`) REFERENCES `wms_equipment` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wms_pending_business_action` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `business_no` varchar(64) NOT NULL,
  `business_type` varchar(32) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `created_by` varchar(64) NOT NULL,
  `finished_at` datetime(6) DEFAULT NULL,
  `payload` text NOT NULL,
  `source_id` bigint NOT NULL,
  `status` varchar(16) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wms_product` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `name` varchar(128) NOT NULL,
  `sku` varchar(64) NOT NULL,
  `spec` varchar(255) DEFAULT NULL,
  `unit` varchar(32) DEFAULT NULL,
  `batch_managed` bit(1) NOT NULL DEFAULT b'1',
  `brand` varchar(64) DEFAULT NULL,
  `category` varchar(64) DEFAULT NULL,
  `criticality` varchar(16) NOT NULL DEFAULT 'NORMAL',
  `default_shelf_life_days` int DEFAULT NULL,
  `drawing_no` varchar(64) DEFAULT NULL,
  `enabled` bit(1) NOT NULL DEFAULT b'1',
  `manufacturer` varchar(128) DEFAULT NULL,
  `max_stock` int DEFAULT NULL,
  `min_stock` int NOT NULL DEFAULT '0',
  `model` varchar(128) DEFAULT NULL,
  `procurement_lead_days` int NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  `safety_stock` int NOT NULL DEFAULT '0',
  `serial_managed` bit(1) NOT NULL DEFAULT b'0',
  `updated_at` datetime(6) DEFAULT NULL,
  `vulnerable` bit(1) NOT NULL DEFAULT b'0',
  `life_metric` varchar(16) NOT NULL DEFAULT 'NONE',
  `life_warning_percent` int NOT NULL DEFAULT '80',
  `rated_life_value` decimal(18,2) DEFAULT NULL,
  `reference_unit_price` decimal(18,2) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKkdigqebtln1w4vm1m6wo2g336` (`sku`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wms_putaway_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `qty` int NOT NULL,
  `location_id` bigint NOT NULL,
  `putaway_order_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKr2nm926m2x6aqh5kiiboeq0fg` (`location_id`),
  KEY `FK5nwqbabe3k0atc8k55xx5nx4o` (`putaway_order_id`),
  CONSTRAINT `FK5nwqbabe3k0atc8k55xx5nx4o` FOREIGN KEY (`putaway_order_id`) REFERENCES `wms_putaway_order` (`id`),
  CONSTRAINT `FKr2nm926m2x6aqh5kiiboeq0fg` FOREIGN KEY (`location_id`) REFERENCES `wms_location` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wms_putaway_order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `order_no` varchar(64) NOT NULL,
  `plan_qty` int NOT NULL,
  `put_qty` int NOT NULL,
  `status` enum('PENDING','DONE') NOT NULL,
  `batch_id` bigint DEFAULT NULL,
  `product_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKbhos6tjjvxqj479m9l713us04` (`order_no`),
  KEY `FKe6f1y3pvc85k31qg14jhguydf` (`batch_id`),
  KEY `FK2rkiywa5h1djcie840ng1plyv` (`product_id`),
  CONSTRAINT `FK2rkiywa5h1djcie840ng1plyv` FOREIGN KEY (`product_id`) REFERENCES `wms_product` (`id`),
  CONSTRAINT `FKe6f1y3pvc85k31qg14jhguydf` FOREIGN KEY (`batch_id`) REFERENCES `wms_batch` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wms_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `builtin` bit(1) NOT NULL,
  `code` varchar(32) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `enabled` bit(1) NOT NULL,
  `name` varchar(64) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKson9sryje9u96r1a20skilont` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wms_role_permission` (
  `role_id` bigint NOT NULL,
  `permission_code` varchar(64) NOT NULL,
  PRIMARY KEY (`role_id`,`permission_code`),
  CONSTRAINT `FKdl2a9ypd53bm1vcbpy2b6u87j` FOREIGN KEY (`role_id`) REFERENCES `wms_role` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wms_spare_part_usage` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `baseline_running_hours` decimal(18,2) DEFAULT NULL,
  `baseline_status` varchar(24) NOT NULL,
  `baseline_task_count` bigint DEFAULT NULL,
  `batch_id` bigint DEFAULT NULL,
  `batch_no` varchar(64) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `current_running_hours` decimal(18,2) DEFAULT NULL,
  `current_task_count` bigint DEFAULT NULL,
  `data_error` varchar(500) DEFAULT NULL,
  `equipment_code` varchar(64) DEFAULT NULL,
  `equipment_id` bigint DEFAULT NULL,
  `equipment_name` varchar(128) DEFAULT NULL,
  `last_synced_at` datetime(6) DEFAULT NULL,
  `life_metric` varchar(16) NOT NULL,
  `outbound_item_id` bigint NOT NULL,
  `outbound_order_id` bigint NOT NULL,
  `outbound_order_no` varchar(64) NOT NULL,
  `product_id` bigint NOT NULL,
  `product_name` varchar(128) NOT NULL,
  `qty` int NOT NULL,
  `rated_life_value` decimal(18,2) DEFAULT NULL,
  `sku` varchar(64) NOT NULL,
  `spec` varchar(255) DEFAULT NULL,
  `started_at` datetime(6) NOT NULL,
  `status` varchar(24) NOT NULL,
  `unit` varchar(32) DEFAULT NULL,
  `usage_no` varchar(64) NOT NULL,
  `warning_percent` int NOT NULL DEFAULT '80',
  `active_qty` int NOT NULL DEFAULT '0',
  `end_reason` varchar(500) DEFAULT NULL,
  `ended_at` datetime(6) DEFAULT NULL,
  `returned_qty` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_usage_outbound_item` (`outbound_item_id`),
  UNIQUE KEY `UK_484i3qxn2ms21cd4hfhbo5chc` (`usage_no`),
  KEY `idx_usage_product` (`product_id`),
  KEY `idx_usage_equipment` (`equipment_code`),
  KEY `idx_usage_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wms_spare_part_usage_snapshot` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `collected_at` datetime(6) NOT NULL,
  `remaining_life_value` decimal(18,2) DEFAULT NULL,
  `running_hours` decimal(18,2) DEFAULT NULL,
  `source` varchar(16) NOT NULL,
  `status` varchar(24) NOT NULL,
  `task_count` bigint DEFAULT NULL,
  `usage_id` bigint NOT NULL,
  `used_life_value` decimal(18,2) DEFAULT NULL,
  `used_percent` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_usage_snapshot_usage_time` (`usage_id`,`collected_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wms_stocktake_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `actual_qty` int DEFAULT NULL,
  `book_qty` int NOT NULL,
  `inventory_id` bigint NOT NULL,
  `stocktake_order_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK27karo2b2fyvts8m3wb2r9w5r` (`inventory_id`),
  KEY `FKf3hygml985jvwp8ca4h0vlry6` (`stocktake_order_id`),
  CONSTRAINT `FK27karo2b2fyvts8m3wb2r9w5r` FOREIGN KEY (`inventory_id`) REFERENCES `wms_inventory` (`id`),
  CONSTRAINT `FKf3hygml985jvwp8ca4h0vlry6` FOREIGN KEY (`stocktake_order_id`) REFERENCES `wms_stocktake_order` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wms_stocktake_order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `completed_at` datetime(6) DEFAULT NULL,
  `completed_by` varchar(64) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `created_by` varchar(64) DEFAULT NULL,
  `order_no` varchar(64) NOT NULL,
  `remark` varchar(255) DEFAULT NULL,
  `status` enum('COUNTING','DONE','CANCELLED') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK8wapsy14h4dnlu2pblf35demt` (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wms_user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `display_name` varchar(64) DEFAULT NULL,
  `enabled` bit(1) NOT NULL,
  `password` varchar(128) NOT NULL,
  `role` varchar(16) NOT NULL,
  `username` varchar(64) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKdmorvvvdqlfvt4o8g62nq857e` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wms_workflow_definition` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `business_type` varchar(32) NOT NULL,
  `enabled` bit(1) NOT NULL,
  `name` varchar(64) NOT NULL,
  `min_total_amount` decimal(18,2) DEFAULT NULL,
  `min_total_qty` int DEFAULT NULL,
  `trigger_categories` varchar(1000) DEFAULT NULL,
  `trigger_mode` varchar(16) NOT NULL DEFAULT 'ALWAYS',
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKiv7vqmfga7amaagyfd5aqpenx` (`business_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wms_workflow_node` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `approver_type` varchar(16) NOT NULL,
  `approver_value` varchar(64) NOT NULL,
  `name` varchar(64) NOT NULL,
  `sequence` int NOT NULL,
  `definition_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKigg96k6mlhbbkfkk4dxknqha7` (`definition_id`),
  CONSTRAINT `FKigg96k6mlhbbkfkk4dxknqha7` FOREIGN KEY (`definition_id`) REFERENCES `wms_workflow_definition` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;
