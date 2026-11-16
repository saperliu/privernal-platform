-- ============================================================================
-- R2-14 应用模板市场：存量库菜单补种（MySQL 方言，幂等可重复执行）
--
-- 适用：升级到含 R2-14 的版本后，给存量 MySQL 库补 3 行菜单（模板市场页 + 应用包导出/导入按钮）。
-- 全新安装不需要本脚本（init.sql 已含同名种子行）。
-- SQLite 侧用 upgrade_r214_template_market.sqlite.sql。
--
-- 回滚（按需手工执行）：
--   DELETE FROM system_role_menu         WHERE menu_id IN ('r214-app-template-market-menu','r214-apppkg-export-btn','r214-apppkg-import-btn');
--   DELETE FROM system_organization_menu WHERE menu_id IN ('r214-app-template-market-menu','r214-apppkg-export-btn','r214-apppkg-import-btn');
--   DELETE FROM system_menu              WHERE id       IN ('r214-app-template-market-menu','r214-apppkg-export-btn','r214-apppkg-import-btn');
-- ============================================================================

-- 1) 菜单行（C 页面 + 应用管理下两个 F 按钮）
INSERT INTO `system_menu`
(`id`, `menu_name`, `menu_code`, `order_num`, `visible`, `path`, `menu_type`, `permission`, `status`, `icon`,
 `app_menu`, `component`, `params`, `is_frame`, `is_cache`, `parent_id`, `organization_id`, `tenant_id`, `revision`,
 `created_by`, `created_time`, `deleted_flag`)
SELECT 'r214-app-template-market-menu', '模板市场', 'system_app-template-market', 171, 1, '/system/app-template-market',
       'C', 'system:appPackage:template', 1, 'icon-default', 0, 'self', NULL, 0, 0,
       '1798986040628969473', '16663888968888889', 'system-default', 1, '188666666666', NOW(), 'N'
FROM (SELECT 1) v
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 'r214-app-template-market-menu');

INSERT INTO `system_menu`
(`id`, `menu_name`, `menu_code`, `order_num`, `visible`, `path`, `menu_type`, `permission`, `status`, `icon`,
 `app_menu`, `component`, `params`, `is_frame`, `is_cache`, `parent_id`, `organization_id`, `tenant_id`, `revision`,
 `created_by`, `created_time`, `deleted_flag`)
SELECT 'r214-apppkg-export-btn', '导出应用包', '', 1, 1, '', 'F', 'system:appPackage:export', 1, 'icon-default', 0,
       '', NULL, 0, 0, 'cd2e0e2e6f2411ee983f00163e085602', '16663888968888889', 'system-default', 1, '188666666666', NOW(), 'N'
FROM (SELECT 1) v
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 'r214-apppkg-export-btn');

INSERT INTO `system_menu`
(`id`, `menu_name`, `menu_code`, `order_num`, `visible`, `path`, `menu_type`, `permission`, `status`, `icon`,
 `app_menu`, `component`, `params`, `is_frame`, `is_cache`, `parent_id`, `organization_id`, `tenant_id`, `revision`,
 `created_by`, `created_time`, `deleted_flag`)
SELECT 'r214-apppkg-import-btn', '导入应用包', '', 1, 1, '', 'F', 'system:appPackage:import', 1, 'icon-default', 0,
       '', NULL, 0, 0, 'cd2e0e2e6f2411ee983f00163e085602', '16663888968888889', 'system-default', 1, '188666666666', NOW(), 'N'
FROM (SELECT 1) v
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 'r214-apppkg-import-btn');

-- 2) 组织授权（organization_menu，app_id 留空与种子口径一致）
INSERT INTO `system_organization_menu`
  (`id`, `menu_id`, `app_id`, `status`, `organization_id`, `tenant_id`, `revision`,
   `created_by`, `created_time`, `deleted_flag`)
SELECT REPLACE(UUID(), '-', ''), m.id, NULL, 1, '16663888968888889', 'system-default', 1,
       '188666666666', NOW(), 'N'
FROM `system_menu` m
WHERE m.id IN ('r214-app-template-market-menu', 'r214-apppkg-export-btn', 'r214-apppkg-import-btn')
  AND NOT EXISTS (SELECT 1 FROM `system_organization_menu` om
                  WHERE om.menu_id = m.id AND om.organization_id = '16663888968888889'
                    AND (om.deleted_flag IS NULL OR om.deleted_flag = 'N'));

-- 3) 角色授权（role_menu，data_scope=50 组织级上限，与 init 种子口径一致）
INSERT INTO `system_role_menu`
  (`tenant_id`, `revision`, `created_by`, `created_time`, `deleted_flag`, `id`,
   `role_id`, `menu_id`, `permission`, `data_scope`, `app_id`)
SELECT 'system-default', 1, '188666666666', NOW(), 'N', REPLACE(UUID(), '-', ''),
       r.id, m.id, m.permission, '50', NULL
FROM `system_role` r CROSS JOIN `system_menu` m
WHERE m.id IN ('r214-app-template-market-menu', 'r214-apppkg-export-btn', 'r214-apppkg-import-btn')
  AND NOT EXISTS (SELECT 1 FROM `system_role_menu` rm
                  WHERE rm.role_id = r.id AND rm.menu_id = m.id
                    AND (rm.deleted_flag IS NULL OR rm.deleted_flag = 'N'));

-- 4) 校验：三条均为 3 才算成功
SELECT 'menu'    AS what, COUNT(*) AS cnt FROM `system_menu`              WHERE id       IN ('r214-app-template-market-menu','r214-apppkg-export-btn','r214-apppkg-import-btn')
UNION ALL
SELECT 'org'     AS what, COUNT(*) AS cnt FROM `system_organization_menu` WHERE menu_id  IN ('r214-app-template-market-menu','r214-apppkg-export-btn','r214-apppkg-import-btn')
UNION ALL
SELECT 'role'    AS what, COUNT(*) AS cnt FROM `system_role_menu`         WHERE menu_id  IN ('r214-app-template-market-menu','r214-apppkg-export-btn','r214-apppkg-import-btn');
