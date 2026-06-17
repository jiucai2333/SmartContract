CREATE TABLE `reminder_record` (
  `reminder_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'reminder id',
  `plan_id` bigint unsigned NOT NULL COMMENT 'plan id',
  `contract_id` bigint unsigned NOT NULL COMMENT 'contract id',
  `reminder_level` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'reminder level',
  `reminder_date` date NOT NULL COMMENT 'reminder date',
  `channel` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'IN_APP' COMMENT 'channel',
  `receiver` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'receiver',
  `content` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'content',
  `send_status` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'SENT' COMMENT 'send status',
  `sent_at` datetime NOT NULL COMMENT 'sent time',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT 'delete flag',
  PRIMARY KEY (`reminder_id`),
  KEY `idx_reminder_plan` (`plan_id`,`reminder_level`,`reminder_date`),
  KEY `idx_reminder_contract` (`contract_id`,`sent_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='reminder record';
