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
-- Table structure for table `contract_template`
--

DROP TABLE IF EXISTS `contract_template`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `contract_template` (
  `template_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '模板ID',
  `template_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '模板类型',
  `template_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '模板名称',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_bin COMMENT '模板描述',
  `file_id` bigint unsigned NOT NULL COMMENT '关联文件ID',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT 'SYSTEM_INIT' COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除：0未删除，1已删除',
  `version` int unsigned NOT NULL DEFAULT '1' COMMENT '乐观锁版本号',
  PRIMARY KEY (`template_id`),
  KEY `idx_file_id` (`file_id`),
  CONSTRAINT `fk_template_file` FOREIGN KEY (`file_id`) REFERENCES `file_info` (`file_id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=27 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='合同模板表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `contract_template`
--

LOCK TABLES `contract_template` WRITE;
/*!40000 ALTER TABLE `contract_template` DISABLE KEYS */;
INSERT INTO `contract_template` VALUES (1,'PURCHASE','办公用品采购合同','适用于办公用品采购，用于约定供货、验收、结算及质量责任。',1,'SYSTEM_INIT','2026-06-03 19:50:03',NULL,'2026-06-04 19:38:07',0,1),(2,'LABOR','变更劳动合同','适用于劳动合同变更，用于约定变更事项及原合同继续履行。',2,'SYSTEM_INIT','2026-06-03 19:50:03',NULL,'2026-06-04 19:38:07',0,1),(3,'ENTERPRISE_SERVICE','财税顾问服务协议','适用于财税顾问服务，用于约定咨询、筹划、培训、费用及保密责任。',3,'SYSTEM_INIT','2026-06-03 19:50:03',NULL,'2026-06-04 19:38:07',0,1),(4,'LOGISTICS','仓库租赁合同','适用于仓库或厂房租赁，用于约定租期、租金、使用及维护责任。',4,'SYSTEM_INIT','2026-06-03 19:50:03',NULL,'2026-06-04 19:38:07',0,1),(5,'LABOR','带薪实习合同','适用于学生带薪实习，用于约定岗位、补助、管理及保密义务。',5,'SYSTEM_INIT','2026-06-03 19:50:03',NULL,'2026-06-04 19:38:07',0,1),(6,'ENTERPRISE_SERVICE','会计代账服务协议','适用于代理记账服务，用于约定票据交接、记账报税、费用及交接责任。',6,'SYSTEM_INIT','2026-06-03 19:50:03',NULL,'2026-06-04 19:38:07',0,1),(7,'LOGISTICS','货物运输合同','适用于货物运输委托，用于约定货物、路线、费用及运输责任。',7,'SYSTEM_INIT','2026-06-03 19:50:03',NULL,'2026-06-04 19:38:07',0,1),(8,'TECH','计算机软件许可合同','适用于软件许可使用，用于约定系统交付、安装、培训及使用限制。',8,'SYSTEM_INIT','2026-06-03 19:50:03',NULL,'2026-06-04 19:38:07',0,1),(9,'TECH','技术委托开发合同','适用于技术项目委托开发，用于约定开发经费、成果交付及知识产权。',9,'SYSTEM_INIT','2026-06-03 19:50:03',NULL,'2026-06-04 19:38:07',0,1),(10,'TECH','技术委托开发框架协议','适用于长期软件开发合作，用于约定任务单、费用、验收及成果归属。',10,'SYSTEM_INIT','2026-06-03 19:50:03',NULL,'2026-06-04 19:38:07',0,1),(11,'TECH','技术转让合同','适用于技术成果转让，用于约定技术资料、使用范围、费用及保密义务。',11,'SYSTEM_INIT','2026-06-03 19:50:03',NULL,'2026-06-04 19:38:07',0,1),(12,'TECH','技术咨询合同','适用于技术咨询服务，用于约定咨询范围、报告交付、费用及保密义务。',12,'SYSTEM_INIT','2026-06-03 19:50:03',NULL,'2026-06-04 19:38:07',0,1),(13,'LABOR','劳务派遣协议','适用于劳务派遣用工，用于约定派遣岗位、薪酬、社保及管理责任。',13,'SYSTEM_INIT','2026-06-03 19:50:03',NULL,'2026-06-04 19:38:07',0,1),(14,'LABOR','劳务外包合同','适用于劳务或兼职外包，用于约定服务范围、人员管理、费用及责任。',14,'SYSTEM_INIT','2026-06-03 19:50:03',NULL,'2026-06-04 19:38:07',0,1),(15,'ENTERPRISE_SERVICE','企业顾问咨询合同','适用于企业管理咨询，用于约定咨询内容、服务方式、费用及保密责任。',15,'SYSTEM_INIT','2026-06-03 19:50:03',NULL,'2026-06-04 19:38:07',0,1),(16,'TECH','软件技术服务合同','适用于软件技术服务，用于约定服务内容、费用、保密及违约责任。',16,'SYSTEM_INIT','2026-06-03 19:50:03',NULL,'2026-06-04 19:38:07',0,1),(17,'TECH','软件技术开发合同','适用于软件系统开发，用于约定开发目标、进度、付款及成果归属。',17,'SYSTEM_INIT','2026-06-03 19:50:03',NULL,'2026-06-04 19:38:07',0,1),(18,'PURCHASE','设备采购合同','适用于办公或生产设备采购，用于约定设备参数、安装验收、付款及保修。',18,'SYSTEM_INIT','2026-06-03 19:50:03',NULL,'2026-06-04 19:38:07',0,1),(19,'PURCHASE','物资采购合同','适用于生产经营物资采购，用于约定质量、交货、验收及付款方式。',19,'SYSTEM_INIT','2026-06-03 19:50:03',NULL,'2026-06-04 19:38:07',0,1),(20,'TECH','系统维保合同','适用于系统运行维护，用于约定维保范围、巡检、抢修及服务费用。',20,'SYSTEM_INIT','2026-06-03 19:50:03',NULL,'2026-06-04 19:38:07',0,1),(21,'LABOR','员工试用期合同','适用于员工试用期管理，用于约定岗位、工资、考勤及转正条件。',21,'SYSTEM_INIT','2026-06-03 19:50:03',NULL,'2026-06-04 19:38:07',0,1),(22,'LABOR','终止雇佣协议','适用于劳动关系终止，用于约定离职补偿、权利放弃及双方确认。',22,'SYSTEM_INIT','2026-06-03 19:50:03',NULL,'2026-06-04 19:38:07',0,1),(23,'INTELLECTUAL_PROPERTY','注册商标使用许可合同','适用于商标授权使用，用于约定许可范围、期限、费用及备案事项。',23,'SYSTEM_INIT','2026-06-03 19:50:03',NULL,'2026-06-04 19:38:07',0,1),(24,'INTELLECTUAL_PROPERTY','著作权许可协议','适用于著作权授权使用，用于约定使用范围、数字化处理及权利保护。',24,'SYSTEM_INIT','2026-06-03 19:50:03',NULL,'2026-06-04 19:38:07',0,1),(25,'LABOR','专家聘请协议','适用于聘请专家顾问，用于约定指导内容、服务期限、报酬及责任。',25,'SYSTEM_INIT','2026-06-03 19:50:03',NULL,'2026-06-04 19:38:07',0,1),(26,'TECH','委托开发合同','适用于委托开发项目，用于约定开发内容、交付验收、费用及成果归属。',48,'template-migration','2026-06-10 12:00:47','template-migration','2026-06-10 12:00:47',0,1);
/*!40000 ALTER TABLE `contract_template` ENABLE KEYS */;
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
