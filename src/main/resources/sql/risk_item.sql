CREATE TABLE `risk_item` (
  `risk_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'risk id',
  `contract_id` bigint unsigned NOT NULL COMMENT 'contract id',
  `version_id` bigint unsigned NOT NULL COMMENT 'version id',
  `clause_ref` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'clause ref',
  `risk_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'risk type',
  `risk_level` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'risk level',
  `suggestion` text CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'suggestion',
  `review_status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'PENDING' COMMENT 'review status',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  PRIMARY KEY (`risk_id`),
  KEY `idx_contract_risk` (`contract_id`,`risk_level`),
  KEY `idx_version_id` (`version_id`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='risk item';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `risk_item`
--

LOCK TABLES `risk_item` WRITE;
/*!40000 ALTER TABLE `risk_item` DISABLE KEYS */;
INSERT INTO `risk_item` VALUES (1,1001,1,'付款条款','BUSINESS','MEDIUM','付款节点未绑定验收材料，建议增加发票、验收单和付款审批条件。','PENDING','2026-05-30 11:44:38'),(2,1004,4,'权属条款','LEGAL','HIGH','知识产权归属与衍生成果授权边界不清，提交审批前必须法务复核。','PENDING','2026-06-01 11:44:38'),(3,1003,2,'保密条款','LEGAL','LOW','保密期限与数据返还义务描述完整，建议保留当前表述。','CONFIRMED','2026-05-24 11:44:38');

