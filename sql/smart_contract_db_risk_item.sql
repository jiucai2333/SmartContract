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
-- Table structure for table `risk_item`
--

DROP TABLE IF EXISTS `risk_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `risk_item` (
  `risk_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '风险项ID',
  `report_id` bigint unsigned DEFAULT NULL COMMENT '关联风险报告ID',
  `contract_id` bigint unsigned DEFAULT NULL COMMENT '关联合同ID',
  `version_id` bigint unsigned DEFAULT NULL COMMENT '关联合同版本ID',
  `clause_ref` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '风险所在条款位置或引用',
  `risk_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '风险类型：LEGAL法律/BUSINESS商务/FINANCIAL财务/AI_REVIEW模型审查',
  `risk_level` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '风险等级：LOW低/MEDIUM中/HIGH高',
  `suggestion` text CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '处理建议',
  `replacement` text CHARACTER SET utf8mb4 COLLATE utf8mb4_bin,
  `review_status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'PENDING' COMMENT '复核状态：PENDING待复核/CONFIRMED已确认/DISMISSED已忽略/AI_PENDING待AI复核',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`risk_id`),
  KEY `idx_contract_risk` (`contract_id`,`risk_level`),
  KEY `idx_version_id` (`version_id`),
  KEY `idx_risk_item_report` (`report_id`),
  CONSTRAINT `fk_risk_contract` FOREIGN KEY (`contract_id`) REFERENCES `contract_main` (`contract_id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_risk_item_report` FOREIGN KEY (`report_id`) REFERENCES `risk_report` (`report_id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_risk_version` FOREIGN KEY (`version_id`) REFERENCES `contract_version` (`version_id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='风险项表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `risk_item`
--

LOCK TABLES `risk_item` WRITE;
/*!40000 ALTER TABLE `risk_item` DISABLE KEYS */;
INSERT INTO `risk_item` VALUES (1,1,1,1,'付款条款','BUSINESS','MEDIUM','付款节点未绑定验收材料，建议增加发票、验收单和付款审批条件。',NULL,'PENDING','2026-05-30 11:44:38','2026-06-09 10:00:00'),(2,2,4,4,'权属条款','LEGAL','HIGH','知识产权归属与衍生成果授权边界不清，提交审批前必须法务复核。',NULL,'PENDING','2026-06-01 11:44:38','2026-06-09 10:00:00'),(3,3,3,3,'保密条款','LEGAL','LOW','保密期限与数据返还义务描述完整，建议保留当前表述。',NULL,'CONFIRMED','2026-05-24 11:44:38','2026-06-09 10:00:00'),(12,4,8,16,'甲方（委托方/广告主）：上海星禾品牌管理有限公司\n住所地：上海市浦东新区陆家嘴环路1000号18层\n联系人：李明\n联系电话：021-68886666\n\n乙方（服务方/广告服务提供方）：北京云帆数字传媒有限公司\n住所地：北京市朝阳区望京东路1号15层\n联系人：王悦\n联系电话：010-58660088','SUBJECT_INFO','HIGH','应在甲乙双方基本信息处补充统一社会信用代码，并明确联系人为‘授权代表’，注明其职务及签字权限；建议增加电子邮箱、法定代表人姓名等关键信息。','甲方（委托方/广告主）：上海星禾品牌管理有限公司\n统一社会信用代码：91310115MA1FPX1234\n住所地：上海市浦东新区陆家嘴环路1000号18层\n法定代表人：张伟\n授权代表：李明（职务：市场总监）\n联系电话：021-68886666\n电子邮箱：liming@xinghebrand.com\n\n乙方（服务方/广告服务提供方）：北京云帆数字传媒有限公司\n统一社会信用代码：91110105MA00ABCD12\n住所地：北京市朝阳区望京东路1号15层\n法定代表人：陈涛\n授权代表：王悦（职务：运营总监）\n联系电话：010-58660088\n电子邮箱：wangyue@yunfanmedia.com','AI_PENDING','2026-06-24 17:22:45','2026-06-24 17:25:45'),(13,4,8,16,'首期款人民币36000元，于合同生效后5个工作日内支付；\n进度款人民币60000元，于2026年8月15日支付；\n尾款人民币24000元，于服务成果验收或结算确认后5个工作日内支付。','PAYMENT','HIGH','将进度款支付与具体里程碑（如首轮投放完成并提交数据报告）绑定；明确尾款支付需以甲方出具书面《验收确认单》为前提，并限定验收异议期与确认时限。','首期款人民币36000元，于本合同生效后5个工作日内支付；\n进度款人民币60000元，于乙方完成首轮30天投放并提交经甲方签收的《阶段性结案初稿》及平台后台数据截图后5个工作日内支付；\n尾款人民币24000元，于甲方签署《最终验收确认单》后5个工作日内支付；甲方应在收到乙方完整结案材料后5个工作日内完成验收，逾期未提出书面异议视为验收合格。','AI_PENDING','2026-06-24 17:22:45','2026-06-24 17:25:45'),(14,4,8,16,'甲方逾期支付费用的，每逾期一日，应按逾期未付款金额的万分之五向乙方支付违约金；逾期超过15日的，乙方有权暂停服务或解除合同。','LIABILITY','MEDIUM','补充乙方实质性违约情形（如未按期交付、数据严重失真、重大合规事故）的违约金标准（建议按日0.1%或单次服务费20%计）、整改宽限期及甲方单方解约权。','乙方未按约定时间交付核心成果（含投放数据报告、结案材料），每逾期一日，应按当期应付费用千分之一向甲方支付违约金；逾期超过5个工作日，或因乙方原因导致广告被平台下架、账号受限、引发监管处罚的，甲方有权单方解除合同，并要求乙方退还已收款及支付合同总额20%的违约金。','AI_PENDING','2026-06-24 17:22:45','2026-06-24 17:25:45'),(15,4,8,16,'本合同服务期限自2026年7月1日起至2026年9月30日止。期限届满后，双方可另行协商续签或补充约定。\n本项目首轮广告投放周期暂定为30天，具体以双方确认的投放排期表为准。','TERM','HIGH','明确合同项下全部服务（含多轮投放、优化、结案）须在主服务期内完成；如因甲方原因导致排期延后，应约定书面延期程序及超期服务费率。','本合同服务期限自2026年7月1日起至2026年9月30日止，乙方应于该期限内完成全部广告策划、投放、优化及结案工作。首轮投放周期为30天，后续轮次及优化周期由双方在《广告投放排期表》中明确；如因甲方原因需调整排期导致服务延至2026年9月30日后，双方应签订书面补充协议并约定超期服务单价。','AI_PENDING','2026-06-24 17:22:45','2026-06-24 17:25:45'),(16,4,8,16,'本合同的订立、生效、解释、履行、变更、终止及争议解决均适用中华人民共和国法律。双方因本合同产生争议的，应先协商解决；协商不成的，可向北京市朝阳区人民法院提起诉讼，或向北京仲裁委员会申请仲裁，以双方最终书面约定为准。','DISPUTE_RESOLUTION','HIGH','删除仲裁选项，明确唯一管辖法院；根据《民事诉讼法》第35条，优先约定甲方所在地（浦东新区）法院为管辖法院，或采用‘被告住所地’这一法定连接点以确保有效性。','本合同适用中华人民共和国法律。因本合同引起的或与本合同有关的任何争议，双方应首先通过友好协商解决；协商不成的，任何一方均有权向甲方住所地（即上海市浦东新区人民法院）提起诉讼。','AI_PENDING','2026-06-24 17:22:45','2026-06-24 17:25:45');
/*!40000 ALTER TABLE `risk_item` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-06-24 20:32:43
