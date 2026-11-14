# CareerFlow-AI 2.0 前端审计

> 审计日期：2026-11-14
> 审计分支：`feature/2.0-audit`
> 审计方式：静态阅读 `frontend/src`、`package.json`、`vite.config.ts`，并运行现有前端测试。
> 范围：只审计现状，不修改业务代码。

## 1. 结论摘要

- 现有前端是完整的 v1 单页应用：登录注册、工作台、对话建档、个人档案、简历、岗位、匹配、投递、反馈分析共 10 个路由。
- 8 个业务页面均已实现，没有发现纯占位页面；但简历页是“编辑 + 预览 + 模板库 + 版本历史”的单页工作台，**不是 2.0 的八步式创建**。
- 模板渲染体系是前端最有价值的可复用资产：`src/components/resume-template/` 下有 215 个文件，包含模板清单、渲染器、适配器、推荐逻辑和第三方模板资产。
- 当前设计系统只有 8 个颜色变量，缺少字号、间距、圆角、阴影、层级等 token；页面大量使用 scoped CSS，2.0 统一视觉需要先补设计令牌。
- 当前没有企业招聘页、没有三栏 AI 简历分析、没有草稿自动保存、没有区块级修改确认、没有 JD 截图上传入口。
- 现有 25 个前端测试文件、155 个用例，覆盖 API 封装、核心组件、组合式函数和主要页面；可作为 2.0 重构的回归基线。

## 2. 路由与页面清单

路由定义：`frontend/src/router/index.ts`

| 路径 | name | 组件 | 权限 | 完成度 | 说明 |
|---|---|---|---|---|---|
| `/login` | Login | `views/Login/index.vue` | 公开 | 完整 | 登录表单、错误提示、redirect 回跳 |
| `/register` | Register | `views/Register/index.vue` | 公开 | 完整 | 注册后自动登录 |
| `/dashboard` | Dashboard | `views/Dashboard/index.vue` | 登录 | 完整 | 工作台数据、待跟进提醒、快捷入口 |
| `/chat` | Chat | `views/Chat/index.vue` | 登录 | 完整 | 会话列表、消息流、发送、证据确认 |
| `/profile` | Profile | `views/Profile/index.vue` | 登录 | 完整 | 档案编辑、项目/经历 CRUD、证据确认、完整度 |
| `/resume` | Resume | `views/Resume/index.vue` | 登录 | v1 完整 | 编辑器、模板库、版本历史、AI 生成、导出 |
| `/jobs` | Jobs | `views/Jobs/index.vue` | 登录 | 完整 | 列表、筛选、分页、详情、手动新增、JD 解析 |
| `/match/:jobId` | Match | `views/Match/index.vue` | 登录 | 完整 | 匹配分析、投递建议、一键投递、反向优化 |
| `/applications` | Applications | `views/Applications/index.vue` | 登录 | 完整 | 投递列表、筛选、详情、状态时间轴、待跟进 |
| `/analytics` | Analytics | `views/Analytics/index.vue` | 登录 | 完整 | 漏斗、版本效果、反馈分布 |
| `/:pathMatch(.*)*` | - | 重定向 `/dashboard` | - | - | 兜底 |

路由守卫仅做 `localStorage.token` 存在性判断（`router/index.ts:40-46`），不校验 token 有效性，也不做角色判断。

## 3. 页面功能现状

### 3.1 Dashboard

- 调用投递/跟进接口展示工作台数据与待跟进提醒。
- 快捷入口覆盖简历、岗位、投递、分析。
- 缺口：没有 2.0 的“下一步行动”工作流编排，没有企业招聘入口。

### 3.2 Chat

- 支持创建会话、加载历史消息、发送消息、后端多轮上下文。
- 与证据确认流程联动：AI 抽取的候选信息进入 `profile_evidence`，由用户确认。
- 缺口：对话只服务建档，不服务 2.0 的“区块级简历修改对话”。

### 3.3 Profile

- 档案基本信息编辑。
- 项目、实习/工作经历的新增、编辑、删除。
- 证据列表与确认/拒绝，完整度雷达。
- 缺口：没有证件照上传；没有软性信息区块；没有档案变更历史。

### 3.4 Resume

- 双栏：左侧结构化编辑器，右侧模板实时预览。
- 模板库、模板推荐、版本历史、AI 生成、手动保存新版本。
- DOCX 导出走后端 `POST /api/resumes/{id}/export`；PDF 导出使用浏览器打印能力（`utils/exportPdf.ts`），不是后端生成 PDF。
- 缺口：没有八步向导、没有自动保存/草稿恢复、没有区块目录、没有前后对比与确认、没有原 DOCX 上传/模板保留。

### 3.5 Jobs

- 岗位列表、关键词/城市/薪资筛选、分页、详情。
- 手动新增岗位、AI 解析 JD、跳转匹配分析。
- 岗位反馈入口（不感兴趣、地点不符、薪资不符、技能已掌握）。
- 缺口：没有企业库联动、没有 JD 截图 OCR 入口、没有招聘平台跳转。

### 3.6 Match

- 选择简历版本做匹配分析。
- 展示匹配分数、技能差距、投递建议、AI 话术。
- 一键投递、差距项发起反向优化。
- 缺口：没有“定向简历生成”入口。

### 3.7 Applications

- 投递列表/状态筛选/分页、状态更新、详情抽屉、状态时间轴、待跟进 Tab。
- 缺口：没有投递渠道/平台字段展示，没有外部平台跳转记录入口。

### 3.8 Analytics

- ECharts 投递漏斗、简历版本效果柱状图、偏好反馈分布饼图。
- 指标卡与推荐排序影响说明。
- 缺口：没有企业维度或岗位维度分析。

## 4. 组件组织与复用边界

### 4.1 顶层组件（`frontend/src/components/`）

| 组件 | 用途 | 2.0 处理建议 |
|---|---|---|
| `CompletenessRadar.vue` | 档案完整度雷达 | 直接复用 |
| `DocumentAnalyzer.vue` | 文档上传与 AI 分析 | 改造为资产上传 + 多用途解析入口 |
| `EvidenceConfirmDialog.vue` | AI 候选证据确认 | 直接复用，样式适配新设计 |
| `ExperienceDialog.vue` | 经历编辑弹窗 | 直接复用 |
| `PagePlaceholder.vue` | 页面占位 | 2.0 新页面开发期可复用，最终页面不应保留 |
| `ProfileInfoForm.vue` | 档案基本信息表单 | 直接复用 |
| `ProjectDialog.vue` | 项目编辑弹窗 | 直接复用 |

### 4.2 简历组件（`frontend/src/components/resume/`）

| 组件 | 用途 | 2.0 处理建议 |
|---|---|---|
| `ResumeEditor.vue` | 结构化简历编辑器 | 改造为八步表单的数据编辑层，保留字段渲染逻辑 |
| `TemplateGalleryDialog.vue` | 模板选择 | 直接复用 |
| `VersionHistoryDrawer.vue` | 版本历史 | 直接复用，增加“来源（AI/手动/导入/定向）” |

### 4.3 模板体系（`frontend/src/components/resume-template/`）

- `ResumeTemplateRenderer.vue`：统一渲染入口。
- `manifest.ts`、`adapter.ts`、`recommend.ts`、`util.ts`：模板清单、数据适配、推荐、工具函数。
- `vendor/`：第三方模板资产，目录总计 215 个文件。
- 结论：这是 2.0 实时预览和 PDF 打印的基础，不应重写；2.0 只在外部增加八步表单、区块分析和版本来源。

### 4.4 组合式函数与工具

- `composables/useProfile.ts`、`useResume.ts`、`useEvidenceConfirm.ts`：页面状态与业务动作复用。
- `utils/completenessChart.ts`、`evidenceDisplay.ts`、`exportPdf.ts`、`profileForms.ts`：图表、证据展示、打印、表单映射。
- 结论：可以直接复用；`useResume` 需要扩展草稿自动保存和版本来源。

## 5. 全局样式与布局

- 全局样式：`frontend/src/assets/main.css` 仅 28 行，定义 8 个颜色变量：
  `--cf-primary`、`--cf-primary-light`、`--cf-primary-dark`、`--cf-bg`、`--cf-text`、`--cf-text-secondary`、`--cf-card-bg`、`--cf-border`。
- 字体：`'Helvetica Neue', 'PingFang SC', 'Microsoft YaHei', sans-serif`。
- 布局：`layouts/MainLayout.vue`，固定 220px 侧边栏 + 60px 顶栏 + 主内容区；菜单 7 项，匹配分析不直接出现在菜单。
- 现状问题：
  - 无字号/行高/间距/圆角/阴影/层级 token。
  - 各页面 scoped CSS 重复实现页头、卡片、标签、空状态。
  - 侧边栏固定宽度，无折叠，移动端未适配。
- 2.0 建议：保留 `--cf-*` 命名，扩展 `--cf-space-*`、`--cf-radius-*`、`--cf-shadow-*`、`--cf-font-*`；先统一布局与页头，再逐页迁移。

## 6. 状态管理

- `stores/user.ts`：仅保存 `token`、`username`，初始化自 `localStorage`，`setLogin/logout` 同步本地存储。
- 缺口：
  - 没有当前用户详情 store（`GET /api/auth/me` 未在前端启动时调用）。
  - 没有草稿 store，自动保存状态只能放在页面局部。
  - 没有企业库筛选状态、没有简历分析对话状态。

## 7. Axios 请求与认证

- `api/request.ts`：
  - `baseURL = import.meta.env.VITE_API_BASE_URL || '/api'`，超时 60 秒。
  - 请求拦截自动注入 `Authorization: Bearer <token>`。
  - 响应拦截解包 `{ code, message, data }`；blob 响应返回 `{ blob, filename }`。
  - 401 时清理 `token/username`，非登录/注册页跳转 `/login`。
- API 文件：`analytics.ts`、`applications.ts`、`auth.ts`、`chat.ts`、`feedback.ts`、`jobs.ts`、`match.ts`、`optimization.ts`、`profile.ts`、`request.ts`、`resume.ts`。
- 缺口：没有统一业务错误提示封装，页面各自处理 `ElMessage`；没有请求取消/重试策略；没有上传进度封装。

## 8. 可复用资产清单

| 资产 | 位置 | 复用结论 |
|---|---|---|
| 模板渲染器 | `components/resume-template/ResumeTemplateRenderer.vue` | 直接复用 |
| 模板清单/推荐 | `components/resume-template/manifest.ts`、`recommend.ts` | 直接复用 |
| 版本历史抽屉 | `components/resume/VersionHistoryDrawer.vue` | 复用并扩展来源字段 |
| 简历编辑器 | `components/resume/ResumeEditor.vue` | 改造为八步表单编辑层 |
| 证据确认 | `components/EvidenceConfirmDialog.vue` | 直接复用 |
| 完整度雷达 | `components/CompletenessRadar.vue` | 直接复用 |
| PDF 打印 | `utils/exportPdf.ts` | 直接复用 |
| 请求封装 | `api/request.ts` | 直接复用，增加上传与错误提示扩展 |
| 页面布局 | `layouts/MainLayout.vue` | 重构，不重写路由机制 |

## 9. 2.0 前端缺口

1. 八步式简历创建：向导、步骤校验、自动保存、草稿恢复、实时预览、最终生成。
2. AI 简历分析：左侧区块目录、中间简历内容、右侧对话；区块独立分析。
3. 修改前后对比与用户确认：不能直接覆盖原简历。
4. 岗位定向简历：JD 截图上传、DOCX 原件上传、差距分析、定向生成、原模板导出。
5. 企业信息库：200+ 企业列表、行业筛选、搜索、官网/招聘官网、招聘信息、来源与核验时间。
6. 招聘平台跳转：平台入口与手动投递记录。
7. UI 整体重构：设计令牌、导航、工作台、简历中心、岗位中心、企业招聘、投递管理。
8. 证件照上传与预览。
9. 行政/管理端：企业数据导入入口（如果做独立页面）。

## 10. UI 重构影响评估

| 区域 | 处理方式 | 原因 |
|---|---|---|
| 路由与守卫 | 保留并扩展 | 现有结构清晰，新增企业/简历子路由即可 |
| MainLayout | 重写布局与导航 | 需要新导航分组、可折叠侧栏、顶部栏 |
| 全局样式 | 扩展设计令牌 | 现有变量不足以支撑统一设计 |
| Dashboard | 重做视觉与信息架构 | 2.0 工作台需要行动导向 |
| Profile | 保留逻辑，重做布局 | 表单和证据确认可直接复用 |
| Resume | 保留模板/版本能力，新增向导 | 现有单页编辑器不能承载八步流程 |
| Jobs | 保留列表/详情，增加企业联动 | 现有筛选与解析可复用 |
| Match | 保留分析/建议，增加定向生成 | 现有匹配结果结构可复用 |
| Applications | 保留逻辑，补充渠道字段 | 现有状态机完整 |
| Analytics | 保留 | 已满足 P1 分析 |
| Chat | 保留，新增分析对话模式 | 现有会话能力可扩展 |

## 11. 测试覆盖

现有 25 个测试文件、155 个用例：

- API：`applications`、`jobs`、`match`、`profile`、`request`、`resume`。
- 组件：`CompletenessRadar`、`EvidenceConfirmDialog`、`ExperienceDialog`、`ProfileInfoForm`、`ProjectDialog`。
- 模板体系：`adapter`、`manifest`、`recommend`、`ResumeTemplateRenderer`、`util`。
- 组合式函数：`useEvidenceConfirm`、`useProfile`、`useResume`。
- 工具：`completenessChart`、`evidenceDisplay`、`exportPdf`、`profileForms`。
- 页面：`Analytics`、`Applications`、`Chat` 证据、`Jobs`、`Match`、`Profile`、`Resume`。

缺口：没有八步向导、企业库、资产上传、修改对比的测试；没有 E2E 测试。

## 12. 技术债与风险

1. 设计令牌不足，页面 scoped 样式重复，UI 重构容易产生视觉不一致。
2. 简历页逻辑集中，拆八步时容易破坏模板预览与版本保存。
3. 没有草稿状态，自动保存需要新增前后端契约与并发控制。
4. 修改确认依赖区块级 diff，现有数据模型没有 sectionKey 概念。
5. 企业库是全新页面与新数据源，不能复用现有岗位表结构。
6. 文件上传目前只在内存解析，2.0 需要持久化、权限与清理策略。
7. 缺少 E2E 回归，UI 大改后依赖人工验收风险高。

## 13. 依赖与构建

- 关键依赖：Vue 3、Vite、TypeScript、Element Plus、Pinia、Vue Router、Axios、ECharts。
- 路径别名：`@` → `frontend/src`（`vite.config.ts`）。
- 开发代理：`/api` 转发到 `http://localhost:8080`。
- 构建脚本：`dev`、`build`（含 `vue-tsc`）、`test`（Vitest）。

## 14. 审计结论

前端 v1 的业务闭环是完整的，2.0 的主要工作不是修 bug，而是：

1. 在现有简历模板/版本能力之上增加“八步创建 + 草稿 + 区块分析 + 修改确认”。
2. 新增企业库与招聘平台入口。
3. 以设计令牌和布局重构为基础，逐页替换视觉，不做一次性重写。

现有模板体系、证据确认、档案表单、投递管理、反馈分析应优先复用，避免 2.0 重写导致功能回退。
