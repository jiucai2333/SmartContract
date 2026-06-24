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
-- Table structure for table `risk_report`
--

DROP TABLE IF EXISTS `risk_report`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `risk_report` (
  `report_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '风险报告ID',
  `contract_id` bigint unsigned DEFAULT NULL COMMENT '关联合同ID，上传文本审查时为NULL',
  `version_id` bigint unsigned DEFAULT NULL COMMENT '关联合同版本ID，上传文本审查时为NULL',
  `report_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '报告编号，全局唯一',
  `contract_type` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '合同类型（AI识别）',
  `party_a` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '甲方名称（AI识别）',
  `party_b` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '乙方名称（AI识别）',
  `business_scope` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '业务范围描述',
  `highest_risk_level` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'LOW' COMMENT '最高风险等级：LOW/MEDIUM/HIGH',
  `risk_count` int NOT NULL DEFAULT '0' COMMENT '风险项总数',
  `high_count` int NOT NULL DEFAULT '0' COMMENT '高风险数量',
  `medium_count` int NOT NULL DEFAULT '0' COMMENT '中风险数量',
  `low_count` int NOT NULL DEFAULT '0' COMMENT '低风险数量',
  `contract_text` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '被审查的合同原文',
  `summary` text CHARACTER SET utf8mb4 COLLATE utf8mb4_bin COMMENT '风险摘要',
  `model_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '审查使用的模型名称',
  `review_status` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'COMPLETED' COMMENT '审查状态：COMPLETED已完成/PENDING待处理',
  `created_by` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`report_id`),
  UNIQUE KEY `uk_risk_report_no` (`report_no`),
  KEY `idx_risk_report_contract` (`contract_id`,`created_at`),
  KEY `idx_risk_report_version` (`version_id`),
  CONSTRAINT `fk_risk_report_contract` FOREIGN KEY (`contract_id`) REFERENCES `contract_main` (`contract_id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_risk_report_version` FOREIGN KEY (`version_id`) REFERENCES `contract_version` (`version_id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='AI风险审查报告表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `risk_report`
--

LOCK TABLES `risk_report` WRITE;
/*!40000 ALTER TABLE `risk_report` DISABLE KEYS */;
INSERT INTO `risk_report` VALUES (1,1,1,'RISK-20260530114438-0001','PURCHASE','智合科技有限公司','深圳星河制造有限公司','设备采购','MEDIUM',1,0,1,0,'设备采购合同正文摘要。','发现1项中风险，主要集中在付款节点与验收材料绑定不足。','Qwen','COMPLETED','legal','2026-05-30 11:44:38','2026-06-09 10:00:00'),(2,4,4,'RISK-20260601114438-0002','INTELLECTUAL_PROPERTY','智合科技有限公司','北京衡信知识产权代理有限公司','知识产权合作','HIGH',1,1,0,0,'知识产权合作协议正文摘要。','发现1项高风险，主要集中在知识产权归属与衍生成果授权边界。','Qwen','COMPLETED','legal','2026-06-01 11:44:38','2026-06-09 10:00:00'),(3,3,3,'RISK-20260524114438-0003','SALES','智合科技有限公司','成都数联信息有限公司','软件系统销售','LOW',1,0,0,1,'软件系统销售合同正文摘要。','发现1项低风险，保密条款整体完整，仅建议保持现有表述。','Qwen','COMPLETED','legal','2026-05-24 11:44:38','2026-06-09 10:00:00'),(4,8,16,'RISK-V2-20260624172245','服务合同','（委托方/广告主）：上海星禾品牌管理有限公司 住所地：上海市浦东新区陆家嘴环路1000号18层','北京云帆数字传媒有限公司','广告服务合同','HIGH',5,4,1,0,'广告服务合同\n\n \n\n甲方（委托方/广告主）：上海星禾品牌管理有限公司\n\n住所地：上海市浦东新区陆家嘴环路1000号18层\n\n联系人：李明\n\n联系电话：021-68886666\n\n \n\n乙方（服务方/广告服务提供方）：北京云帆数字传媒有限公司\n\n住所地：北京市朝阳区望京东路1号15层\n\n联系人：王悦\n\n联系电话：010-58660088\n\n \n\n根据《中华人民共和国民法典》《中华人民共和国广告法》及相关法律法规，甲、乙双方在平等、自愿、诚实信用的基础上，就乙方向甲方提供广告服务事宜达成如下协议。\n\n \n\n第一条 服务内容\n\n1. 乙方接受甲方委托，提供广告策划、创意设计、文案撰写、素材制作、媒介投放、数据监测、投放优化及结案报告等服务。\n\n2. 本项目主要投放平台包括但不限于：微信、抖音、小红书、微博、百度信息流及双方书面确认的其他平台。\n\n3. 广告发布形式包括图文广告、短视频广告、信息流广告、搜索推广、达人合作内容及其他双方确认的广告形式。\n\n4. 具体服务范围、投放平台、媒介渠道、发布形式、投放周期、预算安排及交付要求，以本合同附件、排期表、报价单或双方书面确认文件为准。\n\n5. 未经甲方书面确认，乙方不得擅自变更广告内容、投放时间、投放渠道、预算分配或其他主要执行安排。\n\n \n\n第二条 服务期限\n\n本合同服务期限自2026年7月1日起至2026年9月30日止。期限届满后，双方可另行协商续签或补充约定。\n\n本项目首轮广告投放周期暂定为30天，具体以双方确认的投放排期表为准。\n\n \n\n第三条 双方权利义务\n\n1. 甲方应向乙方提供真实、准确、完整的品牌资料、产品信息、资质文件、授权文件、素材文件及其他必要资料，并保证上述资料不侵犯第三方合法权益。\n\n2. 甲方应于合同生效后3个工作日内向乙方提供项目所需资料。\n\n3. 甲方有权对广告服务方案、广告内容、投放计划、预算使用及执行进度进行审核、确认和监督，并应按约及时支付相关费用。\n\n4. 乙方应按照甲方确认的服务范围、投放计划和预算安排提供服务，及时报告项目进展、费用使用和投放效果。\n\n5. 乙方应每5个工作日向甲方提交一次阶段性投放数据或项目进度报告。\n\n6. 乙方应对广告内容进行必要的形式审查和合规提示，发现违法、违规或侵权风险的，应及时向甲方提出修改建议。\n\n7. 未经甲方书面同意，乙方不得将本合同项下主要服务事项转委托给第三方。\n\n \n\n第四条 服务费用及付款方式\n\n1. 本合同费用包括广告服务费、广告制作费、媒介投放费、第三方采购费及双方确认的其他费用。\n\n2. 合同总金额为人民币120000元（大写：壹拾贰万元整）。如媒介投放费用以平台结算或第三方账单为准，双方以书面确认的结算文件作为付款依据。\n\n3. 甲方付款安排如下：\n\n首期款人民币36000元，于合同生效后5个工作日内支付；\n\n进度款人民币60000元，于2026年8月15日支付；\n\n尾款人民币24000元，于服务成果验收或结算确认后5个工作日内支付。\n\n4. 如甲方需调整广告预算，应至少提前5个工作日书面通知乙方，并经双方确认后执行。\n\n5. 乙方收款账户：\n\n户名：北京云帆数字传媒有限公司；\n\n账号：110102030405060708；\n\n开户行：招商银行北京望京支行。\n\n6. 乙方应按甲方要求提供合法有效的发票，发票类型为：增值税专用发票（税率6%）。\n\n \n\n第五条 验收与结算\n\n1. 乙方完成约定服务并提交成果、投放数据、费用明细及结案材料后，甲方应在5个工作日内进行验收或提出书面修改意见。\n\n2. 验收标准包括：服务内容符合双方确认的方案或排期；广告素材已按确认稿完成；投放数据、平台记录或第三方证明能够反映实际执行情况；结案报告内容完整。\n\n3. 乙方应提交的结案材料包括：投放数据报告、费用明细、广告截图或链接、平台后台数据证明及其他双方确认的材料。\n\n4. 甲方认为成果不符合约定的，乙方应在3个工作日内修改、补充或说明；经修改后仍不符合约定的，甲方有权要求继续整改、减少相应费用或追究违约责任。\n\n \n\n第六条 知识产权、合规与保密\n\n1. 甲方提供的品牌、商标、标识、图片、文字、音视频、数据及其他素材，其权利归甲方或相应权利人所有。\n\n2. 乙方为履行本合同形成的广告成果，在甲方付清相应费用后按双方约定由甲方使用；涉及乙方通用方法、模板、工具、经验或第三方授权素材的，相关权利仍归原权利人所有。\n\n3. 双方应遵守广告法律法规、平台规则及行业规范。甲方对其提供资料及广告基础信息的真实性、合法性和完整性负责；乙方应结合专业能力提供必要合规建议。\n\n4. 双方对履约过程中知悉的商业秘密、客户信息、账户信息、广告数据、价格政策、投放策略及其他未公开信息负有保密义务，未经对方书面同意不得向第三方披露或用于本合同目的之外。\n\n \n\n第七条 违约责任\n\n1. 甲方逾期支付费用的，每逾期一日，应按逾期未付款金额的万分之五向乙方支付违约金；逾期超过15日的，乙方有权暂停服务或解除合同。\n\n2. 乙方未按约定时间、范围或质量要求提供服务，经甲方书面催告后仍未在合理期限内改正的，甲方有权要求乙方继续履行、采取补救措施、减少费用或赔偿损失。\n\n3. 任一方违反保密、知识产权、合规或其他合同义务，给对方造成损失的，应承担相应赔偿责任。因一方提供资料不真实、不合法、不完整或未经授权使用第三方内容导致纠纷、处罚、投诉、下架、账号受限或索赔的，由责任方承担责任。\n\n \n\n第八条 合同变更、解除与不可抗力\n\n1. 本合同履行过程中，如需调整服务内容、预算、交付周期、媒介渠道、结算方式或其他重要事项，双方应协商一致并以书面形式确认。\n\n2. 任何一方严重违约，且经守约方书面催告后仍未在合理期限内改正的，守约方有权解除合同并要求违约方承担责任。合同解除或终止后，双方应根据已实际完成服务、已发生费用及过错情况进行结算。\n\n3. 因不可抗力导致不能履行或不能完全履行合同的，受影响方应及时通知对方并在合理期限内提供证明。双方可协商延期履行、部分履行、变更合同或解除合同。\n\n \n\n第九条 通知与争议解决\n\n1. 双方在本合同中载明的联系地址、联系电话、电子邮箱为合同履行及争议解决的有效联系方式和送达地址。联系方式变更的，应在3日内书面通知对方；未及时通知的，按原地址或方式送达视为有效送达。\n\n2. 本合同的订立、生效、解释、履行、变更、终止及争议解决均适用中华人民共和国法律。双方因本合同产生争议的，应先协商解决；协商不成的，可向北京市朝阳区人民法院提起诉讼，或向北京仲裁委员会申请仲裁，以双方最终书面约定为准。\n\n \n\n第十条 附则\n\n1. 本合同自甲、乙双方签名或盖章之日起成立并生效。\n\n2. 本合同附件、排期表、报价单、确认单、验收单、结算单及双方书面确认的其他文件为本合同组成部分，与本合同具有同等法律效力。\n\n3. 本合同一式4份，甲方执2份，乙方执2份，具有同等法律效力。\n\n4. 其他约定：本合同附件包括《广告投放排期表》《报价单》《验收确认单》，与本合同具有同等法律效力。\n\n \n\n \n\n甲方负责人（或授权代表）         乙方负责人（或授权代表）\n\n签名：李明       （盖章）        签名：王悦       （盖章）\n\n \n\n签署日期：2026年7月1日         签署日期：2026年7月1日','风险审查完成，共发现 5 项风险，其中高风险 4 项、中风险 1 项、低风险 0 项。','Qwen','COMPLETED','admin','2026-06-24 17:22:45','2026-06-24 17:27:21');
/*!40000 ALTER TABLE `risk_report` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-06-24 20:32:32
