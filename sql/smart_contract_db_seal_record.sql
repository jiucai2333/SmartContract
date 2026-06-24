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
-- Table structure for table `seal_record`
--

DROP TABLE IF EXISTS `seal_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `seal_record` (
  `seal_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '签章记录ID',
  `contract_id` bigint unsigned NOT NULL COMMENT '所属合同ID',
  `version_id` bigint unsigned NOT NULL COMMENT '签章对应的合同版本ID',
  `file_id` bigint unsigned DEFAULT NULL COMMENT '签章文件ID（上传的签章附件）',
  `file_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '签章文件访问地址',
  `file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '签章文件名称',
  `seal_status` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'SEALED' COMMENT '签章状态：SEALED纸质盖章/ELECTRONIC电子签章/PENDING待签章',
  `seal_time` datetime DEFAULT NULL COMMENT '签章时间',
  `operator_id` bigint unsigned DEFAULT NULL COMMENT '经办人用户ID',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '签章备注',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除：0未删除，1已删除',
  `version` int unsigned NOT NULL DEFAULT '1' COMMENT '乐观锁版本号',
  `signature_provider` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT 'local' COMMENT '签章平台标识',
  `transaction_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '签章平台流水号',
  `signature_data` json DEFAULT NULL COMMENT '签章平台返回详情（JSON）',
  `file_hash` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '签章文件SHA-256哈希',
  `blockchain_hash` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '区块链存证哈希',
  PRIMARY KEY (`seal_id`),
  KEY `idx_contract_id` (`contract_id`),
  KEY `idx_version_id` (`version_id`),
  KEY `fk_seal_file` (`file_id`),
  KEY `fk_seal_operator` (`operator_id`),
  CONSTRAINT `fk_seal_contract` FOREIGN KEY (`contract_id`) REFERENCES `contract_main` (`contract_id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_seal_file` FOREIGN KEY (`file_id`) REFERENCES `file_info` (`file_id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_seal_operator` FOREIGN KEY (`operator_id`) REFERENCES `user_info` (`user_id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_seal_version` FOREIGN KEY (`version_id`) REFERENCES `contract_version` (`version_id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='签章登记记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `seal_record`
--

LOCK TABLES `seal_record` WRITE;
/*!40000 ALTER TABLE `seal_record` DISABLE KEYS */;
INSERT INTO `seal_record` VALUES (1,1,1,41,'/api/files/41/download','HT-2026-001-签章文件.pdf','ELECTRONIC','2026-05-31 23:16:00',1,'电子签章完成。','admin','2026-05-31 23:16:00',NULL,'2026-06-09 10:00:00',0,1,'local','LOCAL-SEAL-20260609-001','{\"operator\": \"admin\", \"sealType\": \"ELECTRONIC\"}','d41074ed4a46b37ff595637a038070f369ed439e58dfc80e5c2c62f5d088d82a','bdf2feb171c9412bbba4ef52fd872eb36dd52359219808dfd787e48c20b6cce3'),(2,2,2,42,'/api/files/42/download','HT-2026-002-签章文件.pdf','ELECTRONIC','2026-06-04 03:34:00',1,'电子签章完成。','admin','2026-06-04 03:34:00',NULL,'2026-06-09 10:00:00',0,1,'local','LOCAL-SEAL-20260609-002','{\"operator\": \"admin\", \"sealType\": \"ELECTRONIC\"}','16a299f3091046784f6b35d2c19c298dd15240f2a2b07efd2502391d719c4aaa','808796d1fbd07bc31c9e8512f4441b1caf6aa98bf1e0bf4569efbb00429e1859'),(3,7,7,43,'/api/files/43/download','HT-2026-007-签章文件.pdf','ELECTRONIC','2026-06-05 13:24:00',1,'电子签章完成。','admin','2026-06-05 13:24:00',NULL,'2026-06-09 10:00:00',0,1,'local','LOCAL-SEAL-20260609-003','{\"operator\": \"admin\", \"sealType\": \"ELECTRONIC\"}','35f82522acced7bc2eb619e7415d31ee467b59defe3685abe4730c734ca128cc','4144ecffae663576492c25e72b16c893ecd7162a9ffa7be7ca6c353adbd4fc75'),(4,9,9,49,NULL,NULL,'ELECTRONIC','2026-06-23 10:36:16',1,NULL,'SIGN_1','2026-06-23 10:36:16',NULL,'2026-06-23 10:36:16',0,2,'fadada','1782182174412135918','{\"signUrl\": \"https://api.fadada.com/Jkr1i4jxoHS\"}',NULL,'3f1f0785cfa52f6ea52c7d39d9b65ab3db70c7f8161db15f35718525196a5653'),(5,9,9,NULL,NULL,NULL,'ELECTRONIC','2026-06-23 14:29:09',1,NULL,'SIGN_1','2026-06-23 14:29:09',NULL,'2026-06-23 14:29:09',0,2,'fadada','1782196147302157009','{\"signUrl\": \"https://api.fadada.com/1ZHA1zXjvyF\"}',NULL,'ad4a37ba04fd0019a4e4b2bea1adb67a9f9039b2de7b8eceaa6d687d75693071'),(6,4,4,72,NULL,NULL,'ELECTRONIC','2026-06-24 20:17:21',1,NULL,'SIGN_1','2026-06-24 20:17:21',NULL,'2026-06-24 20:17:21',0,2,'fadada','1782303440060114508','{\"signUrl\": \"https://test.fdd1.cn/7eErJ7feRJn\"}',NULL,'2dbadffdf3ad4a1d35645ebd3e2ebb7f307142d7408935b09616758598a3a619');
/*!40000 ALTER TABLE `seal_record` ENABLE KEYS */;
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
