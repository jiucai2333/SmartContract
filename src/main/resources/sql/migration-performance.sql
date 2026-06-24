-- ============================================================
-- migration-performance.sql
-- 履约、交付物与付款台账 — 三张新表
-- ============================================================

CREATE TABLE `deliverable` (
  `deliverable_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'deliverable id',
  `plan_id` bigint unsigned NOT NULL COMMENT 'fulfillment plan id',
  `contract_id` bigint unsigned NOT NULL COMMENT 'contract id',
  `deliverable_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'DESIGN_DOC / SOURCE_CODE / RUNNABLE_PROGRAM / ACCEPTANCE_REPORT',
  `item_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'item name',
  `contract_stage` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'SIGNING / MID_DELIVERY / ACCEPTANCE',
  `is_confirmed` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '0-unconfirmed / 1-confirmed',
  `confirmed_at` datetime DEFAULT NULL COMMENT 'confirmed time',
  `confirmed_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'confirmed by',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT 'sort order',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT 'delete flag',
  PRIMARY KEY (`deliverable_id`),
  KEY `idx_plan_id` (`plan_id`),
  KEY `idx_contract_id` (`contract_id`),
  KEY `idx_contract_stage` (`contract_id`,`contract_stage`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='deliverable';

CREATE TABLE `payment_plan` (
  `payment_plan_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'payment plan id',
  `plan_id` bigint unsigned NOT NULL COMMENT 'fulfillment plan id',
  `contract_id` bigint unsigned NOT NULL COMMENT 'contract id',
  `installment_no` int NOT NULL COMMENT 'installment number (1,2,3...)',
  `ratio` decimal(5,4) NOT NULL COMMENT 'payment ratio (0.3000)',
  `amount` decimal(15,2) NOT NULL COMMENT 'installment amount',
  `due_date` date NOT NULL COMMENT 'due date',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / PAID / OVERDUE',
  `prerequisite_deliverable_id` bigint unsigned DEFAULT NULL COMMENT 'prerequisite deliverable condition',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT 'delete flag',
  PRIMARY KEY (`payment_plan_id`),
  KEY `idx_plan_id` (`plan_id`),
  KEY `idx_contract_id` (`contract_id`),
  KEY `idx_due_date` (`due_date`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='payment plan';

CREATE TABLE `payment_record` (
  `record_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'record id',
  `payment_plan_id` bigint unsigned NOT NULL COMMENT 'payment plan id',
  `contract_id` bigint unsigned NOT NULL COMMENT 'contract id',
  `paid_amount` decimal(15,2) NOT NULL COMMENT 'paid amount',
  `paid_at` datetime NOT NULL COMMENT 'payment time',
  `receipt_no` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'receipt number',
  `notes` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'notes',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT 'delete flag',
  PRIMARY KEY (`record_id`),
  KEY `idx_payment_plan_id` (`payment_plan_id`),
  KEY `idx_contract_id` (`contract_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='payment record';
