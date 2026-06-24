SET NAMES utf8mb4;
CREATE DATABASE IF NOT EXISTS smart_contract_db CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
USE smart_contract_db;

CREATE TABLE IF NOT EXISTS contract_attachment (
  attachment_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'attachment id',
  contract_id BIGINT UNSIGNED DEFAULT NULL COMMENT 'contract id',
  file_id BIGINT UNSIGNED NOT NULL COMMENT 'file id',
  attach_type VARCHAR(40) NOT NULL DEFAULT 'CONTRACT_FILE' COMMENT 'attachment type',
  ocr_status VARCHAR(30) NOT NULL DEFAULT 'PENDING' COMMENT 'ocr status',
  ocr_text LONGTEXT DEFAULT NULL COMMENT 'ocr html/text',
  ocr_error VARCHAR(500) DEFAULT NULL COMMENT 'ocr error',
  page_count INT DEFAULT NULL COMMENT 'page count',
  created_by VARCHAR(64) DEFAULT 'system' COMMENT 'created by',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  updated_by VARCHAR(64) DEFAULT 'system' COMMENT 'updated by',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
  is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'delete flag',
  version INT NOT NULL DEFAULT 1 COMMENT 'version',
  PRIMARY KEY (attachment_id),
  KEY idx_contract_id (contract_id),
  KEY idx_file_id (file_id),
  KEY idx_ocr_status (ocr_status),
  KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='contract attachment';
