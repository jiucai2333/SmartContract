CREATE TABLE `role_info` (
  `role_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'role id',
  `role_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'role code',
  `role_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'role name',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT 'delete flag',
  PRIMARY KEY (`role_id`),
  UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='role info';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `role_info`
--

LOCK TABLES `role_info` WRITE;
/*!40000 ALTER TABLE `role_info` DISABLE KEYS */;
INSERT INTO `role_info` VALUES (1,'ADMIN','系统管理员',0),(2,'LEGAL','法务专员',0),(3,'FINANCE','财务专员',0),(4,'USER','普通员工',0),(5,'DEPT_LEADER','部门主管',0),(6,'EXECUTIVE','企业高管',0);

