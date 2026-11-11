-- R2-19 导入任务持久化（V202611111900）：新表 dynamic_io_import_task（存量 MySQL 库补；全新安装已含于 init.sql）
-- 背景：导入任务态原在内存 Map（IoDataService.tasks），重启即丢、多实例不可见、无痕迹。
--   本表持久化任务全生命周期（跨环境预检/自定义列映射/进度/失败明细），
--   worker_id + heartbeat_time 支持多实例断点续跑：心跳超过 stale 阈值视为执行实例死亡，可被重新认领。
-- 适用：pro=MySQL；执行前见 sql/README.md「迁移台账」节。
-- 回滚：DROP TABLE `dynamic_io_import_task`（只删任务痕迹，不影响业务数据）；
-- 台账自举：1.14 之前建的存量库还没有台账表，这里 IF NOT EXISTS 兜底（已存在则跳过）。

CREATE TABLE IF NOT EXISTS `platform_schema_history` (
  `id` varchar(36) NOT NULL COMMENT '主键',
  `version` varchar(32) NOT NULL COMMENT '版本号（V+时间戳，如 V202611111900）',
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

CREATE TABLE IF NOT EXISTS `dynamic_io_import_task` (
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '主键=taskId',
  `tenant_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'system-default' COMMENT '租户号',
  `revision` int NOT NULL DEFAULT '1' COMMENT '乐观锁',
  `created_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '发起人',
  `created_time` datetime NOT NULL COMMENT '创建时间',
  `updated_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted_flag` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'N' COMMENT '是否删除',
  `deleted_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '删除人',
  `deleted_time` datetime DEFAULT NULL COMMENT '删除时间',
  `department_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '所属部门',
  `organization_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '所属组织',
  `remark` varchar(900) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '备注（任务级异常信息）',
  `form_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '关联 dynamic_form.id',
  `table_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '动态物理表名',
  `database_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '业务数据源名',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'PENDING_MAPPING' COMMENT 'PENDING_MAPPING待映射确认/RUNNING执行中/FINISHED完成/FAILED任务级失败',
  `total` int NOT NULL DEFAULT '0' COMMENT '总行数',
  `processed` int NOT NULL DEFAULT '0' COMMENT '已处理行数（含失败/跳过，续跑断点）',
  `success` int NOT NULL DEFAULT '0' COMMENT '成功行数',
  `skipped` int NOT NULL DEFAULT '0' COMMENT '跳过行数（重复键 SKIP 等）',
  `failed` int NOT NULL DEFAULT '0' COMMENT '失败行数',
  `worker_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '执行实例标识 ip+pid+启动时间（CAS 认领防多实例重复执行）',
  `heartbeat_time` datetime DEFAULT NULL COMMENT '执行心跳；超过 dynamic.io.import.stale-seconds 未刷新可被重新认领续跑',
  `headers_json` longtext COMMENT 'Excel 表头行原始字符串数组 JSON',
  `mapping_json` longtext COMMENT '自定义列映射 {表头:字段编码}，空串=忽略该列',
  `precheck_json` longtext COMMENT '跨环境列结构预检报告（matched/unmatched/missingRequired/warnings）',
  `rows_json` longtext COMMENT '解析后的原始行（以表头为键的字符串值；类型转换在执行期做，保证续跑与首跑一致）',
  `errors_json` longtext COMMENT '失败行明细数组（封顶 5000 条）',
  `config_json` longtext COMMENT '发起时 io 配置快照（dup/strict/runBackscript/importFlowMode 等）',
  `finished_time` datetime DEFAULT NULL COMMENT '结束时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_io_task_form` (`form_id`, `created_time`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='动态表数据导入任务持久化(R2-19：重启可续/多实例可见/自定义列映射/跨环境预检)';

-- 登记台账（checksum 由执行人按 README 口径核对后填写或置空）
INSERT INTO `platform_schema_history`
    (`id`, `version`, `script_name`, `dialect`, `checksum`, `applied_by`, `applied_at`)
VALUES (REPLACE(UUID(), '-', ''),
        'V202611111900', 'V202611111900__io_import_task.mysql.sql', 'mysql', NULL, 'manual', NOW());
