-- ============================================================
-- 演示合同种子数据（10条，覆盖全生命周期）
-- ============================================================
USE `smart_contract_db`;

DELETE FROM `contract_main` WHERE `contract_id` > 1005;
DELETE FROM `contract_version` WHERE `version_id` > 5;

INSERT INTO `contract_main` (`contract_id`,`contract_no`,`title`,`type`,`amount`,`counterparty`,`dept_id`,`owner_id`,`status`,`risk_level`,`due_date`,`created_by`,`created_at`) VALUES
(2001,'HT-2026-2001','办公设备采购合同','PURCHASE',85000.00,'深圳华强数码科技有限公司',4,3,'DRAFT','MEDIUM',DATE_ADD(CURDATE(),INTERVAL 45 DAY),'user',DATE_SUB(NOW(),INTERVAL 10 DAY)),
(2002,'HT-2026-2002','员工培训服务协议','LABOR',32000.00,'北京博雅教育咨询有限公司',4,3,'DRAFT','LOW',DATE_ADD(CURDATE(),INTERVAL 60 DAY),'user',DATE_SUB(NOW(),INTERVAL 3 DAY)),
(2003,'HT-2026-2003','物流仓储服务框架协议','PURCHASE',280000.00,'上海顺通达物流有限公司',4,3,'APPROVING','MEDIUM',DATE_ADD(CURDATE(),INTERVAL 90 DAY),'user',DATE_SUB(NOW(),INTERVAL 2 DAY)),
(2004,'HT-2026-2004','原材料供应长期合同','PURCHASE',420000.00,'广州鑫源钢铁贸易有限公司',4,3,'APPROVED','MEDIUM',DATE_ADD(CURDATE(),INTERVAL 180 DAY),'legal',DATE_SUB(NOW(),INTERVAL 7 DAY)),
(2005,'HT-2026-2005','品牌授权合作协议','TECH',150000.00,'杭州天域品牌管理有限公司',4,3,'APPROVED','LOW',DATE_ADD(CURDATE(),INTERVAL 120 DAY),'legal',DATE_SUB(NOW(),INTERVAL 5 DAY)),
(2006,'HT-2026-2006','市场推广代理合同','SALES',180000.00,'成都万象互动广告有限公司',4,3,'SIGNING','LOW',DATE_ADD(CURDATE(),INTERVAL 90 DAY),'legal',DATE_SUB(NOW(),INTERVAL 12 DAY)),
(2007,'HT-2026-2007','IT系统运维服务合同','TECH',260000.00,'深圳鹏城信息技术有限公司',4,3,'ARCHIVED','MEDIUM',DATE_ADD(CURDATE(),INTERVAL 365 DAY),'legal',DATE_SUB(NOW(),INTERVAL 30 DAY)),
(2008,'HT-2026-2008','年度安全维保服务合同','TECH',196000.00,'北京天融信安科技有限公司',4,3,'EXECUTING','LOW',DATE_ADD(CURDATE(),INTERVAL 60 DAY),'admin',DATE_SUB(NOW(),INTERVAL 20 DAY)),
(2009,'HT-2026-2009','渠道代理销售协议','SALES',520000.00,'武汉中南商贸集团有限公司',4,3,'EXECUTING','MEDIUM',DATE_ADD(CURDATE(),INTERVAL 30 DAY),'admin',DATE_SUB(NOW(),INTERVAL 25 DAY)),
(2010,'HT-2026-2010','会议会展服务合同','LABOR',68000.00,'广州广交会展服务有限公司',4,3,'COMPLETED','LOW',CURDATE(),'admin',DATE_SUB(NOW(),INTERVAL 60 DAY))
ON DUPLICATE KEY UPDATE `title`=VALUES(`title`),`status`=VALUES(`status`),`risk_level`=VALUES(`risk_level`);

INSERT INTO `contract_version` (`version_id`,`contract_id`,`version_no`,`content_hash`,`file_id`,`created_by`,`created_at`) VALUES
(10,2001,'v1.0','hash2001v1abcdef',4,'user',DATE_SUB(NOW(),INTERVAL 10 DAY)),
(11,2002,'v1.0','hash2002v1abcdef',1,'user',DATE_SUB(NOW(),INTERVAL 3 DAY)),
(12,2003,'v1.0','hash2003v1abcdef',4,'user',DATE_SUB(NOW(),INTERVAL 2 DAY)),
(13,2004,'v1.0','hash2004v1abcdef',5,'legal',DATE_SUB(NOW(),INTERVAL 7 DAY)),
(14,2005,'v1.0','hash2005v1abcdef',1,'legal',DATE_SUB(NOW(),INTERVAL 5 DAY)),
(15,2006,'v1.0','hash2006v1abcdef',5,'legal',DATE_SUB(NOW(),INTERVAL 12 DAY)),
(16,2007,'v1.0','hash2007v1abcdef',1,'legal',DATE_SUB(NOW(),INTERVAL 30 DAY)),
(17,2008,'v1.0','hash2008v1abcdef',4,'admin',DATE_SUB(NOW(),INTERVAL 20 DAY)),
(18,2009,'v1.0','hash2009v1abcdef',5,'admin',DATE_SUB(NOW(),INTERVAL 25 DAY)),
(19,2010,'v1.0','hash2010v1abcdef',1,'admin',DATE_SUB(NOW(),INTERVAL 60 DAY))
ON DUPLICATE KEY UPDATE `version_no`=VALUES(`version_no`);

SELECT '演示合同数据已就绪' AS message, (SELECT COUNT(*) FROM contract_main) AS total;
