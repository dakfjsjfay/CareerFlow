# CareerFlow-AI 2.0 API 增量设计

> 审计日期：2026-11-14
> 基线：`docs/api.md`（v1）、现有 10 个 Controller
> 约定：不引入 `/api/v2` 前缀，新增接口作为 `/api` 下的增量能力；旧接口保持兼容，不删除、不改语义。
> 说明：本文为设计稿，本轮不实现。

## 1. 全局约定

- 统一响应：`Result<T>`，分页 `PageResult<T>`。
- 认证：`Authorization: Bearer <JWT>`；用户 ID 由 `@AuthenticationPrincipal Long userId` 注入。
- 错误码：沿用现有 `BusinessException` 语义（400 参数、401 未登录、403 无权限、404 不存在、409 冲突、502 AI 不可用）。
- 时间：ISO-8601（`yyyy-MM-dd'T'HH:mm:ss`）。
- 幂等：草稿保存使用 `revision` 乐观锁；文件上传使用 `sha256` 提示重复。
- 新增接口一律做用户归属校验：`user_id = 当前用户`。
- 公共数据（企业、招聘信息、平台入口）只读；写接口仅 `ADMIN`。
- AI 相关接口统一经 `ai/AIService`，不直接在 Controller 调 `DeepSeekClient`。

## 2. 接口总览

| 模块 | 方法 | 路径 | 说明 | 新增/复用 |
|---|---|---|---|---|
| 草稿 | POST | `/api/resume-drafts` | 创建八步草稿 | 新增 |
| 草稿 | GET | `/api/resume-drafts` | 我的草稿列表 | 新增 |
| 草稿 | GET | `/api/resume-drafts/{id}` | 草稿详情 | 新增 |
| 草稿 | PUT | `/api/resume-drafts/{id}` | 全量自动保存（含 revision） | 新增 |
| 草稿 | DELETE | `/api/resume-drafts/{id}` | 放弃草稿（软删除） | 新增 |
| 草稿 | POST | `/api/resume-drafts/{id}/complete` | 第八步生成正式简历与版本 | 新增 |
| 区块 | POST | `/api/resume-drafts/{id}/sections/{sectionKey}/analyze` | 单区块 AI 分析 | 新增 |
| 区块 | POST | `/api/resume-drafts/{id}/sections/{sectionKey}/rewrite` | 生成候选修改（写入历史） | 新增 |
| 区块 | POST | `/api/resume-drafts/{id}/sections/{sectionKey}/chat` | 区块 AI 对话（无状态） | 新增 |
| 修改 | GET | `/api/resumes/{id}/edit-history` | 修改历史列表 | 新增 |
| 修改 | GET | `/api/resume-edit-history/{id}` | 单条修改详情（前后对比） | 新增 |
| 修改 | POST | `/api/resume-edit-history/{id}/confirm` | 确认并应用 | 新增 |
| 修改 | POST | `/api/resume-edit-history/{id}/reject` | 拒绝候选 | 新增 |
| 文件 | POST | `/api/document-assets` | 上传素材（原始简历/JD 截图/证件照） | 新增 |
| 文件 | GET | `/api/document-assets/{id}` | 素材元数据 | 新增 |
| 文件 | GET | `/api/document-assets/{id}/download` | 鉴权下载 | 新增 |
| 文件 | DELETE | `/api/document-assets/{id}` | 软删除素材 | 新增 |
| 文件 | POST | `/api/document-assets/{id}/parse-resume` | DOCX 解析为结构化简历 | 新增 |
| 文件 | POST | `/api/document-assets/{id}/parse-jd` | JD 截图/文本解析 | 新增 |
| 定向 | POST | `/api/resumes/{id}/tailor/preview` | 岗位差距与定向方案预览 | 新增 |
| 定向 | POST | `/api/resumes/{id}/tailor/apply` | 生成定向新版本 | 新增 |
| 导出 | POST | `/api/resumes/{id}/export` | 导出 DOCX（扩展保留模板） | 复用并扩展 |
| 企业 | GET | `/api/companies` | 企业列表/筛选/搜索 | 新增 |
| 企业 | GET | `/api/companies/{id}` | 企业详情 | 新增 |
| 企业 | GET | `/api/companies/{id}/recruitments` | 企业招聘信息 | 新增 |
| 企业 | GET | `/api/companies/industries` | 行业选项 | 新增 |
| 企业 | POST | `/api/admin/companies` | 新增企业 | 新增（ADMIN） |
| 企业 | PUT | `/api/admin/companies/{id}` | 更新企业 | 新增（ADMIN） |
| 企业 | POST | `/api/admin/companies/import` | 批量导入企业 | 新增（ADMIN） |
| 企业 | POST | `/api/admin/companies/{id}/recruitments` | 新增招聘信息 | 新增（ADMIN） |
| 企业 | PUT | `/api/admin/company-recruitments/{id}` | 更新招聘信息 | 新增（ADMIN） |
| 平台 | GET | `/api/job-platforms` | 招聘平台入口列表 | 新增 |
| 投递 | POST | `/api/applications` | 创建投递（增加 channel） | 复用并扩展 |

## 3. 八步草稿接口

### 3.1 创建草稿

`POST /api/resume-drafts`

请求：

```json
{
  "resumeId": null,
  "draftName": "2027 届 Java 后端简历",
  "content": {}
}
```

响应 `Result<ResumeDraftVO>`：

```json
{
  "id": 101,
  "resumeId": null,
  "draftName": "2027 届 Java 后端简历",
  "currentStep": 1,
  "totalSteps": 8,
  "revision": 1,
  "status": "DRAFT",
  "lastSavedAt": "2026-11-14T20:10:00",
  "content": {}
}
```

规则：

- `resumeId` 为空表示“全新简历草稿”；有值时必须属于当前用户。
- 创建时 `currentStep=1`、`revision=1`。

### 3.2 草稿列表

`GET /api/resume-drafts?status=DRAFT&page=1&size=10`

- 只返回 `user_id = 当前用户` 的草稿。
- 默认按 `last_saved_at DESC`。

### 3.3 草稿详情

`GET /api/resume-drafts/{id}`

- 非本人返回 403；不存在返回 404。

### 3.4 全量自动保存

`PUT /api/resume-drafts/{id}`

请求：

```json
{
  "currentStep": 4,
  "revision": 7,
  "content": {
    "schemaVersion": 1,
    "basic": {},
    "direction": {},
    "experiences": [],
    "star": {},
    "soft": {},
    "photoAssetId": null,
    "preview": {},
    "meta": {}
  }
}
```

响应：更新后的 `ResumeDraftVO`，`revision` 变为 8。

规则：

- `WHERE id=? AND user_id=? AND revision=?` 更新；影响行数 0 返回 409 `草稿已被其他页面修改，请刷新`。
- 每次保存 `revision + 1`，同时更新 `last_saved_at`。
- 自动保存建议前端 3 秒节流，后端不做频率限制。

### 3.5 放弃草稿

`DELETE /api/resume-drafts/{id}`

- 软删除：`status=ABANDONED`，不物理删除，历史保留。

### 3.6 完成草稿并生成简历

`POST /api/resume-drafts/{id}/complete`

请求：

```json
{
  "resumeName": "Java 后端简历-v1",
  "generationReason": "八步创建完成"
}
```

响应 `Result<ResumeCompleteVO>`：

```json
{
  "resumeId": 12,
  "versionId": 34,
  "versionNumber": 1,
  "draftId": 101
}
```

规则：

- 先校验草稿归属与 `status=DRAFT`。
- 调用现有 `ResumeVersionService.create` 路径写入不可变版本。
- 若草稿 `resume_id` 为空：先创建 `resume`，再写版本，并回填草稿 `resume_id`。
- 草稿 `status=COMPLETED`，不删除。
- AI 文案必须经 `FactGuard` 过滤，未确认信息不得进入正式内容。

## 4. 区块分析与局部修改

`sectionKey` 取值：`BASIC`、`EDUCATION`、`PROJECT`、`SKILL`、`EXPERIENCE`、`SELF_EVALUATION`、`SOFT`、`PHOTO`。

### 4.1 单区块分析

`POST /api/resume-drafts/{id}/sections/{sectionKey}/analyze`

请求（可选，附加上下文）：

```json
{
  "jobId": 88,
  "focus": "量化成果"
}
```

响应：

```json
{
  "sectionKey": "PROJECT",
  "score": 62,
  "issues": ["项目成果缺少量化指标", "技术栈与目标岗位匹配度一般"],
  "suggestions": ["补充 QPS/耗时/用户量", "突出 Spring Boot 与 MySQL"],
  "missingFields": ["项目成果"],
  "factsConfirmed": true
}
```

规则：

- 只分析指定区块，不返回整份简历。
- `jobId` 可选，用于岗位定向分析。
- 分析结果不落库；用户点击“生成修改”才写入 `resume_edit_history`。

### 4.2 生成候选修改

`POST /api/resume-drafts/{id}/sections/{sectionKey}/rewrite`

请求：

```json
{
  "instruction": "把项目成果改成 STAR 结构并量化",
  "jobId": 88
}
```

响应：

```json
{
  "editHistoryId": 501,
  "sectionKey": "PROJECT",
  "beforeContent": [],
  "afterContent": [],
  "status": "PENDING",
  "warnings": ["以下内容需你确认后才可应用"]
}
```

规则：

- 写入 `resume_edit_history`，`source=AI`，`status=PENDING`，`draft_id`、`base_version_id` 按场景填写。
- **不直接修改草稿**。
- AI 输出必须经 `FactGuard`；发现未确认技能时剔除并在 `warnings` 说明。

### 4.3 区块 AI 对话（无状态）

`POST /api/resume-drafts/{id}/sections/{sectionKey}/chat`

请求：

```json
{
  "message": "这段项目描述还能怎么改？",
  "history": [
    { "role": "user", "content": "..." },
    { "role": "assistant", "content": "..." }
  ]
}
```

响应：

```json
{
  "reply": "可以从以下三个角度优化……",
  "proposedEditHistoryId": null
}
```

规则：

- 第一阶段不持久化对话，`history` 由前端携带；避免与现有 `chat_session` 的“建档会话”语义混淆。
- 若 AI 给出完整区块候选，可创建 `resume_edit_history` 并返回 `proposedEditHistoryId`。
- 待确认：是否需要为区块对话单独建会话表；当前设计为无状态，降低复杂度。

### 4.4 修改历史

`GET /api/resumes/{id}/edit-history?sectionKey=PROJECT&status=PENDING&page=1&size=20`

- 必须校验 `resume` 属于当前用户。
- 返回按 `created_at DESC`。

### 4.5 单条修改详情

`GET /api/resume-edit-history/{id}`

响应包含 `beforeContent`、`afterContent`、`status`、`source`、`model`、`createdAt`、`appliedAt`。

### 4.6 确认应用

`POST /api/resume-edit-history/{id}/confirm`

请求（可选，用户二次编辑后的最终内容）：

```json
{
  "finalContent": []
}
```

规则：

- 仅 `PENDING` 可确认；重复确认返回 409。
- 校验归属：`user_id = 当前用户`。
- 将 `afterContent`（或 `finalContent`）写回草稿对应区块，`revision + 1`。
- 更新历史 `status=APPLIED`、`applied_at=now()`。
- 不直接生成正式版本；正式版本仍由“完成草稿”或“手动保存版本”触发。

### 4.7 拒绝修改

`POST /api/resume-edit-history/{id}/reject`

- 仅 `PENDING` 可拒绝；`status=REJECTED`。
- 草稿内容不变。

## 5. 文件素材与解析

### 5.1 上传素材

`POST /api/document-assets`（`multipart/form-data`）

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `file` | File | 是 | 单文件 |
| `assetType` | String | 是 | `ORIGINAL_RESUME` / `JD_SCREENSHOT` / `PHOTO` / `OTHER` |

响应：

```json
{
  "id": 9001,
  "assetType": "ORIGINAL_RESUME",
  "originalName": "我的简历.docx",
  "mimeType": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
  "fileSize": 245760,
  "sha256": "…",
  "duplicate": false,
  "createdAt": "2026-11-14T20:20:00"
}
```

规则：

- 单文件上限沿用 `spring.servlet.multipart.max-file-size=10MB`。
- 安全校验：扩展名白名单（docx/pdf/png/jpg/jpeg/bmp/gif）、MIME、大小；不信任前端。
- 落盘路径由后端生成，前端永远拿不到物理路径。
- `duplicate=true` 仅提示，不阻止上传。

### 5.2 素材元数据 / 下载 / 删除

- `GET /api/document-assets/{id}`：返回元数据，不含 `storage_path`。
- `GET /api/document-assets/{id}/download`：`Content-Disposition: attachment`，先校验 `user_id`。
- `DELETE /api/document-assets/{id}`：`status=DELETED`，不物理删文件。

### 5.3 DOCX 解析为结构化简历

`POST /api/document-assets/{id}/parse-resume`

响应：

```json
{
  "assetId": 9001,
  "detectedSections": ["BASIC", "EDUCATION", "PROJECT", "SKILL", "EXPERIENCE"],
  "content": {
    "basic": {},
    "education": [],
    "projects": [],
    "skills": [],
    "experiences": [],
    "selfEvaluation": ""
  },
  "warnings": ["第 2 页表格未完全识别"],
  "templatePreserved": true,
  "draftId": null
}
```

规则：

- 仅支持 DOCX（第一阶段）；PDF 明确标记“待扩展”。
- 复用 `DocumentTextExtractor`，新增结构化解析（段落/表格 → 区块）。
- `templatePreserved=true` 只表示原文件已保存，**不承诺样式级还原**。
- 可选 `draftId`：解析结果可直接填入草稿。

### 5.4 JD 截图/文本解析

`POST /api/document-assets/{id}/parse-jd`

请求（可选）：

```json
{
  "saveAsJob": true,
  "companyId": 3
}
```

响应：

```json
{
  "rawText": "……",
  "job": {
    "position": "Java 后端开发",
    "company": "示例科技",
    "city": "杭州",
    "salary": "15-25K",
    "requirements": [
      { "skill": "Java", "importance": "REQUIRED", "weight": 0.9 }
    ]
  },
  "savedJobId": 120,
  "warnings": ["截图底部薪资区域未识别"]
}
```

规则：

- 复用 `OcrService`（图片）与 `DocumentTextExtractor`（docx/pdf/txt）。
- 解析后可选写入 `job` + `job_requirement`，`source_type=MANUAL`、`created_by=当前用户`、`visibility=PRIVATE`。
- 同步执行；第一阶段不建 `job_parse_task`。

## 6. 岗位定向简历

### 6.1 定向预览

`POST /api/resumes/{id}/tailor/preview`

请求：

```json
{
  "jobId": 88,
  "sourceAssetId": 9001
}
```

响应：

```json
{
  "jobId": 88,
  "resumeId": 12,
  "baseVersionId": 34,
  "matchScore": 71.5,
  "gaps": [
    { "skill": "Redis", "status": "MISSING", "suggestion": "补充缓存设计经历" }
  ],
  "sectionPlans": [
    { "sectionKey": "PROJECT", "action": "REWRITE", "reason": "突出高并发" }
  ],
  "templatePreserved": true
}
```

规则：

- 复用 `MatchService` + `MatchCalculator`，不重复实现匹配。
- 只预览，不写版本。
- `sourceAssetId` 必须属于当前用户且为 `ORIGINAL_RESUME`。

### 6.2 定向生成

`POST /api/resumes/{id}/tailor/apply`

请求：

```json
{
  "jobId": 88,
  "sourceAssetId": 9001,
  "generationReason": "定向 Java 后端岗位",
  "saveAsNewResume": false
}
```

响应：

```json
{
  "resumeId": 12,
  "versionId": 35,
  "versionNumber": 2,
  "sourceAssetId": 9001,
  "exportReady": true
}
```

规则：

- 生成新版本，旧版本不变。
- `saveAsNewResume=true` 时复制为新 `resume`（用于“一岗一版”管理）。
- 内容必须经 `FactGuard`；未确认技能不写入。
- 原 DOCX 文件保留在 `document_asset`，用于后续导出参考。

## 7. 导出扩展

`POST /api/resumes/{id}/export`（复用现有接口）

请求体扩展：

```json
{
  "versionId": 35,
  "format": "DOCX",
  "preserveTemplate": true
}
```

规则：

- `format` 第一阶段仅 `DOCX`；`PDF` 返回 400 `暂不支持 PDF 导出`。
- `preserveTemplate=true` 且 `resume.source_document_asset_id` 存在时，导出使用原文件作为模板基底；否则回退现有 `ResumeDocxExporter`。
- 明确：样式级保留为渐进能力，第一阶段保证“原文件可下载 + 新版本可导出”。

## 8. 企业信息库

### 8.1 企业列表

`GET /api/companies?page=1&size=20&industry=互联网&city=杭州&keyword=字节`

响应：

```json
{
  "list": [
    {
      "id": 1,
      "name": "示例科技有限公司",
      "shortName": "示例科技",
      "industry": "互联网",
      "companyType": "民企",
      "scale": "1000-9999 人",
      "city": "杭州",
      "website": "https://example.com",
      "recruitmentUrl": "https://example.com/jobs",
      "logoUrl": null,
      "lastVerifiedAt": "2026-10-01T00:00:00"
    }
  ],
  "total": 213,
  "page": 1,
  "size": 20
}
```

规则：

- 登录即可读；只返回 `status=ACTIVE`。
- `keyword` 匹配 `name` / `short_name`。
- 不返回内部字段 `source_url` 以外的敏感信息（本身无敏感信息）。

### 8.2 企业详情

`GET /api/companies/{id}`

- 含简介、官网、招聘官网、来源、最后核验时间。
- 可选返回 `recruitments` 摘要。

### 8.3 企业招聘信息

`GET /api/companies/{id}/recruitments?type=AUTUMN&status=OPEN`

响应字段：`id`、`title`、`recruitmentType`、`targetGraduates`、`city`、`startAt`、`endAt`、`applyUrl`、`status`、`source`、`sourceUrl`、`lastVerifiedAt`。

### 8.4 行业选项

`GET /api/companies/industries`

- 返回去重后的行业列表，供前端筛选器使用。

### 8.5 管理端接口（ADMIN）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/admin/companies` | 新增企业 |
| PUT | `/api/admin/companies/{id}` | 更新企业 |
| POST | `/api/admin/companies/import` | 批量导入（JSON/CSV） |
| POST | `/api/admin/companies/{id}/recruitments` | 新增招聘信息 |
| PUT | `/api/admin/company-recruitments/{id}` | 更新招聘信息 |

规则：

- `SecurityConfig` 增加 `/api/admin/**` → `hasRole('ADMIN')`。
- 批量导入按 `name` upsert，返回 `created/updated/skipped` 统计。
- 所有写操作必须更新 `last_verified_at`。

## 9. 招聘平台入口

`GET /api/job-platforms`

响应：

```json
[
  { "code": "BOSS", "name": "BOSS 直聘", "url": "https://www.zhipin.com", "logoUrl": null },
  { "code": "LAGOU", "name": "拉勾", "url": "https://www.lagou.com", "logoUrl": null }
]
```

规则：

- 只提供入口链接，不实现自动投递、不抓取平台数据。
- 平台清单第一阶段配置在后端常量或配置文件，不建表。

## 10. 投递接口扩展

`POST /api/applications`（复用）

请求新增字段：

```json
{
  "jobId": 88,
  "resumeVersionId": 35,
  "channel": "OFFICIAL_SITE",
  "appliedUrl": "https://example.com/jobs/123",
  "appliedAt": "2026-11-14T20:30:00"
}
```

规则：

- `channel` 取值 `MANUAL/OFFICIAL_SITE/JOB_BOARD/REFERRAL`，默认 `MANUAL`。
- 继续校验 `resumeVersionId` 属于当前用户，且 `jobId` 可见。
- 不新增自动投递接口。

## 11. 权限矩阵

| 接口组 | 未登录 | USER | ADMIN |
|---|---|---|---|
| `/api/auth/**`、`/api/health` | 允许 | 允许 | 允许 |
| `/api/resume-drafts/**` | 401 | 仅本人 | 仅本人 |
| `/api/resume-edit-history/**` | 401 | 仅本人 | 仅本人 |
| `/api/document-assets/**` | 401 | 仅本人 | 仅本人 |
| `/api/resumes/**` | 401 | 仅本人 | 仅本人 |
| `/api/companies/**` 读 | 401 | 允许 | 允许 |
| `/api/job-platforms` | 401 | 允许 | 允许 |
| `/api/admin/**` | 401 | 403 | 允许 |

## 12. 兼容性说明

- 所有新增接口为增量，旧前端不受影响。
- `POST /api/resumes/{id}/export` 请求体新增字段均为可选，旧调用保持可用。
- `POST /api/applications` 新增 `channel`、`appliedUrl` 可选，旧调用默认 `MANUAL`。
- `GET /api/jobs` 未来可增加 `companyId` 筛选，但默认行为不变。
- 不修改 `Result` 结构、不改变已有错误码语义。

## 13. 待确认

- 区块 AI 对话是否需要持久化（当前设计为无状态，`history` 由前端携带）。
- DOCX 解析的结构化程度：是否要把表格识别为教育/项目条目，第一阶段先给出“可编辑候选 + 警告”。
- PDF 导出排期。
- 企业批量导入格式：JSON 还是 CSV；建议 JSON，便于嵌套招聘信息。
- 招聘平台清单是否需要后台可维护；第一阶段用配置常量。
