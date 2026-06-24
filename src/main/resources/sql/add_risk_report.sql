SET NAMES utf8mb4;
CREATE DATABASE IF NOT EXISTS smart_contract_db CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
USE smart_contract_db;

CREATE TABLE IF NOT EXISTS risk_report (
  report_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'risk report id',
  contract_id BIGINT UNSIGNED DEFAULT NULL COMMENT 'contract id',
  version_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'version id',
  report_no VARCHAR(80) NOT NULL COMMENT 'report no',
  contract_type VARCHAR(80) DEFAULT NULL COMMENT 'contract type',
  party_a VARCHAR(200) DEFAULT NULL COMMENT 'party a',
  party_b VARCHAR(200) DEFAULT NULL COMMENT 'party b',
  business_scope VARCHAR(255) DEFAULT NULL COMMENT 'business scope',
  highest_risk_level VARCHAR(20) NOT NULL DEFAULT 'LOW' COMMENT 'highest risk level',
  risk_count INT NOT NULL DEFAULT 0 COMMENT 'risk count',
  high_count INT NOT NULL DEFAULT 0 COMMENT 'high risk count',
  medium_count INT NOT NULL DEFAULT 0 COMMENT 'medium risk count',
  low_count INT NOT NULL DEFAULT 0 COMMENT 'low risk count',
  contract_text LONGTEXT DEFAULT NULL COMMENT 'reviewed contract text',
  summary TEXT DEFAULT NULL COMMENT 'report summary',
  model_name VARCHAR(100) DEFAULT NULL COMMENT 'model name',
  review_status VARCHAR(30) NOT NULL DEFAULT 'COMPLETED' COMMENT 'review status',
  created_by VARCHAR(64) DEFAULT NULL COMMENT 'created by',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
  PRIMARY KEY (report_id),
  UNIQUE KEY uk_report_no (report_no),
  KEY idx_contract_version (contract_id, version_id),
  KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='AI risk report';

SET @has_report_id := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'risk_item'
    AND column_name = 'report_id'
);

SET @ddl := IF(
  @has_report_id = 0,
  'ALTER TABLE risk_item ADD COLUMN report_id BIGINT UNSIGNED DEFAULT NULL COMMENT ''risk report id'' AFTER risk_id',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_report_idx := (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'risk_item'
    AND index_name = 'idx_report_id'
);

SET @idx_ddl := IF(
  @has_report_idx = 0,
  'ALTER TABLE risk_item ADD KEY idx_report_id (report_id)',
  'SELECT 1'
);
PREPARE idx_stmt FROM @idx_ddl;
EXECUTE idx_stmt;
DEALLOCATE PREPARE idx_stmt;

SET @has_updated_at := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'risk_item'
    AND column_name = 'updated_at'
);

SET @updated_at_ddl := IF(
  @has_updated_at = 0,
  'ALTER TABLE risk_item ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''updated time'' AFTER created_at',
  'SELECT 1'
);
PREPARE updated_at_stmt FROM @updated_at_ddl;
EXECUTE updated_at_stmt;
DEALLOCATE PREPARE updated_at_stmt;
