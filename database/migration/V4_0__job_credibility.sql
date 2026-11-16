-- =====================================================================
-- CareerFlow-AI 2.0 阶段 1：岗位数据可信度增量迁移
-- 适用：MySQL 8，库 ai_resume（与 init.sql / V2_0 / V3_0 同一库）
--
-- 执行顺序：init.sql → V2_0__base.sql → V3_0__company.sql → V3_0__company_seed.sql
--           → V4_0__job_credibility.sql
-- 说明：
--   1. job 表只做增量加列加索引，不重建、不删除、不重命名旧表。
--   2. 可重复执行：加列/加索引先查 information_schema，已存在则跳过。
--   3. 复用 external_url 作为「原招聘信息链接」，不重复建列。
--   4. 存量演示数据 source_type 由 MOCK 迁移为 DEMO，并标记 verification_status='DEMO'。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 0. 幂等辅助存储过程（与 V2_0 / V3_0 一致，先删后建保证最新）
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
-- 1. job 表增量：来源追溯 + 岗位级核验状态
--    V3_0 已有：company_id / created_by / visibility / recruitment_batch /
--              recruitment_status / external_url（原招聘页链接）
--
--    双状态模型：
--      verification_status  数据可信度 DEMO / UNVERIFIED / VERIFIED / EXPIRED
--      recruitment_status   招聘生命周期 OPEN / SOON / CLOSED（已有）
--    后端统一计算 displayStatus 返回前端，前端不自行推断。
-- ---------------------------------------------------------------------
CALL cf_add_column('job', 'source_name',
    'VARCHAR(100) NULL COMMENT ''来源名称，如 企业官网 / 用户粘贴'' AFTER source_type');
CALL cf_add_column('job', 'published_at',
    'DATETIME NULL COMMENT ''原招聘信息发布时间'' AFTER source_name');
CALL cf_add_column('job', 'verification_status',
    'VARCHAR(20) NOT NULL DEFAULT ''UNVERIFIED'' COMMENT ''DEMO 演示 / UNVERIFIED 待核实 / VERIFIED 已核实 / EXPIRED 已过期'' AFTER recruitment_status');
CALL cf_add_column('job', 'verified_at',
    'DATETIME NULL COMMENT ''最近核验时间'' AFTER verification_status');
CALL cf_add_column('job', 'verified_by',
    'BIGINT NULL COMMENT ''核验管理员 user_id，便于审计'' AFTER verified_at');
CALL cf_add_column('job', 'valid_until',
    'DATETIME NULL COMMENT ''招聘信息有效期截止'' AFTER verified_by');

CALL cf_add_index('job', 'idx_job_verification', 'KEY idx_job_verification (verification_status)');
CALL cf_add_index('job', 'idx_job_source_type', 'KEY idx_job_source_type (source_type)');

-- ---------------------------------------------------------------------
-- 2. 存量迁移：MOCK（内置模拟）→ DEMO（演示数据），明确不可当作真实招聘
-- ---------------------------------------------------------------------
UPDATE job
SET source_type = 'DEMO',
    verification_status = 'DEMO',
    source_name = COALESCE(NULLIF(source_name, ''), '内置演示数据')
WHERE source_type = 'MOCK';

-- 已是 DEMO 但核验状态未对齐的历史数据（重复执行安全）
UPDATE job
SET verification_status = 'DEMO',
    source_name = COALESCE(NULLIF(source_name, ''), '内置演示数据')
WHERE source_type = 'DEMO'
  AND (verification_status IS NULL OR verification_status <> 'DEMO');
