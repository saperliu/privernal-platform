-- R2-17 评论与附件（V202611121000）：新表 flow_task_comment（存量 MySQL 库补；全新安装已含于 init.sql）
-- 背景：流程实例缺少过程沟通沉淀通道，审批意见（flow_his_task.message）只随流转产生；
--   本表按实例维度存评论，附件复用 /back/file/upload（module=flow-comment），
--   文件元信息（fileId/name/url）JSON 数组存 attachments。
-- 适用：pro=MySQL；执行前见 sql/README.md「迁移台账」节。
-- 回滚：DROP TABLE `flow_task_comment`（评论内容不可恢复，执行回滚前请自行导出）。

CREATE TABLE IF NOT EXISTS `flow_task_comment` (
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
  `instance_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '流程实例id（flow_instance.id）',
  `task_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '流程任务id（可空：实例级评论不带任务）',
  `user_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '评论人用户id',
  `user_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '评论人姓名',
  `content` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '评论内容',
  `attachments` text CHARACTER SET utf8mb4 COLLATE utf8mb4_bin COMMENT '附件 JSON 数组（[{fileId,name,url}]）',
  `remark` varchar(900) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '备注（BaseEntity 通用列）',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_ftc_instance` (`instance_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='流程实例评论（R2-17 评论与附件）';

-- 登记台账（checksum 由执行人按 README 口径核对后填写或置空）
INSERT INTO `platform_schema_history`
    (`id`, `version`, `script_name`, `dialect`, `checksum`, `applied_by`, `applied_at`)
VALUES (REPLACE(UUID(), '-', ''),
        'V202611121000', 'V202611121000__flow_task_comment.mysql.sql', 'mysql', NULL, 'manual', NOW());
