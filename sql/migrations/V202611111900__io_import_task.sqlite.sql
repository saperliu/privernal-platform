-- R2-19 导入任务持久化（V202611111900）：新表 dynamic_io_import_task（存量 SQLite 库补；全新安装已含于 init-sqlite.sql）
-- 背景：导入任务态原在内存 Map（IoDataService.tasks），重启即丢、多实例不可见、无痕迹。
--   本表持久化任务全生命周期（跨环境预检/自定义列映射/进度/失败明细），
--   worker_id + heartbeat_time 支持多实例断点续跑：心跳超过 stale 阈值视为执行实例死亡，可被重新认领。
-- 适用：dev/local=SQLite；执行前见 sql/README.md「迁移台账」节。执行方式：
--   sqlite3 data/privernal.db < sql/migrations/V202611111900__io_import_task.sqlite.sql
-- 回滚：DROP TABLE dynamic_io_import_task（只删任务痕迹，不影响业务数据）；
-- 台账自举：1.14 之前建的存量库还没有台账表，这里 IF NOT EXISTS 兜底（已存在则跳过）。

CREATE TABLE IF NOT EXISTS platform_schema_history (
  id TEXT NOT NULL,
  version TEXT NOT NULL,
  script_name TEXT NOT NULL,
  dialect TEXT NOT NULL,
  checksum TEXT,
  applied_by TEXT NOT NULL DEFAULT 'manual',
  applied_at TEXT NOT NULL,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_schema_script ON platform_schema_history (script_name, dialect);

INSERT OR IGNORE INTO platform_schema_history (id, version, script_name, dialect, applied_by, applied_at)
VALUES (lower(hex(randomblob(16))), 'baseline', 'init-sqlite.sql 全量基线（台账自举补记）', 'sqlite', 'init', datetime('now', 'localtime'));

CREATE TABLE IF NOT EXISTS dynamic_io_import_task (
  id TEXT NOT NULL,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  department_id TEXT NOT NULL,
  organization_id TEXT NOT NULL,
  remark TEXT,
  form_id TEXT NOT NULL,
  table_code TEXT,
  database_name TEXT,
  status TEXT NOT NULL DEFAULT 'PENDING_MAPPING',
  total INTEGER NOT NULL DEFAULT 0,
  processed INTEGER NOT NULL DEFAULT 0,
  success INTEGER NOT NULL DEFAULT 0,
  skipped INTEGER NOT NULL DEFAULT 0,
  failed INTEGER NOT NULL DEFAULT 0,
  worker_id TEXT,
  heartbeat_time TEXT,
  headers_json TEXT,
  mapping_json TEXT,
  precheck_json TEXT,
  rows_json TEXT,
  errors_json TEXT,
  config_json TEXT,
  finished_time TEXT,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_io_task_form ON dynamic_io_import_task (form_id, created_time);

-- 登记台账（checksum 由执行人按 README 口径核对后填写或置空）
INSERT INTO platform_schema_history
    (id, version, script_name, dialect, checksum, applied_by, applied_at)
VALUES (lower(hex(randomblob(16))),
        'V202611111900', 'V202611111900__io_import_task.sqlite.sql', 'sqlite', NULL, 'manual',
        datetime('now', 'localtime'));
