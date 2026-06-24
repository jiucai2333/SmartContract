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
-- Table structure for table `approval_record`
--

DROP TABLE IF EXISTS `approval_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `approval_record` (
  `record_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '审批记录ID',
  `instance_id` bigint unsigned NOT NULL COMMENT '所属审批实例ID',
  `node_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '审批节点名称',
  `approver_id` bigint unsigned NOT NULL COMMENT '审批人用户ID',
  `action` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '审批动作：AGREE同意/REJECT驳回/TRANSFER转签',
  `comment` text CHARACTER SET utf8mb4 COLLATE utf8mb4_bin COMMENT '审批意见',
  `action_time` datetime NOT NULL COMMENT '审批操作时间',
  PRIMARY KEY (`record_id`),
  KEY `idx_instance_id` (`instance_id`),
  KEY `idx_approver_action` (`approver_id`,`action_time`),
  CONSTRAINT `fk_approval_record_approver` FOREIGN KEY (`approver_id`) REFERENCES `user_info` (`user_id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_approval_record_instance` FOREIGN KEY (`instance_id`) REFERENCES `approval_instance` (`instance_id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='审批记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `approval_record`
--

LOCK TABLES `approval_record` WRITE;
/*!40000 ALTER TABLE `approval_record` DISABLE KEYS */;
INSERT INTO `approval_record` VALUES (1,1,'部门主管审批',4,'AGREE','采购需求明确，预算与供应商信息完整，同意提交法务复核。','2026-05-30 14:00:00'),(2,1,'法务专员审批',2,'AGREE','交付、验收和违约责任已补充，准予进入签章归档。','2026-05-31 18:00:00'),(3,2,'部门主管审批',4,'AGREE','销售合同金额与业务机会匹配，同意提交法务复核。','2026-05-18 14:00:00'),(4,2,'法务专员审批',2,'AGREE','保密和付款条款风险较低，准予签署。','2026-05-19 18:00:00'),(5,3,'部门主管审批',4,'AGREE','技术服务范围明确，同意进入法务复核。','2026-05-31 15:00:00'),(6,3,'法务专员审批',2,'AGREE','服务边界和验收条款清晰，准予签章。','2026-06-01 18:00:00'),(7,4,'部门主管审批',4,'AGREE','仓储服务预算已确认，同意进入法务复核。','2026-06-03 15:30:00'),(8,5,'部门主管审批',4,'AGREE','长期采购合同符合业务计划，同意提交法务复核。','2026-05-28 15:00:00'),(9,5,'法务专员审批',2,'AGREE','供应与违约条款已确认，同意提交高管审批。','2026-05-29 11:00:00'),(10,5,'企业高管审批',6,'AGREE','金额较大但风险可控，同意通过。','2026-05-30 18:00:00'),(11,6,'提交审批',1,'SUBMIT','提交审批','2026-06-23 10:10:23');
/*!40000 ALTER TABLE `approval_record` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-06-24 20:32:48
