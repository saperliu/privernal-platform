-- R2-17 流程代理（V202611121100）：新表 flow_user_delegate（存量 MySQL 库补；全新安装已含于 init.sql）
-- 背景：代理人规则——user_id 的待办任务生成时（引擎办理人解析回调）把 agent_id 一并加入
--   办理人；有效期 [start_time, end_time]（空边界=长期），status 控制启停；
--   待办可见性：代理人按 user:{被代理人} 标识扩展匹配（FlowUserResolveServiceImpl）。
-- 适用：pro=MySQL；执行前见 sql/README.md「迁移台账」节。
-- 回滚：DROP TABLE `flow_user_delegate`（仅删规则，在途实例不受影响）。

CREATE TABLE IF NOT EXISTS `flow_user_delegate` (
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '主键',
  `tenant_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'system-default' COMMENT '租户号',
  `revision` int NOT NULL DEFAULT '1' COMMENT '乐观锁',
  `created_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '创建人',
  `created_time` datetime DEFAULT NULL COMMENT '创建时间',
  `updated_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted_flag` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'N' COMMENT '是否删除',
  `deleted_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '删除人',
  `deleted_time` datetime DEFAULT NULL COMMENT '删除时间',
  `department_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '所属部门',
  `organization_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '所属组织（保存时显式带入）',
  `user_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '被代理人用户id（待办原办理人）',
  `agent_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '代理人用户id（一并加入办理人）',
  `start_time` datetime DEFAULT NULL COMMENT '代理开始时间（空=立即生效）',
  `end_time` datetime DEFAULT NULL COMMENT '代理结束时间（空=长期有效）',
  `status` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '1' COMMENT '状态（1启用 0停用）',
  `remark` varchar(900) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '说明',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_fud_user` (`user_id`) USING BTREE,
  KEY `idx_fud_agent` (`agent_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='流程代理规则（R2-17 代理与离职移交）';

-- 登记台账（checksum 由执行人按 README 口径核对后填写或置空）
INSERT INTO `platform_schema_history`
    (`id`, `version`, `script_name`, `dialect`, `checksum`, `applied_by`, `applied_at`)
VALUES (REPLACE(UUID(), '-', ''),
        'V202611121100', 'V202611121100__flow_user_delegate.mysql.sql', 'mysql', NULL, 'manual', NOW());
