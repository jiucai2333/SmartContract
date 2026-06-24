CREATE DATABASE  IF NOT EXISTS `smart_contract_db` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin */ /*!80016 DEFAULT ENCRYPTION='N' */;
USE `smart_contract_db`;
-- MySQL dump 10.13  Distrib 8.0.42, for Win64 (x86_64)
--
-- Host: localhost    Database: smart_contract_db
-- ------------------------------------------------------
-- Server version	8.0.42

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `user_info`
--

DROP TABLE IF EXISTS `user_info`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_info` (
  `user_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '用户名',
  `password` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '密码哈希',
  `role_id` bigint unsigned DEFAULT NULL COMMENT '主角色ID',
  `dept_id` bigint unsigned NOT NULL COMMENT '所属部门ID',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '用户状态：1启用，0禁用',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT 'SYSTEM_INIT' COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除：0未删除，1已删除',
  `version` int unsigned NOT NULL DEFAULT '1' COMMENT '乐观锁版本号',
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_dept_id` (`dept_id`),
  KEY `idx_role_id` (`role_id`),
  CONSTRAINT `fk_user_dept` FOREIGN KEY (`dept_id`) REFERENCES `dept_info` (`dept_id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_user_role` FOREIGN KEY (`role_id`) REFERENCES `role_info` (`role_id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='用户信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user_info`
--

LOCK TABLES `user_info` WRITE;
/*!40000 ALTER TABLE `user_info` DISABLE KEYS */;
INSERT INTO `user_info` VALUES (1,'admin','$2a$10$ZrlZqdAR7oe5ZH8951bE/.6nbiWlJurcP8473XvExmigiqo.4.PwC',1,1,1,'SYSTEM_INIT','2026-06-03 11:44:38',NULL,'2026-06-24 15:56:25',0,7),(2,'legal','$2a$10$ZrlZqdAR7oe5ZH8951bE/.6nbiWlJurcP8473XvExmigiqo.4.PwC',2,2,1,'SYSTEM_INIT','2026-06-03 11:44:38',NULL,'2026-06-23 23:07:44',0,1),(3,'user','$2a$10$ZrlZqdAR7oe5ZH8951bE/.6nbiWlJurcP8473XvExmigiqo.4.PwC',4,4,1,'SYSTEM_INIT','2026-06-03 11:44:38',NULL,'2026-06-24 18:20:02',0,1),(4,'dept_leader','$2a$10$ZrlZqdAR7oe5ZH8951bE/.6nbiWlJurcP8473XvExmigiqo.4.PwC',5,4,1,'SYSTEM_INIT','2026-06-03 11:44:38',NULL,'2026-06-23 23:07:44',0,1),(5,'finance','$2a$10$ZrlZqdAR7oe5ZH8951bE/.6nbiWlJurcP8473XvExmigiqo.4.PwC',3,3,1,'SYSTEM_INIT','2026-06-03 11:44:38',NULL,'2026-06-23 23:07:44',0,1),(6,'executive','$2a$10$ZrlZqdAR7oe5ZH8951bE/.6nbiWlJurcP8473XvExmigiqo.4.PwC',6,1,1,'SYSTEM_INIT','2026-06-03 11:44:38',NULL,'2026-06-23 23:07:44',0,1);
/*!40000 ALTER TABLE `user_info` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-06-24 20:32:35
