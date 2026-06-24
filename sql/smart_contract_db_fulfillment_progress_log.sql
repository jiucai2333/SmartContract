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
-- Table structure for table `fulfillment_progress_log`
--

DROP TABLE IF EXISTS `fulfillment_progress_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `fulfillment_progress_log` (
  `log_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'log id',
  `plan_id` bigint unsigned NOT NULL COMMENT 'plan id',
  `contract_id` bigint unsigned NOT NULL COMMENT 'contract id',
  `operation` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'operation',
  `before_status` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'before status',
  `after_status` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'after status',
  `before_value` text CHARACTER SET utf8mb4 COLLATE utf8mb4_bin COMMENT 'before value',
  `after_value` text CHARACTER SET utf8mb4 COLLATE utf8mb4_bin COMMENT 'after value',
  `operator_id` bigint unsigned DEFAULT NULL COMMENT 'operator id',
  `operator_name` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'operator name',
  `operate_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'operate time',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'remark',
  `client_ip` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'client ip',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT 'delete flag',
  PRIMARY KEY (`log_id`),
  KEY `idx_progress_plan` (`plan_id`,`operate_time`),
  KEY `idx_progress_contract` (`contract_id`,`operate_time`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='fulfillment progress log';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `fulfillment_progress_log`
--

LOCK TABLES `fulfillment_progress_log` WRITE;
/*!40000 ALTER TABLE `fulfillment_progress_log` DISABLE KEYS */;
INSERT INTO `fulfillment_progress_log` VALUES (1,1,1,'COMPLETE_NODE','PENDING','COMPLETED','{\"status\":\"PENDING\",\"progress\":0}','{\"status\":\"COMPLETED\",\"progress\":100,\"actual_date\":\"2026-06-12\"}',1,'admin','2026-06-12 15:20:00','设备供货确认节点完成','127.0.0.1',0),(2,2,1,'COMPLETE_NODE','PENDING','COMPLETED','{\"status\":\"PENDING\",\"progress\":0}','{\"status\":\"COMPLETED\",\"progress\":100,\"actual_date\":\"2026-06-18\"}',1,'admin','2026-06-18 17:00:00','设备到货验收节点完成','127.0.0.1',0),(3,3,1,'MARK_OVERDUE','PENDING','OVERDUE','{\"status\":\"PENDING\",\"overdue_days\":0}','{\"status\":\"OVERDUE\",\"overdue_days\":1}',1,'admin','2026-06-24 09:00:00','安装调试验收逾期自动标记','127.0.0.1',0),(4,5,11,'COMPLETE_NODE','PENDING','COMPLETED','{\"status\":\"PENDING\",\"progress\":0}','{\"status\":\"COMPLETED\",\"progress\":100,\"actual_date\":\"2026-06-20\"}',1,'admin','2026-06-20 16:30:00','推广执行方案确认节点完成','127.0.0.1',0),(5,9,12,'COMPLETE_NODE','PENDING','COMPLETED','{\"status\":\"PENDING\",\"progress\":0}','{\"status\":\"COMPLETED\",\"progress\":100,\"actual_date\":\"2026-06-18\"}',1,'admin','2026-06-18 14:40:00','运维交接方案确认节点完成','127.0.0.1',0);
/*!40000 ALTER TABLE `fulfillment_progress_log` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-06-24 20:32:47
