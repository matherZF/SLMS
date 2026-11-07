ALTER TABLE `wms_workflow_definition`
    DROP INDEX `UKiv7vqmfga7amaagyfd5aqpenx`,
    ADD COLUMN `version` INT NOT NULL DEFAULT 1 AFTER `business_type`,
    ADD COLUMN `published_at` DATETIME(6) NULL AFTER `min_total_qty`,
    ADD UNIQUE KEY `uk_workflow_business_version` (`business_type`, `version`);

UPDATE `wms_workflow_definition`
SET `published_at` = CURRENT_TIMESTAMP(6)
WHERE `published_at` IS NULL;

ALTER TABLE `wms_approval_record`
    ADD COLUMN `workflow_version` INT NULL AFTER `workflow_definition_id`,
    ADD COLUMN `match_reason` VARCHAR(1000) NULL AFTER `workflow_version`;

UPDATE `wms_approval_record` a
LEFT JOIN `wms_workflow_definition` w ON w.id = a.workflow_definition_id
SET a.workflow_version = w.version
WHERE a.workflow_definition_id IS NOT NULL;
