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
-- Table structure for table `blockchain_record`
--

DROP TABLE IF EXISTS `blockchain_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `blockchain_record` (
  `record_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '存证记录ID',
  `contract_id` bigint unsigned NOT NULL COMMENT '所属合同ID',
  `version_id` bigint unsigned DEFAULT NULL COMMENT '关联合同版本ID',
  `record_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '存证类型：VERSION_CREATE版本创建/SEAL签章/ARCHIVE归档',
  `summary` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '存证摘要',
  `node_hash` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '当前节点SHA-256哈希',
  `previous_hash` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT '0x0' COMMENT '前一节点哈希（链式结构）',
  `merkle_root` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '默克尔树根哈希',
  `snapshot_data` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin COMMENT '瀛樿瘉鏁版嵁蹇収(鍘熷JSON瀛楃涓诧紝涓嶅彲褰掍竴鍖?',
  `recorded_at` datetime DEFAULT NULL COMMENT '存证时间',
  `created_by` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '创建人',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`record_id`),
  KEY `idx_contract_id` (`contract_id`),
  KEY `idx_record_type` (`record_type`),
  KEY `fk_blockchain_version` (`version_id`),
  CONSTRAINT `fk_blockchain_contract` FOREIGN KEY (`contract_id`) REFERENCES `contract_main` (`contract_id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_blockchain_version` FOREIGN KEY (`version_id`) REFERENCES `contract_version` (`version_id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='区块链存证记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `blockchain_record`
--

LOCK TABLES `blockchain_record` WRITE;
/*!40000 ALTER TABLE `blockchain_record` DISABLE KEYS */;
INSERT INTO `blockchain_record` VALUES (1,1,1,'SEAL','Seal anchor','c35d8374ff4a5e68c2edfc24784566ba798c5feb4e2605780f2af65d0aee7803','0000000000000000000000000000000000000000000000000000000000000000','c35d8374ff4a5e68c2edfc24784566ba798c5feb4e2605780f2af65d0aee7803','{\"contractId\":1,\"contractNo\":\"HT-2026-001\",\"title\":\"设备采购合同\",\"type\":\"PURCHASE\",\"amount\":128000.00,\"counterparty\":\"深圳星河制造有限公司\",\"status\":\"ARCHIVED\",\"signDate\":[2026,5,31],\"dueDate\":[2026,6,26],\"seals\":[{\"sealId\":1,\"sealStatus\":\"ELECTRONIC\",\"sealTime\":[2026,5,31,23,16],\"signatureProvider\":\"local\",\"fileHash\":\"d41074ed4a46b37ff595637a038070f369ed439e58dfc80e5c2c62f5d088d82a\"}]}','2026-06-22 20:51:55','BLOCKCHAIN_SVC','2026-06-22 20:51:55'),(2,1,1,'ARCHIVE','Archive anchor','45d42f5da11ccc1a965e037123fb6eeae1949318058be39be6742af3168cc6bb','c35d8374ff4a5e68c2edfc24784566ba798c5feb4e2605780f2af65d0aee7803','b69e85616980cdb886078d22c71032903be67b056c0a47e16ed814ff2cc486bf','{\"contractId\":1,\"contractNo\":\"HT-2026-001\",\"title\":\"设备采购合同\",\"type\":\"PURCHASE\",\"amount\":128000.00,\"counterparty\":\"深圳星河制造有限公司\",\"status\":\"ARCHIVED\",\"signDate\":[2026,5,31],\"dueDate\":[2026,6,26],\"seals\":[{\"sealId\":1,\"sealStatus\":\"ELECTRONIC\",\"sealTime\":[2026,5,31,23,16],\"signatureProvider\":\"local\",\"fileHash\":\"d41074ed4a46b37ff595637a038070f369ed439e58dfc80e5c2c62f5d088d82a\"}]}','2026-06-22 20:51:55','BLOCKCHAIN_SVC','2026-06-22 20:51:55'),(3,11,11,'ARCHIVE','Archive anchor','7ee532d63ade499b1b387bd78f06deb535c48475722051c55a53c291f82b8384','0000000000000000000000000000000000000000000000000000000000000000','7ee532d63ade499b1b387bd78f06deb535c48475722051c55a53c291f82b8384','{\"contractId\":11,\"contractNo\":\"HT-2026-011\",\"title\":\"市场推广代理合同\",\"type\":\"SALES\",\"amount\":180000.00,\"counterparty\":\"成都万象互动广告有限公司\",\"status\":\"ARCHIVED\",\"signDate\":[2026,6,2],\"dueDate\":[2026,9,1]}','2026-06-22 20:51:56','BLOCKCHAIN_SVC','2026-06-22 20:51:56'),(4,2,2,'SEAL','Seal anchor','a129f31ed216bf40d6d65cdb21b071f81a14b862eba3d4836c0b9a5f0d875af8','0000000000000000000000000000000000000000000000000000000000000000','a129f31ed216bf40d6d65cdb21b071f81a14b862eba3d4836c0b9a5f0d875af8','{\"contractId\":2,\"contractNo\":\"HT-2026-002\",\"title\":\"企业技术服务合同\",\"type\":\"TECH\",\"amount\":46000.00,\"counterparty\":\"杭州云策科技有限公司\",\"status\":\"SIGNING\",\"signDate\":[2026,6,4],\"dueDate\":[2026,8,31],\"seals\":[{\"sealId\":2,\"sealStatus\":\"ELECTRONIC\",\"sealTime\":[2026,6,4,3,34],\"signatureProvider\":\"local\",\"fileHash\":\"16a299f3091046784f6b35d2c19c298dd15240f2a2b07efd2502391d719c4aaa\"}]}','2026-06-22 20:51:56','BLOCKCHAIN_SVC','2026-06-22 20:51:56'),(5,7,7,'SEAL','Seal anchor','c2e478a764f37477ffeaaca835f7f6ee966804bf446cf5f436e17c5894a16f30','0000000000000000000000000000000000000000000000000000000000000000','c2e478a764f37477ffeaaca835f7f6ee966804bf446cf5f436e17c5894a16f30','{\"contractId\":7,\"contractNo\":\"HT-2026-007\",\"title\":\"员工培训服务协议\",\"type\":\"ENTERPRISE_SERVICE\",\"amount\":32000.00,\"counterparty\":\"北京博雅教育咨询有限公司\",\"status\":\"SIGNING\",\"signDate\":[2026,6,5],\"dueDate\":[2026,8,2],\"seals\":[{\"sealId\":3,\"sealStatus\":\"ELECTRONIC\",\"sealTime\":[2026,6,5,13,24],\"signatureProvider\":\"local\",\"fileHash\":\"35f82522acced7bc2eb619e7415d31ee467b59defe3685abe4730c734ca128cc\"}]}','2026-06-22 20:51:57','BLOCKCHAIN_SVC','2026-06-22 20:51:57'),(6,10,10,'SEAL','Seal anchor','ea1e8f97fca7fc201744eda05a224eb3232be573d48d6f04484c35caade8cc10','0000000000000000000000000000000000000000000000000000000000000000','ea1e8f97fca7fc201744eda05a224eb3232be573d48d6f04484c35caade8cc10','{\"contractId\":10,\"contractNo\":\"HT-2026-010\",\"title\":\"品牌授权合作协议\",\"type\":\"INTELLECTUAL_PROPERTY\",\"amount\":150000.00,\"counterparty\":\"杭州天域品牌管理有限公司\",\"status\":\"SIGNING\",\"signDate\":null,\"dueDate\":[2026,10,1],\"seals\":[{\"sealId\":4,\"sealStatus\":\"ELECTRONIC\",\"sealTime\":[2026,6,11,14,11],\"signatureProvider\":\"local\",\"fileHash\":null}]}','2026-06-22 20:51:57','BLOCKCHAIN_SVC','2026-06-22 20:51:57'),(7,9,9,'SEAL','电子签章锚定','3f1f0785cfa52f6ea52c7d39d9b65ab3db70c7f8161db15f35718525196a5653','0000000000000000000000000000000000000000000000000000000000000000','3f1f0785cfa52f6ea52c7d39d9b65ab3db70c7f8161db15f35718525196a5653','{\"contractId\":9,\"contractNo\":\"HT-2026-009\",\"title\":\"原材料供应长期合同\",\"type\":\"PURCHASE\",\"amount\":420000.00,\"counterparty\":\"广州鑫源钢铁贸易有限公司\",\"status\":\"SIGNING\",\"signDate\":null,\"dueDate\":[2026,11,30],\"seals\":[{\"sealId\":20,\"sealStatus\":\"ELECTRONIC\",\"sealTime\":[2026,6,23,10,36,16],\"signatureProvider\":\"fadada\",\"fileHash\":null}]}','2026-06-23 10:36:16','BLOCKCHAIN_SVC','2026-06-23 10:36:16'),(8,9,9,'SEAL','电子签章锚定','ad4a37ba04fd0019a4e4b2bea1adb67a9f9039b2de7b8eceaa6d687d75693071','3f1f0785cfa52f6ea52c7d39d9b65ab3db70c7f8161db15f35718525196a5653','46a2a3ffde6ebea1e5a091fa701c16ee2bc4e5afeb988e772dad781d083e54b3','{\"contractId\":9,\"contractNo\":\"HT-2026-009\",\"title\":\"原材料供应长期合同\",\"type\":\"PURCHASE\",\"amount\":420000.00,\"counterparty\":\"广州鑫源钢铁贸易有限公司\",\"status\":\"SIGNING\",\"signDate\":null,\"dueDate\":[2026,11,30],\"seals\":[{\"sealId\":20,\"sealStatus\":\"ELECTRONIC\",\"sealTime\":[2026,6,23,10,36,16],\"signatureProvider\":\"fadada\",\"fileHash\":null},{\"sealId\":21,\"sealStatus\":\"ELECTRONIC\",\"sealTime\":[2026,6,23,14,29,9],\"signatureProvider\":\"fadada\",\"fileHash\":null}]}','2026-06-23 14:29:09','BLOCKCHAIN_SVC','2026-06-23 14:29:09'),(9,4,4,'SEAL','电子签章锚定','2dbadffdf3ad4a1d35645ebd3e2ebb7f307142d7408935b09616758598a3a619','0000000000000000000000000000000000000000000000000000000000000000','2dbadffdf3ad4a1d35645ebd3e2ebb7f307142d7408935b09616758598a3a619','{\"contractId\":4,\"contractNo\":\"HT-2026-004\",\"title\":\"知识产权合作协议\",\"type\":\"INTELLECTUAL_PROPERTY\",\"amount\":880000.00,\"counterparty\":\"北京衡信知识产权代理有限公司\",\"status\":\"SIGNING\",\"signDate\":null,\"dueDate\":[2026,9,28],\"seals\":[{\"sealId\":6,\"sealStatus\":\"ELECTRONIC\",\"sealTime\":[2026,6,24,20,17,21],\"signatureProvider\":\"fadada\",\"fileHash\":null}]}','2026-06-24 20:17:21','BLOCKCHAIN_SVC','2026-06-24 20:17:21');
/*!40000 ALTER TABLE `blockchain_record` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-06-24 20:32:36
