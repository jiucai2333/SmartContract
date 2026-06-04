CREATE TABLE `user_info` (
  `user_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'user id',
  `username` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'username',
  `password` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'password hash',
  `role_id` bigint unsigned DEFAULT NULL COMMENT 'role id',
  `dept_id` bigint unsigned NOT NULL COMMENT 'dept id',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT 'status',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT 'delete flag',
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_dept_id` (`dept_id`),
  KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='user info';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user_info`
--

LOCK TABLES `user_info` WRITE;
/*!40000 ALTER TABLE `user_info` DISABLE KEYS */;
INSERT INTO `user_info` VALUES (1,'admin','$2b$10$iA0EswHWkeiVPlKTFtkrj.ymTveFlNNW/bQgIp6zlFiuTG3NxRxr6',1,1,1,'2026-06-03 11:44:38','2026-06-03 11:44:38',0),(2,'legal','$2b$10$AkONnpahKND41VtPU/2kaO797FqxoxLKN7XaqWAQ2xo3Ld3qa6IaG',2,2,1,'2026-06-03 11:44:38','2026-06-03 11:44:38',0),(3,'user','$2b$10$5kyzzboHhRnJPOKJhyLy6eDzl88/oUkIoDBt2SXsJ5fcTOxxpxrXm',4,4,1,'2026-06-03 11:44:38','2026-06-03 11:44:38',0),(4,'dept_leader','$2b$10$IY4wZpFrkpJY9RNeDpj/vuDtP3p/NoE4fZ4DZOAhAn3pCQAAOLKwu',5,4,1,'2026-06-03 11:44:38','2026-06-03 11:44:38',0),(5,'finance','$2b$10$IY4wZpFrkpJY9RNeDpj/vuDtP3p/NoE4fZ4DZOAhAn3pCQAAOLKwu',3,3,1,'2026-06-03 11:44:38','2026-06-03 11:44:38',0),(6,'executive','$2b$10$IY4wZpFrkpJY9RNeDpj/vuDtP3p/NoE4fZ4DZOAhAn3pCQAAOLKwu',6,1,1,'2026-06-03 11:44:38','2026-06-03 11:44:38',0);

