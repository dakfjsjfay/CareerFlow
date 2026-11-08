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

## 已知限制（如实声明）

| # | 限制 | 影响 | 计划 |
|---|---|---|---|
| 1 | job.salary 为 VARCHAR 文本 | 无法精确数值过滤薪资，SALARY_MISMATCH 规则只能粗略判断 | MVP 接受；P1 改结构化薪资字段 |
| 2 | profile_evidence 多态关联无外键 | 完整性靠后端校验 | 已写入开发规范 |
| 3 | 反馈样本量小 | 推荐排序的统计调整只作启发式，不宣称算法学习 | 答辩如实说明 |
| 4 | NO_RESPONSE 手动标记 | 无定时任务自动标记 | P1 加 @Scheduled |
| 5 | 教育经历单条存储 | 无法支持多段教育经历 | P1 拆 profile_education |

## 尚未解决的问题

1. DeepSeek 模型 ID：待 D1 实测（账号权限为准）
2. Spring Boot 3.x 精确小版本：D1 验证与 mybatis-plus-spring-boot3-starter 兼容性后确定
3. 简历 content JSON 的最终 schema：D6 前后端对齐定稿
4. 简历模板视觉设计（预览样式 = 导出样式）：D6 由 A 主导、C 提供内容
5. 种子 JD 具体内容：C 在 D8 前准备 10~20 条
6. 赛题截图、业务闭环 PDF 尚未放入 docs/reference/：待团队成员提供
7. 本地 Git 未初始化（远程仓库已建：https://github.com/dakfjsjfay/CareerFlow.git）：D1 完成连接与首次推送
