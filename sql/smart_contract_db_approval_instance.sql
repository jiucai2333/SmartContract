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
-- Table structure for table `approval_instance`
--

DROP TABLE IF EXISTS `approval_instance`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `approval_instance` (
  `instance_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '审批实例ID',
  `contract_id` bigint unsigned NOT NULL COMMENT '所属合同ID',
  `flow_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '审批流程类型：NORMAL普通/COUNTER反签/MAJOR重大',
  `current_node` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '当前审批节点名称',
  `status` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '审批状态：RUNNING进行中/APPROVED已通过/REJECTED已驳回',
  `started_at` datetime NOT NULL COMMENT '审批开始时间',
  `ended_at` datetime DEFAULT NULL COMMENT '审批结束时间',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除：0未删除，1已删除',
  `version` int unsigned NOT NULL DEFAULT '1' COMMENT '乐观锁版本号',
  PRIMARY KEY (`instance_id`),
  KEY `idx_contract_id` (`contract_id`),
  CONSTRAINT `fk_approval_instance_contract` FOREIGN KEY (`contract_id`) REFERENCES `contract_main` (`contract_id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='审批实例表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `approval_instance`
--

LOCK TABLES `approval_instance` WRITE;
/*!40000 ALTER TABLE `approval_instance` DISABLE KEYS */;
INSERT INTO `approval_instance` VALUES (1,1,'MAJOR',NULL,'APPROVED','2026-05-30 09:00:00','2026-05-31 18:00:00','user','2026-05-30 09:00:00','admin','2026-06-24 20:03:16',0,2),(2,3,'NORMAL',NULL,'APPROVED','2026-05-18 09:00:00','2026-05-19 18:00:00','admin','2026-05-18 09:00:00','legal','2026-05-19 18:00:00',0,1),(3,2,'NORMAL',NULL,'APPROVED','2026-05-31 09:00:00','2026-06-01 18:00:00','user','2026-05-31 09:00:00','legal','2026-06-01 18:00:00',0,2),(4,8,'MAJOR','法务专员审批','RUNNING','2026-06-03 09:00:00',NULL,'legal','2026-06-03 09:00:00',NULL,'2026-06-24 20:03:16',0,1),(5,9,'MAJOR',NULL,'APPROVED','2026-05-28 09:00:00','2026-05-30 18:00:00','legal','2026-05-28 09:00:00','executive','2026-05-30 18:00:00',0,1),(6,6,'MAJOR','部门主管审批','RUNNING','2026-06-23 10:10:23',NULL,'admin','2026-06-23 10:10:23',NULL,'2026-06-23 10:10:23',0,1);
/*!40000 ALTER TABLE `approval_instance` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-06-24 20:32:38
