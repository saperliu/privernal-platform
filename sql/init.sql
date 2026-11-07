-- ============================================================================
-- 春祈平台（privernal-platform）数据库初始化脚本
-- 版本基线：2026-11-09（全量自足：原 41 个 upgrade_* / system_* 增量与模块脚本已全部并入本文件）
--
-- 内容说明：
--   1. 创建数据库 dynamic_platform（utf8mb4），并建立全部 97 张平台表；
--      动态表单在运行期按需建表，本脚本不含任何业务数据表。
--   2. 仅保留平台运行所需的最小种子数据：
--      - 默认组织「系统企业」、默认部门及其闭包关系；
--      - 内置管理员 admin / admin123（密码存 bcrypt(SHA512(admin123))）；
--        ⚠️ 上线前必须登录改密，默认口令仅供首次进入使用；
--      - 内置角色：组织管理员（SuperRole）、平台管理员（SuperPlatformRole）；
--      - 内置应用：系统管理（SYSTEM）、平台管理（PLATFORM）；
--      - 平台功能菜单全集；
--      - 系统菜单/角色菜单授权（按种子菜单全集自动生成）；
--      - 基础字典（性别/系统模块/编码类型/编码配置类型）；
--      - 系统配置：SYSTEM_THEME（前端主题）、联动日志/外链审计在线保留天数；
--      - 脚本资产中心 5 个官方模板（overlap_check/conflict_count/workday_add/
--        biz_number_rule/time_range_available，状态 DRAFT，试运行后由管理员发布）。
--   3. 业务运行数据（流程实例、表单定义、报表、定时任务、日志等）全部为空，
--      由部署后在使用中产生。
--
-- 使用方法（任选其一）：
--   mysql -u root -p < init.sql
--   或：mysql -u root -p --default-character-set=utf8mb4 dynamic_platform < init.sql
--
-- ⚠️ 重复执行会 DROP 并重建全部表，切勿在生产库上直接执行；
--    如需修改库名，同步调整 CREATE DATABASE / USE 语句，并更新后端
--    application-*.yaml 中 spring.datasource.dynamic.datasource.master.url。
-- ============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE DATABASE IF NOT EXISTS `dynamic_platform`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_general_ci;
USE `dynamic_platform`;


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
DROP TABLE IF EXISTS `dynamic_dashboard`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dynamic_dashboard` (
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
  `department_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '所属部门',
  `organization_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '所属组织',
  `remark` varchar(900) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '备注',
  `dashboard_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '仪表盘编码（业务主键，菜单/权限用它）',
  `dashboard_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '仪表盘名称',
  `app_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '所属应用ID',
  `config` json DEFAULT NULL COMMENT '布局配置{gap}等，栅格明细在 item 表',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_dashboard_code` (`dashboard_code`) USING BTREE,
  KEY `idx_dashboard_app` (`app_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='报表仪表盘';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `dynamic_dashboard_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dynamic_dashboard_item` (
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
  `department_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '所属部门',
  `organization_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '所属组织',
  `remark` varchar(900) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '备注',
  `dashboard_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '仪表盘编码(dynamic_dashboard.dashboard_code)',
  `report_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '图表编码(dynamic_report.report_code)',
  `title` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '卡片标题（缺省用图表名）',
  `span` int DEFAULT '6' COMMENT '栅格跨度 1-12',
  `order_num` int DEFAULT '0' COMMENT '排序号',
  `config` json DEFAULT NULL COMMENT '卡片样式配置',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_ddi_dashboard` (`dashboard_code`) USING BTREE,
  KEY `idx_ddi_report` (`report_code`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='仪表盘卡片（独立立表：删图表可反查引用，同 form_relation 的教训）';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `dynamic_database`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dynamic_database` (
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
  `department_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '所属部门',
  `organization_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '所属组织',
  `remark` varchar(900) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '备注',
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '数据标题',
  `status` int DEFAULT NULL COMMENT '状态;业务对象的状态,0:草稿,1审批完成或已提交,2:流程运行中,3:被取消',
  `database_name` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '数据库名称',
  `database_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '数据库的类型',
  `driver` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '数据库使用的驱动',
  `password` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '数据库的密码',
  `url` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '使用的URL连接',
  `user_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '数据库的用户名',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='前端列表配置';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `dynamic_dataset`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dynamic_dataset` (
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
  `department_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '所属部门',
  `organization_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '所属组织',
  `remark` varchar(900) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '备注',
  `dataset_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '数据集编码（业务主键，跨环境可移植）',
  `dataset_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '数据集名称',
  `table_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '逻辑表编码（不带组织前缀，dynamic_form.table_code 同源）',
  `app_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '所属应用ID',
  `column_snapshot` json DEFAULT NULL COMMENT '列快照[{fieldCode,fieldName,dataType(number|date|text)}]，建数据集时从 dynamic_form_column 解析一次落库',
  `default_filter` json DEFAULT NULL COMMENT '默认过滤[{field,op,value}]（结构化条件，非 SQL 文本）',
  `source_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT 'dynamic_table' COMMENT '数据源类型;dynamic_table动态表,http外部接口,jdbc外部库,websocket推送,excel上传,static静态',
  `source_config` json DEFAULT NULL COMMENT '数据源配置，按 source_type 解释；外部凭据只存引用ID不落明文',
  `refresh_config` json DEFAULT NULL COMMENT '刷新与缓存策略{ttlSec,strategy,onError}',
  `computed_columns` json DEFAULT NULL COMMENT '派生列[{code,name,dataType,expr}]，表达式在服务端沙箱求值',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_dataset_code` (`dataset_code`) USING BTREE,
  KEY `idx_dataset_app` (`app_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='报表数据集';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `dynamic_doc_template`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dynamic_doc_template` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '主键',
  `tenant_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'system-default' COMMENT '租户号',
  `revision` int NOT NULL DEFAULT '1' COMMENT '乐观锁',
  `created_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '创建人',
  `created_time` datetime NOT NULL COMMENT '创建时间',
  `updated_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted_flag` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'N' COMMENT '是否删除',
  `deleted_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '删除人',
  `deleted_time` datetime NOT NULL DEFAULT '1970-01-01 00:00:00' COMMENT '删除时间(NOT NULL 才能让 (template_code,deleted_time) 唯一键对未删行生效)',
  `department_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '所属部门',
  `organization_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '所属组织',
  `remark` varchar(900) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '备注',
  `template_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '稳定编码;应用包判重键(不用 id/form_id)',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '模板名称',
  `app_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '归属应用,随应用包走',
  `form_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '关联表单(列表/详情打印入口)',
  `source_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT 'FORM' COMMENT 'FORM|DATASET',
  `source_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '逻辑 tableCode 或 datasetCode(不带组织前缀)',
  `template_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT 'DOC' COMMENT 'DOC=AiEditor 文档模板;SHEET=Luckysheet 表格模板;预留 GRID(宽表台账)',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '1' COMMENT '启用1/停用0(运行态下拉只列启用行;停用不删,历史单据可重打)',
  `content_html` longtext COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'DOC 模板版式真相:编辑器产出的 HTML(唯一真相);SHEET 模板为空',
  `content_sheet` longtext COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'SHEET 模板版式真相:Luckysheet 单 sheet JSON(celldata+config);占位符绑定自描述在单元格文本里',
  `binding_json` json DEFAULT NULL COMMENT '占位符与明细区绑定(plan-sheet-print §3.4)',
  `params_json` json DEFAULT NULL COMMENT '参数定义 [{field,op,label,default}]',
  `print_config` json DEFAULT NULL COMMENT 'paper/orientation/margins/scale/headerFooter',
  `is_default` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'N' COMMENT '默认模板 Y/N(唯一性靠保存先清后设,单条条件 update)',
  `sort` int DEFAULT '0' COMMENT '排序',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `UNI_template_code` (`template_code`,`deleted_time`),
  KEY `idx_form` (`form_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='单据打印模板(HTML 版式+数据绑定, plan-sheet-print §3.2)';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `dynamic_form`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dynamic_form` (
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
  `department_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '所属部门',
  `organization_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '所属组织',
  `remark` varchar(900) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '备注',
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '数据标题',
  `status` int DEFAULT NULL COMMENT '状态;业务对象的状态,0:草稿,1审批完成或已提交,2:流程运行中,3:被取消',
  `table_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '表单编码,数据库表的表名',
  `table_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '表单名称',
  `meta_data` json DEFAULT NULL COMMENT '配置的前端界面的json',
  `is_workflow` int DEFAULT NULL COMMENT '是否启用工作流 0 未启用  1 启用',
  `workflow_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '工作流的标识',
  `app_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '应用的ID',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `code_unque` (`table_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='前端列表配置';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `dynamic_form_column`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dynamic_form_column` (
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
  `department_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '所属部门',
  `organization_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '所属组织',
  `remark` varchar(900) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '备注',
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '数据标题',
  `status` int DEFAULT NULL COMMENT '状态;业务对象的状态,0:草稿,1审批完成或已提交,2:流程运行中,3:被取消',
  `form_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '表单ID',
  `filed_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT ' 字段编码,数据库表的列表',
  `filed_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '字段名称',
  `filed_type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '字段类型',
  `length` int DEFAULT NULL COMMENT '长度',
  `scale` int DEFAULT NULL COMMENT '小数点位数',
  `sort` int DEFAULT NULL COMMENT '排序',
  `default_value` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '默认值',
  `query_default_value` varchar(900) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '查询条件的默认值;列表查询面板自动预填该值',
  `order_dir` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '列表默认排序方向;ASC/DESC,空为不参与默认排序',
  `show_list` int DEFAULT NULL COMMENT '列表是否显示',
  `show_query` int DEFAULT NULL COMMENT '查询是否显示',
  `sensitive_flag` char(1) COLLATE utf8mb4_bin DEFAULT 'N' COMMENT '敏感字段标记;导出/打印/外链按 io_config.sensitive_policy 脱敏或隐藏',
  `unique_flag` char(1) COLLATE utf8mb4_bin DEFAULT 'N' COMMENT '业务唯一键标记;由组件 props.unique 物化,只读镜像',
  `component_id` varchar(100) COLLATE utf8mb4_bin DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='form的列的数据';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `dynamic_form_io_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dynamic_form_io_config` (
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
  `department_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '所属部门',
  `organization_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '所属组织',
  `remark` varchar(900) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '备注',
  `form_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '关联 dynamic_form.id',
  `export_enabled` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'N' COMMENT '允许数据导出 Y/N',
  `import_enabled` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'N' COMMENT '允许数据导入 Y/N',
  `export_scope` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT 'SELF' COMMENT '导出范围 SELF/DEPT/ALL（叠加行级数据范围取交集）',
  `export_columns` json DEFAULT NULL COMMENT '导出字段编码数组,空=跟随show_list',
  `sensitive_policy` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT 'MASK' COMMENT '敏感列策略 MASK脱敏/HIDE不出列（载体 dynamic_form_column.sensitive，§4.4）',
  `dup_strategy` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT 'SKIP' COMMENT '导入重复键策略 SKIP/OVERWRITE/REJECT',
  `strict_validate` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT 'Y' COMMENT '导入必填/类型校验 Y/N',
  `import_limit` int DEFAULT '5000' COMMENT '单次导入行数上限',
  `import_async` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT 'Y' COMMENT '导入执行方式 Y异步/N同步(仅小批量)',
  `run_backscript` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT 'N' COMMENT '导入是否逐行执行 backScript',
  `trigger_mode` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT 'SUPPRESS' COMMENT '导入是否发联动事件 SUPPRESS/FIRE',
  `import_flow_mode` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT 'NONE' COMMENT '工作流表单导入 NONE不发起/AUTO_START',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_form` (`form_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='表单数据导入导出配置(high-setting §4.3)';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `dynamic_form_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dynamic_form_log` (
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `tenant_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'system-default' COMMENT '租户号',
  `revision` int NOT NULL DEFAULT '1' COMMENT '乐观锁',
  `created_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '创建人',
  `created_time` datetime NOT NULL COMMENT '创建时间',
  `updated_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted_flag` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'N' COMMENT '是否删除',
  `deleted_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '删除人',
  `deleted_time` datetime DEFAULT NULL COMMENT '删除时间',
  `department_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '所属部门',
  `organization_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '所属组织',
  `remark` varchar(900) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '备注',
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '数据标题',
  `status` int DEFAULT NULL COMMENT '状态;业务对象的状态,0:草稿,1审批完成或已提交,2:流程运行中,3:被取消',
  `form_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
  `version` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
  `meta_data` json DEFAULT NULL,
  `is_workflow` int DEFAULT NULL COMMENT '是否启用工作流 0 未启用  1 启用',
  `workflow_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '工作流的标识',
  `log_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT 'VERSION' COMMENT '快照类型;VERSION=正式生效版本,DRAFT=设计草稿',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='form的历史版本';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `dynamic_form_relation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dynamic_form_relation` (
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
  `department_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '所属部门',
  `organization_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '所属组织',
  `remark` varchar(900) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '备注',
  `form_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '引用方表单ID(dynamic_form.id)',
  `table_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '引用方表单编码(dynamic_form.table_code)',
  `target_table_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '被关联表单编码(relation.formCode)',
  `return_columns` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '引用到的被关联表字段,逗号分隔(displayField + mappings.field)',
  `has_filter` int NOT NULL DEFAULT '0' COMMENT '任一引用配置了数据范围 0否 1是；配置后读取不收敛列',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_dfr_target` (`target_table_code`) USING BTREE,
  KEY `idx_dfr_form` (`form_id`) USING BTREE,
  KEY `idx_dfr_referencer` (`table_code`,`target_table_code`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='动态表单关联引用注册表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `dynamic_form_share`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dynamic_form_share` (
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
  `department_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '所属部门',
  `organization_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '所属组织',
  `remark` varchar(900) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '备注',
  `form_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '关联 dynamic_form.id',
  `share_type` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'FILL填写/VIEW查看',
  `token_digest` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'SHA-256(token)摘要,原文不落库(§5.3-1)',
  `token_tail` varchar(4) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '展示用尾号',
  `link_cipher` varchar(512) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '完整 token 的 AES/GCM 密文（密钥 openapi.secret-master-key）',
  `prefill_fields` varchar(1000) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '免登预填白名单（字段编码 JSON 数组）',
  `scope_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'ANON_PUBLIC' COMMENT 'FILL固定ANON_PUBLIC;VIEW: LOGIN_ALL/LOGIN_DEPT/ANON_PUBLIC',
  `scope_dept_ids` json DEFAULT NULL COMMENT 'LOGIN_DEPT 时部门id数组',
  `password` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '访问密码bcrypt,可空',
  `expire_time` datetime DEFAULT NULL COMMENT '有效期截止,NULL永久',
  `submit_limit` int DEFAULT NULL COMMENT '提交总次数上限,NULL不限',
  `per_person_limit` int DEFAULT '1' COMMENT '每人限填次数(匿名按IP+UA指纹)',
  `captcha` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'N' COMMENT '行为验证码 Y/N(P0未接,预留)',
  `editable_after_submit` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'N' COMMENT '提交后允许凭回执码修改 Y/N',
  `proxy_user_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '外链提交代理用户,空=表单创建人',
  `enabled` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'Y' COMMENT '启用 Y/N(停用即404)',
  `submit_count` int NOT NULL DEFAULT '0' COMMENT '累计提交数(填写链)',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_digest` (`token_digest`),
  KEY `idx_form` (`form_id`,`share_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='表单外链配置(high-setting §5.4)';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `dynamic_linkage_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dynamic_linkage_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `rule_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '规则ID',
  `form_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '表单ID',
  `rule_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '规则编码(冗余,日志常青)',
  `organization_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '所属组织(隔离维度)',
  `database_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '业务库名(异步重放上下文)',
  `event_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'CREATE/UPDATE/DELETE/EFFECTIVE',
  `data_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '数据ID',
  `event_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '幂等键(§7.5.1),禁止随机UUID',
  `fire_time` datetime(3) DEFAULT NULL COMMENT '计划触发时刻(TIMER/DATE_FIELD参与event_id)',
  `source_tag` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '事件来源 NORMAL/OPENAPI/PUBLIC_LINK/IMPORT/LINKAGE',
  `depth` int NOT NULL DEFAULT '0' COMMENT '级联深度',
  `condition_pass` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '条件判定 Y/N',
  `condition_trace` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '条件判定依据(排查"为什么没触发")',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'SUCCESS/PARTIAL/FAILED/SKIPPED/SKIPPED_CONCURRENT/DUPLICATE',
  `action_results` json DEFAULT NULL COMMENT '逐动作结果[{type,status,costMs,error,attempts}]',
  `error_msg` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '错误信息',
  `start_time` datetime(3) DEFAULT NULL COMMENT '开始时间',
  `cost_ms` int DEFAULT NULL COMMENT '耗时毫秒',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_idem` (`rule_id`,`event_id`),
  KEY `idx_rule` (`rule_id`,`start_time`),
  KEY `idx_data` (`form_id`,`data_id`),
  KEY `idx_org_time` (`organization_id`,`start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='联动规则执行日志(high-setting §7.1)';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `dynamic_linkage_log_archive`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dynamic_linkage_log_archive` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `rule_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '规则ID',
  `form_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '表单ID',
  `rule_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '规则编码(冗余,日志常青)',
  `organization_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '所属组织(隔离维度)',
  `database_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '业务库名(异步重放上下文)',
  `event_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'CREATE/UPDATE/DELETE/EFFECTIVE',
  `data_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '数据ID',
  `event_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '幂等键(§7.5.1),禁止随机UUID',
  `fire_time` datetime(3) DEFAULT NULL COMMENT '计划触发时刻(TIMER/DATE_FIELD参与event_id)',
  `source_tag` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '事件来源 NORMAL/OPENAPI/PUBLIC_LINK/IMPORT/LINKAGE',
  `depth` int NOT NULL DEFAULT '0' COMMENT '级联深度',
  `condition_pass` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '条件判定 Y/N',
  `condition_trace` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '条件判定依据(排查"为什么没触发")',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'SUCCESS/PARTIAL/FAILED/SKIPPED/SKIPPED_CONCURRENT/DUPLICATE',
  `action_results` json DEFAULT NULL COMMENT '逐动作结果[{type,status,costMs,error,attempts}]',
  `error_msg` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '错误信息',
  `start_time` datetime(3) DEFAULT NULL COMMENT '开始时间',
  `cost_ms` int DEFAULT NULL COMMENT '耗时毫秒',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_idem` (`rule_id`,`event_id`),
  KEY `idx_rule` (`rule_id`,`start_time`),
  KEY `idx_data` (`form_id`,`data_id`),
  KEY `idx_org_time` (`organization_id`,`start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='联动规则执行日志(high-setting §7.1)';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `dynamic_linkage_rule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dynamic_linkage_rule` (
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
  `department_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '所属部门',
  `organization_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '所属组织',
  `remark` varchar(900) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '备注',
  `form_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '关联 dynamic_form.id',
  `rule_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '应用内稳定编码[beta-z][a-z0-9_]{2,63};调度biz_key与应用包判重用它(§7.6)',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '规则名称',
  `enabled` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'Y' COMMENT '启用 Y/N',
  `event_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'CREATE/UPDATE/DELETE/EFFECTIVE(TIMER/DATE_FIELD待调度内核)',
  `event_config` json DEFAULT NULL COMMENT 'UPDATE:{watchFields[]};TIMER:{cron};DATE_FIELD:{fieldCode,offsetDays}预留',
  `condition_tree` json DEFAULT NULL COMMENT '条件树,NULL=恒真(§7.3)',
  `actions` json NOT NULL COMMENT '有序动作数组[{type,config,stopOnFail}](§7.4)',
  `async_mode` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'ASYNC' COMMENT 'ASYNC事件后异步/SYNC同事务(仅DATA_*动作,§7.5.2)',
  `fail_policy` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'CONTINUE' COMMENT 'CONTINUE/ABORT',
  `max_depth` int NOT NULL DEFAULT '1' COMMENT '允许级联深度,默认1=本规则触发的变更不再触发(§7.5.3)',
  `target_mode` varchar(16) COLLATE utf8mb4_bin DEFAULT 'PER_ROW' COMMENT 'TIMER作用对象 PER_ROW逐条/WHOLE_TABLE整表一次',
  `misfire_policy` varchar(12) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT 'SKIP' COMMENT '漏触发 COMPENSATE/SKIP(P2)',
  `concurrent_policy` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT 'FORBID' COMMENT '重叠执行 FORBID/QUEUE(P2)',
  `timeout_seconds` int DEFAULT '300' COMMENT '单轮执行上限',
  `max_rows_per_fire` int DEFAULT '5000' COMMENT '单次触发最多处理行数',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_form_code` (`form_id`,`rule_code`),
  KEY `idx_form_event` (`form_id`,`event_type`,`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='表单联动规则(high-setting §7.1)';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `dynamic_report`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dynamic_report` (
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
  `department_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '所属部门',
  `organization_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '所属组织',
  `remark` varchar(900) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '备注',
  `report_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '图表编码（业务主键）',
  `report_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '图表名称',
  `report_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '图表类型;bar柱状,pie饼图,line折线,stat数字卡',
  `dataset_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '数据集编码(dynamic_dataset.dataset_code)',
  `app_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '所属应用ID',
  `config` json DEFAULT NULL COMMENT '图表配置{dimensions:[{field,granularity,lookup:{targetTableCode,displayField}}],measures:[{field,agg}],filters,sort,limit}',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_report_code` (`report_code`) USING BTREE,
  KEY `idx_report_dataset` (`dataset_code`) USING BTREE,
  KEY `idx_report_app` (`app_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='报表图表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `dynamic_script_asset`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dynamic_script_asset` (
  `id` varchar(36) NOT NULL COMMENT '主键',
  `tenant_id` varchar(36) DEFAULT NULL,
  `script_code` varchar(64) NOT NULL COMMENT '资产编码（组织内唯一，稳定接口）',
  `script_name` varchar(100) NOT NULL COMMENT '资产名称',
  `category` varchar(20) NOT NULL COMMENT 'VALIDATION|CALC|AUTOFILL|DATA_OP',
  `language` varchar(20) NOT NULL DEFAULT 'GROOVY' COMMENT 'v1 唯一枚举 GROOVY',
  `body` mediumtext COMMENT '脚本正文（沙箱白名单校验后入库）',
  `params_schema` json DEFAULT NULL COMMENT '[{"name","type","required","default","description"}]',
  `result_type` varchar(20) NOT NULL DEFAULT 'NONE' COMMENT 'NONE|BOOLEAN|OBJECT',
  `timeout_seconds` int NOT NULL DEFAULT '10' COMMENT '执行超时秒（1-60）',
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT|PUBLISHED|DISABLED',
  `version` int NOT NULL DEFAULT '1' COMMENT '版本（已发布改正文/签名必须递增）',
  `app_id` varchar(36) DEFAULT NULL COMMENT '所属应用',
  `organization_id` varchar(36) DEFAULT NULL COMMENT '所属组织',
  `department_id` varchar(36) DEFAULT NULL COMMENT '所属部门',
  `remark` varchar(500) DEFAULT NULL,
  `revision` bigint NOT NULL DEFAULT '0',
  `created_by` varchar(36) DEFAULT NULL,
  `created_time` datetime(3) DEFAULT NULL,
  `updated_by` varchar(36) DEFAULT NULL,
  `updated_time` datetime(3) DEFAULT NULL,
  `deleted_flag` char(1) NOT NULL DEFAULT 'N',
  `deleted_by` varchar(36) DEFAULT NULL,
  `deleted_time` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_script_code_org` (`script_code`,`organization_id`,`deleted_flag`),
  KEY `idx_script_asset_org_status` (`organization_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='脚本资产（C1）';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `dynamic_script_asset_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dynamic_script_asset_log` (
  `id` varchar(36) NOT NULL,
  `tenant_id` varchar(36) DEFAULT NULL,
  `asset_id` varchar(36) NOT NULL COMMENT '资产 id',
  `script_code` varchar(64) NOT NULL,
  `version` int NOT NULL,
  `log_type` varchar(20) NOT NULL COMMENT 'PUBLISH|DISABLE',
  `snapshot` mediumtext COMMENT '发布时整行快照 JSON（含 body/params_schema）',
  `organization_id` varchar(36) DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  `revision` bigint NOT NULL DEFAULT '0',
  `created_by` varchar(36) DEFAULT NULL,
  `created_time` datetime(3) DEFAULT NULL,
  `updated_by` varchar(36) DEFAULT NULL,
  `updated_time` datetime(3) DEFAULT NULL,
  `deleted_flag` char(1) NOT NULL DEFAULT 'N',
  `deleted_by` varchar(36) DEFAULT NULL,
  `deleted_time` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_script_asset_log_asset` (`asset_id`,`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='脚本资产发布快照';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `dynamic_script_binding`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dynamic_script_binding` (
  `id` varchar(36) NOT NULL,
  `tenant_id` varchar(36) DEFAULT NULL,
  `asset_id` varchar(36) NOT NULL COMMENT '脚本资产 id',
  `asset_version` int DEFAULT NULL COMMENT '钉住版本，NULL=跟随当前发布版',
  `form_id` varchar(36) NOT NULL COMMENT '表单 id（dynamic_form.id）',
  `table_code` varchar(64) DEFAULT NULL COMMENT '表单物理表名',
  `bind_point` varchar(20) NOT NULL COMMENT 'SAVE_VALIDATE|BACK_SCRIPT|SCHEDULE|LINKAGE_SCRIPT',
  `param_mapping` json DEFAULT NULL COMMENT '{"资产参数名":"字段编码|{{currentUser}}|{{now}}|常量"}',
  `fail_message` varchar(200) DEFAULT NULL COMMENT '校验失败回显提示',
  `execution_order` int DEFAULT NULL COMMENT '执行顺序（小者先）',
  `enabled` char(1) NOT NULL DEFAULT 'Y',
  `organization_id` varchar(36) DEFAULT NULL,
  `department_id` varchar(36) DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  `revision` bigint NOT NULL DEFAULT '0',
  `created_by` varchar(36) DEFAULT NULL,
  `created_time` datetime(3) DEFAULT NULL,
  `updated_by` varchar(36) DEFAULT NULL,
  `updated_time` datetime(3) DEFAULT NULL,
  `deleted_flag` char(1) NOT NULL DEFAULT 'N',
  `deleted_by` varchar(36) DEFAULT NULL,
  `deleted_time` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_script_binding_form` (`form_id`,`bind_point`,`enabled`),
  KEY `idx_script_binding_asset` (`asset_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='脚本挂点绑定（独立表，不进 metaData）';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `dynamic_script_exec_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dynamic_script_exec_log` (
  `id` varchar(36) NOT NULL,
  `asset_id` varchar(36) NOT NULL,
  `script_code` varchar(64) NOT NULL,
  `version` int DEFAULT NULL,
  `bind_point` varchar(20) NOT NULL COMMENT 'SAVE_VALIDATE|BACK_SCRIPT|SCHEDULE|LINKAGE_SCRIPT|TRIAL',
  `form_id` varchar(36) DEFAULT NULL,
  `table_code` varchar(64) DEFAULT NULL,
  `data_id` varchar(36) DEFAULT NULL COMMENT '被保存数据行 id（保存校验挂点）',
  `success` char(1) NOT NULL COMMENT 'Y/N',
  `result_summary` varchar(500) DEFAULT NULL COMMENT '结果摘要（前 500 字符）',
  `error_message` varchar(1000) DEFAULT NULL,
  `elapsed_ms` bigint DEFAULT NULL,
  `organization_id` varchar(36) DEFAULT NULL,
  `created_time` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_script_exec_asset` (`asset_id`,`version`,`created_time`),
  KEY `idx_script_exec_trial` (`asset_id`,`version`,`bind_point`,`success`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='脚本执行日志（append-only）';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `dynamic_share_access_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dynamic_share_access_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `organization_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '所属组织',
  `share_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '外链配置ID',
  `form_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '表单ID',
  `action` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'META预检/SUBMIT提交/UPDATE回执修改/VIEW_LIST/VIEW_DETAIL',
  `token_tail` varchar(4) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'token尾号(不落原文)',
  `client_ip` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '访问IP',
  `user_agent` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'UA',
  `result` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'SUCCESS/DENIED/REJECTED',
  `deny_reason` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '拒绝原因(404对外不区分,这里内部可查)',
  `data_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '关联数据行(提交/详情时)',
  `operator_id` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '操作人（REVEAL 取链等管理端动作）',
  `created_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '访问时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_share_time` (`share_id`,`created_time`),
  KEY `idx_org_time` (`organization_id`,`created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='外链访问审计日志(high-setting §5.3-2)';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `dynamic_share_access_log_archive`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dynamic_share_access_log_archive` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `organization_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '所属组织',
  `share_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '外链配置ID',
  `form_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '表单ID',
  `action` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'META预检/SUBMIT提交/UPDATE回执修改/VIEW_LIST/VIEW_DETAIL',
  `token_tail` varchar(4) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'token尾号(不落原文)',
  `client_ip` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '访问IP',
  `user_agent` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'UA',
  `result` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'SUCCESS/DENIED/REJECTED',
  `deny_reason` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '拒绝原因(404对外不区分,这里内部可查)',
  `data_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '关联数据行(提交/详情时)',
  `created_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '访问时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_share_time` (`share_id`,`created_time`),
  KEY `idx_org_time` (`organization_id`,`created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='外链访问审计日志(high-setting §5.3-2)';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `dynamic_share_receipt`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dynamic_share_receipt` (
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
  `department_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '所属部门',
  `organization_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '所属组织',
  `remark` varchar(900) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '备注',
  `share_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '关联 dynamic_form_share.id',
  `receipt_digest` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '回执码SHA-256摘要,原文不落库',
  `data_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '回执码对应的数据行ID',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_share_receipt` (`share_id`,`receipt_digest`),
  KEY `idx_data` (`data_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='外链回执码摘要→数据行映射(§5.3-7)';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `flow_definition`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flow_definition` (
  `id` bigint NOT NULL COMMENT '主键id',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `tenant_id` varchar(40) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '租户id',
  `del_flag` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标志',
  `flow_code` varchar(40) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '流程编码',
  `flow_name` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '流程名称',
  `model_value` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'CLASSICS' COMMENT '流程模型（经典/仿钉钉）',
  `category` varchar(40) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '流程类别',
  `version` varchar(20) COLLATE utf8mb4_general_ci DEFAULT '1' COMMENT '流程版本',
  `is_publish` int DEFAULT '0' COMMENT '是否发布（0未发布 1已发布 9已失效）',
  `form_custom` char(1) COLLATE utf8mb4_general_ci DEFAULT 'N' COMMENT '审批表单是否自定义（Y是 N否）',
  `form_path` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '审批表单路径',
  `activity_status` int DEFAULT '1' COMMENT '流程激活状态（0挂起 1激活）',
  `listener_type` varchar(30) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '监听器类型',
  `listener_path` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '监听器路径',
  `ext` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '业务详情-扩展字段',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='流程定义表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `flow_form`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flow_form` (
  `id` bigint NOT NULL COMMENT '主键id',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `tenant_id` varchar(40) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '租户id',
  `del_flag` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标志',
  `form_code` varchar(40) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '表单编码',
  `form_name` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '表单名称',
  `version` varchar(20) COLLATE utf8mb4_general_ci DEFAULT '1' COMMENT '表单版本',
  `is_publish` int DEFAULT '0' COMMENT '是否发布（0未发布 1已发布）',
  `form_type` int DEFAULT NULL COMMENT '表单类型',
  `form_path` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '表单路径',
  `form_content` mediumtext COLLATE utf8mb4_general_ci COMMENT '表单内容',
  `ext` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '扩展字段',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='流程表单表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `flow_form_bind`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flow_form_bind` (
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
  `department_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '所属部门',
  `organization_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '所属组织',
  `remark` varchar(900) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '备注',
  `flow_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '流程定义编码（warm-flow flow_definition.flow_code）',
  `flow_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '流程名称',
  `table_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '动态表单编码（dynamic_form.table_code）',
  `form_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '表单名称',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_flow_code_table_code` (`flow_code`,`table_code`) USING BTREE,
  KEY `idx_ffb_table_code` (`table_code`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='流程定义与动态表单绑定';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `flow_form_btn_perm`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flow_form_btn_perm` (
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
  `department_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '所属部门',
  `organization_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '所属组织',
  `remark` varchar(900) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '备注',
  `version_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '版本主表id（flow_form_version.id）',
  `flow_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '流程定义编码',
  `version` int NOT NULL DEFAULT '1' COMMENT '版本号',
  `node_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'global' COMMENT '节点编码，global 表示全局默认配置',
  `btn_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '按钮编码（save/approve/reject/transfer/delegate/addSign/reduceSign/jump/terminate/revoke）',
  `btn_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '按钮名称',
  `alias_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '按钮别名',
  `is_show` int NOT NULL DEFAULT '0' COMMENT '是否显示 0否 1是',
  `show_dialog` int DEFAULT NULL COMMENT '是否显示确认弹窗 0否 1是',
  `color` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '按钮颜色',
  `sort` int DEFAULT NULL COMMENT '排序',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_ffbp_version_id` (`version_id`) USING BTREE,
  KEY `idx_ffbp_node` (`version_id`,`node_code`) USING BTREE,
  KEY `idx_ffbp_flow` (`flow_code`,`version`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='流程表单节点按钮权限明细';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `flow_form_field_perm`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flow_form_field_perm` (
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
  `department_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '所属部门',
  `organization_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '所属组织',
  `remark` varchar(900) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '备注',
  `version_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '版本主表id（flow_form_version.id）',
  `flow_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '流程定义编码',
  `version` int NOT NULL DEFAULT '1' COMMENT '版本号',
  `node_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'global' COMMENT '节点编码，global 表示全局默认配置',
  `field_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '字段编码（dynamic_form_column.filed_code）',
  `field_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '字段名称',
  `is_show` int NOT NULL DEFAULT '1' COMMENT '是否显示 0否 1是',
  `is_edit` int NOT NULL DEFAULT '0' COMMENT '是否可编辑 0否 1是',
  `is_required` int NOT NULL DEFAULT '0' COMMENT '是否必填 0否 1是',
  `default_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '默认值类型',
  `default_value` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '默认值',
  `dict_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '字典类型',
  `sort` int DEFAULT NULL COMMENT '排序',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_fffp_version_id` (`version_id`) USING BTREE,
  KEY `idx_fffp_node` (`version_id`,`node_code`) USING BTREE,
  KEY `idx_fffp_flow` (`flow_code`,`version`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='流程表单节点字段权限明细';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `flow_form_node_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flow_form_node_config` (
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
  `department_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '所属部门',
  `organization_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '所属组织',
  `remark` varchar(900) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '备注',
  `definition_id` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '流程定义id（warm-flow flow_definition.id）',
  `definition_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '流程定义编码（flow_code）',
  `node_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '节点编码',
  `table_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '动态表单编码',
  `field_config` text CHARACTER SET utf8mb4 COLLATE utf8mb4_bin COMMENT '节点字段权限配置JSON',
  `btn_config` text CHARACTER SET utf8mb4 COLLATE utf8mb4_bin COMMENT '节点按钮权限配置JSON',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_definition_node` (`definition_id`,`node_code`) USING BTREE,
  KEY `idx_ffnc_definition_code` (`definition_code`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='流程节点表单配置';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `flow_form_version`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flow_form_version` (
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
  `department_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '所属部门',
  `organization_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '所属组织',
  `remark` varchar(900) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '备注',
  `flow_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '流程定义编码（warm-flow flow_definition.flow_code）',
  `flow_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '流程名称',
  `table_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '动态表单编码（dynamic_form.table_code）',
  `form_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '表单名称',
  `version` int NOT NULL DEFAULT '1' COMMENT '版本号（同 flow_code 下递增，从1开始）',
  `publish_status` int NOT NULL DEFAULT '0' COMMENT '发布状态 0草稿 1已发布',
  `current_flag` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'N' COMMENT '是否当前生效发布版 Y/N',
  `publish_time` datetime DEFAULT NULL COMMENT '发布时间',
  `publish_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '发布人',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_flow_version` (`flow_code`,`version`) USING BTREE,
  KEY `idx_ffv_table_code` (`table_code`) USING BTREE,
  KEY `idx_ffv_current` (`flow_code`,`current_flag`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='流程表单配置版本主表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `flow_his_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flow_his_task` (
  `id` bigint NOT NULL COMMENT '主键id',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间（任务开始时间）',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间（审批完成时间）',
  `tenant_id` varchar(40) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '租户id',
  `del_flag` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标志',
  `definition_id` bigint DEFAULT NULL COMMENT '对应flow_definition表的id',
  `flow_name` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '流程名称',
  `instance_id` bigint DEFAULT NULL COMMENT '对应flow_instance表的id',
  `task_id` bigint DEFAULT NULL COMMENT '对应flow_task表的id',
  `cooperate_type` int DEFAULT '0' COMMENT '协作方式（1审批 2转办 3委派 4会签 5票签 6加签 7减签）',
  `business_id` varchar(40) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '业务id',
  `node_code` varchar(40) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '开始节点编码',
  `node_name` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '开始节点名称',
  `node_type` int DEFAULT NULL COMMENT '开始节点类型',
  `target_node_code` varchar(40) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '目标节点编码',
  `target_node_name` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '目标节点名称',
  `approver` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '审批者',
  `collaborator` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '协作人',
  `skip_type` varchar(10) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '跳转类型（PASS通过 REJECT退回 NONE无动作）',
  `flow_status` varchar(10) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '流程状态',
  `message` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '审批意见',
  `variable` mediumtext COLLATE utf8mb4_general_ci COMMENT '任务变量',
  `ext` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '扩展字段',
  `form_custom` char(1) COLLATE utf8mb4_general_ci DEFAULT 'N' COMMENT '审批表单是否自定义',
  `form_path` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '审批表单路径',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_his_instance_id` (`instance_id`) USING BTREE,
  KEY `idx_his_task_id` (`task_id`) USING BTREE,
  KEY `idx_his_approver` (`approver`) USING BTREE,
  KEY `idx_his_business_id` (`business_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='历史任务记录表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `flow_instance`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flow_instance` (
  `id` bigint NOT NULL COMMENT '主键id',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人（流程发起人）',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `tenant_id` varchar(40) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '租户id',
  `del_flag` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标志',
  `definition_id` bigint DEFAULT NULL COMMENT '对应flow_definition表的id',
  `flow_name` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '流程名称',
  `business_id` varchar(40) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '业务id（业务数据主键）',
  `node_type` int DEFAULT NULL COMMENT '节点类型',
  `node_code` varchar(40) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '流程节点编码',
  `node_name` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '流程节点名称',
  `variable` mediumtext COLLATE utf8mb4_general_ci COMMENT '任务变量',
  `flow_status` varchar(10) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '流程状态（0待提交 1审批中 2审批通过 3自动完成 4终止 5作废 6撤销 7取回 8已完成 9已退回 10失效 11拿回）',
  `activity_status` int DEFAULT '1' COMMENT '流程激活状态（0挂起 1激活）',
  `form_custom` char(1) COLLATE utf8mb4_general_ci DEFAULT 'N' COMMENT '审批表单是否自定义',
  `form_path` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '审批表单路径',
  `def_json` mediumtext COLLATE utf8mb4_general_ci COMMENT '流程定义json',
  `ext` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '扩展字段',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_instance_definition_id` (`definition_id`) USING BTREE,
  KEY `idx_instance_business_id` (`business_id`) USING BTREE,
  KEY `idx_instance_create_by` (`create_by`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='流程实例表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `flow_node`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flow_node` (
  `id` bigint NOT NULL COMMENT '主键id',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `tenant_id` varchar(40) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '租户id',
  `del_flag` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标志',
  `node_type` int DEFAULT NULL COMMENT '节点类型（0开始 1中间 2结束 3互斥网关 4并行网关）',
  `definition_id` bigint DEFAULT NULL COMMENT '流程定义id',
  `node_code` varchar(40) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '节点编码',
  `node_name` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '节点名称',
  `permission_flag` varchar(1000) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '权限标识（权限类型:权限标识，多个@@分隔）',
  `node_ratio` varchar(10) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '会签票签比例（0或签，1~99票签，100会签）',
  `coordinate` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '坐标',
  `version` varchar(20) COLLATE utf8mb4_general_ci DEFAULT '1' COMMENT '版本',
  `any_node_skip` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '任意跳转的节点编码',
  `listener_type` varchar(30) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '监听器类型',
  `listener_path` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '监听器路径',
  `form_custom` char(1) COLLATE utf8mb4_general_ci DEFAULT 'N' COMMENT '审批表单是否自定义',
  `form_path` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '审批表单路径',
  `ext` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '扩展属性',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_node_definition_id` (`definition_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='流程节点表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `flow_skip`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flow_skip` (
  `id` bigint NOT NULL COMMENT '主键id',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `tenant_id` varchar(40) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '租户id',
  `del_flag` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标志',
  `definition_id` bigint DEFAULT NULL COMMENT '流程定义id',
  `node_id` bigint DEFAULT NULL COMMENT '节点id',
  `now_node_code` varchar(40) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '当前流程节点编码',
  `now_node_type` int DEFAULT NULL COMMENT '当前节点类型',
  `next_node_code` varchar(40) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '下一个流程节点编码',
  `next_node_type` int DEFAULT NULL COMMENT '下一个节点类型',
  `skip_name` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '跳转名称',
  `skip_type` varchar(10) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '跳转类型（PASS通过 REJECT退回）',
  `skip_condition` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '跳转条件',
  `coordinate` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '坐标',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_skip_definition_id` (`definition_id`) USING BTREE,
  KEY `idx_skip_now_node_code` (`now_node_code`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='节点跳转关联表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `flow_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flow_task` (
  `id` bigint NOT NULL COMMENT '主键id',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `tenant_id` varchar(40) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '租户id',
  `del_flag` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标志',
  `definition_id` bigint DEFAULT NULL COMMENT '对应flow_definition表的id',
  `instance_id` bigint DEFAULT NULL COMMENT '对应flow_instance表的id',
  `flow_name` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '流程名称',
  `business_id` varchar(40) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '业务id',
  `node_code` varchar(40) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '节点编码',
  `node_name` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '节点名称',
  `node_type` int DEFAULT NULL COMMENT '节点类型',
  `flow_status` varchar(10) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '流程状态',
  `form_custom` char(1) COLLATE utf8mb4_general_ci DEFAULT 'N' COMMENT '审批表单是否自定义',
  `form_path` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '审批表单路径',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_task_instance_id` (`instance_id`) USING BTREE,
  KEY `idx_task_definition_id` (`definition_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='待办任务表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `flow_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flow_user` (
  `id` bigint NOT NULL COMMENT '主键id',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `tenant_id` varchar(40) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '租户id',
  `del_flag` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标志',
  `type` char(1) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '人员类型（1待办任务的审批人权限 2待办任务的转办人权限 3待办任务的委托人权限）',
  `processed_by` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '权限人（用户/角色/部门等权限标识值）',
  `associated` bigint DEFAULT NULL COMMENT '任务表id',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_user_associated` (`associated`) USING BTREE,
  KEY `idx_user_processed_by` (`processed_by`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='流程用户表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_ai_artifact`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_ai_artifact` (
  `id` varchar(36) NOT NULL,
  `tenant_id` varchar(36) DEFAULT NULL,
  `session_id` varchar(64) DEFAULT NULL COMMENT '生成会话标识（人工=manual）',
  `object_type` varchar(20) NOT NULL COMMENT 'FORM|REPORT|DASHBOARD|FLOW|APP',
  `object_id` varchar(64) NOT NULL COMMENT '生成物主键/编码',
  `code` varchar(64) DEFAULT NULL COMMENT '生成物编码（展示与定位）',
  `rolled_back` char(1) NOT NULL DEFAULT 'N',
  `rollback_note` varchar(500) DEFAULT NULL COMMENT '撤销结果摘要/人工处理提示',
  `organization_id` varchar(36) DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  `revision` bigint NOT NULL DEFAULT '0',
  `created_by` varchar(36) DEFAULT NULL,
  `created_time` datetime(3) DEFAULT NULL,
  `updated_by` varchar(36) DEFAULT NULL,
  `updated_time` datetime(3) DEFAULT NULL,
  `deleted_flag` char(1) NOT NULL DEFAULT 'N',
  `deleted_by` varchar(36) DEFAULT NULL,
  `deleted_time` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_ai_artifact_session` (`session_id`,`rolled_back`),
  KEY `idx_ai_artifact_object` (`object_type`,`object_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 生成物台账（B3）';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_ai_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_ai_config` (
  `id` varchar(36) NOT NULL,
  `tenant_id` varchar(36) DEFAULT NULL,
  `organization_id` varchar(36) NOT NULL COMMENT '组织（每组织一份）',
  `provider` varchar(50) DEFAULT NULL COMMENT '服务商标识（ZHIPU/OPENAI/CUSTOM 等）',
  `base_url` varchar(500) NOT NULL COMMENT '模型 API 地址（公网模型=公网 API；内网模型=内网推理地址）',
  `api_key_cipher` varchar(1024) DEFAULT NULL COMMENT 'apiKey AES/GCM 密文（不回显）',
  `api_key_prefix` varchar(16) DEFAULT NULL COMMENT '掩码：明文前 6 位',
  `api_key_last4` varchar(8) DEFAULT NULL COMMENT '掩码：明文后 4 位',
  `model` varchar(100) DEFAULT NULL COMMENT '模型名',
  `temperature` decimal(3,2) DEFAULT NULL COMMENT '温度',
  `session_token_limit` int DEFAULT NULL COMMENT '单会话 token 上限（NULL=不限）',
  `intranet_flag` char(1) NOT NULL DEFAULT 'N' COMMENT '是否内网模型（Y=数据不出内网）',
  `enabled` char(1) NOT NULL DEFAULT 'N' COMMENT '本组织 AI 能力总开关',
  `remark` varchar(500) DEFAULT NULL,
  `revision` bigint NOT NULL DEFAULT '0',
  `created_by` varchar(36) DEFAULT NULL,
  `created_time` datetime(3) DEFAULT NULL,
  `updated_by` varchar(36) DEFAULT NULL,
  `updated_time` datetime(3) DEFAULT NULL,
  `deleted_flag` char(1) NOT NULL DEFAULT 'N',
  `deleted_by` varchar(36) DEFAULT NULL,
  `deleted_time` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_config_org` (`organization_id`,`deleted_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 模型配置（B5，v1.4 公网模型口径）';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_app_common_group`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_app_common_group` (
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'primary key id',
  `app_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '应用的ID',
  `common_group_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '关联的公共组',
  `status` int NOT NULL COMMENT '状态',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
  `tenant_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'system-default' COMMENT '租户号',
  `revision` int NOT NULL DEFAULT '1' COMMENT '乐观锁',
  `created_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '创建人',
  `created_time` datetime NOT NULL COMMENT '创建时间',
  `updated_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted_flag` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'N' COMMENT '是否删除',
  `deleted_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '删除人',
  `deleted_time` datetime DEFAULT NULL COMMENT '删除时间',
  `organization_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '组织ID',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin ROW_FORMAT=DYNAMIC COMMENT='应用关联的公共组';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_app_info`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_app_info` (
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'primary key id',
  `app_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'application key',
  `app_secret` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '密钥',
  `icon` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '图标',
  `app_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '应用名称',
  `code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '编码;访问的路径',
  `app_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'APP里的访问路径',
  `pc_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'PC里的访问路径',
  `type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'CUSTOM' COMMENT '类型;SYSTEM 系统默认 CUSTOM 用户创建',
  `user_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '用户ID',
  `status` int NOT NULL DEFAULT '0' COMMENT '状态    0 正常  1 禁用',
  `sort` int NOT NULL DEFAULT '1' COMMENT '排序',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
  `tenant_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'system-default' COMMENT '租户号',
  `revision` int NOT NULL DEFAULT '1' COMMENT '乐观锁',
  `created_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '创建人',
  `created_time` datetime NOT NULL COMMENT '创建时间',
  `updated_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted_flag` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'N' COMMENT '是否删除',
  `deleted_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '删除人',
  `deleted_time` datetime DEFAULT NULL COMMENT '删除时间',
  `organization_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '组织ID',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `user_id_index` (`user_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin ROW_FORMAT=DYNAMIC COMMENT='应用信息';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_app_menu`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_app_menu` (
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'primary key id',
  `app_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '应用的ID',
  `menu_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '关联的菜单',
  `status` int NOT NULL COMMENT '状态',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
  `tenant_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'system-default' COMMENT '租户号',
  `revision` int NOT NULL DEFAULT '1' COMMENT '乐观锁',
  `created_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '创建人',
  `created_time` datetime NOT NULL COMMENT '创建时间',
  `updated_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted_flag` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'N' COMMENT '是否删除',
  `deleted_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '删除人',
  `deleted_time` datetime DEFAULT NULL COMMENT '删除时间',
  `organization_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '组织ID',
  `must_menu` int DEFAULT '0' COMMENT '应用必须要有的功能',
  `visible_menu` int DEFAULT '1' COMMENT '应用内是否显示。0不显示，1显示',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `appid_menuid` (`app_id`,`menu_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin ROW_FORMAT=DYNAMIC COMMENT='应用关联的菜单';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_app_path`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_app_path` (
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'primary key id',
  `app_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '应用的ID',
  `path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'path',
  `status` int NOT NULL COMMENT '状态',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
  `tenant_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'system-default' COMMENT '租户号',
  `revision` int NOT NULL DEFAULT '1' COMMENT '乐观锁',
  `created_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '创建人',
  `created_time` datetime NOT NULL COMMENT '创建时间',
  `updated_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted_flag` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'N' COMMENT '是否删除',
  `deleted_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '删除人',
  `deleted_time` datetime DEFAULT NULL COMMENT '删除时间',
  `organization_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '组织ID',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin ROW_FORMAT=DYNAMIC COMMENT='应用可以访问的路径';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_app_workflow`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_app_workflow` (
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '主键',
  `app_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '应用的ID',
  `workflow_form_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '关联的工作流表单ID',
  `status` int NOT NULL COMMENT '状态',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
  `tenant_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'system-default' COMMENT '租户号',
  `revision` int NOT NULL DEFAULT '1' COMMENT '乐观锁',
  `created_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '创建人',
  `created_time` datetime NOT NULL COMMENT '创建时间',
  `updated_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted_flag` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'N' COMMENT '是否删除',
  `deleted_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '删除人',
  `deleted_time` datetime DEFAULT NULL COMMENT '删除时间',
  `organization_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '组织ID',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '名称',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin ROW_FORMAT=DYNAMIC COMMENT='应用关联的流程表单';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_common_group`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_common_group` (
  `tenant_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'system-default' COMMENT '租户号',
  `revision` int NOT NULL DEFAULT '1' COMMENT '乐观锁',
  `created_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '创建人',
  `created_time` datetime NOT NULL COMMENT '创建时间',
  `updated_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '删除人',
  `deleted_time` datetime DEFAULT NULL COMMENT '删除时间',
  `deleted_flag` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'N' COMMENT '是否删除',
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '主键',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '备注',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '组名',
  `code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '组编码',
  `module_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT 'SYSTEM' COMMENT '模块类型;模块类型，方便分组显示',
  `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '描述',
  `group_type` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'USER_GROUP' COMMENT '类型;ROLE 角色组 USER_GROUP 用户组',
  `organization_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '组织ID',
  `department_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '部门ID',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin ROW_FORMAT=DYNAMIC COMMENT='公共组[用户或角色]';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_common_group_menu`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_common_group_menu` (
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'primary key id',
  `menu_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '菜单的ID',
  `common_group_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '关联的公共组',
  `status` int NOT NULL COMMENT '状态',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
  `tenant_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'system-default' COMMENT '租户号',
  `revision` int NOT NULL DEFAULT '1' COMMENT '乐观锁',
  `created_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '创建人',
  `created_time` datetime NOT NULL COMMENT '创建时间',
  `updated_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted_flag` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'N' COMMENT '是否删除',
  `deleted_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '删除人',
  `deleted_time` datetime DEFAULT NULL COMMENT '删除时间',
  `organization_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '组织ID',
  `must_menu` int DEFAULT '1' COMMENT '组必须要有的功能',
  `visible_menu` int DEFAULT '1' COMMENT '组内是否显示。0不显示，1显示',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin ROW_FORMAT=DYNAMIC COMMENT='组关联的权限';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_default_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_default_config` (
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '主键',
  `config_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '配置的键名',
  `config_value` json DEFAULT NULL COMMENT '配置的数据',
  `config_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '配置的名称',
  `organization_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '组织ID;所属组织的配置',
  `tenant_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'system-default' COMMENT '租户号',
  `revision` int NOT NULL DEFAULT '1' COMMENT '乐观锁',
  `created_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '创建人',
  `created_time` datetime NOT NULL COMMENT '创建时间',
  `updated_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted_flag` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'N' COMMENT '是否删除',
  `deleted_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '删除人',
  `deleted_time` datetime DEFAULT NULL COMMENT '删除时间',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin ROW_FORMAT=COMPACT COMMENT='系统默认配置';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_department`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_department` (
  `tenant_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'system-default' COMMENT '租户号',
  `revision` int NOT NULL DEFAULT '1' COMMENT '乐观锁',
  `created_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '创建人',
  `created_time` datetime NOT NULL COMMENT '创建时间',
  `updated_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted_flag` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT 'N' COMMENT '是否删除',
  `deleted_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '删除人',
  `deleted_time` datetime DEFAULT NULL COMMENT '删除时间',
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '主键',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '备注',
  `dept_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '部门编码',
  `dept_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '部门名称',
  `order_num` int DEFAULT '0' COMMENT '排序',
  `leader_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '主管的ID',
  `status` int NOT NULL DEFAULT '1' COMMENT '状态;是否禁用 0是否，1是是',
  `organization_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '组织ID;所属组织',
  `system_type` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'NORMAL' COMMENT '部门类型',
  `syn_flag` int unsigned DEFAULT '0' COMMENT '同步标识，1是同步，0非同步',
  `syn_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '同步的第三方ID',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin ROW_FORMAT=COMPACT COMMENT='部门';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_dept_relation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_dept_relation` (
  `tenant_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'system-default' COMMENT '租户号',
  `revision` int NOT NULL DEFAULT '1' COMMENT '乐观锁',
  `created_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '创建人',
  `created_time` datetime NOT NULL COMMENT '创建时间',
  `updated_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted_flag` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'N' COMMENT '是否删除',
  `deleted_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '删除人',
  `deleted_time` datetime DEFAULT NULL COMMENT '删除时间',
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '主键',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '备注',
  `parent_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '部门ID;上级部门',
  `dept_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '部门ID;下级部门',
  `level` int NOT NULL COMMENT '层级;上与下级是第几层级:顶级是0 组织名称。部门级别都是多1开始',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin ROW_FORMAT=COMPACT COMMENT='部门关系';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_dictionary_data`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_dictionary_data` (
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '字典id',
  `dict_sort` int NOT NULL DEFAULT '0' COMMENT '字典排序',
  `dict_label` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '字典标签',
  `dict_value` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '字典键值',
  `dict_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '字典类型',
  `css_class` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '样式属性（其他样式扩展）',
  `list_class` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '表格回显样式',
  `is_default` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT 'N' COMMENT '是否默认（Y是 N否）',
  `status` int NOT NULL DEFAULT '0' COMMENT '状态;（0正常 1停用）',
  `tenant_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'system-default' COMMENT '租户号',
  `revision` int NOT NULL DEFAULT '1' COMMENT '乐观锁',
  `created_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '创建人',
  `created_time` datetime NOT NULL COMMENT '创建时间',
  `updated_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted_flag` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'N' COMMENT '是否删除',
  `deleted_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '删除人',
  `deleted_time` datetime DEFAULT NULL COMMENT '删除时间',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '备注',
  `organization_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '组织',
  `department_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '部门',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin ROW_FORMAT=COMPACT COMMENT='字典数据表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_dictionary_type`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_dictionary_type` (
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '字典主键',
  `dict_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '字典名称',
  `dict_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '字典类型',
  `status` int NOT NULL DEFAULT '0' COMMENT '状态;（0正常 1停用）',
  `tenant_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'system-default' COMMENT '租户号',
  `revision` int NOT NULL DEFAULT '1' COMMENT '乐观锁',
  `created_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '创建人',
  `created_time` datetime NOT NULL COMMENT '创建时间',
  `updated_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted_flag` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'N' COMMENT '是否删除',
  `deleted_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '删除人',
  `deleted_time` datetime DEFAULT NULL COMMENT '删除时间',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '备注',
  `organization_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '组织',
  `department_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '部门',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin ROW_FORMAT=COMPACT COMMENT='字典类型表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_file_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_file_config` (
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '主键',
  `tenant_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'system-default' COMMENT '租户号',
  `revision` int NOT NULL DEFAULT '1' COMMENT '乐观锁',
  `created_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '创建人',
  `created_time` datetime NOT NULL COMMENT '创建时间',
  `updated_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted_flag` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'N' COMMENT '是否删除',
  `deleted_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '删除人',
  `deleted_time` datetime DEFAULT NULL COMMENT '删除时间',
  `department_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '部门ID',
  `organization_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '所属组织',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '备注',
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '标题',
  `name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'INIT' COMMENT '名称',
  `type` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'IMAGE' COMMENT '类型: OSS，LOCAL,MINIO',
  `endpoint` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '存储地址',
  `access_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '存储的ID',
  `access_key` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT 'LOCAL' COMMENT '存储的KEY',
  `bucket` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '桶',
  `store_path` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '存储的目录',
  `net_path` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '网络访问的地址',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin ROW_FORMAT=DYNAMIC COMMENT='上传文件的配置';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_file_info`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_file_info` (
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'GLOBAL' COMMENT '主键',
  `tenant_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'system-default' COMMENT '租户号',
  `revision` int NOT NULL DEFAULT '1' COMMENT '乐观锁',
  `created_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '创建人',
  `created_time` datetime NOT NULL COMMENT '创建时间',
  `updated_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted_flag` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'N' COMMENT '是否删除',
  `deleted_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '删除人',
  `deleted_time` datetime DEFAULT NULL COMMENT '删除时间',
  `department_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '部门ID',
  `organization_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '所属组织',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '备注',
  `source_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '1' COMMENT '来源ID',
  `source_stage` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'INIT' COMMENT '来源内不同阶段的图片，如申请、审批、验收',
  `type` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'IMAGE' COMMENT '类型: IMAGE,FILE',
  `image_path` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '图片存储地址',
  `image_url` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '图片访问地址',
  `original_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '原名称',
  `new_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '新名称',
  `file_size` int NOT NULL COMMENT '文件大小(字节数)',
  `file_store_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT 'LOCAL' COMMENT '文件存储类型: AliOSS,MINIO,LOCAL',
  `module` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'GLOBAL' COMMENT '文件所属模块',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin ROW_FORMAT=DYNAMIC COMMENT='上传文件信息';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_log_login`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_log_login` (
  `tenant_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'system-default' COMMENT '租户号',
  `revision` int NOT NULL DEFAULT '1' COMMENT '乐观锁',
  `created_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '创建人',
  `created_time` datetime NOT NULL COMMENT '创建时间',
  `updated_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '删除人',
  `deleted_time` datetime DEFAULT NULL COMMENT '删除时间',
  `deleted_flag` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'N' COMMENT '是否删除',
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '主键',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '备注',
  `login_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '登录名',
  `nick_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '昵称',
  `login_ip` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '登录IP',
  `login_location` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '登录地址',
  `browser` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '浏览器',
  `os` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '操作系统',
  `platform` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '平台固件',
  `message` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '消息',
  `status` int NOT NULL DEFAULT '0' COMMENT '状态;登录是否成功（0正常 1异常）',
  `department_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `organization_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin ROW_FORMAT=DYNAMIC COMMENT='系统登录日志';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_log_operation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_log_operation` (
  `tenant_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'system-default' COMMENT '租户号',
  `revision` int NOT NULL DEFAULT '1' COMMENT '乐观锁',
  `created_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '创建人;操作人ID',
  `created_time` datetime NOT NULL COMMENT '创建时间;操作时间',
  `updated_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '删除人',
  `deleted_time` datetime DEFAULT NULL COMMENT '删除时间',
  `deleted_flag` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'N' COMMENT '是否删除',
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '主键',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '备注',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '模块名称',
  `operation_type` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '操作类型;操作类型（0其它 1新增 2修改 3删除）',
  `method` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '方法名称',
  `request_method` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '请求方式',
  `business_type` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '业务类型;0其它 1后台用户 2手机端用户',
  `operation_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '请求URL',
  `operation_ip` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '主机地址',
  `operation_location` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '操作地点',
  `operation_param` text CHARACTER SET utf8mb4 COLLATE utf8mb4_bin COMMENT '请求参数',
  `operation_result` text CHARACTER SET utf8mb4 COLLATE utf8mb4_bin COMMENT '返回参数',
  `status` int NOT NULL DEFAULT '0' COMMENT '操作状态;操作状态（0正常 1异常）',
  `operation_person` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '操作人员;操作人名称',
  `message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_bin COMMENT '操作消息',
  `organization_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '组织ID',
  `department_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '部门',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin ROW_FORMAT=DYNAMIC COMMENT='系统操作日志';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_menu`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_menu` (
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '主键',
  `menu_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '菜单名称',
  `menu_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '菜单编码',
  `order_num` int NOT NULL DEFAULT '1' COMMENT '显示顺序;显示顺序',
  `visible` int DEFAULT '1' COMMENT '菜单状态;菜单状态（0显示 1隐藏）',
  `path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '路由地址;路由地址',
  `menu_type` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '菜单类型;菜单类型（M目录 C菜单 F按钮）',
  `permission` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '权限标识;权限标识',
  `status` int DEFAULT '1' COMMENT '菜单状态;菜单状态（0正常 1停用）',
  `icon` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '菜单图标;菜单图标',
  `app_menu` int DEFAULT '0' COMMENT '是否APP的菜单0是，1不是',
  `component` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '组件路径;组件路径',
  `params` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '路由参数;路由参数',
  `is_frame` int DEFAULT NULL COMMENT '是否为外链;是否为外链（0是 1否）',
  `is_cache` int DEFAULT NULL COMMENT '是否缓存;是否缓存（0缓存 1不缓存）',
  `parent_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '父级菜单;父级菜单',
  `organization_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '16663888968888889' COMMENT '组织ID',
  `tenant_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'system-default' COMMENT '租户号',
  `revision` int NOT NULL DEFAULT '1' COMMENT '乐观锁',
  `created_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '创建人',
  `created_time` datetime NOT NULL COMMENT '创建时间',
  `updated_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '备注',
  `deleted_time` datetime DEFAULT NULL COMMENT '删除时间',
  `deleted_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '删除人',
  `deleted_flag` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'N' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin ROW_FORMAT=COMPACT COMMENT='菜单';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_message`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_message` (
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '主键',
  `main_title` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '标题',
  `sub_title` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '二级标题',
  `message_type` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'NOTICE' COMMENT '类型, WORKFLOW-审批消息，ALARM-报警消息，NOTICE-通知',
  `message_person` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'SEL' COMMENT '发送人类型，ALL-所有人[限制组织内]，SEL-选定的人员',
  `detail_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '详情链接',
  `content` varchar(900) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '内容简介',
  `status` int NOT NULL DEFAULT '0' COMMENT '状态;（0未读 1已读）',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '备注',
  `organization_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '组织',
  `department_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '部门',
  `tenant_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'system-default' COMMENT '租户号',
  `revision` int NOT NULL DEFAULT '1' COMMENT '乐观锁',
  `created_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '创建人',
  `created_time` datetime NOT NULL COMMENT '创建时间',
  `updated_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '删除人',
  `deleted_time` datetime DEFAULT NULL COMMENT '删除时间',
  `deleted_flag` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'N' COMMENT '是否删除',
  `created_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin ROW_FORMAT=DYNAMIC COMMENT='系统消息通知';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_message_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_message_user` (
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '主键',
  `message_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '消息ID',
  `user_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '用户',
  `status` int NOT NULL DEFAULT '0' COMMENT '状态;（0未读 1已读）',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '备注',
  `tenant_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'system-default' COMMENT '租户号',
  `revision` int NOT NULL DEFAULT '1' COMMENT '乐观锁',
  `created_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '创建人',
  `created_time` datetime NOT NULL COMMENT '创建时间',
  `updated_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '删除人',
  `deleted_time` datetime DEFAULT NULL COMMENT '删除时间',
  `deleted_flag` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'N' COMMENT '是否删除',
  `nick_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `unique_user_message` (`message_id`,`user_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin ROW_FORMAT=DYNAMIC COMMENT='系统通知人员';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_notice`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_notice` (
  `tenant_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'system-default' COMMENT '租户号',
  `revision` int NOT NULL DEFAULT '1' COMMENT '乐观锁',
  `created_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '创建人',
  `created_time` datetime NOT NULL COMMENT '创建时间',
  `updated_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '删除人',
  `deleted_time` datetime DEFAULT NULL COMMENT '删除时间',
  `deleted_flag` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'N' COMMENT '是否删除',
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '主键',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '备注',
  `notice_title` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '公告标题',
  `notice_type` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '公告类型',
  `content` varchar(900) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '内容简介',
  `status` int NOT NULL DEFAULT '0' COMMENT '状态;（0正常 1停止）',
  `detail_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '详情链接',
  `organization_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '组织',
  `department_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '部门',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin ROW_FORMAT=COMPACT COMMENT='系统公告';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_openapi_call_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_openapi_call_log` (
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '主键',
  `tenant_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'system-default' COMMENT '租户号',
  `revision` int NOT NULL DEFAULT '1' COMMENT '乐观锁',
  `created_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '创建人（机器调用无登录态时为空）',
  `created_time` datetime DEFAULT NULL COMMENT '创建时间（代码显式写入）',
  `updated_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted_flag` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'N' COMMENT '是否删除',
  `deleted_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '删除人',
  `deleted_time` datetime DEFAULT NULL COMMENT '删除时间',
  `department_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '所属部门',
  `organization_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '所属组织（取自凭证，代码显式写入）',
  `remark` varchar(900) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '备注',
  `request_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '本次调用追踪号',
  `credential_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '凭证ID',
  `app_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '应用ID',
  `access_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'AccessKey',
  `endpoint` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '请求PATH',
  `http_method` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '请求METHOD',
  `action` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '归一化动作;data:list/data:read/data:add/data:edit/data:remove/flow:start/meta:list',
  `table_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '目标动态表编码',
  `biz_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '业务数据ID',
  `result` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'SUCCESS' COMMENT '结果;SUCCESS=成功,DENIED=鉴权失败,FAILED=业务失败,RATE_LIMITED=限流',
  `status_code` int DEFAULT NULL COMMENT 'HTTP状态码',
  `error_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '错误码',
  `error_msg` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '错误信息',
  `cost_ms` bigint DEFAULT NULL COMMENT '耗时毫秒',
  `client_ip` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '调用方IP',
  `nonce` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '防重放随机串',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_call_log_org_time` (`organization_id`,`created_time`) USING BTREE,
  KEY `idx_call_log_ak_time` (`access_key`,`created_time`) USING BTREE,
  KEY `idx_call_log_cred` (`credential_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='OpenAPI调用流水';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_openapi_credential`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_openapi_credential` (
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
  `department_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '所属部门',
  `organization_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '所属组织',
  `remark` varchar(900) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '备注',
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '数据标题',
  `app_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '归属应用ID（system_app_info.id）',
  `credential_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '凭证名称',
  `access_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'AccessKey，公开标识，请求头 X-Access-Key',
  `secret_cipher` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'Secret 的 AES/GCM 密文（base64）。⚠️ 不能改成 sha256：HMAC 要求服务端能还原密钥，见 commercialization.md 9.3',
  `secret_fingerprint` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'sha256(明文secret) hex，仅用于展示掩码与查重，不参与验签',
  `secret_prefix` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'Secret 前缀明文，列表展示用',
  `secret_last4` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'Secret 末4位明文，列表展示用',
  `scopes` json DEFAULT NULL COMMENT 'API范围 {"scopes":["data:read","data:write","meta:read","flow:start"]}',
  `table_grant` json DEFAULT NULL COMMENT '按表授权 {"grants":[{"tableCode":"xl_foo","actions":["list","add"]}]}',
  `bound_user_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '数据归属用户（写数据时补 created_by、发起流程时的发起人），不作为权限来源',
  `bound_department_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '数据归属部门',
  `status` int NOT NULL DEFAULT '1' COMMENT '状态;1=启用,0=停用',
  `expire_time` datetime DEFAULT NULL COMMENT '过期时间，空=永不过期',
  `rate_limit_per_minute` int DEFAULT '600' COMMENT '每分钟调用额度',
  `last_call_time` datetime DEFAULT NULL COMMENT '最近一次调用时间',
  `call_count` bigint DEFAULT '0' COMMENT '累计调用次数',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_openapi_access_key` (`access_key`) USING BTREE,
  KEY `idx_openapi_cred_app` (`app_id`) USING BTREE,
  KEY `idx_openapi_cred_org` (`organization_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='OpenAPI应用凭证';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_organization`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_organization` (
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '主键',
  `tenant_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'system-default' COMMENT '租户号',
  `revision` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '1' COMMENT '乐观锁',
  `created_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '创建人',
  `created_time` datetime NOT NULL COMMENT '创建时间',
  `updated_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted_flag` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'N' COMMENT '是否删除',
  `deleted_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '删除人',
  `deleted_time` datetime DEFAULT NULL COMMENT '删除时间',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '备注',
  `organization_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '组织名称',
  `order_num` int NOT NULL DEFAULT '1' COMMENT '排序',
  `leader_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '主管的ID;就是用户的ID',
  `status` int DEFAULT '1' COMMENT '状态;是否禁用  0是否，1是是',
  `organization_code` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '组织编码',
  `syn_type` int unsigned DEFAULT '0' COMMENT '同步类型;1同步钉钉，2同步企微，3自管理不同步任何第三方',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `unique_code` (`organization_code`) USING BTREE COMMENT '编码唯一'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin ROW_FORMAT=COMPACT COMMENT='组织';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_organization_app`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_organization_app` (
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '主键',
  `app_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '应用的ID',
  `status` int DEFAULT '0' COMMENT '菜单状态;菜单状态（0正常 1停用）',
  `organization_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '组织ID',
  `tenant_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'system-default' COMMENT '租户号',
  `revision` int NOT NULL DEFAULT '1' COMMENT '乐观锁',
  `created_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '创建人',
  `created_time` datetime NOT NULL COMMENT '创建时间',
  `updated_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted_time` datetime DEFAULT NULL COMMENT '删除时间',
  `deleted_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '删除人',
  `deleted_flag` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'N' COMMENT '是否删除',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin ROW_FORMAT=DYNAMIC COMMENT='组织授权的应用';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_organization_menu`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_organization_menu` (
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '主键',
  `menu_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '菜单名称',
  `app_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '应用的ID',
  `status` int DEFAULT '1' COMMENT '菜单状态;菜单状态（0正常 1停用）',
  `organization_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '组织ID',
  `tenant_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'system-default' COMMENT '租户号',
  `revision` int NOT NULL DEFAULT '1' COMMENT '乐观锁',
  `created_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '创建人',
  `created_time` datetime NOT NULL COMMENT '创建时间',
  `updated_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted_time` datetime DEFAULT NULL COMMENT '删除时间',
  `deleted_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '删除人',
  `deleted_flag` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'N' COMMENT '是否删除',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `organization_menu` (`menu_id`,`organization_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin ROW_FORMAT=COMPACT COMMENT='菜单';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_position`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_position` (
  `tenant_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'system-default' COMMENT '租户号',
  `revision` int NOT NULL DEFAULT '1' COMMENT '乐观锁',
  `created_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '创建人',
  `created_time` datetime NOT NULL COMMENT '创建时间',
  `updated_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted_flag` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'N' COMMENT '是否删除',
  `deleted_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '删除人',
  `deleted_time` datetime DEFAULT NULL COMMENT '删除时间',
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '主键',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '备注',
  `position_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '岗位名称',
  `position_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '岗位编码',
  `status` int DEFAULT '0' COMMENT '状态;是否禁用0是否，1是是',
  `order_num` int DEFAULT '1' COMMENT '排序值',
  `organization_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '组织ID;所属组织',
  `department_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '部门ID;所属部门',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin ROW_FORMAT=COMPACT COMMENT='岗位';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_role` (
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '主键',
  `tenant_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'system-default' COMMENT '租户号',
  `revision` int NOT NULL DEFAULT '1' COMMENT '乐观锁',
  `created_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '创建人',
  `created_time` datetime NOT NULL COMMENT '创建时间',
  `updated_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted_flag` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'N' COMMENT '是否删除',
  `deleted_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '删除人',
  `deleted_time` datetime DEFAULT NULL COMMENT '删除时间',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '备注',
  `data_scope` int DEFAULT '20' COMMENT '数据范围（50：全部数据权限 40：自定数据权限 20：本部门数据权限 30：本部门及以下数据权限 10: 仅本人数据权限）',
  `role_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '角色KEY',
  `status` int NOT NULL DEFAULT '1' COMMENT '状态;角色状态（0正常 1停用）',
  `order_num` int NOT NULL DEFAULT '1' COMMENT '排序值',
  `role_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '角色名称',
  `classification_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '角色分类ID',
  `department_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '部门ID',
  `organization_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '所属组织',
  `system_type` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'NORMAL' COMMENT '角色类型',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin ROW_FORMAT=COMPACT COMMENT='角色';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_role_group`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_role_group` (
  `tenant_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'system-default' COMMENT '租户号',
  `revision` int NOT NULL DEFAULT '1' COMMENT '乐观锁',
  `created_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '创建人',
  `created_time` datetime NOT NULL COMMENT '创建时间',
  `updated_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted_flag` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'N' COMMENT '是否删除',
  `deleted_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '删除人',
  `deleted_time` datetime DEFAULT NULL COMMENT '删除时间',
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '主键',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '备注',
  `group_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '组名',
  `group_type` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'CUSTOM' COMMENT '组的类型;SYSTEM 系统默认 CUSTOM 用户创建',
  `department_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '部门;所属部门',
  `organization_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '组织ID;默认的组织',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin ROW_FORMAT=COMPACT COMMENT='角色组';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_role_group_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_role_group_role` (
  `tenant_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'system-default' COMMENT '租户号',
  `revision` int NOT NULL DEFAULT '1' COMMENT '乐观锁',
  `created_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '创建人',
  `created_time` datetime NOT NULL COMMENT '创建时间',
  `updated_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '删除人',
  `deleted_time` datetime DEFAULT NULL COMMENT '删除时间',
  `deleted_flag` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'N' COMMENT '是否删除',
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '主键',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '备注',
  `group_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '组ID',
  `role_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '角色ID',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin ROW_FORMAT=COMPACT COMMENT='角色组与角色';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_role_menu`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_role_menu` (
  `tenant_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'system-default' COMMENT '租户号',
  `revision` int NOT NULL DEFAULT '1' COMMENT '乐观锁',
  `created_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '创建人',
  `created_time` datetime NOT NULL COMMENT '创建时间',
  `updated_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted_flag` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'N' COMMENT '是否删除',
  `deleted_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '删除人',
  `deleted_time` datetime DEFAULT NULL COMMENT '删除时间',
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '主键',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '备注',
  `role_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '角色ID',
  `menu_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '菜单ID',
  `permission` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '菜单的权限标识;后端通过标识查询角色授权，然后通过当前用户和角色匹配，找到此用户设置的服务部门，再查询数据。如果没有设置则只查询主部门的',
  `data_scope` int DEFAULT '20' COMMENT '数据范围（50：全部数据权限 40：自定数据权限 20：本部门数据权限 30：本部门及以下数据权限 10: 仅本人数据权限）',
  `app_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '应用的ID',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `role_menu_appid_idx` (`role_id`,`menu_id`,`app_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin ROW_FORMAT=COMPACT COMMENT='角色拥有的菜单';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_role_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_role_user` (
  `tenant_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'system-default' COMMENT '租户号',
  `revision` int NOT NULL DEFAULT '1' COMMENT '乐观锁',
  `created_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '创建人',
  `created_time` datetime NOT NULL COMMENT '创建时间',
  `updated_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted_flag` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'N' COMMENT '是否删除',
  `deleted_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '删除人',
  `deleted_time` datetime DEFAULT NULL COMMENT '删除时间',
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '主键',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '备注',
  `role_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '角色ID',
  `user_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '用户ID',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `role_user_idx` (`role_id`,`user_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin ROW_FORMAT=COMPACT COMMENT='角色拥有的用户';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_role_user_dept`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_role_user_dept` (
  `tenant_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'system-default' COMMENT '租户号',
  `revision` int NOT NULL DEFAULT '1' COMMENT '乐观锁',
  `created_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '创建人',
  `created_time` datetime NOT NULL COMMENT '创建时间',
  `updated_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted_flag` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'N' COMMENT '是否删除',
  `deleted_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '删除人',
  `deleted_time` datetime DEFAULT NULL COMMENT '删除时间',
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '主键',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '备注',
  `role_user_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '角色用户表的ID;角色用户表的ID',
  `organization_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '组织ID;所属组织',
  `department_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '服务的部门',
  `auth_way` int DEFAULT '1' COMMENT '授权的方式：2、自动 1、手动',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin ROW_FORMAT=COMPACT COMMENT='角色里用户的服务部门';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_schedule_instance`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_schedule_instance` (
  `instance_id` varchar(64) COLLATE utf8mb4_bin NOT NULL COMMENT 'ip+pid+启动时间',
  `role` varchar(10) COLLATE utf8mb4_bin NOT NULL DEFAULT 'WORKER' COMMENT 'LEADER/WORKER',
  `lease_until` datetime(3) NOT NULL COMMENT '心跳租约,每 10s 续 30s',
  `start_time` datetime NOT NULL,
  `stat_json` json DEFAULT NULL COMMENT '线程池水位/在跑任务数/本轮扫描耗时',
  PRIMARY KEY (`instance_id`),
  KEY `idx_lease` (`lease_until`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='调度/执行实例心跳';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_schedule_lock`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_schedule_lock` (
  `lock_key` varchar(64) COLLATE utf8mb4_bin NOT NULL COMMENT '锁名:scheduler / log-partition / quota-check ...',
  `holder` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  `lease_until` datetime(3) NOT NULL DEFAULT '1970-01-01 00:00:00.000',
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`lock_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='DB 租约锁,替代分布式锁中间件';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_schedule_log_archive`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_schedule_log_archive` (
  `id` bigint NOT NULL,
  `task_id` varchar(36) COLLATE utf8mb4_bin NOT NULL,
  `organization_id` varchar(36) COLLATE utf8mb4_bin NOT NULL,
  `fire_time` datetime(3) NOT NULL,
  `attempt` int NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_bin NOT NULL,
  `error_msg` varchar(2000) COLLATE utf8mb4_bin DEFAULT NULL,
  `result_digest` varchar(500) COLLATE utf8mb4_bin DEFAULT NULL,
  `archived_time` datetime NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_task` (`task_id`,`fire_time`),
  KEY `idx_time` (`archived_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='失败执行记录归档(保留12个月)';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_schedule_metric_snapshot`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_schedule_metric_snapshot` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `metric_name` varchar(64) COLLATE utf8mb4_bin NOT NULL COMMENT '指标名(schedule.*)',
  `tag_outcome` varchar(20) COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'executions 的 outcome 标签',
  `statistic` varchar(20) COLLATE utf8mb4_bin NOT NULL COMMENT 'COUNT/TOTAL_TIME/MAX/VALUE',
  `value` double NOT NULL,
  `snap_time` datetime(3) NOT NULL COMMENT '快照时刻',
  PRIMARY KEY (`id`),
  KEY `idx_name_time` (`metric_name`,`snap_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='调度指标快照(D5-C二期方案B:应用内自采集,报警列表指标趋势数据源)';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_schedule_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_schedule_task` (
  `id` varchar(36) COLLATE utf8mb4_bin NOT NULL COMMENT '主键UUID',
  `tenant_id` varchar(255) COLLATE utf8mb4_bin NOT NULL DEFAULT 'system-default' COMMENT '租户号(基线列,平台实际隔离维度是 organization_id)',
  `revision` int NOT NULL DEFAULT '1' COMMENT '乐观锁',
  `created_by` varchar(36) COLLATE utf8mb4_bin NOT NULL COMMENT '创建人',
  `created_time` datetime NOT NULL COMMENT '创建时间',
  `updated_by` varchar(36) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted_flag` varchar(1) COLLATE utf8mb4_bin NOT NULL DEFAULT 'N' COMMENT '是否删除',
  `deleted_by` varchar(36) COLLATE utf8mb4_bin DEFAULT NULL,
  `deleted_time` datetime(3) NOT NULL DEFAULT '1970-01-01 00:00:00.000' COMMENT '删除时刻;未删固定为纪元值,参与唯一键(注4)',
  `department_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '部门(SYSTEM任务为空串)',
  `organization_id` varchar(36) COLLATE utf8mb4_bin NOT NULL COMMENT '组织(配额/数据范围维度)',
  `remark` varchar(900) COLLATE utf8mb4_bin DEFAULT NULL,
  `task_code` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '应用内稳定编码,随应用包迁移;ONCE 可空(uk_code 对 NULL 不去重)',
  `title` varchar(255) COLLATE utf8mb4_bin NOT NULL COMMENT '任务名称',
  `app_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '所属应用(SYSTEM任务为空串)',
  `source` varchar(20) COLLATE utf8mb4_bin NOT NULL COMMENT 'WORKFLOW/DESIGNER/DESIGNER_DATA/SYSTEM/OPENAPI/MANUAL',
  `biz_key` varchar(128) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '业务关联键;无键时由代码生成 uuid 保证唯一索引生效',
  `trigger_type` varchar(20) COLLATE utf8mb4_bin NOT NULL COMMENT 'CRON/FIXED_DELAY/ONCE',
  `cron_expr` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '6位秒级 cron,trigger_type=CRON 必填',
  `interval_seconds` int DEFAULT NULL COMMENT '间隔秒,trigger_type=FIXED_DELAY 必填(最小 60)',
  `period_seconds` bigint DEFAULT NULL COMMENT '预估周期长度(秒),保存时由 cron 连续两次触发差值算出;FIXED_DELAY=interval_seconds;ONCE 为空',
  `jitter_seconds` int NOT NULL DEFAULT '0' COMMENT '触发抖动 0-60s,整点任务用于削峰(确定性 hash,已计入 next_fire_time)',
  `misfire_policy` varchar(20) COLLATE utf8mb4_bin NOT NULL DEFAULT 'COMPENSATE' COMMENT 'COMPENSATE补偿一次/SKIP跳下次',
  `misfire_window_seconds` int NOT NULL DEFAULT '300' COMMENT '补偿窗口:错过超过此值不再补(周期任务另受一个周期上限约束)',
  `concurrent_policy` varchar(20) COLLATE utf8mb4_bin NOT NULL DEFAULT 'FORBID' COMMENT 'FORBID上次未完则跳过本次/SKIP不记跳过;QUEUE 未启用',
  `timeout_seconds` int NOT NULL DEFAULT '300' COMMENT '单次执行硬超时,上限 3600',
  `max_retry` tinyint NOT NULL DEFAULT '3' COMMENT '失败重试次数上限,仅 ONCE/WORKFLOW 用到(退避 1m/5m/30m);周期任务按 period_seconds 决定补不补',
  `action_type` varchar(20) COLLATE utf8mb4_bin NOT NULL COMMENT '动作类型 WORKFLOW_TIMEOUT/GROOVY_SCRIPT/FORM_DATA_OP/HTTP_CALL/MESSAGE_NOTIFY/BEAN',
  `action_config` json DEFAULT NULL COMMENT '动作参数(脚本/url/通知模板/数据操作);表名一律存物理名',
  `biz_context` json DEFAULT NULL COMMENT '注册时上下文快照(数据主键/节点编码等),一次性任务不依赖业务现值',
  `database_name` varchar(64) COLLATE utf8mb4_bin NOT NULL DEFAULT 'master' COMMENT '业务库名注册时快照;执行时优先按 organization_id 现场解析、取不到才用它兜底',
  `callback_url` varchar(500) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '执行终态回调,仅 OPENAPI 来源生效(P1,界面标注未启用)',
  `status` varchar(20) COLLATE utf8mb4_bin NOT NULL DEFAULT 'READY' COMMENT 'READY/PAUSED/TRIGGERED/RUNNING/DONE/CANCELLED',
  `next_fire_time` datetime(3) DEFAULT NULL COMMENT '下次触发时间(DB时区),READY 态非空',
  `pending_next_fire_time` datetime(3) DEFAULT NULL COMMENT '本轮执行完要提升为 next_fire_time 的值(周期任务用),避免执行失败丢周期',
  `pending_change` json DEFAULT NULL COMMENT 'P1-4 在途改配置暂存:TRIGGERED/RUNNING 期间的编辑/启停登记于此,本轮结束回写后应用并清空',
  `last_fire_time` datetime(3) DEFAULT NULL,
  `owner_instance` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '抢占该任务的调度实例',
  `lease_until` datetime(3) DEFAULT NULL COMMENT '调度租约,过期由对账回收',
  `running_instance` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '正在执行的实例',
  `running_since` datetime(3) DEFAULT NULL COMMENT '开始执行时刻,僵尸判定用',
  `trigger_count` bigint NOT NULL DEFAULT '0',
  `fail_count` bigint NOT NULL DEFAULT '0' COMMENT '连续失败次数,成功清零,达 10 自动 PAUSED+告警',
  `last_error` varchar(1000) COLLATE utf8mb4_bin DEFAULT NULL,
  `current_attempt` int NOT NULL DEFAULT '1' COMMENT '当前执行轮次(幂等键 attempt 来源):正常触发为 1,失败重试递增,成功/跳过后重置',
  `version` bigint NOT NULL DEFAULT '0' COMMENT '配置版本号:仅用户改配置时+1,调度抢占不改此值',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_biz` (`organization_id`,`source`,`biz_key`,`deleted_time`),
  UNIQUE KEY `uk_code` (`app_id`,`task_code`,`deleted_time`),
  KEY `idx_trigger` (`deleted_flag`,`status`,`next_fire_time`),
  KEY `idx_org` (`organization_id`,`app_id`,`source`,`status`),
  KEY `idx_lease` (`status`,`lease_until`),
  KEY `idx_running` (`status`,`running_since`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='定时任务定义与调度状态';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_schedule_task_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_schedule_task_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_id` varchar(36) COLLATE utf8mb4_bin NOT NULL,
  `organization_id` varchar(36) COLLATE utf8mb4_bin NOT NULL,
  `app_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '',
  `source` varchar(20) COLLATE utf8mb4_bin NOT NULL,
  `fire_time` datetime(3) NOT NULL COMMENT '计划触发时间(未来分区键),时间筛选一律用此列',
  `attempt` int NOT NULL DEFAULT '1' COMMENT '第几次尝试;跳过类固定为 0,与执行链不撞 uk_idem',
  `task_version` bigint NOT NULL COMMENT '执行时的配置版本',
  `action_snapshot` json DEFAULT NULL COMMENT '执行时的 action_config 快照',
  `status` varchar(20) COLLATE utf8mb4_bin NOT NULL COMMENT 'RUNNING/SUCCESS/FAILED/TIMEOUT/SKIPPED_MISFIRE/SKIPPED_CONCURRENT/SKIPPED_POOL/CANCELLED',
  `executor` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '执行实例标识',
  `start_time` datetime(3) DEFAULT NULL,
  `end_time` datetime(3) DEFAULT NULL,
  `cost_ms` int DEFAULT NULL,
  `error_msg` varchar(2000) COLLATE utf8mb4_bin DEFAULT NULL,
  `result_digest` varchar(500) COLLATE utf8mb4_bin DEFAULT NULL,
  `trace_id` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  PRIMARY KEY (`id`,`fire_time`),
  UNIQUE KEY `uk_idem` (`task_id`,`fire_time`,`attempt`),
  KEY `idx_org_status` (`organization_id`,`status`,`fire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='定时任务执行日志(全量落库,保留3个月)'
/*!50500 PARTITION BY RANGE  COLUMNS(fire_time)
(PARTITION p202610 VALUES LESS THAN ('2026-11-01') ENGINE = InnoDB,
 PARTITION p202611 VALUES LESS THAN ('2026-12-01') ENGINE = InnoDB,
 PARTITION p202612 VALUES LESS THAN ('2027-01-01') ENGINE = InnoDB,
 PARTITION p202701 VALUES LESS THAN ('2027-02-01') ENGINE = InnoDB) */;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_schedule_task_queue`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_schedule_task_queue` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_id` varchar(36) COLLATE utf8mb4_bin NOT NULL COMMENT '任务id',
  `fire_time` datetime(3) NOT NULL COMMENT '错过的计划触发时刻(保留原相位,到点补跑)',
  `status` varchar(20) COLLATE utf8mb4_bin NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/DONE',
  `queued_time` datetime(3) NOT NULL COMMENT '入队时刻',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_fire` (`task_id`,`fire_time`),
  KEY `idx_poll` (`task_id`,`status`,`fire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='QUEUE 并发策略的错过轮次队列(§7.4 P2,落库防重启丢排队)';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_social_app_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_social_app_config` (
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `app_id` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'appId',
  `secret` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '密钥',
  `source` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '来源',
  `app_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '应用的编码;区分哪个应用登录',
  `app_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '应用的名称;解绑时区分解绑哪一个',
  `app_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '应用的url',
  `examine_user` varchar(5) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT 'N' COMMENT '是否需要审核用户',
  `agent_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '创建应用的agentId',
  `corp_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '创建应用的企业ID',
  `scheme` varchar(125) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '创建应用的scheme',
  `role_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '角色',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
  `created_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `created_time` datetime NOT NULL,
  `updated_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
  `updated_time` datetime DEFAULT NULL,
  `deleted_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
  `deleted_time` datetime DEFAULT NULL,
  `deleted_flag` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'N',
  `organization_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '所属组织',
  `department_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '部门',
  `revision` int NOT NULL DEFAULT '1' COMMENT '乐观锁',
  `tenant_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'system-default' COMMENT '租户',
  `syn_employee` int DEFAULT NULL COMMENT '是否同步员工',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin ROW_FORMAT=COMPACT COMMENT='第三方登录配置';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_social_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_social_user` (
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `uuid` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '第三方系统的唯一ID',
  `source` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '第三方用户来源;GITHUB、GITEE、QQ',
  `app_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '应用的编码;区分哪个应用登录',
  `access_token` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '用户的授权令牌',
  `expire_in` int DEFAULT NULL COMMENT '第三方用户的授权令牌的有效期',
  `refresh_token` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '刷新令牌',
  `open_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '第三方用户的;open;id',
  `uid` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '第三方用户的;ID',
  `access_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '个别平台的授权信息',
  `union_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '第三方用户的;union;id',
  `scope` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '第三方用户授予的权限',
  `token_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '个别平台的授权信息',
  `id_token` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'id;token',
  `mac_algorithm` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '小米平台用户的附带属性',
  `mac_key` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '小米平台用户的附带属性',
  `code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '用户的授权code',
  `oauth_token` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'Twitter平台用户的附带属性',
  `oauth_token_secret` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'Twitter平台用户的附带属性',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
  `created_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `created_time` datetime NOT NULL,
  `updated_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
  `updated_time` datetime DEFAULT NULL,
  `deleted_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
  `deleted_time` datetime DEFAULT NULL,
  `deleted_flag` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'N' COMMENT '删除标识',
  `organization_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '所属组织',
  `department_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '所属部门',
  `revision` int NOT NULL DEFAULT '1',
  `tenant_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'system-default',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin ROW_FORMAT=COMPACT COMMENT='第三方用户';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_sso_oidc_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_sso_oidc_config` (
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
  `department_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '所属部门',
  `organization_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '所属组织',
  `remark` varchar(900) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '备注',
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '数据标题',
  `config_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '渠道编码，登录页标识（/auth/oidc/authorize/{configCode}）',
  `config_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '渠道名称',
  `icon` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '图标',
  `issuer` varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'Issuer，用于校验 id_token 的 iss',
  `discovery_url` varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '发现文档地址，留空则由 issuer 拼 /.well-known/openid-configuration',
  `authorize_url` varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '授权端点（留空则取发现文档）',
  `token_url` varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '令牌端点',
  `userinfo_url` varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '用户信息端点',
  `jwks_uri` varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'JWKS地址，用于验签 id_token',
  `client_id` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '客户端ID',
  `client_secret_cipher` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '客户端密钥密文（授权码换令牌用；公共客户端可空）',
  `client_secret_prefix` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '密钥前缀明文，展示用',
  `client_secret_last4` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '密钥末4位明文，展示用',
  `scopes` varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT 'openid profile email' COMMENT '请求scope，空格分隔',
  `redirect_uri` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '回调地址，需与IdP登记一致',
  `claim_mapping` json DEFAULT NULL COMMENT '声明映射 {"externalUserId":"sub","name":"nickname","nickName":"name","email":"email","phone":"phone_number","department":"dept"}',
  `auto_register` int NOT NULL DEFAULT '0' COMMENT '首次登录是否自动建用户;1=建并挂默认角色,0=仅允许已绑定用户',
  `default_role_ids` varchar(900) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '自动建用户时授予的角色ID，逗号分隔',
  `status` int NOT NULL DEFAULT '1' COMMENT '状态;1=启用,0=停用',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_sso_config_code` (`config_code`) USING BTREE,
  KEY `idx_sso_config_org` (`organization_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='SSO单点登录OIDC配置';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_user_app_menu`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_user_app_menu` (
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '主键',
  `user_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '用户的ID',
  `menu_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '关联的菜单',
  `status` int NOT NULL COMMENT '状态',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
  `tenant_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'system-default' COMMENT '租户号',
  `revision` int NOT NULL DEFAULT '1' COMMENT '乐观锁',
  `created_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '创建人',
  `created_time` datetime NOT NULL COMMENT '创建时间',
  `updated_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted_flag` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'N' COMMENT '是否删除',
  `deleted_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '删除人',
  `deleted_time` datetime DEFAULT NULL COMMENT '删除时间',
  `organization_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '组织ID',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin ROW_FORMAT=DYNAMIC COMMENT='用户收藏的常用功能菜单';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_user_base`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_user_base` (
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '主键',
  `login_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '登录名',
  `nick_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '昵称',
  `user_type` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'default' COMMENT '用户类型;用户类型（default系统用户）',
  `email` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '邮箱',
  `phone` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '手机',
  `password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '密码',
  `password_v2` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'F4 封存口令摘要（AES-GCM，挑战登录用；未迁移为空）',
  `gender` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT '2' COMMENT '性别;用户性别（0男1女 2未知）',
  `avatar` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '头像',
  `status` int NOT NULL DEFAULT '0' COMMENT '状态;取值见 UserStatus 枚举（1正常 3停用 5审核 Y删除）',
  `identity_num` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '身份证号;身份证号',
  `organization_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '所属组织ID;所属组织ID[主组织必须存在]',
  `department_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '所属部门ID;所属部门ID[主部门:必须存在]',
  `tenant_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'system-default' COMMENT '租户号',
  `deleted_time` datetime DEFAULT NULL COMMENT '删除时间',
  `revision` int NOT NULL DEFAULT '1' COMMENT '乐观锁',
  `created_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '创建人',
  `created_time` datetime NOT NULL COMMENT '创建时间',
  `updated_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted_flag` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'N' COMMENT '是否删除',
  `deleted_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '删除人',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `login_status_index` (`login_name`,`status`,`organization_id`,`department_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin ROW_FORMAT=COMPACT COMMENT='用户';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_user_department`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_user_department` (
  `tenant_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'system-default' COMMENT '租户号',
  `revision` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '1' COMMENT '乐观锁',
  `created_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '创建人',
  `created_time` datetime NOT NULL COMMENT '创建时间',
  `updated_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted_flag` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'N' COMMENT '是否删除',
  `deleted_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '删除人',
  `deleted_time` datetime DEFAULT NULL COMMENT '删除时间',
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '主键',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '备注',
  `user_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '用户ID',
  `organization_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '组织ID;此数据所属组织',
  `department_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '部门ID;所属部门',
  `is_main` int NOT NULL DEFAULT '0' COMMENT '是否主部门(0 否，1是)，目前主部门放在了user表里',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin ROW_FORMAT=DYNAMIC COMMENT='用户兼职部门';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_user_group`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_user_group` (
  `tenant_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'system-default' COMMENT '租户号',
  `revision` int NOT NULL DEFAULT '1' COMMENT '乐观锁',
  `created_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '创建人',
  `created_time` datetime NOT NULL COMMENT '创建时间',
  `updated_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '删除人',
  `deleted_time` datetime DEFAULT NULL COMMENT '删除时间',
  `deleted_flag` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'N' COMMENT '是否删除',
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '主键',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '备注',
  `group_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '组编码',
  `group_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '组名',
  `group_type` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'SYSTEM' COMMENT '类型;SYSTEM 系统默认 CUSTOM 用户创建',
  `organization_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '组织ID',
  `department_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '部门ID',
  `common_group_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '公共组的ID',
  `status` int NOT NULL DEFAULT '1' COMMENT '用户组状态（1正常 3停用）',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin ROW_FORMAT=COMPACT COMMENT='用户组';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_user_group_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_user_group_user` (
  `tenant_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'system-default' COMMENT '租户号',
  `revision` int NOT NULL DEFAULT '1' COMMENT '乐观锁',
  `created_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '创建人',
  `created_time` datetime NOT NULL COMMENT '创建时间',
  `updated_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '删除人',
  `deleted_time` datetime DEFAULT NULL COMMENT '删除时间',
  `deleted_flag` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'N' COMMENT '是否删除',
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '主键',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '备注',
  `group_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '用户组ID',
  `user_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '用户ID',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin ROW_FORMAT=COMPACT COMMENT='用户组与用户';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_user_position`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_user_position` (
  `tenant_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'system-default' COMMENT '租户号',
  `revision` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '1' COMMENT '乐观锁',
  `created_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '创建人',
  `created_time` datetime NOT NULL COMMENT '创建时间',
  `updated_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted_flag` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'N' COMMENT '是否删除',
  `deleted_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '删除人',
  `deleted_time` datetime DEFAULT NULL COMMENT '删除时间',
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '主键',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '备注',
  `user_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '用户ID',
  `position_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '岗位ID',
  `is_main` int(1) unsigned zerofill NOT NULL DEFAULT '0' COMMENT '是否主岗位(0 否，1是)',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin ROW_FORMAT=COMPACT COMMENT='用户所属岗位';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_user_service_org`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_user_service_org` (
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '主键',
  `user_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '用户ID;用户ID',
  `organization_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '组织ID;兼职的组织ID',
  `department_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '部门ID;兼职部门ID',
  `data_scope` int NOT NULL DEFAULT '20' COMMENT '数据范围(50：全部数据权限 40：自定数据权限 20：本部门数据权限 30：本部门及以下数据权限 10: 仅本人数据权限\n)',
  `tenant_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'system-default' COMMENT '租户号',
  `revision` int NOT NULL DEFAULT '1' COMMENT '乐观锁',
  `created_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '创建人',
  `created_time` datetime NOT NULL COMMENT '创建时间',
  `updated_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted_flag` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'N' COMMENT '是否删除',
  `deleted_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '删除人',
  `deleted_time` datetime DEFAULT NULL COMMENT '删除时间',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `user_org_unique` (`user_id`,`organization_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin ROW_FORMAT=COMPACT COMMENT='用户服务组织';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_user_social_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_user_social_user` (
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '主键;',
  `user_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '系统用户ID',
  `social_user_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '社交用户ID',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
  `created_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '创建人;',
  `created_time` datetime NOT NULL COMMENT '创建时间;',
  `updated_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '更新人;',
  `updated_time` datetime DEFAULT NULL COMMENT '更新时间;',
  `deleted_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '删除人;',
  `deleted_time` datetime DEFAULT NULL COMMENT '删除时间;',
  `deleted_flag` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'N' COMMENT '是否删除;',
  `tenant_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'system-default' COMMENT '租户号;',
  `revision` int NOT NULL DEFAULT '1' COMMENT '乐观锁;',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `social_user_index` (`user_id`,`social_user_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin ROW_FORMAT=COMPACT COMMENT='第三方用户与系统用户的关联';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_webhook_delivery_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_webhook_delivery_log` (
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '主键',
  `tenant_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'system-default' COMMENT '租户号',
  `revision` int NOT NULL DEFAULT '1' COMMENT '乐观锁',
  `created_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '创建人',
  `created_time` datetime DEFAULT NULL COMMENT '创建时间（代码显式写入）',
  `updated_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted_flag` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'N' COMMENT '是否删除',
  `deleted_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '删除人',
  `deleted_time` datetime DEFAULT NULL COMMENT '删除时间',
  `department_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '所属部门',
  `organization_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '所属组织（代码显式写入）',
  `remark` varchar(900) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '备注',
  `event_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '事件ID（system_webhook_event.id）',
  `subscription_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '订阅ID',
  `attempt` int DEFAULT '1' COMMENT '第几次尝试',
  `url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '实际请求地址',
  `status_code` int DEFAULT NULL COMMENT '响应状态码',
  `success` int DEFAULT '0' COMMENT '是否成功;1=成功,0=失败',
  `response_brief` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '响应内容摘要（截断）',
  `error_msg` varchar(900) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '失败原因',
  `cost_ms` bigint DEFAULT NULL COMMENT '耗时毫秒',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_webhook_del_event` (`event_id`) USING BTREE,
  KEY `idx_webhook_del_sub_time` (`subscription_id`,`created_time`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='Webhook投递记录';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_webhook_event`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_webhook_event` (
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '主键',
  `tenant_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'system-default' COMMENT '租户号',
  `revision` int NOT NULL DEFAULT '1' COMMENT '乐观锁',
  `created_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '创建人（业务写入人，异步线程为空）',
  `created_time` datetime DEFAULT NULL COMMENT '创建时间（代码显式写入）',
  `updated_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted_flag` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'N' COMMENT '是否删除',
  `deleted_by` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '删除人',
  `deleted_time` datetime DEFAULT NULL COMMENT '删除时间',
  `department_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '所属部门',
  `organization_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '所属组织（代码显式写入）',
  `remark` varchar(900) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '备注',
  `subscription_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '订阅ID（一个业务变更按匹配到的订阅扇出成多行，重试状态因此互不影响）',
  `event_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '事件类型',
  `table_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '动态表编码',
  `biz_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '业务数据ID',
  `payload` json NOT NULL COMMENT '事件内容（投递时原样作为请求体）',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'PENDING' COMMENT '状态;PENDING=待投递,DELIVERING=投递中,SUCCESS=已送达,FAILED=待重试,DEAD=已放弃',
  `retry_count` int DEFAULT '0' COMMENT '已重试次数',
  `next_retry_time` datetime DEFAULT NULL COMMENT '下次可投递时间（退避）',
  `last_error` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '最近一次失败原因',
  `delivered_time` datetime DEFAULT NULL COMMENT '成功送达时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_webhook_evt_scan` (`status`,`next_retry_time`) USING BTREE,
  KEY `idx_webhook_evt_sub` (`subscription_id`) USING BTREE,
  KEY `idx_webhook_evt_org` (`organization_id`,`event_type`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='Webhook事件表（outbox：业务事务提交后异步落库并投递）';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_webhook_subscription`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_webhook_subscription` (
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
  `department_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '所属部门',
  `organization_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '所属组织',
  `remark` varchar(900) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '备注',
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '数据标题',
  `app_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '归属应用ID',
  `subscription_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '订阅名称',
  `url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '回调地址',
  `http_method` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT 'POST' COMMENT '请求方法',
  `secret_cipher` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '签名密钥密文（HMAC-SHA256，头部 X-Webhook-Signature）',
  `secret_prefix` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '密钥前缀明文，展示用',
  `secret_last4` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '密钥末4位明文，展示用',
  `events` json DEFAULT NULL COMMENT '订阅事件 {"events":["dynamic.data.created","flow.instance.started"]}',
  `table_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '仅该表的事件，空=本组织全部表',
  `headers` json DEFAULT NULL COMMENT '附加请求头 {"headers":{"X-Tenant":"..."}}',
  `ssl_verify` int NOT NULL DEFAULT '1' COMMENT '是否校验TLS证书;1=校验,0=不校验（内网自签场景）',
  `timeout_ms` int DEFAULT '5000' COMMENT '连接/读取超时毫秒',
  `max_retry` int DEFAULT '5' COMMENT '最大重试次数',
  `status` int NOT NULL DEFAULT '1' COMMENT '状态;1=启用,0=停用',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_webhook_sub_org` (`organization_id`) USING BTREE,
  KEY `idx_webhook_sub_table` (`table_code`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='Webhook事件订阅';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- ----------------------------
-- 数据库结构变更台账（ROADMAP 1.14）：init 基线之后的增量脚本在此登记，终结升级脚本漂移。
-- 流程见 sql/README.md「迁移台账」节：apply 前先核对脚本 SHA-256，apply 后 INSERT 登记。
-- ----------------------------
CREATE TABLE IF NOT EXISTS `platform_schema_history` (
  `id` varchar(36) NOT NULL COMMENT '主键',
  `version` varchar(32) NOT NULL COMMENT '版本号（V+时间戳，如 V202611081900）',
  `script_name` varchar(200) NOT NULL COMMENT '迁移脚本文件名',
  `dialect` varchar(16) NOT NULL COMMENT 'mysql/sqlite',
  `checksum` varchar(64) DEFAULT NULL COMMENT '脚本 SHA-256（apply 前核对）',
  `applied_by` varchar(64) NOT NULL DEFAULT 'manual' COMMENT '执行人',
  `applied_at` datetime NOT NULL COMMENT '执行时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_schema_script` (`script_name`, `dialect`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据库结构变更台账';

INSERT INTO `platform_schema_history`
VALUES ('10000000000000000001', 'baseline', 'init.sql 全量基线（97表/390菜单+种子，11-09 口径合并）', 'mysql', NULL, 'init', '2026-11-08 19:00:00');

INSERT INTO `system_organization` VALUES ('16663888968888889','system-default','1','188666666666','2022-11-16 14:41:05','188666666666','2023-04-17 14:58:23','N',NULL,NULL,'不要删除。','系统企业',1,'188666666666',1,'default',0);
INSERT INTO `system_department` VALUES ('system-default',1,'188666666666','2022-11-23 17:01:04',NULL,NULL,'N',NULL,NULL,'155555555555','','','开发部',0,'188666666666',1,'16663888968888889','NORMAL',0,NULL),('system-default',1,'188666666666','2022-11-25 13:33:33','188666666666','2024-07-18 11:19:21','N',NULL,NULL,'16663888968888889','','','工程部',8,'',1,'16663888968888889','SYSTEM',0,NULL),('system-default',1,'188666666666','2023-09-14 11:55:22',NULL,NULL,'N',NULL,NULL,'1702169136598220802','','','设计组',0,'',1,'16663888968888889','NORMAL',0,NULL),('system-default',1,'188666666666','2023-09-25 15:49:54',NULL,NULL,'N',NULL,NULL,'1706214426871541762','','','项目实施部',0,'1677150163900211202',1,'16663888968888889','NORMAL',0,NULL);
INSERT INTO `system_user_base` VALUES ('188666666666','admin','系统管理员','default',NULL,NULL,'$2a$10$SRK9EBHXmAmqPCQ7BtNkN.guFmO6bkcvD6iM/bvWFuA1.xNqR0cNy',NULL,'1',NULL,1,NULL,'16663888968888889','155555555555','system-default',NULL,1,'188666666666','2022-11-06 14:49:29','188666666666','2024-02-28 15:43:08','N',NULL,NULL);
INSERT INTO `system_role` VALUES ('12263522227777777','system-default',1,'188666666666','2022-11-18 16:22:15',NULL,NULL,'N',NULL,NULL,NULL,20,'SuperRole',1,1,'组织管理员','1','155555555555','16663888968888889','SYSTEM'),('12263567888888','system-default',1,'188666666666','2023-12-01 16:22:15',NULL,NULL,'N',NULL,NULL,NULL,20,'SuperPlatformRole',1,1,'平台管理员','1','155555555555','16663888968888889','SYSTEM');
INSERT INTO `system_role_user` VALUES ('system-default',1,'188666666666','2022-11-18 16:44:39','188666666666','2022-12-01 10:39:08','N','188666666666','2022-12-01 10:39:08','1',NULL,'12263522227777777','188666666666'),('system-default',1,'188666666666','2022-11-18 16:44:39','188666666666','2022-12-01 10:39:08','N','188666666666','2022-12-01 10:39:08','66887799',NULL,'12263567888888','188666666666');
INSERT INTO `system_user_service_org` VALUES ('1114933510994120716','188666666666','16663888968888889','155555555555',50,'system-default',1,'188666666666','2022-11-18 16:36:44',NULL,NULL,'N',NULL,NULL,NULL);
INSERT INTO `system_app_info` VALUES ('1768482874137485238','PLATFORM','PLATFORM','evaluation','平台管理','PLATFORM',NULL,'system','SYSTEM','',1,30,NULL,'system-default',1,'188666666666','2024-03-15 11:42:29','188666666666','2024-06-06 14:43:01','N',NULL,NULL,'16663888968888889'),('1768482874137485248','SYSTEM','SYSTEM','evaluation','系统管理','SYSTEM',NULL,'system','SYSTEM','',1,25,NULL,'system-default',1,'188666666666','2024-03-15 11:42:29','188666666666','2024-06-07 15:28:30','N',NULL,NULL,'16663888968888889');
INSERT INTO `system_menu` VALUES ('12fe5bef6f2511ee983f00163e085602','菜单管理','system_menu',140,1,'/system/menu','C','system:menu:list',1,'icon-default',0,'self',NULL,0,0,'1798986040628969473','16663888968888889','system-default',1,'188666666666','2023-10-20 16:45:51','188666666666','2024-06-07 15:22:41',NULL,NULL,NULL,'N'),('12fe82156f2511ee983f00163e085602','菜单列表','',1,1,'','F','system:menu:list',1,'icon-default',0,'',NULL,0,0,'12fe5bef6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:45:51',NULL,NULL,NULL,NULL,NULL,'N'),('12fe82166f2511ee983f00163e085602','菜单详情','',1,1,'','F','system:menu:query',1,'icon-default',0,'',NULL,0,0,'12fe5bef6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:45:51',NULL,NULL,NULL,NULL,NULL,'N'),('12fea83a6f2511ee983f00163e085602','菜单新增','',1,1,'','F','system:menu:add',1,'icon-default',0,'',NULL,0,0,'12fe5bef6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:45:51',NULL,NULL,NULL,NULL,NULL,'N'),('12fea83b6f2511ee983f00163e085602','菜单修改','',1,1,'','F','system:menu:edit',1,'icon-default',0,'',NULL,0,0,'12fe5bef6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:45:51',NULL,NULL,NULL,NULL,NULL,'N'),('12fece606f2511ee983f00163e085602','菜单删除','',1,1,'','F','system:menu:remove',1,'icon-default',0,'',NULL,0,0,'12fe5bef6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:45:51',NULL,NULL,NULL,NULL,NULL,'N'),('12fef4856f2511ee983f00163e085602','菜单导入','',1,1,'','F','system:menu:import',1,'icon-default',0,'',NULL,0,0,'12fe5bef6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:45:51',NULL,NULL,NULL,NULL,NULL,'N'),('12fef4866f2511ee983f00163e085602','菜单导出','',1,1,'','F','system:menu:export',1,'icon-default',0,'',NULL,0,0,'12fe5bef6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:45:51',NULL,NULL,NULL,NULL,NULL,'N'),('1391b0aebff811f18fc3a5e694323b94','联动规则配置','dynamic_form_linkage',81,1,NULL,'F','dynamic:form:linkage',1,NULL,0,NULL,NULL,NULL,NULL,'0','16663888968888889','system-default',1,'system','2026-10-04 21:32:42',NULL,NULL,'高级设置的联动规则管理',NULL,NULL,'N'),('1391b176bff811f18fc3a5e694323b94','表单外链配置','dynamic_form_share',82,1,NULL,'F','dynamic:form:share',1,NULL,0,NULL,NULL,NULL,NULL,'0','16663888968888889','system-default',1,'system','2026-10-04 21:32:42',NULL,NULL,'高级设置的表单外链管理',NULL,NULL,'N'),('1391b1bcbff811f18fc3a5e694323b94','导入导出配置','dynamic_form_io_config',84,1,NULL,'F','dynamic:form:io:config',1,NULL,0,NULL,NULL,NULL,NULL,'0','16663888968888889','system-default',1,'system','2026-10-04 21:32:42',NULL,NULL,'高级设置的导入导出策略配置',NULL,NULL,'N'),('1391b1dabff811f18fc3a5e694323b94','数据打印','dynamic_data_print',85,1,NULL,'F','dynamic:data:print',1,NULL,0,NULL,NULL,NULL,NULL,'0','16663888968888889','system-default',1,'system','2026-10-04 21:32:42',NULL,NULL,'数据级：按模板打印数据',NULL,NULL,'N'),('1391b1eebff811f18fc3a5e694323b94','数据导入','dynamic_data_import',86,1,NULL,'F','dynamic:data:import',1,NULL,0,NULL,NULL,NULL,NULL,'0','16663888968888889','system-default',1,'system','2026-10-04 21:32:42',NULL,NULL,'数据级：Excel 导入数据',NULL,NULL,'N'),('1391b20cbff811f18fc3a5e694323b94','数据导出','dynamic_data_export',87,1,NULL,'F','dynamic:data:export',1,NULL,0,NULL,NULL,NULL,NULL,'0','16663888968888889','system-default',1,'system','2026-10-04 21:32:42',NULL,NULL,'数据级：导出数据 Excel（脱敏+审计）',NULL,NULL,'N'),('1663888968294588891','系统管理','system',500,1,'/system','M','system:system:list',1,'carbon:cloud-service-management',0,'basic',NULL,0,0,'0','16663888968888889','system-default',1,'188666666666','2022-11-10 16:33:44',NULL,NULL,NULL,NULL,NULL,'N'),('1798986040628969473','平台管理','platform',100,1,'/platform','M','',1,'carbon:cloud-service-management',0,'basic','',0,0,'0','16663888968888889','system-default',1,'188666666666','2024-06-07 15:51:10','188666666666','2024-06-20 15:37:50',NULL,NULL,NULL,'N'),('1803694748176957442','系统管理','org',66,1,'/org','M','',1,'carbon:column-dependency',0,'basic','',0,0,'0','16663888968888889','system-default',1,'188666666666','2024-06-07 15:51:10','188666666666','2024-06-20 15:48:41',NULL,NULL,NULL,'N'),('1814169974018928641','系统日志','system_log',30,1,'','M','',1,'tabler:logs',0,'basic','',0,0,'0','16663888968888889','system-default',1,'188666666666','2024-07-19 13:26:42','188666666666','2026-10-06 22:14:28',NULL,NULL,NULL,'N'),('1866100000000000001','流程管理','flow',150,1,'/flow','M','',1,'icon-default',0,'basic',NULL,0,0,'0','16663888968888889','system-default',1,'188666666666','2026-08-16 00:00:00',NULL,NULL,'基于warm-flow的工作流',NULL,NULL,'N'),('1866100000000000002','流程定义','flow_definition',1,1,'/flow/definition','C','flow:definition:list',1,'icon-default',0,'self',NULL,0,0,'1866100000000000001','16663888968888889','system-default',1,'188666666666','2026-08-16 00:00:00',NULL,NULL,NULL,NULL,NULL,'N'),('1866100000000000003','流程设计器','flow_designer',2,0,'/flow/designer','C','flow:definition:query',1,'icon-default',0,'self',NULL,0,0,'1866100000000000001','16663888968888889','system-default',1,'188666666666','2026-08-16 00:00:00',NULL,NULL,'warm-flow设计器（隐藏菜单，从流程定义进入）',NULL,NULL,'N'),('1866100000000000004','流程表单配置','flow_form-config',3,1,'/flow/form-config','C','flow:form:list',1,'icon-default',0,'self',NULL,0,0,'1866100000000000001','16663888968888889','system-default',1,'188666666666','2026-08-16 00:00:00',NULL,NULL,'流程与动态表单绑定及节点字段权限',NULL,NULL,'N'),('1866100000000000005','待办任务','flow_todo',4,1,'/flow/todo','C','flow:task:list',1,'icon-default',0,'self',NULL,0,0,'1866100000000000001','16663888968888889','system-default',1,'188666666666','2026-08-16 00:00:00',NULL,NULL,NULL,NULL,NULL,'N'),('1866100000000000006','已办任务','flow_done',5,1,'/flow/done','C','flow:task:list',1,'icon-default',0,'self',NULL,0,0,'1866100000000000001','16663888968888889','system-default',1,'188666666666','2026-08-16 00:00:00',NULL,NULL,NULL,NULL,NULL,'N'),('1866100000000000007','我发起的','flow_instance',6,1,'/flow/instance','C','flow:instance:list',1,'icon-default',0,'self',NULL,0,0,'1866100000000000001','16663888968888889','system-default',1,'188666666666','2026-08-16 00:00:00',NULL,NULL,NULL,NULL,NULL,'N'),('1866100000000000011','流程定义查询','',1,1,'','F','flow:definition:query',1,'icon-default',0,'',NULL,0,0,'1866100000000000002','16663888968888889','system-default',1,'188666666666','2026-08-16 00:00:00',NULL,NULL,NULL,NULL,NULL,'N'),('1866100000000000012','流程定义新增','',2,1,'','F','flow:definition:add',1,'icon-default',0,'',NULL,0,0,'1866100000000000002','16663888968888889','system-default',1,'188666666666','2026-08-16 00:00:00',NULL,NULL,NULL,NULL,NULL,'N'),('1866100000000000013','流程定义修改','',3,1,'','F','flow:definition:edit',1,'icon-default',0,'',NULL,0,0,'1866100000000000002','16663888968888889','system-default',1,'188666666666','2026-08-16 00:00:00',NULL,NULL,NULL,NULL,NULL,'N'),('1866100000000000014','流程定义删除','',4,1,'','F','flow:definition:remove',1,'icon-default',0,'',NULL,0,0,'1866100000000000002','16663888968888889','system-default',1,'188666666666','2026-08-16 00:00:00',NULL,NULL,NULL,NULL,NULL,'N'),('1866100000000000015','流程定义发布/停用','',5,1,'','F','flow:definition:publish',1,'icon-default',0,'',NULL,0,0,'1866100000000000002','16663888968888889','system-default',1,'188666666666','2026-08-16 00:00:00',NULL,NULL,NULL,NULL,NULL,'N'),('1866100000000000016','流程定义导入导出','',6,1,'','F','flow:definition:import',1,'icon-default',0,'',NULL,0,0,'1866100000000000002','16663888968888889','system-default',1,'188666666666','2026-08-16 00:00:00',NULL,NULL,NULL,NULL,NULL,'N'),('1866100000000000021','流程表单查询','',1,1,'','F','flow:form:query',1,'icon-default',0,'',NULL,0,0,'1866100000000000004','16663888968888889','system-default',1,'188666666666','2026-08-16 00:00:00',NULL,NULL,NULL,NULL,NULL,'N'),('1866100000000000022','流程表单新增','',2,1,'','F','flow:form:add',1,'icon-default',0,'',NULL,0,0,'1866100000000000004','16663888968888889','system-default',1,'188666666666','2026-08-16 00:00:00',NULL,NULL,NULL,NULL,NULL,'N'),('1866100000000000023','流程表单修改','',3,1,'','F','flow:form:edit',1,'icon-default',0,'',NULL,0,0,'1866100000000000004','16663888968888889','system-default',1,'188666666666','2026-08-16 00:00:00',NULL,NULL,NULL,NULL,NULL,'N'),('1866100000000000024','流程表单删除','',4,1,'','F','flow:form:remove',1,'icon-default',0,'',NULL,0,0,'1866100000000000004','16663888968888889','system-default',1,'188666666666','2026-08-16 00:00:00',NULL,NULL,NULL,NULL,NULL,'N'),('1866100000000000031','任务通过','',1,1,'','F','flow:task:approve',1,'icon-default',0,'',NULL,0,0,'1866100000000000005','16663888968888889','system-default',1,'188666666666','2026-08-16 00:00:00',NULL,NULL,NULL,NULL,NULL,'N'),('1866100000000000032','任务驳回','',2,1,'','F','flow:task:reject',1,'icon-default',0,'',NULL,0,0,'1866100000000000005','16663888968888889','system-default',1,'188666666666','2026-08-16 00:00:00',NULL,NULL,NULL,NULL,NULL,'N'),('1866100000000000033','任务转办','',3,1,'','F','flow:task:transfer',1,'icon-default',0,'',NULL,0,0,'1866100000000000005','16663888968888889','system-default',1,'188666666666','2026-08-16 00:00:00',NULL,NULL,NULL,NULL,NULL,'N'),('1866100000000000034','任务委派','',4,1,'','F','flow:task:delegate',1,'icon-default',0,'',NULL,0,0,'1866100000000000005','16663888968888889','system-default',1,'188666666666','2026-08-16 00:00:00',NULL,NULL,NULL,NULL,NULL,'N'),('1866100000000000035','任务加减签','',5,1,'','F','flow:task:addSign',1,'icon-default',0,'',NULL,0,0,'1866100000000000005','16663888968888889','system-default',1,'188666666666','2026-08-16 00:00:00',NULL,NULL,NULL,NULL,NULL,'N'),('1866100000000000036','任务跳转','',6,1,'','F','flow:task:jump',1,'icon-default',0,'',NULL,0,0,'1866100000000000005','16663888968888889','system-default',1,'188666666666','2026-08-16 00:00:00',NULL,NULL,NULL,NULL,NULL,'N'),('1866100000000000041','实例撤销','',1,1,'','F','flow:instance:revoke',1,'icon-default',0,'',NULL,0,0,'1866100000000000007','16663888968888889','system-default',1,'188666666666','2026-08-16 00:00:00',NULL,NULL,NULL,NULL,NULL,'N'),('1866100000000000042','实例终止','',2,1,'','F','flow:instance:terminate',1,'icon-default',0,'',NULL,0,0,'1866100000000000007','16663888968888889','system-default',1,'188666666666','2026-08-16 00:00:00',NULL,NULL,NULL,NULL,NULL,'N'),('24d3c6e26f2511ee983f00163e085602','通知','system_notice',70,1,'/system/notice','C','system:notice:list',1,'icon-default',0,'self',NULL,0,0,'1803694748176957442','16663888968888889','system-default',1,'188666666666','2023-10-20 16:46:21','188666666666','2024-06-20 15:44:53',NULL,NULL,NULL,'N'),('24d3ed076f2511ee983f00163e085602','通知列表','',1,1,'','F','system:notice:list',1,'icon-default',0,'',NULL,0,0,'24d3c6e26f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:46:21',NULL,NULL,NULL,NULL,NULL,'N'),('24d4132d6f2511ee983f00163e085602','通知详情','',1,1,'','F','system:notice:query',1,'icon-default',0,'',NULL,0,0,'24d3c6e26f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:46:21',NULL,NULL,NULL,NULL,NULL,'N'),('24d4132e6f2511ee983f00163e085602','通知新增','',1,1,'','F','system:notice:add',1,'icon-default',0,'',NULL,0,0,'24d3c6e26f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:46:21',NULL,NULL,NULL,NULL,NULL,'N'),('24d439536f2511ee983f00163e085602','通知修改','',1,1,'','F','system:notice:edit',1,'icon-default',0,'',NULL,0,0,'24d3c6e26f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:46:21',NULL,NULL,NULL,NULL,NULL,'N'),('24d439546f2511ee983f00163e085602','通知删除','',1,1,'','F','system:notice:remove',1,'icon-default',0,'',NULL,0,0,'24d3c6e26f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:46:21',NULL,NULL,NULL,NULL,NULL,'N'),('24d45f786f2511ee983f00163e085602','通知导入','',1,1,'','F','system:notice:import',1,'icon-default',0,'',NULL,0,0,'24d3c6e26f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:46:21',NULL,NULL,NULL,NULL,NULL,'N'),('24d45f796f2511ee983f00163e085602','通知导出','',1,1,'','F','system:notice:export',1,'icon-default',0,'',NULL,0,0,'24d3c6e26f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:46:21',NULL,NULL,NULL,NULL,NULL,'N'),('26bb3ab0dad111ee9b3d00163e085602','公共组','system_common-group',155,1,'/system/common-group','C','system:commonGroup:list',1,'icon-default',0,'self',NULL,0,0,'1798986040628969473','16663888968888889','system-default',1,'188666666666','2024-03-05 17:17:12','188666666666','2024-06-07 15:22:02',NULL,NULL,NULL,'N'),('26bb86fcdad111ee9b3d00163e085602','公共组列表','',1,1,'','F','system:commonGroup:list',1,'icon-default',0,'',NULL,0,0,'26bb3ab0dad111ee9b3d00163e085602','16663888968888889','system-default',1,'188666666666','2024-03-05 17:17:12',NULL,NULL,NULL,NULL,NULL,'N'),('26bbad22dad111ee9b3d00163e085602','公共组详情','',1,1,'','F','system:commonGroup:query',1,'icon-default',0,'',NULL,0,0,'26bb3ab0dad111ee9b3d00163e085602','16663888968888889','system-default',1,'188666666666','2024-03-05 17:17:12',NULL,NULL,NULL,NULL,NULL,'N'),('26bbd348dad111ee9b3d00163e085602','公共组新增','',1,1,'','F','system:commonGroup:add',1,'icon-default',0,'',NULL,0,0,'26bb3ab0dad111ee9b3d00163e085602','16663888968888889','system-default',1,'188666666666','2024-03-05 17:17:12',NULL,NULL,NULL,NULL,NULL,'N'),('26bbf96edad111ee9b3d00163e085602','公共组修改','',1,1,'','F','system:commonGroup:edit',1,'icon-default',0,'',NULL,0,0,'26bb3ab0dad111ee9b3d00163e085602','16663888968888889','system-default',1,'188666666666','2024-03-05 17:17:12',NULL,NULL,NULL,NULL,NULL,'N'),('26bbf96fdad111ee9b3d00163e085602','公共组删除','',1,1,'','F','system:commonGroup:remove',1,'icon-default',0,'',NULL,0,0,'26bb3ab0dad111ee9b3d00163e085602','16663888968888889','system-default',1,'188666666666','2024-03-05 17:17:12',NULL,NULL,NULL,NULL,NULL,'N'),('26bc1f94dad111ee9b3d00163e085602','公共组导入','',1,1,'','F','system:commonGroup:import',1,'icon-default',0,'',NULL,0,0,'26bb3ab0dad111ee9b3d00163e085602','16663888968888889','system-default',1,'188666666666','2024-03-05 17:17:12',NULL,NULL,NULL,NULL,NULL,'N'),('26bc45badad111ee9b3d00163e085602','公共组导出','',1,1,'','F','system:commonGroup:export',1,'icon-default',0,'',NULL,0,0,'26bb3ab0dad111ee9b3d00163e085602','16663888968888889','system-default',1,'188666666666','2024-03-05 17:17:12',NULL,NULL,NULL,NULL,NULL,'N'),('304d18026f2511ee983f00163e085602','企业管理','system_organization',200,1,'/system/organization','C','system:organization:list',1,'icon-default',0,'self',NULL,0,0,'1798986040628969473','16663888968888889','system-default',1,'188666666666','2023-10-20 16:46:40','188666666666','2024-06-07 15:18:53',NULL,NULL,NULL,'N'),('304d3e276f2511ee983f00163e085602','企业列表','',1,1,'','F','system:organization:list',1,'icon-default',0,'',NULL,0,0,'304d18026f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:46:41',NULL,NULL,NULL,NULL,NULL,'N'),('304d644d6f2511ee983f00163e085602','企业详情','',1,1,'','F','system:organization:query',1,'icon-default',0,'',NULL,0,0,'304d18026f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:46:41',NULL,NULL,NULL,NULL,NULL,'N'),('304d644e6f2511ee983f00163e085602','企业新增','',1,1,'','F','system:organization:add',1,'icon-default',0,'',NULL,0,0,'304d18026f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:46:41',NULL,NULL,NULL,NULL,NULL,'N'),('304d8a736f2511ee983f00163e085602','企业修改','',1,1,'','F','system:organization:edit',1,'icon-default',0,'',NULL,0,0,'304d18026f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:46:41',NULL,NULL,NULL,NULL,NULL,'N'),('304d8a746f2511ee983f00163e085602','企业删除','',1,1,'','F','system:organization:remove',1,'icon-default',0,'',NULL,0,0,'304d18026f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:46:41',NULL,NULL,NULL,NULL,NULL,'N'),('304db0986f2511ee983f00163e085602','企业导入','',1,1,'','F','system:organization:import',1,'icon-default',0,'',NULL,0,0,'304d18026f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:46:41',NULL,NULL,NULL,NULL,NULL,'N'),('304dd6be6f2511ee983f00163e085602','企业导出','',1,1,'','F','system:organization:export',1,'icon-default',0,'',NULL,0,0,'304d18026f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:46:41',NULL,NULL,NULL,NULL,NULL,'N'),('3ca2a65b6f2511ee983f00163e085602','组织拥有的菜单','system_organization-menu',1,1,'/system/organization-menu','C','system:organizationMenu:list',1,'icon-default',0,'self',NULL,0,0,'1663888968294588891','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:01','188666666666','2024-06-07 15:19:50',NULL,NULL,NULL,'N'),('3ca2cc816f2511ee983f00163e085602','组织拥有的菜单列表','',1,1,'','F','system:organizationMenu:list',1,'icon-default',0,'',NULL,0,0,'3ca2a65b6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:01',NULL,NULL,NULL,NULL,NULL,'N'),('3ca2cc826f2511ee983f00163e085602','组织拥有的菜单详情','',1,1,'','F','system:organizationMenu:query',1,'icon-default',0,'',NULL,0,0,'3ca2a65b6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:01',NULL,NULL,NULL,NULL,NULL,'N'),('3ca2f2a76f2511ee983f00163e085602','组织拥有的菜单新增','',1,1,'','F','system:organizationMenu:add',1,'icon-default',0,'',NULL,0,0,'3ca2a65b6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:01',NULL,NULL,NULL,NULL,NULL,'N'),('3ca2f2a86f2511ee983f00163e085602','组织拥有的菜单修改','',1,1,'','F','system:organizationMenu:edit',1,'icon-default',0,'',NULL,0,0,'3ca2a65b6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:01',NULL,NULL,NULL,NULL,NULL,'N'),('3ca318cc6f2511ee983f00163e085602','组织拥有的菜单删除','',1,1,'','F','system:organizationMenu:remove',1,'icon-default',0,'',NULL,0,0,'3ca2a65b6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:01',NULL,NULL,NULL,NULL,NULL,'N'),('3ca33ef26f2511ee983f00163e085602','组织拥有的菜单导入','',1,1,'','F','system:organizationMenu:import',1,'icon-default',0,'',NULL,0,0,'3ca2a65b6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:01',NULL,NULL,NULL,NULL,NULL,'N'),('3ca33ef36f2511ee983f00163e085602','组织拥有的菜单导出','',1,1,'','F','system:organizationMenu:export',1,'icon-default',0,'',NULL,0,0,'3ca2a65b6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:01',NULL,NULL,NULL,NULL,NULL,'N'),('42c53b736f2511ee983f00163e085602','岗位','system_position',80,1,'/system/position','C','system:position:list',1,'icon-default',0,'self',NULL,0,0,'1803694748176957442','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:11','188666666666','2024-06-20 15:44:42',NULL,NULL,NULL,'N'),('42c561996f2511ee983f00163e085602','岗位列表','',1,1,'','F','system:position:list',1,'icon-default',0,'',NULL,0,0,'42c53b736f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:11',NULL,NULL,NULL,NULL,NULL,'N'),('42c5619a6f2511ee983f00163e085602','岗位详情','',1,1,'','F','system:position:query',1,'icon-default',0,'',NULL,0,0,'42c53b736f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:11',NULL,NULL,NULL,NULL,NULL,'N'),('42c587be6f2511ee983f00163e085602','岗位新增','',1,1,'','F','system:position:add',1,'icon-default',0,'',NULL,0,0,'42c53b736f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:12',NULL,NULL,NULL,NULL,NULL,'N'),('42c587bf6f2511ee983f00163e085602','岗位修改','',1,1,'','F','system:position:edit',1,'icon-default',0,'',NULL,0,0,'42c53b736f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:12',NULL,NULL,NULL,NULL,NULL,'N'),('42c5ade46f2511ee983f00163e085602','岗位删除','',1,1,'','F','system:position:remove',1,'icon-default',0,'',NULL,0,0,'42c53b736f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:12',NULL,NULL,NULL,NULL,NULL,'N'),('42c5d4096f2511ee983f00163e085602','岗位导入','',1,1,'','F','system:position:import',1,'icon-default',0,'',NULL,0,0,'42c53b736f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:12',NULL,NULL,NULL,NULL,NULL,'N'),('42c5d40a6f2511ee983f00163e085602','岗位导出','',1,1,'','F','system:position:export',1,'icon-default',0,'',NULL,0,0,'42c53b736f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:12',NULL,NULL,NULL,NULL,NULL,'N'),('470abfd0f31b11ef905d78fb839f9406','动态表单','dynamic',20,1,'/dynamic','M','dynamic:business:list',1,'carbon:cloud-service-management',0,'basic',NULL,0,0,'0','16663888968888889','system-default',1,'188666666666','2025-02-25 09:53:15','188666666666','2025-02-25 09:57:24',NULL,NULL,NULL,'N'),('490e8ec16f2511ee983f00163e085602','角色组','system_role-group',60,1,'/system/role-group','C','system:roleGroup:list',1,'icon-default',0,'self',NULL,0,0,'1663888968294588891','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:22',NULL,NULL,NULL,NULL,NULL,'N'),('490eb4e76f2511ee983f00163e085602','角色组列表','',1,1,'','F','system:roleGroup:list',1,'icon-default',0,'',NULL,0,0,'490e8ec16f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:22',NULL,NULL,NULL,NULL,NULL,'N'),('490eb4e86f2511ee983f00163e085602','角色组详情','',1,1,'','F','system:roleGroup:query',1,'icon-default',0,'',NULL,0,0,'490e8ec16f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:22',NULL,NULL,NULL,NULL,NULL,'N'),('490edb0c6f2511ee983f00163e085602','角色组新增','',1,1,'','F','system:roleGroup:add',1,'icon-default',0,'',NULL,0,0,'490e8ec16f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:22',NULL,NULL,NULL,NULL,NULL,'N'),('490f01326f2511ee983f00163e085602','角色组修改','',1,1,'','F','system:roleGroup:edit',1,'icon-default',0,'',NULL,0,0,'490e8ec16f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:22',NULL,NULL,NULL,NULL,NULL,'N'),('490f01336f2511ee983f00163e085602','角色组删除','',1,1,'','F','system:roleGroup:remove',1,'icon-default',0,'',NULL,0,0,'490e8ec16f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:22',NULL,NULL,NULL,NULL,NULL,'N'),('490f27576f2511ee983f00163e085602','角色组导入','',1,1,'','F','system:roleGroup:import',1,'icon-default',0,'',NULL,0,0,'490e8ec16f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:22',NULL,NULL,NULL,NULL,NULL,'N'),('490f27586f2511ee983f00163e085602','角色组导出','',1,1,'','F','system:roleGroup:export',1,'icon-default',0,'',NULL,0,0,'490e8ec16f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:22',NULL,NULL,NULL,NULL,NULL,'N'),('4d61c2aac16111f18fc3a5e694323b94','脚本资产查询','dynamic_script_list',88,1,NULL,'F','dynamic:script:list',1,NULL,0,NULL,NULL,NULL,NULL,'0','16663888968888889','system-default',1,'system','2026-10-06 16:38:27',NULL,NULL,'脚本资产列表与绑定查询',NULL,NULL,'N'),('4d61c2dcc16111f18fc3a5e694323b94','脚本资产明细','dynamic_script_query',89,1,NULL,'F','dynamic:script:query',1,NULL,0,NULL,NULL,NULL,NULL,'0','16663888968888889','system-default',1,'system','2026-10-06 16:38:27',NULL,NULL,'资产详情/引用影响面/执行日志/版本历史',NULL,NULL,'N'),('4d61c2e6c16111f18fc3a5e694323b94','脚本资产新增','dynamic_script_add',90,1,NULL,'F','dynamic:script:add',1,NULL,0,NULL,NULL,NULL,NULL,'0','16663888968888889','system-default',1,'system','2026-10-06 16:38:27',NULL,NULL,'新建脚本资产（沙箱校验）',NULL,NULL,'N'),('4d61c2fac16111f18fc3a5e694323b94','脚本资产编辑','dynamic_script_edit',91,1,NULL,'F','dynamic:script:edit',1,NULL,0,NULL,NULL,NULL,NULL,'0','16663888968888889','system-default',1,'system','2026-10-06 16:38:27',NULL,NULL,'编辑资产与挂点绑定',NULL,NULL,'N'),('4d61c304c16111f18fc3a5e694323b94','脚本资产删除','dynamic_script_remove',92,1,NULL,'F','dynamic:script:remove',1,NULL,0,NULL,NULL,NULL,NULL,'0','16663888968888889','system-default',1,'system','2026-10-06 16:38:27',NULL,NULL,'删除资产（被引用时拒绝）',NULL,NULL,'N'),('4d61c30ec16111f18fc3a5e694323b94','脚本资产发布','dynamic_script_publish',93,1,NULL,'F','dynamic:script:publish',1,NULL,0,NULL,NULL,NULL,NULL,'0','16663888968888889','system-default',1,'system','2026-10-06 16:38:27',NULL,NULL,'发布/停用（试运行纪律）',NULL,NULL,'N'),('4d61c318c16111f18fc3a5e694323b94','脚本试运行','dynamic_script_run',94,1,NULL,'F','dynamic:script:run',1,NULL,0,NULL,NULL,NULL,NULL,'0','16663888968888889','system-default',1,'system','2026-10-06 16:38:27',NULL,NULL,'试运行与按编码直跑',NULL,NULL,'N'),('4f131aa6c17811f18fc3a5e694323b94','AI模型配置查询','system_ai_config_query',96,1,NULL,'F','system:aiConfig:query',1,NULL,0,NULL,NULL,NULL,NULL,'0','16663888968888889','system-default',1,'system','2026-10-06 19:23:09',NULL,NULL,'查看本组织模型配置（apiKey 掩码）',NULL,NULL,'N'),('4f131aecc17811f18fc3a5e694323b94','AI模型配置保存','system_ai_config_save',97,1,NULL,'F','system:aiConfig:save',1,NULL,0,NULL,NULL,NULL,NULL,'0','16663888968888889','system-default',1,'system','2026-10-06 19:23:09',NULL,NULL,'保存/启停模型配置（公网模型须明示数据流向）',NULL,NULL,'N'),('51ff13f66f2511ee983f00163e085602','角色组与角色','system_role-group-role',999,1,'/system/role-group-role','C','system:roleGroupRole:list',1,'icon-default',0,'self',NULL,0,0,'1663888968294588891','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:37',NULL,NULL,NULL,NULL,NULL,'N'),('51ff13f76f2511ee983f00163e085602','角色组与角色列表','',1,1,'','F','system:roleGroupRole:list',1,'icon-default',0,'',NULL,0,0,'51ff13f66f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:37',NULL,NULL,NULL,NULL,NULL,'N'),('51ff3a1b6f2511ee983f00163e085602','角色组与角色详情','',1,1,'','F','system:roleGroupRole:query',1,'icon-default',0,'',NULL,0,0,'51ff13f66f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:37',NULL,NULL,NULL,NULL,NULL,'N'),('51ff60416f2511ee983f00163e085602','角色组与角色新增','',1,1,'','F','system:roleGroupRole:add',1,'icon-default',0,'',NULL,0,0,'51ff13f66f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:37',NULL,NULL,NULL,NULL,NULL,'N'),('51ff86676f2511ee983f00163e085602','角色组与角色修改','',1,1,'','F','system:roleGroupRole:edit',1,'icon-default',0,'',NULL,0,0,'51ff13f66f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:37',NULL,NULL,NULL,NULL,NULL,'N'),('51ff86686f2511ee983f00163e085602','角色组与角色删除','',1,1,'','F','system:roleGroupRole:remove',1,'icon-default',0,'',NULL,0,0,'51ff13f66f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:37',NULL,NULL,NULL,NULL,NULL,'N'),('51ffac8c6f2511ee983f00163e085602','角色组与角色导入','',1,1,'','F','system:roleGroupRole:import',1,'icon-default',0,'',NULL,0,0,'51ff13f66f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:37',NULL,NULL,NULL,NULL,NULL,'N'),('51ffac8d6f2511ee983f00163e085602','角色组与角色导出','',1,1,'','F','system:roleGroupRole:export',1,'icon-default',0,'',NULL,0,0,'51ff13f66f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:37',NULL,NULL,NULL,NULL,NULL,'N'),('57f757506f2511ee983f00163e085602','角色管理','system_role',100,1,'/system/role','C','system:role:list',1,'icon-default',0,'self',NULL,0,0,'1803694748176957442','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:47','188666666666','2024-06-20 15:44:10',NULL,NULL,NULL,'N'),('57f77d756f2511ee983f00163e085602','角色列表','',1,1,'','F','system:role:list',1,'icon-default',0,'',NULL,0,0,'57f757506f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:47',NULL,NULL,NULL,NULL,NULL,'N'),('57f77d766f2511ee983f00163e085602','角色详情','',1,1,'','F','system:role:query',1,'icon-default',0,'',NULL,0,0,'57f757506f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:47',NULL,NULL,NULL,NULL,NULL,'N'),('57f7a39b6f2511ee983f00163e085602','角色新增','',1,1,'','F','system:role:add',1,'icon-default',0,'',NULL,0,0,'57f757506f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:47',NULL,NULL,NULL,NULL,NULL,'N'),('57f7a39c6f2511ee983f00163e085602','角色修改','',1,1,'','F','system:role:edit',1,'icon-default',0,'',NULL,0,0,'57f757506f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:47',NULL,NULL,NULL,NULL,NULL,'N'),('57f7c9c16f2511ee983f00163e085602','角色删除','',1,1,'','F','system:role:remove',1,'icon-default',0,'',NULL,0,0,'57f757506f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:47',NULL,NULL,NULL,NULL,NULL,'N'),('57f7efe66f2511ee983f00163e085602','角色导入','',1,1,'','F','system:role:import',1,'icon-default',0,'',NULL,0,0,'57f757506f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:47',NULL,NULL,NULL,NULL,NULL,'N'),('57f7efe76f2511ee983f00163e085602','角色导出','',1,1,'','F','system:role:export',1,'icon-default',0,'',NULL,0,0,'57f757506f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:47',NULL,NULL,NULL,NULL,NULL,'N'),('5dda25806f2511ee983f00163e085602','角色与菜单关系','system_role-menu',1,1,'/system/role-menu','C','system:roleMenu:list',1,'icon-default',0,'self',NULL,0,0,'1663888968294588891','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:57','188666666666','2024-06-07 15:20:02',NULL,NULL,NULL,'N'),('5dda4ba56f2511ee983f00163e085602','角色与菜单关系列表','',1,1,'','F','system:roleMenu:list',1,'icon-default',0,'',NULL,0,0,'5dda25806f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:57',NULL,NULL,NULL,NULL,NULL,'N'),('5dda71cb6f2511ee983f00163e085602','角色与菜单关系详情','',1,1,'','F','system:roleMenu:query',1,'icon-default',0,'',NULL,0,0,'5dda25806f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:57',NULL,NULL,NULL,NULL,NULL,'N'),('5dda71cc6f2511ee983f00163e085602','角色与菜单关系新增','',1,1,'','F','system:roleMenu:add',1,'icon-default',0,'',NULL,0,0,'5dda25806f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:57',NULL,NULL,NULL,NULL,NULL,'N'),('5dda97f16f2511ee983f00163e085602','角色与菜单关系修改','',1,1,'','F','system:roleMenu:edit',1,'icon-default',0,'',NULL,0,0,'5dda25806f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:57',NULL,NULL,NULL,NULL,NULL,'N'),('5dda97f26f2511ee983f00163e085602','角色与菜单关系删除','',1,1,'','F','system:roleMenu:remove',1,'icon-default',0,'',NULL,0,0,'5dda25806f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:57',NULL,NULL,NULL,NULL,NULL,'N'),('5ddabe166f2511ee983f00163e085602','角色与菜单关系导入','',1,1,'','F','system:roleMenu:import',1,'icon-default',0,'',NULL,0,0,'5dda25806f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:57',NULL,NULL,NULL,NULL,NULL,'N'),('5ddabe176f2511ee983f00163e085602','角色与菜单关系导出','',1,1,'','F','system:roleMenu:export',1,'icon-default',0,'',NULL,0,0,'5dda25806f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:57',NULL,NULL,NULL,NULL,NULL,'N'),('63dc8ad86f2511ee983f00163e085602','角色用户与部门关系','system_role-user-dept',1,1,'/system/role-user-dept','C','system:roleUserDept:list',1,'icon-default',0,'self',NULL,0,0,'1663888968294588891','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:07','188666666666','2024-06-07 15:20:10',NULL,NULL,NULL,'N'),('63dc8ad96f2511ee983f00163e085602','角色用户与部门关系列表','',1,1,'','F','system:roleUserDept:list',1,'icon-default',0,'',NULL,0,0,'63dc8ad86f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:07',NULL,NULL,NULL,NULL,NULL,'N'),('63dcb0fe6f2511ee983f00163e085602','角色用户与部门关系详情','',1,1,'','F','system:roleUserDept:query',1,'icon-default',0,'',NULL,0,0,'63dc8ad86f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:07',NULL,NULL,NULL,NULL,NULL,'N'),('63dcd7236f2511ee983f00163e085602','角色用户与部门关系新增','',1,1,'','F','system:roleUserDept:add',1,'icon-default',0,'',NULL,0,0,'63dc8ad86f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:07',NULL,NULL,NULL,NULL,NULL,'N'),('63dcd7246f2511ee983f00163e085602','角色用户与部门关系修改','',1,1,'','F','system:roleUserDept:edit',1,'icon-default',0,'',NULL,0,0,'63dc8ad86f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:07',NULL,NULL,NULL,NULL,NULL,'N'),('63dcfd496f2511ee983f00163e085602','角色用户与部门关系删除','',1,1,'','F','system:roleUserDept:remove',1,'icon-default',0,'',NULL,0,0,'63dc8ad86f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:07',NULL,NULL,NULL,NULL,NULL,'N'),('63dd236f6f2511ee983f00163e085602','角色用户与部门关系导入','',1,1,'','F','system:roleUserDept:import',1,'icon-default',0,'',NULL,0,0,'63dc8ad86f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:07',NULL,NULL,NULL,NULL,NULL,'N'),('63dd23706f2511ee983f00163e085602','角色用户与部门关系导出','',1,1,'','F','system:roleUserDept:export',1,'icon-default',0,'',NULL,0,0,'63dc8ad86f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:07',NULL,NULL,NULL,NULL,NULL,'N'),('69b5cfa06f2511ee983f00163e085602','角色与用户关系','system_role-user',999,1,'/system/role-user','C','system:roleUser:list',1,'icon-default',0,'self',NULL,0,0,'1663888968294588891','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:17',NULL,NULL,NULL,NULL,NULL,'N'),('69b5f5c66f2511ee983f00163e085602','角色与用户关系列表','',1,1,'','F','system:roleUser:list',1,'icon-default',0,'',NULL,0,0,'69b5cfa06f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:17',NULL,NULL,NULL,NULL,NULL,'N'),('69b5f5c76f2511ee983f00163e085602','角色与用户关系详情','',1,1,'','F','system:roleUser:query',1,'icon-default',0,'',NULL,0,0,'69b5cfa06f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:17',NULL,NULL,NULL,NULL,NULL,'N'),('69b61beb6f2511ee983f00163e085602','角色与用户关系新增','',1,1,'','F','system:roleUser:add',1,'icon-default',0,'',NULL,0,0,'69b5cfa06f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:17',NULL,NULL,NULL,NULL,NULL,'N'),('69b61bec6f2511ee983f00163e085602','角色与用户关系修改','',1,1,'','F','system:roleUser:edit',1,'icon-default',0,'',NULL,0,0,'69b5cfa06f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:17',NULL,NULL,NULL,NULL,NULL,'N'),('69b642116f2511ee983f00163e085602','角色与用户关系删除','',1,1,'','F','system:roleUser:remove',1,'icon-default',0,'',NULL,0,0,'69b5cfa06f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:17',NULL,NULL,NULL,NULL,NULL,'N'),('69b642126f2511ee983f00163e085602','角色与用户关系导入','',1,1,'','F','system:roleUser:import',1,'icon-default',0,'',NULL,0,0,'69b5cfa06f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:17',NULL,NULL,NULL,NULL,NULL,'N'),('69b668376f2511ee983f00163e085602','角色与用户关系导出','',1,1,'','F','system:roleUser:export',1,'icon-default',0,'',NULL,0,0,'69b5cfa06f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:17',NULL,NULL,NULL,NULL,NULL,'N'),('6c1d7c80bf4411f18fc3a5e694323b94','定时任务','job',25,1,'/job','M','',1,'icon-default',0,'basic',NULL,0,0,'1663888968294588891','16663888968888889','system-default',1,'188666666666','2026-10-04 00:06:41',NULL,NULL,NULL,NULL,NULL,'N'),('6c1dc370bf4411f18fc3a5e694323b94','任务管理','job_task',1,1,'/job/task','C','schedule:task:list',1,'icon-default',0,'self',NULL,0,0,'6c1d7c80bf4411f18fc3a5e694323b94','16663888968888889','system-default',1,'188666666666','2026-10-04 00:06:41',NULL,NULL,NULL,NULL,NULL,'N'),('6c1dc398bf4411f18fc3a5e694323b94','执行记录','job_log',2,1,'/job/log','C','schedule:log:list',1,'icon-default',0,'self',NULL,0,0,'6c1d7c80bf4411f18fc3a5e694323b94','16663888968888889','system-default',1,'188666666666','2026-10-04 00:06:41',NULL,NULL,NULL,NULL,NULL,'N'),('6c1dc3a2bf4411f18fc3a5e694323b94','实例状态','job_instance',3,1,'/job/instance','C','schedule:instance:list',1,'icon-default',0,'self',NULL,0,0,'6c1d7c80bf4411f18fc3a5e694323b94','16663888968888889','system-default',1,'188666666666','2026-10-04 00:06:41',NULL,NULL,NULL,NULL,NULL,'N'),('6c1e3512bf4411f18fc3a5e694323b94','任务查询','',1,1,'','F','schedule:task:query',1,'icon-default',0,'',NULL,0,0,'6c1dc370bf4411f18fc3a5e694323b94','16663888968888889','system-default',1,'188666666666','2026-10-04 00:06:41',NULL,NULL,NULL,NULL,NULL,'N'),('6c1e3526bf4411f18fc3a5e694323b94','任务新增','',1,1,'','F','schedule:task:add',1,'icon-default',0,'',NULL,0,0,'6c1dc370bf4411f18fc3a5e694323b94','16663888968888889','system-default',1,'188666666666','2026-10-04 00:06:41',NULL,NULL,NULL,NULL,NULL,'N'),('6c1e3530bf4411f18fc3a5e694323b94','任务编辑','',1,1,'','F','schedule:task:edit',1,'icon-default',0,'',NULL,0,0,'6c1dc370bf4411f18fc3a5e694323b94','16663888968888889','system-default',1,'188666666666','2026-10-04 00:06:41',NULL,NULL,NULL,NULL,NULL,'N'),('6c1e3544bf4411f18fc3a5e694323b94','任务删除','',1,1,'','F','schedule:task:delete',1,'icon-default',0,'',NULL,0,0,'6c1dc370bf4411f18fc3a5e694323b94','16663888968888889','system-default',1,'188666666666','2026-10-04 00:06:41',NULL,NULL,NULL,NULL,NULL,'N'),('6c1e354ebf4411f18fc3a5e694323b94','手动执行','',1,1,'','F','schedule:task:run',1,'icon-default',0,'',NULL,0,0,'6c1dc370bf4411f18fc3a5e694323b94','16663888968888889','system-default',1,'188666666666','2026-10-04 00:06:41',NULL,NULL,NULL,NULL,NULL,'N'),('6c1e3558bf4411f18fc3a5e694323b94','终止运行','',1,1,'','F','schedule:task:terminate',1,'icon-default',0,'',NULL,0,0,'6c1dc370bf4411f18fc3a5e694323b94','16663888968888889','system-default',1,'188666666666','2026-10-04 00:06:41',NULL,NULL,NULL,NULL,NULL,'N'),('6c1e3562bf4411f18fc3a5e694323b94','批量启停','',1,1,'','F','schedule:task:pause-batch',1,'icon-default',0,'',NULL,0,0,'6c1dc370bf4411f18fc3a5e694323b94','16663888968888889','system-default',1,'188666666666','2026-10-04 00:06:41',NULL,NULL,NULL,NULL,NULL,'N'),('6c1e356cbf4411f18fc3a5e694323b94','日志重跑','',1,1,'','F','schedule:log:retry',1,'icon-default',0,'',NULL,0,0,'6c1dc398bf4411f18fc3a5e694323b94','16663888968888889','system-default',1,'188666666666','2026-10-04 00:06:41',NULL,NULL,NULL,NULL,NULL,'N'),('6f0a294f6f2511ee983f00163e085602','社交账号','system_social-user',180,1,'/system/social-user','C','system:socialUser:list',1,'icon-default',0,'self',NULL,0,0,'1663888968294588891','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:26',NULL,NULL,NULL,NULL,NULL,'N'),('6f0a4f746f2511ee983f00163e085602','社交账号列表','',1,1,'','F','system:socialUser:list',1,'icon-default',0,'',NULL,0,0,'6f0a294f6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:26',NULL,NULL,NULL,NULL,NULL,'N'),('6f0a759a6f2511ee983f00163e085602','社交账号详情','',1,1,'','F','system:socialUser:query',1,'icon-default',0,'',NULL,0,0,'6f0a294f6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:26',NULL,NULL,NULL,NULL,NULL,'N'),('6f0a759b6f2511ee983f00163e085602','社交账号新增','',1,1,'','F','system:socialUser:add',1,'icon-default',0,'',NULL,0,0,'6f0a294f6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:26',NULL,NULL,NULL,NULL,NULL,'N'),('6f0a9bc06f2511ee983f00163e085602','社交账号修改','',1,1,'','F','system:socialUser:edit',1,'icon-default',0,'',NULL,0,0,'6f0a294f6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:26',NULL,NULL,NULL,NULL,NULL,'N'),('6f0a9bc16f2511ee983f00163e085602','社交账号删除','',1,1,'','F','system:socialUser:remove',1,'icon-default',0,'',NULL,0,0,'6f0a294f6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:26',NULL,NULL,NULL,NULL,NULL,'N'),('6f0ac1e56f2511ee983f00163e085602','社交账号导入','',1,1,'','F','system:socialUser:import',1,'icon-default',0,'',NULL,0,0,'6f0a294f6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:26',NULL,NULL,NULL,NULL,NULL,'N'),('6f0ac1e66f2511ee983f00163e085602','社交账号导出','',1,1,'','F','system:socialUser:export',1,'icon-default',0,'',NULL,0,0,'6f0a294f6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:26',NULL,NULL,NULL,NULL,NULL,'N'),('747490be6f2511ee983f00163e085602','用户兼职部门','system_user-department',999,1,'/system/user-department','C','system:userDepartment:list',1,'icon-default',0,'self',NULL,0,0,'1663888968294588891','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:35',NULL,NULL,NULL,NULL,NULL,'N'),('7474b6e36f2511ee983f00163e085602','用户兼职部门列表','',1,1,'','F','system:userDepartment:list',1,'icon-default',0,'',NULL,0,0,'747490be6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:35',NULL,NULL,NULL,NULL,NULL,'N'),('7474b6e46f2511ee983f00163e085602','用户兼职部门详情','',1,1,'','F','system:userDepartment:query',1,'icon-default',0,'',NULL,0,0,'747490be6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:35',NULL,NULL,NULL,NULL,NULL,'N'),('7474dd096f2511ee983f00163e085602','用户兼职部门新增','',1,1,'','F','system:userDepartment:add',1,'icon-default',0,'',NULL,0,0,'747490be6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:35',NULL,NULL,NULL,NULL,NULL,'N'),('7474dd0a6f2511ee983f00163e085602','用户兼职部门修改','',1,1,'','F','system:userDepartment:edit',1,'icon-default',0,'',NULL,0,0,'747490be6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:35',NULL,NULL,NULL,NULL,NULL,'N'),('7475032f6f2511ee983f00163e085602','用户兼职部门删除','',1,1,'','F','system:userDepartment:remove',1,'icon-default',0,'',NULL,0,0,'747490be6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:35',NULL,NULL,NULL,NULL,NULL,'N'),('747529546f2511ee983f00163e085602','用户兼职部门导入','',1,1,'','F','system:userDepartment:import',1,'icon-default',0,'',NULL,0,0,'747490be6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:35',NULL,NULL,NULL,NULL,NULL,'N'),('747529556f2511ee983f00163e085602','用户兼职部门导出','',1,1,'','F','system:userDepartment:export',1,'icon-default',0,'',NULL,0,0,'747490be6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:35',NULL,NULL,NULL,NULL,NULL,'N'),('79bae89b6f2511ee983f00163e085602','用户组','system_user-group',90,1,'/system/user-group','C','system:userGroup:list',1,'icon-default',0,'self',NULL,0,0,'1803694748176957442','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:44','188666666666','2024-06-20 15:43:55',NULL,NULL,NULL,'N'),('79bb0ec16f2511ee983f00163e085602','用户组列表','',1,1,'','F','system:userGroup:list',1,'icon-default',0,'',NULL,0,0,'79bae89b6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:44',NULL,NULL,NULL,NULL,NULL,'N'),('79bb0ec26f2511ee983f00163e085602','用户组详情','',1,1,'','F','system:userGroup:query',1,'icon-default',0,'',NULL,0,0,'79bae89b6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:44',NULL,NULL,NULL,NULL,NULL,'N'),('79bb34e76f2511ee983f00163e085602','用户组新增','',1,1,'','F','system:userGroup:add',1,'icon-default',0,'',NULL,0,0,'79bae89b6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:44',NULL,NULL,NULL,NULL,NULL,'N'),('79bb5b0c6f2511ee983f00163e085602','用户组修改','',1,1,'','F','system:userGroup:edit',1,'icon-default',0,'',NULL,0,0,'79bae89b6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:44',NULL,NULL,NULL,NULL,NULL,'N'),('79bb5b0d6f2511ee983f00163e085602','用户组删除','',1,1,'','F','system:userGroup:remove',1,'icon-default',0,'',NULL,0,0,'79bae89b6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:44',NULL,NULL,NULL,NULL,NULL,'N'),('79bb81326f2511ee983f00163e085602','用户组导入','',1,1,'','F','system:userGroup:import',1,'icon-default',0,'',NULL,0,0,'79bae89b6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:44',NULL,NULL,NULL,NULL,NULL,'N'),('79bb81336f2511ee983f00163e085602','用户组导出','',1,1,'','F','system:userGroup:export',1,'icon-default',0,'',NULL,0,0,'79bae89b6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:44',NULL,NULL,NULL,NULL,NULL,'N'),('7ede62156f2511ee983f00163e085602','用户组与用户','system_user-group-user',999,1,'/system/user-group-user','C','system:userGroupUser:list',1,'icon-default',0,'self',NULL,0,0,'1663888968294588891','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:52',NULL,NULL,NULL,NULL,NULL,'N'),('7ede62166f2511ee983f00163e085602','用户组与用户列表','',1,1,'','F','system:userGroupUser:list',1,'icon-default',0,'',NULL,0,0,'7ede62156f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:52',NULL,NULL,NULL,NULL,NULL,'N'),('7ede883b6f2511ee983f00163e085602','用户组与用户详情','',1,1,'','F','system:userGroupUser:query',1,'icon-default',0,'',NULL,0,0,'7ede62156f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:52',NULL,NULL,NULL,NULL,NULL,'N'),('7ede883c6f2511ee983f00163e085602','用户组与用户新增','',1,1,'','F','system:userGroupUser:add',1,'icon-default',0,'',NULL,0,0,'7ede62156f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:52',NULL,NULL,NULL,NULL,NULL,'N'),('7edeae606f2511ee983f00163e085602','用户组与用户修改','',1,1,'','F','system:userGroupUser:edit',1,'icon-default',0,'',NULL,0,0,'7ede62156f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:52',NULL,NULL,NULL,NULL,NULL,'N'),('7edeae616f2511ee983f00163e085602','用户组与用户删除','',1,1,'','F','system:userGroupUser:remove',1,'icon-default',0,'',NULL,0,0,'7ede62156f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:52',NULL,NULL,NULL,NULL,NULL,'N'),('7eded4866f2511ee983f00163e085602','用户组与用户导入','',1,1,'','F','system:userGroupUser:import',1,'icon-default',0,'',NULL,0,0,'7ede62156f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:52',NULL,NULL,NULL,NULL,NULL,'N'),('7edefaab6f2511ee983f00163e085602','用户组与用户导出','',1,1,'','F','system:userGroupUser:export',1,'icon-default',0,'',NULL,0,0,'7ede62156f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:52',NULL,NULL,NULL,NULL,NULL,'N'),('85a389856f2511ee983f00163e085602','用户管理','system_user',110,1,'/system/user','C','system:user:list',1,'icon-default',0,'self',NULL,0,0,'1803694748176957442','16663888968888889','system-default',1,'188666666666','2023-10-20 16:49:04','188666666666','2024-06-20 15:42:44',NULL,NULL,NULL,'N'),('85a389866f2511ee983f00163e085602','用户列表','',1,1,'','F','system:user:list',1,'icon-default',0,'',NULL,0,0,'85a389856f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:49:04',NULL,NULL,NULL,NULL,NULL,'N'),('85a3afab6f2511ee983f00163e085602','用户详情','',1,1,'','F','system:user:query',1,'icon-default',0,'',NULL,0,0,'85a389856f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:49:04',NULL,NULL,NULL,NULL,NULL,'N'),('85a3d5d16f2511ee983f00163e085602','用户新增','',1,1,'','F','system:user:add',1,'icon-default',0,'',NULL,0,0,'85a389856f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:49:04',NULL,NULL,NULL,NULL,NULL,'N'),('85a3d5d26f2511ee983f00163e085602','用户修改','',1,1,'','F','system:user:edit',1,'icon-default',0,'',NULL,0,0,'85a389856f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:49:04',NULL,NULL,NULL,NULL,NULL,'N'),('85a3fbf66f2511ee983f00163e085602','用户删除','',1,1,'','F','system:user:remove',1,'icon-default',0,'',NULL,0,0,'85a389856f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:49:04',NULL,NULL,NULL,NULL,NULL,'N'),('85a4221c6f2511ee983f00163e085602','用户导入','',1,1,'','F','system:user:import',1,'icon-default',0,'',NULL,0,0,'85a389856f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:49:04',NULL,NULL,NULL,NULL,NULL,'N'),('85a4221d6f2511ee983f00163e085602','用户导出','',1,1,'','F','system:user:export',1,'icon-default',0,'',NULL,0,0,'85a389856f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:49:04',NULL,NULL,NULL,NULL,NULL,'N'),('888f1f526b0b11ed9d555855a3323333','我发起的','task_instance',4,1,'/task/instance','C','wfs:processInstance:list',1,'icon-default',0,'self',NULL,0,0,'88ff1f526b0b11ed9d555855a3321111','16663888968888889','system-default',1,'188666666666','2022-11-14 10:27:21',NULL,NULL,NULL,NULL,NULL,'N'),('88ff1f526b0b11ed9d555855a3321111','我的流程','task',30,1,'/task','M','wfs:task:list',1,'mdi:monitor-dashboard',0,'basic',NULL,0,0,'0','16663888968888889','system-default',1,'188666666666','2022-11-14 10:24:11',NULL,NULL,NULL,NULL,NULL,'N'),('88ff1f526b0b11ed9d555855a3322222','待办','task_todo',1,1,'/task/todo','C','wfs:task:list',1,'icon-default',0,'self',NULL,0,0,'88ff1f526b0b11ed9d555855a3321111','16663888968888889','system-default',1,'188666666666','2022-11-14 10:25:55',NULL,NULL,NULL,NULL,NULL,'N'),('88ff1f526b0b11ed9d555855a3323333','已办','task_done',2,1,'/task/done','C','wfs:task:list',1,'icon-default',0,'self',NULL,0,0,'88ff1f526b0b11ed9d555855a3321111','16663888968888889','system-default',1,'188666666666','2022-11-14 10:27:21',NULL,NULL,NULL,NULL,NULL,'N'),('8ad066416f2511ee983f00163e085602','用户的岗位','system_user-position',999,1,'/system/user-position','C','system:userPosition:list',1,'icon-default',0,'self',NULL,0,0,'1663888968294588891','16663888968888889','system-default',1,'188666666666','2023-10-20 16:49:12',NULL,NULL,NULL,NULL,NULL,'N'),('8ad08c676f2511ee983f00163e085602','用户的岗位列表','',1,1,'','F','system:userPosition:list',1,'icon-default',0,'',NULL,0,0,'8ad066416f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:49:12',NULL,NULL,NULL,NULL,NULL,'N'),('8ad08c686f2511ee983f00163e085602','用户的岗位详情','',1,1,'','F','system:userPosition:query',1,'icon-default',0,'',NULL,0,0,'8ad066416f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:49:12',NULL,NULL,NULL,NULL,NULL,'N'),('8ad0b28c6f2511ee983f00163e085602','用户的岗位新增','',1,1,'','F','system:userPosition:add',1,'icon-default',0,'',NULL,0,0,'8ad066416f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:49:12',NULL,NULL,NULL,NULL,NULL,'N'),('8ad0b28d6f2511ee983f00163e085602','用户的岗位修改','',1,1,'','F','system:userPosition:edit',1,'icon-default',0,'',NULL,0,0,'8ad066416f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:49:12',NULL,NULL,NULL,NULL,NULL,'N'),('8ad0d8b26f2511ee983f00163e085602','用户的岗位删除','',1,1,'','F','system:userPosition:remove',1,'icon-default',0,'',NULL,0,0,'8ad066416f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:49:12',NULL,NULL,NULL,NULL,NULL,'N'),('8ad0fed86f2511ee983f00163e085602','用户的岗位导入','',1,1,'','F','system:userPosition:import',1,'icon-default',0,'',NULL,0,0,'8ad066416f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:49:12',NULL,NULL,NULL,NULL,NULL,'N'),('8ad124fd6f2511ee983f00163e085602','用户的岗位导出','',1,1,'','F','system:userPosition:export',1,'icon-default',0,'',NULL,0,0,'8ad066416f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:49:12',NULL,NULL,NULL,NULL,NULL,'N'),('91f5a3f8bfe211f18fc3a5e694323b94','任务报警列表','job_alarm',4,1,'/job/alarm','C','schedule:alarm:list',1,'icon-default',0,'self',NULL,0,0,'6c1d7c80bf4411f18fc3a5e694323b94','16663888968888889','system-default',1,'188666666666','2026-10-04 18:58:45',NULL,NULL,NULL,NULL,NULL,'N'),('94eaa51d6f2511ee983f00163e085602','用户服务组织','system_user-service-org',999,1,'/system/user-service-org','C','system:userServiceOrg:list',1,'icon-default',0,'self',NULL,0,0,'1663888968294588891','16663888968888889','system-default',1,'188666666666','2023-10-20 16:49:29',NULL,NULL,NULL,NULL,NULL,'N'),('94eaa51e6f2511ee983f00163e085602','用户服务组织列表','',1,1,'','F','system:userServiceOrg:list',1,'icon-default',0,'',NULL,0,0,'94eaa51d6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:49:29',NULL,NULL,NULL,NULL,NULL,'N'),('94eacb426f2511ee983f00163e085602','用户服务组织详情','',1,1,'','F','system:userServiceOrg:query',1,'icon-default',0,'',NULL,0,0,'94eaa51d6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:49:29',NULL,NULL,NULL,NULL,NULL,'N'),('94eaf1686f2511ee983f00163e085602','用户服务组织新增','',1,1,'','F','system:userServiceOrg:add',1,'icon-default',0,'',NULL,0,0,'94eaa51d6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:49:29',NULL,NULL,NULL,NULL,NULL,'N'),('94eaf1696f2511ee983f00163e085602','用户服务组织修改','',1,1,'','F','system:userServiceOrg:edit',1,'icon-default',0,'',NULL,0,0,'94eaa51d6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:49:29',NULL,NULL,NULL,NULL,NULL,'N'),('94eb178e6f2511ee983f00163e085602','用户服务组织删除','',1,1,'','F','system:userServiceOrg:remove',1,'icon-default',0,'',NULL,0,0,'94eaa51d6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:49:29',NULL,NULL,NULL,NULL,NULL,'N'),('94eb178f6f2511ee983f00163e085602','用户服务组织导入','',1,1,'','F','system:userServiceOrg:import',1,'icon-default',0,'',NULL,0,0,'94eaa51d6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:49:29',NULL,NULL,NULL,NULL,NULL,'N'),('94eb3db36f2511ee983f00163e085602','用户服务组织导出','',1,1,'','F','system:userServiceOrg:export',1,'icon-default',0,'',NULL,0,0,'94eaa51d6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:49:29',NULL,NULL,NULL,NULL,NULL,'N'),('9a36d84cf31b11ef905d78fb839f9406','动态数据库','dynamic_database',1,1,'/dynamic/database','C','dynamic:database:list',1,'icon-default',0,'self',NULL,0,0,'470abfd0f31b11ef905d78fb839f9406','16663888968888889','system-default',1,'188666666666','2025-02-25 09:55:34',NULL,NULL,NULL,NULL,NULL,'N'),('9a372feaf31b11ef905d78fb839f9406','动态数据库列表','',6,1,'','F','dynamic:database:list',1,'icon-default',0,'',NULL,0,0,'9a36d84cf31b11ef905d78fb839f9406','16663888968888889','system-default',1,'188666666666','2025-02-25 09:55:34',NULL,NULL,NULL,NULL,NULL,'N'),('9a373c88f31b11ef905d78fb839f9406','动态数据库详情','',1,1,'','F','dynamic:database:query',1,'icon-default',0,'',NULL,0,0,'9a36d84cf31b11ef905d78fb839f9406','16663888968888889','system-default',1,'188666666666','2025-02-25 09:55:34',NULL,NULL,NULL,NULL,NULL,'N'),('9a374b88f31b11ef905d78fb839f9406','动态数据库新增','',5,1,'','F','dynamic:database:add',1,'icon-default',0,'',NULL,0,0,'9a36d84cf31b11ef905d78fb839f9406','16663888968888889','system-default',1,'188666666666','2025-02-25 09:55:34',NULL,NULL,NULL,NULL,NULL,'N'),('9a3757ccf31b11ef905d78fb839f9406','动态数据库修改','',4,1,'','F','dynamic:database:edit',1,'icon-default',0,'',NULL,0,0,'9a36d84cf31b11ef905d78fb839f9406','16663888968888889','system-default',1,'188666666666','2025-02-25 09:55:34',NULL,NULL,NULL,NULL,NULL,'N'),('9a3765d2f31b11ef905d78fb839f9406','动态数据库删除','',3,1,'','F','dynamic:database:remove',1,'icon-default',0,'',NULL,0,0,'9a36d84cf31b11ef905d78fb839f9406','16663888968888889','system-default',1,'188666666666','2025-02-25 09:55:34',NULL,NULL,NULL,NULL,NULL,'N'),('9a3776eef31b11ef905d78fb839f9406','动态数据库导入','',2,1,'','F','dynamic:database:import',1,'icon-default',0,'',NULL,0,0,'9a36d84cf31b11ef905d78fb839f9406','16663888968888889','system-default',1,'188666666666','2025-02-25 09:55:34',NULL,NULL,NULL,NULL,NULL,'N'),('9a378382f31b11ef905d78fb839f9406','动态数据库导出','',1,1,'','F','dynamic:database:export',1,'icon-default',0,'',NULL,0,0,'9a36d84cf31b11ef905d78fb839f9406','16663888968888889','system-default',1,'188666666666','2025-02-25 09:55:34',NULL,NULL,NULL,NULL,NULL,'N'),('9a71582cdf9e11ee9b3d00163e085602','系统登录日志','message_system-log-login',160,1,'/message/system-log-login','C','message:systemLogLogin:list',1,'icon-default',0,'self',NULL,0,0,'1814169974018928641','16663888968888889','system-default',1,'188666666666','2024-03-11 19:57:58','188666666666','2024-07-19 13:27:31',NULL,NULL,NULL,'N'),('9a717e52df9e11ee9b3d00163e085602','系统登录日志列表','',1,1,'','F','message:systemLogLogin:list',1,'icon-default',0,'',NULL,0,0,'9a71582cdf9e11ee9b3d00163e085602','16663888968888889','system-default',1,'188666666666','2024-03-11 19:57:58',NULL,NULL,NULL,NULL,NULL,'N'),('9a717e53df9e11ee9b3d00163e085602','系统登录日志详情','',1,1,'','F','message:systemLogLogin:query',1,'icon-default',0,'',NULL,0,0,'9a71582cdf9e11ee9b3d00163e085602','16663888968888889','system-default',1,'188666666666','2024-03-11 19:57:58',NULL,NULL,NULL,NULL,NULL,'N'),('9a71a478df9e11ee9b3d00163e085602','系统登录日志新增','',1,1,'','F','message:systemLogLogin:add',1,'icon-default',0,'',NULL,0,0,'9a71582cdf9e11ee9b3d00163e085602','16663888968888889','system-default',1,'188666666666','2024-03-11 19:57:58',NULL,NULL,NULL,NULL,NULL,'N'),('9a71a479df9e11ee9b3d00163e085602','系统登录日志修改','',1,1,'','F','message:systemLogLogin:edit',1,'icon-default',0,'',NULL,0,0,'9a71582cdf9e11ee9b3d00163e085602','16663888968888889','system-default',1,'188666666666','2024-03-11 19:57:58',NULL,NULL,NULL,NULL,NULL,'N'),('9a71ca9edf9e11ee9b3d00163e085602','系统登录日志删除','',1,1,'','F','message:systemLogLogin:remove',1,'icon-default',0,'',NULL,0,0,'9a71582cdf9e11ee9b3d00163e085602','16663888968888889','system-default',1,'188666666666','2024-03-11 19:57:58',NULL,NULL,NULL,NULL,NULL,'N'),('9a71f0c4df9e11ee9b3d00163e085602','系统登录日志导入','',1,1,'','F','message:systemLogLogin:import',1,'icon-default',0,'',NULL,0,0,'9a71582cdf9e11ee9b3d00163e085602','16663888968888889','system-default',1,'188666666666','2024-03-11 19:57:58',NULL,NULL,NULL,NULL,NULL,'N'),('9a71f0c5df9e11ee9b3d00163e085602','系统登录日志导出','',1,1,'','F','message:systemLogLogin:export',1,'icon-default',0,'',NULL,0,0,'9a71582cdf9e11ee9b3d00163e085602','16663888968888889','system-default',1,'188666666666','2024-03-11 19:57:58',NULL,NULL,NULL,NULL,NULL,'N'),('9b9461826f2511ee983f00163e085602','社交帐号关联系统账号','system_user-social-user',999,1,'/system/user-social-user','C','system:userSocialUser:list',1,'icon-default',0,'self',NULL,0,0,'1663888968294588891','16663888968888889','system-default',1,'188666666666','2023-10-20 16:49:40',NULL,NULL,NULL,NULL,NULL,'N'),('9b9461836f2511ee983f00163e085602','社交帐号关联系统账号列表','',1,1,'','F','system:userSocialUser:list',1,'icon-default',0,'',NULL,0,0,'9b9461826f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:49:40',NULL,NULL,NULL,NULL,NULL,'N'),('9b9487a86f2511ee983f00163e085602','社交帐号关联系统账号详情','',1,1,'','F','system:userSocialUser:query',1,'icon-default',0,'',NULL,0,0,'9b9461826f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:49:40',NULL,NULL,NULL,NULL,NULL,'N'),('9b94adcd6f2511ee983f00163e085602','社交帐号关联系统账号新增','',1,1,'','F','system:userSocialUser:add',1,'icon-default',0,'',NULL,0,0,'9b9461826f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:49:41',NULL,NULL,NULL,NULL,NULL,'N'),('9b94adce6f2511ee983f00163e085602','社交帐号关联系统账号修改','',1,1,'','F','system:userSocialUser:edit',1,'icon-default',0,'',NULL,0,0,'9b9461826f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:49:41',NULL,NULL,NULL,NULL,NULL,'N'),('9b94d3f36f2511ee983f00163e085602','社交帐号关联系统账号删除','',1,1,'','F','system:userSocialUser:remove',1,'icon-default',0,'',NULL,0,0,'9b9461826f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:49:41',NULL,NULL,NULL,NULL,NULL,'N'),('9b94d3f46f2511ee983f00163e085602','社交帐号关联系统账号导入','',1,1,'','F','system:userSocialUser:import',1,'icon-default',0,'',NULL,0,0,'9b9461826f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:49:41',NULL,NULL,NULL,NULL,NULL,'N'),('9b94fa196f2511ee983f00163e085602','社交帐号关联系统账号导出','',1,1,'','F','system:userSocialUser:export',1,'icon-default',0,'',NULL,0,0,'9b9461826f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:49:41',NULL,NULL,NULL,NULL,NULL,'N'),('a0ba9526df9e11ee9b3d00163e085602','系统操作日志','message_system-log-operation',150,1,'/message/system-log-operation','C','message:systemLogOperation:list',1,'icon-default',0,'self',NULL,0,0,'1814169974018928641','16663888968888889','system-default',1,'188666666666','2024-03-11 19:58:08','188666666666','2024-07-19 13:27:42',NULL,NULL,NULL,'N'),('a0ba9527df9e11ee9b3d00163e085602','系统操作日志列表','',1,1,'','F','message:systemLogOperation:list',1,'icon-default',0,'',NULL,0,0,'a0ba9526df9e11ee9b3d00163e085602','16663888968888889','system-default',1,'188666666666','2024-03-11 19:58:08',NULL,NULL,NULL,NULL,NULL,'N'),('a0babb4cdf9e11ee9b3d00163e085602','系统操作日志详情','',1,1,'','F','message:systemLogOperation:query',1,'icon-default',0,'',NULL,0,0,'a0ba9526df9e11ee9b3d00163e085602','16663888968888889','system-default',1,'188666666666','2024-03-11 19:58:08',NULL,NULL,NULL,NULL,NULL,'N'),('a0bae172df9e11ee9b3d00163e085602','系统操作日志新增','',1,1,'','F','message:systemLogOperation:add',1,'icon-default',0,'',NULL,0,0,'a0ba9526df9e11ee9b3d00163e085602','16663888968888889','system-default',1,'188666666666','2024-03-11 19:58:08',NULL,NULL,NULL,NULL,NULL,'N'),('a0bae173df9e11ee9b3d00163e085602','系统操作日志修改','',1,1,'','F','message:systemLogOperation:edit',1,'icon-default',0,'',NULL,0,0,'a0ba9526df9e11ee9b3d00163e085602','16663888968888889','system-default',1,'188666666666','2024-03-11 19:58:08',NULL,NULL,NULL,NULL,NULL,'N'),('a0bb0798df9e11ee9b3d00163e085602','系统操作日志删除','',1,1,'','F','message:systemLogOperation:remove',1,'icon-default',0,'',NULL,0,0,'a0ba9526df9e11ee9b3d00163e085602','16663888968888889','system-default',1,'188666666666','2024-03-11 19:58:08',NULL,NULL,NULL,NULL,NULL,'N'),('a0bb0799df9e11ee9b3d00163e085602','系统操作日志导入','',1,1,'','F','message:systemLogOperation:import',1,'icon-default',0,'',NULL,0,0,'a0ba9526df9e11ee9b3d00163e085602','16663888968888889','system-default',1,'188666666666','2024-03-11 19:58:08',NULL,NULL,NULL,NULL,NULL,'N'),('a0bb2dbedf9e11ee9b3d00163e085602','系统操作日志导出','',1,1,'','F','message:systemLogOperation:export',1,'icon-default',0,'',NULL,0,0,'a0ba9526df9e11ee9b3d00163e085602','16663888968888889','system-default',1,'188666666666','2024-03-11 19:58:08',NULL,NULL,NULL,NULL,NULL,'N'),('a2ba9abcf31b11ef905d78fb839f9406','表单的列','dynamic_form-column',1,1,'/dynamic/form-column','C','dynamic:formColumn:list',1,'icon-default',0,'self',NULL,0,0,'470abfd0f31b11ef905d78fb839f9406','16663888968888889','system-default',1,'188666666666','2025-02-25 09:55:49','188666666666','2025-02-25 09:58:08',NULL,NULL,NULL,'N'),('a2baac46f31b11ef905d78fb839f9406','表单的列的数据列表','',6,1,'','F','dynamic:formColumn:list',1,'icon-default',0,'',NULL,0,0,'a2ba9abcf31b11ef905d78fb839f9406','16663888968888889','system-default',1,'188666666666','2025-02-25 09:55:49',NULL,NULL,NULL,NULL,NULL,'N'),('a2bab722f31b11ef905d78fb839f9406','表单的列的数据详情','',1,1,'','F','dynamic:formColumn:query',1,'icon-default',0,'',NULL,0,0,'a2ba9abcf31b11ef905d78fb839f9406','16663888968888889','system-default',1,'188666666666','2025-02-25 09:55:49',NULL,NULL,NULL,NULL,NULL,'N'),('a2bac122f31b11ef905d78fb839f9406','表单的列的数据新增','',5,1,'','F','dynamic:formColumn:add',1,'icon-default',0,'',NULL,0,0,'a2ba9abcf31b11ef905d78fb839f9406','16663888968888889','system-default',1,'188666666666','2025-02-25 09:55:49',NULL,NULL,NULL,NULL,NULL,'N'),('a2bacd7af31b11ef905d78fb839f9406','表单的列的数据修改','',4,1,'','F','dynamic:formColumn:edit',1,'icon-default',0,'',NULL,0,0,'a2ba9abcf31b11ef905d78fb839f9406','16663888968888889','system-default',1,'188666666666','2025-02-25 09:55:49',NULL,NULL,NULL,NULL,NULL,'N'),('a2bad770f31b11ef905d78fb839f9406','表单的列的数据删除','',3,1,'','F','dynamic:formColumn:remove',1,'icon-default',0,'',NULL,0,0,'a2ba9abcf31b11ef905d78fb839f9406','16663888968888889','system-default',1,'188666666666','2025-02-25 09:55:49',NULL,NULL,NULL,NULL,NULL,'N'),('a2bae116f31b11ef905d78fb839f9406','表单的列的数据导入','',2,1,'','F','dynamic:formColumn:import',1,'icon-default',0,'',NULL,0,0,'a2ba9abcf31b11ef905d78fb839f9406','16663888968888889','system-default',1,'188666666666','2025-02-25 09:55:49',NULL,NULL,NULL,NULL,NULL,'N'),('a2baea9ef31b11ef905d78fb839f9406','表单的列的数据导出','',1,1,'','F','dynamic:formColumn:export',1,'icon-default',0,'',NULL,0,0,'a2ba9abcf31b11ef905d78fb839f9406','16663888968888889','system-default',1,'188666666666','2025-02-25 09:55:49',NULL,NULL,NULL,NULL,NULL,'N'),('a64beef4df9e11ee9b3d00163e085602','系统消息','message_system-message',120,1,'/message/system-message','C','message:systemMessage:list',1,'icon-default',0,'self',NULL,0,0,'1798986040628969473','16663888968888889','system-default',1,'188666666666','2024-03-11 19:58:18','188666666666','2024-06-07 15:23:07',NULL,NULL,NULL,'N'),('a64beef5df9e11ee9b3d00163e085602','系统消息列表','',1,1,'','F','message:systemMessage:list',1,'icon-default',0,'',NULL,0,0,'a64beef4df9e11ee9b3d00163e085602','16663888968888889','system-default',1,'188666666666','2024-03-11 19:58:18',NULL,NULL,NULL,NULL,NULL,'N'),('a64c151adf9e11ee9b3d00163e085602','系统消息详情','',1,1,'','F','message:systemMessage:query',1,'icon-default',0,'',NULL,0,0,'a64beef4df9e11ee9b3d00163e085602','16663888968888889','system-default',1,'188666666666','2024-03-11 19:58:18',NULL,NULL,NULL,NULL,NULL,'N'),('a64c151bdf9e11ee9b3d00163e085602','系统消息新增','',1,1,'','F','message:systemMessage:add',1,'icon-default',0,'',NULL,0,0,'a64beef4df9e11ee9b3d00163e085602','16663888968888889','system-default',1,'188666666666','2024-03-11 19:58:18',NULL,NULL,NULL,NULL,NULL,'N'),('a64c878cdf9e11ee9b3d00163e085602','系统消息修改','',1,1,'','F','message:systemMessage:edit',1,'icon-default',0,'',NULL,0,0,'a64beef4df9e11ee9b3d00163e085602','16663888968888889','system-default',1,'188666666666','2024-03-11 19:58:18',NULL,NULL,NULL,NULL,NULL,'N'),('a64c878ddf9e11ee9b3d00163e085602','系统消息删除','',1,1,'','F','message:systemMessage:remove',1,'icon-default',0,'',NULL,0,0,'a64beef4df9e11ee9b3d00163e085602','16663888968888889','system-default',1,'188666666666','2024-03-11 19:58:18',NULL,NULL,NULL,NULL,NULL,'N'),('a64cadb2df9e11ee9b3d00163e085602','系统消息导入','',1,1,'','F','message:systemMessage:import',1,'icon-default',0,'',NULL,0,0,'a64beef4df9e11ee9b3d00163e085602','16663888968888889','system-default',1,'188666666666','2024-03-11 19:58:18',NULL,NULL,NULL,NULL,NULL,'N'),('a64cadb3df9e11ee9b3d00163e085602','系统消息导出','',1,1,'','F','message:systemMessage:export',1,'icon-default',0,'',NULL,0,0,'a64beef4df9e11ee9b3d00163e085602','16663888968888889','system-default',1,'188666666666','2024-03-11 19:58:18',NULL,NULL,NULL,NULL,NULL,'N'),('ac47fd90f31b11ef905d78fb839f9406','表单的历史版本','dynamic_form-log',1,1,'/dynamic/form-log','C','dynamic:formLog:list',1,'icon-default',0,'self',NULL,0,0,'470abfd0f31b11ef905d78fb839f9406','16663888968888889','system-default',1,'188666666666','2025-02-25 09:56:05','188666666666','2025-02-25 09:58:22',NULL,NULL,NULL,'N'),('ac481618f31b11ef905d78fb839f9406','表单的历史版本列表','',6,1,'','F','dynamic:formLog:list',1,'icon-default',0,'',NULL,0,0,'ac47fd90f31b11ef905d78fb839f9406','16663888968888889','system-default',1,'188666666666','2025-02-25 09:56:05',NULL,NULL,NULL,NULL,NULL,'N'),('ac482248f31b11ef905d78fb839f9406','表单的历史版本详情','',1,1,'','F','dynamic:formLog:query',1,'icon-default',0,'',NULL,0,0,'ac47fd90f31b11ef905d78fb839f9406','16663888968888889','system-default',1,'188666666666','2025-02-25 09:56:05',NULL,NULL,NULL,NULL,NULL,'N'),('ac482d7ef31b11ef905d78fb839f9406','表单的历史版本新增','',5,1,'','F','dynamic:formLog:add',1,'icon-default',0,'',NULL,0,0,'ac47fd90f31b11ef905d78fb839f9406','16663888968888889','system-default',1,'188666666666','2025-02-25 09:56:05',NULL,NULL,NULL,NULL,NULL,'N'),('ac483846f31b11ef905d78fb839f9406','表单的历史版本修改','',4,1,'','F','dynamic:formLog:edit',1,'icon-default',0,'',NULL,0,0,'ac47fd90f31b11ef905d78fb839f9406','16663888968888889','system-default',1,'188666666666','2025-02-25 09:56:05',NULL,NULL,NULL,NULL,NULL,'N'),('ac484340f31b11ef905d78fb839f9406','表单的历史版本删除','',3,1,'','F','dynamic:formLog:remove',1,'icon-default',0,'',NULL,0,0,'ac47fd90f31b11ef905d78fb839f9406','16663888968888889','system-default',1,'188666666666','2025-02-25 09:56:05',NULL,NULL,NULL,NULL,NULL,'N'),('ac486618f31b11ef905d78fb839f9406','表单的历史版本导入','',2,1,'','F','dynamic:formLog:import',1,'icon-default',0,'',NULL,0,0,'ac47fd90f31b11ef905d78fb839f9406','16663888968888889','system-default',1,'188666666666','2025-02-25 09:56:05',NULL,NULL,NULL,NULL,NULL,'N'),('ac48752cf31b11ef905d78fb839f9406','表单的历史版本导出','',1,1,'','F','dynamic:formLog:export',1,'icon-default',0,'',NULL,0,0,'ac47fd90f31b11ef905d78fb839f9406','16663888968888889','system-default',1,'188666666666','2025-02-25 09:56:05',NULL,NULL,NULL,NULL,NULL,'N'),('b1e4ac20f31b11ef905d78fbdead0001','报表设计器','report-designer',15,1,'/report-designer','C','report:dataset:add',1,'icon-default',0,'self',NULL,0,0,'470abfd0f31b11ef905d78fb839f9406','16663888968888889','system-default',1,'188666666666','2026-10-02 23:15:46',NULL,NULL,NULL,NULL,NULL,'N'),('b1efac20f31b11ef905d78fb839f9406','表单','dynamic_form',1,1,'/dynamic/form','C','dynamic:form:list',1,'icon-default',0,'self',NULL,0,0,'470abfd0f31b11ef905d78fb839f9406','16663888968888889','system-default',1,'188666666666','2025-02-25 09:56:14','188666666666','2025-02-25 09:58:35',NULL,NULL,NULL,'N'),('b1efc02af31b11ef905d78fb839f9406','表单列表','',6,1,'','F','dynamic:form:list',1,'icon-default',0,'',NULL,0,0,'b1efac20f31b11ef905d78fb839f9406','16663888968888889','system-default',1,'188666666666','2025-02-25 09:56:14',NULL,NULL,NULL,NULL,NULL,'N'),('b1efcf52f31b11ef905d78fb839f9406','表单详情','',1,1,'','F','dynamic:form:query',1,'icon-default',0,'',NULL,0,0,'b1efac20f31b11ef905d78fb839f9406','16663888968888889','system-default',1,'188666666666','2025-02-25 09:56:14',NULL,NULL,NULL,NULL,NULL,'N'),('b1efe41af31b11ef905d78fb839f9406','表单新增','',5,1,'','F','dynamic:form:add',1,'icon-default',0,'',NULL,0,0,'b1efac20f31b11ef905d78fb839f9406','16663888968888889','system-default',1,'188666666666','2025-02-25 09:56:14',NULL,NULL,NULL,NULL,NULL,'N'),('b1eff57cf31b11ef905d78fb839f9406','表单修改','',4,1,'','F','dynamic:form:edit',1,'icon-default',0,'',NULL,0,0,'b1efac20f31b11ef905d78fb839f9406','16663888968888889','system-default',1,'188666666666','2025-02-25 09:56:14',NULL,NULL,NULL,NULL,NULL,'N'),('b1f006d4f31b11ef905d78fb839f9406','表单删除','',3,1,'','F','dynamic:form:remove',1,'icon-default',0,'',NULL,0,0,'b1efac20f31b11ef905d78fb839f9406','16663888968888889','system-default',1,'188666666666','2025-02-25 09:56:14',NULL,NULL,NULL,NULL,NULL,'N'),('b1f01e3af31b11ef905d78fb839f9406','表单导入','',2,1,'','F','dynamic:form:import',1,'icon-default',0,'',NULL,0,0,'b1efac20f31b11ef905d78fb839f9406','16663888968888889','system-default',1,'188666666666','2025-02-25 09:56:14',NULL,NULL,NULL,NULL,NULL,'N'),('b1f0338ef31b11ef905d78fb839f9406','表单导出','',1,1,'','F','dynamic:form:export',1,'icon-default',0,'',NULL,0,0,'b1efac20f31b11ef905d78fb839f9406','16663888968888889','system-default',1,'188666666666','2025-02-25 09:56:14',NULL,NULL,NULL,NULL,NULL,'N'),('bfa051ec6f2411ee983f00163e085602','第三方应用配置(小程序)','system_app-config',100,1,'/system/app-config','C','system:appConfig:list',1,'icon-default',0,'self',NULL,0,0,'1663888968294588891','16663888968888889','system-default',1,'188666666666','2023-10-20 16:43:31',NULL,NULL,NULL,NULL,NULL,'N'),('bfa078126f2411ee983f00163e085602','第三方应用配置(小程序)列表','',1,1,'','F','system:appConfig:list',1,'icon-default',0,'',NULL,0,0,'bfa051ec6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:43:31',NULL,NULL,NULL,NULL,NULL,'N'),('bfa078136f2411ee983f00163e085602','第三方应用配置(小程序)详情','',1,1,'','F','system:appConfig:query',1,'icon-default',0,'',NULL,0,0,'bfa051ec6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:43:31',NULL,NULL,NULL,NULL,NULL,'N'),('bfa09e376f2411ee983f00163e085602','第三方应用配置(小程序)新增','',1,1,'','F','system:appConfig:add',1,'icon-default',0,'',NULL,0,0,'bfa051ec6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:43:31',NULL,NULL,NULL,NULL,NULL,'N'),('bfa09e386f2411ee983f00163e085602','第三方应用配置(小程序)修改','',1,1,'','F','system:appConfig:edit',1,'icon-default',0,'',NULL,0,0,'bfa051ec6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:43:31',NULL,NULL,NULL,NULL,NULL,'N'),('bfa0c45d6f2411ee983f00163e085602','第三方应用配置(小程序)删除','',1,1,'','F','system:appConfig:remove',1,'icon-default',0,'',NULL,0,0,'bfa051ec6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:43:31',NULL,NULL,NULL,NULL,NULL,'N'),('bfa0ea826f2411ee983f00163e085602','第三方应用配置(小程序)导入','',1,1,'','F','system:appConfig:import',1,'icon-default',0,'',NULL,0,0,'bfa051ec6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:43:32',NULL,NULL,NULL,NULL,NULL,'N'),('bfa0ea836f2411ee983f00163e085602','第三方应用配置(小程序)导出','',1,1,'','F','system:appConfig:export',1,'icon-default',0,'',NULL,0,0,'bfa051ec6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:43:32',NULL,NULL,NULL,NULL,NULL,'N'),('c55cb4616f1911ee983f00163e085602','文件信息','system_file-info',90,1,'/system/file-info','C','system:fileInfo:list',1,'icon-default',0,'self',NULL,0,0,'1663888968294588891','16663888968888889','system-default',1,'188666666666','2023-10-20 15:24:57',NULL,NULL,NULL,NULL,NULL,'N'),('c55cda866f1911ee983f00163e085602','文件信息列表','',1,1,'','F','system:fileInfo:list',1,'icon-default',0,'',NULL,0,0,'c55cb4616f1911ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 15:24:57',NULL,NULL,NULL,NULL,NULL,'N'),('c55cda876f1911ee983f00163e085602','文件信息详情','',1,1,'','F','system:fileInfo:query',1,'icon-default',0,'',NULL,0,0,'c55cb4616f1911ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 15:24:57',NULL,NULL,NULL,NULL,NULL,'N'),('c55d00ac6f1911ee983f00163e085602','文件信息新增','',1,1,'','F','system:fileInfo:add',1,'icon-default',0,'',NULL,0,0,'c55cb4616f1911ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 15:24:57',NULL,NULL,NULL,NULL,NULL,'N'),('c55d00ad6f1911ee983f00163e085602','文件信息修改','',1,1,'','F','system:fileInfo:edit',1,'icon-default',0,'',NULL,0,0,'c55cb4616f1911ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 15:24:57',NULL,NULL,NULL,NULL,NULL,'N'),('c55d26d26f1911ee983f00163e085602','文件信息删除','',1,1,'','F','system:fileInfo:remove',1,'icon-default',0,'',NULL,0,0,'c55cb4616f1911ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 15:24:57',NULL,NULL,NULL,NULL,NULL,'N'),('c55d26d36f1911ee983f00163e085602','文件信息导入','',1,1,'','F','system:fileInfo:import',1,'icon-default',0,'',NULL,0,0,'c55cb4616f1911ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 15:24:57',NULL,NULL,NULL,NULL,NULL,'N'),('c55d4cf76f1911ee983f00163e085602','文件信息导出','',1,1,'','F','system:fileInfo:export',1,'icon-default',0,'',NULL,0,0,'c55cb4616f1911ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 15:24:57',NULL,NULL,NULL,NULL,NULL,'N'),('cd2e0e2e6f2411ee983f00163e085602','应用管理','system_app-info',170,1,'/system/app-info','C','system:appInfo:list',1,'icon-default',0,'self',NULL,0,0,'1798986040628969473','16663888968888889','system-default',1,'188666666666','2023-10-20 16:43:54','188666666666','2024-06-07 15:20:53',NULL,NULL,NULL,'N'),('cd2e34546f2411ee983f00163e085602','应用列表','',1,1,'','F','system:appInfo:list',1,'icon-default',0,'',NULL,0,0,'cd2e0e2e6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:43:54',NULL,NULL,NULL,NULL,NULL,'N'),('cd2e34556f2411ee983f00163e085602','应用详情','',1,1,'','F','system:appInfo:query',1,'icon-default',0,'',NULL,0,0,'cd2e0e2e6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:43:54',NULL,NULL,NULL,NULL,NULL,'N'),('cd2e5a796f2411ee983f00163e085602','应用新增','',1,1,'','F','system:appInfo:add',1,'icon-default',0,'',NULL,0,0,'cd2e0e2e6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:43:54',NULL,NULL,NULL,NULL,NULL,'N'),('cd2e5a7a6f2411ee983f00163e085602','应用修改','',1,1,'','F','system:appInfo:edit',1,'icon-default',0,'',NULL,0,0,'cd2e0e2e6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:43:54',NULL,NULL,NULL,NULL,NULL,'N'),('cd2e809f6f2411ee983f00163e085602','应用删除','',1,1,'','F','system:appInfo:remove',1,'icon-default',0,'',NULL,0,0,'cd2e0e2e6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:43:54',NULL,NULL,NULL,NULL,NULL,'N'),('cd2e80a06f2411ee983f00163e085602','应用导入','',1,1,'','F','system:appInfo:import',1,'icon-default',0,'',NULL,0,0,'cd2e0e2e6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:43:54',NULL,NULL,NULL,NULL,NULL,'N'),('cd2ea6c56f2411ee983f00163e085602','应用导出','',1,1,'','F','system:appInfo:export',1,'icon-default',0,'',NULL,0,0,'cd2e0e2e6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:43:54',NULL,NULL,NULL,NULL,NULL,'N'),('d17ed0c8befa11f18fc3a5e694323b94','OpenAPI凭证','system_openapi-app',190,1,'/system/openapi-app','C','system:openapi:list',1,'icon-default',0,'self',NULL,0,0,'1798986040628969473','16663888968888889','system-default',1,'188666666666','2026-10-03 15:19:49',NULL,NULL,'开放与集成-OpenAPI应用凭证与调用统计',NULL,NULL,'N'),('d17ed0f0befa11f18fc3a5e694323b94','Webhook订阅','system_webhook',191,1,'/system/webhook','C','system:webhook:list',1,'icon-default',0,'self',NULL,0,0,'1798986040628969473','16663888968888889','system-default',1,'188666666666','2026-10-03 15:19:49',NULL,NULL,'开放与集成-Webhook事件订阅与投递记录',NULL,NULL,'N'),('d17ed0fabefa11f18fc3a5e694323b94','单点登录配置','system_sso-config',192,1,'/system/sso-config','C','system:ssoConfig:list',1,'icon-default',0,'self',NULL,0,0,'1798986040628969473','16663888968888889','system-default',1,'188666666666','2026-10-03 15:19:49',NULL,NULL,'开放与集成-SSO OIDC渠道配置',NULL,NULL,'N'),('d17f3b94befa11f18fc3a5e694323b94','凭证详情','',1,1,'','F','system:openapi:query',1,'icon-default',0,'',NULL,0,0,'d17ed0c8befa11f18fc3a5e694323b94','16663888968888889','system-default',1,'188666666666','2026-10-03 15:19:49',NULL,NULL,'OpenAPI-凭证详情',NULL,NULL,'N'),('d17f3bb2befa11f18fc3a5e694323b94','凭证新增','',1,1,'','F','system:openapi:add',1,'icon-default',0,'',NULL,0,0,'d17ed0c8befa11f18fc3a5e694323b94','16663888968888889','system-default',1,'188666666666','2026-10-03 15:19:49',NULL,NULL,'OpenAPI-新建凭证（明文Secret仅此一次返回）',NULL,NULL,'N'),('d17f3bc6befa11f18fc3a5e694323b94','凭证修改','',1,1,'','F','system:openapi:edit',1,'icon-default',0,'',NULL,0,0,'d17ed0c8befa11f18fc3a5e694323b94','16663888968888889','system-default',1,'188666666666','2026-10-03 15:19:49',NULL,NULL,'OpenAPI-改名称/范围/授权表/状态',NULL,NULL,'N'),('d17f3bd0befa11f18fc3a5e694323b94','凭证删除','',1,1,'','F','system:openapi:remove',1,'icon-default',0,'',NULL,0,0,'d17ed0c8befa11f18fc3a5e694323b94','16663888968888889','system-default',1,'188666666666','2026-10-03 15:19:49',NULL,NULL,'OpenAPI-删除凭证',NULL,NULL,'N'),('d17f3bdabefa11f18fc3a5e694323b94','凭证轮换','',1,1,'','F','system:openapi:rotate',1,'icon-default',0,'',NULL,0,0,'d17ed0c8befa11f18fc3a5e694323b94','16663888968888889','system-default',1,'188666666666','2026-10-03 15:19:49',NULL,NULL,'OpenAPI-轮换Secret',NULL,NULL,'N'),('d17f3be4befa11f18fc3a5e694323b94','调用统计','',1,1,'','F','system:openapi:callLog',1,'icon-default',0,'',NULL,0,0,'d17ed0c8befa11f18fc3a5e694323b94','16663888968888889','system-default',1,'188666666666','2026-10-03 15:19:49',NULL,NULL,'OpenAPI-查看调用流水',NULL,NULL,'N'),('d17f3beebefa11f18fc3a5e694323b94','订阅详情','',1,1,'','F','system:webhook:query',1,'icon-default',0,'',NULL,0,0,'d17ed0f0befa11f18fc3a5e694323b94','16663888968888889','system-default',1,'188666666666','2026-10-03 15:19:49',NULL,NULL,'Webhook-订阅详情',NULL,NULL,'N'),('d17f3bf8befa11f18fc3a5e694323b94','订阅新增','',1,1,'','F','system:webhook:add',1,'icon-default',0,'',NULL,0,0,'d17ed0f0befa11f18fc3a5e694323b94','16663888968888889','system-default',1,'188666666666','2026-10-03 15:19:49',NULL,NULL,'Webhook-新建订阅',NULL,NULL,'N'),('d17f3c02befa11f18fc3a5e694323b94','订阅修改','',1,1,'','F','system:webhook:edit',1,'icon-default',0,'',NULL,0,0,'d17ed0f0befa11f18fc3a5e694323b94','16663888968888889','system-default',1,'188666666666','2026-10-03 15:19:49',NULL,NULL,'Webhook-修改订阅',NULL,NULL,'N'),('d17f3c0cbefa11f18fc3a5e694323b94','订阅删除','',1,1,'','F','system:webhook:remove',1,'icon-default',0,'',NULL,0,0,'d17ed0f0befa11f18fc3a5e694323b94','16663888968888889','system-default',1,'188666666666','2026-10-03 15:19:49',NULL,NULL,'Webhook-删除订阅',NULL,NULL,'N'),('d17f3c16befa11f18fc3a5e694323b94','事件重发','',1,1,'','F','system:webhook:resend',1,'icon-default',0,'',NULL,0,0,'d17ed0f0befa11f18fc3a5e694323b94','16663888968888889','system-default',1,'188666666666','2026-10-03 15:19:49',NULL,NULL,'Webhook-手动重发事件',NULL,NULL,'N'),('d17f3c20befa11f18fc3a5e694323b94','投递记录','',1,1,'','F','system:webhook:delivery',1,'icon-default',0,'',NULL,0,0,'d17ed0f0befa11f18fc3a5e694323b94','16663888968888889','system-default',1,'188666666666','2026-10-03 15:19:49',NULL,NULL,'Webhook-查看投递记录',NULL,NULL,'N'),('d17f3c2abefa11f18fc3a5e694323b94','渠道详情','',1,1,'','F','system:ssoConfig:query',1,'icon-default',0,'',NULL,0,0,'d17ed0fabefa11f18fc3a5e694323b94','16663888968888889','system-default',1,'188666666666','2026-10-03 15:19:49',NULL,NULL,'SSO-渠道详情',NULL,NULL,'N'),('d17f3c34befa11f18fc3a5e694323b94','渠道新增','',1,1,'','F','system:ssoConfig:add',1,'icon-default',0,'',NULL,0,0,'d17ed0fabefa11f18fc3a5e694323b94','16663888968888889','system-default',1,'188666666666','2026-10-03 15:19:49',NULL,NULL,'SSO-新建OIDC渠道',NULL,NULL,'N'),('d17f3c3ebefa11f18fc3a5e694323b94','渠道修改','',1,1,'','F','system:ssoConfig:edit',1,'icon-default',0,'',NULL,0,0,'d17ed0fabefa11f18fc3a5e694323b94','16663888968888889','system-default',1,'188666666666','2026-10-03 15:19:49',NULL,NULL,'SSO-修改OIDC渠道',NULL,NULL,'N'),('d17f3c48befa11f18fc3a5e694323b94','渠道删除','',1,1,'','F','system:ssoConfig:remove',1,'icon-default',0,'',NULL,0,0,'d17ed0fabefa11f18fc3a5e694323b94','16663888968888889','system-default',1,'188666666666','2026-10-03 15:19:49',NULL,NULL,'SSO-删除OIDC渠道',NULL,NULL,'N'),('d17f3c52befa11f18fc3a5e694323b94','渠道测试','',1,1,'','F','system:ssoConfig:test',1,'icon-default',0,'',NULL,0,0,'d17ed0fabefa11f18fc3a5e694323b94','16663888968888889','system-default',1,'188666666666','2026-10-03 15:19:49',NULL,NULL,'SSO-发现文档连通性测试',NULL,NULL,'N'),('d399cd6f6f2411ee983f00163e085602','应用的接口','system_app-path',999,1,'/system/app-path','C','system:appPath:list',1,'icon-default',0,'self',NULL,0,0,'1663888968294588891','16663888968888889','system-default',1,'188666666666','2023-10-20 16:44:05',NULL,NULL,NULL,NULL,NULL,'N'),('d399f3956f2411ee983f00163e085602','应用的接口列表','',1,1,'','F','system:appPath:list',1,'icon-default',0,'',NULL,0,0,'d399cd6f6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:44:05',NULL,NULL,NULL,NULL,NULL,'N'),('d39a19bb6f2411ee983f00163e085602','应用的接口详情','',1,1,'','F','system:appPath:query',1,'icon-default',0,'',NULL,0,0,'d399cd6f6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:44:05',NULL,NULL,NULL,NULL,NULL,'N'),('d39a19bc6f2411ee983f00163e085602','应用的接口新增','',1,1,'','F','system:appPath:add',1,'icon-default',0,'',NULL,0,0,'d399cd6f6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:44:05',NULL,NULL,NULL,NULL,NULL,'N'),('d39a3fe06f2411ee983f00163e085602','应用的接口修改','',1,1,'','F','system:appPath:edit',1,'icon-default',0,'',NULL,0,0,'d399cd6f6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:44:05',NULL,NULL,NULL,NULL,NULL,'N'),('d39a3fe16f2411ee983f00163e085602','应用的接口删除','',1,1,'','F','system:appPath:remove',1,'icon-default',0,'',NULL,0,0,'d399cd6f6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:44:05',NULL,NULL,NULL,NULL,NULL,'N'),('d39a66066f2411ee983f00163e085602','应用的接口导入','',1,1,'','F','system:appPath:import',1,'icon-default',0,'',NULL,0,0,'d399cd6f6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:44:05',NULL,NULL,NULL,NULL,NULL,'N'),('d39a66076f2411ee983f00163e085602','应用的接口导出','',1,1,'','F','system:appPath:export',1,'icon-default',0,'',NULL,0,0,'d399cd6f6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:44:05',NULL,NULL,NULL,NULL,NULL,'N'),('d46cd3bd6f1911ee983f00163e085602','文件配置','system_file-config',80,1,'/system/file-config','C','system:fileConfig:list',1,'icon-default',0,'self',NULL,0,0,'1798986040628969473','16663888968888889','system-default',1,'188666666666','2023-10-20 15:25:22','188666666666','2024-06-07 15:54:15',NULL,NULL,NULL,'N'),('d46cf9e26f1911ee983f00163e085602','文件配置列表','',1,1,'','F','system:fileConfig:list',1,'icon-default',0,'',NULL,0,0,'d46cd3bd6f1911ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 15:25:22',NULL,NULL,NULL,NULL,NULL,'N'),('d46d20086f1911ee983f00163e085602','文件配置详情','',1,1,'','F','system:fileConfig:query',1,'icon-default',0,'',NULL,0,0,'d46cd3bd6f1911ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 15:25:22',NULL,NULL,NULL,NULL,NULL,'N'),('d46d20096f1911ee983f00163e085602','文件配置新增','',1,1,'','F','system:fileConfig:add',1,'icon-default',0,'',NULL,0,0,'d46cd3bd6f1911ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 15:25:22',NULL,NULL,NULL,NULL,NULL,'N'),('d46d462d6f1911ee983f00163e085602','文件配置修改','',1,1,'','F','system:fileConfig:edit',1,'icon-default',0,'',NULL,0,0,'d46cd3bd6f1911ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 15:25:22',NULL,NULL,NULL,NULL,NULL,'N'),('d46d462e6f1911ee983f00163e085602','文件配置删除','',1,1,'','F','system:fileConfig:remove',1,'icon-default',0,'',NULL,0,0,'d46cd3bd6f1911ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 15:25:22',NULL,NULL,NULL,NULL,NULL,'N'),('d46d6c536f1911ee983f00163e085602','文件配置导入','',1,1,'','F','system:fileConfig:import',1,'icon-default',0,'',NULL,0,0,'d46cd3bd6f1911ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 15:25:22',NULL,NULL,NULL,NULL,NULL,'N'),('d46d92796f1911ee983f00163e085602','文件配置导出','',1,1,'','F','system:fileConfig:export',1,'icon-default',0,'',NULL,0,0,'d46cd3bd6f1911ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 15:25:22',NULL,NULL,NULL,NULL,NULL,'N'),('da0815306f2411ee983f00163e085602','系统配置','system_default-config',110,1,'/system/default-config','C','system:defaultConfig:list',1,'icon-default',0,'self',NULL,0,0,'1798986040628969473','16663888968888889','system-default',1,'188666666666','2023-10-20 16:44:16','188666666666','2024-06-07 15:54:03',NULL,NULL,NULL,'N'),('da083b566f2411ee983f00163e085602','系统配置列表','',1,1,'','F','system:defaultConfig:list',1,'icon-default',0,'',NULL,0,0,'da0815306f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:44:16',NULL,NULL,NULL,NULL,NULL,'N'),('da083b576f2411ee983f00163e085602','系统配置详情','',1,1,'','F','system:defaultConfig:query',1,'icon-default',0,'',NULL,0,0,'da0815306f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:44:16',NULL,NULL,NULL,NULL,NULL,'N'),('da08617c6f2411ee983f00163e085602','系统配置新增','',1,1,'','F','system:defaultConfig:add',1,'icon-default',0,'',NULL,0,0,'da0815306f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:44:16',NULL,NULL,NULL,NULL,NULL,'N'),('da08617d6f2411ee983f00163e085602','系统配置修改','',1,1,'','F','system:defaultConfig:edit',1,'icon-default',0,'',NULL,0,0,'da0815306f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:44:16',NULL,NULL,NULL,NULL,NULL,'N'),('da0887a16f2411ee983f00163e085602','系统配置删除','',1,1,'','F','system:defaultConfig:remove',1,'icon-default',0,'',NULL,0,0,'da0815306f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:44:16',NULL,NULL,NULL,NULL,NULL,'N'),('da0887a26f2411ee983f00163e085602','系统配置导入','',1,1,'','F','system:defaultConfig:import',1,'icon-default',0,'',NULL,0,0,'da0815306f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:44:16',NULL,NULL,NULL,NULL,NULL,'N'),('da08adc76f2411ee983f00163e085602','系统配置导出','',1,1,'','F','system:defaultConfig:export',1,'icon-default',0,'',NULL,0,0,'da0815306f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:44:16',NULL,NULL,NULL,NULL,NULL,'N'),('e198a33f6f2411ee983f00163e085602','部门管理','system_department',120,1,'/system/department','C','system:department:list',1,'icon-default',0,'self',NULL,0,0,'1803694748176957442','16663888968888889','system-default',1,'188666666666','2023-10-20 16:44:28','188666666666','2024-06-20 15:43:11',NULL,NULL,NULL,'N'),('e198a3406f2411ee983f00163e085602','组织架构列表','',1,1,'','F','system:department:list',1,'icon-default',0,'',NULL,0,0,'e198a33f6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:44:28',NULL,NULL,NULL,NULL,NULL,'N'),('e198c9656f2411ee983f00163e085602','组织架构详情','',1,1,'','F','system:department:query',1,'icon-default',0,'',NULL,0,0,'e198a33f6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:44:29',NULL,NULL,NULL,NULL,NULL,'N'),('e198c9666f2411ee983f00163e085602','组织架构新增','',1,1,'','F','system:department:add',1,'icon-default',0,'',NULL,0,0,'e198a33f6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:44:29',NULL,NULL,NULL,NULL,NULL,'N'),('e198ef8a6f2411ee983f00163e085602','组织架构修改','',1,1,'','F','system:department:edit',1,'icon-default',0,'',NULL,0,0,'e198a33f6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:44:29',NULL,NULL,NULL,NULL,NULL,'N'),('e198ef8b6f2411ee983f00163e085602','组织架构删除','',1,1,'','F','system:department:remove',1,'icon-default',0,'',NULL,0,0,'e198a33f6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:44:29',NULL,NULL,NULL,NULL,NULL,'N'),('e19915b06f2411ee983f00163e085602','组织架构导入','',1,1,'','F','system:department:import',1,'icon-default',0,'',NULL,0,0,'e198a33f6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:44:29',NULL,NULL,NULL,NULL,NULL,'N'),('e1993bd56f2411ee983f00163e085602','组织架构导出','',1,1,'','F','system:department:export',1,'icon-default',0,'',NULL,0,0,'e198a33f6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:44:29',NULL,NULL,NULL,NULL,NULL,'N'),('ee25c66b6f2411ee983f00163e085602','部门内联关系','system_dept-relation',999,1,'/system/dept-relation','C','system:deptRelation:list',1,'icon-default',0,'self',NULL,0,0,'1663888968294588891','16663888968888889','system-default',1,'188666666666','2023-10-20 16:44:50',NULL,NULL,NULL,NULL,NULL,'N'),('ee25ec906f2411ee983f00163e085602','部门内联关系列表','',1,1,'','F','system:deptRelation:list',1,'icon-default',0,'',NULL,0,0,'ee25c66b6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:44:50',NULL,NULL,NULL,NULL,NULL,'N'),('ee2612b66f2411ee983f00163e085602','部门内联关系详情','',1,1,'','F','system:deptRelation:query',1,'icon-default',0,'',NULL,0,0,'ee25c66b6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:44:50',NULL,NULL,NULL,NULL,NULL,'N'),('ee2612b76f2411ee983f00163e085602','部门内联关系新增','',1,1,'','F','system:deptRelation:add',1,'icon-default',0,'',NULL,0,0,'ee25c66b6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:44:50',NULL,NULL,NULL,NULL,NULL,'N'),('ee2638dc6f2411ee983f00163e085602','部门内联关系修改','',1,1,'','F','system:deptRelation:edit',1,'icon-default',0,'',NULL,0,0,'ee25c66b6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:44:50',NULL,NULL,NULL,NULL,NULL,'N'),('ee2638dd6f2411ee983f00163e085602','部门内联关系删除','',1,1,'','F','system:deptRelation:remove',1,'icon-default',0,'',NULL,0,0,'ee25c66b6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:44:50',NULL,NULL,NULL,NULL,NULL,'N'),('ee265f016f2411ee983f00163e085602','部门内联关系导入','',1,1,'','F','system:deptRelation:import',1,'icon-default',0,'',NULL,0,0,'ee25c66b6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:44:50',NULL,NULL,NULL,NULL,NULL,'N'),('ee2685276f2411ee983f00163e085602','部门内联关系导出','',1,1,'','F','system:deptRelation:export',1,'icon-default',0,'',NULL,0,0,'ee25c66b6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:44:50',NULL,NULL,NULL,NULL,NULL,'N'),('f959a70e6f2411ee983f00163e085602','字典数据','system_dictionary-data',120,0,'/system/dictionary-data','C','system:dictionaryData:list',1,'icon-default',0,'self',NULL,0,0,'1798986040628969473','16663888968888889','system-default',1,'188666666666','2023-10-20 16:45:08','188666666666','2024-06-21 11:40:27',NULL,NULL,NULL,'N'),('f959cd336f2411ee983f00163e085602','字典数据列表','',1,1,'','F','system:dictionaryData:list',1,'icon-default',0,'',NULL,0,0,'f959a70e6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:45:08',NULL,NULL,NULL,NULL,NULL,'N'),('f959cd346f2411ee983f00163e085602','字典数据详情','',1,1,'','F','system:dictionaryData:query',1,'icon-default',0,'',NULL,0,0,'f959a70e6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:45:08',NULL,NULL,NULL,NULL,NULL,'N'),('f959f3596f2411ee983f00163e085602','字典数据新增','',1,1,'','F','system:dictionaryData:add',1,'icon-default',0,'',NULL,0,0,'f959a70e6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:45:08',NULL,NULL,NULL,NULL,NULL,'N'),('f95a197e6f2411ee983f00163e085602','字典数据修改','',1,1,'','F','system:dictionaryData:edit',1,'icon-default',0,'',NULL,0,0,'f959a70e6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:45:08',NULL,NULL,NULL,NULL,NULL,'N'),('f95a3fa46f2411ee983f00163e085602','字典数据删除','',1,1,'','F','system:dictionaryData:remove',1,'icon-default',0,'',NULL,0,0,'f959a70e6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:45:08',NULL,NULL,NULL,NULL,NULL,'N'),('f95a65ca6f2411ee983f00163e085602','字典数据导入','',1,1,'','F','system:dictionaryData:import',1,'icon-default',0,'',NULL,0,0,'f959a70e6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:45:08',NULL,NULL,NULL,NULL,NULL,'N'),('f95a8bef6f2411ee983f00163e085602','字典数据导出','',1,1,'','F','system:dictionaryData:export',1,'icon-default',0,'',NULL,0,0,'f959a70e6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:45:08',NULL,NULL,NULL,NULL,NULL,'N'),('fe59371b6f2411ee983f00163e085602','字典类型','system_dictionary-type',118,1,'/system/dictionary-type','C','system:dictionaryType:list',1,'icon-default',0,'self',NULL,0,0,'1798986040628969473','16663888968888889','system-default',1,'188666666666','2023-10-20 16:45:17','188666666666','2024-06-21 11:39:53',NULL,NULL,NULL,'N'),('fe595d416f2411ee983f00163e085602','字典类型列表','',1,1,'','F','system:dictionaryType:list',1,'icon-default',0,'',NULL,0,0,'fe59371b6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:45:17',NULL,NULL,NULL,NULL,NULL,'N'),('fe5983676f2411ee983f00163e085602','字典类型详情','',1,1,'','F','system:dictionaryType:query',1,'icon-default',0,'',NULL,0,0,'fe59371b6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:45:17',NULL,NULL,NULL,NULL,NULL,'N'),('fe5983686f2411ee983f00163e085602','字典类型新增','',1,1,'','F','system:dictionaryType:add',1,'icon-default',0,'',NULL,0,0,'fe59371b6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:45:17',NULL,NULL,NULL,NULL,NULL,'N'),('fe59a98c6f2411ee983f00163e085602','字典类型修改','',1,1,'','F','system:dictionaryType:edit',1,'icon-default',0,'',NULL,0,0,'fe59371b6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:45:17',NULL,NULL,NULL,NULL,NULL,'N'),('fe59a98d6f2411ee983f00163e085602','字典类型删除','',1,1,'','F','system:dictionaryType:remove',1,'icon-default',0,'',NULL,0,0,'fe59371b6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:45:17',NULL,NULL,NULL,NULL,NULL,'N'),('fe59cfb26f2411ee983f00163e085602','字典类型导入','',1,1,'','F','system:dictionaryType:import',1,'icon-default',0,'',NULL,0,0,'fe59371b6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:45:17',NULL,NULL,NULL,NULL,NULL,'N'),('fe59f5d86f2411ee983f00163e085602','字典类型导出','',1,1,'','F','system:dictionaryType:export',1,'icon-default',0,'',NULL,0,0,'fe59371b6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:45:17',NULL,NULL,NULL,NULL,NULL,'N');
INSERT INTO `system_app_menu` VALUES ('1798606500543033345','1768482874137485238','304d18026f2511ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-06 14:43:01',NULL,NULL,'N',NULL,NULL,'16663888968888889',NULL,1),('1798606500626919425','1768482874137485238','26bb3ab0dad111ee9b3d00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-06 14:43:01',NULL,NULL,'N',NULL,NULL,'16663888968888889',NULL,1),('1798606500694028289','1768482874137485238','d46cd3bd6f1911ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-06 14:43:01',NULL,NULL,'N',NULL,NULL,'16663888968888889',NULL,1),('1798606500756942850','1768482874137485238','da0815306f2411ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-06 14:43:01',NULL,NULL,'N',NULL,NULL,'16663888968888889',NULL,1),('1798606501725827074','1768482874137485238','12fe5bef6f2511ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-06 14:43:01',NULL,NULL,'N',NULL,NULL,'16663888968888889',NULL,1),('1798606501797130242','1768482874137485238','a64beef4df9e11ee9b3d00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-06 14:43:01',NULL,NULL,'N',NULL,NULL,'16663888968888889',NULL,1),('1798606503038644225','1768482874137485238','cd2e0e2e6f2411ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-06 14:43:01',NULL,NULL,'N',NULL,NULL,'16663888968888889',NULL,1),('1798980337281499137','1768482874137485248','e198a33f6f2411ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-07 15:28:31',NULL,NULL,'N',NULL,NULL,'16663888968888889',NULL,1),('1798980337386356738','1768482874137485248','57f757506f2511ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-07 15:28:31',NULL,NULL,'N',NULL,NULL,'16663888968888889',NULL,1),('1798980337482825729','1768482874137485248','85a389856f2511ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-07 15:28:31',NULL,NULL,'N',NULL,NULL,'16663888968888889',NULL,1),('1798987186722537474','1768482874137485238','1798986040628969473',1,NULL,'system-default',1,'188666666666','2024-06-07 15:55:44',NULL,NULL,'N',NULL,NULL,'16663888968888889',NULL,1),('1803696170603855873','1768482874137485248','1803694748176957442',1,NULL,'system-default',1,'188666666666','2024-06-20 15:47:33',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997413599539201','1768482874137485238','f959a70e6f2411ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:35','188666666666','2024-06-21 11:40:27','N',NULL,NULL,'16663888968888889',0,1),('1803997413956055042','1768482874137485238','fe59371b6f2411ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:35','188666666666','2024-06-21 11:39:53','N',NULL,NULL,'16663888968888889',0,1),('1803997414669086721','1768482874137485238','f959cd336f2411ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:35',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997415029796866','1768482874137485238','f959cd346f2411ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:35',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997415222734849','1768482874137485238','f959f3596f2411ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:35',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997415482781698','1768482874137485238','f95a197e6f2411ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:35',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997415654748161','1768482874137485238','f95a3fa46f2411ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:35',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997415776382978','1768482874137485238','f95a65ca6f2411ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:35',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997415939960834','1768482874137485238','f95a8bef6f2411ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:35',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997416116121602','1768482874137485238','fe595d416f2411ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:35',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997416241950721','1768482874137485238','fe5983676f2411ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:35',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997416355196929','1768482874137485238','fe5983686f2411ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:35',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997416992731137','1768482874137485238','fe59a98c6f2411ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:36',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997417177280513','1768482874137485238','fe59a98d6f2411ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:36',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997417382801410','1768482874137485238','fe59cfb26f2411ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:36',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997417542184962','1768482874137485238','fe59f5d86f2411ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:36',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997418796281858','1768482874137485238','d46d92796f1911ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:36',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997419043745793','1768482874137485238','d46d6c536f1911ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:36',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997419169574913','1768482874137485238','d46d462e6f1911ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:36',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997419320569857','1768482874137485238','d46d462d6f1911ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:36',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997419597393921','1768482874137485238','d46d20096f1911ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:36',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997419677085697','1768482874137485238','d46d20086f1911ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:36',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997419995852801','1768482874137485238','d46cf9e26f1911ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:36',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997420138459137','1768482874137485238','da083b566f2411ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:36',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997420306231298','1768482874137485238','da083b576f2411ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:36',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997420494974977','1768482874137485238','da08617c6f2411ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:36',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997420591443970','1768482874137485238','da08617d6f2411ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:36',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997420734050306','1768482874137485238','da0887a16f2411ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:36',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997421656797186','1768482874137485238','da0887a26f2411ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:37',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997421958787073','1768482874137485238','da08adc76f2411ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:37',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997422080421889','1768482874137485238','26bb86fcdad111ee9b3d00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:37',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997422239805441','1768482874137485238','26bbad22dad111ee9b3d00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:37',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997422462103553','1768482874137485238','26bbd348dad111ee9b3d00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:37',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997422659235841','1768482874137485238','26bbf96edad111ee9b3d00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:37',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997422776676353','1768482874137485238','26bbf96fdad111ee9b3d00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:37',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997422910894082','1768482874137485238','26bc1f94dad111ee9b3d00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:37',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997423124803585','1768482874137485238','26bc45badad111ee9b3d00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:37',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1804001669706608641','1768482874137485248','85a389866f2511ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 12:01:29',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1804001671304638465','1768482874137485248','85a3afab6f2511ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 12:01:30',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1804001672797810689','1768482874137485248','85a3d5d16f2511ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 12:01:30',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1804001674299371522','1768482874137485248','85a3d5d26f2511ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 12:01:31',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1804001675423444995','1768482874137485248','85a3fbf66f2511ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 12:01:31',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1804001677587705857','1768482874137485248','85a4221c6f2511ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 12:01:31',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1804001678678224897','1768482874137485248','85a4221d6f2511ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 12:01:32',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1804001680062345217','1768482874137485248','e198a3406f2411ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 12:01:32',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1804001681228361730','1768482874137485248','e198c9656f2411ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 12:01:32',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1804001682578927618','1768482874137485248','e198c9666f2411ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 12:01:33',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1804001686529961986','1768482874137485248','e198ef8a6f2411ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 12:01:33',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1804001688480313346','1768482874137485248','e198ef8b6f2411ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 12:01:34',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1804001689948319745','1768482874137485248','e19915b06f2411ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 12:01:34',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1804001691139502081','1768482874137485248','e1993bd56f2411ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 12:01:35',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1804001693232459778','1768482874137485248','57f77d756f2511ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 12:01:35',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1804001694302007297','1768482874137485248','57f77d766f2511ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 12:01:35',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1804001695799373825','1768482874137485248','57f7a39b6f2511ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 12:01:36',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1804001696952807426','1768482874137485248','57f7a39c6f2511ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 12:01:36',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1804001698664083457','1768482874137485248','57f7c9c16f2511ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 12:01:36',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1804001703059714049','1768482874137485248','57f7efe66f2511ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 12:01:37',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1804001704762601474','1768482874137485248','57f7efe76f2511ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 12:01:38',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1894205724006428673','1768482874137485238','470abfd0f31b11ef905d78fb839f9406',1,NULL,'system-default',1,'188666666666','2025-02-25 10:00:12','188666666666','2025-02-25 10:02:49','N',NULL,NULL,'16663888968888889',1,1),('1894205724081926145','1768482874137485238','9a36d84cf31b11ef905d78fb839f9406',1,NULL,'system-default',1,'188666666666','2025-02-25 10:00:12','188666666666','2025-02-25 10:02:49','N',NULL,NULL,'16663888968888889',1,1),('1894205724149035009','1768482874137485238','9a372feaf31b11ef905d78fb839f9406',1,NULL,'system-default',1,'188666666666','2025-02-25 10:00:12',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1894205724199366658','1768482874137485238','9a374b88f31b11ef905d78fb839f9406',1,NULL,'system-default',1,'188666666666','2025-02-25 10:00:12',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1894205724253892609','1768482874137485238','9a3757ccf31b11ef905d78fb839f9406',1,NULL,'system-default',1,'188666666666','2025-02-25 10:00:12',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1894205724308418562','1768482874137485238','9a3765d2f31b11ef905d78fb839f9406',1,NULL,'system-default',1,'188666666666','2025-02-25 10:00:12',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1894205724350361601','1768482874137485238','9a3776eef31b11ef905d78fb839f9406',1,NULL,'system-default',1,'188666666666','2025-02-25 10:00:12',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1894205724413276161','1768482874137485238','9a373c88f31b11ef905d78fb839f9406',1,NULL,'system-default',1,'188666666666','2025-02-25 10:00:12',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1894205724463607810','1768482874137485238','9a378382f31b11ef905d78fb839f9406',1,NULL,'system-default',1,'188666666666','2025-02-25 10:00:12',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1894205724509745154','1768482874137485238','a2ba9abcf31b11ef905d78fb839f9406',1,NULL,'system-default',1,'188666666666','2025-02-25 10:00:12','188666666666','2025-02-25 10:02:49','N',NULL,NULL,'16663888968888889',1,1),('1894205724560076801','1768482874137485238','a2baac46f31b11ef905d78fb839f9406',1,NULL,'system-default',1,'188666666666','2025-02-25 10:00:12',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1894205724606214146','1768482874137485238','a2bac122f31b11ef905d78fb839f9406',1,NULL,'system-default',1,'188666666666','2025-02-25 10:00:12',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1894205724656545793','1768482874137485238','a2bacd7af31b11ef905d78fb839f9406',1,NULL,'system-default',1,'188666666666','2025-02-25 10:00:12',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1894205724706877441','1768482874137485238','a2bad770f31b11ef905d78fb839f9406',1,NULL,'system-default',1,'188666666666','2025-02-25 10:00:12',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1894205724753014786','1768482874137485238','a2bae116f31b11ef905d78fb839f9406',1,NULL,'system-default',1,'188666666666','2025-02-25 10:00:12',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1894205724807540737','1768482874137485238','a2bab722f31b11ef905d78fb839f9406',1,NULL,'system-default',1,'188666666666','2025-02-25 10:00:12',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1894205724857872386','1768482874137485238','a2baea9ef31b11ef905d78fb839f9406',1,NULL,'system-default',1,'188666666666','2025-02-25 10:00:12',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1894205724899815426','1768482874137485238','ac47fd90f31b11ef905d78fb839f9406',1,NULL,'system-default',1,'188666666666','2025-02-25 10:00:12','188666666666','2025-02-25 10:02:49','N',NULL,NULL,'16663888968888889',1,1),('1894205724950147074','1768482874137485238','ac481618f31b11ef905d78fb839f9406',1,NULL,'system-default',1,'188666666666','2025-02-25 10:00:12',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1894205725000478722','1768482874137485238','ac482d7ef31b11ef905d78fb839f9406',1,NULL,'system-default',1,'188666666666','2025-02-25 10:00:12',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1894205725046616065','1768482874137485238','ac483846f31b11ef905d78fb839f9406',1,NULL,'system-default',1,'188666666666','2025-02-25 10:00:12',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1894205725101142017','1768482874137485238','ac484340f31b11ef905d78fb839f9406',1,NULL,'system-default',1,'188666666666','2025-02-25 10:00:12',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1894205725151473665','1768482874137485238','ac486618f31b11ef905d78fb839f9406',1,NULL,'system-default',1,'188666666666','2025-02-25 10:00:12',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1894205725201805314','1768482874137485238','ac482248f31b11ef905d78fb839f9406',1,NULL,'system-default',1,'188666666666','2025-02-25 10:00:12',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1894205725247942658','1768482874137485238','ac48752cf31b11ef905d78fb839f9406',1,NULL,'system-default',1,'188666666666','2025-02-25 10:00:12',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1894205725302468610','1768482874137485238','b1efac20f31b11ef905d78fb839f9406',1,NULL,'system-default',1,'188666666666','2025-02-25 10:00:12','188666666666','2025-02-25 10:02:49','N',NULL,NULL,'16663888968888889',1,1),('1894205725344411649','1768482874137485238','b1efc02af31b11ef905d78fb839f9406',1,NULL,'system-default',1,'188666666666','2025-02-25 10:00:12',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1894205725394743297','1768482874137485238','b1efe41af31b11ef905d78fb839f9406',1,NULL,'system-default',1,'188666666666','2025-02-25 10:00:12',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1894205725453463553','1768482874137485238','b1eff57cf31b11ef905d78fb839f9406',1,NULL,'system-default',1,'188666666666','2025-02-25 10:00:12',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1894205725499600898','1768482874137485238','b1f01e3af31b11ef905d78fb839f9406',1,NULL,'system-default',1,'188666666666','2025-02-25 10:00:12',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1894205725549932545','1768482874137485238','b1f006d4f31b11ef905d78fb839f9406',1,NULL,'system-default',1,'188666666666','2025-02-25 10:00:12',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1894205725591875585','1768482874137485238','b1efcf52f31b11ef905d78fb839f9406',1,NULL,'system-default',1,'188666666666','2025-02-25 10:00:12',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1894205725642207234','1768482874137485238','b1f0338ef31b11ef905d78fb839f9406',1,NULL,'system-default',1,'188666666666','2025-02-25 10:00:12',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('b1e4ac20f31b11ef905d78fbdead0002','1768482874137485238','b1e4ac20f31b11ef905d78fbdead0001',1,NULL,'system-default',1,'188666666666','2026-10-02 23:15:46',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('e4963d54c18e11f18fc3a5e694323b94','1768482874137485248','6c1d7c80bf4411f18fc3a5e694323b94',1,NULL,'system-default',1,'system','2026-10-06 22:04:48',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('e4963dd6c18e11f18fc3a5e694323b94','1768482874137485248','6c1dc370bf4411f18fc3a5e694323b94',1,NULL,'system-default',1,'system','2026-10-06 22:04:48',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('e4963e26c18e11f18fc3a5e694323b94','1768482874137485248','6c1dc398bf4411f18fc3a5e694323b94',1,NULL,'system-default',1,'system','2026-10-06 22:04:48',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('e4963e76c18e11f18fc3a5e694323b94','1768482874137485248','6c1dc3a2bf4411f18fc3a5e694323b94',1,NULL,'system-default',1,'system','2026-10-06 22:04:48',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('e4963ebcc18e11f18fc3a5e694323b94','1768482874137485248','6c1e3512bf4411f18fc3a5e694323b94',1,NULL,'system-default',1,'system','2026-10-06 22:04:48',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('e4963ef8c18e11f18fc3a5e694323b94','1768482874137485248','6c1e3526bf4411f18fc3a5e694323b94',1,NULL,'system-default',1,'system','2026-10-06 22:04:48',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('e4963f3ec18e11f18fc3a5e694323b94','1768482874137485248','6c1e3530bf4411f18fc3a5e694323b94',1,NULL,'system-default',1,'system','2026-10-06 22:04:48',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('e4963f7ac18e11f18fc3a5e694323b94','1768482874137485248','6c1e3544bf4411f18fc3a5e694323b94',1,NULL,'system-default',1,'system','2026-10-06 22:04:48',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('e4963fb6c18e11f18fc3a5e694323b94','1768482874137485248','6c1e354ebf4411f18fc3a5e694323b94',1,NULL,'system-default',1,'system','2026-10-06 22:04:48',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('e4963ff2c18e11f18fc3a5e694323b94','1768482874137485248','6c1e3558bf4411f18fc3a5e694323b94',1,NULL,'system-default',1,'system','2026-10-06 22:04:48',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('e496402ec18e11f18fc3a5e694323b94','1768482874137485248','6c1e3562bf4411f18fc3a5e694323b94',1,NULL,'system-default',1,'system','2026-10-06 22:04:48',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('e496406ac18e11f18fc3a5e694323b94','1768482874137485248','6c1e356cbf4411f18fc3a5e694323b94',1,NULL,'system-default',1,'system','2026-10-06 22:04:48',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('e49640b0c18e11f18fc3a5e694323b94','1768482874137485248','91f5a3f8bfe211f18fc3a5e694323b94',1,NULL,'system-default',1,'system','2026-10-06 22:04:48',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('e49743dec18e11f18fc3a5e694323b94','1768482874137485248','1814169974018928641',1,NULL,'system-default',1,'system','2026-10-06 22:04:48',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('e4974492c18e11f18fc3a5e694323b94','1768482874137485248','9a71582cdf9e11ee9b3d00163e085602',1,NULL,'system-default',1,'system','2026-10-06 22:04:48',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('e49744f6c18e11f18fc3a5e694323b94','1768482874137485248','9a717e52df9e11ee9b3d00163e085602',1,NULL,'system-default',1,'system','2026-10-06 22:04:48',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('e497455ac18e11f18fc3a5e694323b94','1768482874137485248','9a717e53df9e11ee9b3d00163e085602',1,NULL,'system-default',1,'system','2026-10-06 22:04:48',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('e49745aac18e11f18fc3a5e694323b94','1768482874137485248','9a71a478df9e11ee9b3d00163e085602',1,NULL,'system-default',1,'system','2026-10-06 22:04:48',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('e49745e6c18e11f18fc3a5e694323b94','1768482874137485248','9a71a479df9e11ee9b3d00163e085602',1,NULL,'system-default',1,'system','2026-10-06 22:04:48',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('e497462cc18e11f18fc3a5e694323b94','1768482874137485248','9a71ca9edf9e11ee9b3d00163e085602',1,NULL,'system-default',1,'system','2026-10-06 22:04:48',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('e4974668c18e11f18fc3a5e694323b94','1768482874137485248','9a71f0c4df9e11ee9b3d00163e085602',1,NULL,'system-default',1,'system','2026-10-06 22:04:48',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('e49746a4c18e11f18fc3a5e694323b94','1768482874137485248','9a71f0c5df9e11ee9b3d00163e085602',1,NULL,'system-default',1,'system','2026-10-06 22:04:48',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('e49746eac18e11f18fc3a5e694323b94','1768482874137485248','a0ba9526df9e11ee9b3d00163e085602',1,NULL,'system-default',1,'system','2026-10-06 22:04:48',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('e4974726c18e11f18fc3a5e694323b94','1768482874137485248','a0ba9527df9e11ee9b3d00163e085602',1,NULL,'system-default',1,'system','2026-10-06 22:04:48',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('e4974762c18e11f18fc3a5e694323b94','1768482874137485248','a0babb4cdf9e11ee9b3d00163e085602',1,NULL,'system-default',1,'system','2026-10-06 22:04:48',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('e497479ec18e11f18fc3a5e694323b94','1768482874137485248','a0bae172df9e11ee9b3d00163e085602',1,NULL,'system-default',1,'system','2026-10-06 22:04:48',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('e4974848c18e11f18fc3a5e694323b94','1768482874137485248','a0bae173df9e11ee9b3d00163e085602',1,NULL,'system-default',1,'system','2026-10-06 22:04:48',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('e4974898c18e11f18fc3a5e694323b94','1768482874137485248','a0bb0798df9e11ee9b3d00163e085602',1,NULL,'system-default',1,'system','2026-10-06 22:04:48',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('e4974910c18e11f18fc3a5e694323b94','1768482874137485248','a0bb0799df9e11ee9b3d00163e085602',1,NULL,'system-default',1,'system','2026-10-06 22:04:48',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('e4974960c18e11f18fc3a5e694323b94','1768482874137485248','a0bb2dbedf9e11ee9b3d00163e085602',1,NULL,'system-default',1,'system','2026-10-06 22:04:48',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1);
INSERT INTO `system_organization_app` VALUES ('2107000000000000001','1768482874137485238',1,'16663888968888889','system-default',1,'188666666666','2026-10-05 06:44:04',NULL,NULL,NULL,NULL,'N',NULL),('2107000000000000002','1768482874137485248',1,'16663888968888889','system-default',1,'188666666666','2026-10-05 06:44:04',NULL,NULL,NULL,NULL,'N',NULL);
INSERT INTO `system_dictionary_type` VALUES ('1710583019914629121','性别','SEX',1,'system-default',1,'188666666666','2023-10-07 17:09:08',NULL,NULL,'N',NULL,NULL,'','16663888968888889','155555555555'),('1716711670456475650','系统模块','SYSTEM_MODULE',1,'system-default',1,'188666666666','2023-10-24 15:02:12',NULL,NULL,'N',NULL,NULL,'模块的编码','16663888968888889','155555555555'),('1716712018248163329','编码配置类型','CODE_CONFIG_TYPE',1,'system-default',1,'188666666666','2023-10-24 15:03:35','188666666666','2023-10-24 15:46:57','N',NULL,NULL,'编码配置可选类型，固定编码，给后台查询该编码的配置情况','16663888968888889','155555555555'),('1716722866219868161','编码类型','CODE_TYPE',1,'system-default',1,'188666666666','2023-10-24 15:03:35',NULL,NULL,'N',NULL,NULL,'编码的类型:时间、递增、数字ID','16663888968888889','155555555555');
INSERT INTO `system_dictionary_data` VALUES ('1231122411',0,'男','0','SEX',NULL,NULL,'N',1,'system-default',1,'SYSTEM','2023-09-17 07:52:51','188666666666','2024-02-28 14:30:06','N',NULL,NULL,NULL,'16663888968888889','155555555555'),('123445523',0,'女','1','SEX',NULL,NULL,'N',1,'system-default',1,'SYSTEM','2023-09-17 07:53:31','188666666666','2024-02-28 14:30:13','N',NULL,NULL,NULL,'16663888968888889','155555555555'),('1716723130733649922',0,'时间','DATETIME','CODE_TYPE','','','',1,'system-default',1,'188666666666','2023-10-24 15:05:02',NULL,NULL,'N',NULL,NULL,'','16663888968888889','155555555555'),('1716723276338913282',0,'数字ID','NUMBER_ID','CODE_TYPE','','','',1,'system-default',1,'188666666666','2023-10-24 15:05:02',NULL,NULL,'N',NULL,NULL,'','16663888968888889','155555555555'),('1716723506346156033',0,'递增ID','INCREMENTAL','CODE_TYPE','','','',1,'system-default',1,'188666666666','2023-10-24 15:05:02',NULL,NULL,'N',NULL,NULL,'','16663888968888889','155555555555'),('1762728211723202561',1,'未知','2','SEX','','','',1,'system-default',1,'188666666666','2024-02-28 14:35:30',NULL,NULL,'N',NULL,NULL,'','16663888968888889','155555555555');
INSERT INTO `system_default_config` VALUES ('1209099090','SYSTEM_THEME','{\"tab\": {\"mode\": \"chrome\", \"height\": 44, \"isCache\": true, \"visible\": false, \"modeList\": [{\"label\": \"谷歌风格\", \"value\": \"chrome\"}, {\"label\": \"按钮风格\", \"value\": \"button\"}]}, \"menu\": {\"horizontalPosition\": \"flex-start\", \"horizontalPositionList\": [{\"label\": \"居左\", \"value\": \"flex-start\"}, {\"label\": \"居中\", \"value\": \"center\"}, {\"label\": \"居右\", \"value\": \"flex-end\"}]}, \"page\": {\"animate\": true, \"animateMode\": \"fade-slide\", \"animateModeList\": [{\"label\": \"滑动\", \"value\": \"fade-slide\"}, {\"label\": \"消退\", \"value\": \"fade\"}, {\"label\": \"底部消退\", \"value\": \"fade-bottom\"}, {\"label\": \"缩放消退\", \"value\": \"fade-scale\"}, {\"label\": \"渐变\", \"value\": \"zoom-fade\"}, {\"label\": \"闪现\", \"value\": \"zoom-out\"}]}, \"sider\": {\"width\": 180, \"inverted\": false, \"mixWidth\": 80, \"collapsedWidth\": 64, \"mixChildMenuWidth\": 200, \"mixCollapsedWidth\": 48}, \"footer\": {\"fixed\": false, \"right\": true, \"height\": 48, \"visible\": false, \"inverted\": false}, \"header\": {\"crumb\": {\"visible\": false, \"showIcon\": true}, \"height\": 56, \"inverted\": false}, \"layout\": {\"mode\": \"horizontal-mix\", \"minWidth\": 900, \"modeList\": [{\"label\": \"左侧菜单模式\", \"value\": \"vertical\"}, {\"label\": \"左侧菜单混合模式\", \"value\": \"vertical-mix\"}, {\"label\": \"顶部菜单模式\", \"value\": \"horizontal\"}, {\"label\": \"顶部菜单混合模式\", \"value\": \"horizontal-mix\"}]}, \"darkMode\": false, \"otherColor\": {\"info\": \"#0099ad\", \"error\": \"#f5222d\", \"success\": \"#52c41a\", \"warning\": \"#faad14\"}, \"scrollMode\": \"content\", \"showReload\": true, \"themeColor\": \"#165DFF\", \"scrollModeList\": [{\"label\": \"外层滚动\", \"value\": \"wrapper\"}, {\"label\": \"主体滚动\", \"value\": \"content\"}], \"themeColorList\": [\"#1890ff\", \"#409EFF\", \"#007AFF\", \"#5ac8fa\", \"#5856D6\", \"#536dfe\", \"#646cff\", \"#9c27b0\", \"#AF52DE\", \"#0096c7\", \"#00C1D4\", \"#34C759\", \"#43a047\", \"#7cb342\", \"#c0ca33\", \"#78DEC7\", \"#e53935\", \"#d81b60\", \"#f4511e\", \"#fb8c00\", \"#ffb300\", \"#fdd835\", \"#6d4c41\", \"#546e7a\"], \"fixedHeaderAndTab\": true, \"followSystemTheme\": false, \"isCustomizeInfoColor\": false, \"isCustomizeDarkModeTransition\": false}','平台样式','16663888968888889','system-default',1,'1','2023-09-19 12:01:22',NULL,NULL,'N',NULL,NULL,NULL),('427c486abff911f18fc3a5e694323b94','dynamic.advanced.linkage-log.retention-days','30','联动日志在线保留天数',NULL,'system-default',1,'system','2026-10-04 21:41:10',NULL,NULL,'N',NULL,NULL,NULL),('427c489cbff911f18fc3a5e694323b94','dynamic.advanced.access-log.retention-days','90','外链审计在线保留天数',NULL,'system-default',1,'system','2026-10-04 21:41:10',NULL,NULL,'N',NULL,NULL,NULL);
INSERT INTO `dynamic_script_asset` VALUES ('08b81220c16211f18fc3a5e694323b94',NULL,'biz_number_rule','编号规则','AUTOFILL','GROOVY','// 模板：业务编号生成（前缀 + yyyyMMdd + 当日序号）。序号按同前缀当日已有记录数递增，\n// 并发窗口下非严格唯一，严格唯一请叠加复合唯一约束。\n// params: tableName、numberField、prefix\ndef identOk = { s -> s != null && s ==~ /[A-Za-z0-9_]+/ }\nif (!identOk(params.tableName) || !identOk(params.numberField)) {\n  throw new Exception(\'表名/列名必须为字母数字下划线\')\n}\nif (params.prefix == null || params.prefix.toString().contains(\"\'\")) {\n  throw new Exception(\'prefix 不允许包含单引号\')\n}\ndef today = cn.hutool.core.date.DateUtil.format(new java.util.Date(), \'yyyyMMdd\')\ndef likePrefix = params.prefix.toString() + today\ndef sql = \"SELECT \" + params.numberField + \" AS v FROM \" + params.tableName + \" WHERE deleted_flag=\'N\' AND \" + params.numberField + \" LIKE \'\" + likePrefix + \"%\'\"\ndef rows = daoService.querySql(params.tableName, sql)\ndef maxSeq = 0\nfor (row in rows) {\n  def v = row.v == null ? \'\' : row.v.toString()\n  if (v.startsWith(likePrefix)) {\n    def seq = cn.hutool.core.convert.Convert.toInt(v.substring(likePrefix.length()), 0)\n    if (seq > maxSeq) maxSeq = seq\n  }\n}\ndef nextSeq = maxSeq + 1\nreturn likePrefix + cn.hutool.core.util.StrUtil.padPre(String.valueOf(nextSeq), 4, \'0\')\n','[{\"name\": \"tableName\", \"type\": \"STRING\", \"required\": true}, {\"name\": \"numberField\", \"type\": \"STRING\", \"required\": true}, {\"name\": \"prefix\", \"type\": \"STRING\", \"required\": true}]','OBJECT',15,'DRAFT',1,NULL,'16663888968888889',NULL,'模板种子：编号规则（按当日序号，非严格唯一）',0,'system','2026-10-06 16:43:42.000',NULL,NULL,'N',NULL,NULL),('08b811dac16211f18fc3a5e694323b94',NULL,'conflict_count','冲突计数','CALC','GROOVY','// 模板：同一资源在某时段内的冲突行数。表/列名过标识符白名单，值禁单引号（模板口径，严格场景请改参数化）。\n// params: tableName、resourceField、resourceValue、startField/endField、start/end、excludeId（编辑态排除自身）\ndef identOk = { s -> s != null && s ==~ /[A-Za-z0-9_]+/ }\nif (!identOk(params.tableName) || !identOk(params.resourceField) || !identOk(params.startField) || !identOk(params.endField)) {\n  throw new Exception(\'表名/列名必须为字母数字下划线\')\n}\nif (params.resourceValue == null || params.resourceValue.toString().contains(\"\'\")) {\n  throw new Exception(\'resourceValue 不允许包含单引号\')\n}\ndef sql = \"SELECT * FROM \" + params.tableName + \" WHERE deleted_flag=\'N\' AND \" + params.resourceField + \" = \'\" + params.resourceValue + \"\'\"\ndef rows = daoService.querySql(params.tableName, sql)\ndef toTime(v) { v == null ? null : (v instanceof java.util.Date ? v : cn.hutool.core.date.DateUtil.parse(v.toString())) }\ndef s = toTime(params.start); def e = toTime(params.end)\ndef count = 0\nfor (row in rows) {\n  if (params.excludeId != null && params.excludeId.toString() == String.valueOf(row.id)) continue\n  def rs = toTime(row[params.startField]); def re = toTime(row[params.endField])\n  if (rs == null || re == null) continue\n  if (!(re <= s || e <= rs)) count++\n}\nreturn count\n','[{\"name\": \"tableName\", \"type\": \"STRING\", \"required\": true}, {\"name\": \"resourceField\", \"type\": \"STRING\", \"required\": true}, {\"name\": \"resourceValue\", \"type\": \"STRING\", \"required\": true}, {\"name\": \"startField\", \"type\": \"STRING\", \"required\": true}, {\"name\": \"endField\", \"type\": \"STRING\", \"required\": true}, {\"name\": \"start\", \"type\": \"STRING\", \"required\": true}, {\"name\": \"end\", \"type\": \"STRING\", \"required\": true}, {\"name\": \"excludeId\", \"type\": \"STRING\", \"required\": false}]','OBJECT',15,'DRAFT',1,NULL,'16663888968888889',NULL,'模板种子：冲突计数（querySql 口径，标识符白名单 + 值禁单引号）',0,'system','2026-10-06 16:43:42.000',NULL,NULL,'N',NULL,NULL),('08b81144c16211f18fc3a5e694323b94',NULL,'overlap_check','时间重叠判断','VALIDATION','GROOVY','// 模板：时间重叠判断（半开区间 [start,end)）。返回 false 时保存校验挂点会用 fail_message 拦截\n// params: startA/endA（基准时段）、startB/endB（待比对时段），STRING（yyyy-MM-dd HH:mm:ss）或 Date\ndef toTime(v) {\n  if (v == null) return null\n  if (v instanceof java.util.Date) return v\n  return cn.hutool.core.date.DateUtil.parse(v.toString())\n}\ndef a1 = toTime(params.startA); def a2 = toTime(params.endA)\ndef b1 = toTime(params.startB); def b2 = toTime(params.endB)\nif (a1 == null || a2 == null || b1 == null || b2 == null) return false\nreturn !(a2 <= b1 || b2 <= a1)\n','[{\"name\": \"startA\", \"type\": \"STRING\", \"required\": true, \"description\": \"基准时段开始\"}, {\"name\": \"endA\", \"type\": \"STRING\", \"required\": true, \"description\": \"基准时段结束\"}, {\"name\": \"startB\", \"type\": \"STRING\", \"required\": true, \"description\": \"比对时段开始\"}, {\"name\": \"endB\", \"type\": \"STRING\", \"required\": true, \"description\": \"比对时段结束\"}]','BOOLEAN',10,'PUBLISHED',1,NULL,'16663888968888889',NULL,'模板种子：时间重叠（overlap）半开区间口径',0,'system','2026-10-06 16:43:42.000','188666666666','2026-10-06 16:57:00.756','N',NULL,NULL),('60bee154c16411f18fc3a5e694323b94',NULL,'time_range_available','时段可用校验','VALIDATION','GROOVY','// 模板：时段可用性校验（保存校验挂点专用，返回 true=可用/通过）。\n// 查询同表同资源的既有预约，判断 [start,end) 与存量时段是否重叠；重叠返回 false 中止保存。\n// params: tableName、resourceField、resourceValue、startField/endField、start/end、excludeId（编辑态排除自身）\ndef identOk = { s -> s != null && s ==~ /[A-Za-z0-9_]+/ }\nif (!identOk(params.tableName) || !identOk(params.resourceField) || !identOk(params.startField) || !identOk(params.endField)) {\n  throw new Exception(\'表名/列名必须为字母数字下划线\')\n}\nif (params.resourceValue == null || params.resourceValue.toString().contains(\"\'\")) {\n  throw new Exception(\'resourceValue 不允许包含单引号\')\n}\ndef sql = \"SELECT * FROM \" + params.tableName + \" WHERE deleted_flag=\'N\' AND \" + params.resourceField + \" = \'\" + params.resourceValue + \"\'\"\ndef rows = daoService.querySql(params.tableName, sql)\ndef toTime(v) { v == null ? null : (v instanceof java.util.Date ? v : cn.hutool.core.date.DateUtil.parse(v.toString())) }\ndef s = toTime(params.start); def e = toTime(params.end)\nif (s == null || e == null) return true\nfor (row in rows) {\n  if (params.excludeId != null && params.excludeId.toString() == String.valueOf(row.id)) continue\n  def rs = toTime(row[params.startField]); def re = toTime(row[params.endField])\n  if (rs == null || re == null) continue\n  if (!(re <= s || e <= rs)) return false\n}\nreturn true\n','[{\"name\": \"tableName\", \"type\": \"STRING\", \"required\": true}, {\"name\": \"resourceField\", \"type\": \"STRING\", \"required\": true}, {\"name\": \"resourceValue\", \"type\": \"STRING\", \"required\": true}, {\"name\": \"startField\", \"type\": \"STRING\", \"required\": true}, {\"name\": \"endField\", \"type\": \"STRING\", \"required\": true}, {\"name\": \"start\", \"type\": \"STRING\", \"required\": true}, {\"name\": \"end\", \"type\": \"STRING\", \"required\": true}, {\"name\": \"excludeId\", \"type\": \"STRING\", \"required\": false}]','BOOLEAN',15,'PUBLISHED',1,NULL,'16663888968888889',NULL,'模板种子：保存校验挂点专用（true=通过）；与 overlap_check 互为补集',0,'system','2026-10-06 17:00:28.000','188666666666','2026-10-06 17:01:53.353','N',NULL,NULL),('08b8120cc16211f18fc3a5e694323b94',NULL,'workday_add','工作日计算','CALC','GROOVY','// 模板：从起始日起加 N 个工作日（周一至周五），节假日表未接入（如需请叠加节假日参数扩展）。\n// params: startDate（yyyy-MM-dd）、days（NUMBER）\ndef base = cn.hutool.core.date.DateUtil.parse(params.startDate.toString())\ndef n = cn.hutool.core.convert.Convert.toInt(params.days, 0)\ndef cursor = base\ndef added = 0\nwhile (added < n) {\n  cursor = cn.hutool.core.date.DateUtil.offsetDay(cursor, 1)\n  def dow = cn.hutool.core.date.DateUtil.dayOfWeek(cursor)\n  if (dow >= 2 && dow <= 6) added++\n}\nreturn cn.hutool.core.date.DateUtil.format(cursor, \'yyyy-MM-dd\')\n','[{\"name\": \"startDate\", \"type\": \"STRING\", \"required\": true}, {\"name\": \"days\", \"type\": \"NUMBER\", \"required\": true}]','OBJECT',10,'DRAFT',1,NULL,'16663888968888889',NULL,'模板种子：工作日顺延（未含节假日表）',0,'system','2026-10-06 16:43:42.000',NULL,NULL,'N',NULL,NULL);
INSERT INTO `system_dept_relation` VALUES ('system-default',1,'188666666666','2023-03-02 21:25:52',NULL,NULL,'N',NULL,NULL,'1631284688245706753',NULL,'16663888968888889','155555555555',1),('system-default',1,'188666666666','2023-03-02 21:27:11',NULL,NULL,'N',NULL,NULL,'1631285019985707010',NULL,'16663888968888889','16663888968888889',1),('system-default',1,'188666666666','2023-09-14 11:55:22',NULL,NULL,'N',NULL,NULL,'1702169136837296129',NULL,'16663888968888889','1702169136598220802',2),('system-default',1,'188666666666','2023-09-14 11:55:22',NULL,NULL,'N',NULL,NULL,'1702169136862461953',NULL,'155555555555','1702169136598220802',1),('system-default',1,'188666666666','2023-09-25 15:49:54',NULL,NULL,'N',NULL,NULL,'1706214426926067714',NULL,'16663888968888889','1706214426871541762',1);

-- ----------------------------------------------------------------------------
-- 种子授权：默认组织、两个内置角色拥有全部平台菜单（与上方菜单种子保持一致）
-- 新增菜单后如需授权，参照下面两条语句补授或通过「企业管理/菜单授权」页面操作
-- ----------------------------------------------------------------------------

-- 组织拥有的菜单（organization_menu：app_id 留空、status=1 与现有数据保持一致）
INSERT INTO `system_organization_menu`
  (`id`, `menu_id`, `app_id`, `status`, `organization_id`, `tenant_id`, `revision`,
   `created_by`, `created_time`, `deleted_flag`)
SELECT REPLACE(UUID(), '-', ''), m.id, NULL, 1, '16663888968888889', 'system-default', 1,
       '188666666666', NOW(), 'N'
FROM `system_menu` m;

-- 角色拥有的菜单（role_menu：permission 取菜单权限标识，data_scope=50 组织级上限）
INSERT INTO `system_role_menu`
  (`tenant_id`, `revision`, `created_by`, `created_time`, `deleted_flag`, `id`,
   `role_id`, `menu_id`, `permission`, `data_scope`, `app_id`)
SELECT 'system-default', 1, '188666666666', NOW(), 'N', REPLACE(UUID(), '-', ''),
       r.id, m.id, m.permission, '50', NULL
FROM `system_role` r CROSS JOIN `system_menu` m;

-- ----------------------------
-- 数据大屏（datav）与 AI 会话表（原增量脚本并入，2026-11-09）
-- ----------------------------

DROP TABLE IF EXISTS `dynamic_datav_screen`;
CREATE TABLE `dynamic_datav_screen`
(
    `id`              varchar(36) COLLATE utf8mb4_bin   NOT NULL COMMENT '主键',
    `tenant_id`       varchar(255) COLLATE utf8mb4_bin  NOT NULL DEFAULT 'system-default' COMMENT '租户号',
    `revision`        int                               NOT NULL DEFAULT '1' COMMENT '乐观锁',
    `created_by`      varchar(36) COLLATE utf8mb4_bin   NOT NULL COMMENT '创建人',
    `created_time`    datetime                          NOT NULL COMMENT '创建时间',
    `updated_by`      varchar(36) COLLATE utf8mb4_bin            DEFAULT NULL COMMENT '更新人',
    `updated_time`    datetime                                   DEFAULT NULL COMMENT '更新时间',
    `deleted_flag`    varchar(1) COLLATE utf8mb4_bin    NOT NULL DEFAULT 'N' COMMENT '是否删除',
    `deleted_by`      varchar(36) COLLATE utf8mb4_bin            DEFAULT NULL COMMENT '删除人',
    `deleted_time`    datetime                                   DEFAULT NULL COMMENT '删除时间',
    `department_id`   varchar(36) COLLATE utf8mb4_bin   NOT NULL COMMENT '所属部门',
    `organization_id` varchar(36) COLLATE utf8mb4_bin   NOT NULL COMMENT '所属组织',
    `remark`          varchar(900) COLLATE utf8mb4_bin           DEFAULT NULL COMMENT '备注',
    `screen_code`     varchar(100) COLLATE utf8mb4_bin  NOT NULL COMMENT '大屏编码（业务主键，跨环境可移植）',
    `screen_name`     varchar(100) COLLATE utf8mb4_bin  NOT NULL COMMENT '大屏名称',
    `app_id`          varchar(36) COLLATE utf8mb4_bin            DEFAULT NULL COMMENT '所属应用ID',
    `layout_mode`     varchar(20) COLLATE utf8mb4_bin            DEFAULT 'absolute' COMMENT '布局方式;absolute自由布局,grid栅格吸附',
    `schema_json`     mediumtext COLLATE utf8mb4_bin COMMENT '画布schema{schemaVersion,canvas,params,widgets,groups}',
    `status`          int                                        DEFAULT 0 COMMENT '状态;0草稿,1已发布,2已下线',
    `thumbnail`       varchar(255) COLLATE utf8mb4_bin           DEFAULT NULL COMMENT '缩略图地址',
    `widget_count`    int                                        DEFAULT 0 COMMENT '组件数冗余列，列表页不必解析画布JSON',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_datav_screen_code` (`screen_code`) USING BTREE,
    INDEX `idx_datav_screen_app` (`app_id`) USING BTREE
) ENGINE = InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='数据大屏';

DROP TABLE IF EXISTS `dynamic_datav_version`;
CREATE TABLE `dynamic_datav_version`
(
    `id`              varchar(36) COLLATE utf8mb4_bin   NOT NULL COMMENT '主键',
    `tenant_id`       varchar(255) COLLATE utf8mb4_bin  NOT NULL DEFAULT 'system-default' COMMENT '租户号',
    `revision`        int                               NOT NULL DEFAULT '1' COMMENT '乐观锁',
    `created_by`      varchar(36) COLLATE utf8mb4_bin   NOT NULL COMMENT '创建人',
    `created_time`    datetime                          NOT NULL COMMENT '创建时间',
    `updated_by`      varchar(36) COLLATE utf8mb4_bin            DEFAULT NULL COMMENT '更新人',
    `updated_time`    datetime                                   DEFAULT NULL COMMENT '更新时间',
    `deleted_flag`    varchar(1) COLLATE utf8mb4_bin    NOT NULL DEFAULT 'N' COMMENT '是否删除',
    `deleted_by`      varchar(36) COLLATE utf8mb4_bin            DEFAULT NULL COMMENT '删除人',
    `deleted_time`    datetime                                   DEFAULT NULL COMMENT '删除时间',
    `organization_id` varchar(36) COLLATE utf8mb4_bin   NOT NULL COMMENT '所属组织',
    `remark`          varchar(900) COLLATE utf8mb4_bin           DEFAULT NULL COMMENT '备注',
    `screen_code`     varchar(100) COLLATE utf8mb4_bin  NOT NULL COMMENT '大屏编码',
    `version_no`      int                               NOT NULL COMMENT '版本号，同屏内递增',
    `screen_name`     varchar(100) COLLATE utf8mb4_bin           DEFAULT NULL COMMENT '该版本时的名称',
    `schema_json`     mediumtext COLLATE utf8mb4_bin COMMENT '该版本的画布快照，不可变',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_datav_version` (`screen_code`, `version_no`) USING BTREE
) ENGINE = InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='数据大屏历史版本';

DROP TABLE IF EXISTS `dynamic_datav_asset`;
CREATE TABLE `dynamic_datav_asset`
(
    `id`              varchar(36) COLLATE utf8mb4_bin   NOT NULL COMMENT '主键',
    `tenant_id`       varchar(255) COLLATE utf8mb4_bin  NOT NULL DEFAULT 'system-default' COMMENT '租户号',
    `revision`        int                               NOT NULL DEFAULT '1' COMMENT '乐观锁',
    `created_by`      varchar(36) COLLATE utf8mb4_bin   NOT NULL COMMENT '创建人',
    `created_time`    datetime                          NOT NULL COMMENT '创建时间',
    `updated_by`      varchar(36) COLLATE utf8mb4_bin            DEFAULT NULL COMMENT '更新人',
    `updated_time`    datetime                                   DEFAULT NULL COMMENT '更新时间',
    `deleted_flag`    varchar(1) COLLATE utf8mb4_bin    NOT NULL DEFAULT 'N' COMMENT '是否删除',
    `deleted_by`      varchar(36) COLLATE utf8mb4_bin            DEFAULT NULL COMMENT '删除人',
    `deleted_time`    datetime                                   DEFAULT NULL COMMENT '删除时间',
    `department_id`   varchar(36) COLLATE utf8mb4_bin   NOT NULL COMMENT '所属部门',
    `organization_id` varchar(36) COLLATE utf8mb4_bin   NOT NULL COMMENT '所属组织',
    `remark`          varchar(900) COLLATE utf8mb4_bin           DEFAULT NULL COMMENT '备注',
    `asset_code`      varchar(64) COLLATE utf8mb4_bin   NOT NULL COMMENT '素材编码，对外业务主键；不含文件名与路径信息，可安全出现在日志与URL里',
    `asset_name`      varchar(200) COLLATE utf8mb4_bin  NOT NULL COMMENT '素材显示名',
    `category`        varchar(50) COLLATE utf8mb4_bin   NOT NULL DEFAULT '通用' COMMENT '业务分类：背景/边框/视频等自由文本',
    `asset_type`      varchar(20) COLLATE utf8mb4_bin   NOT NULL DEFAULT 'image' COMMENT 'image|video，决定素材面板展示形态',
    `file_url`        varchar(500) COLLATE utf8mb4_bin  NOT NULL COMMENT '文件服务返回的可访问URL，大屏schema引用的就是它；删除保护按它LIKE扫描',
    `original_name`   varchar(255) COLLATE utf8mb4_bin           DEFAULT NULL COMMENT '上传时的原始文件名，只作展示与检索，禁止参与路径拼接',
    `file_size`       bigint                            NOT NULL DEFAULT '0' COMMENT '字节数，仅作展示',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_datav_asset_code` (`asset_code`) USING BTREE,
    INDEX `idx_datav_asset_category` (`category`) USING BTREE
) ENGINE = InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='数据大屏素材';

DROP TABLE IF EXISTS `dynamic_datav_file`;
CREATE TABLE `dynamic_datav_file`
(
    `id`              varchar(36) COLLATE utf8mb4_bin   NOT NULL COMMENT '主键',
    `tenant_id`       varchar(255) COLLATE utf8mb4_bin  NOT NULL DEFAULT 'system-default' COMMENT '租户号',
    `revision`        int                               NOT NULL DEFAULT '1' COMMENT '乐观锁',
    `created_by`      varchar(36) COLLATE utf8mb4_bin   NOT NULL COMMENT '创建人',
    `created_time`    datetime                          NOT NULL COMMENT '创建时间',
    `updated_by`      varchar(36) COLLATE utf8mb4_bin            DEFAULT NULL COMMENT '更新人',
    `updated_time`    datetime                                   DEFAULT NULL COMMENT '更新时间',
    `deleted_flag`    varchar(1) COLLATE utf8mb4_bin    NOT NULL DEFAULT 'N' COMMENT '是否删除',
    `deleted_by`      varchar(36) COLLATE utf8mb4_bin            DEFAULT NULL COMMENT '删除人',
    `deleted_time`    datetime                                   DEFAULT NULL COMMENT '删除时间',
    `department_id`   varchar(36) COLLATE utf8mb4_bin   NOT NULL COMMENT '所属部门',
    `organization_id` varchar(36) COLLATE utf8mb4_bin   NOT NULL COMMENT '所属组织',
    `remark`          varchar(900) COLLATE utf8mb4_bin           DEFAULT NULL COMMENT '备注',
    `file_code`       varchar(64) COLLATE utf8mb4_bin   NOT NULL COMMENT '文件编码，即 sourceConfig.fileId；不含文件名与路径信息，可安全出现在日志与URL里',
    `dataset_code`    varchar(100) COLLATE utf8mb4_bin           DEFAULT NULL COMMENT '所属数据集编码；先上传后保存数据集时为NULL',
    `file_name`       varchar(255) COLLATE utf8mb4_bin  NOT NULL COMMENT '上传时的原始文件名，只作展示，禁止参与路径拼接或下载名',
    `size_bytes`      bigint                            NOT NULL DEFAULT '0' COMMENT '内容字节数，落库前的硬上限校验对象',
    `content`         longblob COMMENT 'CSV 原始字节；只交给 CsvParser 解析，不落盘、不执行、不渲染',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_datav_file_code` (`file_code`) USING BTREE,
    INDEX `idx_datav_file_dataset` (`dataset_code`) USING BTREE
) ENGINE = InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='数据大屏 CSV 上传文件';

DROP TABLE IF EXISTS `dynamic_datav_share`;
CREATE TABLE `dynamic_datav_share`
(
    `id`                 varchar(36) COLLATE utf8mb4_bin  NOT NULL COMMENT '主键',
    `tenant_id`          varchar(255) COLLATE utf8mb4_bin NOT NULL DEFAULT 'system-default' COMMENT '租户号',
    `revision`           int                              NOT NULL DEFAULT '1' COMMENT '乐观锁',
    `created_by`         varchar(36) COLLATE utf8mb4_bin  NOT NULL COMMENT '创建人',
    `created_time`       datetime                         NOT NULL COMMENT '创建时间',
    `updated_by`         varchar(36) COLLATE utf8mb4_bin           DEFAULT NULL COMMENT '更新人',
    `updated_time`       datetime                                  DEFAULT NULL COMMENT '更新时间',
    `deleted_flag`       varchar(1) COLLATE utf8mb4_bin   NOT NULL DEFAULT 'N' COMMENT '是否删除',
    `deleted_by`         varchar(36) COLLATE utf8mb4_bin           DEFAULT NULL COMMENT '删除人',
    `deleted_time`       datetime                                  DEFAULT NULL COMMENT '删除时间',
    `department_id`      varchar(36) COLLATE utf8mb4_bin  NOT NULL COMMENT '所属部门',
    `organization_id`    varchar(36) COLLATE utf8mb4_bin  NOT NULL COMMENT '分享所属组织：免登取数按它定位业务库与组织谓词',
    `remark`             varchar(900) COLLATE utf8mb4_bin          DEFAULT NULL COMMENT '备注',
    `share_code`         varchar(80) COLLATE utf8mb4_bin  NOT NULL COMMENT '分享编码，随机不透明值，对外即链接标识',
    `screen_code`        varchar(64) COLLATE utf8mb4_bin  NOT NULL COMMENT '被分享的大屏编码（须为已发布状态）',
    `expire_time`        datetime                                  DEFAULT NULL COMMENT '失效时间；NULL 表示长期有效',
    `referer_whitelist`  varchar(900) COLLATE utf8mb4_bin          DEFAULT NULL COMMENT '允许嵌入的来源域名白名单，逗号分隔；NULL/空表示不限制',
    `enabled`            varchar(1) COLLATE utf8mb4_bin   NOT NULL DEFAULT 'Y' COMMENT 'Y=有效 N=已禁用（禁用即吊销，不删行以便审计）',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_datav_share_code` (`share_code`) USING BTREE,
    INDEX `idx_datav_share_screen` (`screen_code`) USING BTREE
) ENGINE = InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='数据大屏外链分享';

DROP TABLE IF EXISTS `system_ai_session_log`;
CREATE TABLE `system_ai_session_log` (
    `id`              varchar(36)  NOT NULL,
    `session_id`      varchar(64)           DEFAULT NULL COMMENT 'AI 会话号（X-AI-Session）',
    `user_id`         varchar(36)           DEFAULT NULL COMMENT '发起人（START 行为权威值，Filter 行 best-effort）',
    `user_name`       varchar(64)           DEFAULT NULL,
    `organization_id` varchar(36)           DEFAULT NULL,
    `action`          varchar(16)  NOT NULL COMMENT 'START/CLOSE/ALLOW/DENIED/CONFIG_FETCH',
    `http_method`     varchar(8)            DEFAULT NULL,
    `request_uri`     varchar(500)          DEFAULT NULL,
    `deny_reason`     varchar(200)          DEFAULT NULL,
    `created_time`    datetime(3)           DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_ai_session_log_session` (`session_id`, `created_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI 会话审计日志';

DROP TABLE IF EXISTS `system_ai_chat_message`;
CREATE TABLE `system_ai_chat_message` (
    `id`              varchar(36)  NOT NULL,
    `session_id`      varchar(64)           DEFAULT NULL COMMENT 'AI 会话号（X-AI-Session）',
    `user_id`         varchar(36)           DEFAULT NULL COMMENT '发言人（写入线程取登录上下文）',
    `user_name`       varchar(64)           DEFAULT NULL,
    `organization_id` varchar(36)           DEFAULT NULL,
    `role`            varchar(16)  NOT NULL COMMENT 'user/assistant',
    `content`         mediumtext            COMMENT '消息正文（user=原文，assistant=终文）',
    `charts`          mediumtext            COMMENT '本条消息内联图表 JSON 数组（chart SSE 事件载荷）',
    `created_time`    datetime(3)           DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_ai_chat_message_session` (`session_id`, `created_time`),
    KEY `idx_ai_chat_message_user` (`user_id`, `created_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI 问数对话历史';

-- ----------------------------
-- 数据大屏与 AI 菜单种子（幂等，可重复执行；放在角色全量绑定之后，绑定由下列语句自带）
-- ----------------------------

--    会被当成缺 layout 的单级页去拆，直接抛
--    `Layout component "view.datav" not found` 并整条丢弃，表现就是 /datav 永远 403。
--    对应地，列表视图必须落在 src/views/dynamic/datav/，菜单名才命中得到 views 里的键。
-- ----------------------------
SET @datav_menu_id := 'da7a00c0f31b11ef905d78fbdead0010';
SET @dynamic_dir_id := (SELECT `id` FROM `system_menu` WHERE `menu_code` = 'dynamic' AND `deleted_flag` = 'N' LIMIT 1);

INSERT INTO `system_menu`
(`id`, `menu_name`, `menu_code`, `order_num`, `visible`, `path`, `menu_type`, `permission`, `status`, `icon`,
 `app_menu`, `component`, `params`, `is_frame`, `is_cache`, `parent_id`, `organization_id`, `tenant_id`, `revision`,
 `created_by`, `created_time`, `deleted_flag`)
SELECT @datav_menu_id, '数据大屏', 'dynamic_datav', 16, 1, '/dynamic/datav', 'C', 'datav:screen:list', 1, 'icon-default', 0,
       'self', NULL, 0, 0,
       @dynamic_dir_id, '16663888968888889', 'system-default', 1, '188666666666', NOW(), 'N'
WHERE @dynamic_dir_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = @datav_menu_id);

-- 归一化历史写法：本脚本早期版本插的是 `datav` + `/datav`，那种形态在前端会被整条丢掉，
-- 存量库里已按旧码插入的行必须就地改写，否则重跑脚本既不会补上新形态也不会报错。

-- 绑定应用（跟随「动态表单」目录所在应用）
INSERT INTO `system_app_menu`
(`id`, `app_id`, `menu_id`, `status`, `organization_id`, `tenant_id`, `revision`, `created_by`, `created_time`, `deleted_flag`, `must_menu`, `visible_menu`)
SELECT 'da7a00c0f31b11ef905d78fbdead0011', p.app_id, @datav_menu_id, 1, '16663888968888889', 'system-default', 1,
       '188666666666', NOW(), 'N', 0, 1
FROM (SELECT app_id FROM `system_app_menu` WHERE menu_id = @dynamic_dir_id AND `deleted_flag` = 'N' LIMIT 1) p
WHERE @dynamic_dir_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `system_app_menu` WHERE menu_id = @datav_menu_id AND `deleted_flag` = 'N');

-- 绑定默认组织
INSERT INTO `system_organization_menu`
(`id`, `menu_id`, `app_id`, `status`, `organization_id`, `tenant_id`, `revision`, `created_by`, `created_time`, `deleted_flag`)
SELECT 'da7a00c0f31b11ef905d78fbdead0012', @datav_menu_id, NULL, 1, '16663888968888889', 'system-default', 1,
       '188666666666', NOW(), 'N'
WHERE NOT EXISTS (SELECT 1 FROM `system_organization_menu` WHERE menu_id = @datav_menu_id AND `deleted_flag` = 'N');

-- ----------------------------
-- 4. 设计态权限码
--    控制器上 5 个 @PreAuthorize 用的码必须落库，否则除超管外无人能建大屏——
--    报表模块踩过这个坑，这里一次配齐。
--    C 菜单自身已承载 datav:screen:list，下面的 NOT EXISTS 判重会自然跳过它。
-- ----------------------------
INSERT INTO `system_menu`
(`id`, `menu_name`, `menu_code`, `order_num`, `visible`, `path`, `menu_type`, `permission`, `status`,
 `icon`, `app_menu`, `component`, `params`, `is_frame`, `is_cache`, `parent_id`, `organization_id`,
 `tenant_id`, `revision`, `created_by`, `created_time`, `updated_by`, `updated_time`, `remark`,
 `deleted_time`, `deleted_by`, `deleted_flag`)
SELECT REPLACE(UUID(), '-', ''), b.menu_name, '', 1, 1, '', 'F', b.permission, 1,
       'icon-default', 0, '', NULL, 0, 0,
       d.id, d.organization_id, d.tenant_id, 1, d.created_by, NOW(), NULL, NULL, b.remark,
       NULL, NULL, 'N'
FROM (
         SELECT '大屏列表' AS menu_name, 'datav:screen:list' AS permission, '大屏-列表（与 C 菜单同码，判重跳过）' AS remark
         UNION ALL
         SELECT '大屏详情', 'datav:screen:query', '大屏-详情与版本列表'
         UNION ALL
         SELECT '大屏新增', 'datav:screen:add', '大屏-保存与复制'
         UNION ALL
         SELECT '大屏编辑', 'datav:screen:edit', '大屏-发布/下线与版本回滚'
         UNION ALL
         SELECT '大屏删除', 'datav:screen:remove', '大屏-删除'
     ) `b`
         JOIN (SELECT `id`, `organization_id`, `tenant_id`, `created_by`
               FROM `system_menu`
               WHERE `menu_code` IN ('datav', 'dynamic_datav') AND `menu_type` = 'C' AND `deleted_flag` = 'N'
               LIMIT 1) d
WHERE NOT EXISTS (SELECT 1
                  FROM `system_menu` `x`
                  WHERE `x`.`deleted_flag` = 'N'
                    AND `x`.`menu_type` = 'F'
                    AND `x`.`permission` = b.permission);

-- 授权组织管理员角色（沿用「动态表单」目录的授权面，与报表同口径）
INSERT INTO `system_role_menu`
(`tenant_id`, `revision`, `created_by`, `created_time`, `deleted_flag`, `id`, `role_id`, `menu_id`, `permission`, `data_scope`, `app_id`)
SELECT 'system-default', 1, '188666666666', NOW(), 'N', 'da7a00c0f31b11ef905d78fbdead0013', '12263522227777777',
       @datav_menu_id, 'datav:screen:list', 20, NULL
WHERE @datav_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `system_role_menu` WHERE menu_id = @datav_menu_id AND `deleted_flag` = 'N');

-- 把大屏 C 菜单与其按钮授给「已经拿到报表设计器」的角色/组织：大屏是报表的延伸能力，
--    锚点同时接受 report-designer 与 dynamic_report-designer 两种菜单码：报表脚本把该菜单
--    归一化成带层级的码后，这里只认旧码就会静默 0 行授权（大屏能看见却没人有权限）。
-- 不新造一条授权面，避免「有设计器却没有大屏」这种需要人工补授权的情况。
INSERT INTO `system_role_menu`
(`id`, `remark`, `role_id`, `menu_id`, `permission`, `data_scope`, `app_id`,
 `tenant_id`, `revision`, `created_by`, `created_time`, `updated_by`, `updated_time`,
 `deleted_flag`, `deleted_by`, `deleted_time`)
SELECT REPLACE(UUID(), '-', ''), NULL, r.role_id, m.menu_id, m.permission, r.data_scope, r.app_id,
       r.tenant_id, 1, r.created_by, NOW(), NULL, NULL, 'N', NULL, NULL
FROM (
         SELECT x.id AS menu_id, x.permission AS permission
         FROM system_menu x
         WHERE x.deleted_flag = 'N'
           AND (x.menu_code IN ('datav', 'dynamic_datav') OR (x.menu_type = 'F' AND x.permission LIKE 'datav:screen:%'))
     ) m
         JOIN system_role_menu r
              ON r.menu_id = (SELECT id FROM system_menu WHERE menu_code IN ('report-designer', 'dynamic_report-designer') AND deleted_flag = 'N' LIMIT 1)
                  AND r.deleted_flag = 'N'
WHERE NOT EXISTS (SELECT 1 FROM `system_role_menu` e WHERE e.menu_id = m.menu_id AND e.role_id = r.role_id);

INSERT INTO `system_organization_menu`
(`id`, `menu_id`, `app_id`, `status`, `organization_id`, `tenant_id`, `revision`, `created_by`, `created_time`, `deleted_flag`)
SELECT REPLACE(UUID(), '-', ''), m.menu_id, NULL, 1, o.organization_id, 'system-default', 1, o.created_by, NOW(), 'N'
FROM (
         SELECT x.id AS menu_id
         FROM system_menu x
         WHERE x.deleted_flag = 'N'
           AND (x.menu_code IN ('datav', 'dynamic_datav') OR (x.menu_type = 'F' AND x.permission LIKE 'datav:screen:%'))
     ) m
         JOIN system_organization_menu o
              ON o.menu_id = (SELECT id FROM system_menu WHERE menu_code IN ('report-designer', 'dynamic_report-designer') AND deleted_flag = 'N' LIMIT 1)
                  AND o.deleted_flag = 'N'
WHERE NOT EXISTS (SELECT 1 FROM `system_organization_menu` e WHERE e.menu_id = m.menu_id AND e.organization_id = o.organization_id);

-- ----------------------------
-- 4.5 默认同时授权「数据集列表 / 数据集查询」两个按钮码（2026-10-17 决策）
--     大屏的属性面板要列数据集、要做设计态预览取数，分别要 report:dataset:list 与
--     report:dataset:query。缺任一条时面板只能退回静态数据，用户看到的是「选不了数据集」
--     而不是权限问题。既然大屏菜单本来就是跟着报表设计器授下去的，这里把这两条一并发给
--     同一批角色/组织，避免出现「能设计大屏却绑不了数据」。
--     报表脚本未执行时子查询为空，本节自然 0 行，不会报错。
-- ----------------------------
INSERT INTO `system_role_menu`
(`id`, `remark`, `role_id`, `menu_id`, `permission`, `data_scope`, `app_id`,
 `tenant_id`, `revision`, `created_by`, `created_time`, `updated_by`, `updated_time`,
 `deleted_flag`, `deleted_by`, `deleted_time`)
SELECT REPLACE(UUID(), '-', ''), '大屏默认随附授权', r.role_id, m.menu_id, m.permission, r.data_scope, r.app_id,
       r.tenant_id, 1, r.created_by, NOW(), NULL, NULL, 'N', NULL, NULL
FROM (
         SELECT x.id AS menu_id, x.permission
         FROM system_menu x
         WHERE x.deleted_flag = 'N'
           AND x.menu_type = 'F'
           AND x.permission IN ('report:dataset:list', 'report:dataset:query')
     ) m
         JOIN system_role_menu r
              ON r.menu_id = @datav_menu_id AND r.deleted_flag = 'N'
WHERE NOT EXISTS (SELECT 1 FROM `system_role_menu` e WHERE e.menu_id = m.menu_id AND e.role_id = r.role_id);

INSERT INTO `system_organization_menu`
(`id`, `menu_id`, `app_id`, `status`, `organization_id`, `tenant_id`, `revision`, `created_by`, `created_time`, `deleted_flag`)
SELECT REPLACE(UUID(), '-', ''), m.menu_id, o.app_id, 1, o.organization_id, 'system-default', 1, o.created_by, NOW(), 'N'
FROM (
         SELECT x.id AS menu_id
         FROM system_menu x
         WHERE x.deleted_flag = 'N'
           AND x.menu_type = 'F'
           AND x.permission IN ('report:dataset:list', 'report:dataset:query')
     ) m
         JOIN system_organization_menu o
              ON o.menu_id = @datav_menu_id AND o.deleted_flag = 'N'
WHERE NOT EXISTS (SELECT 1 FROM `system_organization_menu` e WHERE e.menu_id = m.menu_id AND e.organization_id = o.organization_id);

INSERT INTO system_menu (id, menu_name, menu_code, parent_id, menu_type, path, component, permission,
                         icon, order_num, visible, status, app_menu, deleted_flag, created_by, created_time, organization_id, remark)
SELECT '2107100000000001001', 'AI 搭建', 'ai', '0', 'M', '/ai', 'basic', NULL,
       'icon-park-outline:robot', 5, 1, 1, 0, 'N', 'system', NOW(), '16663888968888889', 'AI 对话式搭建入口'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE menu_code = 'ai' AND deleted_flag = 'N');

INSERT INTO system_menu (id, menu_name, menu_code, parent_id, menu_type, path, component, permission,
                         icon, order_num, visible, status, app_menu, deleted_flag, created_by, created_time, organization_id, remark)
SELECT '2107100000000001002', 'AI 对话搭建', 'ai_build', '2107100000000001001', 'C', '/ai/build', 'self', 'system:aiBuild:use',
       'icon-park-outline:chat', 10, 1, 1, 0, 'N', 'system', NOW(), '16663888968888889', '与 AI 对话生成表单/流程/报表草稿，人工确认发布'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE menu_code = 'ai_build' AND deleted_flag = 'N');

-- AI 模型配置页（挂平台管理目录 1798986040628969473）
INSERT INTO system_menu (id, menu_name, menu_code, parent_id, menu_type, path, component, permission,
                         icon, order_num, visible, status, app_menu, deleted_flag, created_by, created_time, organization_id, remark)
SELECT '2107100000000001003', 'AI 模型配置', 'ai_config', '1798986040628969473', 'C', '/ai/config', 'self', 'system:aiConfig:query',
       'icon-park-outline:config', 95, 1, 1, 0, 'N', 'system', NOW(), '16663888968888889', 'B5 模型配置前端（公网/内网、apiKey 密文、数据流向明示）'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE menu_code = 'ai_config' AND deleted_flag = 'N');

-- ---------- 绑定种子：组织菜单 / SuperRole / 平台管理应用 ----------
-- 组织菜单（组织 16663888968888889）
INSERT INTO system_organization_menu (id, menu_id, app_id, status, organization_id, created_by, created_time, deleted_flag)
SELECT REPLACE(UUID(), '-', ''), m.id, NULL, 1, '16663888968888889', 'system', NOW(), 'N'
FROM system_menu m
WHERE m.menu_code IN ('ai', 'ai_build', 'ai_config') AND m.deleted_flag = 'N'
  AND NOT EXISTS (SELECT 1 FROM system_organization_menu om
      WHERE om.menu_id = m.id AND om.organization_id = '16663888968888889' AND om.deleted_flag = 'N');

-- SuperRole 角色菜单（组织管理员；角色本身持 *:*:*，绑定只为菜单可见）
INSERT INTO system_role_menu (id, role_id, menu_id, permission, deleted_flag, created_by, created_time)
SELECT REPLACE(UUID(), '-', ''), '12263522227777777', m.id, COALESCE(m.permission, ''), 'N', 'system', NOW()
FROM system_menu m
WHERE m.menu_code IN ('ai', 'ai_build', 'ai_config') AND m.deleted_flag = 'N'
  AND NOT EXISTS (SELECT 1 FROM system_role_menu rm
      WHERE rm.role_id = '12263522227777777' AND rm.menu_id = m.id AND rm.deleted_flag = 'N');

-- 平台管理应用绑定（应用 1768482874137485238；目录/页面都要绑，树由绑定集构建）
INSERT INTO system_app_menu (id, app_id, menu_id, status, tenant_id, revision, created_by, created_time, deleted_flag, organization_id)
SELECT REPLACE(UUID(), '-', ''), '1768482874137485238', m.id, 1, 'system-default', 1, 'system', NOW(), 'N', '16663888968888889'
FROM system_menu m
WHERE m.menu_code IN ('ai', 'ai_build', 'ai_config') AND m.deleted_flag = 'N'
  AND NOT EXISTS (SELECT 1 FROM system_app_menu am
      WHERE am.app_id = '1768482874137485238' AND am.menu_id = m.id AND am.deleted_flag = 'N');

INSERT INTO system_menu (id, menu_name, menu_code, parent_id, menu_type, path, component, permission,
                         icon, order_num, visible, status, app_menu, deleted_flag, created_by, created_time, organization_id, remark)
SELECT '2107100000000001001', 'AI 搭建', 'ai', '0', 'M', '/ai', 'basic', NULL,
       'icon-park-outline:robot', 5, 1, 1, 0, 'N', 'system', NOW(), '16663888968888889', 'AI 对话式搭建入口'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE menu_code = 'ai' AND deleted_flag = 'N');

-- AI 问数助手页（业务用户问数入口；不要求设计态报表权限）
INSERT INTO system_menu (id, menu_name, menu_code, parent_id, menu_type, path, component, permission,
                         icon, order_num, visible, status, app_menu, deleted_flag, created_by, created_time, organization_id, remark)
SELECT '2107100000000001004', 'AI 问数', 'ai_analyze', '2107100000000001001', 'C', '/ai/analyze', 'self', 'system:aiAnalyze:use',
       'mdi:chart-box-plus-outline', 30, 1, 1, 0, 'N', 'system', NOW(), '16663888968888889', '与 AI 对话问数出图，结果可存为报表'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE menu_code = 'ai_analyze' AND deleted_flag = 'N');

-- 问数取数按钮权限（F 型）：/back/report/data/chat 与 /back/report/chat/save-chart 共用
INSERT INTO system_menu (id, menu_name, menu_code, parent_id, menu_type, path, component, permission,
                         icon, order_num, visible, status, app_menu, deleted_flag, created_by, created_time, organization_id, remark)
SELECT '2107100000000001005', 'AI 问数取数', 'ai_analyze_data', '2107100000000001004', 'F', '', '', 'report:data:analyze',
       '', 1, 1, 1, 0, 'N', 'system', NOW(), '16663888968888889', 'AI 问数取数/存图端点权限（不碰设计态 report:dataset:*）'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE menu_code = 'ai_analyze_data' AND deleted_flag = 'N');

-- ---------- 绑定种子：组织菜单 / SuperRole / SYSTEM 应用 ----------
-- 组织菜单（组织 16663888968888889）
INSERT INTO system_organization_menu (id, menu_id, app_id, status, organization_id, created_by, created_time, deleted_flag)
SELECT REPLACE(UUID(), '-', ''), m.id, NULL, 1, '16663888968888889', 'system', NOW(), 'N'
FROM system_menu m
WHERE m.menu_code IN ('ai', 'ai_analyze', 'ai_analyze_data') AND m.deleted_flag = 'N'
  AND NOT EXISTS (SELECT 1 FROM system_organization_menu om
      WHERE om.menu_id = m.id AND om.organization_id = '16663888968888889' AND om.deleted_flag = 'N');

-- SuperRole 角色菜单（组织管理员；角色本身持 *:*:*，绑定只为菜单可见）
INSERT INTO system_role_menu (id, role_id, menu_id, permission, deleted_flag, created_by, created_time)
SELECT REPLACE(UUID(), '-', ''), '12263522227777777', m.id, COALESCE(m.permission, ''), 'N', 'system', NOW()
FROM system_menu m
WHERE m.menu_code IN ('ai', 'ai_analyze', 'ai_analyze_data') AND m.deleted_flag = 'N'
  AND NOT EXISTS (SELECT 1 FROM system_role_menu rm
      WHERE rm.role_id = '12263522227777777' AND rm.menu_id = m.id AND rm.deleted_flag = 'N');

-- AI 搭建树归属迁到 SYSTEM 应用（与 init.sql 2026-10-17 同口径；已迁移时 0 行）

-- 新菜单挂 SYSTEM 应用（应用 1768482874137485238→248 迁移后仍缺绑定的补上；目录/页面/按钮都要绑，树由绑定集构建）
INSERT INTO system_app_menu (id, app_id, menu_id, status, tenant_id, revision, created_by, created_time, deleted_flag, organization_id)
SELECT REPLACE(UUID(), '-', ''), '1768482874137485248', m.id, 1, 'system-default', 1, 'system', NOW(), 'N', '16663888968888889'
FROM system_menu m
WHERE m.menu_code IN ('ai', 'ai_analyze', 'ai_analyze_data') AND m.deleted_flag = 'N'
  AND NOT EXISTS (SELECT 1 FROM system_app_menu am
      WHERE am.app_id = '1768482874137485248' AND am.menu_id = m.id AND am.deleted_flag = 'N');

-- ---------- ④ M3-4 移动端问数入口：AI 问数树挂到组织内全部应用 ----------
-- APP 应用详情的菜单树按 system_app_menu 构建（SystemMenuMapper.selectMenuButtonTree*），
-- 未绑定的应用里 /ai/analyze 路由不存在，深链 ?bare=1&app=<code>&token= 打不开。
-- 默认给组织内全部应用挂上（业务用户在每个应用内直接问数；管理员可按应用在菜单管理里摘除），
-- F 按钮不进应用树（角色授权走 system_role_menu，与应用绑定无关）；SYSTEM 应用已绑，幂等跳过。
INSERT INTO system_app_menu (id, app_id, menu_id, status, tenant_id, revision, created_by, created_time, deleted_flag, organization_id)
SELECT REPLACE(UUID(), '-', ''), a.id, m.id, 1, 'system-default', 1, 'system', NOW(), 'N', '16663888968888889'
FROM system_app_info a
JOIN system_menu m ON m.menu_code IN ('ai', 'ai_analyze') AND m.deleted_flag = 'N'
WHERE a.deleted_flag = 'N'
  AND NOT EXISTS (SELECT 1 FROM system_app_menu am
      WHERE am.app_id = a.id AND am.menu_id = m.id AND am.deleted_flag = 'N');

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================================
-- 初始化完成后快速自检（人工执行）：
--   SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='dynamic_platform';  -- 期望 97
--   SELECT COUNT(*) FROM system_menu;        -- 期望 390
--   SELECT COUNT(*) FROM system_user_base;   -- 期望 1（admin）
--   SELECT COUNT(*) FROM system_role;        -- 期望 2
--   SELECT COUNT(*) FROM system_app_info;    -- 期望 2（SYSTEM/PLATFORM）
-- 默认登录：admin / admin123（登录接口传 SHA512('admin123') 的摘要，页面端自动处理）
-- ============================================================================
