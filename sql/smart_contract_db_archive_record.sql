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
-- Table structure for table `archive_record`
--

DROP TABLE IF EXISTS `archive_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `archive_record` (
  `archive_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '归档记录ID',
  `contract_id` bigint unsigned NOT NULL COMMENT '归档合同ID',
  `version_id` bigint unsigned NOT NULL COMMENT '归档合同版本ID',
  `archive_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '归档编号',
  `archive_time` datetime NOT NULL COMMENT '归档时间',
  `archiver_id` bigint unsigned DEFAULT NULL COMMENT '归档操作人用户ID',
  `is_locked` tinyint unsigned NOT NULL DEFAULT '1' COMMENT '归档是否锁定版本：0未锁定，1已锁定',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除：0未删除，1已删除',
  `version` int unsigned NOT NULL DEFAULT '1' COMMENT '乐观锁版本号',
  `knowledge_id` bigint unsigned DEFAULT NULL COMMENT '关联合同知识库条目ID',
  `merkle_root` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '归档默克尔树根哈希，用于完整性验证',
  PRIMARY KEY (`archive_id`),
  KEY `idx_contract_id` (`contract_id`),
  KEY `idx_version_id` (`version_id`),
  KEY `fk_archive_archiver` (`archiver_id`),
  KEY `fk_archive_knowledge` (`knowledge_id`),
  CONSTRAINT `fk_archive_archiver` FOREIGN KEY (`archiver_id`) REFERENCES `user_info` (`user_id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_archive_contract` FOREIGN KEY (`contract_id`) REFERENCES `contract_main` (`contract_id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_archive_version` FOREIGN KEY (`version_id`) REFERENCES `contract_version` (`version_id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='归档确认记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `archive_record`
--

LOCK TABLES `archive_record` WRITE;
/*!40000 ALTER TABLE `archive_record` DISABLE KEYS */;
INSERT INTO `archive_record` VALUES (1,1,1,'AR-2026-001-v1-0','2026-05-31 23:22:00',1,1,'admin','2026-05-31 23:22:00',NULL,'2026-06-09 10:00:00',0,1,1,'510e3dbd33aab02ab240110ad683235b3727b8b0137c839ad67ddbf8a9af7b4d'),(2,11,11,'AR-2026-011-v1-0','2026-06-03 01:45:54',1,1,'admin','2026-06-03 01:45:54',NULL,'2026-06-09 10:00:00',0,1,2,'d738af1d7d51b02e7acb85857a78a1c2b4337bd7c13cf268159b545bf3ca9b73');
/*!40000 ALTER TABLE `archive_record` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-06-24 20:32:49
