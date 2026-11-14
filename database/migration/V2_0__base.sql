-- =====================================================================
-- CareerFlow-AI 2.0 基础增量迁移
-- 适用：MySQL 8，库 ai_resume（与 database/init.sql 同一库）
--
-- 执行方式：
--   mysql -u <user> -p ai_resume < database/migration/V2_0__base.sql
--
-- 说明：
--   1. 只做增量：不删除、不重命名旧表，不修改旧列语义。
--   2. 可重复执行：新表用 CREATE TABLE IF NOT EXISTS；
--      加列/加索引先查 information_schema，已存在则跳过。
--   3. 不建物理外键，沿用「逻辑外键 + 索引」约定。
--   4. 执行顺序：本脚本 → 按需执行 V2_0__admin.sql。
--   5. 建议在业务低峰执行（存量数据少，ALTER 锁表时间可忽略）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 0. 幂等辅助存储过程
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
-- 1. 新增表
-- ---------------------------------------------------------------------

-- 1.1 八步创建草稿：可反复覆盖的编辑态，与不可变的 resume_version 分离
CREATE TABLE IF NOT EXISTS resume_draft (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id       BIGINT       NOT NULL COMMENT '归属用户，关联 sys_user',
    resume_id     BIGINT       NULL COMMENT '关联 resume；新建草稿可为空，首次生成后回填',
    draft_name    VARCHAR(100) NULL COMMENT '草稿名称',
    current_step  TINYINT      NOT NULL DEFAULT 1 COMMENT '当前步骤 1-8',
    content       JSON         NOT NULL COMMENT '八步表单完整内容快照',
    revision      INT          NOT NULL DEFAULT 1 COMMENT '乐观锁版本，每次保存 +1',
    status        VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/COMPLETED/ABANDONED',
    last_saved_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近自动保存时间',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_user_status (user_id, status),
    KEY idx_resume_id (resume_id),
    KEY idx_last_saved_at (last_saved_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '简历八步创建草稿表';

-- 1.2 区块级修改历史：AI 修改必须先落 PENDING，用户确认后才 APPLIED
CREATE TABLE IF NOT EXISTS resume_edit_history (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id         BIGINT       NOT NULL COMMENT '归属用户',
    resume_id       BIGINT       NULL COMMENT '关联 resume',
    draft_id        BIGINT       NULL COMMENT '关联 resume_draft；草稿态修改时填写',
    base_version_id BIGINT       NULL COMMENT '基于哪个正式版本修改',
    section_key     VARCHAR(50)  NOT NULL COMMENT '区块：BASIC/EDUCATION/PROJECT/SKILL/EXPERIENCE/SELF_EVALUATION/SOFT/PHOTO',
    before_content  JSON         NULL COMMENT '修改前内容',
    after_content   JSON         NULL COMMENT '修改后候选内容',
    source          VARCHAR(20)  NOT NULL COMMENT 'AI/MANUAL',
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPLIED/REJECTED',
    warnings        TEXT         NULL COMMENT 'JSON 数组：AI 候选的事实校验/降级提示',
    prompt_summary  VARCHAR(500) NULL COMMENT '本次修改意图摘要',
    model           VARCHAR(50)  NULL COMMENT '使用的模型，便于追溯',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    applied_at      DATETIME     NULL COMMENT '用户确认应用时间',
    PRIMARY KEY (id),
    KEY idx_user_resume (user_id, resume_id),
    KEY idx_draft_status (draft_id, status),
    KEY idx_base_version (base_version_id),
    KEY idx_created_at (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '简历区块修改历史表';

-- 1.3 文件素材：原始 DOCX / JD 截图 / 证件照，下载必须经后端鉴权
CREATE TABLE IF NOT EXISTS document_asset (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id       BIGINT       NOT NULL COMMENT '归属用户',
    asset_type    VARCHAR(30)  NOT NULL COMMENT 'ORIGINAL_RESUME/JD_SCREENSHOT/PHOTO/OTHER',
    original_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
    storage_path  VARCHAR(500) NOT NULL COMMENT '本地存储相对路径，禁止直接暴露给前端',
    mime_type     VARCHAR(100) NOT NULL COMMENT 'MIME 类型',
    file_size     BIGINT       NOT NULL COMMENT '字节数',
    sha256        CHAR(64)     NULL COMMENT '内容摘要，用于去重',
    source        VARCHAR(20)  NOT NULL DEFAULT 'UPLOAD' COMMENT 'UPLOAD/GENERATED',
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DELETED',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_user_type (user_id, asset_type),
    KEY idx_sha256 (sha256)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '文件素材表';

-- 1.4 企业信息库：公共数据，无 user_id，写接口仅管理员
CREATE TABLE IF NOT EXISTS company (
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    name             VARCHAR(200) NOT NULL COMMENT '企业全称',
    short_name       VARCHAR(100) NULL COMMENT '简称',
    industry         VARCHAR(100) NULL COMMENT '行业',
    company_type     VARCHAR(50)  NULL COMMENT '企业类型：国企/民企/外企/合资',
    scale            VARCHAR(50)  NULL COMMENT '规模区间',
    city             VARCHAR(100) NULL COMMENT '总部城市',
    website          VARCHAR(500) NULL COMMENT '企业官网',
    recruitment_url  VARCHAR(500) NULL COMMENT '招聘官网',
    logo_url         VARCHAR(500) NULL COMMENT 'Logo 地址',
    description      TEXT         NULL COMMENT '企业简介',
    status           VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
    source           VARCHAR(50)  NOT NULL DEFAULT 'SEED' COMMENT 'SEED/ADMIN_IMPORT/MANUAL',
    source_url       VARCHAR(500) NULL COMMENT '信息来源链接',
    last_verified_at DATETIME     NULL COMMENT '最后核验时间',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_name (name),
    KEY idx_industry (industry),
    KEY idx_city (city),
    KEY idx_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '企业信息表';

-- 1.5 企业招聘信息：秋招/春招/实习/社招，记录来源与最后核验时间
CREATE TABLE IF NOT EXISTS company_recruitment (
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    company_id       BIGINT       NOT NULL COMMENT '关联 company',
    title            VARCHAR(200) NOT NULL COMMENT '招聘项目名称',
    recruitment_type VARCHAR(30)  NOT NULL COMMENT 'AUTUMN/SPRING/INTERNSHIP/SOCIAL',
    target_graduates VARCHAR(100) NULL COMMENT '面向届别，如 2027 届',
    city             VARCHAR(200) NULL COMMENT '工作城市，可多值文本',
    start_at         DATETIME     NULL COMMENT '开始时间',
    end_at           DATETIME     NULL COMMENT '截止时间',
    apply_url        VARCHAR(500) NULL COMMENT '投递入口',
    status           VARCHAR(20)  NOT NULL DEFAULT 'UNKNOWN' COMMENT 'OPEN/CLOSED/UPCOMING/UNKNOWN',
    source           VARCHAR(50)  NOT NULL DEFAULT 'SEED' COMMENT 'SEED/ADMIN_IMPORT/MANUAL',
    source_url       VARCHAR(500) NULL COMMENT '信息来源链接',
    last_verified_at DATETIME     NULL COMMENT '最后核验时间',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_company (company_id),
    KEY idx_type_status (recruitment_type, status),
    KEY idx_end_at (end_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '企业招聘信息表';

-- ---------------------------------------------------------------------
-- 2. 旧表加列（只加列/加索引，已存在则跳过）
-- ---------------------------------------------------------------------

-- 2.1 sys_user 角色
CALL cf_add_column('sys_user', 'role',
    "VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT 'USER/ADMIN' AFTER password_hash");
CALL cf_add_index('sys_user', 'idx_role', 'KEY idx_role (role)');

-- 2.2 job 企业关联与可见性
CALL cf_add_column('job', 'company_id', 'BIGINT NULL COMMENT ''关联 company'' AFTER company');
CALL cf_add_column('job', 'created_by', 'BIGINT NULL COMMENT ''创建者用户 ID；系统数据为空'' AFTER source_type');
CALL cf_add_column('job', 'visibility',
    "VARCHAR(20) NOT NULL DEFAULT 'PUBLIC' COMMENT 'PUBLIC/PRIVATE' AFTER created_by");
CALL cf_add_index('job', 'idx_company_id', 'KEY idx_company_id (company_id)');
CALL cf_add_index('job', 'idx_created_by', 'KEY idx_created_by (created_by)');
CALL cf_add_index('job', 'idx_visibility', 'KEY idx_visibility (visibility)');

-- 2.3 resume 关联原始文件
CALL cf_add_column('resume', 'source_document_asset_id',
    'BIGINT NULL COMMENT ''原始简历文件，关联 document_asset'' AFTER current_version_id');
CALL cf_add_index('resume', 'idx_source_asset', 'KEY idx_source_asset (source_document_asset_id)');

-- 2.4 job_application 投递渠道
CALL cf_add_column('job_application', 'channel',
    "VARCHAR(30) NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL/OFFICIAL_SITE/JOB_BOARD/REFERRAL' AFTER resume_version_id");
CALL cf_add_column('job_application', 'applied_url',
    'VARCHAR(500) NULL COMMENT ''实际投递页面链接'' AFTER channel');
CALL cf_add_index('job_application', 'idx_channel', 'KEY idx_channel (channel)');

-- 2.5 optimization_task 用户归属
CALL cf_add_column('optimization_task', 'user_id', 'BIGINT NULL COMMENT ''归属用户'' AFTER id');
CALL cf_add_index('optimization_task', 'idx_user_id', 'KEY idx_user_id (user_id)');

-- 2.6 resume_edit_history 事实校验提示（存量库补列）
CALL cf_add_column('resume_edit_history', 'warnings',
    'TEXT NULL COMMENT ''JSON 数组：AI 候选的事实校验/降级提示'' AFTER status');

-- ---------------------------------------------------------------------
-- 3. 存量数据回填：optimization_task.user_id
--    优先经 chat_session.user_id，其次经 resume_version → resume.user_id。
--    只更新 user_id IS NULL 的行，可重复执行。
-- ---------------------------------------------------------------------
UPDATE optimization_task t
    JOIN chat_session s ON s.id = t.chat_session_id
SET t.user_id = s.user_id
WHERE t.user_id IS NULL;

UPDATE optimization_task t
    JOIN resume_version rv ON rv.id = t.resume_version_id
    JOIN resume r ON r.id = rv.resume_id
SET t.user_id = r.user_id
WHERE t.user_id IS NULL;

SELECT COUNT(*) AS optimization_task_without_user_id
FROM optimization_task
WHERE user_id IS NULL;

-- ---------------------------------------------------------------------
-- 4. 清理辅助存储过程
-- ---------------------------------------------------------------------
DROP PROCEDURE IF EXISTS cf_add_column;
DROP PROCEDURE IF EXISTS cf_add_index;
