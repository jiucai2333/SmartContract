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
-- Table structure for table `contract_main`
--

DROP TABLE IF EXISTS `contract_main`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `contract_main` (
  `contract_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '合同ID',
  `contract_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '合同编号，全局唯一',
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '合同标题',
  `type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '合同类型：PURCHASE/TECH/SALES/LABOR等',
  `amount` decimal(15,2) NOT NULL DEFAULT '0.00' COMMENT '合同金额（元）',
  `counterparty` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '相对方名称',
  `dept_id` bigint unsigned NOT NULL COMMENT '所属部门ID',
  `owner_id` bigint unsigned NOT NULL COMMENT '合同负责人用户ID',
  `template_id` bigint unsigned DEFAULT NULL COMMENT '关联模板ID',
  `status` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'DRAFT' COMMENT '合同状态：DRAFT草稿/REVIEWING审查/APPROVING审批中/APPROVED已审批/SIGNING签章/ARCHIVED归档/EXECUTING履约中/COMPLETED已完成',
  `risk_level` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT 'LOW' COMMENT '风险等级：LOW低/MEDIUM中/HIGH高',
  `sign_date` date DEFAULT NULL COMMENT '签订日期',
  `due_date` date DEFAULT NULL COMMENT '到期日期',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除：0未删除，1已删除',
  `version` int unsigned NOT NULL DEFAULT '1' COMMENT '乐观锁版本号',
  PRIMARY KEY (`contract_id`),
  UNIQUE KEY `uk_contract_no` (`contract_no`),
  KEY `idx_template_id` (`template_id`),
  KEY `idx_contract_composite` (`type`,`status`,`dept_id`,`owner_id`,`risk_level`,`due_date`,`created_at`),
  KEY `fk_contract_dept` (`dept_id`),
  KEY `fk_contract_owner` (`owner_id`),
  CONSTRAINT `fk_contract_dept` FOREIGN KEY (`dept_id`) REFERENCES `dept_info` (`dept_id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_contract_owner` FOREIGN KEY (`owner_id`) REFERENCES `user_info` (`user_id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_contract_template` FOREIGN KEY (`template_id`) REFERENCES `contract_template` (`template_id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='合同主表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `contract_main`
--

LOCK TABLES `contract_main` WRITE;
/*!40000 ALTER TABLE `contract_main` DISABLE KEYS */;
INSERT INTO `contract_main` VALUES (1,'HT-2026-001','设备采购合同','PURCHASE',128000.00,'深圳星河制造有限公司',4,3,18,'ARCHIVED','MEDIUM','2026-05-31','2026-06-26','user','2026-05-26 00:25:31','admin','2026-06-09 10:00:00',0,4),(2,'HT-2026-002','企业技术服务合同','TECH',46000.00,'杭州云策科技有限公司',4,3,20,'SIGNING','LOW','2026-06-04','2026-08-31','user','2026-05-28 00:25:31','admin','2026-06-09 10:00:00',0,6),(3,'HT-2026-003','软件系统销售合同','SALES',320000.00,'成都数联信息有限公司',4,3,NULL,'EXECUTING','LOW','2026-05-20','2026-06-08','admin','2026-05-16 00:25:31',NULL,'2026-06-09 10:00:00',0,2),(4,'HT-2026-004','知识产权合作协议','INTELLECTUAL_PROPERTY',880000.00,'北京衡信知识产权代理有限公司',4,3,23,'SIGNING','LOW',NULL,'2026-09-28','legal','2026-05-29 00:25:31','legal','2026-06-24 20:17:21',0,10),(5,'HT-2026-005','年度IT运维服务合同','TECH',156000.00,'上海智云信息技术有限公司',4,3,20,'SIGNING','LOW',NULL,'2026-11-27','legal','2026-05-23 22:55:11',NULL,'2026-06-09 10:00:00',0,1),(6,'HT-2026-006','办公用品采购合同','PURCHASE',85000.00,'深圳华强数码科技有限公司',4,3,18,'DRAFT','MEDIUM',NULL,'2026-07-18','user','2026-05-24 00:37:15','admin','2026-06-23 21:30:15',0,4),(7,'HT-2026-007','员工培训服务协议','ENTERPRISE_SERVICE',32000.00,'北京博雅教育咨询有限公司',4,3,15,'SIGNING','LOW','2026-06-05','2026-08-02','user','2026-05-31 00:37:15','admin','2026-06-24 19:53:51',0,6),(8,'HT-2026-008','广告服务合同','ENTERPRISE_SERVICE',280000.00,'北京云帆数字传媒有限公司',4,3,4,'DRAFT','HIGH',NULL,'2026-09-01','legal','2026-06-01 00:37:15','admin','2026-06-24 19:54:53',0,19),(9,'HT-2026-009','原材料供应长期合同','PURCHASE',420000.00,'广州鑫源钢铁贸易有限公司',4,3,19,'SIGNING','LOW',NULL,'2026-11-30','legal','2026-05-27 00:37:15',NULL,'2026-06-24 19:53:51',0,3),(10,'HT-2026-010','品牌授权合作协议','INTELLECTUAL_PROPERTY',150000.00,'杭州天域品牌管理有限公司',4,3,23,'SIGNING','MEDIUM',NULL,'2026-10-01','user','2026-05-29 00:37:15',NULL,'2026-06-11 14:11:22',0,2),(11,'HT-2026-011','市场推广代理合同','SALES',180000.00,'成都万象互动广告有限公司',4,3,NULL,'ARCHIVED','LOW','2026-06-02','2026-09-01','user','2026-05-22 00:37:15','admin','2026-06-09 10:00:00',0,2),(12,'HT-2026-012','IT系统运维服务合同','TECH',260000.00,'深圳鹏城信息技术有限公司',4,3,20,'ARCHIVED','LOW','2026-05-10','2027-06-03','legal','2026-05-04 00:37:15',NULL,'2026-06-09 10:00:00',0,1),(13,'HT-2026-013','年度安全维保服务合同','TECH',196000.00,'北京天融信安科技有限公司',4,3,20,'EXECUTING','MEDIUM','2026-05-15','2026-08-02','user','2026-05-14 00:37:15',NULL,'2026-06-09 10:00:00',0,1),(14,'HT-2026-014','渠道代理销售协议','SALES',520000.00,'武汉中南商贸集团有限公司',4,3,NULL,'EXECUTING','LOW','2026-05-10','2026-07-03','admin','2026-05-09 00:37:15',NULL,'2026-06-09 10:00:00',0,1),(15,'HT-2026-015','会议会展服务合同','ENTERPRISE_SERVICE',68000.00,'广州广交会展服务有限公司',4,3,15,'COMPLETED','MEDIUM','2026-04-05','2026-06-03','legal','2026-04-04 00:37:15','legal','2026-06-09 10:00:00',0,2);
/*!40000 ALTER TABLE `contract_main` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-06-24 20:32:44
