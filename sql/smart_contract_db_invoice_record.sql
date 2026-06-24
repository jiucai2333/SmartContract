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
-- Table structure for table `invoice_record`
--

DROP TABLE IF EXISTS `invoice_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `invoice_record` (
  `invoice_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'invoice id',
  `payment_plan_id` bigint unsigned NOT NULL COMMENT 'payment plan id',
  `contract_id` bigint unsigned NOT NULL COMMENT 'contract id',
  `invoice_no` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'invoice no',
  `invoice_amount` decimal(15,2) NOT NULL DEFAULT '0.00' COMMENT 'invoice amount',
  `invoice_date` date DEFAULT NULL COMMENT 'invoice date',
  `invoice_status` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'RECEIVED' COMMENT 'invoice status',
  `file_id` bigint unsigned DEFAULT NULL COMMENT 'file id',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'remark',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT 'delete flag',
  PRIMARY KEY (`invoice_id`),
  KEY `idx_invoice_plan` (`payment_plan_id`,`invoice_status`),
  KEY `idx_invoice_contract` (`contract_id`,`invoice_date`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='invoice record';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `invoice_record`
--

LOCK TABLES `invoice_record` WRITE;
/*!40000 ALTER TABLE `invoice_record` DISABLE KEYS */;
INSERT INTO `invoice_record` VALUES (1,1,1,'INV-HT2026001-01',38400.00,'2026-06-13','RECEIVED',NULL,'设备采购合同首期款发票已登记','2026-06-13 10:30:00','2026-06-24 16:30:00',0),(2,2,1,'INV-HT2026001-02',51200.00,'2026-06-19','RECEIVED',NULL,'设备采购合同到货验收款发票已登记','2026-06-19 15:50:00','2026-06-24 16:30:00',0);
/*!40000 ALTER TABLE `invoice_record` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-06-24 20:32:33
