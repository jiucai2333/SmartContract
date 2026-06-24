-- ============================================================
-- 签章登记 & 归档确认模块 — 数据库迁移脚本
-- ============================================================
USE `smart_contract_db`;

CREATE TABLE IF NOT EXISTS `seal_record` (
  `seal_id` BIGINT UNSIGNED AUTO_INCREMENT COMMENT '签章记录ID',
  `contract_id` BIGINT UNSIGNED NOT NULL,
  `version_id` BIGINT UNSIGNED NOT NULL,
  `file_id` BIGINT UNSIGNED DEFAULT NULL,
  `file_url` VARCHAR(512) DEFAULT NULL,
  `file_name` VARCHAR(255) DEFAULT NULL,
  `seal_status` VARCHAR(50) NOT NULL DEFAULT 'SEALED',
  `seal_time` DATETIME DEFAULT NULL,
  `operator_id` BIGINT UNSIGNED DEFAULT NULL,
  `remark` VARCHAR(500) DEFAULT NULL,
  `created_by` VARCHAR(64) DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` VARCHAR(64) DEFAULT NULL,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` TINYINT UNSIGNED NOT NULL DEFAULT 0,
  `version` INT UNSIGNED NOT NULL DEFAULT 1,
  PRIMARY KEY (`seal_id`),
  KEY `idx_contract_id` (`contract_id`),
  KEY `idx_version_id` (`version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS `archive_record` (
  `archive_id` BIGINT UNSIGNED AUTO_INCREMENT COMMENT '归档记录ID',
  `contract_id` BIGINT UNSIGNED NOT NULL,
  `version_id` BIGINT UNSIGNED NOT NULL,
  `archive_no` VARCHAR(64) NOT NULL,
  `archive_time` DATETIME NOT NULL,
  `archiver_id` BIGINT UNSIGNED DEFAULT NULL,
  `is_locked` TINYINT UNSIGNED NOT NULL DEFAULT 1,
  `created_by` VARCHAR(64) DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` VARCHAR(64) DEFAULT NULL,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` TINYINT UNSIGNED NOT NULL DEFAULT 0,
  `version` INT UNSIGNED NOT NULL DEFAULT 1,
  PRIMARY KEY (`archive_id`),
  KEY `idx_contract_id` (`contract_id`),
  KEY `idx_version_id` (`version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

DROP PROCEDURE IF EXISTS add_is_locked_column;
DELIMITER $$
CREATE PROCEDURE add_is_locked_column()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = 'smart_contract_db' AND TABLE_NAME = 'contract_version' AND COLUMN_NAME = 'is_locked'
    ) THEN
        ALTER TABLE `contract_version` ADD COLUMN `is_locked` TINYINT UNSIGNED NOT NULL DEFAULT 0 AFTER `content`;
    END IF;
END$$
DELIMITER ;
CALL add_is_locked_column();
DROP PROCEDURE IF EXISTS add_is_locked_column;

SELECT '签章归档迁移完成' AS message;
