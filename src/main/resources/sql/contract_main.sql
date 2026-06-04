CREATE TABLE `contract_main` (
  `contract_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'contract id',
  `contract_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'contract no',
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'title',
  `type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'contract type',
  `amount` decimal(15,2) NOT NULL DEFAULT '0.00' COMMENT 'amount',
  `counterparty` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'counterparty',
  `dept_id` bigint unsigned NOT NULL COMMENT 'dept id',
  `owner_id` bigint unsigned NOT NULL COMMENT 'owner id',
  `template_id` bigint unsigned DEFAULT NULL COMMENT 'template id',
  `status` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'DRAFT' COMMENT 'status',
  `due_date` date DEFAULT NULL COMMENT 'due date',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT 'delete flag',
  PRIMARY KEY (`contract_id`),
  UNIQUE KEY `uk_contract_no` (`contract_no`),
  KEY `idx_contract_composite` (`type`,`status`,`dept_id`,`owner_id`,`due_date`,`created_at`),
  KEY `idx_template_id` (`template_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1100 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='contract main';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `contract_main`
--

LOCK TABLES `contract_main` WRITE;
/*!40000 ALTER TABLE `contract_main` DISABLE KEYS */;
INSERT INTO `contract_main` VALUES (1001,'HT-2026-1001','设备采购合同','PURCHASE',128000.00,'深圳星河制造有限公司',4,3,1,'APPROVING','2026-06-29','2026-05-29 11:44:38','2026-06-03 11:44:38',0),(1002,'HT-2026-1002','企业技术服务合同','TECH',46000.00,'杭州云策科技有限公司',4,3,3,'DRAFT','2026-09-03','2026-05-31 11:44:38','2026-06-03 11:44:38',0),(1003,'HT-2026-1003','软件系统销售合同','SALES',320000.00,'成都数联信息有限公司',4,3,2,'EXECUTING','2026-06-11','2026-05-19 11:44:38','2026-06-03 11:44:38',0),(1004,'HT-2026-1004','知识产权合作协议','TECH',880000.00,'北京衡信知识产权代理有限公司',4,3,3,'DRAFT','2026-10-01','2026-06-01 11:44:38','2026-06-03 11:44:38',0);

