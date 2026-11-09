-- 存量库 flow 表 ext 列扩容：varchar(255) → varchar(2000)（节点/实例配置 JSON 增长，
-- 超时/消息/催办等配置进 ext 后 255 容量不足；全新安装已含于 init.sql）
-- 回滚：各行按原 255 回改即可（仅当存量数据未超长时可行）

ALTER TABLE `flow_form` MODIFY `ext` varchar(2000) DEFAULT NULL COMMENT '扩展字段（节点/实例配置 JSON）';
ALTER TABLE `flow_his_task` MODIFY `ext` varchar(2000) DEFAULT NULL COMMENT '扩展字段（节点/实例配置 JSON）';
ALTER TABLE `flow_instance` MODIFY `ext` varchar(2000) DEFAULT NULL COMMENT '扩展字段（节点/实例配置 JSON）';
ALTER TABLE `flow_node` MODIFY `ext` varchar(2000) DEFAULT NULL COMMENT '扩展属性（节点配置 JSON：超时/消息等）';

CREATE TABLE IF NOT EXISTS platform_schema_history (
  id varchar(36) NOT NULL,
  version varchar(32) NOT NULL,
  script_name varchar(200) NOT NULL,
  dialect varchar(16) NOT NULL,
  checksum varchar(64) DEFAULT NULL,
  applied_by varchar(64) NOT NULL DEFAULT 'manual',
  applied_at datetime NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_schema_script (script_name, dialect)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据库结构变更台账';

INSERT IGNORE INTO platform_schema_history (id, version, script_name, dialect, applied_by, applied_at)
VALUES (REPLACE(UUID(), '-', ''), 'V202611081940',
        'V202611081940__flow_ext_widen.mysql.sql', 'mysql', 'manual', NOW());
