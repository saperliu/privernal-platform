-- R2-18 子流程（V202611112000）：新表 flow_subflow_rel（存量 MySQL 库补；全新安装已含于 init.sql）
-- 背景：warm-flow 1.8.9 引擎无原生子流程/Call Activity，平台侧桥接：
--   父流程流转到 ext.nodeKind=subflow 节点 → 自动发起 ext.subflowCode 指定的已发布流程实例；
--   子流程办结 → 自动回推父任务流转；子流程终止/撤销 → 记状态并通知父流程发起人。
-- 本表记录父子实例映射与状态（uk_parent_task 防同一父任务重复发起；先插行后发起防并发双发）。
-- 适用：pro=MySQL；执行前见 sql/README.md「迁移台账」节。
-- 回滚：DROP TABLE `flow_subflow_rel`（只删父子映射痕迹；在途子流程实例本身不受影响）。

CREATE TABLE IF NOT EXISTS `flow_subflow_rel` (
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '主键',
  `tenant_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'system-default' COMMENT '租户号',
  `revision` int NOT NULL DEFAULT '1' COMMENT '乐观锁',
  `created_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '创建人',
  `created_time` datetime NOT NULL COMMENT '创建时间',
  `updated_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted_flag` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'N' COMMENT '是否删除',
  `deleted_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '删除人',
  `deleted_time` datetime DEFAULT NULL COMMENT '删除时间',
  `department_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '所属部门（技术关联表可空）',
  `organization_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '所属组织（技术关联表可空）',
  `remark` varchar(900) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '备注（未发起原因等）',
  `parent_instance_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '父流程实例id（flow_instance.id）',
  `parent_task_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '父流程子流程节点任务id（flow_task.id）',
  `child_instance_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '子流程实例id（发起成功后回填）',
  `subflow_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '子流程编码（节点 ext.subflowCode 快照）',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'LAUNCHED' COMMENT 'LAUNCHED已发起等待/DONE子流程办结已回推父流程/CHILD_TERMINATED子流程已终止/CHILD_REVOKED子流程已撤销',
  `finished_time` datetime DEFAULT NULL COMMENT '子流程结束时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_parent_task` (`parent_task_id`) USING BTREE,
  KEY `idx_fsr_child` (`child_instance_id`) USING BTREE,
  KEY `idx_fsr_parent` (`parent_instance_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='流程父子实例关系（R2-18 子流程桥接）';

-- 登记台账（checksum 由执行人按 README 口径核对后填写或置空）
INSERT INTO `platform_schema_history`
    (`id`, `version`, `script_name`, `dialect`, `checksum`, `applied_by`, `applied_at`)
VALUES (REPLACE(UUID(), '-', ''),
        'V202611112000', 'V202611112000__flow_subflow_rel.mysql.sql', 'mysql', NULL, 'manual', NOW());
