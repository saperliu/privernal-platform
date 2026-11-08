-- 历史数据回填：flow_his_task.flow_name 空值从所属实例补齐（运行时已兜底 nodeName 显示）
-- 回滚：无需（仅补空值，不改非空行）

UPDATE flow_his_task
SET flow_name = (SELECT i.flow_name FROM flow_instance i WHERE i.id = flow_his_task.instance_id)
WHERE flow_name IS NULL
  AND EXISTS (SELECT 1 FROM flow_instance i WHERE i.id = flow_his_task.instance_id AND i.flow_name IS NOT NULL);

INSERT INTO platform_schema_history
    (id, version, script_name, dialect, applied_by, applied_at)
VALUES (lower(hex(randomblob(16))), 'V202611081910',
        'V202611081910__his_task_flow_name_backfill.sqlite.sql', 'sqlite', 'manual',
        datetime('now', 'localtime'));
