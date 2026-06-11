CREATE TABLE `contract_version` (
  `version_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'version id',
  `contract_id` bigint unsigned NOT NULL COMMENT 'contract id',
  `version_no` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'version no',
  `content_hash` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'content hash',
  `file_id` bigint unsigned NOT NULL COMMENT 'file id',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'created by',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  PRIMARY KEY (`version_id`),
  KEY `idx_contract_id` (`contract_id`),
  KEY `idx_file_id` (`file_id`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='contract version';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `contract_version`
--

LOCK TABLES `contract_version` WRITE;
/*!40000 ALTER TABLE `contract_version` DISABLE KEYS */;
INSERT INTO `contract_version` VALUES (1,1001,'v1.0','hash1001v1abcdef1234567890abcdef1234567890abcdef1234567890ab',4,'user','2026-05-29 11:44:38'),(2,1003,'v1.0','hash1003v1abcdef1234567890abcdef1234567890abcdef1234567890ab',5,'admin','2026-05-19 11:44:38'),(3,1002,'v1.0','hash1002v1abcdef1234567890abcdef1234567890abcdef1234567890ab',1,'user','2026-05-31 11:44:38'),(4,1004,'v1.0','hash1004v1abcdef1234567890abcdef1234567890abcdef1234567890ab',3,'legal','2026-06-01 11:44:38');

