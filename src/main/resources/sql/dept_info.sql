CREATE TABLE `dept_info` (
  `dept_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'dept id',
  `dept_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'dept name',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT 'delete flag',
  PRIMARY KEY (`dept_id`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='dept info';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `dept_info`
--

LOCK TABLES `dept_info` WRITE;
/*!40000 ALTER TABLE `dept_info` DISABLE KEYS */;
INSERT INTO `dept_info` VALUES (1,'XX科技有限公司',0),(2,'法务合规部',0),(3,'财务管理部',0),(4,'业务一部',0);

