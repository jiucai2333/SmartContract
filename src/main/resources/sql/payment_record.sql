CREATE TABLE `payment_record` (
  `record_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'payment record id',
  `payment_plan_id` bigint unsigned NOT NULL COMMENT 'payment plan id',
  `contract_id` bigint unsigned NOT NULL COMMENT 'contract id',
  `paid_amount` decimal(15,2) NOT NULL DEFAULT '0.00' COMMENT 'paid amount',
  `paid_date` date NOT NULL COMMENT 'paid date',
  `payer` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'payer',
  `receiver` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'receiver',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'remark',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT 'delete flag',
  PRIMARY KEY (`record_id`),
  KEY `idx_payment_record_plan` (`payment_plan_id`,`paid_date`),
  KEY `idx_payment_record_contract` (`contract_id`,`paid_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='payment record';
