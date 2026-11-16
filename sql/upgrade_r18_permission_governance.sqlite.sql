-- ============================================================================
-- R1-8 权限治理 升级脚本（SQLite 版；菜单授权段幂等可重入）
-- 内容：
--   1) system_role_user 加 expire_time 授权有效期列（同 V202611121200，见迁移台账；
--      SQLite 无条件 DDL，重复执行会在加列处报 duplicate column——报错即已升过，菜单段可单独重放）
--   2) 权限审计页菜单（平台管理组 /system/permission-audit）+ 组织/角色授权
-- 适用：存量 dev/local=SQLite 库（data/privernal.db）；全新安装执行 init-sqlite.sql 已包含，无需本脚本。
-- 执行前见 sql/README.md「迁移台账」节（checksum 登记）。
-- ============================================================================

-- 1) 授权有效期列
ALTER TABLE system_role_user ADD COLUMN expire_time TEXT;

-- 2) 权限审计页菜单
INSERT INTO `system_menu`
(`id`, `menu_name`, `menu_code`, `order_num`, `visible`, `path`, `menu_type`, `permission`, `status`, `icon`,
 `app_menu`, `component`, `params`, `is_frame`, `is_cache`, `parent_id`, `organization_id`, `tenant_id`, `revision`,
 `created_by`, `created_time`, `deleted_flag`)
SELECT 'r18-permission-audit-menu', '权限审计', 'system_permission-audit', 195, 1, '/system/permission-audit',
       'C', 'system:permissionAudit:list', 1, 'icon-default', 0, 'self', NULL, 0, 0,
       '1798986040628969473', '16663888968888889', 'system-default', 1, '188666666666', datetime('now','localtime'), 'N'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 'r18-permission-audit-menu');

-- 3) 组织授权（organization_menu，app_id 留空与种子口径一致）
INSERT INTO `system_organization_menu`
  (`id`, `menu_id`, `app_id`, `status`, `organization_id`, `tenant_id`, `revision`,
   `created_by`, `created_time`, `deleted_flag`)
SELECT lower(hex(randomblob(16))), m.id, NULL, 1, '16663888968888889', 'system-default', 1,
       '188666666666', datetime('now','localtime'), 'N'
FROM `system_menu` m
WHERE m.id = 'r18-permission-audit-menu'
  AND NOT EXISTS (SELECT 1 FROM `system_organization_menu` om
                  WHERE om.menu_id = m.id AND om.organization_id = '16663888968888889'
                    AND (om.deleted_flag IS NULL OR om.deleted_flag = 'N'));

-- 4) 角色授权（role_menu，data_scope=50 组织级上限，与 init 种子口径一致）
INSERT INTO `system_role_menu`
  (`tenant_id`, `revision`, `created_by`, `created_time`, `deleted_flag`, `id`,
   `role_id`, `menu_id`, `permission`, `data_scope`, `app_id`)
SELECT 'system-default', 1, '188666666666', datetime('now','localtime'), 'N', lower(hex(randomblob(16))),
       r.id, m.id, m.permission, '50', NULL
FROM `system_role` r CROSS JOIN `system_menu` m
WHERE m.id = 'r18-permission-audit-menu'
  AND NOT EXISTS (SELECT 1 FROM `system_role_menu` rm
                  WHERE rm.role_id = r.id AND rm.menu_id = m.id
                    AND (rm.deleted_flag IS NULL OR rm.deleted_flag = 'N'));

-- 4b) 应用绑定（system_app_menu：平台管理应用侧栏才显示该页）
INSERT INTO `system_app_menu`
  (`id`, `app_id`, `menu_id`, `status`, `organization_id`, `tenant_id`, `revision`,
   `created_by`, `created_time`, `deleted_flag`)
SELECT lower(hex(randomblob(16))), '1768482874137485238', m.id, 1, '16663888968888889', 'system-default', 1,
       '188666666666', datetime('now','localtime'), 'N'
FROM `system_menu` m
WHERE m.id = 'r18-permission-audit-menu'
  AND NOT EXISTS (SELECT 1 FROM `system_app_menu` am
                  WHERE am.app_id = '1768482874137485238' AND am.menu_id = m.id
                    AND (am.deleted_flag IS NULL OR am.deleted_flag = 'N'));

-- 5) 校验：前三条均为 1 才算成功
SELECT 'menu' AS what, COUNT(*) AS cnt FROM `system_menu`             WHERE id = 'r18-permission-audit-menu'
UNION ALL
SELECT 'org'  AS what, COUNT(*) AS cnt FROM `system_organization_menu` WHERE menu_id = 'r18-permission-audit-menu'
UNION ALL
SELECT 'role' AS what, COUNT(*) AS cnt FROM `system_role_menu`        WHERE menu_id = 'r18-permission-audit-menu'
UNION ALL
SELECT 'expire_time' AS what, COUNT(*) AS cnt FROM pragma_table_info('system_role_user') WHERE name = 'expire_time';

-- 6) 台账登记（checksum 由执行人核对后填写或置空）
INSERT INTO platform_schema_history
    (id, version, script_name, dialect, checksum, applied_by, applied_at)
VALUES (lower(hex(randomblob(16))),
        'V202611121201', 'upgrade_r18_permission_governance.sqlite.sql', 'sqlite', NULL, 'manual',
        datetime('now', 'localtime'));
