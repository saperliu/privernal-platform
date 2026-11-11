-- R2-18 子流程（V202611112000）：新表 flow_subflow_rel（存量 SQLite 库补；全新安装已含于 init-sqlite.sql）
-- 背景：warm-flow 1.8.9 引擎无原生子流程/Call Activity，平台侧桥接：
--   父流程流转到 ext.nodeKind=subflow 节点 → 自动发起 ext.subflowCode 指定的已发布流程实例；
--   子流程办结 → 自动回推父任务流转；子流程终止/撤销 → 记状态并通知父流程发起人。
-- 本表记录父子实例映射与状态（uk_parent_task 防同一父任务重复发起；先插行后发起防并发双发）。
-- 适用：dev/local=SQLite；执行前见 sql/README.md「迁移台账」节。执行方式：
--   sqlite3 data/privernal.db < sql/migrations/V202611112000__flow_subflow_rel.sqlite.sql
-- 回滚：DROP TABLE flow_subflow_rel（只删父子映射痕迹；在途子流程实例本身不受影响）；
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

CREATE TABLE IF NOT EXISTS flow_subflow_rel (
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
  department_id TEXT,
  organization_id TEXT,
  remark TEXT,
  parent_instance_id TEXT NOT NULL,
  parent_task_id TEXT NOT NULL,
  child_instance_id TEXT,
  subflow_code TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'LAUNCHED',
  finished_time TEXT,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_fsr_parent_task ON flow_subflow_rel (parent_task_id);
CREATE INDEX IF NOT EXISTS idx_fsr_child ON flow_subflow_rel (child_instance_id);
CREATE INDEX IF NOT EXISTS idx_fsr_parent ON flow_subflow_rel (parent_instance_id);

-- 登记台账（checksum 由执行人按 README 口径核对后填写或置空）
INSERT INTO platform_schema_history
    (id, version, script_name, dialect, checksum, applied_by, applied_at)
VALUES (lower(hex(randomblob(16))),
        'V202611112000', 'V202611112000__flow_subflow_rel.sqlite.sql', 'sqlite', NULL, 'manual',
        datetime('now', 'localtime'));
