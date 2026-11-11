-- R2-17 流程代理（V202611121100）：新表 flow_user_delegate（存量 SQLite 库补；全新安装已含于 init-sqlite.sql）
-- 适用：dev/local=SQLite；执行前见 sql/README.md「迁移台账」节。执行方式：
--   sqlite3 data/privernal.db < sql/migrations/V202611121100__flow_user_delegate.sqlite.sql
-- 回滚：DROP TABLE flow_user_delegate（仅删规则，在途实例不受影响）。

CREATE TABLE IF NOT EXISTS flow_user_delegate (
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
  user_id TEXT NOT NULL,
  agent_id TEXT NOT NULL,
  start_time TEXT,
  end_time TEXT,
  status TEXT NOT NULL DEFAULT '1',
  remark TEXT,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_fud_user ON flow_user_delegate (user_id);
CREATE INDEX IF NOT EXISTS idx_fud_agent ON flow_user_delegate (agent_id);

-- 登记台账
INSERT INTO platform_schema_history
    (id, version, script_name, dialect, checksum, applied_by, applied_at)
VALUES (lower(hex(randomblob(16))),
        'V202611121100', 'V202611121100__flow_user_delegate.sqlite.sql', 'sqlite', NULL, 'manual',
        datetime('now', 'localtime'));
