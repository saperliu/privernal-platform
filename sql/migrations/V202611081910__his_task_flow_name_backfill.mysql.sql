-- 历史数据回填：flow_his_task.flow_name 空值从所属实例补齐（运行时已兜底 nodeName 显示）
-- 回滚：无需（仅补空值，不改非空行）

UPDATE flow_his_task h
JOIN flow_instance i ON i.id = h.instance_id
SET h.flow_name = i.flow_name
WHERE h.flow_name IS NULL AND i.flow_name IS NOT NULL;

INSERT INTO platform_schema_history
    (id, version, script_name, dialect, applied_by, applied_at)
VALUES (REPLACE(UUID(), '-', ''), 'V202611081910',
        'V202611081910__his_task_flow_name_backfill.mysql.sql', 'mysql', 'manual', NOW());
