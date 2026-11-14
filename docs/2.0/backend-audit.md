# CareerFlow-AI 2.0 后端审计

> 审计日期：2026-11-14
> 审计分支：`feature/2.0-audit`
> 审计方式：静态阅读 `backend/src/main/java`、`backend/pom.xml`、`backend/src/main/resources/application.yml`、`database/init.sql`，核对现有测试类。
> 范围：只审计现状，不修改业务代码。

## 1. 结论摘要

- 后端是完整可运行的模块化单体：`com.team.resume` 下按业务域分包，共 14 个业务包 + 3 个基础包，没有跨包循环依赖的迹象。
- 认证采用无状态 JWT + Spring Security；所有接口默认要求登录，仅注册/登录/健康检查放行；**没有角色体系**，写岗位等管理型接口无管理员校验。
- 简历采用“主体 + 不可变版本”模型：`resume` 保存当前版本指针，`resume_version` 只新增、不更新、不删除，版本号由行锁 + `max + 1` + 唯一索引保证。
- 文件能力已有较扎实的基础：DOCX/PPTX/PDF/TXT/图片/ZIP 文本抽取、Tess4J 本地 OCR、DOCX 导出；但**上传文件不落盘、分析结果不持久化**，无法支撑 2.0 的素材复用和“保留原始 DOCX 模板”。
- AI 调用已统一收敛到 `ai` 包：`AIService` 是业务唯一入口，`DeepSeekClient` 是唯一 HTTP 出口；提示词、JSON 解析、空白响应重试、502 降级均有实现。
- 2.0 需要的八步草稿、区块级修改确认、企业信息库、公司招聘信息、投递渠道字段、简历素材文件表**均不存在**。
- 现有 27 个后端测试类覆盖认证、档案、证据链、简历生成/版本/导出、文件抽取、AI 解析、匹配、投递状态机和反馈分析，可作为 2.0 回归基线。

## 2. 包结构与职责

根包：`backend/src/main/java/com/team/resume`

| 包 | 关键类 | 职责 | 2.0 复用判断 |
|---|---|---|---|
| `auth` | `AuthController`、`AuthService`、`JwtUtil`、`JwtAuthFilter`、`SysUser` | 注册、登录、当前用户、JWT 校验 | 直接复用；需扩展角色 |
| `config` | `SecurityConfig`、`CorsConfig`、`MybatisPlusConfig` | 安全、跨域、分页插件 | 直接复用；需加管理端路径规则 |
| `common` | `Result`、`PageResult`、`BusinessException`、`GlobalExceptionHandler` | 统一响应与异常 | 直接复用 |
| `profile` | `ProfileService`、`EvidenceService`、`CompletenessService`、`FactGuard` | 档案、项目/经历、证据链、完整度、事实校验 | 直接复用，是 2.0 定向生成的事实底座 |
| `chat` | `ChatService`、`ChatController` | 多轮建档对话、消息持久化 | 复用会话模型；2.0 需新增“区块修改对话” |
| `file` | `DocumentTextExtractor`、`OcrService`、`FileAnalysisService` | 文件文本抽取、OCR、AI 分析 | 复用抽取与 OCR；需新增落盘与任务化 |
| `resume` | `ResumeService`、`ResumeVersionService`、`ResumeContentAssembler`、`ResumeAiService`、`ResumeDocxExporter` | 简历主体、版本、内容组装、AI 文案、导出 | 核心复用；需新增草稿、区块编辑 |
| `job` | `JobService`、`JobController` | 岗位列表/详情/新增/JD 解析 | 复用；需关联企业、权限收紧 |
| `matching` | `MatchService`、`MatchCalculator`、`MatchAdviceService`、`FeedbackService` | 匹配分析、推荐、投递话术、反馈 | 直接复用 |
| `optimization` | `OptimizationService` | 反向优化追问任务 | 直接复用 |
| `application` | `ApplicationService`、`ApplicationStatus` | 投递、状态机、跟进提醒、历史 | 直接复用；需加渠道字段 |
| `analytics` | `FeedbackAnalyticsService` | 反馈分析统计 | 直接复用 |
| `ai` | `AIService`、`DeepSeekClient`、`PromptTemplate`、`AIResponseParser` | AI 统一入口与解析 | 2.0 所有新 AI 能力必须走这里 |

## 3. Controller 与 API 现状

统一返回 `Result<T>`；分页返回 `PageResult<T>`；用户 ID 通过 `@AuthenticationPrincipal Long userId` 注入。

### 3.1 认证 `auth/controller/AuthController.java`

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | `/api/auth/register` | 公开 | 用户名/密码注册，BCrypt 加密 |
| POST | `/api/auth/login` | 公开 | 返回 JWT 与用户信息 |
| GET | `/api/auth/me` | 登录 | 当前用户 |

### 3.2 档案 `profile/controller/ProfileController.java`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET / PUT | `/api/profile` | 查询/更新基础档案 |
| POST / PUT / DELETE | `/api/profile/projects`、`/api/profile/projects/{id}` | 项目经历 CRUD |
| POST / PUT / DELETE | `/api/profile/experiences`、`/api/profile/experiences/{id}` | 工作/实习经历 CRUD |
| GET | `/api/profile/completeness` | 档案完整度 |
| GET | `/api/profile/evidence` | 待确认证据列表 |
| POST | `/api/profile/evidence/confirm` | 确认/拒绝证据并落档 |

### 3.3 对话 `chat/controller/ChatController.java`

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/chat/sessions` | 创建会话 |
| GET | `/api/chat/sessions` | 会话列表 |
| GET | `/api/chat/sessions/{id}/messages` | 消息历史 |
| POST | `/api/chat/send` | 发送消息，触发抽取与证据生成 |

### 3.4 简历 `resume/controller/ResumeController.java`

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/resumes` | 创建简历主体 |
| GET | `/api/resumes` | 当前用户简历列表 |
| POST | `/api/resumes/{id}/generate` | 基于档案 + AI 生成新版本 |
| POST | `/api/resumes/{id}/versions` | 手动保存新版本 |
| GET | `/api/resumes/{id}` | 简历详情（含当前版本） |
| GET | `/api/resumes/{id}/versions` | 版本列表 |
| GET | `/api/resumes/{id}/versions/{versionId}` | 单版本内容 |
| POST | `/api/resumes/{id}/export` | 导出 DOCX（仅 DOCX） |

### 3.5 岗位与匹配

`job/controller/JobController.java`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/jobs` | 分页 + keyword/city/salary |
| GET | `/api/jobs/{id}` | 详情含技能要求 |
| POST | `/api/jobs` | 手动新增岗位 |
| POST | `/api/jobs/{id}/parse` | AI 解析 JD 并写入 `job_requirement` |

`matching/controller/MatchController.java`

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/match/analyze` | 基于指定简历版本分析岗位匹配 |
| GET | `/api/match/{id}` | 匹配结果详情 |
| GET | `/api/match/jobs/recommend` | 推荐岗位（含历史反馈加权） |
| POST | `/api/match/advice` | 生成投递建议话术（含事实校验兜底） |

`matching/controller/FeedbackController.java`

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/feedback` | 岗位不感兴趣/地点/薪资/技能反馈 |

### 3.6 反向优化 `optimization/controller/OptimizationController.java`

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/optimization/tasks` | 创建针对技能差距的追问任务 |
| GET | `/api/optimization/tasks/{id}` | 任务详情 |
| POST | `/api/optimization/tasks/{id}/reply` | 多轮回答 |
| POST | `/api/optimization/tasks/{id}/confirm` | 确认抽取结果并生成证据 |

### 3.7 投递 `application/controller/ApplicationController.java`

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/applications` | 创建投递，绑定 `resume_version_id` |
| GET | `/api/applications` | 投递列表 |
| GET | `/api/applications/follow-ups` | 待跟进提醒 |
| GET | `/api/applications/{id}` | 投递详情 |
| PUT | `/api/applications/{id}/status` | 状态流转（非法转移 409） |
| GET | `/api/applications/{id}/history` | 状态历史 |
| POST | `/api/applications/{id}/follow-up` | 记录跟进 |

### 3.8 文件、分析与健康

| 方法 | 路径 | 类 | 说明 |
|---|---|---|---|
| POST | `/api/files/analyze` | `FileAnalysisController` | 多文件（最多 6 个）分析，`analysisType` = AUTO/PROFILE/PROJECT |
| GET | `/api/analytics/feedback` | `AnalyticsController` | 反馈漏斗与分布 |
| GET | `/api/health` | `HealthController` | 公开健康检查 |

## 4. 认证与权限审计

- `config/SecurityConfig.java:48-61`：CSRF 关闭、无 Session、`/api/auth/register`、`/api/auth/login`、`/api/health` 放行，其余全部 `authenticated()`。
- `auth/JwtAuthFilter.java`：解析 Bearer Token，把用户 ID 放入 SecurityContext。
- 401/403 以统一 `Result.error` JSON 返回，符合前端拦截器预期。
- 数据归属校验主要在 Service 层手工完成，例如：
  - `ResumeVersionService.requireResume` 校验 `resume.userId == userId`，否则 403。
  - `ApplicationService` 所有查询按 `user_id` 过滤。
  - `ProfileService` / `EvidenceService` 按 `user_id` 过滤。
- 现存问题：
  1. `sys_user` 无 `role` 字段，任何登录用户都能调用 `POST /api/jobs`、`POST /api/jobs/{id}/parse`。
  2. `JobController` 注释称“岗位是公共资源”，但 `SecurityConfig` 实际要求登录，注释与实现不一致。
  3. `job` 表无 `created_by`/`visibility`，用户手动新增的岗位会进入全局列表，可能互相污染。
  4. 匹配、优化、投递之间通过 `job_id`/`resume_version_id` 关联，但部分查询未再次校验 `job` 是否属于当前用户可见范围（岗位当前全局共享，风险暂时被掩盖）。

## 5. 简历生成与版本管理

核心类：

- `resume/service/ResumeService.java`：创建、列表、详情、生成、手动保存、导出编排。
- `resume/service/ResumeContentAssembler.java`：把档案、项目、经历、证据组装成 `ResumeContent`。
- `resume/service/ResumeAiService.java`：调用 `AIService.generateResume`，并用 `FactGuard` 过滤未确认事实。
- `resume/service/ResumeVersionService.java`：版本写入与读取。
- `resume/vo/ResumeContent.java`：结构化简历内容（个人信息、求职意向、教育、技能、项目、经历、自我评价）。

版本不可变实现要点（`ResumeVersionService.java:43-71`）：

1. `resumeMapper.selectByIdForUpdate(resumeId)` 行锁。
2. 校验归属，非本人 403。
3. `selectMaxVersionNumber(resumeId) + 1`。
4. 插入失败 `DuplicateKeyException` 转 409。
5. 更新 `resume.current_version_id`。
6. 无 update/delete 版本接口。

2.0 可复用结论：

- “草稿态”不能塞进 `resume_version`，否则破坏不可变语义；应新增 `resume_draft`（见数据库增量文档）。
- 区块级修改确认可复用“生成新版本”路径：确认后在草稿上应用，再显式保存为新版本。
- `ResumeContent` 是八步表单的天然目标结构，但当前只有 7 个顶层字段，缺少证件照、软性信息、模块开关等 2.0 字段。

## 6. 文件上传、解析与导出

### 6.1 文本抽取 `file/DocumentTextExtractor.java`

支持格式：

- DOCX：段落 + 表格文本。
- PPTX：文本框 + 表格 + 组合形状递归。
- PDF：优先文本层；无文本层渲染前 5 页做 OCR。
- 图片：PNG/JPG/JPEG/BMP/GIF → `OcrService`。
- ZIP：递归分析文本/Word/PPT/PDF/图片，限制 200 条目、单条 2MB、总 20MB、OCR 最多 3 个重文件。
- 纯文本/代码：UTF-8 → GBK 回退，去 BOM。

安全约束：不落盘、不解压到磁盘、路径穿越跳过、ZIP 炸弹防护（`ZipSecureFile`）。

### 6.2 OCR `file/OcrService.java`

- Tess4J + 随包语言包 `tessdata/eng.traineddata`、`tessdata/chi_sim.traineddata`。
- 启动时复制语言包到临时目录。
- 图片超过 2400px 先等比缩放。
- 仅在内存中处理，不落盘。

### 6.3 文件分析 `file/FileAnalysisService.java`

- 最多 6 个文件，总大小 60MB，合并文本上限 20000 字符。
- ZIP 必须单独上传。
- 调用 `AIService.analyzeDocument`，返回摘要、抽取信息、优势、简历建议、岗位差距、缺失字段、警告。
- **结果不写数据库**，仅返回前端；依赖证据链二次确认才入档。

### 6.4 导出 `resume/service/ResumeDocxExporter.java`

- 使用 Apache POI 生成单栏 DOCX：姓名 → 联系方式 → 求职意向 → 教育 → 技能 → 项目 → 经历 → 自我评价。
- 页面 A4、统一边距、微软雅黑、主题色 `2563EB`。
- 空章节跳过，用户文本去除 HTML 标签。
- 明确“不追求与前端 20 套模板 1:1 一致”。

2.0 差距：

- 无原始文件持久化，无法“保留原 DOCX 模板”。
- 无 PDF 导出。
- 无素材文件权限校验（当前不存文件，所以也谈不上）。
- 无解析任务表，长任务无法异步化/重试/审计。

## 7. AI 调用链

调用层级：

```text
Controller → 业务 Service → AIService → DeepSeekClient → DeepSeek API
```

`ai/AIService.java` 现有 8 个方法：

| 方法 | 使用方 | 输出 |
|---|---|---|
| `chat` | `ChatService` | 对话回复 + 抽取候选 |
| `analyzeDocument` | `FileAnalysisService` | 文件分析结果 |
| `parseJd` | `JobService` | 岗位名 + 技能要求 |
| `generateResume` | `ResumeAiService` | 自我评价/亮点文案 |
| `generateOptimizationQuestion` | `OptimizationService` | 首轮追问 |
| `optimizationReply` | `OptimizationService` | 追问回复 + 新信息 |
| `generateAdvicePitch` | `MatchAdviceService` | 投递话术 |
| `extractOptimizationInfo` | `OptimizationService` | 批量抽取 |

`ai/DeepSeekClient.java` 行为：

- 连接超时 5s，读超时 60s，最多 2 次尝试。
- `response_format = json_object`，temperature 0.3。
- 401/404 直接映射 502 并给出中文配置提示。
- 其他失败重试 1 次，最终 502。
- API Key 只从环境变量读取，不写日志。
- 空白 content 抛 `EmptyAiContentException`，由 `AIService.chat` 追加约束重试。

2.0 要求“AI 不得编造”，现状已具备两道防线：

1. 提示词约束 + JSON 结构。
2. `profile/support/FactGuard.java` 对生成文案做事实过滤；投递话术有确定性兜底。

## 8. JD 解析、匹配与反向优化

### 8.1 JD 解析

- `JobService.parse`：读取 `job.jd_content`，调用 `AIService.parseJd`，把技能写入 `job_requirement`（`skill`、`importance`、`weight`）。
- 当前只接受**文本 JD**，没有截图上传入口；不过 `DocumentTextExtractor` + `OcrService` 已具备图片 OCR 能力，可直接复用。

### 8.2 匹配

- `MatchCalculator` 用 Java 计算加权分数，AI 只做解释性分析，避免分数不可复现。
- `match_result` 绑定 `resume_version_id` + `job_id`，`match_detail` 记录技能状态 `MATCHED/PARTIAL/MISSING/UNCONFIRMED`。
- `MatchService` 推荐逻辑会读取历史投递的已读率、面试邀请率做小幅加权，并写入 `rankReasons`。

### 8.3 投递建议

- `MatchAdviceService` 先取已确认事实，调用 `generateAdvicePitch`，再做事实校验；未确认技能出现时回退确定性话术。

### 8.4 反向优化

- `OptimizationService` 管理 `optimization_task` 状态机，追问对话复用 `chat_session`，确认后生成 `profile_evidence` 候选。

## 9. 投递、提醒与反馈

### 9.1 投递状态机 `application/ApplicationStatus.java`

状态：`PENDING`、`APPLIED`、`READ`、`INTERVIEW_INVITED`、`INTERVIEWING`、`OFFERED`、`REJECTED`、`WITHDRAWN`、`FAILED`、`NO_RESPONSE`。

主要转移：

- `PENDING → APPLIED / WITHDRAWN`
- `APPLIED → READ / FAILED / WITHDRAWN / NO_RESPONSE`
- `READ → INTERVIEW_INVITED / NO_RESPONSE`
- `INTERVIEW_INVITED → INTERVIEWING / NO_RESPONSE`
- `INTERVIEWING → OFFERED / REJECTED`
- `NO_RESPONSE → READ / INTERVIEW_INVITED / REJECTED`
- 终态：`OFFERED`、`REJECTED`、`WITHDRAWN`、`FAILED`

非法转移统一 409。

### 9.2 提醒与历史

- `job_application.next_follow_up_at` 驱动待跟进列表。
- 未设置时默认“更新时间 + 3 天”。
- 每次状态变化写 `application_history`。
- 首条历史由创建时 `PENDING → APPLIED` 自动写入。

### 9.3 反馈

- `user_feedback` 记录 `NOT_INTERESTED/LOCATION_MISMATCH/SALARY_MISMATCH/SKILL_ALREADY_HAVE`。
- `FeedbackAnalyticsService` 输出漏斗、版本效果、反馈分布。

### 9.4 与 2.0 的差距

- `job_application` 无 `channel`（官网/招聘平台/内推/手动），无法支撑“招聘平台跳转 + 手动记录投递”。
- 无企业维度统计（企业名只是 `job.company` 文本）。
- 岗位与企业没有外键关系，企业信息库无法直接关联岗位。

## 10. 数据库现状

初始化脚本：`database/init.sql`，共 17 张表，无物理外键，仅索引。

| 表 | 关键字段 | 归属校验 | 2.0 关注点 |
|---|---|---|---|
| `sys_user` | `username`、`password_hash`、`email` | 自身 | 缺 `role` |
| `user_profile` | `user_id` 唯一、`skills` JSON、求职意向 | `user_id` | 可继续作为档案主表 |
| `profile_project` | `profile_id`、`tech_stack` JSON | 经 `profile` 间接 | 可复用 |
| `profile_experience` | `profile_id`、`company`、`achievements` | 经 `profile` 间接 | 可复用 |
| `profile_evidence` | `user_id`、`entity_type`、`confirmation_status` | `user_id` | 证据链核心，2.0 不改语义 |
| `chat_session` / `chat_message` | `user_id` / `session_id` | `user_id` | 2.0 区块对话可复用会话表 |
| `resume` | `user_id`、`current_version_id` | `user_id` | 需增加 `source_document_asset_id` 等 |
| `resume_version` | `resume_id`、`version_number` 唯一、`content` JSON | 经 `resume` | 不可变，禁止改语义 |
| `job` | `company`、`position`、`jd_content`、`source_type` | 无 | 缺 `company_id`、`created_by`、`visibility` |
| `job_requirement` | `job_id`、`skill`、`importance`、`weight` | 经 `job` | 可复用 |
| `match_result` | `resume_version_id`、`job_id`、`match_score` | 经版本/岗位 | 可复用 |
| `match_detail` | `match_result_id`、`skill`、`match_status` | 经结果 | 可复用 |
| `optimization_task` | `job_id`、`resume_version_id`、`chat_session_id` | 无显式 `user_id` | 建议补 `user_id` 或严格经会话校验 |
| `job_application` | `user_id`、`job_id`、`resume_version_id`、`status` | `user_id` | 缺 `channel` |
| `application_history` | `application_id`、`old_status`、`new_status` | 经投递 | 可复用 |
| `user_feedback` | `user_id`、`job_id`、`feedback_type` | `user_id` | 可复用 |

## 11. 测试覆盖

`backend/src/test/java` 共 27 个测试类：

- 认证：`AuthServiceTest`
- 档案与证据：`ProfileServiceTest`、`ProfileProjectServiceTest`、`ProfileExperienceServiceTest`、`EvidenceServiceTest`、`ExtractionEvidenceFactoryTest`、`FactGuardTest`、`FlexibleDateParserTest`、`CompletenessServiceTest`、`CompletenessCalculatorTest`
- 对话：`ChatServiceTest`
- 简历：`ResumeServiceTest`、`ResumeVersionServiceTest`、`ResumeGenerateServiceTest`、`ResumeExportServiceTest`、`ResumeDocxExporterTest`、`ResumeContentTest`、`ResumeContentAssemblerTest`、`ResumeAiServiceTest`
- 文件：`FileAnalysisServiceTest`、`DocumentTextExtractorTest`
- AI：`AIServiceTest`、`AIResponseParserTest`、`ResumeGenerationParserTest`
- 分析：`FeedbackAnalyticsServiceTest`
- 投递：`ApplicationStatusTest`
- 通用：`GlobalExceptionHandlerTest`

结论：v1 关键链路有单元测试兜底，但**没有 Controller 层集成测试**，也没有数据库迁移测试；2.0 新增表需要同时补 Service 单测与至少一条端到端冒烟脚本。

## 12. 2.0 能力差距清单

| 2.0 能力 | 现状 | 缺口 |
|---|---|---|
| 八步式简历创建 | 无 | 无草稿表、无分步接口、无自动保存 |
| 草稿恢复 | 无 | 仅 `resume_version` 不可变快照 |
| 区块级 AI 分析 | 无 | 现有 AI 只做整份生成/文件分析 |
| 局部修改 + 前后对比 + 确认 | 无 | 无 diff/候选/确认模型 |
| JD 截图 OCR | 有 OCR 能力 | 无截图上传接口、无解析任务 |
| DOCX 原简历解析 | 有文本抽取 | 无样式/版面保留，无文件持久化 |
| 定向简历生成 | 部分（匹配 + 生成） | 无“按 JD 定向重写 + 保留模板导出”链路 |
| 企业信息库 | 无 | 无 `company`/`company_recruitment` |
| 招聘平台跳转 | 无 | 无平台配置与投递渠道 |
| 管理端 | 无 | 无角色、无 `/api/admin/**` |
| 文件权限 | 无文件表 | 需新增 `document_asset` 与下载鉴权 |

## 13. 必须修复的旧问题

按风险排序：

1. **岗位写接口无权限**：`POST /api/jobs`、`POST /api/jobs/{id}/parse` 任何登录用户可调用。
2. **岗位全局污染**：用户手动新增岗位进入公共列表，无 `created_by`/`visibility`。
3. **注释与安全配置不一致**：`JobController` 声称岗位公开，实际需要 JWT。
4. **文件分析不持久化**：重复上传无法复用，也无法做“原文件保留”。
5. **`optimization_task` 无 `user_id`**：归属只能经会话/岗位间接推断，2.0 查询增多后容易漏校验。
6. **无 Controller 集成测试**：2.0 大量新增接口，仅 Service 单测不足以发现序列化/权限问题。
7. **前端 token 仅判存在**：属于前端问题，但后端 `/api/auth/me` 可作为启动校验入口，2.0 应接入。

## 14. 可直接复用的代码资产

- 统一响应与异常：`common/Result`、`common/PageResult`、`common/GlobalExceptionHandler`、`common/BusinessException`。
- 认证链路：`auth/JwtUtil`、`auth/JwtAuthFilter`、`config/SecurityConfig`。
- 档案与证据：`profile/service/ProfileDataService`、`EvidenceService`、`FactGuard`、`CompletenessCalculator`。
- 简历内容模型：`resume/vo/ResumeContent`、`ResumeContentAssembler`、`ResumeVersionService`。
- 文件能力：`DocumentTextExtractor`、`OcrService`。
- AI 链路：`ai/AIService`、`ai/PromptTemplate`、`ai/AIResponseParser`、`ai/DeepSeekClient`。
- 匹配与投递：`MatchCalculator`、`MatchService`、`MatchAdviceService`、`ApplicationService`、`ApplicationStatus`。
- 导出：`ResumeDocxExporter`。

## 15. 待确认事项

- 2.0 是否需要 PDF 导出？（当前只有 DOCX）
- 企业信息库是“内置种子数据 + 管理端导入”，已由用户确认；但管理端账号如何初始化仍需确定（首个管理员脚本 or 环境变量）。
- 原简历截图版面重建明确列为后续扩展，第一阶段只承诺文本抽取与 DOCX 保留。
- `optimization_task` 是否补 `user_id` 字段，需在数据库增量文档中定稿。
