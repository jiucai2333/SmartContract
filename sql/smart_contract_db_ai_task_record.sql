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
-- Table structure for table `ai_task_record`
--

DROP TABLE IF EXISTS `ai_task_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_task_record` (
  `task_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'AI任务ID',
  `contract_id` bigint unsigned NOT NULL COMMENT '所属合同ID',
  `task_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '任务类型：AUDIT风险审查/GENERATE合同生成',
  `model_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '调用模型名称',
  `prompt_hash` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '提示词SHA-256哈希，用于缓存复用',
  `token_usage` int unsigned NOT NULL DEFAULT '0' COMMENT 'Token消耗量',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '任务状态：SUCCESS成功/FAILED失败/RUNNING进行中',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `input_summary` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'input summary',
  `output_summary` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'output summary',
  `error_reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'error reason',
  `duration_ms` bigint DEFAULT NULL COMMENT 'duration milliseconds',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
  PRIMARY KEY (`task_id`),
  KEY `idx_contract_id` (`contract_id`),
  CONSTRAINT `fk_ai_task_contract` FOREIGN KEY (`contract_id`) REFERENCES `contract_main` (`contract_id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='AI任务记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_task_record`
--

LOCK TABLES `ai_task_record` WRITE;
/*!40000 ALTER TABLE `ai_task_record` DISABLE KEYS */;
INSERT INTO `ai_task_record` VALUES (1,1,'AUDIT','qwen-plus','fe4a2b0edad290b3f2afa5590a984e3a75e75bff9de67ac1fe83800ffd2c4218',1820,'SUCCESS','legal','2026-05-30 11:44:38',NULL,NULL,NULL,NULL,'2026-06-23 00:29:03'),(2,4,'AUDIT','qwen-plus','434a9500f8466a807f659387447df9c3dff54a37fd4e7b1dc8c3378b7f9f7263',2340,'SUCCESS','legal','2026-06-01 11:44:38',NULL,NULL,NULL,NULL,'2026-06-23 00:29:03'),(3,2,'GENERATE','qwen-plus','e3fd2337ed17eb50fc83fd4c8ba08f7f5ad3a4775c3493c710bcb984e1ca7fed',1560,'SUCCESS','user','2026-05-31 11:44:38',NULL,NULL,NULL,NULL,'2026-06-23 00:29:03'),(4,8,'AUDIT','qwen-plus','35b8838e713766d6367b67b3aa4842fe03ee4ec181f3fd46af4a1d8650c32525',2680,'SUCCESS','legal','2026-06-09 18:59:15',NULL,NULL,NULL,NULL,'2026-06-23 00:29:03');
/*!40000 ALTER TABLE `ai_task_record` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-06-24 20:32:26
