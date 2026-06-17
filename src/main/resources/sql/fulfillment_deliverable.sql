CREATE TABLE `fulfillment_deliverable` (
  `deliverable_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'deliverable id',
  `contract_id` bigint unsigned NOT NULL COMMENT 'contract id',
  `deliverable_type` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'deliverable type',
  `deliverable_name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'deliverable name',
  `stage_name` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'stage name',
  `confirm_method` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'CHECKLIST' COMMENT 'confirm method',
  `confirmed` tinyint unsigned NOT NULL DEFAULT '0' COMMENT 'confirmed flag',
  `confirmer` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'confirmer',
  `confirmed_at` datetime DEFAULT NULL COMMENT 'confirmed time',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'remark',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT 'delete flag',
  PRIMARY KEY (`deliverable_id`),
  KEY `idx_deliverable_contract` (`contract_id`,`deliverable_type`,`confirmed`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='fulfillment deliverable';
