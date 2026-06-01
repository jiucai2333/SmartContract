-- ============================================================
-- 智能合同管理系统 - 最小化建库建表 + 初始化数据（UTF-8）
-- 用法：mysql -u root -p < smartcontract.sql
-- 说明：
--   1. 仅包含用户/角色/部门核心表
--   2. 包含六角色（USER/DEPT_LEADER/LEGAL/FINANCE/EXECUTIVE/ADMIN）
--   3. 密码为 BCrypt 哈希（与后端 JWT 登录一致）
--   4. 演示账号：admin / Admin@123
--   5. 种子数据使用 ON DUPLICATE KEY UPDATE，可重复执行
-- ============================================================

CREATE DATABASE IF NOT EXISTS `smart_contract_db`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_bin;
USE `smart_contract_db`;

CREATE TABLE IF NOT EXISTS `dept_info` (
  `dept_id` BIGINT UNSIGNED AUTO_INCREMENT COMMENT '部门ID',
  `parent_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '父部门ID',
  `dept_name` VARCHAR(100) NOT NULL COMMENT '部门名称',
  `leader_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '部门负责人用户ID',
  `level` INT NOT NULL DEFAULT 1 COMMENT '部门层级',
  `created_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` VARCHAR(64) DEFAULT NULL COMMENT '更新人',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '删除标记',
  `version` INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '版本号',
  PRIMARY KEY (`dept_id`),
  KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='部门组织架构';

CREATE TABLE IF NOT EXISTS `user_info` (
  `user_id` BIGINT UNSIGNED AUTO_INCREMENT COMMENT '用户ID',
  `dept_id` BIGINT UNSIGNED NOT NULL COMMENT '所属部门ID',
  `role_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '角色ID（每个用户唯一角色）',
  `username` VARCHAR(100) NOT NULL COMMENT '登录账号',
  `password` VARCHAR(100) NOT NULL COMMENT 'BCrypt密码哈希',
  `mobile` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
  `email` VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '用户状态',
  `created_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` VARCHAR(64) DEFAULT NULL COMMENT '更新人',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '删除标记',
  `version` INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '版本号',
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_dept_id` (`dept_id`),
  KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='用户基础信息';

CREATE TABLE IF NOT EXISTS `role_info` (
  `role_id` BIGINT UNSIGNED AUTO_INCREMENT COMMENT '角色ID',
  `role_code` VARCHAR(50) NOT NULL COMMENT '角色编码',
  `role_name` VARCHAR(100) NOT NULL COMMENT '角色名称',
  `data_scope` VARCHAR(50) NOT NULL DEFAULT 'SELF' COMMENT '数据权限范围',
  `created_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` VARCHAR(64) DEFAULT NULL COMMENT '更新人',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '删除标记',
  `version` INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '版本号',
  PRIMARY KEY (`role_id`),
  UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='角色定义';

CREATE TABLE IF NOT EXISTS `permission_info` (
  `perm_id` BIGINT UNSIGNED AUTO_INCREMENT COMMENT '权限ID',
  `perm_code` VARCHAR(100) NOT NULL COMMENT '权限标识',
  `resource_type` VARCHAR(50) NOT NULL COMMENT '资源类型',
  `path` VARCHAR(255) DEFAULT NULL COMMENT '菜单路由或接口Path',
  `created_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` VARCHAR(64) DEFAULT NULL COMMENT '更新人',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '删除标记',
  `version` INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '版本号',
  PRIMARY KEY (`perm_id`),
  UNIQUE KEY `uk_perm_code` (`perm_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='菜单与接口权限';

CREATE TABLE IF NOT EXISTS `user_role_rel` (
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `role_id` BIGINT UNSIGNED NOT NULL COMMENT '角色ID',
  PRIMARY KEY (`user_id`, `role_id`),
  KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='用户角色关系';

CREATE TABLE IF NOT EXISTS `role_permission_rel` (
  `role_id` BIGINT UNSIGNED NOT NULL COMMENT '角色ID',
  `perm_id` BIGINT UNSIGNED NOT NULL COMMENT '权限ID',
  PRIMARY KEY (`role_id`, `perm_id`),
  KEY `idx_perm_id` (`perm_id`),
  CONSTRAINT `fk_role_perm_role` FOREIGN KEY (`role_id`) REFERENCES `role_info` (`role_id`),
  CONSTRAINT `fk_role_perm_perm` FOREIGN KEY (`perm_id`) REFERENCES `permission_info` (`perm_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='角色权限关系';

-- ==========================================
-- 初始化基础数据（部门 / 角色 / 用户 / 权限）
-- 演示账号：admin / Admin@123
-- 其他演示账号密码均为 Demo@123
-- ==========================================

INSERT INTO `dept_info` (`dept_id`, `parent_id`, `dept_name`, `leader_id`, `level`, `created_by`) VALUES
(1, 0, 'XX科技有限公司', NULL, 1, 'seed'),
(2, 1, '法务合规部', 2, 2, 'seed'),
(3, 1, '财务管理部', 1, 2, 'seed'),
(4, 1, '业务一部', 3, 2, 'seed')
ON DUPLICATE KEY UPDATE
  `dept_name` = VALUES(`dept_name`),
  `parent_id` = VALUES(`parent_id`),
  `level` = VALUES(`level`);

UPDATE `dept_info` SET `leader_id` = 1 WHERE `dept_id` = 1;
UPDATE `dept_info` SET `leader_id` = 2 WHERE `dept_id` = 2;
UPDATE `dept_info` SET `leader_id` = 1 WHERE `dept_id` = 3;
UPDATE `dept_info` SET `leader_id` = 3 WHERE `dept_id` = 4;

INSERT INTO `role_info` (`role_id`, `role_code`, `role_name`, `data_scope`, `created_by`) VALUES
(1, 'ADMIN',       '系统管理员', 'ALL',  'seed'),
(2, 'LEGAL',       '法务专员',   'ALL',  'seed'),
(3, 'FINANCE',     '财务专员',   'ALL',  'seed'),
(4, 'USER',        '普通员工',   'SELF', 'seed'),
(5, 'DEPT_LEADER', '部门主管',   'DEPT', 'seed'),
(6, 'EXECUTIVE',   '企业高管',   'ALL',  'seed')
ON DUPLICATE KEY UPDATE
  `role_name` = VALUES(`role_name`),
  `data_scope` = VALUES(`data_scope`);

INSERT INTO `user_info` (`user_id`, `dept_id`, `role_id`, `username`, `password`, `mobile`, `email`, `status`, `created_by`) VALUES
(1, 1, 1, 'admin',       '$2b$10$iA0EswHWkeiVPlKTFtkrj.ymTveFlNNW/bQgIp6zlFiuTG3NxRxr6', '13800000001', 'admin@example.com',       1, 'seed'),
(2, 2, 2, 'legal',       '$2b$10$AkONnpahKND41VtPU/2kaO797FqxoxLKN7XaqWAQ2xo3Ld3qa6IaG', '13800000002', 'legal@example.com',       1, 'seed'),
(3, 4, 4, 'user',        '$2b$10$5kyzzboHhRnJPOKJhyLy6eDzl88/oUkIoDBt2SXsJ5fcTOxxpxrXm', '13800000003', 'user@example.com',        1, 'seed'),
(4, 4, 5, 'dept_leader', '$2b$10$IY4wZpFrkpJY9RNeDpj/vuDtP3p/NoE4fZ4DZOAhAn3pCQAAOLKwu', '13800000004', 'dept_leader@example.com', 1, 'seed'),
(5, 3, 3, 'finance',     '$2b$10$IY4wZpFrkpJY9RNeDpj/vuDtP3p/NoE4fZ4DZOAhAn3pCQAAOLKwu', '13800000005', 'finance@example.com',     1, 'seed'),
(6, 1, 6, 'executive',   '$2b$10$IY4wZpFrkpJY9RNeDpj/vuDtP3p/NoE4fZ4DZOAhAn3pCQAAOLKwu', '13800000006', 'executive@example.com',   1, 'seed')
ON DUPLICATE KEY UPDATE
  `password` = VALUES(`password`),
  `dept_id` = VALUES(`dept_id`),
  `role_id` = VALUES(`role_id`),
  `status` = VALUES(`status`);

INSERT INTO `user_role_rel` (`user_id`, `role_id`) VALUES
(1, 1),
(2, 2),
(3, 4),
(4, 5),
(5, 3),
(6, 6)
ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);

INSERT INTO `permission_info` (`perm_id`, `perm_code`, `resource_type`, `path`, `created_by`) VALUES
(1,  'menu:dashboard',   'MENU', '/dashboard.html',   'seed'),
(2,  'menu:users',       'MENU', '/users.html',       'seed'),
(3,  'api:admin:users',  'API',  '/api/admin/users',  'seed'),
(4,  'api:admin:roles',  'API',  '/api/admin/roles',  'seed')
ON DUPLICATE KEY UPDATE
  `resource_type` = VALUES(`resource_type`),
  `path` = VALUES(`path`);

-- ==========================================
-- 角色权限映射（RBAC配置）
-- ==========================================
INSERT INTO `role_permission_rel` (`role_id`, `perm_id`) VALUES
-- ADMIN(1): 所有权限
(1, 1), (1, 2), (1, 3), (1, 4),
-- 其他角色：仅菜单和工作台
(2, 1), (3, 1), (4, 1), (5, 1), (6, 1)
ON DUPLICATE KEY UPDATE `perm_id` = VALUES(`perm_id`);

ALTER TABLE `dept_info`       AUTO_INCREMENT = 10;
ALTER TABLE `user_info`       AUTO_INCREMENT = 10;
ALTER TABLE `role_info`       AUTO_INCREMENT = 10;
ALTER TABLE `permission_info` AUTO_INCREMENT = 10;

SELECT 'smart_contract_db 初始化完成（精简版 - 6角色体系）' AS message,
       (SELECT COUNT(*) FROM user_info) AS users,
       (SELECT COUNT(*) FROM role_info) AS roles;
