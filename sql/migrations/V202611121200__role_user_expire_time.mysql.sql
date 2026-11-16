-- R1-8 权限治理①（V202611121200）：system_role_user 加 expire_time 授权有效期列（存量 MySQL 库补；全新安装已含于 init.sql）
-- 背景：授权有效期——NULL=永久授权；> 当前时间=生效中；<= 当前时间=已到期。
--   登录装配（角色 key / 菜单权限 / 路由 / 数据范围）七处 SQL 均按 expire_time 过滤，
--   调度任务 role-auth-expire-recycle 定期回收到期行（软删）并刷新在线用户会话快照。
-- 适用：pro=MySQL；执行前见 sql/README.md「迁移台账」节。
-- 回滚：ALTER TABLE `system_role_user` DROP COLUMN `expire_time`;（已到期被软删的行不可由本回滚恢复）

ALTER TABLE `system_role_user`
    ADD COLUMN `expire_time` datetime NULL DEFAULT NULL COMMENT '授权有效期（NULL=永久，到期后装配过滤+回收扫描软删）' AFTER `user_id`;

-- 登记台账（checksum 由执行人按 README 口径核对后填写或置空）
INSERT INTO `platform_schema_history`
    (`id`, `version`, `script_name`, `dialect`, `checksum`, `applied_by`, `applied_at`)
VALUES (REPLACE(UUID(), '-', ''),
        'V202611121200', 'V202611121200__role_user_expire_time.mysql.sql', 'mysql', NULL, 'manual', NOW());
