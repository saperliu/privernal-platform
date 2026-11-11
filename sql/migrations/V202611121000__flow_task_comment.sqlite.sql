-- R2-17 评论与附件（V202611121000）：新表 flow_task_comment（存量 SQLite 库补；全新安装已含于 init-sqlite.sql）
-- 适用：dev/local=SQLite；执行前见 sql/README.md「迁移台账」节。执行方式：
--   sqlite3 data/privernal.db < sql/migrations/V202611121000__flow_task_comment.sqlite.sql
-- 回滚：DROP TABLE flow_task_comment（评论内容不可恢复，执行回滚前请自行导出）。

CREATE TABLE IF NOT EXISTS flow_task_comment (
  id TEXT NOT NULL,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT,
  created_time TEXT,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  department_id TEXT,
  organization_id TEXT,
  instance_id TEXT NOT NULL,
  task_id TEXT,
  user_id TEXT,
  user_name TEXT,
  content TEXT NOT NULL,
  attachments TEXT,
  remark TEXT,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_ftc_instance ON flow_task_comment (instance_id);

-- 登记台账
INSERT INTO platform_schema_history
    (id, version, script_name, dialect, checksum, applied_by, applied_at)
VALUES (lower(hex(randomblob(16))),
        'V202611121000', 'V202611121000__flow_task_comment.sqlite.sql', 'sqlite', NULL, 'manual',
        datetime('now', 'localtime'));
