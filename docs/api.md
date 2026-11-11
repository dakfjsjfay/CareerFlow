# API 接口契约：CareerFlow-AI 智能求职助手

> 版本：v1.1（2026-11-08）｜已按第二轮架构审查修订
> ⚠️ 本文件是前后端协作的唯一依据。**开始开发后不得随意改字段**；必须修改时先改本文件并同步通知前后端。

## 1. 统一约定

| 项 | 约定 |
|---|---|
| 前缀 | 所有接口 /api 开头 |
| 请求格式 | application/json，UTF-8 |
| 认证 | Bearer Token（除 register/login/health 外均需；Spring Security + JWT） |
| URL | 全小写，资源用复数 |
| 参数命名 | **camelCase**（数据库为 snake_case，转换由 MyBatis-Plus 映射） |
| 时间 | ISO 8601（如 2026-11-08T10:00:00+08:00）；日期 yyyy-MM-dd |
| 分页 | 请求 page、size；返回 items、total、page、size |
| 空值 | 明确使用 null 或空数组，不省略字段 |
| AI 输出 | 结构化 JSON，后端结构校验 + 事实校验 |
| 数据归属 | 所有业务接口必须校验当前用户归属（见 database.md §8），不满足 → 403 |

## 2. 统一返回格式

成功：

```json
{ "code": 200, "message": "success", "data": {} }
```

失败：

```json
{ "code": 400, "message": "参数错误", "data": null }
```

**HTTP 状态码与返回体 code 不要混用**，业务错误也应使用正确 HTTP 状态码：

| HTTP | 含义 | 典型场景 |
|---|---|---|
| 200 | 成功 | |
| 400 | 参数错误 | 字段缺失/格式非法/枚举值非法 |
| 401 | 未登录或 Token 失效 | |
| 403 | 无权限 | 访问他人数据、越权操作 |
| 404 | 资源不存在 | 记录不存在 |
| 409 | 业务状态冲突 | 用户名重复、非法状态流转、证据已处理 |
| 500 | 服务端错误 | |
| 502 | AI 服务调用失败 | 超时/重试后仍失败 |

## 3. 健康检查

| 接口 | 方法 | 说明 |
|---|---|---|
| /api/health | GET | 服务状态（免认证） |

## 4. 认证（auth）

| 接口 | 方法 | 说明 | 校验与错误 |
|---|---|---|---|
| /api/auth/register | POST | 注册 | username 3~50 字符必填；password ≥8 字符必填；用户名已存在 → 409 |
| /api/auth/login | POST | 登录 | 用户名或密码错误 → 401 |
| /api/auth/me | GET | 当前用户信息 | Token 失效 → 401 |

注册：

```json
{ "username": "student01", "password": "Password123", "email": "student@example.com" }
```

登录返回：

```json
{
  "code": 200, "message": "success",
  "data": { "token": "jwt-token", "user": { "id": 1, "username": "student01" } }
}
```

## 5. AI 对话（chat）

| 接口 | 方法 | 说明 |
|---|---|---|
| /api/chat/sessions | POST | 创建会话 |
| /api/chat/sessions | GET | 会话列表 |
| /api/chat/sessions/{id}/messages | GET | 历史消息（session 归属校验） |
| /api/chat/send | POST | 发送消息（后端组织多轮上下文调用 AI） |

发送消息：

```json
{ "sessionId": 1001, "message": "我是计算机专业大二学生，学习了Java，做过学生管理系统。" }
```

返回：

```json
{
  "code": 200, "message": "success",
  "data": {
    "sessionId": 1001,
    "reply": "你的学生管理系统使用了什么技术实现数据存储？",
    "extractedInfo": {
      "major": "计算机相关专业",
      "grade": "大二",
      "skills": ["Java"],
      "projects": [{ "name": "学生管理系统", "techStack": ["Java"] }]
    },
    "missingFields": ["项目开发时间", "个人负责模块", "项目数据存储方式"]
  }
}
```

校验与错误：sessionId 不属于当前用户 → 403；message 空 → 400；AI 失败 → 502（不落库）。

### 5.1 AI 抽取候选信息的确认流程（二轮审查明确）

**抽取结果不等于档案数据。** 完整流程：

```
用户消息 → AI 抽取 → 后端创建 profile_evidence（PENDING，实体表不写入）
→ 前端展示「待确认」信息 → 用户确认/拒绝（POST /api/profile/evidence/confirm）
→ CONFIRMED：写入档案实体表；REJECTED：不写入，标记拒绝
```

- **自动确认规则**：基础信息与教育经历字段（姓名、学校、专业等）信息明确时，可抽取后直接 CONFIRMED 并写入；技能、项目、工作/实习经历**必须用户确认**（涉及归因风险，如「学过 MySQL」≠「项目用了 MySQL」）
- **D4 实现策略**：所有抽取项（含基础信息与教育经历）统一先落 PENDING，由用户显式确认；自动确认暂不启用
- **事实校验（Java）**：落 PENDING 前逐项校验抽取内容能否在本轮用户原话中找到依据（归一化包含 / 数字匹配 / 2-gram 覆盖率 ≥ 60%），不通过则丢弃；Prompt 中的示例内容禁止作为真实信息输出
- 用户拒绝的信息标记 REJECTED，**不允许进入已确认档案，也不允许出现在简历中**
- 简历生成只使用「已确认且有证据」的信息

## 6. 个人档案（profile）

| 接口 | 方法 | 说明 |
|---|---|---|
| /api/profile | GET | 获取档案（仅含已确认数据；待确认项走 evidence 接口） |
| /api/profile | PUT | 更新档案（手动编辑，写 evidence source_type=MANUAL 且 CONFIRMED） |
| /api/profile/projects | POST / PUT / DELETE /{id} | 项目经历增改删（归属校验） |
| /api/profile/experiences | POST / PUT / DELETE /{id} | 工作/实习经历增改删（归属校验） |
| /api/profile/evidence | GET | ⭐ 证据列表（query: status=PENDING/CONFIRMED/REJECTED，默认 PENDING） |
| /api/profile/evidence/confirm | POST | ⭐ 确认/拒绝抽取信息（二轮审查新增） |
| /api/profile/completeness | GET | 完整度（Java 计算 6 维度，见 database.md §6） |

### 6.1 证据列表

GET /api/profile/evidence?status=PENDING

返回：

```json
{
  "code": 200, "message": "success",
  "data": [
    {
      "id": 101,
      "entityType": "PROJECT",
      "entityId": null,
      "fieldName": "project",
      "sourceType": "CHAT",
      "sourceId": 55,
      "sourceContent": "我用 Java 做过学生管理系统",
      "evidenceContent": "{\"projectName\":\"学生管理系统\",\"techStack\":[\"Java\"]}",
      "confirmationStatus": "PENDING",
      "createdAt": "2026-11-10T10:30:00"
    }
  ]
}
```

说明：
- status 只允许 PENDING / CONFIRMED / REJECTED，缺省 PENDING；非法值 → 400；结果按 id 倒序
- sourceContent 为来源消息原文（sourceType=CHAT 时经 sourceId 关联 chat_message），用于展示「AI 从哪句话里抽取」；sourceId 可能指向历史用户消息，而不是本轮消息
- evidenceContent：PROFILE / SKILL 为文本值；PROJECT / EXPERIENCE 为 JSON（projectName、role、description、techStack、startDate、endDate 或 company、position、description、achievements、startDate、endDate）

### 6.2 确认接口

请求：

```json
{ "evidenceIds": [101, 102], "confirmed": true }
```

返回：

```json
{
  "code": 200, "message": "success",
  "data": {
    "confirmedCount": 2,
    "updatedFields": ["skills", "project.techStack"]
  }
}
```

行为与校验：
- 逐条校验证据归属当前用户（不归属 → 403；不存在 → 404；已处理 → 409 并返回其当前状态）
- 同一请求中重复的 evidenceId 只处理一次；单次最多 100 条
- `confirmedCount` 只统计本次真正变为 CONFIRMED 的证据；`confirmed=false` 时为 0，`updatedFields` 为空
- `updatedFields` 使用 API camelCase 字段路径（如 `realName`、`graduationYear`、`project.techStack`）
- `confirmed=true`：evidence → CONFIRMED，按 entity_type/field_name 映射写入档案实体表（PROFILE→user_profile；SKILL→user_profile.skills；PROJECT/EXPERIENCE→对应实体表，entity_id 为 NULL 时创建实体并回填）
- 并发确认使用「PENDING 条件更新」，只有仍为 PENDING 的证据能流转，冲突 → 409
- `confirmed=false`：evidence → REJECTED，不写入任何档案数据

### 6.3 完整度

GET /api/profile/completeness

返回：

```json
{
  "code": 200, "message": "success",
  "data": {
    "overall": 75,
    "dimensions": [
      { "name": "基础信息", "score": 100 },
      { "name": "教育经历", "score": 80 },
      { "name": "技能信息", "score": 70 },
      { "name": "项目经历", "score": 50 },
      { "name": "工作/实习经历", "score": 50 },
      { "name": "求职意向", "score": 100 }
    ]
  }
}
```

评分细则见 database.md §6；技能/项目/经历三个逻辑维度采用分级计分，保证雷达图结果可解释。

### 6.4 档案读写与经历 CRUD

GET /api/profile 返回（只含已确认数据；无档案时返回空结构，不 404）：

```json
{
  "code": 200, "message": "success",
  "data": {
    "id": 1,
    "realName": "张三", "phone": "13800000000",
    "school": "某某大学", "major": "计算机科学与技术", "degree": "本科", "graduationYear": 2027,
    "skills": ["Java", "MySQL"],
    "targetPosition": "Java 后端", "targetCity": "杭州", "expectedSalary": "8-12K",
    "projects": [
      { "id": 21, "projectName": "学生管理系统", "role": "后端开发", "description": "增删改查", "techStack": ["Java", "MySQL"], "startDate": "2025-03-01", "endDate": "2025-06-30" }
    ],
    "experiences": [
      { "id": 31, "company": "某某科技", "position": "后端实习生", "description": "参与接口开发", "achievements": "完成 3 个接口", "startDate": "2025-07-01", "endDate": "2025-09-30" }
    ]
  }
}
```

PUT /api/profile 请求（字段全部可选；null 表示不修改，空串表示清空，skills 传数组表示整体替换）：

```json
{
  "realName": "张三",
  "phone": "13800000000",
  "school": "某某大学",
  "major": "计算机科学与技术",
  "degree": "本科",
  "graduationYear": 2027,
  "skills": ["Java", "MySQL"],
  "targetPosition": "Java 后端",
  "targetCity": "杭州",
  "expectedSalary": "8-12K"
}
```

行为：每个实际变更的字段写一条 `source_type=MANUAL`、`confirmation_status=CONFIRMED` 的证据（技能证据 `entity_type=SKILL`）；返回更新后的档案。

项目/经历：

| 接口 | 方法 | 说明 |
|---|---|---|
| /api/profile/projects | POST | 创建项目，写 MANUAL 证据 |
| /api/profile/projects/{id} | PUT | 更新项目，写一条新的 MANUAL 证据 |
| /api/profile/projects/{id} | DELETE | 删除项目及其证据 |
| /api/profile/experiences | POST / PUT / DELETE /{id} | 工作/实习经历同上 |

项目请求：

```json
{
  "projectName": "学生管理系统",
  "role": "后端开发",
  "description": "增删改查",
  "techStack": ["Java", "MySQL"],
  "startDate": "2025-03-01",
  "endDate": "2025-06-30"
}
```

经历请求：

```json
{
  "company": "某某科技",
  "position": "后端实习生",
  "description": "参与接口开发",
  "achievements": "完成 3 个接口",
  "startDate": "2025-07-01",
  "endDate": "2025-09-30"
}
```

校验：记录不存在 → 404；不属于当前用户 → 403；`projectName`/`company` 为空 → 400。删除返回 `{ "code": 200, "message": "success", "data": null }`。

## 7. 简历（resumes）

| 接口 | 方法 | 说明 |
|---|---|---|
| /api/resumes | POST | 创建简历主体 |
| /api/resumes | GET | 简历列表（含 currentVersionId / currentVersionNumber / templateId） |
| /api/resumes/{id} | GET | 简历详情（含当前版本内容） |
| /api/resumes/{id}/generate | POST | AI 生成新版本（锁定事务 + UNIQUE 约束，**永不覆盖旧版本**） |
| /api/resumes/{id}/versions | POST | 手动保存新版本（编辑 / 换模板 / 恢复；**永不覆盖旧版本**） |
| /api/resumes/{id}/versions | GET | 历史版本列表（不含 content） |
| /api/resumes/{id}/versions/{versionId} | GET | 指定版本内容 |
| /api/resumes/{id}/export | POST | 导出 DOCX（**必须指定 versionId**；PDF 由前端浏览器打印生成） |

### 7.1 创建 / 列表 / 详情

创建请求（名称可选，缺省「我的简历」，长度 ≤100）：

```json
{ "resumeName": "Java 开发实习生简历" }
```

列表项：

```json
{
  "id": 1, "resumeName": "我的简历",
  "currentVersionId": 3, "currentVersionNumber": 3,
  "templateId": "6746fe472f0052cb6a9365aa",
  "updatedAt": "2026-11-10T12:00:00"
}
```

详情（尚未生成版本时 `currentVersionId` / `currentVersion` 为 null）：

```json
{
  "code": 200, "message": "success",
  "data": {
    "id": 1, "resumeName": "我的简历",
    "currentVersionId": 3, "currentVersionNumber": 3,
    "currentVersion": {
      "versionId": 3, "versionNumber": 3,
      "generationReason": "INITIAL",
      "templateId": "6746fe472f0052cb6a9365aa",
      "content": {}, "createdAt": "2026-11-10T12:00:00"
    }
  }
}
```

### 7.2 生成新版本

请求：

```json
{
  "templateId": "6746fe472f0052cb6a9365aa",
  "targetJobId": null,
  "generationReason": "INITIAL"
}
```

`templateId` 必填（≤64）；`generationReason` ∈ `INITIAL / JOB_TARGETED / OPTIMIZATION / REGENERATE`。
后端从 **CONFIRMED** 档案确定性组装事实，AI 只改写 `selfEvaluation` 与项目/经历亮点，并逐句事实校验；`targetJobId` 本期只存入 `content.meta`，JD 适配在 D10。

生成/保存返回：

```json
{
  "code": 200, "message": "success",
  "data": {
    "resumeId": 1, "versionId": 3, "versionNumber": 3,
    "content": {}
  }
}
```

### 7.3 手动保存新版本

编辑内容、切换模板、从旧版本恢复都走这里；服务端覆盖 `content.meta`：

```json
{
  "content": {},
  "templateId": "6746fe472f0052cb6a9365aa",
  "generationReason": "MANUAL_EDIT"
}
```

`generationReason` ∈ `MANUAL_EDIT / TEMPLATE_SWITCH / RESTORE`；服务端设置 `meta.source = "MANUAL"`、`meta.templateId`、`meta.generatedAt`、`meta.generationReason`。

### 7.4 简历内容（ResumeContent）

内容与样式分离：`content` 只描述事实，模板由 `meta.templateId` 决定。

```json
{
  "meta": {
    "templateId": "6746fe472f0052cb6a9365aa",
    "templateName": "双色标签经典简历模板",
    "generatedAt": "2026-11-10T12:00:00",
    "source": "AI",
    "generationReason": "INITIAL",
    "targetJobId": null,
    "warnings": []
  },
  "personalInfo": {
    "realName": "张同学", "phone": "13800000000",
    "email": null, "city": null, "avatar": null,
    "school": "深圳大学", "major": "软件工程", "degree": "本科",
    "graduationYear": 2028, "yearsOfExperience": 0,
    "abstract": "软件工程在读，掌握 Java 与 MySQL。"
  },
  "jobIntention": { "targetPosition": "Java 开发实习生", "targetCity": "深圳", "expectedSalary": "面议" },
  "education": [
    { "school": "深圳大学", "major": "软件工程", "degree": "本科",
      "graduationYear": 2028, "startDate": null, "endDate": null }
  ],
  "skills": ["Java", "MySQL"],
  "projects": [
    { "projectName": "学生信息管理系统", "role": "后端开发",
      "description": "……", "techStack": ["Java", "MySQL"],
      "highlights": [], "startDate": "2025-03-01", "endDate": "2025-06-30" }
  ],
  "experiences": [
    { "company": "某科技有限公司", "position": "后端实习生", "type": "INTERNSHIP",
      "description": "……", "achievements": "……", "highlights": [],
      "startDate": "2025-07-01", "endDate": "2025-09-30" }
  ],
  "selfEvaluation": "……"
}
```

说明：`experiences[].type` 由 Java 判定（岗位/公司含「实习 / Intern」→ `INTERNSHIP`，否则 `WORK`）；`highlights` 为 AI 生成且通过事实校验的亮点；未提供的字段为 null，模板渲染时隐藏。

### 7.5 导出 DOCX

请求（⭐ versionId 必填，**不允许默认导出最新版本**）：

```json
{ "versionId": 3, "format": "DOCX" }
```

| format | 输出 | Content-Type |
|---|---|---|
| DOCX | .docx | `application/vnd.openxmlformats-officedocument.wordprocessingml.document` |

响应为二进制流，`Content-Disposition: attachment; filename*=UTF-8''...`；文件名为 `{resumeName}-v{versionNumber}.docx`。
PDF 由前端把预览区交给浏览器打印生成（与模板样式 100% 一致），不经过后端。

### 7.6 校验与错误

- 简历/版本不存在 → 404；不属于当前用户 → 403。
- `templateId` 缺失/超长、`generationReason` 非法、`versionId` 缺失、`format` 非 DOCX → 400。
- 档案没有任何 CONFIRMED 教育/技能/项目/经历 → 400，提示先完善档案。
- AI 调用失败（超时 / HTTP 错误 / JSON 解析失败）→ 502，不创建版本。
- AI 输出事实校验失败 → 使用确定性兜底文案，版本照常创建，`content.meta.warnings` 含 `AI_FACT_CHECK_FALLBACK`。
- 并发生成由锁定事务 + `UNIQUE(resume_id, version_number)` 保证；冲突返回 409「版本冲突，请重试」。

## 8. 岗位（jobs）

岗位是全局数据（不属于某个用户），但所有接口仍需登录（JWT）。种子数据见 `database/seed.sql`（12 条模拟 JD，ID 1001~1012）。

| 接口 | 方法 | 说明 |
|---|---|---|
| /api/jobs | GET | 岗位列表（分页 + 筛选） |
| /api/jobs/{id} | GET | 岗位详情（含 job_requirement 技能列表） |
| /api/jobs | POST | 手动新增岗位（MVP 仅开发用途，不开放管理端） |
| /api/jobs/{id}/parse | POST | AI 解析 JD 生成技能要求，成功后覆盖该岗位的 job_requirement |

### 8.1 列表筛选参数

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| page / size | int | 否 | 分页，默认 1/10；page < 1 或 size > 50 → 400 |
| keyword | string | 否 | 关键词，模糊匹配 position、company |
| city | string | 否 | 城市精确匹配 |
| salary | string | 否 | 薪资关键字模糊匹配（salary 为文本，见 database.md 已知限制） |

示例：`GET /api/jobs?page=1&size=10&keyword=Java&city=深圳`

返回：

```json
{
  "code": 200, "message": "success",
  "data": {
    "total": 12, "page": 1, "size": 10,
    "records": [
      { "id": 1001, "company": "星云科技", "position": "Java 开发实习生",
        "city": "深圳", "salary": "200-300元/天", "sourceType": "MOCK",
        "externalUrl": null, "createdAt": "2026-11-10T10:00:00" }
    ]
  }
}
```

### 8.2 岗位详情

`GET /api/jobs/{id}`：

```json
{
  "code": 200, "message": "success",
  "data": {
    "id": 1001, "company": "星云科技", "position": "Java 开发实习生",
    "city": "深圳", "salary": "200-300元/天",
    "jdContent": "岗位职责：……任职要求：熟悉 Java、Spring Boot、MySQL，了解 Redis 与 Docker。",
    "sourceType": "MOCK", "externalUrl": null,
    "createdAt": "2026-11-10T10:00:00", "updatedAt": "2026-11-10T10:00:00",
    "requirements": [
      { "id": 1, "skill": "Java", "importance": "REQUIRED", "weight": 25.00 },
      { "id": 2, "skill": "Spring Boot", "importance": "REQUIRED", "weight": 25.00 },
      { "id": 3, "skill": "MySQL", "importance": "REQUIRED", "weight": 20.00 }
    ]
  }
}
```

### 8.3 手动新增岗位（开发用途）

```json
{
  "company": "星云科技",
  "position": "Java 开发实习生",
  "city": "深圳",
  "salary": "200-300元/天",
  "jdContent": "岗位要求：熟悉 Java、MySQL。",
  "externalUrl": null,
  "requirements": [
    { "skill": "Java", "importance": "REQUIRED", "weight": 60 },
    { "skill": "MySQL", "importance": "REQUIRED", "weight": 40 }
  ]
}
```

`company`、`position` 必填；`requirements` 可省略，省略后可通过解析接口生成。返回岗位详情（同 8.2）。`importance` 只能是 `REQUIRED/PREFERRED`，权重由 Java 归一化为合计 100。

### 8.4 JD 解析

请求体可省略；`jdContent` 可选：

```json
{ "jdContent": "岗位要求：熟悉Java、Spring Boot、MySQL，了解Redis。" }
```

行为：

1. 不传 `jdContent` → 使用岗位已保存的 `jd_content`；
2. 传入 `jdContent` → 先覆盖岗位 JD，再解析（便于人工修正后重新生成要求）；
3. AI 输出经 Java 结构校验、技能去重、权重归一化后，覆盖该岗位的 `job_requirement`；
4. 解析失败不覆盖已有技能要求。

返回：

```json
{
  "code": 200, "message": "success",
  "data": {
    "position": "Java 开发实习生",
    "requirements": [
      { "id": 61, "skill": "Java", "importance": "REQUIRED", "weight": 30.00 },
      { "id": 62, "skill": "Spring Boot", "importance": "REQUIRED", "weight": 25.00 },
      { "id": 63, "skill": "MySQL", "importance": "REQUIRED", "weight": 25.00 },
      { "id": 64, "skill": "Redis", "importance": "PREFERRED", "weight": 10.00 },
      { "id": 65, "skill": "Docker", "importance": "PREFERRED", "weight": 10.00 }
    ]
  }
}
```

### 8.5 校验与错误

- 岗位不存在 → 404。
- `page/size` 不合法、创建字段缺失/超长、JD 为空、`importance` 非法 → 400。
- AI 调用失败、AI 返回非 JSON 或未解析出有效技能 → 502，且不覆盖已有技能要求。

## 9. 岗位匹配（match）

| 接口 | 方法 | 说明 |
|---|---|---|
| /api/match/analyze | POST | 分析匹配度（生成 match_result + match_detail） |
| /api/match/{id} | GET | 匹配详情（归属校验） |
| /api/match/jobs/recommend | GET | 岗位推荐（排序参考用户反馈 + 历史反馈统计） |
| /api/match/advice | POST | 投递建议：匹配度分析 + AI 投递话术（MVP 实时生成不落库，P1 独立保存） |

匹配分析请求：

```json
{ "jobId": 1001, "resumeVersionId": 3 }
```

返回：

```json
{
  "code": 200, "message": "success",
  "data": {
    "matchResultId": 5001, "matchScore": 65,
    "matchedCount": 2, "partialCount": 1, "missingCount": 1, "unconfirmedCount": 0,
    "details": [
      { "skill": "Java", "status": "MATCHED", "evidence": "学生管理系统项目", "suggestion": "保留" },
      { "skill": "MySQL", "status": "PARTIAL", "evidence": "个人技能记录中有学习经历", "suggestion": "补充项目应用证据" },
      { "skill": "Spring Boot", "status": "MISSING", "evidence": null, "suggestion": "发起追问确认是否学习过" }
    ]
  }
}
```

**匹配分数由 Java 计算**：`M = Σ(wᵢ·sᵢ) / Σwᵢ × 100%`，wᵢ 为岗位技能权重；sᵢ 满足系数 = 有项目证据 1 / 部分满足 0.5 / 无证据 0 / 待确认不计分并单独标记。此分数是系统定义的规则匹配分，**不是招聘成功概率**。

### 9.1 岗位推荐排序规则（二轮审查明确：可解释规则，无机器学习）

`GET /api/match/jobs/recommend?page=1&size=10`

基础排序：按匹配分数降序。叠加以下**可解释规则**（Java 实现，规则详见 database.md §3.7）：

1. **用户偏好反馈降权**：用户对某岗位反馈过 NOT_INTERESTED → 同 position/company 岗位排序系数 ×0.5；LOCATION_MISMATCH → 该城市后置；SALARY_MISMATCH → 薪资低于期望后置
2. **历史反馈统计微调**：该岗位类型（按 position 关键词）历史已读率/面试率小幅加权
3. 返回中标注每个岗位的 `rankReasons`（如 "同类岗位曾标记不感兴趣，已后置"），保证排序可解释

### 9.2 投递建议（含话术，赛题要求）

请求：

```json
{ "jobId": 1001, "resumeVersionId": 3 }
```

返回：

```json
{
  "code": 200, "message": "success",
  "data": {
    "matchScore": 65,
    "strengths": ["Java 有项目证据", "MySQL 有学习记录"],
    "gaps": ["Spring Boot 无证据"],
    "pitchMessage": "您好，我是XX大学计算机专业大二学生，用 Java 独立完成过学生信息管理系统（增删改查+文件持久化），对 MySQL 有系统学习。看到贵司 Java 实习生岗位，希望有机会进一步交流。",
    "suggestions": ["建议先补充 Spring Boot 练习经历后再投递"]
  }
}
```

约束：话术**仅使用已确认且有证据的信息**，不编造技能；输出经 Java 结构校验。校验与错误：jobId/resumeVersionId 不存在 → 404；简历版本不属于当前用户 → 403。

## 10. 反向优化（optimization）

| 接口 | 方法 | 说明 |
|---|---|---|
| /api/optimization/tasks | POST | 创建优化任务（点击差距项发起） |
| /api/optimization/tasks/{id} | GET | 任务详情（归属校验） |
| /api/optimization/tasks/{id}/reply | POST | 回复 AI 追问（多轮） |
| /api/optimization/tasks/{id}/confirm | POST | 确认补充信息 → 写入档案（evidence CONFIRMED）→ 触发简历重新生成 |

任务创建：

```json
{ "jobId": 1001, "resumeVersionId": 3, "targetSkill": "Spring Boot", "chatSessionId": 1001 }
```

回复追问：

```json
{ "taskId": 2001, "answer": "我学习过Spring Boot，做过简单的用户信息接口，使用了Controller和Service。" }
```

```json
{
  "code": 200, "message": "success",
  "data": {
    "reply": "你这个练习项目使用数据库保存用户信息了吗？",
    "nextQuestion": true,
    "taskStatus": "WAITING_USER"
  }
}
```

任务状态机：WAITING_USER → ANSWER_RECEIVED → LLM_EXTRACTION → USER_CONFIRMATION → PROFILE_UPDATED → RESUME_REGENERATED。
**只有 confirm 后才写入档案**并触发重生成；追问产生的候选信息同样走 profile_evidence（PENDING→CONFIRMED/REJECTED）流程。

## 11. 投递管理（applications）

| 接口 | 方法 | 说明 |
|---|---|---|
| /api/applications | POST | 创建投递（模拟一键投递，状态 PENDING→APPLIED） |
| /api/applications | GET | 投递列表（分页、按状态筛选：query status） |
| /api/applications/{id} | GET | 投递详情 |
| /api/applications/{id}/status | PUT | 更新状态（后端校验状态机） |
| /api/applications/{id}/history | GET | 状态历史（时间轴） |
| /api/applications/follow-ups | GET | 待跟进列表（主动提醒） |
| /api/applications/{id}/follow-up | POST | ⭐ 处理提醒：标记已跟进（二轮审查新增） |

创建投递：

```json
{ "jobId": 1001, "resumeVersionId": 3 }
```

校验：job 存在 → 404；resume_version 存在且属于当前用户 → 403；同一岗位重复投递 → 409。

更新状态（note 即**状态类**反馈）：

```json
{ "status": "INTERVIEW_INVITED", "note": "HR通知下周参加技术面试" }
```

校验：①投递记录属于当前用户 ②简历版本属于当前用户 ③岗位存在 ④状态转移合法（见 database.md §4），否则 403/404/409。更新成功后**自动重置提醒计时**（last_follow_up_at=now，next_follow_up_at=now+3 天）。

### 11.1 待跟进列表（主动提醒，赛题要求）

`GET /api/applications/follow-ups`（触发规则见 database.md §5）：

```json
{
  "code": 200, "message": "success",
  "data": {
    "count": 2,
    "items": [
      {
        "applicationId": 3001, "jobId": 1001, "company": "某科技公司",
        "position": "Java开发实习生", "status": "APPLIED",
        "lastUpdateAt": "2026-11-05T10:00:00+08:00", "daysNoResponse": 3
      }
    ]
  }
}
```

### 11.2 处理提醒

请求：

```json
{ "action": "COMPLETED", "note": "已联系HR", "nextFollowUpAt": "2026-11-15T10:00:00+08:00" }
```

| 参数 | 必填 | 说明 |
|---|---|---|
| action | 是 | 固定 COMPLETED（已跟进） |
| note | 否 | 跟进说明 |
| nextFollowUpAt | 否 | 下次提醒时间；不填默认 now+3 天 |

行为：last_follow_up_at=now，next_follow_up_at=指定值或 now+3 天 → 该记录移出待跟进列表，到点后重新出现（**不重复弹已处理提醒**）。
校验：投递不属于当前用户 → 403；nextFollowUpAt 早于当前时间 → 400。

## 12. 用户反馈（feedback，二轮审查新增）

| 接口 | 方法 | 说明 |
|---|---|---|
| /api/feedback | POST | 提交岗位偏好反馈（写 user_feedback，影响推荐排序） |

请求：

```json
{ "jobId": 1001, "feedbackType": "NOT_INTERESTED", "feedbackContent": "距离太远" }
```

返回：

```json
{ "code": 200, "message": "success", "data": { "feedbackId": 7001 } }
```

校验：jobId 存在 → 404；feedbackType ∈ {NOT_INTERESTED, LOCATION_MISMATCH, SALARY_MISMATCH, SKILL_ALREADY_HAVE}，非法 → 400。
效果：写入 user_feedback 后，GET /api/match/jobs/recommend 按 §9.1 规则调整排序。**与状态类反馈（§11 status+note）区分，两者不混用。**

## 13. 数据库字段 ↔ API 参数命名对照（前后端统一）

| 数据库（snake_case） | API（camelCase） |
|---|---|
| real_name | realName |
| graduation_year | graduationYear |
| target_position | targetPosition |
| target_city | targetCity |
| expected_salary | expectedSalary |
| project_name | projectName |
| tech_stack | techStack |
| jd_content | jdContent |
| source_type | sourceType |
| external_url | externalUrl |
| match_score | matchScore |
| match_status | status |
| resume_version_id | resumeVersionId |
| generation_reason | generationReason |
| current_version_id | currentVersionId |
| confirmation_status | confirmationStatus |
| entity_type / entity_id | entityType / entityId |
| source_id | sourceId |
| evidence_content | evidenceContent |
| old_status / new_status | oldStatus / newStatus |
| applied_at | appliedAt |
| next_follow_up_at / last_follow_up_at | nextFollowUpAt / lastFollowUpAt |
| feedback_type / feedback_content | feedbackType / feedbackContent |

其余字段名一致（如 id、skill、weight、note）。**新增字段必须先在本文件登记。**

> `sourceContent`（§6.1）为派生字段：由 sourceType=CHAT + sourceId 关联 chat_message.content，不落库。

## 14. 接口 ↔ P0 功能覆盖检查

| P0 功能 | 覆盖接口 |
|---|---|
| 注册登录 | /api/auth/register、/login、/me |
| 对话建档 | /api/chat/sessions、sessions/{id}/messages、send |
| 信息抽取+证据链 | /api/chat/send + /api/profile/evidence、evidence/confirm |
| 缺失追问 | /api/chat/send（missingFields） |
| 完整度雷达 | /api/profile/completeness |
| 简历生成+版本 | /api/resumes/* |
| PDF/Word 导出 | /api/resumes/{id}/export（versionId 必填） |
| 求职意向 | /api/profile（PUT） |
| 职位库+JD解析 | /api/jobs*、/api/jobs/{id}/parse |
| 岗位匹配+推荐 | /api/match/analyze、/{id}、jobs/recommend |
| 反向优化 | /api/optimization/tasks* |
| 投递+话术+状态机 | /api/applications*、/api/match/advice |
| 主动提醒 | /api/applications/follow-ups、{id}/follow-up |
| 反馈回流 | /api/feedback + /api/match/jobs/recommend 排序规则 |

## 15. 文件分析（files，D3 增强）

| 接口 | 方法 | 说明 |
|---|---|---|
| /api/files/analyze | POST | 上传资料文件，AI 分析个人资料/项目并生成候选信息与简历文案 |

请求：`multipart/form-data`

| 字段 | 必填 | 说明 |
|---|---|---|
| file | 是 | 可重复 1~6 个；支持 .docx / .pptx / .pdf / .png / .jpg / .jpeg / .bmp / .gif / .txt / .md / .zip / 常见代码文件；单文件 ≤10MB，总大小 ≤60MB；ZIP 必须单独上传 |
| analysisType | 否 | AUTO / PROFILE / PROJECT，默认 AUTO |
| jobDescription | 否 | 目标岗位 JD；提供后返回岗位差距建议 |

返回：

```json
{
  "code": 200, "message": "success",
  "data": {
    "files": [
      { "fileName": "项目说明.docx", "fileType": "DOCX", "truncated": false }
    ],
    "fileName": "项目说明.docx",
    "fileType": "DOCX",
    "analysisType": "PROJECT",
    "truncated": false,
    "summary": "项目是一个基于 Java 的学生信息管理系统……",
    "extractedInfo": {
      "basic": {}, "education": {}, "skills": [],
      "strengths": [], "projects": [], "experiences": [], "jobIntention": {}
    },
    "strengths": ["独立完成增删改查功能"],
    "resumeSuggestions": ["基于 Java SE 实现学生信息管理系统，使用 MyBatis 操作 MySQL……"],
    "jobGapSuggestions": ["JD 要求 Spring Boot，资料中未体现，建议补充真实练习经历"],
    "missingFields": ["项目开发时间", "个人负责模块"],
    "warnings": ["分析结果来自上传文件，需用户确认后才可写入档案"]
  }
}
```

约束与错误：

- 文件只在内存中解析，**不保存原件**；正文超过 20000 字符会截断并标记 `truncated=true`
- 一次最多 6 个文件；多个文件会合并为一次 AI 分析（响应中 `fileName` 为文件列表、`fileType=MULTI`）
- ZIP 只能单独上传，不能与其他文件混合
- PDF：优先抽取文本层；扫描件渲染前 5 页做 OCR（PDFBox）
- 图片：使用本地 OCR（Tess4J，中文 + 英文语言包随应用打包），不调用外部视觉服务
- ZIP 内最多 OCR 3 个图片/PDF，避免压缩包导致长时间占用
- 文件分析包含 AI 调用；默认关闭 thinking 后通常几秒到 20 秒，复杂 PDF/扫描件或多文件可能达到 1 分钟；前端对该接口单独设置 300 秒超时
- 分析结果只是**待确认候选**，不直接写入档案或简历（证据链规则）
- 不支持的文件类型 / 空文件 / 超过大小限制 → 400
- AI 失败 → 502，不产生任何档案数据
- 上传内容按不可信数据处理，提示词中要求模型忽略文档内的指令
