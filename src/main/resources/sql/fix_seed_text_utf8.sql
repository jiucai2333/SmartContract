-- Repair demo seed text that was imported with the wrong client encoding.
-- Run with:
-- mysql -u root -p --default-character-set=utf8mb4 < src/main/resources/sql/fix_seed_text_utf8.sql

SET NAMES utf8mb4;
CREATE DATABASE IF NOT EXISTS smart_contract_db CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
USE smart_contract_db;

UPDATE dept_info SET dept_name = '总部' WHERE dept_id = 1;
UPDATE dept_info SET dept_name = '法务部' WHERE dept_id = 2;
UPDATE dept_info SET dept_name = '财务部' WHERE dept_id = 3;
UPDATE dept_info SET dept_name = '业务部' WHERE dept_id = 4;

UPDATE role_info SET role_name = '系统管理员' WHERE role_id = 1;
UPDATE role_info SET role_name = '法务专员' WHERE role_id = 2;
UPDATE role_info SET role_name = '财务专员' WHERE role_id = 3;
UPDATE role_info SET role_name = '普通员工' WHERE role_id = 4;
UPDATE role_info SET role_name = '部门主管' WHERE role_id = 5;
UPDATE role_info SET role_name = '企业高管' WHERE role_id = 6;

UPDATE file_info SET file_name = '采购合同模板.docx', updated_at = NOW() WHERE file_id = 1;
UPDATE file_info SET file_name = '销售合同模板.docx', updated_at = NOW() WHERE file_id = 2;
UPDATE file_info SET file_name = '技术服务合同模板.docx', updated_at = NOW() WHERE file_id = 3;

UPDATE contract_template
SET template_name = '采购合同模板',
    description = '适用于设备、物资采购场景，包含验收、付款、违约责任条款。',
    updated_at = NOW()
WHERE template_id = 1;

UPDATE contract_template
SET template_name = '销售合同模板',
    description = '适用于产品销售和交付场景。',
    updated_at = NOW()
WHERE template_id = 2;

UPDATE contract_template
SET template_name = '技术服务合同模板',
    description = '适用于技术开发、运维服务、知识产权合作场景。',
    updated_at = NOW()
WHERE template_id = 3;

UPDATE contract_main
SET title = '设备采购合同',
    counterparty = '深圳星河制造有限公司'
WHERE contract_id = 1001;

UPDATE contract_main
SET title = '企业技术服务合同',
    counterparty = '杭州云策科技有限公司'
WHERE contract_id = 1002;

UPDATE contract_main
SET title = '软件系统销售合同',
    counterparty = '成都数联信息有限公司'
WHERE contract_id = 1003;

UPDATE contract_main
SET title = '知识产权合作协议',
    counterparty = '北京衡信知识产权代理有限公司'
WHERE contract_id = 1004;

UPDATE risk_item
SET clause_ref = '付款条款',
    suggestion = '付款节点未绑定验收材料，建议增加发票、验收单和付款审批条件。'
WHERE risk_id = 1;

UPDATE risk_item
SET clause_ref = '权属条款',
    suggestion = '知识产权归属与衍生成果授权边界不清，提交审批前必须法务复核。'
WHERE risk_id = 2;

UPDATE risk_item
SET clause_ref = '保密条款',
    suggestion = '保密期限与数据返还义务描述完整，建议保留当前表述。'
WHERE risk_id = 3;

UPDATE fulfillment_plan SET milestone_name = '设备到货验收' WHERE plan_id = 1;
UPDATE fulfillment_plan SET milestone_name = '尾款回款确认' WHERE plan_id = 2;
UPDATE fulfillment_plan SET milestone_name = '服务启动会' WHERE plan_id = 3;

UPDATE approval_instance SET current_node = '法务审核' WHERE instance_id = 1;
