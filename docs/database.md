# 数据库设计：CareerFlow-AI 智能求职助手

> 版本：v1.1（2026-11-08）｜已按第二轮架构审查修订
> 数据库名：ai_resume（建表 SQL 由成员 B 在 D2 按本文件编写 database/init.sql，进入 Git）

## 1. 设计总则

- 引擎 InnoDB，字符集 utf8mb4
- 主键 BIGINT 自增（初期不引入分布式 ID）
- 时间 DATETIME；日期 DATE
- **数据库字段 snake_case，API 参数 camelCase**（映射约定见 api.md §13）
- 每张业务表都有 created_at；需要更新的表加 updated_at
- 所有查询按 user_id 做数据隔离（隔离规则见 §8）
- **本设计为 17 张表**：Chat 2 的 15 张 + profile_experience（赛题要求工作/实习经历）+ user_feedback（二轮审查新增，偏好反馈回流）
- 不建的表：profile_field（完整度规则硬编码）、reminder（提醒由 job_application 时间字段计算）、profile_education（MVP 教育经历暂存 user_profile 单条，P1 再拆）

## 2. ER 关系图

```mermaid
erDiagram
    sys_user ||--o| user_profile : "1:1 档案"
    user_profile ||--o{ profile_project : "1:N 项目经历"
    user_profile ||--o{ profile_experience : "1:N 工作实习"
    sys_user ||--o{ profile_evidence : "1:N 证据"
    sys_user ||--o{ chat_session : "1:N 会话"
    chat_session ||--o{ chat_message : "1:N 消息"
    sys_user ||--o{ resume : "1:N 简历"
    resume ||--o{ resume_version : "1:N 版本"
    resume ||--o| resume_version : "current_version_id 当前版本"
    job ||--o{ job_requirement : "1:N 技能要求"
    resume_version ||--o{ match_result : "1:N 匹配(按版本)"
    job ||--o{ match_result : "1:N 匹配"
    match_result ||--o{ match_detail : "1:N 逐技能详情"
    job ||--o{ optimization_task : "1:N 优化任务"
    resume_version ||--o{ optimization_task : "1:N"
    chat_session ||--o{ optimization_task : "1:N 追问复用对话"
    sys_user ||--o{ job_application : "1:N 投递"
    job ||--o{ job_application : "1:N"
    resume_version ||--o{ job_application : "1:N 投递关联版本"
    job_application ||--o{ application_history : "1:N 状态历史"
    sys_user ||--o{ user_feedback : "1:N 岗位反馈"
    job ||--o{ user_feedback : "1:N"
```

## 3. 完整字段定义

### 3.1 用户与档案

**sys_user 用户表**

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK 自增 | |
| username | VARCHAR(50) | UNIQUE NOT NULL | 登录名 |
| password_hash | VARCHAR(255) | NOT NULL | BCrypt 加密，**禁止存明文** |
| email | VARCHAR(100) | | 邮箱（MVP 不做验证码） |
| created_at | DATETIME | NOT NULL | |

**user_profile 用户档案表**（MVP 教育经历暂不拆表，P1 拆 profile_education）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK 自增 | |
| user_id | BIGINT | UNIQUE NOT NULL | 关联 sys_user |
| real_name | VARCHAR(50) | | 姓名 |
| phone | VARCHAR(30) | | 电话 |
| school | VARCHAR(100) | | 学校 |
| major | VARCHAR(100) | | 专业 |
| degree | VARCHAR(50) | | 学历 |
| graduation_year | INT | | 毕业年份 |
| skills | JSON | | 技能列表（如 ["Java","MySQL"]） |
| target_position | VARCHAR(100) | | 目标岗位（求职意向） |
| target_city | VARCHAR(100) | | 意向城市 |
| expected_salary | VARCHAR(50) | | 期望薪资（文本，如 "8k-12k"） |
| created_at / updated_at | DATETIME | | |

### 3.2 经历与证据

**profile_project 项目经历表**

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK 自增 | |
| profile_id | BIGINT | NOT NULL, INDEX | 关联 user_profile |
| project_name | VARCHAR(200) | NOT NULL | 项目名称 |
| role | VARCHAR(100) | | 个人角色 |
| description | TEXT | | 项目描述 |
| tech_stack | JSON | | 技术栈（该项目**真实使用**的技术） |
| start_date / end_date | DATE | | 起止时间 |
| created_at / updated_at | DATETIME | | |

**profile_experience 工作/实习经历表**（赛题明确要求收集工作/实习经历）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK 自增 | |
| profile_id | BIGINT | NOT NULL, INDEX | 关联 user_profile |
| company | VARCHAR(200) | NOT NULL | 公司/单位 |
| position | VARCHAR(100) | | 职位 |
| description | TEXT | | 职责描述 |
| achievements | TEXT | | 成果 |
| start_date / end_date | DATE | | 起止时间 |
| created_at / updated_at | DATETIME | | |

**profile_evidence 经历证据表**（证据链核心，二轮审查修订：增加实体关联）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK 自增 | |
| user_id | BIGINT | NOT NULL, INDEX | 归属用户 |
| entity_type | VARCHAR(30) | NOT NULL | ⭐ 新增：证据对应的实体类型 |
| entity_id | BIGINT | | ⭐ 新增：实体记录 ID |
| field_name | VARCHAR(100) | NOT NULL | 对应档案字段（如 skills / project.tech_stack） |
| source_type | VARCHAR(30) | NOT NULL | 原始来源类型：CHAT / MANUAL |
| source_id | BIGINT | | 原始来源记录 ID（如 chat_message.id） |
| evidence_content | TEXT | NOT NULL | 用户原始表述 |
| confirmation_status | VARCHAR(30) | NOT NULL | PENDING（待确认）/ CONFIRMED（已确认）/ REJECTED（已拒绝） |
| created_at / updated_at | DATETIME | | |

**entity_type 取值与 entity_id 语义：**

| entity_type | entity_id 指向 | 用途 |
|---|---|---|
| PROFILE | user_profile.id | 基础信息、教育经历、求职意向等档案字段 |
| SKILL | user_profile.id（field_name=skills 定位） | 技能（技能存 JSON 无独立表） |
| PROJECT | profile_project.id | 项目经历（未创建实体前为 NULL，确认时补填） |
| EXPERIENCE | profile_experience.id | 工作/实习经历（同上） |

> ⚠️ 多态关联无法用普通外键保证完整性，**后端必须校验证据与实体的归属**（user_id 一致）。
> ⚠️ 未确认的抽取数据**只存在于 profile_evidence（PENDING）**，绝不进入 profile 表；用户确认后才落实体表。

### 3.3 对话

**chat_session 对话会话表**

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| user_id | BIGINT NOT NULL, INDEX | 归属用户 |
| title | VARCHAR(200) | 会话名称 |
| status | VARCHAR(30) | ACTIVE / CLOSED |
| created_at / updated_at | DATETIME | |

**chat_message 对话消息表**

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| session_id | BIGINT NOT NULL, INDEX | 关联会话 |
| role | VARCHAR(20) NOT NULL | USER / ASSISTANT |
| content | TEXT NOT NULL | 消息内容 |
| created_at | DATETIME NOT NULL | |

### 3.4 简历

**resume 简历主体表**（二轮审查修订：增加当前版本指针）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| user_id | BIGINT NOT NULL, INDEX | 归属用户 |
| resume_name | VARCHAR(100) | 简历名称 |
| current_version_id | BIGINT | ⭐ 新增：当前版本 ID，NULL 表示尚无版本。省去每次按版本号排序查询 |
| created_at / updated_at | DATETIME | |

**resume_version 简历版本表**（⭐ 永不覆盖；二轮审查修订：并发约束）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| resume_id | BIGINT NOT NULL, INDEX | 关联简历主体 |
| version_number | INT NOT NULL | 版本号（同一 resume 内递增） |
| content | JSON NOT NULL | 结构化简历内容（personalInfo/education/skills/projects/experiences/selfEvaluation） |
| generation_reason | VARCHAR(200) | 生成原因（如 初次生成 / 岗位定向 / 补充SpringBoot练习） |
| created_at | DATETIME NOT NULL | |

- **UNIQUE(resume_id, version_number)**：防两个并发请求生成同一个版本号
- **生成新版本必须在一个事务中完成**：① `SELECT … FOR UPDATE` 锁定 resume 主记录 ② 取 max(version_number)+1 ③ INSERT 新版本 ④ UPDATE resume.current_version_id ⑤ 提交。旧版本禁止修改。

### 3.5 岗位与匹配

**job 岗位表**

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| company | VARCHAR(200) | 公司 |
| position | VARCHAR(200) | 岗位名称 |
| city | VARCHAR(100) | 工作城市 |
| salary | VARCHAR(100) | 薪资（文本，如 "6k-10k"；精确数值过滤是已知限制，见 decision-record） |
| jd_content | TEXT | 原始 JD 全文 |
| source_type | VARCHAR(30) | MOCK（内置模拟）/ MANUAL（手动录入） |
| external_url | VARCHAR(500) | 外部招聘页面跳转链接（可空） |
| created_at / updated_at | DATETIME | |

**job_requirement 岗位技能要求表**

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| job_id | BIGINT NOT NULL, INDEX | 关联岗位 |
| skill | VARCHAR(100) NOT NULL | 技能名（由 JD 解析生成，人工可改） |
| importance | VARCHAR(30) NOT NULL | REQUIRED（必备）/ PREFERRED（加分） |
| weight | DECIMAL(6,2) NOT NULL | 权重（用于匹配评分） |

**match_result 匹配结果表**（⭐ 关联简历版本）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| resume_version_id | BIGINT NOT NULL, INDEX | 关联简历版本（不是 resume_id，保证匹配有版本依据） |
| job_id | BIGINT NOT NULL, INDEX | 关联岗位 |
| match_score | DECIMAL(5,2) | 匹配分数（Java 加权计算） |
| analysis_json | JSON | LLM 原始分析输出备查（结构化结果一律在 match_detail） |
| created_at | DATETIME | |

**match_detail 匹配详情表**（每项技能一条，可解释性核心）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| match_result_id | BIGINT NOT NULL, INDEX | 关联匹配结果 |
| skill | VARCHAR(100) NOT NULL | 技能 |
| match_status | VARCHAR(30) NOT NULL | MATCHED / PARTIAL / MISSING / UNCONFIRMED |
| evidence | TEXT | 简历中的证据（缺失则为 null） |
| suggestion | TEXT | 优化建议 |

### 3.6 优化、投递与历史

**optimization_task 反向优化任务表**

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| job_id | BIGINT | 目标岗位 |
| resume_version_id | BIGINT | 当时的简历版本 |
| chat_session_id | BIGINT | 追问复用对话页，关联会话 |
| target_skill | VARCHAR(100) | 待确认技能（如 SpringBoot） |
| question | TEXT | 追问内容 |
| status | VARCHAR(30) | WAITING_USER → ANSWER_RECEIVED → LLM_EXTRACTION → USER_CONFIRMATION → PROFILE_UPDATED → RESUME_REGENERATED |
| source | VARCHAR(30) | JD_GAP_ANALYSIS |
| created_at / updated_at | DATETIME | |

**job_application 投递记录表**（⭐ 关联简历版本；二轮审查修订：增加提醒字段）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| user_id | BIGINT NOT NULL, INDEX | 归属用户 |
| job_id | BIGINT NOT NULL, INDEX | 关联岗位 |
| resume_version_id | BIGINT NOT NULL, INDEX | 投递时使用的简历版本 |
| status | VARCHAR(30) NOT NULL | 见状态机 |
| applied_at | DATETIME | 投递时间 |
| next_follow_up_at | DATETIME NULL | ⭐ 新增：下次跟进提醒时间（NULL = 用默认规则 applied/updated+3 天） |
| last_follow_up_at | DATETIME NULL | ⭐ 新增：最近一次已跟进时间 |
| updated_at | DATETIME | 最近更新（提醒计算兜底） |

**application_history 状态历史表**

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| application_id | BIGINT NOT NULL, INDEX | 关联投递 |
| old_status / new_status | VARCHAR(30) | 状态变化 |
| note | TEXT | 状态变化备注（「HR 通知下周面试」等**状态类**反馈由此承载） |
| created_at | DATETIME | |

### 3.7 用户反馈（二轮审查新增）

**user_feedback 用户反馈表**（偏好类反馈，与状态备注区分；回流到推荐排序）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| user_id | BIGINT NOT NULL, INDEX | 归属用户 |
| job_id | BIGINT NOT NULL, INDEX | 反馈的岗位 |
| feedback_type | VARCHAR(30) NOT NULL | 反馈类型（见下） |
| feedback_content | VARCHAR(500) | 补充说明（可空） |
| created_at | DATETIME | |

**feedback_type 枚举与推荐回流规则（可解释规则权重，不引入机器学习）：**

| 类型 | 含义 | 推荐排序影响 |
|---|---|---|
| NOT_INTERESTED | 对该岗位不感兴趣 | 同 position 或同 company 的岗位排序系数 ×0.5（后置，不删除） |
| LOCATION_MISMATCH | 地点不合适 | 该 city 的岗位后置 |
| SALARY_MISMATCH | 薪资不合适 | 薪资低于期望的岗位后置（salary 为文本，按关键字粗略判断，见 decision-record 已知限制） |
| SKILL_ALREADY_HAVE | 技能已掌握但简历未体现 | 不影响排序；提示用户补充该技能证据（可发起反向优化任务） |

> 区分两种反馈：**状态类反馈**（已读、面试邀请等）走 PUT /api/applications/{id}/status 的 note，存 application_history；**偏好类反馈**（不感兴趣、地点薪资不符）走 POST /api/feedback，存 user_feedback。两者用途不同，不混用。

## 4. 投递状态机（二轮审查确认版）

枚举 `ApplicationStatus`：PENDING, APPLIED, READ, INTERVIEW_INVITED, INTERVIEWING, OFFERED, REJECTED, WITHDRAWN, FAILED, NO_RESPONSE

**合法转移表：**

| 当前状态 | 可转移到 |
|---|---|
| PENDING | APPLIED、WITHDRAWN（未投先撤） |
| APPLIED | READ、FAILED、WITHDRAWN、NO_RESPONSE |
| READ | INTERVIEW_INVITED、NO_RESPONSE |
| INTERVIEW_INVITED | INTERVIEWING、NO_RESPONSE |
| INTERVIEWING | OFFERED、REJECTED |
| NO_RESPONSE | READ、INTERVIEW_INVITED、REJECTED（恢复跟进） |
| OFFERED / REJECTED / WITHDRAWN / FAILED | 终态，不再流转 |

- NO_RESPONSE（长期无反馈）：MVP 由用户手动标记（不引入定时任务）；P1 加 @Scheduled 自动标记
- 每次流转**一个事务**完成：①查原状态 ②校验转移合法（不合法 → 409）③更新 job_application ④插入 application_history ⑤**重置提醒计时**（last_follow_up_at=now，next_follow_up_at=now+3 天）
- 状态只能由后端变更，前端不能任意改

## 5. 待跟进提醒规则（二轮审查确认版）

**触发条件**（GET /api/applications/follow-ups 的查询规则，Java 计算）：

```
status ∈ {APPLIED, READ, INTERVIEW_INVITED, NO_RESPONSE}
且 due ≤ now
其中 due = next_follow_up_at != NULL ? next_follow_up_at : (updated_at + 3 天)
```

**用户处理流程**（POST /api/applications/{id}/follow-up）：

1. 用户点击「已跟进」，可指定下次提醒时间
2. 后端更新 last_follow_up_at = now、next_follow_up_at = 用户指定时间（不指定默认 now+3 天）
3. 该记录从待跟进列表消失，到 next_follow_up_at 后重新出现 → **不会重复弹出已处理的提醒**
4. 状态更新（PUT status）自动重置计时（见状态机 ⑤）

不建 reminder 表，全部由 job_application 的两个时间字段计算。

## 6. 档案完整度计算规则（Java 硬编码）

6 个维度，每维度按「已满足必填项 / 必填项总数」计分，总分取平均：

| 维度 | 必填项 | 说明 |
|---|---|---|
| 基础信息 | real_name、phone | 2 项 |
| 教育经历 | school、major、degree、graduation_year | 4 项 |
| 技能信息 | skills 非空且 ≥3 条（且至少 1 条 CONFIRMED 证据） | 逻辑判断 |
| 项目经历 | ≥1 条 profile_project，且含 project_name、description、tech_stack | 逻辑判断 |
| 工作/实习经历 | ≥1 条 profile_experience，**或用户在对话中明确表示「无」**（记证据） | 学生无经历不强制 |
| 求职意向 | target_position、target_city | expected_salary 计半分 |

前端以雷达图展示（亮点一），补充信息后自动更新。

## 7. 索引与唯一约束清单

| 表 | 约束/索引 |
|---|---|
| sys_user | UNIQUE(username) |
| user_profile | UNIQUE(user_id)；INDEX(user_id) |
| profile_project | INDEX(profile_id) |
| profile_experience | INDEX(profile_id) |
| profile_evidence | INDEX(user_id)、INDEX(entity_type, entity_id)、INDEX(confirmation_status) |
| chat_session | INDEX(user_id) |
| chat_message | INDEX(session_id, created_at) |
| resume | INDEX(user_id) |
| resume_version | **UNIQUE(resume_id, version_number)**；INDEX(resume_id) |
| job | INDEX(city)、INDEX(position) |
| job_requirement | INDEX(job_id) |
| match_result | INDEX(resume_version_id)、INDEX(job_id) |
| match_detail | INDEX(match_result_id) |
| optimization_task | INDEX(chat_session_id)、INDEX(status) |
| job_application | INDEX(user_id, status)、INDEX(next_follow_up_at) |
| application_history | INDEX(application_id, created_at) |
| user_feedback | INDEX(user_id)、INDEX(job_id) |

## 8. 数据隔离规则（所有接口必须校验）

| 表 | 归属判定链 |
|---|---|
| user_profile / profile_project / profile_experience | 经 profile_id → user_profile.user_id 校验 |
| profile_evidence | 直接 user_id 校验（实体表同校验） |
| chat_session | user_id 校验 |
| chat_message | 经 session_id → chat_session.user_id 校验 |
| resume / resume_version | resume 经 user_id；version 经 resume_id → resume.user_id |
| job / job_requirement | 公共资源，读不隔离；写仅管理员场景（MVP 不开放） |
| match_result / match_detail | 经 resume_version_id → resume.user_id |
| optimization_task | 经 chat_session_id → chat_session.user_id |
| job_application | user_id 校验；resume_version/job 存在性校验 |
| application_history | 经 application_id → job_application.user_id |
| user_feedback | user_id 校验 |

**禁止仅凭前端传来的 ID 查询或修改任何数据。**

## 9. 数据库铁律（写进 CLAUDE.md）

1. 每张业务表都有 created_at，需要更新的表加 updated_at
2. 用户数据必须通过 user_id 隔离，禁止仅凭前端传来的 ID 查询他人数据
3. 所有简历版本保留，禁止覆盖旧版本；生成版本必须走锁定事务 + 唯一约束
4. 投递记录与匹配结果必须关联具体的简历版本
5. 用户原始经历与 AI 优化后的文案分开保存（原始在 profile_* 与 profile_evidence，优化文案在 resume_version.content，证据可追溯）
6. 未确认的抽取数据只存在于 profile_evidence（PENDING），确认后才落实体表
