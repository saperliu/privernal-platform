-- V202611081900 F4 口令摘要改造：存量 MySQL 库补 password_v2 列（全新安装已含于 init.sql）
-- 适用：pro=MySQL；执行前见 sql/README.md「迁移台账」节。
-- 回滚：ALTER TABLE `system_user_base` DROP COLUMN `password_v2`;
-- 台账自举：1.14 之前建的存量库还没有台账表，这里 IF NOT EXISTS 兜底（已存在则跳过）。

CREATE TABLE IF NOT EXISTS `platform_schema_history` (
  `id` varchar(36) NOT NULL COMMENT '主键',
  `version` varchar(32) NOT NULL COMMENT '版本号（V+时间戳，如 V202611081900）',
  `script_name` varchar(200) NOT NULL COMMENT '迁移脚本文件名',
  `dialect` varchar(16) NOT NULL COMMENT 'mysql/sqlite',
  `checksum` varchar(64) DEFAULT NULL COMMENT '脚本 SHA-256（apply 前核对）',
  `applied_by` varchar(64) NOT NULL DEFAULT 'manual' COMMENT '执行人',
  `applied_at` datetime NOT NULL COMMENT '执行时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_schema_script` (`script_name`, `dialect`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据库结构变更台账';

INSERT IGNORE INTO `platform_schema_history` (`id`, `version`, `script_name`, `dialect`, `applied_by`, `applied_at`)
VALUES (REPLACE(UUID(), '-', ''), 'baseline', 'init.sql 全量基线（台账自举补记）', 'mysql', 'init', NOW());

ALTER TABLE `system_user_base`
    ADD COLUMN `password_v2` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL
        COMMENT 'F4 封存口令摘要（AES-GCM，挑战登录用；未迁移为空）' AFTER `password`;

-- 登记台账（checksum 由执行人按 README 口径核对后填写或置空）
INSERT INTO `platform_schema_history`
    (`id`, `version`, `script_name`, `dialect`, `checksum`, `applied_by`, `applied_at`)
VALUES (REPLACE(UUID(), '-', ''),
        'V202611081900', 'V202611081900__f4_password_digest.mysql.sql', 'mysql', NULL, 'manual', NOW());
