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
-- Table structure for table `reminder_record`
--

DROP TABLE IF EXISTS `reminder_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reminder_record` (
  `reminder_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '提醒记录ID',
  `contract_id` bigint unsigned NOT NULL COMMENT '所属合同ID',
  `reminder_type` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT 'legacy reminder type',
  `send_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'legacy send time',
  `receiver_id` bigint unsigned NOT NULL DEFAULT '1' COMMENT 'legacy receiver id',
  `send_status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '发送状态：SUCCESS成功/PENDING待发/FAILED失败',
  `plan_id` bigint unsigned NOT NULL DEFAULT '0' COMMENT 'plan id',
  `reminder_level` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'LEVEL1' COMMENT 'reminder level',
  `reminder_date` date DEFAULT NULL COMMENT 'reminder date',
  `channel` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'IN_APP' COMMENT 'channel',
  `receiver` varchar(500) COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'receiver',
  `content` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT 'content',
  `sent_at` datetime DEFAULT NULL COMMENT 'sent time',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT 'delete flag',
  PRIMARY KEY (`reminder_id`),
  KEY `idx_contract_id` (`contract_id`),
  KEY `idx_receiver_id` (`receiver_id`),
  CONSTRAINT `fk_reminder_contract` FOREIGN KEY (`contract_id`) REFERENCES `contract_main` (`contract_id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_reminder_receiver` FOREIGN KEY (`receiver_id`) REFERENCES `user_info` (`user_id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='履约提醒记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `reminder_record`
--

LOCK TABLES `reminder_record` WRITE;
/*!40000 ALTER TABLE `reminder_record` DISABLE KEYS */;
INSERT INTO `reminder_record` VALUES (1,1,'FULFILLMENT_OVERDUE','2026-06-24 09:00:00',1,'SUCCESS',3,'OVERDUE','2026-06-24','IN_APP','admin','合同【设备采购合同】履约节点【安装调试验收】已逾期1天，请跟进供应商完成调试验收。','2026-06-24 09:00:00','2026-06-24 09:00:00','2026-06-24 16:30:00',0),(2,1,'FULFILLMENT_DUE_SOON','2026-06-24 09:05:00',1,'SUCCESS',4,'LEVEL3','2026-06-24','IN_APP','admin','合同【设备采购合同】履约节点【质保资料移交】距截止日期还有2天，请提前准备质保资料。','2026-06-24 09:05:00','2026-06-24 09:05:00','2026-06-24 16:30:00',0),(3,11,'PAYMENT_DUE_SOON','2026-06-24 09:10:00',1,'SUCCESS',5,'LEVEL3','2026-06-24','IN_APP','admin','合同【市场推广代理合同】首期款付款日为2026-06-25，前置交付物已确认，请财务安排付款。','2026-06-24 09:10:00','2026-06-24 09:10:00','2026-06-24 16:30:00',0),(4,12,'PAYMENT_DUE_SOON','2026-06-24 09:15:00',1,'SUCCESS',9,'LEVEL2','2026-06-24','IN_APP','admin','合同【IT系统运维服务合同】首期款付款日为2026-06-28，前置交付物已确认，请财务关注。','2026-06-24 09:15:00','2026-06-24 09:15:00','2026-06-24 16:30:00',0);
/*!40000 ALTER TABLE `reminder_record` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-06-24 20:32:28
