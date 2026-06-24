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
-- Table structure for table `fulfillment_plan`
--

DROP TABLE IF EXISTS `fulfillment_plan`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `fulfillment_plan` (
  `plan_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '履约计划ID',
  `contract_id` bigint unsigned NOT NULL COMMENT '所属合同ID',
  `milestone_name` varchar(150) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT 'legacy milestone name',
  `due_date` date DEFAULT NULL COMMENT 'due date',
  `actual_date` date DEFAULT NULL COMMENT '实际完成日期',
  `owner_id` bigint unsigned NOT NULL DEFAULT '1' COMMENT 'legacy owner id',
  `status` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'PENDING' COMMENT '履约状态：PENDING待履约/COMPLETED已完成/OVERDUE已逾期',
  `completion_notes` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '完成备注',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除：0未删除，1已删除',
  `version` int unsigned NOT NULL DEFAULT '1' COMMENT '乐观锁版本号',
  `node_name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT 'node name',
  `plan_type` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'OTHER' COMMENT 'plan type',
  `progress` tinyint unsigned NOT NULL DEFAULT '0' COMMENT 'progress',
  `owner_name` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'owner name',
  `source_type` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'MANUAL' COMMENT 'source type',
  `extracted_rule` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'extract rule',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'remark',
  `handled_at` datetime DEFAULT NULL COMMENT 'handled time',
  `source_clause` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'source clause',
  `ai_confidence` decimal(5,4) DEFAULT NULL COMMENT 'ai confidence',
  `ai_extracted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT 'ai extracted flag',
  `confirm_status` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'CONFIRMED' COMMENT 'confirm status',
  `overdue_days` int unsigned NOT NULL DEFAULT '0' COMMENT 'current overdue days',
  `last_overdue_at` datetime DEFAULT NULL COMMENT 'last overdue marked time',
  `delay_status` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'NONE' COMMENT 'delay approval status',
  `delay_requested_due_date` date DEFAULT NULL COMMENT 'requested due date',
  `delay_reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'delay reason',
  `delay_requested_by` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'delay requester',
  `delay_requested_at` datetime DEFAULT NULL COMMENT 'delay requested time',
  `delay_confirmed_by` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'delay confirmer',
  `delay_confirmed_at` datetime DEFAULT NULL COMMENT 'delay confirmed time',
  `actual_completed_date` date DEFAULT NULL COMMENT 'actual completed date',
  `delay_rejected_by` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'delay rejecter',
  `delay_rejected_at` datetime DEFAULT NULL COMMENT 'delay rejected time',
  `delay_reject_reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'delay reject reason',
  PRIMARY KEY (`plan_id`),
  KEY `idx_contract_id` (`contract_id`),
  KEY `fk_fulfillment_owner` (`owner_id`),
  CONSTRAINT `fk_fulfillment_contract` FOREIGN KEY (`contract_id`) REFERENCES `contract_main` (`contract_id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_fulfillment_owner` FOREIGN KEY (`owner_id`) REFERENCES `user_info` (`user_id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='履约计划表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `fulfillment_plan`
--

LOCK TABLES `fulfillment_plan` WRITE;
/*!40000 ALTER TABLE `fulfillment_plan` DISABLE KEYS */;
INSERT INTO `fulfillment_plan` VALUES (1,1,'设备供货确认','2026-06-12','2026-06-12',3,'COMPLETED','供应商已确认设备型号、数量及到货安排','admin','2026-06-09 10:00:00','admin','2026-06-24 16:30:00',0,1,'设备供货确认','DELIVERY',100,'user','ARCHIVE_GENERATED','合同已归档，系统根据合同类型和履约期限生成正式履约节点','供应商已确认设备型号、数量及到货安排','2026-06-12 15:20:00','合同归档后进入履约阶段，节点名称：设备供货确认',NULL,0,'CONFIRMED',0,NULL,'NONE',NULL,NULL,NULL,NULL,NULL,NULL,'2026-06-12',NULL,NULL,NULL),(2,1,'设备到货验收','2026-06-18','2026-06-18',3,'COMPLETED','设备已按合同清单到货并完成初步验收','admin','2026-06-09 10:00:00','admin','2026-06-24 16:30:00',0,1,'设备到货验收','ACCEPTANCE',100,'user','ARCHIVE_GENERATED','合同已归档，系统根据合同类型和履约期限生成正式履约节点','设备已按合同清单到货并完成初步验收','2026-06-18 17:00:00','合同归档后进入履约阶段，节点名称：设备到货验收',NULL,0,'CONFIRMED',0,NULL,'NONE',NULL,NULL,NULL,NULL,NULL,NULL,'2026-06-18',NULL,NULL,NULL),(3,1,'安装调试验收','2026-06-23',NULL,3,'OVERDUE','安装调试验收尚未完成，需跟进供应商整改进度','admin','2026-06-09 10:00:00','admin','2026-06-24 20:20:38',0,1,'安装调试验收','ACCEPTANCE',60,'user','ARCHIVE_GENERATED','合同已归档，系统根据合同类型和履约期限生成正式履约节点','安装调试验收尚未完成，需跟进供应商整改进度',NULL,'合同归档后进入履约阶段，节点名称：安装调试验收',NULL,0,'CONFIRMED',1,'2026-06-24 16:30:00','NONE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL),(4,1,'质保资料移交','2026-06-26',NULL,3,'PENDING','待最终验收后移交质保卡、说明书及售后联系人信息','admin','2026-06-09 10:00:00','admin','2026-06-24 16:30:00',0,1,'质保资料移交','DOCUMENT_TRANSFER',0,'user','ARCHIVE_GENERATED','合同已归档，系统根据合同类型和履约期限生成正式履约节点','待最终验收后移交质保卡、说明书及售后联系人信息',NULL,'合同归档后进入履约阶段，节点名称：质保资料移交',NULL,0,'CONFIRMED',0,NULL,'NONE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL),(5,11,'推广执行方案确认','2026-06-20','2026-06-20',3,'COMPLETED','推广执行方案已确认，投放周期与渠道安排已明确','admin','2026-06-09 10:00:00','admin','2026-06-24 16:30:00',0,1,'推广执行方案确认','PLAN_CONFIRM',100,'user','ARCHIVE_GENERATED','合同已归档，系统根据合同类型和履约期限生成正式履约节点','推广执行方案已确认，投放周期与渠道安排已明确','2026-06-20 16:30:00','合同归档后进入履约阶段，节点名称：推广执行方案确认',NULL,0,'CONFIRMED',0,NULL,'NONE',NULL,NULL,NULL,NULL,NULL,NULL,'2026-06-20',NULL,NULL,NULL),(6,11,'首轮推广素材确认','2026-07-05',NULL,3,'PENDING','待确认首轮广告素材、落地页和投放账号配置','admin','2026-06-09 10:00:00','admin','2026-06-24 16:30:00',0,1,'首轮推广素材确认','MATERIAL_CONFIRM',0,'user','ARCHIVE_GENERATED','合同已归档，系统根据合同类型和履约期限生成正式履约节点','待确认首轮广告素材、落地页和投放账号配置',NULL,'合同归档后进入履约阶段，节点名称：首轮推广素材确认',NULL,0,'CONFIRMED',0,NULL,'NONE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL),(7,11,'投放数据阶段报告','2026-08-01',NULL,3,'PENDING','阶段报告需包含曝光量、点击量、转化线索和费用消耗','admin','2026-06-09 10:00:00','admin','2026-06-24 16:30:00',0,1,'投放数据阶段报告','PROGRESS_REPORT',0,'user','ARCHIVE_GENERATED','合同已归档，系统根据合同类型和履约期限生成正式履约节点','阶段报告需包含曝光量、点击量、转化线索和费用消耗',NULL,'合同归档后进入履约阶段，节点名称：投放数据阶段报告',NULL,0,'CONFIRMED',0,NULL,'NONE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL),(8,11,'推广结案验收','2026-09-01',NULL,3,'PENDING','合同到期前完成结案报告与效果验收','admin','2026-06-09 10:00:00','admin','2026-06-24 16:30:00',0,1,'推广结案验收','FINAL_ACCEPTANCE',0,'user','ARCHIVE_GENERATED','合同已归档，系统根据合同类型和履约期限生成正式履约节点','合同到期前完成结案报告与效果验收',NULL,'合同归档后进入履约阶段，节点名称：推广结案验收',NULL,0,'CONFIRMED',0,NULL,'NONE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL),(9,12,'运维交接方案确认','2026-06-18','2026-06-18',3,'COMPLETED','已确认运维范围、服务窗口、故障响应联系人和交接清单','admin','2026-06-09 10:00:00','admin','2026-06-24 16:30:00',0,1,'运维交接方案确认','PLAN_CONFIRM',100,'user','ARCHIVE_GENERATED','合同已归档，系统根据合同类型和履约期限生成正式履约节点','已确认运维范围、服务窗口、故障响应联系人和交接清单','2026-06-18 14:40:00','合同归档后进入履约阶段，节点名称：运维交接方案确认',NULL,0,'CONFIRMED',0,NULL,'NONE',NULL,NULL,NULL,NULL,NULL,NULL,'2026-06-18',NULL,NULL,NULL),(10,12,'首月巡检报告','2026-07-03',NULL,3,'PENDING','首月巡检需覆盖服务器、数据库、中间件与备份状态','admin','2026-06-09 10:00:00','admin','2026-06-24 16:30:00',0,1,'首月巡检报告','INSPECTION_REPORT',0,'user','ARCHIVE_GENERATED','合同已归档，系统根据合同类型和履约期限生成正式履约节点','首月巡检需覆盖服务器、数据库、中间件与备份状态',NULL,'合同归档后进入履约阶段，节点名称：首月巡检报告',NULL,0,'CONFIRMED',0,NULL,'NONE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL),(11,12,'季度运行评估','2026-09-30',NULL,3,'PENDING','季度评估需汇总故障工单、SLA达成率和优化建议','admin','2026-06-09 10:00:00','admin','2026-06-24 16:30:00',0,1,'季度运行评估','PROGRESS_REPORT',0,'user','ARCHIVE_GENERATED','合同已归档，系统根据合同类型和履约期限生成正式履约节点','季度评估需汇总故障工单、SLA达成率和优化建议',NULL,'合同归档后进入履约阶段，节点名称：季度运行评估',NULL,0,'CONFIRMED',0,NULL,'NONE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL),(12,12,'年度运维结项验收','2027-06-03',NULL,3,'PENDING','服务期结束后提交全年运维总结并完成结项验收','admin','2026-06-09 10:00:00','admin','2026-06-24 16:30:00',0,1,'年度运维结项验收','FINAL_ACCEPTANCE',0,'user','ARCHIVE_GENERATED','合同已归档，系统根据合同类型和履约期限生成正式履约节点','服务期结束后提交全年运维总结并完成结项验收',NULL,'合同归档后进入履约阶段，节点名称：年度运维结项验收',NULL,0,'CONFIRMED',0,NULL,'NONE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL);
/*!40000 ALTER TABLE `fulfillment_plan` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-06-24 20:32:46
