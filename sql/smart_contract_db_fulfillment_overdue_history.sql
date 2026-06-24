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
-- Table structure for table `fulfillment_overdue_history`
--

DROP TABLE IF EXISTS `fulfillment_overdue_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `fulfillment_overdue_history` (
  `history_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'history id',
  `plan_id` bigint unsigned NOT NULL COMMENT 'plan id',
  `contract_id` bigint unsigned NOT NULL COMMENT 'contract id',
  `node_name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'node name snapshot',
  `due_date` date DEFAULT NULL COMMENT 'overdue due date',
  `overdue_days` int unsigned NOT NULL DEFAULT '0' COMMENT 'overdue days',
  `status` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'OPEN' COMMENT 'history status',
  `started_at` datetime NOT NULL COMMENT 'overdue started time',
  `resolved_at` datetime DEFAULT NULL COMMENT 'resolved time',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT 'delete flag',
  `resolution_type` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'resolution type',
  `disposal_remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'disposal remark',
  `actual_completed_date` date DEFAULT NULL COMMENT 'actual completed date',
  `delay_approval_id` bigint unsigned DEFAULT NULL COMMENT 'delay approval id',
  `resolved_by` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'resolved by',
  PRIMARY KEY (`history_id`),
  KEY `idx_overdue_history_plan` (`plan_id`,`status`),
  KEY `idx_overdue_history_contract` (`contract_id`,`started_at`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='fulfillment overdue history';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `fulfillment_overdue_history`
--

LOCK TABLES `fulfillment_overdue_history` WRITE;
/*!40000 ALTER TABLE `fulfillment_overdue_history` DISABLE KEYS */;
INSERT INTO `fulfillment_overdue_history` VALUES (1,3,1,'安装调试验收','2026-06-23',1,'OPEN','2026-06-24 00:00:00',NULL,'2026-06-24 09:00:00','2026-06-24 20:20:38',0,NULL,'设备采购合同安装调试验收逾期，待供应商完成整改并提交验收单',NULL,NULL,NULL);
/*!40000 ALTER TABLE `fulfillment_overdue_history` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-06-24 20:32:53
