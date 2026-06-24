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
-- Table structure for table `fulfillment_deliverable`
--

DROP TABLE IF EXISTS `fulfillment_deliverable`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `fulfillment_deliverable` (
  `deliverable_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '浜や粯鐗㊣D',
  `contract_id` bigint unsigned NOT NULL COMMENT '鍏宠仈鍚堝悓ID',
  `deliverable_name` varchar(200) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT 'deliverable name',
  `deliverable_type` varchar(60) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT 'deliverable type',
  `contract_stage` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT 'legacy contract stage',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '鎺掑簭搴忓彿',
  `status` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'PENDING' COMMENT '鐘舵?: PENDING/CONFIRMED',
  `confirmed_by` bigint unsigned DEFAULT NULL COMMENT '纭??浜篒D',
  `confirmed_at` datetime DEFAULT NULL COMMENT '纭??鏃堕棿',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '澶囨敞',
  `created_by` bigint unsigned DEFAULT NULL COMMENT '鍒涘缓浜',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '閫昏緫鍒犻櫎',
  `stage_name` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT 'stage name',
  `confirm_method` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '逐项勾选确认' COMMENT 'confirm method',
  `confirmed` tinyint unsigned NOT NULL DEFAULT '0' COMMENT 'confirmed flag',
  `confirmer` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'confirmer',
  `plan_id` bigint unsigned DEFAULT NULL COMMENT 'fulfillment plan id',
  `confirm_status` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'CONFIRMED' COMMENT 'ai/manual confirm status',
  `source_clause` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'source clause',
  `ai_confidence` decimal(5,4) DEFAULT NULL COMMENT 'ai confidence',
  `ai_extracted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT 'ai extracted flag',
  `file_id` bigint unsigned DEFAULT NULL COMMENT 'uploaded file id',
  `acceptance_passed` tinyint unsigned NOT NULL DEFAULT '0' COMMENT 'acceptance passed flag',
  `accepted_by` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'acceptance operator',
  `accepted_at` datetime DEFAULT NULL COMMENT 'acceptance time',
  `submitted_by` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'submitter',
  `submitted_at` datetime DEFAULT NULL COMMENT 'submitted time',
  `reviewer_name` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'reviewer',
  `reviewed_at` datetime DEFAULT NULL COMMENT 'reviewed time',
  `review_comment` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'review comment',
  `submission_version` int unsigned NOT NULL DEFAULT '0' COMMENT 'submission version',
  PRIMARY KEY (`deliverable_id`),
  KEY `idx_deliverable_contract` (`contract_id`,`status`)
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='灞ョ害浜や粯鐗╄〃';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `fulfillment_deliverable`
--

LOCK TABLES `fulfillment_deliverable` WRITE;
/*!40000 ALTER TABLE `fulfillment_deliverable` DISABLE KEYS */;
INSERT INTO `fulfillment_deliverable` VALUES (1,1,'设备供货确认单','DELIVERY_CONFIRMATION','供货确认阶段',1,'ACCEPTED',3,'2026-06-12 15:20:00','设备型号、数量及交付安排已确认',3,'2026-06-09 10:00:00','2026-06-24 16:30:00',0,'供货确认阶段','人工验收确认',1,'user',1,'CONFIRMED','合同归档后进入履约，交付物名称：设备供货确认单',NULL,0,NULL,1,'admin','2026-06-12 15:20:00','user','2026-06-12 15:20:00','admin','2026-06-12 15:20:00','设备型号、数量及交付安排已确认',1),(2,1,'设备到货验收单','ARRIVAL_ACCEPTANCE','到货验收阶段',2,'ACCEPTED',3,'2026-06-18 17:00:00','设备到货验收通过',3,'2026-06-09 10:00:00','2026-06-24 16:30:00',0,'到货验收阶段','人工验收确认',1,'user',2,'CONFIRMED','合同归档后进入履约，交付物名称：设备到货验收单',NULL,0,NULL,1,'admin','2026-06-18 17:00:00','user','2026-06-18 17:00:00','admin','2026-06-18 17:00:00','设备到货验收通过',1),(3,1,'安装调试验收单','INSTALLATION_ACCEPTANCE','安装调试阶段',3,'PENDING_SUBMIT',NULL,NULL,'待供应商完成安装调试后提交验收材料',3,'2026-06-09 10:00:00','2026-06-24 16:30:00',0,'安装调试阶段','人工验收确认',0,NULL,3,'PENDING','合同归档后进入履约，交付物名称：安装调试验收单',NULL,0,NULL,0,NULL,NULL,NULL,NULL,NULL,NULL,'待供应商完成安装调试后提交验收材料',0),(4,1,'质保资料移交清单','WARRANTY_TRANSFER_LIST','资料移交阶段',4,'PENDING_SUBMIT',NULL,NULL,'待最终验收后提交质保与售后资料',3,'2026-06-09 10:00:00','2026-06-24 16:30:00',0,'资料移交阶段','人工验收确认',0,NULL,4,'PENDING','合同归档后进入履约，交付物名称：质保资料移交清单',NULL,0,NULL,0,NULL,NULL,NULL,NULL,NULL,NULL,'待最终验收后提交质保与售后资料',0),(5,11,'推广执行方案','PROMOTION_EXECUTION_PLAN','方案确认阶段',1,'ACCEPTED',3,'2026-06-20 16:30:00','推广渠道、周期、预算和交付口径已确认',3,'2026-06-09 10:00:00','2026-06-24 16:30:00',0,'方案确认阶段','人工验收确认',1,'user',5,'CONFIRMED','合同归档后进入履约，交付物名称：推广执行方案',NULL,0,NULL,1,'admin','2026-06-20 16:30:00','user','2026-06-20 16:30:00','admin','2026-06-20 16:30:00','推广渠道、周期、预算和交付口径已确认',1),(6,11,'首轮推广素材确认表','PROMOTION_MATERIAL_CONFIRMATION','素材确认阶段',2,'PENDING_SUBMIT',NULL,NULL,'待提交广告素材、落地页及投放账号确认材料',3,'2026-06-09 10:00:00','2026-06-24 16:30:00',0,'素材确认阶段','人工验收确认',0,NULL,6,'PENDING','合同归档后进入履约，交付物名称：首轮推广素材确认表',NULL,0,NULL,0,NULL,NULL,NULL,NULL,NULL,NULL,'待提交广告素材、落地页及投放账号确认材料',0),(7,11,'投放数据阶段报告','PROMOTION_DATA_REPORT','阶段报告阶段',3,'PENDING_SUBMIT',NULL,NULL,'待提交阶段投放数据与费用消耗报告',3,'2026-06-09 10:00:00','2026-06-24 16:30:00',0,'阶段报告阶段','人工验收确认',0,NULL,7,'PENDING','合同归档后进入履约，交付物名称：投放数据阶段报告',NULL,0,NULL,0,NULL,NULL,NULL,NULL,NULL,NULL,'待提交阶段投放数据与费用消耗报告',0),(8,11,'推广结案验收报告','PROMOTION_CLOSING_ACCEPTANCE','结案验收阶段',4,'PENDING_SUBMIT',NULL,NULL,'待提交完整结案报告和效果验收材料',3,'2026-06-09 10:00:00','2026-06-24 16:30:00',0,'结案验收阶段','人工验收确认',0,NULL,8,'PENDING','合同归档后进入履约，交付物名称：推广结案验收报告',NULL,0,NULL,0,NULL,NULL,NULL,NULL,NULL,NULL,'待提交完整结案报告和效果验收材料',0),(9,12,'运维交接确认单','MAINTENANCE_HANDOVER_CONFIRMATION','服务交接阶段',1,'ACCEPTED',3,'2026-06-18 14:40:00','运维范围、联系人、响应时限和交接清单已确认',3,'2026-06-09 10:00:00','2026-06-24 16:30:00',0,'服务交接阶段','人工验收确认',1,'user',9,'CONFIRMED','合同归档后进入履约，交付物名称：运维交接确认单',NULL,0,NULL,1,'admin','2026-06-18 14:40:00','user','2026-06-18 14:40:00','admin','2026-06-18 14:40:00','运维范围、联系人、响应时限和交接清单已确认',1),(10,12,'首月巡检报告','MONTHLY_INSPECTION_REPORT','月度巡检阶段',2,'PENDING_SUBMIT',NULL,NULL,'待提交服务器、数据库、中间件和备份巡检结果',3,'2026-06-09 10:00:00','2026-06-24 16:30:00',0,'月度巡检阶段','人工验收确认',0,NULL,10,'PENDING','合同归档后进入履约，交付物名称：首月巡检报告',NULL,0,NULL,0,NULL,NULL,NULL,NULL,NULL,NULL,'待提交服务器、数据库、中间件和备份巡检结果',0),(11,12,'季度运行评估报告','QUARTERLY_OPERATION_REPORT','季度评估阶段',3,'PENDING_SUBMIT',NULL,NULL,'待提交季度故障工单、SLA达成率和优化建议',3,'2026-06-09 10:00:00','2026-06-24 16:30:00',0,'季度评估阶段','人工验收确认',0,NULL,11,'PENDING','合同归档后进入履约，交付物名称：季度运行评估报告',NULL,0,NULL,0,NULL,NULL,NULL,NULL,NULL,NULL,'待提交季度故障工单、SLA达成率和优化建议',0),(12,12,'年度运维结项报告','ANNUAL_MAINTENANCE_CLOSING_REPORT','年度结项阶段',4,'PENDING_SUBMIT',NULL,NULL,'待服务期结束后提交年度运维总结和结项验收材料',3,'2026-06-09 10:00:00','2026-06-24 16:30:00',0,'年度结项阶段','人工验收确认',0,NULL,12,'PENDING','合同归档后进入履约，交付物名称：年度运维结项报告',NULL,0,NULL,0,NULL,NULL,NULL,NULL,NULL,NULL,'待服务期结束后提交年度运维总结和结项验收材料',0);
/*!40000 ALTER TABLE `fulfillment_deliverable` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-06-24 20:32:52
