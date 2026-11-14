# CareerFlow-AI 2.0 开发排期与验收

> 编制日期：2026-11-14
> 依据：`docs/2.0/frontend-audit.md`、`backend-audit.md`、`database-migration.md`、`api-v2.md`、`ui-redesign.md`
> 原则：每个 Feature 可独立开发、独立验收；先后端契约与数据库，再前端页面，最后联调与回归。

## 1. Feature 总览与依赖

```text
F0 基础设施（角色/迁移/存储）
 ├── F1 草稿后端 ── F2 八步前端
 ├── F3 区块修改后端 ── F4 三栏分析前端
 ├── F5 文件素材后端 ── F6 素材前端接入
 │                       └── F9 岗位定向（依赖 F5 + F3 + 现有匹配）
 ├── F7 企业库后端 ── F8 企业招聘前端
 └── F10 招聘平台与投递渠道（依赖 F7/F8 可选）
F11 UI 设计系统与布局壳（可与 F1-F10 并行，必须先于 F2/F4/F8 页面开发）
F12 联调、回归、文档同步（最后执行）
```

依赖要点：

- F2 依赖 F1 的草稿接口与 `resume_draft` 表。
- F4 依赖 F3 的 `resume_edit_history` 接口，以及 F11 的三栏容器。
- F9 依赖现有 `MatchService`、F5 的素材、F3 的候选修改能力。
- F8 依赖 F7 的企业与招聘接口。
- F11 不依赖新业务表，可最先启动，降低后续页面返工。

## 2. Feature 明细

### F0 基础设施与数据库迁移

| 项 | 内容 |
|---|---|
| 负责人建议 | 后端负责人（1 人） |
| 前置依赖 | 无 |
| 修改文件范围 | `database/migration/*.sql`、`docs/database.md`、`docs/api.md`、`backend/src/main/java/com/team/resume/config/SecurityConfig.java`、`auth/entity/SysUser.java`、`auth/vo/UserVO.java`、`application.yml`（文件存储目录） |
| API 依赖 | 无新增业务接口；`/api/admin/**` 权限规则 |
| 数据库依赖 | `sys_user.role`；`document_asset`、`company`、`company_recruitment`、`resume_draft`、`resume_edit_history` 建表；`job`、`resume`、`job_application`、`optimization_task` 加列 |
| 验收标准 | 迁移脚本可在空库与现有库重复执行；`sys_user.role` 默认 `USER`；指定管理员脚本可用；`/api/admin/**` 对 USER 返回 403；现有后端测试全绿 |
| 测试用例 | 1）空库执行迁移成功；2）已有 17 表库执行迁移成功且数据不丢；3）重复执行幂等；4）USER 调用 `/api/admin/companies` 返回 403；5）ADMIN 调用返回 200 |
| 风险 | `ALTER TABLE` 锁表；`optimization_task.user_id` 回填遗漏；管理员初始化方式未定 |

### F1 八步草稿后端

| 项 | 内容 |
|---|---|
| 负责人建议 | 后端负责人（1 人） |
| 前置依赖 | F0 |
| 修改文件范围 | 新增 `backend/.../resume/draft/`（controller/service/mapper/entity/dto/vo）；复用 `resume/service/ResumeVersionService`、`ResumeContentAssembler`、`profile/support/FactGuard` |
| API 依赖 | `POST/GET/PUT/DELETE /api/resume-drafts`、`POST /api/resume-drafts/{id}/complete` |
| 数据库依赖 | `resume_draft`、`resume`、`resume_version` |
| 验收标准 | 草稿可创建、列表、详情、保存、放弃；`revision` 冲突返回 409；完成草稿生成不可变版本；非本人 403 |
| 测试用例 | 1）创建草稿默认 step=1/revision=1；2）保存后 revision+1；3）旧 revision 保存 409；4）他人草稿 403；5）complete 生成 versionNumber=1；6）二次 complete 409 或拒绝；7）事务失败时草稿不被标记完成 |
| 风险 | 草稿 JSON 结构演进；并发保存；完成时 AI 文案事实校验 |

### F2 八步创建前端

| 项 | 内容 |
|---|---|
| 负责人建议 | 前端负责人（1 人） |
| 前置依赖 | F1、F11 |
| 修改文件范围 | `frontend/src/views/Resumes/Create/*`、`frontend/src/components/resume-wizard/*`、`frontend/src/api/resumeDraft.ts`、`frontend/src/router/index.ts`、`frontend/src/stores/resumeDraft.ts` |
| API 依赖 | `/api/resume-drafts/**`、`/api/document-assets`（证件照） |
| 数据库依赖 | 无直接依赖 |
| 验收标准 | 8 步可前进/后退；必填校验；3 秒节流自动保存；刷新后恢复草稿；右侧实时预览；完成生成版本并跳转简历列表 |
| 测试用例 | 1）步骤校验拦截；2）自动保存调用次数节流；3）刷新恢复；4）保存失败提示且不丢本地内容；5）完成跳转；6）模板切换预览更新 |
| 风险 | 自动保存与用户输入竞争；旧 `/resume` 兼容；预览性能 |

### F3 区块分析与修改历史后端

| 项 | 内容 |
|---|---|
| 负责人建议 | 后端负责人（1 人） |
| 前置依赖 | F0、F1 |
| 修改文件范围 | 新增 `backend/.../resume/edit/`；扩展 `ai/PromptTemplate`、`ai/AIService`（区块分析/改写/对话）；复用 `FactGuard` |
| API 依赖 | `POST /api/resume-drafts/{id}/sections/{sectionKey}/analyze`、`.../rewrite`、`.../chat`、`GET /api/resume-edit-history/{id}`、`POST .../confirm`、`.../reject`、`GET /api/resumes/{id}/edit-history` |
| 数据库依赖 | `resume_edit_history`、`resume_draft` |
| 验收标准 | 分析不落库；改写写 `PENDING`；确认后写回草稿并 `APPLIED`；拒绝后草稿不变；未确认技能被过滤并提示；重复确认 409 |
| 测试用例 | 1）分析返回区块级结果；2）改写生成 history；3）confirm 更新草稿 revision；4）reject 不改草稿；5）重复 confirm 409；6）他人 history 403；7）AI 返回空白时降级/重试 |
| 风险 | 提示词导致整份重写；diff 粒度过粗；AI 编造需 FactGuard 兜底 |

### F4 三栏简历分析前端

| 项 | 内容 |
|---|---|
| 负责人建议 | 前端负责人（1 人） |
| 前置依赖 | F3、F11 |
| 修改文件范围 | `frontend/src/views/Resumes/Analyze/*`、`components/resume-analysis/*`（SectionDirectory、SectionEditor、AiChatPanel、EditDiffPanel）、`api/resumeEdit.ts` |
| API 依赖 | F3 全部接口 |
| 数据库依赖 | 无 |
| 验收标准 | 左侧目录定位区块；中间内容可编辑；右侧对话；候选修改显示前后对比；确认后才生效；拒绝恢复原文 |
| 测试用例 | 1）目录切换；2）AI 候选出现但不自动应用；3）确认后中间内容更新；4）拒绝后内容不变；5）未保存修改离开提示；6）1024px 下抽屉模式 |
| 风险 | 长内容滚动同步；diff 展示可读性；对话上下文丢失（无状态设计） |

### F5 文件素材与 DOCX/JD 解析后端

| 项 | 内容 |
|---|---|
| 负责人建议 | 后端负责人（1 人） |
| 前置依赖 | F0 |
| 修改文件范围 | 新增 `backend/.../file/asset/`（controller/service/mapper/entity/storage）；扩展 `file/DocumentTextExtractor`（结构化解析）、`ai/AIService`（简历结构解析、JD 解析） |
| API 依赖 | `/api/document-assets` 全部；`POST /api/document-assets/{id}/parse-resume`、`.../parse-jd` |
| 数据库依赖 | `document_asset`；`job`、`job_requirement`（保存 JD 时） |
| 验收标准 | 上传落盘且元数据入库；下载鉴权；DOCX 解析出区块；JD 截图 OCR 后可保存为私有岗位；非法类型/超限返回 400 |
| 测试用例 | 1）上传 docx 成功；2）上传 exe 400；3）他人下载 403；4）删除后下载 404；5）DOCX 解析出至少 BASIC/EDUCATION；6）JD 图片 OCR 结果非空；7）`saveAsJob=true` 写私有岗位 |
| 风险 | 本地磁盘路径与部署卷；大文件内存；OCR 准确率；DOCX 复杂表格 |

### F6 素材前端接入

| 项 | 内容 |
|---|---|
| 负责人建议 | 前端负责人（1 人） |
| 前置依赖 | F5 |
| 修改文件范围 | `frontend/src/components/upload/*`（JdDropzone、PhotoUploader、ResumeImportPanel）、`frontend/src/api/documentAsset.ts`、简历创建/分析/定向相关页面 |
| API 依赖 | `/api/document-assets/**`、`parse-resume`、`parse-jd` |
| 数据库依赖 | 无 |
| 验收标准 | 拖拽上传、进度、错误提示；解析结果可预览并填入草稿；证件照可上传并显示 |
| 测试用例 | 1）类型校验；2）上传失败提示；3）解析结果填表；4）替换证件照；5）删除素材确认 |
| 风险 | 上传组件重复实现；解析等待时间较长需 loading |

### F7 企业信息库后端

| 项 | 内容 |
|---|---|
| 负责人建议 | 后端负责人（1 人） |
| 前置依赖 | F0 |
| 修改文件范围 | 新增 `backend/.../company/`；`database/seed/companies.json`（200+）；`config/SecurityConfig` 管理端规则 |
| API 依赖 | `GET /api/companies`、`GET /api/companies/{id}`、`GET /api/companies/{id}/recruitments`、`GET /api/companies/industries`、`/api/admin/companies/**` |
| 数据库依赖 | `company`、`company_recruitment`、`job.company_id` |
| 验收标准 | 200+ 企业可分页/筛选/搜索；详情含官网/招聘官网/来源/核验时间；管理端可导入更新；只返回 `ACTIVE` |
| 测试用例 | 1）分页 total 正确；2）行业筛选；3）关键词搜索；4）重复名称导入 upsert；5）USER 写接口 403；6）失效企业不返回 |
| 风险 | 企业数据准确性；重名；Logo 版权 |

### F8 企业招聘前端

| 项 | 内容 |
|---|---|
| 负责人建议 | 前端负责人（1 人） |
| 前置依赖 | F7、F11 |
| 修改文件范围 | `frontend/src/views/Companies/*`、`components/company/*`、`api/company.ts`、路由与菜单 |
| API 依赖 | F7 读接口 |
| 数据库依赖 | 无 |
| 验收标准 | 行业/城市筛选、搜索、企业卡片、详情抽屉、招聘时间线、外链跳转、最后核验时间展示 |
| 测试用例 | 1）筛选组合；2）搜索防抖；3）外链新窗口打开；4）空状态；5）加载骨架 |
| 风险 | 外链失效；数据过期误导 |

### F9 岗位定向简历

| 项 | 内容 |
|---|---|
| 负责人建议 | 全栈负责人（1 人） |
| 前置依赖 | F3、F5、现有 `MatchService` |
| 修改文件范围 | 新增 `backend/.../resume/tailor/`；扩展 `resume/ResumeService.export`；前端 `views/Resumes/Tailor/*`、`components/tailor/*` |
| API 依赖 | `POST /api/resumes/{id}/tailor/preview`、`.../apply`、扩展 `POST /api/resumes/{id}/export` |
| 数据库依赖 | `resume`、`resume_version`、`document_asset`、`match_result` |
| 验收标准 | 预览给出差距与区块计划；生成新版本且旧版本不变；原 DOCX 保留；导出可选保留模板 |
| 测试用例 | 1）预览不写版本；2）apply 版本号 +1；3）旧版本内容不变；4）未确认技能不写入；5）导出 DOCX 可打开；6）format=PDF 返回 400 |
| 风险 | 定向改写事实漂移；模板保留期望过高；大文件导出 |

### F10 招聘平台入口与投递渠道

| 项 | 内容 |
|---|---|
| 负责人建议 | 前端 1 人 + 后端 0.5 人 |
| 前置依赖 | F0（`job_application.channel`） |
| 修改文件范围 | `backend/.../job/PlatformController.java`（或配置常量）、`application/dto/CreateApplicationRequest.java`、`application/vo/ApplicationVO.java`；前端 `components/platform/PlatformLinks.vue`、投递表单 |
| API 依赖 | `GET /api/job-platforms`、扩展 `POST /api/applications` |
| 数据库依赖 | `job_application.channel`、`applied_url` |
| 验收标准 | 平台入口可跳转；投递记录可选择渠道；不实现自动投递 |
| 测试用例 | 1）平台列表返回；2）创建投递带 channel；3）旧请求默认 MANUAL；4）渠道统计正确 |
| 风险 | 用户误解为自动投递，需要文案澄清 |

### F11 UI 设计系统与布局壳

| 项 | 内容 |
|---|---|
| 负责人建议 | 前端负责人（1 人） |
| 前置依赖 | 无（建议最先启动） |
| 修改文件范围 | `frontend/src/assets/main.css`、`layouts/MainLayout.vue` → `AppShell`、`components/common/*`（PageHeader、FilterBar、EmptyState、LoadingSkeleton）、路由 meta |
| API 依赖 | 无 |
| 数据库依赖 | 无 |
| 验收标准 | 设计令牌完整；侧边栏分组与折叠；顶栏面包屑/通知；旧页面在新壳下功能不变 |
| 测试用例 | 1）现有 155 前端用例通过；2）折叠状态；3）菜单高亮；4）面包屑正确；5）1024px 响应式 |
| 风险 | 全局样式影响旧页面；Element Plus 变量冲突 |

### F12 联调、回归与文档同步

| 项 | 内容 |
|---|---|
| 负责人建议 | 测试/全栈 1 人 |
| 前置依赖 | F1–F11 |
| 修改文件范围 | `docs/api.md`、`docs/database.md`、`docs/2.0/*`（状态更新）、测试用例文件 |
| API 依赖 | 全部新增接口 |
| 数据库依赖 | 全部迁移脚本 |
| 验收标准 | 后端测试 + 前端测试全绿；`npm run build` 成功；迁移脚本在干净库可执行；6 份 2.0 文档与实现一致 |
| 测试用例 | 1）注册→建档→八步→生成→匹配→定向→投递→跟进全链路；2）越权访问矩阵；3）草稿并发保存；4）文件上传下载；5）企业筛选；6）旧功能回归 |
| 风险 | 文档与实现漂移；测试数据污染 |

## 3. 推荐开发顺序

1. **F11**（设计与布局壳，可并行启动）
2. **F0**（迁移 + 角色 + 存储）
3. **F1 → F2**（草稿与八步创建）
4. **F3 → F4**（区块分析与三栏）
5. **F5 → F6**（文件素材与解析）
6. **F7 → F8**（企业库与前端）
7. **F9**（岗位定向，依赖前述能力）
8. **F10**（平台入口与投递渠道）
9. **F12**（联调回归与文档同步）

## 4. 里程碑验收

### 里程碑 1：基础设施可用

- 迁移脚本可重复执行。
- 角色与 `/api/admin/**` 生效。
- 设计令牌与 AppShell 上线，旧页面不回归。

### 里程碑 2：八步创建闭环

- 新建草稿 → 8 步填写 → 自动保存 → 刷新恢复 → 完成生成版本。
- 版本不可变，旧版本仍可查看。

### 里程碑 3：AI 分析闭环

- 区块分析 → 候选修改 → 前后对比 → 用户确认 → 写入草稿。
- 拒绝不生效；未确认事实被过滤。

### 里程碑 4：文件与定向闭环

- 上传原 DOCX → 解析 → 生成定向版本 → 导出 DOCX。
- JD 截图 OCR → 保存私有岗位 → 定向匹配。

### 里程碑 5：企业招聘闭环

- 200+ 企业可筛选/搜索/跳转。
- 招聘信息含来源与核验时间。
- 投递可记录渠道。

### 里程碑 6：2.0 发布

- 全部测试通过，构建成功。
- 6 份文档与实现一致。
- 无功能删除，越权矩阵全部返回预期状态码。

## 5. 测试策略

- 后端：每个 Feature 至少 1 个 Service 单测类；新增 Controller 至少 1 条 MockMvc 集成测试。
- 前端：每个新页面至少 1 个组件测试；关键交互（自动保存、确认修改）必须有测试。
- 数据：迁移脚本在空库与存量库各执行一次；准备最小回归数据集。
- 安全：越权矩阵覆盖草稿、修改历史、素材、简历、投递、管理端。
- 回归：保留并运行现有 27 个后端测试类、25 个前端测试文件。

## 6. 全局风险清单

| 风险 | 等级 | 影响 | 缓解 |
|---|---|---|---|
| 一次性重写前端 | 高 | 功能回退、工期失控 | 分阶段，旧路由保留 |
| AI 编造经历 | 高 | 产品可信度 | FactGuard + 用户确认 + 确定性兜底 |
| 草稿并发覆盖 | 中 | 用户内容丢失 | revision 乐观锁 + 前端提示 |
| DOCX 模板保留期望过高 | 中 | 验收争议 | 明确第一阶段只保留文件 + 新版本导出 |
| 企业数据过期/不准 | 中 | 误导用户 | 展示来源与最后核验时间，管理端维护 |
| 越权访问新资源 | 高 | 数据泄露 | 所有新表带 `user_id`，统一归属校验 |
| 文件存储与部署 | 中 | 文件丢失 | 目录可配置、下载鉴权、软删除 |
| 迁移锁表 | 低 | 短暂不可用 | 低峰执行、幂等脚本 |

## 7. 文档同步要求

实施每个 Feature 时必须同步：

- 新增/修改表 → 更新 `docs/database.md`。
- 新增/修改接口 → 更新 `docs/api.md`。
- 与本文冲突时，以实际实现为准并回写 `docs/2.0/implementation-plan.md` 的状态。

## 8. 待确认

- 团队实际人数与角色分工（本文仅给角色建议）。
- 管理员账号初始化方式。
- 企业种子数据来源与版权确认。
- PDF 导出与原简历版面重建是否进入 2.0 范围。
