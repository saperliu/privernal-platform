-- V202611081900 F4 口令摘要改造：存量 SQLite 库补 password_v2 列（全新安装已含于 init-sqlite.sql）
-- 适用：dev/local=SQLite；执行前见 sql/README.md「迁移台账」节。执行方式：
--   sqlite3 data/privernal.db < sql/migrations/V202611081900__f4_password_digest.sqlite.sql
-- 回滚：SQLite 3.35+ 支持 ALTER TABLE system_user_base DROP COLUMN password_v2;
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

ALTER TABLE system_user_base ADD COLUMN password_v2 TEXT;

INSERT INTO platform_schema_history
    (id, version, script_name, dialect, checksum, applied_by, applied_at)
VALUES (lower(hex(randomblob(16))),
        'V202611081900', 'V202611081900__f4_password_digest.sqlite.sql', 'sqlite', NULL, 'manual',
        datetime('now', 'localtime'));
