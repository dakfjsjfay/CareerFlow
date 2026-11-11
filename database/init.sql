-- ============================================================
-- CareerFlow-AI 智能求职助手 数据库初始化脚本
-- 依据：docs/database.md v1.1（17 张表）
-- 执行方式：mysql -u root -p < init.sql（可重复执行）
-- ============================================================

-- ⚠️ 中文 Windows 的 mysql 客户端默认连接字符集是 GBK，重定向 UTF-8 脚本时
-- 中文会被存成乱码甚至报 "Data too long"。必须先强制连接字符集。
SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS ai_resume
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE ai_resume;

-- ---------- 1. 用户表 ----------
CREATE TABLE IF NOT EXISTS sys_user (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username      VARCHAR(50)  NOT NULL COMMENT '登录名',
    password_hash VARCHAR(255) NOT NULL COMMENT 'BCrypt 加密密码，禁止存明文',
    email         VARCHAR(100) NULL COMMENT '邮箱',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户表';

-- ---------- 2. 用户档案表 ----------
CREATE TABLE IF NOT EXISTS user_profile (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id         BIGINT       NOT NULL COMMENT '关联 sys_user',
    real_name       VARCHAR(50)  NULL COMMENT '姓名',
    phone           VARCHAR(30)  NULL COMMENT '电话',
    school          VARCHAR(100) NULL COMMENT '学校',
    major           VARCHAR(100) NULL COMMENT '专业',
    degree          VARCHAR(50)  NULL COMMENT '学历',
    graduation_year INT          NULL COMMENT '毕业年份',
    skills          JSON         NULL COMMENT '技能列表',
    target_position VARCHAR(100) NULL COMMENT '目标岗位',
    target_city     VARCHAR(100) NULL COMMENT '意向城市',
    expected_salary VARCHAR(50)  NULL COMMENT '期望薪资',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_id (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户档案表';

-- ---------- 3. 项目经历表 ----------
CREATE TABLE IF NOT EXISTS profile_project (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    profile_id  BIGINT       NOT NULL COMMENT '关联 user_profile',
    project_name VARCHAR(200) NOT NULL COMMENT '项目名称',
    role        VARCHAR(100) NULL COMMENT '个人角色',
    description TEXT         NULL COMMENT '项目描述',
    tech_stack  JSON         NULL COMMENT '技术栈（该项目真实使用的技术）',
    start_date  DATE         NULL COMMENT '开始日期',
    end_date    DATE         NULL COMMENT '结束日期',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_profile_id (profile_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '项目经历表';

-- ---------- 4. 工作/实习经历表 ----------
CREATE TABLE IF NOT EXISTS profile_experience (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    profile_id  BIGINT       NOT NULL COMMENT '关联 user_profile',
    company     VARCHAR(200) NOT NULL COMMENT '公司/单位',
    position    VARCHAR(100) NULL COMMENT '职位',
    description TEXT         NULL COMMENT '职责描述',
    achievements TEXT        NULL COMMENT '成果',
    start_date  DATE         NULL COMMENT '开始日期',
    end_date    DATE         NULL COMMENT '结束日期',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_profile_id (profile_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工作实习经历表';

-- ---------- 5. 经历证据表（证据链核心） ----------
CREATE TABLE IF NOT EXISTS profile_evidence (
    id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id             BIGINT       NOT NULL COMMENT '归属用户',
    entity_type         VARCHAR(30)  NOT NULL COMMENT '实体类型：PROFILE/SKILL/PROJECT/EXPERIENCE',
    entity_id           BIGINT       NULL COMMENT '实体记录 ID',
    field_name          VARCHAR(100) NOT NULL COMMENT '对应档案字段',
    source_type         VARCHAR(30)  NOT NULL COMMENT '来源类型：CHAT/MANUAL',
    source_id           BIGINT       NULL COMMENT '原始来源记录 ID',
    evidence_content    TEXT         NOT NULL COMMENT '用户原始表述',
    confirmation_status VARCHAR(30)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/CONFIRMED/REJECTED',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id),
    KEY idx_entity (entity_type, entity_id),
    KEY idx_confirmation_status (confirmation_status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '经历证据表';

-- ---------- 6. 对话会话表 ----------
CREATE TABLE IF NOT EXISTS chat_session (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id    BIGINT       NOT NULL COMMENT '归属用户',
    title      VARCHAR(200) NULL COMMENT '会话名称',
    status     VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/CLOSED',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '对话会话表';

-- ---------- 7. 对话消息表 ----------
CREATE TABLE IF NOT EXISTS chat_message (
    id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    session_id BIGINT      NOT NULL COMMENT '关联会话',
    role       VARCHAR(20) NOT NULL COMMENT 'USER/ASSISTANT',
    content    TEXT        NOT NULL COMMENT '消息内容',
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '消息时间',
    PRIMARY KEY (id),
    KEY idx_session_created (session_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '对话消息表';

-- ---------- 8. 简历主体表 ----------
CREATE TABLE IF NOT EXISTS resume (
    id                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id            BIGINT       NOT NULL COMMENT '归属用户',
    resume_name        VARCHAR(100) NULL COMMENT '简历名称',
    current_version_id BIGINT       NULL COMMENT '当前版本 ID',
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '简历主体表';

-- ---------- 9. 简历版本表（永不覆盖） ----------
CREATE TABLE IF NOT EXISTS resume_version (
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    resume_id         BIGINT       NOT NULL COMMENT '关联简历主体',
    version_number    INT          NOT NULL COMMENT '版本号（同一 resume 内递增）',
    content           JSON         NOT NULL COMMENT '结构化简历内容',
    generation_reason VARCHAR(200) NULL COMMENT '生成原因',
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_resume_version (resume_id, version_number),
    KEY idx_resume_id (resume_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '简历版本表';

-- ---------- 10. 岗位表 ----------
CREATE TABLE IF NOT EXISTS job (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    company      VARCHAR(200) NULL COMMENT '公司',
    position     VARCHAR(200) NULL COMMENT '岗位名称',
    city         VARCHAR(100) NULL COMMENT '工作城市',
    salary       VARCHAR(100) NULL COMMENT '薪资（文本）',
    jd_content   TEXT         NULL COMMENT '原始 JD 全文',
    source_type  VARCHAR(30)  NOT NULL DEFAULT 'MOCK' COMMENT 'MOCK/MANUAL',
    external_url VARCHAR(500) NULL COMMENT '外部招聘页面跳转链接',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_city (city),
    KEY idx_position (position)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '岗位表';

-- ---------- 11. 岗位技能要求表 ----------
CREATE TABLE IF NOT EXISTS job_requirement (
    id         BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    job_id     BIGINT        NOT NULL COMMENT '关联岗位',
    skill      VARCHAR(100)  NOT NULL COMMENT '技能名',
    importance VARCHAR(30)   NOT NULL DEFAULT 'REQUIRED' COMMENT 'REQUIRED/PREFERRED',
    weight     DECIMAL(6, 2) NOT NULL DEFAULT 0.00 COMMENT '权重',
    PRIMARY KEY (id),
    KEY idx_job_id (job_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '岗位技能要求表';

-- ---------- 12. 匹配结果表（关联简历版本） ----------
CREATE TABLE IF NOT EXISTS match_result (
    id                BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    resume_version_id BIGINT        NOT NULL COMMENT '关联简历版本',
    job_id            BIGINT        NOT NULL COMMENT '关联岗位',
    match_score       DECIMAL(5, 2) NULL COMMENT '匹配分数（Java 加权计算）',
    analysis_json     JSON          NULL COMMENT 'LLM 原始分析输出备查',
    created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_resume_version_id (resume_version_id),
    KEY idx_job_id (job_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '匹配结果表';

-- ---------- 13. 匹配详情表 ----------
CREATE TABLE IF NOT EXISTS match_detail (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    match_result_id BIGINT       NOT NULL COMMENT '关联匹配结果',
    skill           VARCHAR(100) NOT NULL COMMENT '技能',
    match_status    VARCHAR(30)  NOT NULL COMMENT 'MATCHED/PARTIAL/MISSING/UNCONFIRMED',
    evidence        TEXT         NULL COMMENT '简历中的证据',
    suggestion      TEXT         NULL COMMENT '优化建议',
    PRIMARY KEY (id),
    KEY idx_match_result_id (match_result_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '匹配详情表';

-- ---------- 14. 反向优化任务表 ----------
CREATE TABLE IF NOT EXISTS optimization_task (
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    job_id            BIGINT       NULL COMMENT '目标岗位',
    resume_version_id BIGINT       NULL COMMENT '当时的简历版本',
    chat_session_id   BIGINT       NULL COMMENT '追问复用对话会话',
    target_skill      VARCHAR(100) NULL COMMENT '待确认技能',
    question          TEXT         NULL COMMENT '追问内容',
    status            VARCHAR(30)  NULL COMMENT '任务状态机',
    source            VARCHAR(30)  NULL COMMENT 'JD_GAP_ANALYSIS',
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_chat_session_id (chat_session_id),
    KEY idx_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '反向优化任务表';

-- ---------- 15. 投递记录表（关联简历版本） ----------
CREATE TABLE IF NOT EXISTS job_application (
    id                 BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id            BIGINT      NOT NULL COMMENT '归属用户',
    job_id             BIGINT      NOT NULL COMMENT '关联岗位',
    resume_version_id  BIGINT      NOT NULL COMMENT '投递时使用的简历版本',
    status             VARCHAR(30) NOT NULL DEFAULT 'PENDING' COMMENT '投递状态',
    applied_at         DATETIME    NULL COMMENT '投递时间',
    next_follow_up_at  DATETIME    NULL COMMENT '下次跟进提醒时间',
    last_follow_up_at  DATETIME    NULL COMMENT '最近一次已跟进时间',
    updated_at         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最近更新',
    PRIMARY KEY (id),
    KEY idx_user_status (user_id, status),
    KEY idx_next_follow_up_at (next_follow_up_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '投递记录表';

-- ---------- 16. 状态历史表 ----------
CREATE TABLE IF NOT EXISTS application_history (
    id             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    application_id BIGINT      NOT NULL COMMENT '关联投递',
    old_status     VARCHAR(30) NULL COMMENT '原状态',
    new_status     VARCHAR(30) NULL COMMENT '新状态',
    note           TEXT        NULL COMMENT '备注（状态类反馈）',
    created_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '变化时间',
    PRIMARY KEY (id),
    KEY idx_application_created (application_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '投递状态历史表';

-- ---------- 17. 用户反馈表 ----------
CREATE TABLE IF NOT EXISTS user_feedback (
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id           BIGINT       NOT NULL COMMENT '归属用户',
    job_id            BIGINT       NOT NULL COMMENT '反馈的岗位',
    feedback_type     VARCHAR(30)  NOT NULL COMMENT 'NOT_INTERESTED/LOCATION_MISMATCH/SALARY_MISMATCH/SKILL_ALREADY_HAVE',
    feedback_content  VARCHAR(500) NULL COMMENT '补充说明',
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id),
    KEY idx_job_id (job_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户反馈表';
