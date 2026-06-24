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
-- Table structure for table `payment_plan`
--

DROP TABLE IF EXISTS `payment_plan`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `payment_plan` (
  `payment_plan_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '付款计划ID',
  `plan_id` bigint unsigned NOT NULL DEFAULT '1' COMMENT 'legacy plan id',
  `contract_id` bigint unsigned NOT NULL COMMENT '所属合同ID',
  `installment_no` int NOT NULL DEFAULT '1' COMMENT 'legacy installment no',
  `ratio` decimal(5,4) NOT NULL DEFAULT '0.0000' COMMENT 'legacy ratio',
  `amount` decimal(15,2) NOT NULL DEFAULT '0.00' COMMENT 'legacy amount',
  `due_date` date NOT NULL COMMENT '付款到期日',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'PENDING' COMMENT '付款状态：PENDING待付/PAID已付',
  `prerequisite_deliverable_id` bigint unsigned DEFAULT NULL COMMENT '前置交付物ID，非空则需交付物确认后才可付款',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除：0未删除，1已删除',
  `phase_name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT 'phase name',
  `percentage` decimal(8,2) NOT NULL DEFAULT '0.00' COMMENT 'payment percentage',
  `planned_amount` decimal(15,2) NOT NULL DEFAULT '0.00' COMMENT 'planned amount',
  `prerequisite_delivery` varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'prerequisite delivery',
  `penalty_rate` decimal(8,4) NOT NULL DEFAULT '0.0500' COMMENT 'daily penalty rate percent',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'remark',
  `fulfillment_plan_id` bigint unsigned DEFAULT NULL COMMENT 'fulfillment plan id',
  `payee` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'payee',
  `payment_condition` varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'payment condition',
  `condition_type` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'NONE' COMMENT 'condition type',
  `condition_status` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'SATISFIED' COMMENT 'condition status',
  PRIMARY KEY (`payment_plan_id`),
  KEY `idx_payment_plan_plan` (`plan_id`),
  KEY `idx_payment_plan_contract` (`contract_id`),
  KEY `idx_payment_plan_due_date` (`due_date`),
  KEY `fk_payment_plan_prerequisite` (`prerequisite_deliverable_id`),
  CONSTRAINT `fk_payment_plan_contract` FOREIGN KEY (`contract_id`) REFERENCES `contract_main` (`contract_id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_payment_plan_plan` FOREIGN KEY (`plan_id`) REFERENCES `fulfillment_plan` (`plan_id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_payment_plan_prerequisite` FOREIGN KEY (`prerequisite_deliverable_id`) REFERENCES `fulfillment_deliverable` (`deliverable_id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='付款计划表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `payment_plan`
--

LOCK TABLES `payment_plan` WRITE;
/*!40000 ALTER TABLE `payment_plan` DISABLE KEYS */;
INSERT INTO `payment_plan` VALUES (1,1,1,1,0.3000,38400.00,'2026-06-13','PAID',1,'2026-06-09 10:00:00','2026-06-24 16:30:00',0,'首期款 30%',30.00,38400.00,'设备供货确认单验收通过',0.0500,'已完成首期付款',1,'深圳星河制造有限公司','设备供货确认单验收通过后按合同约定付款','DELIVERABLE','SATISFIED'),(2,2,1,2,0.4000,51200.00,'2026-06-19','PAID',2,'2026-06-09 10:00:00','2026-06-24 16:30:00',0,'到货验收款 40%',40.00,51200.00,'设备到货验收单验收通过',0.0500,'已完成到货验收款付款',2,'深圳星河制造有限公司','设备到货验收单验收通过后按合同约定付款','DELIVERABLE','SATISFIED'),(3,4,1,3,0.3000,38400.00,'2026-06-26','PENDING',4,'2026-06-09 10:00:00','2026-06-24 16:30:00',0,'尾款 30%',30.00,38400.00,'质保资料移交清单提交并验收通过',0.0500,'待质保资料移交后付款',4,'深圳星河制造有限公司','质保资料移交清单提交并验收通过后按合同约定付款','DELIVERABLE','PENDING'),(4,5,11,1,0.3000,54000.00,'2026-06-25','PENDING',5,'2026-06-09 10:00:00','2026-06-24 16:30:00',0,'首期款 30%',30.00,54000.00,'推广执行方案验收通过',0.0500,'付款日临近，待财务付款',5,'成都万象互动广告有限公司','推广执行方案验收通过后按合同约定付款','DELIVERABLE','SATISFIED'),(5,7,11,2,0.3000,54000.00,'2026-08-05','PENDING',7,'2026-06-09 10:00:00','2026-06-24 16:30:00',0,'阶段款 30%',30.00,54000.00,'投放数据阶段报告提交并验收通过',0.0500,'待阶段报告确认后付款',7,'成都万象互动广告有限公司','投放数据阶段报告提交并验收通过后按合同约定付款','DELIVERABLE','PENDING'),(6,8,11,3,0.4000,72000.00,'2026-09-01','PENDING',8,'2026-06-09 10:00:00','2026-06-24 16:30:00',0,'结案尾款 40%',40.00,72000.00,'推广结案验收报告验收通过',0.0500,'待结案验收后付款',8,'成都万象互动广告有限公司','推广结案验收报告验收通过后按合同约定付款','DELIVERABLE','PENDING'),(7,9,12,1,0.3000,78000.00,'2026-06-28','PENDING',9,'2026-06-09 10:00:00','2026-06-24 16:30:00',0,'首期款 30%',30.00,78000.00,'运维交接确认单验收通过',0.0500,'付款日临近，待财务付款',9,'深圳鹏城信息技术有限公司','运维交接确认单验收通过后按合同约定付款','DELIVERABLE','SATISFIED'),(8,11,12,2,0.3000,78000.00,'2026-09-30','PENDING',11,'2026-06-09 10:00:00','2026-06-24 16:30:00',0,'季度服务款 30%',30.00,78000.00,'季度运行评估报告提交并验收通过',0.0500,'待季度评估完成后付款',11,'深圳鹏城信息技术有限公司','季度运行评估报告提交并验收通过后按合同约定付款','DELIVERABLE','PENDING'),(9,12,12,3,0.4000,104000.00,'2027-06-03','PENDING',12,'2026-06-09 10:00:00','2026-06-24 16:30:00',0,'结项尾款 40%',40.00,104000.00,'年度运维结项报告验收通过',0.0500,'待年度结项验收后付款',12,'深圳鹏城信息技术有限公司','年度运维结项报告验收通过后按合同约定付款','DELIVERABLE','PENDING');
/*!40000 ALTER TABLE `payment_plan` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-06-24 20:32:29
