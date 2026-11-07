SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- 本文件取自 Warm-Flow 1.8.9 官方 MySQL 建表脚本，许可证 Apache-2.0。
-- 上游项目：https://github.com/dromara/warm-flow
-- 许可证原文见仓库根 LICENSE；来源登记见 NOTICE.md 第三节。
-- ----------------------------

-- ----------------------------
-- warm-flow 1.8.9 工作流引擎表（MySQL）
-- 字段与 org.dromara.warm:warm-flow-mybatis-plus-core 1.8.9 实体一一对应
-- 注意：引擎表不含 organization_id，已加入 OrganizationHandlerImpl.IGNORE_TABLES；
--       组织隔离由 privernal-flow 的业务扩展表（flow_form_bind / flow_form_node_config）承载
-- ----------------------------

-- ----------------------------
-- 流程定义表
-- ----------------------------
DROP TABLE IF EXISTS `flow_definition`;
CREATE TABLE `flow_definition`
(
    `id`             bigint(20)   NOT NULL COMMENT '主键id',
    `create_time`    datetime     NULL DEFAULT NULL COMMENT '创建时间',
    `update_time`    datetime     NULL DEFAULT NULL COMMENT '更新时间',
    `create_by`      varchar(64)  NULL DEFAULT NULL COMMENT '创建人',
    `update_by`      varchar(64)  NULL DEFAULT NULL COMMENT '更新人',
    `tenant_id`      varchar(40)  NULL DEFAULT NULL COMMENT '租户id',
    `del_flag`       char(1)      NULL DEFAULT '0' COMMENT '删除标志',
    `flow_code`      varchar(40)  NULL DEFAULT NULL COMMENT '流程编码',
    `flow_name`      varchar(50)  NULL DEFAULT NULL COMMENT '流程名称',
    `model_value`    varchar(20)  NULL DEFAULT 'CLASSICS' COMMENT '流程模型（经典/仿钉钉）',
    `category`       varchar(40)  NULL DEFAULT NULL COMMENT '流程类别',
    `version`        varchar(20)  NULL DEFAULT '1' COMMENT '流程版本',
    `is_publish`     int(1)       NULL DEFAULT 0 COMMENT '是否发布（0未发布 1已发布 9已失效）',
    `form_custom`    char(1)      NULL DEFAULT 'N' COMMENT '审批表单是否自定义（Y是 N否）',
    `form_path`      varchar(100) NULL DEFAULT NULL COMMENT '审批表单路径',
    `activity_status` int(1)     NULL DEFAULT 1 COMMENT '流程激活状态（0挂起 1激活）',
    `listener_type`  varchar(30)  NULL DEFAULT NULL COMMENT '监听器类型',
    `listener_path`  varchar(255) NULL DEFAULT NULL COMMENT '监听器路径',
    `ext`            varchar(255) NULL DEFAULT NULL COMMENT '业务详情-扩展字段',
    PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '流程定义表';

-- ----------------------------
-- 流程节点表
-- ----------------------------
DROP TABLE IF EXISTS `flow_node`;
CREATE TABLE `flow_node`
(
    `id`              bigint(20)   NOT NULL COMMENT '主键id',
    `create_time`     datetime     NULL DEFAULT NULL COMMENT '创建时间',
    `update_time`     datetime     NULL DEFAULT NULL COMMENT '更新时间',
    `create_by`       varchar(64)  NULL DEFAULT NULL COMMENT '创建人',
    `update_by`       varchar(64)  NULL DEFAULT NULL COMMENT '更新人',
    `tenant_id`       varchar(40)  NULL DEFAULT NULL COMMENT '租户id',
    `del_flag`        char(1)      NULL DEFAULT '0' COMMENT '删除标志',
    `node_type`       int(1)       NULL DEFAULT NULL COMMENT '节点类型（0开始 1中间 2结束 3互斥网关 4并行网关）',
    `definition_id`   bigint(20)   NULL DEFAULT NULL COMMENT '流程定义id',
    `node_code`       varchar(40)  NULL DEFAULT NULL COMMENT '节点编码',
    `node_name`       varchar(50)  NULL DEFAULT NULL COMMENT '节点名称',
    `permission_flag` varchar(1000) NULL DEFAULT NULL COMMENT '权限标识（权限类型:权限标识，多个@@分隔）',
    `node_ratio`      varchar(10)  NULL DEFAULT NULL COMMENT '会签票签比例（0或签，1~99票签，100会签）',
    `coordinate`      varchar(100) NULL DEFAULT NULL COMMENT '坐标',
    `version`         varchar(20)  NULL DEFAULT '1' COMMENT '版本',
    `any_node_skip`   varchar(100) NULL DEFAULT NULL COMMENT '任意跳转的节点编码',
    `listener_type`   varchar(30)  NULL DEFAULT NULL COMMENT '监听器类型',
    `listener_path`   varchar(255) NULL DEFAULT NULL COMMENT '监听器路径',
    `form_custom`     char(1)      NULL DEFAULT 'N' COMMENT '审批表单是否自定义',
    `form_path`       varchar(100) NULL DEFAULT NULL COMMENT '审批表单路径',
    `ext`             varchar(255) NULL DEFAULT NULL COMMENT '扩展属性',
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_node_definition_id` (`definition_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '流程节点表';

-- ----------------------------
-- 节点跳转关联表
-- ----------------------------
DROP TABLE IF EXISTS `flow_skip`;
CREATE TABLE `flow_skip`
(
    `id`            bigint(20)   NOT NULL COMMENT '主键id',
    `create_time`   datetime     NULL DEFAULT NULL COMMENT '创建时间',
    `update_time`   datetime     NULL DEFAULT NULL COMMENT '更新时间',
    `create_by`     varchar(64)  NULL DEFAULT NULL COMMENT '创建人',
    `update_by`     varchar(64)  NULL DEFAULT NULL COMMENT '更新人',
    `tenant_id`     varchar(40)  NULL DEFAULT NULL COMMENT '租户id',
    `del_flag`      char(1)      NULL DEFAULT '0' COMMENT '删除标志',
    `definition_id` bigint(20)   NULL DEFAULT NULL COMMENT '流程定义id',
    `node_id`       bigint(20)   NULL DEFAULT NULL COMMENT '节点id',
    `now_node_code` varchar(40)  NULL DEFAULT NULL COMMENT '当前流程节点编码',
    `now_node_type` int(1)       NULL DEFAULT NULL COMMENT '当前节点类型',
    `next_node_code` varchar(40) NULL DEFAULT NULL COMMENT '下一个流程节点编码',
    `next_node_type` int(1)      NULL DEFAULT NULL COMMENT '下一个节点类型',
    `skip_name`     varchar(50)  NULL DEFAULT NULL COMMENT '跳转名称',
    `skip_type`     varchar(10)  NULL DEFAULT NULL COMMENT '跳转类型（PASS通过 REJECT退回）',
    `skip_condition` varchar(1000) NULL DEFAULT NULL COMMENT '跳转条件(wfexpr 表达式含字段名映射,见 upgrade_flow_skip_condition_len.sql)',
    `coordinate`    varchar(100) NULL DEFAULT NULL COMMENT '坐标',
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_skip_definition_id` (`definition_id`) USING BTREE,
    INDEX `idx_skip_now_node_code` (`now_node_code`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '节点跳转关联表';

-- ----------------------------
-- 流程实例表
-- ----------------------------
DROP TABLE IF EXISTS `flow_instance`;
CREATE TABLE `flow_instance`
(
    `id`             bigint(20)    NOT NULL COMMENT '主键id',
    `create_time`    datetime      NULL DEFAULT NULL COMMENT '创建时间',
    `update_time`    datetime      NULL DEFAULT NULL COMMENT '更新时间',
    `create_by`      varchar(64)   NULL DEFAULT NULL COMMENT '创建人（流程发起人）',
    `update_by`      varchar(64)   NULL DEFAULT NULL COMMENT '更新人',
    `tenant_id`      varchar(40)   NULL DEFAULT NULL COMMENT '租户id',
    `del_flag`       char(1)       NULL DEFAULT '0' COMMENT '删除标志',
    `definition_id`  bigint(20)    NULL DEFAULT NULL COMMENT '对应flow_definition表的id',
    `flow_name`      varchar(50)   NULL DEFAULT NULL COMMENT '流程名称',
    `business_id`    varchar(40)   NULL DEFAULT NULL COMMENT '业务id（业务数据主键）',
    `node_type`      int(1)        NULL DEFAULT NULL COMMENT '节点类型',
    `node_code`      varchar(40)   NULL DEFAULT NULL COMMENT '流程节点编码',
    `node_name`      varchar(50)   NULL DEFAULT NULL COMMENT '流程节点名称',
    `variable`       mediumtext    NULL COMMENT '任务变量',
    `flow_status`    varchar(10)   NULL DEFAULT NULL COMMENT '流程状态（0待提交 1审批中 2审批通过 3自动完成 4终止 5作废 6撤销 7取回 8已完成 9已退回 10失效 11拿回）',
    `activity_status` int(1)       NULL DEFAULT 1 COMMENT '流程激活状态（0挂起 1激活）',
    `form_custom`    char(1)       NULL DEFAULT 'N' COMMENT '审批表单是否自定义',
    `form_path`      varchar(100)  NULL DEFAULT NULL COMMENT '审批表单路径',
    `def_json`       mediumtext    NULL COMMENT '流程定义json',
    `ext`            varchar(255)  NULL DEFAULT NULL COMMENT '扩展字段',
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_instance_definition_id` (`definition_id`) USING BTREE,
    INDEX `idx_instance_business_id` (`business_id`) USING BTREE,
    INDEX `idx_instance_create_by` (`create_by`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '流程实例表';

-- ----------------------------
-- 待办任务表
-- ----------------------------
DROP TABLE IF EXISTS `flow_task`;
CREATE TABLE `flow_task`
(
    `id`             bigint(20)   NOT NULL COMMENT '主键id',
    `create_time`    datetime     NULL DEFAULT NULL COMMENT '创建时间',
    `update_time`    datetime     NULL DEFAULT NULL COMMENT '更新时间',
    `create_by`      varchar(64)  NULL DEFAULT NULL COMMENT '创建人',
    `update_by`      varchar(64)  NULL DEFAULT NULL COMMENT '更新人',
    `tenant_id`      varchar(40)  NULL DEFAULT NULL COMMENT '租户id',
    `del_flag`       char(1)      NULL DEFAULT '0' COMMENT '删除标志',
    `definition_id`  bigint(20)   NULL DEFAULT NULL COMMENT '对应flow_definition表的id',
    `instance_id`    bigint(20)   NULL DEFAULT NULL COMMENT '对应flow_instance表的id',
    `flow_name`      varchar(50)  NULL DEFAULT NULL COMMENT '流程名称',
    `business_id`    varchar(40)  NULL DEFAULT NULL COMMENT '业务id',
    `node_code`      varchar(40)  NULL DEFAULT NULL COMMENT '节点编码',
    `node_name`      varchar(50)  NULL DEFAULT NULL COMMENT '节点名称',
    `node_type`      int(1)       NULL DEFAULT NULL COMMENT '节点类型',
    `flow_status`    varchar(10)  NULL DEFAULT NULL COMMENT '流程状态',
    `form_custom`    char(1)      NULL DEFAULT 'N' COMMENT '审批表单是否自定义',
    `form_path`      varchar(100) NULL DEFAULT NULL COMMENT '审批表单路径',
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_task_instance_id` (`instance_id`) USING BTREE,
    INDEX `idx_task_definition_id` (`definition_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '待办任务表';

-- ----------------------------
-- 历史任务记录表
-- ----------------------------
DROP TABLE IF EXISTS `flow_his_task`;
CREATE TABLE `flow_his_task`
(
    `id`              bigint(20)   NOT NULL COMMENT '主键id',
    `create_time`     datetime     NULL DEFAULT NULL COMMENT '创建时间（任务开始时间）',
    `update_time`     datetime     NULL DEFAULT NULL COMMENT '更新时间（审批完成时间）',
    `tenant_id`       varchar(40)  NULL DEFAULT NULL COMMENT '租户id',
    `del_flag`        char(1)      NULL DEFAULT '0' COMMENT '删除标志',
    `definition_id`   bigint(20)   NULL DEFAULT NULL COMMENT '对应flow_definition表的id',
    `flow_name`       varchar(50)  NULL DEFAULT NULL COMMENT '流程名称',
    `instance_id`     bigint(20)   NULL DEFAULT NULL COMMENT '对应flow_instance表的id',
    `task_id`         bigint(20)   NULL DEFAULT NULL COMMENT '对应flow_task表的id',
    `cooperate_type`  int(1)       NULL DEFAULT 0 COMMENT '协作方式（1审批 2转办 3委派 4会签 5票签 6加签 7减签）',
    `business_id`     varchar(40)  NULL DEFAULT NULL COMMENT '业务id',
    `node_code`       varchar(40)  NULL DEFAULT NULL COMMENT '开始节点编码',
    `node_name`       varchar(50)  NULL DEFAULT NULL COMMENT '开始节点名称',
    `node_type`       int(1)       NULL DEFAULT NULL COMMENT '开始节点类型',
    `target_node_code` varchar(40) NULL DEFAULT NULL COMMENT '目标节点编码',
    `target_node_name` varchar(50) NULL DEFAULT NULL COMMENT '目标节点名称',
    `approver`        varchar(64)  NULL DEFAULT NULL COMMENT '审批者',
    `collaborator`    varchar(64)  NULL DEFAULT NULL COMMENT '协作人',
    `skip_type`       varchar(10)  NULL DEFAULT NULL COMMENT '跳转类型（PASS通过 REJECT退回 NONE无动作）',
    `flow_status`     varchar(10)  NULL DEFAULT NULL COMMENT '流程状态',
    `message`         varchar(500) NULL DEFAULT NULL COMMENT '审批意见',
    `variable`        mediumtext   NULL COMMENT '任务变量',
    `ext`             varchar(255) NULL DEFAULT NULL COMMENT '扩展字段',
    `form_custom`     char(1)      NULL DEFAULT 'N' COMMENT '审批表单是否自定义',
    `form_path`       varchar(100) NULL DEFAULT NULL COMMENT '审批表单路径',
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_his_instance_id` (`instance_id`) USING BTREE,
    INDEX `idx_his_task_id` (`task_id`) USING BTREE,
    INDEX `idx_his_approver` (`approver`) USING BTREE,
    INDEX `idx_his_business_id` (`business_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '历史任务记录表';

-- ----------------------------
-- 流程用户表（待办任务权限）
-- ----------------------------
DROP TABLE IF EXISTS `flow_user`;
CREATE TABLE `flow_user`
(
    `id`           bigint(20)  NOT NULL COMMENT '主键id',
    `create_time`  datetime    NULL DEFAULT NULL COMMENT '创建时间',
    `update_time`  datetime    NULL DEFAULT NULL COMMENT '更新时间',
    `create_by`    varchar(64) NULL DEFAULT NULL COMMENT '创建人',
    `update_by`    varchar(64) NULL DEFAULT NULL COMMENT '更新人',
    `tenant_id`    varchar(40) NULL DEFAULT NULL COMMENT '租户id',
    `del_flag`     char(1)     NULL DEFAULT '0' COMMENT '删除标志',
    `type`         char(1)     NULL DEFAULT NULL COMMENT '人员类型（1待办任务的审批人权限 2待办任务的转办人权限 3待办任务的委托人权限）',
    `processed_by` varchar(255) NULL DEFAULT NULL COMMENT '权限人（用户/角色/部门等权限标识值）',
    `associated`   bigint(20)  NULL DEFAULT NULL COMMENT '任务表id',
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_user_associated` (`associated`) USING BTREE,
    INDEX `idx_user_processed_by` (`processed_by`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '流程用户表';

-- ----------------------------
-- 通用表单表（warm-flow 内置，流程定义绑定表单路径用）
-- ----------------------------
DROP TABLE IF EXISTS `flow_form`;
CREATE TABLE `flow_form`
(
    `id`           bigint(20)    NOT NULL COMMENT '主键id',
    `create_time`  datetime      NULL DEFAULT NULL COMMENT '创建时间',
    `update_time`  datetime      NULL DEFAULT NULL COMMENT '更新时间',
    `create_by`    varchar(64)   NULL DEFAULT NULL COMMENT '创建人',
    `update_by`    varchar(64)   NULL DEFAULT NULL COMMENT '更新人',
    `tenant_id`    varchar(40)   NULL DEFAULT NULL COMMENT '租户id',
    `del_flag`     char(1)       NULL DEFAULT '0' COMMENT '删除标志',
    `form_code`    varchar(40)   NULL DEFAULT NULL COMMENT '表单编码',
    `form_name`    varchar(50)   NULL DEFAULT NULL COMMENT '表单名称',
    `version`      varchar(20)   NULL DEFAULT '1' COMMENT '表单版本',
    `is_publish`   int(1)        NULL DEFAULT 0 COMMENT '是否发布（0未发布 1已发布）',
    `form_type`    int(1)        NULL DEFAULT NULL COMMENT '表单类型',
    `form_path`    varchar(100)  NULL DEFAULT NULL COMMENT '表单路径',
    `form_content` mediumtext    NULL COMMENT '表单内容',
    `ext`          varchar(255)  NULL DEFAULT NULL COMMENT '扩展字段',
    PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '流程表单表';

SET FOREIGN_KEY_CHECKS = 1;
