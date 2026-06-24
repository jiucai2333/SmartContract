CREATE TABLE `payment_plan` (
  `payment_plan_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'payment plan id',
  `contract_id` bigint unsigned NOT NULL COMMENT 'contract id',
  `phase_name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'phase name',
  `percentage` decimal(8,2) NOT NULL DEFAULT '0.00' COMMENT 'payment percentage',
  `planned_amount` decimal(15,2) NOT NULL DEFAULT '0.00' COMMENT 'planned amount',
  `due_date` date NOT NULL COMMENT 'due date',
  `prerequisite_delivery` varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'prerequisite delivery',
  `penalty_rate` decimal(8,4) NOT NULL DEFAULT '0.0500' COMMENT 'daily penalty rate percent',
  `status` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'UNPAID' COMMENT 'status',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'remark',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT 'delete flag',
  PRIMARY KEY (`payment_plan_id`),
  KEY `idx_payment_plan_contract` (`contract_id`,`due_date`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='payment plan';
