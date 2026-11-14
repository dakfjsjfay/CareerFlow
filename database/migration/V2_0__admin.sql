-- =====================================================================
-- CareerFlow-AI 2.0 管理员初始化
--
-- 用途：把指定用户名提升为 ADMIN（角色规则见 docs/database.md）。
-- 前置：已执行 database/migration/V2_0__base.sql（sys_user.role 已存在）。
-- 执行：修改下方 @admin_username 后，按需执行一次：
--   mysql -u <user> -p ai_resume < database/migration/V2_0__admin.sql
--
-- 说明：
--   - 角色不写死在业务代码里，只通过本脚本或数据库运维变更。
--   - 可重复执行（幂等）。
-- =====================================================================

SET @admin_username = 'admin';

UPDATE sys_user
SET role = 'ADMIN'
WHERE username = @admin_username;

SELECT id, username, role
FROM sys_user
WHERE username = @admin_username;
