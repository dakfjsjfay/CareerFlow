# CareerFlow-AI 2.0 数据库增量设计

> 审计日期：2026-11-14
> 基线：`database/init.sql`（v1.1，17 张表）、`docs/database.md`
> 原则：只做增量，不重建、不删除、不重命名旧表；沿用“逻辑外键 + 索引、无物理 FOREIGN KEY”的现有约定。
> 说明：本文所有 DDL 为设计稿，本轮不执行；实施时统一放入 `database/migration/` 并同步 `docs/database.md`。

## 1. 设计总则

1. **旧表只加列，不改语义**：`resume_version` 继续不可变，`job_application` 继续绑定 `resume_version_id`。
2. **新表必须带用户归属**：`user_id` 或经父表可追溯，且所有查询必须按当前用户过滤。
3. **不建物理外键**：与现有 17 张表保持一致，靠索引 + Service 校验保证一致性。
4. **JSON 优先**：结构化内容继续用 JSON（`resume_version.content`、`resume_draft.content`），避免过早拆表。
5. **字符集统一**：`utf8mb4` / `utf8mb4_unicode_ci` / `InnoDB`。
6. **迁移可重复执行**：统一使用 `CREATE TABLE IF NOT EXISTS` 与“先查 `information_schema` 再 `ALTER`”的幂等写法。
7. **旧数据不丢**：新增列一律 `NULL` 或带默认值，禁止 `NOT NULL` 无默认导致迁移失败。

## 2. 候选表逐项评估

| 候选表 | 结论 | 理由 |
|---|---|---|
| `resume_section` | **第一阶段不建** | `resume_version.content` 已是结构化 JSON，再拆 `resume_section` 会产生双写与一致性问题；区块分析可无状态调用，区块修改用 `resume_edit_history` 记录前后值即可 |
| `resume_edit_history` | **需要** | 2.0 要求 AI 修改必须前后对比、用户确认后才能应用；没有历史表无法审计、回滚和展示 diff |
| `resume_template` | **第一阶段不建** | 模板资产在前端 `src/components/resume-template/`，后端只存 `content.meta.templateId`；等需要后台管理模板时再建 |
| `document_asset` | **需要** | 2.0 要保留原始 DOCX、JD 截图、证件照；当前上传文件不落盘，无法复用与鉴权 |
| `job_parse_task` | **第一阶段不建** | 第一阶段 JD 截图走同步 OCR + 解析，耗时可接受；等需要批量/异步解析再建任务表 |
| `company` | **需要** | 200+ 企业信息库无法用 `job.company` 文本承载；需要官网、招聘官网、行业、核验时间等字段 |
| `company_recruitment` | **需要** | 秋招/春招/实习招聘信息是“一对多”关系，必须独立表并记录来源与核验时间 |
| `resume_draft` | **需要（已确认）** | 八步创建需要自动保存与草稿恢复；草稿可覆盖，正式版本不可覆盖，二者必须分离 |

## 3. 新增表设计

### 3.1 `resume_draft`（八步创建草稿）

定位：可反复覆盖的编辑态，与不可变的 `resume_version` 分离。完成第八步后，用户显式“生成简历”才写入 `resume_version`。

```sql
CREATE TABLE IF NOT EXISTS resume_draft (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id      BIGINT       NOT NULL COMMENT '归属用户，关联 sys_user',
    resume_id    BIGINT       NULL COMMENT '关联 resume；新建草稿可为空，首次生成后回填',
    draft_name   VARCHAR(100) NULL COMMENT '草稿名称',
    current_step TINYINT      NOT NULL DEFAULT 1 COMMENT '当前步骤 1-8',
    content      JSON         NOT NULL COMMENT '八步表单完整内容快照',
    revision     INT          NOT NULL DEFAULT 1 COMMENT '乐观锁版本，每次保存 +1',
    status       VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/COMPLETED/ABANDONED',
    last_saved_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近自动保存时间',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_user_status (user_id, status),
    KEY idx_resume_id (resume_id),
    KEY idx_last_saved_at (last_saved_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '简历八步创建草稿表';
```

设计说明：

- 用户归属：`user_id` 直接归属，所有读写必须 `user_id = 当前用户`。
- 与旧表关系：`resume_id` 逻辑关联 `resume.id`；草稿完成生成版本后回填，不修改 `resume_version`。
- 乐观锁：`revision` 防止多标签页覆盖；保存时 `WHERE revision = ?`，冲突返回 409。
- 索引理由：`(user_id, status)` 支撑“我的草稿/最近草稿”；`last_saved_at` 支撑草稿列表排序。
- 不建唯一索引：允许同一用户对同一简历保留多个草稿（例如针对不同岗位）。

### 3.2 `resume_edit_history`（区块级修改历史）

定位：记录“AI 或用户”对某个区块的修改建议、前后内容、确认状态，是前后对比与审计的唯一来源。

```sql
CREATE TABLE IF NOT EXISTS resume_edit_history (
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id          BIGINT       NOT NULL COMMENT '归属用户',
    resume_id        BIGINT       NULL COMMENT '关联 resume',
    draft_id         BIGINT       NULL COMMENT '关联 resume_draft；草稿态修改时填写',
    base_version_id  BIGINT       NULL COMMENT '基于哪个正式版本修改',
    section_key      VARCHAR(50)  NOT NULL COMMENT '区块：BASIC/EDUCATION/PROJECT/SKILL/EXPERIENCE/SELF_EVALUATION/SOFT/PHOTO',
    before_content   JSON         NULL COMMENT '修改前内容',
    after_content    JSON         NULL COMMENT '修改后候选内容',
    source           VARCHAR(20)  NOT NULL COMMENT 'AI/MANUAL',
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPLIED/REJECTED',
    prompt_summary   VARCHAR(500) NULL COMMENT '本次修改意图摘要',
    model            VARCHAR(50)  NULL COMMENT '使用的模型，便于追溯',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    applied_at       DATETIME     NULL COMMENT '用户确认应用时间',
    PRIMARY KEY (id),
    KEY idx_user_resume (user_id, resume_id),
    KEY idx_draft_status (draft_id, status),
    KEY idx_base_version (base_version_id),
    KEY idx_created_at (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '简历区块修改历史表';
```

设计说明：

- 用户归属：`user_id` 直接归属，查询必须带 `user_id`。
- 与旧表关系：`base_version_id` 逻辑关联 `resume_version.id`；`draft_id` 逻辑关联 `resume_draft.id`。
- 一个区块一次建议一行，避免把整份简历反复存大 JSON。
- `status=PENDING` 表示候选未应用；只有 `APPLIED` 才允许写入草稿或生成新版本。
- 不建物理外键；删除草稿时保留历史（`draft_id` 可为悬空），便于审计。

### 3.3 `document_asset`（文件素材）

定位：统一管理原始简历 DOCX、JD 截图、证件照等文件，提供鉴权下载与复用。

```sql
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
```

设计说明：

- 存储：本地磁盘目录（如 `storage/assets/{userId}/{yyyyMM}/`），数据库只存相对路径；下载必须经后端鉴权接口。
- 用户归属：`user_id` 直接归属；任何下载/预览先校验归属再读文件。
- 去重：`sha256` 非唯一索引，允许同一用户重复上传，但可用于提示“已存在”。
- 与旧表关系：`resume.source_document_asset_id`（新增列）逻辑关联；`resume_draft.content.photoAssetId` 也可引用。
- 软删除：`status=DELETED`，不物理删除文件，避免历史版本导出失败。

### 3.4 `company`（企业信息库）

定位：200+ 企业基础信息，支持行业筛选、搜索、官网与招聘官网跳转。

```sql
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
```

设计说明：

- 这是**公共数据**，无 `user_id`；读接口登录后可用，写接口仅管理员。
- `uk_name` 防止重复企业；导入时按 `name` 做 upsert。
- `source` + `source_url` + `last_verified_at` 满足“来源和最后核验时间”要求。
- 与旧表关系：`job.company_id`（新增列）逻辑关联；旧 `job.company` 文本保留不动，作为回退与展示兜底。

### 3.5 `company_recruitment`（企业招聘信息）

定位：一家企业多条招聘信息（秋招/春招/实习/社招），记录投递入口、时间窗与状态。

```sql
CREATE TABLE IF NOT EXISTS company_recruitment (
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    company_id        BIGINT       NOT NULL COMMENT '关联 company',
    title             VARCHAR(200) NOT NULL COMMENT '招聘项目名称',
    recruitment_type  VARCHAR(30)  NOT NULL COMMENT 'AUTUMN/SPRING/INTERNSHIP/SOCIAL',
    target_graduates  VARCHAR(100) NULL COMMENT '面向届别，如 2027 届',
    city              VARCHAR(200) NULL COMMENT '工作城市，可多值文本',
    start_at          DATETIME     NULL COMMENT '开始时间',
    end_at            DATETIME     NULL COMMENT '截止时间',
    apply_url         VARCHAR(500) NULL COMMENT '投递入口',
    status            VARCHAR(20)  NOT NULL DEFAULT 'UNKNOWN' COMMENT 'OPEN/CLOSED/UPCOMING/UNKNOWN',
    source            VARCHAR(50)  NOT NULL DEFAULT 'SEED' COMMENT 'SEED/ADMIN_IMPORT/MANUAL',
    source_url        VARCHAR(500) NULL COMMENT '信息来源链接',
    last_verified_at  DATETIME     NULL COMMENT '最后核验时间',
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_company (company_id),
    KEY idx_type_status (recruitment_type, status),
    KEY idx_end_at (end_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '企业招聘信息表';
```

设计说明：

- 公共数据，无 `user_id`；写接口仅管理员。
- `status` 允许人工维护，避免依赖不可控的外部抓取。
- 与旧表关系：可选地把用户投递与招聘信息关联，但第一阶段不强制；`job_application` 已有 `job_id` 足够。

## 4. 旧表增量变更

以下全部为 `ALTER TABLE ... ADD COLUMN`，不动旧列、不改旧索引。

### 4.1 `sys_user` 增加角色

```sql
ALTER TABLE sys_user
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT 'USER/ADMIN' AFTER password_hash,
    ADD KEY idx_role (role);
```

- 默认 `USER`，不影响存量数据。
- 管理员初始化建议：单独 SQL 脚本把指定用户名提升为 `ADMIN`，不写死在业务代码。

### 4.2 `job` 关联企业与可见性

```sql
ALTER TABLE job
    ADD COLUMN company_id BIGINT NULL COMMENT '关联 company' AFTER company,
    ADD COLUMN created_by BIGINT NULL COMMENT '创建者用户 ID；系统数据为空' AFTER source_type,
    ADD COLUMN visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLIC' COMMENT 'PUBLIC/PRIVATE' AFTER created_by,
    ADD KEY idx_company_id (company_id),
    ADD KEY idx_created_by (created_by),
    ADD KEY idx_visibility (visibility);
```

- 旧数据 `company_id` 为空、`created_by` 为空、`visibility=PUBLIC`，行为与现在一致。
- 2.0 起：用户手动新增岗位写 `created_by` + `PRIVATE`；管理员/种子数据为 `PUBLIC`。
- 查询规则：`visibility='PUBLIC' OR created_by = 当前用户`。

### 4.3 `resume` 关联原始文件

```sql
ALTER TABLE resume
    ADD COLUMN source_document_asset_id BIGINT NULL COMMENT '原始简历文件，关联 document_asset' AFTER current_version_id,
    ADD KEY idx_source_asset (source_document_asset_id);
```

- 用于“保留原 DOCX 模板”的第一阶段：原文件保留 + 新版本 DOCX 导出。
- 不改变 `resume_version` 不可变语义。

### 4.4 `job_application` 增加投递渠道

```sql
ALTER TABLE job_application
    ADD COLUMN channel VARCHAR(30) NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL/OFFICIAL_SITE/JOB_BOARD/REFERRAL' AFTER resume_version_id,
    ADD COLUMN applied_url VARCHAR(500) NULL COMMENT '实际投递页面链接' AFTER channel,
    ADD KEY idx_channel (channel);
```

- 支撑“招聘平台跳转 + 手动记录投递”。
- 不实现自动投递，`channel` 仅记录来源。

### 4.5 `optimization_task` 补用户归属

```sql
ALTER TABLE optimization_task
    ADD COLUMN user_id BIGINT NULL COMMENT '归属用户' AFTER id,
    ADD KEY idx_user_id (user_id);
```

- 存量数据 `NULL`；实施时执行一次性回填：经 `chat_session.user_id` 或 `resume_version → resume.user_id` 回填。
- 新数据必须写 `user_id`，查询必须带 `user_id`。
- 回填 SQL 放在迁移脚本中并记录影响行数。

### 4.6 `profile_evidence` 是否扩展实体类型

- 现状：`entity_type VARCHAR(30)` 已能容纳 `DOCUMENT`、`JD_SKILL` 等新值，**不需要改表**。
- 新增来源类型只需在代码枚举中扩展，并在 `docs/database.md` 补充取值说明。

## 5. 与旧表的完整关系图（逻辑关系）

```text
sys_user 1 ── n resume ── n resume_version
    │            │              │
    │            │              ├── n match_result ── n match_detail
    │            │              ├── n job_application ── n application_history
    │            │              └── n optimization_task
    │            ├── n resume_draft
    │            ├── n resume_edit_history
    │            └── 1 document_asset（source_document_asset_id）
    ├── 1 user_profile ── n profile_project / profile_experience
    ├── n profile_evidence
    ├── n chat_session ── n chat_message
    └── n user_feedback

company 1 ── n company_recruitment
company 1 ── n job ── n job_requirement
```

## 6. 索引与查询场景对照

| 查询场景 | 使用索引 |
|---|---|
| 我的草稿列表 | `resume_draft.idx_user_status` |
| 草稿自动保存冲突检测 | `resume_draft.revision`（主键定位后比较） |
| 某简历的修改历史 | `resume_edit_history.idx_user_resume` |
| 某草稿待确认修改 | `resume_edit_history.idx_draft_status` |
| 我的原始简历文件 | `document_asset.idx_user_type` |
| 企业按行业筛选 | `company.idx_industry` |
| 企业搜索 | `company.uk_name` + `name LIKE`（数据量小，可接受） |
| 招聘信息按类型/状态 | `company_recruitment.idx_type_status` |
| 岗位按企业聚合 | `job.idx_company_id` |
| 用户私有岗位 | `job.idx_created_by` + `idx_visibility` |
| 投递渠道统计 | `job_application.idx_channel` |

## 7. 迁移执行顺序

1. `ALTER sys_user`（角色）。
2. `CREATE company` → `CREATE company_recruitment`。
3. `ALTER job`（企业关联 + 可见性）。
4. `CREATE document_asset`。
5. `ALTER resume`（原始文件关联）。
6. `CREATE resume_draft` → `CREATE resume_edit_history`。
7. `ALTER job_application`（渠道）。
8. `ALTER optimization_task`（用户归属）+ 回填脚本。
9. 种子数据导入：企业 200+、招聘信息若干。
10. 更新 `docs/database.md`，补表结构、索引、状态取值与迁移说明。

## 8. 风险与回滚

| 风险 | 影响 | 缓解 |
|---|---|---|
| `ALTER TABLE` 锁表 | 存量小，风险低 | 低峰执行；MySQL 8 `ALGORITHM=INPLACE` |
| `optimization_task.user_id` 回填遗漏 | 归属查询漏数据 | 回填后校验 `user_id IS NULL` 数量为 0 |
| 企业重名 | 导入失败或数据分裂 | `uk_name` + 导入前去重 |
| 本地文件与数据库不一致 | 下载 404 | 下载接口先查 `document_asset`，缺失返回 404 并标记 `DELETED` |
| 草稿 JSON 结构演进 | 旧草稿无法渲染 | `content` 内加 `schemaVersion`，读取时按版本兼容 |
| 可见性规则切换 | 旧岗位突然不可见 | 旧数据统一 `PUBLIC`，上线前回归 |

## 9. 明确不做的事

- 不重建 `resume_version`，不把区块拆成独立表。
- 不删除 `job.company` 文本列。
- 不新增物理外键。
- 不引入 Redis、消息队列、搜索引擎。
- 不建 `job_parse_task`、`resume_template`（第一阶段）。
- 不做原简历截图的像素级版面重建。

## 10. 待确认

- 管理员账号初始化方式：SQL 脚本 or 环境变量首次启动提升。
- 企业 Logo 与招聘信息是否允许外链图片，还是统一存 `document_asset`。
- `document_asset` 存储目录是否需要按环境配置（本地 `storage/`，部署时挂载卷）。
