# 决策记录（Decision Record）：CareerFlow-AI 智能求职助手

> 版本：v1.0（2026-11-08）｜记录两轮设计的全部关键技术决策及理由，供答辩与后续审查追溯。
> 记录格式：决策 → 方案对比 → 最终选择 → 理由 → 影响。

## D1 架构模式：模块化单体（保留）

- 方案对比：微服务 / 模块化单体 / 无分包单体
- 最终选择：**模块化单体**（一个 Spring Boot 工程，按业务分包 auth/chat/profile/resume/job/matching/optimization/application/ai/common）
- 理由：3 人 14 天；微服务引入服务间通信与分布式事务，复杂度远超收益；按业务分包保留清晰边界，后续可拆
- 来源：Chat 2 决策，两轮审查均确认保留

## D2 后端分包方式：按业务模块（而非按层）

- 方案对比：按层分包（controller/service/mapper 全平铺）/ 按业务模块分包（每模块内含自己的 controller/service/mapper）
- 最终选择：**按业务模块分包**
- 理由：模块职责清晰、单人负责单模块时冲突少（模块负责人制度）
- 来源：Chat 2 §2.3 决策

## D3 认证方案：Spring Security + JWT + BCrypt（二轮审查确定）

- 方案对比：轻量 JWT 拦截器 + BCrypt（Claude 初版推荐）/ Spring Security + JWT + BCrypt
- 最终选择：**Spring Security + JWT + BCrypt**，采用最简配置（无 session、放行 /api/auth/** 与 /api/health、其余走 JWT filter）
- 理由：导师审查确定采用；Spring Security 为标准方案，答辩可讲；最简配置控制学习成本
- 影响：D2 实现时由成员 B 负责，注意 Spring Boot 3.x 的 SecurityFilterChain 新写法

## D4 导出方案：OpenHTMLtoPDF + Apache POI XWPF（二轮审查确定）

- 方案对比：前端打印 / docx4j / OpenHTMLtoPDF + POI XWPF
- 最终选择：**PDF = OpenHTMLtoPDF**（HTML 模板 → PDF，与前端预览风格统一）；**Word = Apache POI XWPF**（.docx）
- 理由：导师审查确定；Java 后端统一导出，演示稳定，不依赖浏览器
- 影响：Word 导出 MIME 必须为 `application/vnd.openxmlformats-officedocument.wordprocessingml.document`（非 application/msword）；导出必须显式指定 versionId

## D5 数据库：17 张表（两轮演进）

Chat 2 的 15 张 → +profile_experience（赛题要求工作/实习经历）→ +user_feedback（二轮审查：偏好反馈回流）＝ 17 张。

**明确不建的表：**
- profile_field：完整度规则 Java 硬编码
- reminder：提醒由 job_application 的 next/last_follow_up_at 计算
- profile_education：MVP 教育经历暂存 user_profile（P1 拆）
- feedback（Chat 1 原方案）：拆分为状态备注（application_history.note）+ 偏好反馈（user_feedback），职责清晰

## D6 证据表多态关联（二轮审查修订）

- profile_evidence 增加 entity_type + entity_id（PROFILE/SKILL/PROJECT/EXPERIENCE），保留 source_type + source_id 追溯原始来源
- 取舍：多态关联无外键约束 → 后端必须校验实体归属（user_id 一致）
- 未确认数据只存在于 profile_evidence（PENDING），确认后才落实体表

## D7 简历版本并发（二轮审查修订）

- resume 增加 current_version_id（查询当前版本免排序）
- resume_version 增加 UNIQUE(resume_id, version_number)
- 生成新版本事务：锁定 resume → max(version_number)+1 → INSERT → UPDATE current_version_id → 提交
- 旧版本永久只读

## D8 确认流程（二轮审查明确）

- AI 抽取 → profile_evidence（PENDING）→ 用户确认/拒绝（POST /api/profile/evidence/confirm）
- 自动确认规则：基础信息/教育经历明确时可自动 CONFIRMED；技能/项目/工作经历必须用户确认（归因风险）
- D4 实现暂不启用自动确认：所有抽取项统一先 PENDING，见 D15
- REJECTED 数据永不进入档案与简历

## D9 投递话术（赛题要求，二轮审查定 MVP 策略）

- POST /api/match/advice 实时生成（匹配度分析 + 话术），MVP 不落库
- P1：话术独立保存策略
- 话术只用已确认且有证据的信息，防编造

## D10 提醒机制（二轮审查修订）

- job_application 增加 next_follow_up_at / last_follow_up_at，不建 reminder 表
- 触发：状态 ∈ {APPLIED, READ, INTERVIEW_INVITED, NO_RESPONSE} 且 due ≤ now（due = next_follow_up_at 或 updated_at+3 天）
- 处理：POST /api/applications/{id}/follow-up 标记已跟进并重排下次提醒；状态更新自动重置计时

## D11 反馈回流（赛题要求，二轮审查确定可解释规则）

- 状态类反馈 → application_history.note；偏好类反馈 → user_feedback 表
- 推荐排序叠加可解释规则权重：NOT_INTERESTED 同岗位/公司 ×0.5 后置、LOCATION_MISMATCH 城市后置、SALARY_MISMATCH 低薪后置、SKILL_ALREADY_HAVE 提示补证据
- **不引入机器学习训练**；答辩如实声明为统计启发式

## D12 开发方式：纵向切片（二轮审查修订）

- 按业务功能切片（认证 → 建档 → 简历+导出 → 匹配+优化 → 投递 → 提醒+反馈），每切片前后端同周期联调
- 禁止前端做完所有页面、后端做完所有接口后再集中联调

## D13 AI 接入

- DeepSeek API，OpenAI 兼容接口（https://api.deepseek.com）
- 模型 ID 以 API 账号权限为准（候选 deepseek-v4-pro / deepseek-flash），D1 实测确定
- 独立 ai 包封装（DeepSeekClient/AIService/PromptTemplate/AIResponseParser），业务模块不直接发 HTTP
- JSON Output；结构校验 + 事实校验；超时 60s、重试 1 次、失败 502
- 默认关闭 thinking（`thinking.type=disabled`）并限制 `max_tokens=2500`：实测同一 Prompt 从 18.1s 降到 3.0s；需要更高质量时可通过 `DEEPSEEK_THINKING_ENABLED=true` 开启
- 空白响应兜底：模型 HTTP 200 但 `content` 为空白时（实测部分长上下文稳定出现，`finish_reason=stop`），后端追加一条「必须输出非空 reply」的约束消息重试一次；仍失败按 502 返回，并记录 `finish_reason`/`completion_tokens`/`content_length` 便于排查

## D14 文件导入分析：本地解析 + 本地 OCR（D3 增强）

- 支持 Word / PPT / PDF / 图片 / 文本 / Markdown / ZIP / 常见核心代码文件
- PDF 用 PDFBox：优先抽取文本层；扫描件渲染前 5 页后做 OCR
- 图片用 Tess4J + tesseract 原生库，`chi_sim+eng` 语言包随应用打包，不调用外部视觉/OCR 服务
- 实测当前 DeepSeek 模型对 `image_url` 返回 `[Unsupported Image]`，因此不把图片直接交给大模型
- 文件只在内存中解析、不落盘；限制单文件 10MB、总大小 60MB、正文合计 2 万字符、ZIP 200 个文件/20MB、ZIP 内最多 OCR 3 个图片/PDF
- 一次最多 6 个非 ZIP 文件协同分析；ZIP 必须单独上传，避免重复解压和上下文失控
- 分析结果只返回待确认候选，不直接写入档案/简历

## D15 证据链落库与确认（D4 实现）

- 证据粒度：基础信息/教育经历/求职意向按「字段一条」，技能按「技能一条」，项目/工作经历按「整条经历一条」
- PROJECT/EXPERIENCE 的 evidence_content 保存该条候选 JSON；确认时创建实体并回填 entity_id。这样确认一条证据就创建一条完整实体，避免 project_name NOT NULL 字段被拆散
- 确认接口先全量校验（不存在 404 / 非本人 403 / 已处理 409），再用「PENDING 条件更新」防止并发重复确认；实体写入与证据状态更新在同一事务中
- 事实校验前移：落 PENDING 前，后端逐项校验抽取值能否在本轮用户原话中找到依据（归一化包含 / 数字匹配 / 2-gram 覆盖率 ≥ 60%），不通过则丢弃；Prompt 明确禁止把示例内容当作真实信息输出
- 确认策略：D4 统一先 PENDING，不启用自动确认；基础信息/教育经历也需用户显式确认，最大化证据链可信度
- 历史感知来源归属：sources 只包含用户消息（按时间正序），每个值从最近一条用户消息向前查找能支撑它的原话，source_id 指向该消息；助手消息不作为证据来源；同一事实重复出现取最近一次
- 去重：同一用户、同一字段、同样内容的 PENDING/CONFIRMED 证据不重复生成；REJECTED 后再次提到可重新生成待确认
- 影响：D5 档案页与完整度只读 CONFIRMED 数据；简历生成只能使用 CONFIRMED 证据

## D16 档案页与完整度（D5 实现）

- 档案读取只返回已确认数据；无档案时 GET 返回空结构而非 404，便于前端渲染
- 手动编辑：PUT /api/profile 每个实际变更字段写一条 MANUAL + CONFIRMED 证据；技能用单条 SKILL 证据记录整体替换
- 项目/经历 CRUD：新增/修改写 MANUAL 证据（修改保留旧证据，形成审计轨迹）；删除实体时一并删除其证据
- 完整度：技能/项目/经历采用分级计分（0/50/75/100 等），求职意向 expected_salary 计半分；总分取 6 项平均
- “无经历”标记：明确表述触发 PENDING 的 PROFILE/no_experience 证据，确认后经历维度计为完整
- 非目标：简历生成、导出、匹配等后续切片

## D17 简历内容模型与模板接入（D6 实现）

- `resume_version.content` 定稿为 `ResumeContent`：`meta / personalInfo / jobIntention / education / skills / projects / experiences / selfEvaluation`，项目与经历增加 `highlights` 存放 AI 亮点。
- 模板 ID 存在 `content.meta.templateId`，不新增数据库字段；换模板 = 生成新版本（`TEMPLATE_SWITCH`），旧版本保持原模板。
- 前端从 resume-design（MIT）引入 20 套完整模板 JSON，只复制渲染依赖（模块组件、共享子组件、页面布局、样式 hook），改写为只读渲染器；内容通过 adapter 映射到模板 `dataSource`，空模块隐藏，禁止模板示例数据泄露。
- 取舍：只读渲染器不复用设计器的拖拽/选中/store，降低耦合；模板样式固定，不支持在线改色改字体。

## D18 导出方案调整（D6 实现，替代 D4 的 PDF 部分）

- **PDF = 前端浏览器打印**：预览区固定 820px 宽，A4 打印 + `break-inside: avoid`，与模板样式 100% 一致，不依赖后端 HTML 渲染。
- **DOCX = 后端 Apache POI XWPF**：单栏专业排版，不套模板样式，但内容完整、中文正常。
- 原因：20 套 resume-design 模板大量使用 flex，OpenHTMLtoPDF 不支持 flex；若后端复刻 20 套样式，工作量和走样风险都不可接受。
- 影响：`POST /api/resumes/{id}/export` 只接受 `format=DOCX`；PDF 不经过后端。导出仍必须指定 `versionId`，旧版本可随时预览/导出。

## 已知限制（如实声明）

| # | 限制 | 影响 | 计划 |
|---|---|---|---|
| 1 | job.salary 为 VARCHAR 文本 | 无法精确数值过滤薪资，SALARY_MISMATCH 规则只能粗略判断 | MVP 接受；P1 改结构化薪资字段 |
| 2 | profile_evidence 多态关联无外键 | 完整性靠后端校验 | 已写入开发规范 |
| 3 | 反馈样本量小 | 推荐排序的统计调整只作启发式，不宣称算法学习 | 答辩如实说明 |
| 4 | NO_RESPONSE 手动标记 | 无定时任务自动标记 | P1 加 @Scheduled |
| 5 | 教育经历单条存储 | 无法支持多段教育经历 | P1 拆 profile_education |
| 6 | 图片/扫描件依赖 OCR 质量 | 模糊、倾斜、复杂排版可能识别不全 | 提示改用清晰图片或 Word/文本；P1 评估更好的 OCR |
| 7 | 扫描版 PDF 仅 OCR 前 5 页 | 长 PDF 后半部分不参与分析 | MVP 接受；按需提高上限 |

## 尚未解决的问题

1. DeepSeek 模型 ID：待 D1 实测（账号权限为准）
2. Spring Boot 3.x 精确小版本：D1 验证与 mybatis-plus-spring-boot3-starter 兼容性后确定
3. 简历 content JSON 的最终 schema：✅ D6 已定稿（见 D17 与 docs/api.md §7.4）
4. 简历模板视觉设计：✅ D6 已定稿，接入 20 套 resume-design 模板；PDF 预览/导出样式一致，DOCX 为独立单栏排版（见 D17/D18）
5. 种子 JD 具体内容：C 在 D8 前准备 10~20 条
6. 赛题截图、业务闭环 PDF 尚未放入 docs/reference/：待团队成员提供
7. 本地 Git 未初始化（远程仓库已建：https://github.com/dakfjsjfay/CareerFlow.git）：D1 完成连接与首次推送
