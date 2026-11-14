# CareerFlow-AI 2.0 UI 重构规划

> 审计日期：2026-11-14
> 基线：`frontend/src`（Vue 3 + Vite + TS + Element Plus + Pinia + Vue Router）
> 原则：保留现有业务功能，不为 UI 重构删除能力；分阶段迁移，不一次性重写。

## 1. 设计目标

1. 从“功能页面拼接”升级为统一视觉系统：同一套颜色、字号、间距、圆角、阴影、状态色。
2. 从“顶部菜单 + 内容区”升级为“侧边导航 + 顶部栏 + 工作区”的 SaaS 结构。
3. 为 2.0 新能力提供稳定容器：八步创建、三栏简历分析、企业招聘、定向简历。
4. 复用现有资产：`src/components/resume-template/` 模板体系、`CompletenessRadar`、`EvidenceConfirmDialog`、`DocumentAnalyzer` 等。

## 2. 现状回顾

- 布局：`src/layouts/MainLayout.vue`，220px 固定侧边栏 + 60px 顶栏 + 主内容区。
- 路由：10 个（登录、注册、工作台、对话、档案、简历、岗位、匹配、投递、分析）。
- 设计变量：`src/assets/main.css` 仅 8 个颜色变量，无字号/间距/圆角/阴影 token。
- 组件：7 个通用组件、3 个简历组件、`resume-template/` 215 个文件。
- 问题：页面 scoped CSS 各自为政；缺少页面级容器规范；移动端未适配；无统一空状态/加载态。

## 3. 新导航结构

保留现有 7 个菜单项，新增“企业招聘”，并把“简历中心”拆成“简历列表 + 八步创建 + 分析”的二级结构。

```text
工作台            /dashboard
简历中心          /resumes                （简历列表）
  ├─ 新建简历     /resumes/create         （八步创建）
  ├─ 简历分析     /resumes/:id/analyze    （三栏布局）
  └─ 岗位定向     /resumes/:id/tailor
岗位中心          /jobs
企业招聘          /companies              （新增）
投递管理          /applications
数据分析          /analytics
AI 对话建档       /chat
个人档案          /profile
```

说明：

- 现有 `/resume` 保留为重定向到 `/resumes`，避免书签失效。
- 现有 `/match/:jobId` 保留，作为岗位匹配详情页；定向简历从匹配页进入。
- 菜单层级不超过两级；二级菜单仅在“简历中心”展开。

### 3.1 侧边栏分组

| 分组 | 菜单 |
|---|---|
| 求职工作台 | 工作台、岗位中心、企业招聘、投递管理 |
| 我的简历 | 简历中心、AI 对话建档、个人档案 |
| 数据与反馈 | 数据分析 |

### 3.2 顶部栏

- 左侧：面包屑（由路由 meta 生成）。
- 右侧：全局搜索（岗位/企业/简历）、通知（待跟进提醒）、用户菜单（用户名、退出）。
- 保留现有退出逻辑：`useUserStore.logout()`。

## 4. 页面清单与布局

| 页面 | 路由 | 布局 | 复用组件 | 新增组件 |
|---|---|---|---|---|
| 工作台 | `/dashboard` | 单列 + 卡片网格 | 现有 Dashboard 卡片、待跟进提醒 | `NextActionCard`、`CompanyShortcut` |
| 简历列表 | `/resumes` | 列表 + 右侧说明 | 现有简历卡片逻辑 | `ResumeCard`、`EmptyState` |
| 八步创建 | `/resumes/create` | 顶部步骤条 + 中间表单 + 右侧实时预览 | `resume-template` 渲染器 | `StepNav`、`DraftAutoSaveTip`、`PhotoUploader` |
| 简历分析 | `/resumes/:id/analyze` | 三栏：左目录 / 中内容 / 右 AI 对话 | `resume-template`、`EvidenceConfirmDialog` | `SectionDirectory`、`SectionEditor`、`EditDiffPanel`、`AiChatPanel` |
| 岗位定向 | `/resumes/:id/tailor` | 左 JD / 中差距 / 右预览 | 匹配组件、模板渲染 | `JdDropzone`、`GapList`、`TailorPreview` |
| 岗位中心 | `/jobs` | 列表 + 筛选栏 | 现有 Jobs 页 | `FilterBar`、`JobCard` |
| 企业招聘 | `/companies` | 左侧行业筛选 + 企业卡片网格 + 详情抽屉 | 无 | `CompanyCard`、`CompanyDetailDrawer`、`RecruitmentTimeline` |
| 投递管理 | `/applications` | 表格 + 状态时间轴 | 现有投递页 | `StatusTimeline`、`ChannelTag` |
| 数据分析 | `/analytics` | 指标卡 + 图表 | 现有 ECharts 图表 | `MetricCard` |
| 对话建档 | `/chat` | 会话列表 + 消息流 | 现有 Chat 页 | `ChatBubble` |
| 个人档案 | `/profile` | 左侧锚点 + 右侧分区表单 | `ProfileInfoForm`、`ExperienceDialog`、`ProjectDialog`、`CompletenessRadar`、`EvidenceConfirmDialog` | 无 |
| 登录/注册 | `/login`、`/register` | 居中卡片 | 现有表单 | 统一 `AuthLayout` |

### 4.1 八步创建布局

```text
┌───────────────────────────────────────────────────────────┐
│ 顶部：步骤条（8 步）+ 保存状态 + 退出                      │
├───────────────────────────────┬───────────────────────────┤
│ 中间：当前步骤表单             │ 右侧：实时预览（模板渲染） │
│ - 基础信息 / 画像 / 经历 ...   │ - 复用 resume-template    │
│ - 上一步 / 下一步             │ - 可切换模板               │
└───────────────────────────────┴───────────────────────────┘
```

交互要求：

- 自动保存：3 秒节流调用 `PUT /api/resume-drafts/{id}`，显示“保存中/已保存/保存失败”。
- 草稿恢复：进入页面时若存在 `DRAFT` 草稿，提示“继续上次编辑 / 重新开始”。
- 步骤校验：当前步骤必填项未完成时“下一步”置灰并提示。
- 预览：复用现有模板渲染，不新建渲染器。

### 4.2 三栏简历分析布局

```text
┌──────────┬─────────────────────────────┬──────────────────┐
│ 左：目录  │ 中：区块内容                 │ 右：AI 对话       │
│ 基础信息  │ 当前区块编辑态               │ 消息流            │
│ 教育背景  │ 高亮缺失/待确认              │ 输入框            │
│ 项目经历  │                             │ 候选修改卡片      │
│ 专业技能  │                             │ [查看对比][应用]  │
└──────────┴─────────────────────────────┴──────────────────┘
```

交互要求：

- 点击左侧区块，中间滚动定位；右侧对话上下文切换为该区块。
- AI 返回候选修改时，右侧出现“前后对比”卡片；点击“查看对比”打开 `EditDiffPanel`。
- `EditDiffPanel` 展示 `beforeContent` / `afterContent`，用户可二次编辑，确认后调用 `confirm` 接口。
- **禁止** AI 结果直接覆盖原文；未确认前中间内容不变。

### 4.3 企业招聘布局

- 顶部搜索 + 行业筛选 + 城市筛选。
- 企业卡片：Logo、名称、行业、城市、招聘状态、最后核验时间、官网/招聘官网按钮。
- 点击卡片打开右侧抽屉：企业简介、招聘信息时间线、投递入口（跳转外部链接）。
- 明确展示“信息来源”和“最后核验时间”，避免过期信息误导。

## 5. 组件复用关系

### 5.1 现有组件直接复用

| 组件 | 复用位置 |
|---|---|
| `CompletenessRadar.vue` | 档案页、八步创建“画像与方向”步骤 |
| `EvidenceConfirmDialog.vue` | 档案页、对话页、简历分析页 |
| `ExperienceDialog.vue` | 档案页、八步创建“经历补充” |
| `ProjectDialog.vue` | 档案页、八步创建“经历补充” |
| `ProfileInfoForm.vue` | 档案页、八步创建“基础信息” |
| `DocumentAnalyzer.vue` | 简历分析页（导入原简历）、岗位定向页（JD 截图） |
| `ResumeEditor.vue` | 过渡期保留；2.0 新页面使用 `SectionEditor` |
| `TemplateGalleryDialog.vue` | 八步创建“预览微调”、简历分析页 |
| `VersionHistoryDrawer.vue` | 简历列表、简历分析页 |
| `resume-template/` 全部 | 八步预览、分析页中间内容、定向预览 |

### 5.2 新增基础组件

| 组件 | 职责 | 依赖 |
|---|---|---|
| `AppShell` | 统一布局壳（侧边栏 + 顶栏 + 内容） | `MainLayout` 演进 |
| `PageHeader` | 页面标题、描述、操作区 | 无 |
| `FilterBar` | 列表页统一筛选 | Element Plus |
| `EmptyState` | 空状态 | 无 |
| `LoadingSkeleton` | 骨架屏 | 无 |
| `EditDiffPanel` | 前后对比与确认 | `resume_edit_history` API |
| `SectionDirectory` | 左侧区块目录 | 无 |
| `AiChatPanel` | 右侧 AI 对话 | 区块 chat API |
| `StepNav` | 八步步骤条 | 无 |
| `PhotoUploader` | 证件照上传与裁剪 | `document_asset` API |
| `CompanyCard` / `CompanyDetailDrawer` | 企业展示 | 企业 API |
| `StatusTimeline` | 投递状态时间轴 | 现有历史 API |

### 5.3 组件层级

```text
AppShell
├── SideNav
├── TopBar
└── <router-view>
    ├── Dashboard
    ├── ResumeList
    ├── ResumeCreateWizard
    │   ├── StepNav
    │   ├── StepForm（8 个步骤组件）
    │   └── ResumePreview（resume-template）
    ├── ResumeAnalyze
    │   ├── SectionDirectory
    │   ├── SectionEditor
    │   └── AiChatPanel → EditDiffPanel
    ├── CompanyCenter
    │   ├── FilterBar
    │   └── CompanyCard → CompanyDetailDrawer
    └── 其他现有页面
```

## 6. 设计规范

### 6.1 设计令牌（扩展 `src/assets/main.css`）

保留现有 8 个颜色变量，新增：

```css
:root {
  /* 颜色：沿用现有 */
  --cf-primary: #2563eb;
  --cf-primary-light: #3b82f6;
  --cf-primary-dark: #1d4ed8;
  --cf-bg: #f5f7fb;
  --cf-text: #1f2937;
  --cf-text-secondary: #6b7280;
  --cf-card-bg: #ffffff;
  --cf-border: #e5e7eb;

  /* 状态色 */
  --cf-success: #16a34a;
  --cf-warning: #d97706;
  --cf-danger: #dc2626;
  --cf-info: #0891b2;

  /* 字号 */
  --cf-font-xs: 12px;
  --cf-font-sm: 13px;
  --cf-font-base: 14px;
  --cf-font-lg: 16px;
  --cf-font-xl: 20px;
  --cf-font-2xl: 24px;
  --cf-font-3xl: 30px;

  /* 间距 */
  --cf-space-1: 4px;
  --cf-space-2: 8px;
  --cf-space-3: 12px;
  --cf-space-4: 16px;
  --cf-space-5: 24px;
  --cf-space-6: 32px;
  --cf-space-7: 48px;

  /* 圆角 */
  --cf-radius-sm: 6px;
  --cf-radius-md: 10px;
  --cf-radius-lg: 16px;

  /* 阴影 */
  --cf-shadow-sm: 0 1px 2px rgba(15, 23, 42, 0.06);
  --cf-shadow-md: 0 4px 16px rgba(15, 23, 42, 0.08);
  --cf-shadow-lg: 0 12px 32px rgba(15, 23, 42, 0.12);

  /* 布局 */
  --cf-sidebar-width: 232px;
  --cf-header-height: 60px;
  --cf-content-max: 1440px;
}
```

### 6.2 布局规范

- 侧边栏：232px，折叠后 64px；当前项高亮使用 `--cf-primary` 背景 10% 透明度 + 左侧 3px 竖条。
- 顶栏：60px，白底，底部 1px 边框。
- 内容区：`padding: 24px`，最大宽度 1440px，居中。
- 卡片：`--cf-card-bg` + `--cf-radius-lg` + `--cf-shadow-sm`，悬浮时 `--cf-shadow-md`。
- 表格：斑马纹关闭，行高 48px，操作列固定在右侧。

### 6.3 字体与层级

- 字体栈沿用 `'Helvetica Neue', 'PingFang SC', 'Microsoft YaHei', sans-serif`。
- 页面标题 `--cf-font-2xl` + 600；卡片标题 `--cf-font-lg` + 600；正文 `--cf-font-base`。
- 辅助文本使用 `--cf-text-secondary`，不使用低于 12px 的字号。

### 6.4 状态与反馈

- 加载：列表用 `LoadingSkeleton`，按钮用 Element Plus loading。
- 空状态：统一 `EmptyState`，包含图标、说明、主操作按钮。
- 错误：接口错误用 `ElMessage.error`，表单错误就近展示。
- 保存状态：草稿页顶部固定展示“保存中/已保存/保存失败”。

### 6.5 响应式

- ≥1280px：完整侧边栏 + 三栏分析。
- 1024–1279px：侧边栏折叠为图标；三栏改为“目录抽屉 + 内容 + 对话抽屉”。
- <1024px：单列；八步创建隐藏实时预览，改为“预览”按钮弹窗。

## 7. 迁移顺序

分 5 个阶段，每阶段可独立上线、可回滚。

### 阶段 1：设计令牌与布局壳

- 扩展 `main.css` 设计令牌。
- 把 `MainLayout.vue` 演进为 `AppShell`（侧边栏分组、顶栏面包屑、通知入口）。
- 新增 `PageHeader`、`EmptyState`、`LoadingSkeleton`、`FilterBar`。
- 不改业务页面逻辑，仅替换容器与样式。

验收：所有旧页面在新布局下功能不变，现有 155 个前端用例通过。

### 阶段 2：简历中心与八步创建

- 新增 `/resumes`、`/resumes/create`。
- 开发 `StepNav`、8 个步骤组件、`DraftAutoSaveTip`、`PhotoUploader`。
- 复用 `resume-template` 做实时预览。
- 保留旧 `/resume` 重定向。

验收：八步可保存/恢复/生成版本；刷新页面草稿不丢。

### 阶段 3：三栏简历分析

- 新增 `/resumes/:id/analyze`。
- 开发 `SectionDirectory`、`SectionEditor`、`AiChatPanel`、`EditDiffPanel`。
- 接入区块分析、改写、确认/拒绝接口。

验收：AI 修改必须经用户确认才生效；前后对比可查看；拒绝后原文不变。

### 阶段 4：企业招聘与岗位定向

- 新增 `/companies`、`/resumes/:id/tailor`。
- 开发 `CompanyCard`、`CompanyDetailDrawer`、`RecruitmentTimeline`、`JdDropzone`、`GapList`。
- 岗位页增加企业关联展示与投递渠道选择。

验收：企业筛选/搜索/跳转可用；定向预览与生成可用；投递记录带渠道。

### 阶段 5：收尾与一致性

- 统一空状态、加载态、错误提示。
- 旧页面（Dashboard、Profile、Jobs、Match、Applications、Analytics、Chat）逐个套用新 token 与组件。
- 响应式适配。
- 视觉走查与回归测试。

验收：全部页面视觉一致；无功能删除；旧用例 + 新增用例全部通过。

## 8. 风险与约束

| 风险 | 缓解 |
|---|---|
| 一次性重写导致功能回退 | 分 5 阶段，旧路由保留，逐页替换 |
| 模板渲染器改动影响导出 | 前端模板与后端 DOCX 导出解耦，导出继续用 `ResumeDocxExporter` |
| 三栏布局在小屏不可用 | 1024px 以下改为抽屉/单列 |
| 设计令牌与 Element Plus 冲突 | 通过 CSS 变量覆盖，不直接改组件源码 |
| 草稿自动保存覆盖 | 前端节流 + 后端 `revision` 乐观锁，冲突提示刷新 |
| 企业数据过期 | 强制展示“最后核验时间”和“信息来源” |

## 9. 明确不做

- 不更换 UI 框架，不引入 Tailwind/Ant Design Vue。
- 不重写 `resume-template` 渲染体系。
- 不做移动端原生 App 适配。
- 不为了视觉删除现有功能入口。
- 不在 UI 重构阶段修改后端业务逻辑（除必要的接口对接）。

## 10. 待确认

- 侧边栏是否需要折叠持久化（localStorage）。
- 八步创建是否允许“跳过步骤”。
- 三栏分析页是否需要“全屏对比”模式。
- 企业 Logo 使用外链还是统一素材存储。
