-- =====================================================================
-- CareerFlow-AI 阶段 3：企业信息库增量迁移
-- 适用：MySQL 8，库 ai_resume（与 init.sql / V2_0__base.sql 同一库）
--
-- 执行顺序：V2_0__base.sql → V3_0__company.sql → 按需执行 V3_0__company_seed.sql
-- 说明：
--   1. company / company_recruitment 表已在 V2_0__base.sql 建好，本脚本只加列加索引，
--      不重建、不删除、不重命名旧表。
--   2. 可重复执行：加列/加索引先查 information_schema，已存在则跳过。
--   3. 不建物理外键，沿用「逻辑外键 + 索引」约定。
--   4. 种子数据放在 V3_0__company_seed.sql，与本脚本分离。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 0. 幂等辅助存储过程（复用 V2_0；若已存在则先删后建保证最新）
-- ---------------------------------------------------------------------
DROP PROCEDURE IF EXISTS cf_add_column;
DROP PROCEDURE IF EXISTS cf_add_index;

DELIMITER $$

CREATE PROCEDURE cf_add_column(IN p_table VARCHAR(64), IN p_column VARCHAR(64), IN p_ddl VARCHAR(500))
BEGIN
    IF NOT EXISTS (SELECT 1
                   FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = p_table
                     AND COLUMN_NAME = p_column) THEN
        SET @cf_ddl = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN `', p_column, '` ', p_ddl);
        PREPARE cf_stmt FROM @cf_ddl;
        EXECUTE cf_stmt;
        DEALLOCATE PREPARE cf_stmt;
    END IF;
END$$

CREATE PROCEDURE cf_add_index(IN p_table VARCHAR(64), IN p_index VARCHAR(64), IN p_ddl VARCHAR(500))
BEGIN
    IF NOT EXISTS (SELECT 1
                   FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = p_table
                     AND INDEX_NAME = p_index) THEN
        SET @cf_ddl = CONCAT('ALTER TABLE `', p_table, '` ADD ', p_ddl);
        PREPARE cf_stmt FROM @cf_ddl;
        EXECUTE cf_stmt;
        DEALLOCATE PREPARE cf_stmt;
    END IF;
END$$

DELIMITER ;

-- ---------------------------------------------------------------------
-- 1. company 表增量：招聘入口分类 + 链接核验状态
--    V2_0 已有：name / short_name / industry / company_type / scale / city /
--              website / recruitment_url / logo_url / description / status /
--              source / source_url / last_verified_at
--    source 取值扩展：SEED（官方整理导入）/ ADMIN_IMPORT / MANUAL
-- ---------------------------------------------------------------------
CALL cf_add_column('company', 'campus_recruit_url',
    'VARCHAR(500) NULL COMMENT ''校招官网入口'' AFTER recruitment_url');
CALL cf_add_column('company', 'intern_recruit_url',
    'VARCHAR(500) NULL COMMENT ''实习招聘入口'' AFTER campus_recruit_url');
CALL cf_add_column('company', 'social_recruit_url',
    'VARCHAR(500) NULL COMMENT ''社会招聘入口'' AFTER intern_recruit_url');
CALL cf_add_column('company', 'verification_status',
    'VARCHAR(20) NOT NULL DEFAULT ''PENDING'' COMMENT ''VERIFIED 已核实 / PENDING 待核实 / EXPIRED 链接已过期'' AFTER source_url');

CALL cf_add_index('company', 'idx_verification', 'KEY idx_verification (verification_status)');

-- ---------------------------------------------------------------------
-- 2. job 表增量：关联 company + 招聘批次 + 招聘状态
-- ---------------------------------------------------------------------
CALL cf_add_column('job', 'company_id',
    'BIGINT NULL COMMENT ''关联 company.id；存量 job.company 文本保留兜底'' AFTER company');
CALL cf_add_column('job', 'recruitment_batch',
    'VARCHAR(30) NULL COMMENT ''秋招/春招/实习/社招'' AFTER source_type');
CALL cf_add_column('job', 'recruitment_status',
    'VARCHAR(30) NOT NULL DEFAULT ''OPEN'' COMMENT ''OPEN 开放 / CLOSED 已结束 / SOON 即将开始'' AFTER recruitment_batch');

CALL cf_add_index('job', 'idx_company_id', 'KEY idx_company_id (company_id)');
CALL cf_add_index('job', 'idx_batch_status', 'KEY idx_batch_status (recruitment_batch, recruitment_status)');

-- ---------------------------------------------------------------------
-- 3. 存量迁移：不强制回填 job.company_id，
--    继续以 job.company 文本字段做冗余兜底；新录入岗位应关联 company 表。
-- ---------------------------------------------------------------------
