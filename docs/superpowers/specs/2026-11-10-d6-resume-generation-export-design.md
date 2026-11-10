# D6/D7 简历生成与导出设计

> 版本：v1.0（2026-11-10）
> 范围：D6 简历生成 + D7 预览与导出
> 依据：docs/requirements.md、docs/api.md、docs/database.md、docs/decision-record.md、docs/development-plan.md

## 1. 意图与目标

在已完成的「对话建档 → 证据确认 → 个人档案」链路上，补齐赛题要求的简历产出闭环：

- 从**已确认**的档案数据生成结构化简历，AI 只做文案整理，不编造事实。
- 提供 20 套完整简历模板（来自 resume-design / 猫步简历），支持按目标岗位推荐、实时预览、切换模板。
- 每次生成/修改都产生新版本，旧版本永久只读，但可随时预览、导出。
- 导出 PDF（浏览器打印，模板样式与预览一致）和 DOCX（后端 Apache POI）。

完成后，演示链路可以从「张同学对话建档」直接走到「生成简历 → 选模板 → 导出 PDF → 投递」。

## 2. 范围

### 2.1 目标（Goals）

1. 后端新增 `resume` 模块：创建、列表、详情、AI 生成新版本、手动保存新版本、版本列表、指定版本、导出 DOCX。
2. 前端 `/resume` 页从占位页升级为完整简历工作台：模板推荐、模板画廊、内容编辑、实时预览、版本历史、导出。
3. 接入 resume-design 的 20 套完整模板及其渲染组件，封装为 CareerFlow 内部的模板渲染层。
4. 建立 `ResumeContent` 内容模型，内容与样式分离，模板只负责展示。
5. 同步更新 `docs/api.md`、`docs/decision-record.md`、`docs/architecture.md` 中受影响的部分。

### 2.2 非目标（Non-goals）

- 不做真实招聘平台 API、爬虫、多智能体。
- 不做模板在线设计器（拖拽、改色、改字体）；模板是固定样式，用户只能选，不能改样式。
- 不做 DOCX 与模板视觉 1:1 还原；DOCX 是干净的单栏专业排版。
- 不做岗位定向生成（`targetJobId` 的 JD 适配）——匹配与反向优化属于 D8-D10，本次只透传并存储。
- 不做教育经历拆表、多段教育经历（沿用 `user_profile` 单条教育经历）。
- 不引入新的数据库表或字段；模板 ID 存在 `resume_version.content.meta` 中。

### 2.3 反目标（Anti-goals）

- 简历中出现用户未确认的信息，或模板自带的示例数据（如「化简」「小猪课堂」）泄露到成品。
- 覆盖、删除任何历史版本。
- 前端直接调用 DeepSeek。
- 为了导出 PDF 在后端重写 20 套模板；这是明确不采用的路线。

## 3. 约束

1. 技术栈不变：Vue 3 + Vite + TS + Element Plus + Pinia；Java 17 + Spring Boot 3 + MyBatis-Plus；MySQL 8。
2. 所有业务接口校验 `user_id` 数据隔离；简历/版本不属于当前用户返回 403。
3. 简历只使用 `CONFIRMED` 的档案数据；`PENDING`/`REJECTED` 永不进入内容。
4. 返回格式统一 `{ "code": 200, "message": "success", "data": {} }`。
5. 模板与组件来自 resume-design，MIT 协议；必须在仓库保留 LICENSE 与来源说明。
6. 文档中的 `doc` 统一实现为 `.docx`（decision-record D4），不是旧版 `.doc`。
7. 修改后必须执行 `mvn test`、`npm test`、`npm run build`。

## 4. 现状与关键发现

### 4.1 CareerFlow 现状

- 后端已有 auth / chat / profile / ai / file 模块；`resume` 模块尚未实现，`resume`、`resume_version` 表已建。
- `pom.xml` 已有 `poi-ooxml`，没有 OpenHTMLtoPDF。
- 前端 `/resume` 仍是 `PagePlaceholder`；已有 profile API、证据确认、档案页。
- 个人档案可提供：`realName / phone / school / major / degree / graduationYear / skills[] / targetPosition / targetCity / expectedSalary / projects[] / experiences[]`。

### 4.2 resume-design 模板架构

- 「模板」不是单个 Vue 组件，而是一份 JSON（`template_json`），包含：
  - `componentsTree[]`：约 13 个模块，每个模块有 `componentName`（如 `BaseInfo_3`）、`props`（字段显隐）、`css`（模块整体样式）、`customCss`（模块内部样式）、`dataSource`（数据占位）、`customProps`（布局/是否显示）。
  - `props.pageName`：页面布局，取值 `BasePage / LeftRightPage / Page_1`。
  - `css` / `customCss`：页面级样式（宽度 820px、左右栏样式等）。
- 渲染组件位于 `src/views/createTemplate/designer/modules/<Type>/<Type>_<N>.vue`，共享子组件位于 `modules/components/`（26 个），模块标题位于 `modules/ModuleTitle/`。
- 组件样式完全由 JSON 的 `css/customCss` 驱动，数据完全由 `dataSource` 驱动，适合做只读渲染。
- 组件依赖：
  - `useGetBoxStyle` / `useGetCustomStyle` 两个样式 hook。
  - 全局 `<svg-icon>` 组件 + `public/static/iconfont/iconfont.js` 图标雪碧图（约 580KB）。
  - `v-dompurify-html` 指令。
  - `sass`（组件使用 `<style lang="scss">`）。
  - `formatDate`、`textToNumber`、`numberToText` 三个小工具函数。
  - 4 张背景图：`baseInfo_2.png / baseInfo_5.png / baseInfo_6.png / title_bg.png`。

### 4.3 选定的 20 套模板

从 110 套模板中按「布局签名」去重后选出 20 套，覆盖单栏、双栏、经典、简洁、创意、技术岗、行业岗：

| # | 模板 ID | 名称 | 页面布局 | 风格/岗位 |
|---|---|---|---|---|
| 1 | 6746fe472f0052cb6a9365aa | 双色标签经典简历模板 | BasePage | 经典 |
| 2 | 674701372f0052cb6a93660b | 深蓝两栏大气时尚简历模板 | LeftRightPage | 时尚/双栏 |
| 3 | 674704bd2f0052cb6a9366c5 | 简约轻快简历模板 | BasePage | 简洁 |
| 4 | 674705f82f0052cb6a9366f9 | 曲线标签经典简历模板 | BasePage | 创意/经典 |
| 5 | 674708c52f0052cb6a9367bb | 双色典雅简洁简历模板 | BasePage | 简洁 |
| 6 | 67470a292f0052cb6a936802 | 红色水印经典简历模板 | BasePage | 经典 |
| 7 | 67470f622f0052cb6a9368a7 | 灰色经典简历模板 | BasePage | 经典 |
| 8 | 674ef7a1499a3d6818e5f3e9 | 专业大气简洁简历模板 | Page_1 | 简洁/特殊布局 |
| 9 | 675038c5c016bbf823ea30eb | 科技感创意简历模板 | BasePage | 创意/技术 |
| 10 | 67503cd2c016bbf823ea3130 | 线条标签创意简历模板 | BasePage | 创意 |
| 11 | 67503de5c016bbf823ea317d | 水墨中国风创意简历模板 | BasePage | 创意 |
| 12 | 67505d9ac016bbf823ea3497 | 灰白黄时尚简历模板 | LeftRightPage | 时尚/双栏 |
| 13 | 6756f8ac8f0b47d968a0e98c | 贸易买手社招简历模板 | LeftRightPage | 经典/双栏 |
| 14 | 67584681ad08c962be2e9da4 | 会计优秀校招简历模板 | BasePage | 简洁/会计 |
| 15 | 676015175e80d518fa0c1985 | 算法工程师内推简历模板 | BasePage | 经典/后端开发 |
| 16 | 67615d45e4dde1f4d601c8c3 | 新媒体运营实习生简历模板 | BasePage | 简洁/新媒体运营 |
| 17 | 676d4a717017be6da0dfef21 | 交互设计实习生简历模板 | BasePage | 简洁/交互设计 |
| 18 | 67b5e1793370f88ef9258aa6 | Java 开发工程师校招岗位简历模板 | BasePage | 经典/后端开发 |
| 19 | 6787c0990a3df5adfa37071d | 经典简洁简历模板 | BasePage | 简洁 |
| 20 | 6880c88c20e3189056304800 | 文案策划应届生简历模版 | BasePage | 经典/文案策划 |

这 20 套共用到 127 个模块组件（另有 26 个共享子组件、若干 ModuleTitle 组件）。实现时按依赖闭包复制，不复制未使用的模板变体。

## 5. 总体方案

```
┌────────────────────────── 前端 Vue 3 ──────────────────────────┐
│ /resume 简历工作台                                              │
│  ├─ 模板推荐 / 模板画廊（20 套 manifest）                        │
│  ├─ 内容编辑器（基本信息、求职意向、教育、技能、项目、经历）      │
│  ├─ ResumeTemplateRenderer（resume-design 只读渲染层）           │
│  ├─ 版本历史                                                     │
│  └─ 导出：PDF=浏览器打印；DOCX=后端接口                          │
└───────────────┬─────────────────────────────────────────────────┘
                │ /api/resumes/*
┌───────────────▼────────── 后端 Spring Boot ─────────────────────┐
│ resume 模块                                                      │
│  ├─ ResumeController / ResumeService                             │
│  ├─ ResumeContentAssembler（CONFIRMED 档案 → 结构化内容）        │
│  ├─ ResumeAiService（AI 自我评价/亮点 + 事实校验 + 兜底）        │
│  ├─ ResumeVersionService（锁定事务 + 版本号唯一）                │
│  └─ ResumeDocxExporter（POI XWPF）                               │
└───────────────┬─────────────────────────────────────────────────┘
                │
        MySQL（resume / resume_version，结构不变）
```

数据流：

1. 用户在对话/档案页确认数据（D4/D5 已完成）。
2. `/resume` 页创建简历主体，选择或使用推荐模板，点击「AI 生成」。
3. 后端读取 CONFIRMED 档案 → Java 确定性组装 → AI 生成自我评价/亮点 → 事实校验 → 写入 `resume_version`（含 `content.meta.templateId`）。
4. 前端拿到 content + templateId，套模板 JSON 渲染预览。
5. 用户编辑内容 → 保存为新版本；切换模板 → 内容不变，生成带新 `templateId` 的新版本。
6. 导出 PDF（前端打印当前版本）或 DOCX（后端按 versionId 生成）。

## 6. 简历内容模型（D6 定稿）

`resume_version.content` 使用以下 JSON（字段缺失时前端隐藏对应模块，后端生成时尽量填满）：

```json
{
  "meta": {
    "templateId": "6746fe472f0052cb6a9365aa",
    "templateName": "双色标签经典简历模板",
    "generatedAt": "2026-11-10T12:00:00+08:00",
    "source": "AI",
    "generationReason": "INITIAL",
    "targetJobId": null,
    "warnings": []
  },
  "personalInfo": {
    "realName": "张同学",
    "phone": "13800000000",
    "email": null,
    "city": null,
    "avatar": null,
    "school": "深圳大学",
    "major": "软件工程",
    "degree": "本科",
    "graduationYear": 2028,
    "yearsOfExperience": 0,
    "abstract": "软件工程在读，掌握 Java 与 MySQL，有学生信息管理系统项目经验。"
  },
  "jobIntention": {
    "targetPosition": "Java 开发实习生",
    "targetCity": "深圳",
    "expectedSalary": "面议"
  },
  "education": [
    {
      "school": "深圳大学",
      "major": "软件工程",
      "degree": "本科",
      "graduationYear": 2028,
      "startDate": null,
      "endDate": null
    }
  ],
  "skills": ["Java", "MySQL", "Vue"],
  "projects": [
    {
      "projectName": "学生信息管理系统",
      "role": "后端开发",
      "description": "基于 Java SE 与 MySQL 实现学生信息增删改查。",
      "techStack": ["Java", "MySQL"],
      "highlights": [],
      "startDate": "2025-03-01",
      "endDate": "2025-06-30"
    }
  ],
  "experiences": [
    {
      "company": "某科技有限公司",
      "position": "后端实习生",
      "type": "INTERNSHIP",
      "description": "参与接口开发与联调。",
      "achievements": "完成 3 个接口。",
      "highlights": [],
      "startDate": "2025-07-01",
      "endDate": "2025-09-30"
    }
  ],
  "selfEvaluation": "具备扎实的 Java 基础，熟悉 MySQL 与前端 Vue，能独立完成小型系统开发。"
}
```

约定：

- `personalInfo.email / city / avatar` 目前档案没有，允许为 `null`；模板中对应字段隐藏。后续档案补充字段时无需改内容模型。
- `experiences[].type` 由 Java 计算：`position` 或 `company` 包含「实习 / Intern」→ `INTERNSHIP`，否则 `WORK`。用于填充实习经验/工作经验模块。
- `skills` 为字符串数组；档案没有熟练度，模板中的技能熟练度滑条隐藏，只展示技能名。
- `projects[].highlights` / `experiences[].highlights` 存放 AI 基于已确认事实整理出的亮点句；为空时模板只展示 `description` / `achievements`。
- `personalInfo.abstract` 取 `selfEvaluation` 的第一句（最多 60 字），不单独调用 AI。
- `education` 目前最多一条（`user_profile` 单条教育经历）。
- `meta.templateId` 是版本的一部分；换模板必须产生新版本。
- 内容模型定稿后写入 `docs/api.md` §7，后续 D10 反向优化复用。

## 7. 模板系统设计（前端）

### 7.1 目录结构

```
frontend/src/components/resume-template/
├── vendor/                          # 从 resume-design 复制的渲染闭包（MIT）
│   ├── modules/                     # 只保留 20 套模板用到的 <Type>_<N>.vue
│   ├── components/                  # 26 个共享子组件
│   ├── ModuleTitle/                 # 用到的模块标题组件
│   ├── hooks/useGetBoxStyle.ts      # 由 useGetStyle 简化而来
│   ├── hooks/useGetCustomStyle.ts
│   └── types/IHJNewSchema.ts
├── pages/                           # 只读页面布局（自己写，不复用设计器）
│   ├── BasePage.vue
│   ├── LeftRightPage.vue
│   └── Page_1.vue
├── templates/                       # 20 份模板 JSON
│   ├── 6746fe47....json
│   └── ...
├── manifest.ts                      # id / 名称 / 风格 / 行业 / 岗位 / 封面 / 推荐关键词
├── adapter.ts                       # ResumeContent → template_json（含隐藏空模块）
├── recommend.ts                     # 按目标岗位推荐模板
├── registerModules.ts               # componentName → 组件映射
├── ResumeTemplateRenderer.vue       # 只读渲染入口
├── ReadOnlyModuleBox.vue            # 渲染模块 + 标题 slot
├── SvgIcon.vue                      # 从 resume-design 复制
├── util.ts                          # formatDate / textToNumber / numberToText
├── LICENSE-resume-design.md         # MIT 许可证与来源说明
└── README.md                        # 接入说明与升级方式
```

同时新增：

- `frontend/src/api/resume.ts`：接口与类型。
- `frontend/src/composables/useResume.ts`：页面状态与动作（避免为单页引入新的 Pinia store）。
- `frontend/src/utils/exportPdf.ts`：打印导出。
- `frontend/public/static/iconfont/iconfont.js`：图标雪碧图。
- `frontend/src/assets/resume-template/`：4 张背景图 + 20 张模板封面。

### 7.2 渲染器改造（相对 resume-design 的差异）

只读渲染器不复用设计器的 `BasePage/LeftRightPage/Page_1`、`ModuleBox`、`registerModules`，因为原版依赖 Pinia store、`vuedraggable`、选中态和编辑操作。改写后：

- 页面布局组件接收 `template: IHJNewSchema` 作为 prop，遍历 `componentsTree` 渲染模块。
- `LeftRightPage` 按 `customProps.layout === 'left' | 'right'` 分左右两栏，保留模板 JSON 中的 `left/right` 自定义样式。
- `ReadOnlyModuleBox` 只在 `customProps.showModule !== false` 时渲染；保留 `#module-title` slot 机制。
- `useGetBoxStyle` 简化为纯 `computed`：删除主题色/字体联动 watch（模板样式是固定的），保留 `css/customCss` 到内联样式的映射。
- 删除对 `@/store`、`@/router`、`@/http`、`vue-router`、`pinia`、`vuedraggable`、`lodash`、`moment` 的依赖。
- `formatDate` 用原生日期格式化替换 moment；`textToNumber/numberToText` 原样复制。
- `v-dompurify-html`：新增依赖 `vue-dompurify-html`，在 `main.ts` 注册；所有用户文本先转义再包裹 HTML，由 DOMPurify 兜底。
- `<svg-icon>` 注册为全局组件或模板渲染器局部组件；`iconfont.js` 通过 script 标签加载（只在简历页首次渲染时加载一次）。

### 7.3 模板数据适配（adapter.ts）

适配器从零构造 `dataSource`，**不保留模板 JSON 里的任何占位示例值**，避免示例数据泄露。

| 模板模块 | dataSource 字段 | 来源 |
|---|---|---|
| `BaseInfo` | `name` | `personalInfo.realName` |
| | `phoneNumber` | `personalInfo.phone` |
| | `email` | `personalInfo.email`（空则隐藏） |
| | `degree` | `personalInfo.degree` |
| | `address` | `personalInfo.city`（空则隐藏） |
| | `avatar` | `personalInfo.avatar`（空则隐藏） |
| | `age` | 无数据，始终隐藏 |
| | `workService` | `personalInfo.yearsOfExperience`（0 则隐藏） |
| | `abstract` | `personalInfo.abstract`（空则隐藏） |
| `Custom` | `name` / `abstract` | 姓名 / 一句话简介 |
| `ResumeTitle` | `title` | `resumeName` |
| `JobIntention` | `intendedPositions` | `jobIntention.targetPosition` |
| | `intendedCity` | `jobIntention.targetCity` |
| | `expectSalary` | `jobIntention.expectedSalary` |
| | `jobStatus` / `jobSearchType` | 无数据，隐藏 |
| `EduBackground` | `list[]` | `education[]`：`date=[startDate,endDate]`，`schoolName`，`specialized`，`degree`，`majorCourse` 留空并隐藏 |
| `SkillSpecialties` | `list[]` | `skills[]`：`skillName=skill`，`proficiency` 留空并隐藏，`introduce` 按组件变体使用技能名 |
| `WorkExperience` | `list[]` | `experiences[type=WORK]`：`companyName/hosts/contentTtile/jobContent/date` |
| `InternshipExperience` | `list[]` | `experiences[type=INTERNSHIP]` |
| `ProjectExperience` | `list[]` | `projects[]`：`companyName=项目名`，`posts=角色`，`contentTtile=项目名`，`jobContent`=描述+技术栈+highlights |
| `SelfEvaluation` | `content` | `selfEvaluation` |
| `CampusExperience` / `Awards` / `Hobbies` / `WorksDisplay` | 无数据 | 模块 `customProps.showModule=false` |

列表项的 `jobContent` 是 `Array<{ content: { value: string } }>`，适配器把 `description`、`achievements`、`techStack` 转成若干条 HTML 段落（先转义，再包 `<p>`）。

隐藏规则：

1. 列表为空 → `customProps.showModule = false`。
2. 单值字段为空 → 对应 `props.<field>.show = false`。
3. 整个模块所有字段为空（如求职意向）→ `customProps.showModule = false`。
4. 模板原本 `showModule=false` 的模块保持隐藏。
5. 适配完成后做一次断言：渲染树中不得出现模板占位文本（`化简`、`小猪课堂`、`1584775297@qq.com`、`028-83032406` 等）。

### 7.4 模板推荐（recommend.ts）

基于 `profile.targetPosition`（没有则用 `content.jobIntention.targetPosition`）与模板 `manifest` 的 `post/industry/style/title` 关键词打分：

- 岗位关键词匹配（Java/后端/前端/算法/测试/产品/运营/新媒体/会计/设计/文案/外贸）→ +40。
- 行业匹配（互联网通信/职能支持/广告传媒）→ +20。
- 通用模板（无岗位标签）→ +10，保证冷启动也有候选。
- 同分时优先布局多样性：最多 1 套双栏、1 套创意、1 套经典。
- 返回前 3 套；其余按风格分组展示在画廊。

### 7.5 模板封面与体积

- 20 张封面从 maobucv 的 `template_cover` 下载到 `frontend/src/assets/resume-template/covers/`，避免运行时热链。
- 模板 JSON 通过 `import.meta.glob('./templates/*.json')` 按需加载，不在首屏加载全部 20 份。
- 图标雪碧图 580KB，作为静态资源懒加载，不进入首屏 bundle。

## 8. 后端设计

### 8.1 包结构（com.team.resume.resume）

```
resume/
├── controller/ResumeController.java
├── dto/
│   ├── CreateResumeRequest.java
│   ├── GenerateResumeRequest.java
│   ├── SaveVersionRequest.java
│   └── ExportResumeRequest.java
├── entity/Resume.java
├── entity/ResumeVersion.java
├── mapper/ResumeMapper.java
├── mapper/ResumeVersionMapper.java
├── service/
│   ├── ResumeService.java
│   ├── ResumeContentAssembler.java
│   ├── ResumeAiService.java
│   ├── ResumeVersionService.java
│   └── ResumeDocxExporter.java
└── vo/
    ├── ResumeSummaryVO.java
    ├── ResumeDetailVO.java
    ├── ResumeVersionVO.java
    └── ResumeGenerateResultVO.java
```

AI 调用统一走 `com.team.resume.ai.AIService`，不直接发 HTTP。

### 8.2 接口契约（详见 docs/api.md §7 的更新）

| 接口 | 方法 | 说明 |
|---|---|---|
| `/api/resumes` | POST | 创建简历主体 |
| `/api/resumes` | GET | 简历列表 |
| `/api/resumes/{id}` | GET | 简历详情（含当前版本） |
| `/api/resumes/{id}/generate` | POST | AI 生成新版本 |
| `/api/resumes/{id}/versions` | POST | **新增**：手动保存新版本（编辑/换模板/恢复） |
| `/api/resumes/{id}/versions` | GET | 版本列表（不含 content） |
| `/api/resumes/{id}/versions/{versionId}` | GET | 指定版本内容 |
| `/api/resumes/{id}/export` | POST | 导出 DOCX（**仅 DOCX**；PDF 由前端生成） |

请求/响应要点：

- 创建：`{ "resumeName": "我的简历" }`，名称可选，默认「我的简历」，长度 ≤100。
- 生成：`{ "templateId": "...", "targetJobId": null, "generationReason": "INITIAL" }`；`templateId` 必填且 ≤64；`generationReason` ∈ `INITIAL / JOB_TARGETED / OPTIMIZATION / REGENERATE`。
- 手动保存：`{ "content": {...}, "templateId": "...", "generationReason": "MANUAL_EDIT" }`；`generationReason` ∈ `MANUAL_EDIT / TEMPLATE_SWITCH / RESTORE`；服务端覆盖 `content.meta.templateId`、`generatedAt`、`source=MANUAL`。
- 导出：`{ "versionId": 3, "format": "DOCX" }`；`format` 只接受 `DOCX`，其他值 400。响应为二进制流，`Content-Type: application/vnd.openxmlformats-officedocument.wordprocessingml.document`，`Content-Disposition: attachment; filename*=UTF-8''...`。
- 所有响应外层保持 `{ code, message, data }`；导出接口是唯一例外（二进制流）。

校验与错误：

- 简历/版本不存在 → 404。
- 简历/版本不属于当前用户 → 403。
- `versionId` 缺失 → 400。
- 生成时档案不完整（无任何 CONFIRMED 教育/项目/经历/技能）→ 400，提示先完善档案；基础信息缺失只警告，不阻断。
- AI 调用失败（超时/HTTP 错误/JSON 解析失败）→ 502，不创建版本。
- AI 输出事实校验失败 → 用确定性兜底文案，版本照常创建，`meta.warnings` 记录。

### 8.3 生成流程

```
ResumeService.generate(resumeId, req)
 ├─ 校验简历归属（user_id）
 ├─ ProfileDataService 读取 CONFIRMED 档案（基础信息、教育、技能、项目、经历）
 ├─ ResumeContentAssembler.assemble(profile, templateId, resumeName)
 │    ├─ 基础信息/求职意向/教育/技能/项目/经历：Java 原样组装
 │    ├─ 计算 yearsOfExperience：经历起止月份累计（无经历=0）
 │    ├─ 计算 experiences[].type：WORK / INTERNSHIP
 │    └─ 生成确定性兜底 selfEvaluation（只用已有事实）
 ├─ ResumeAiService.enhance(content, profile)
 │    ├─ Prompt：只允许改写 selfEvaluation 与经历/项目亮点，不得新增事实
 │    ├─ 返回 JSON：{ selfEvaluation, experienceHighlights[{index,highlights[]}], projectHighlights[{index,highlights[]}] }
 │    ├─ FactGuard 逐句校验（2-gram 覆盖率 / 数字匹配）
 │    ├─ 通过校验的亮点写入 experiences[i].highlights / projects[i].highlights
 │    └─ 不通过的句子丢弃；全部不通过 → 保留兜底文案，warning=AI_FACT_CHECK_FALLBACK
 ├─ ResumeVersionService.createVersion(resumeId, content, reason)
 │    ├─ 锁定 resume 行（SELECT ... FOR UPDATE）
 │    ├─ versionNumber = max(version_number)+1
 │    ├─ INSERT resume_version
 │    ├─ UPDATE resume.current_version_id
 │    └─ 提交
 └─ 返回 { resumeId, versionId, versionNumber, content }
```

AI 事实校验复用 `profile.support.FactGuard` 的思路；由于它是 profile 包内工具，若跨包不便，抽到 `common` 或复制一个 `ResumeFactGuard`（优先复用/抽取，不复制两份逻辑）。

AI 失败与事实校验失败的区别：

- AI 调用本身失败 → 502，让用户重试，不写半成品。
- AI 返回了内容但事实校验不通过 → 兜底 + warning，保证演示不中断。

### 8.4 DOCX 导出

`ResumeDocxExporter` 使用 Apache POI XWPF：

- A4 页面，上下 2cm、左右 2cm 页边距。
- 标题：姓名（18pt 加粗）+ 联系方式行（电话 / 邮箱 / 城市，空值省略）。
- 段落顺序：求职意向 → 教育背景 → 技能特长 → 项目经历 → 工作/实习经历 → 自我评价。
- 每个标题 14pt 加粗，段后加细分隔线（可用底边框或下划线段落）。
- 项目/经历：第一行「项目名 | 角色 | 起止时间」，下面每条描述/成果/亮点一个「• 」段落。
- 中文字体：`微软雅黑`，同时设置 `FontCharRange.eastAsia` 保证 Word 中正常显示。
- 空章节跳过；内容为空时导出仍成功（至少有姓名标题）。
- 文件名：`{resumeName}-v{versionNumber}.docx`，去除 Windows 非法字符。

### 8.5 数据隔离与事务

- 所有查询先按 `id` 取简历，再比较 `user_id`；不匹配返回 403。
- 版本查询必须校验 `resume_id` 与 `versionId` 同时匹配。
- 生成/保存版本使用事务；`resume` 行锁保证并发下版本号不重复；`UNIQUE(resume_id, version_number)` 作为最后防线。
- 旧版本没有任何 UPDATE/DELETE 入口。

## 9. 前端页面设计

### 9.1 页面结构

`/resume` 单页工作台：

```
┌─────────────────────────────────────────────────────────────┐
│ 顶部：简历选择 | 版本选择 | 模板库 | AI 生成 | 导出 PDF/DOCX  │
├──────────────────────┬──────────────────────────────────────┤
│ 左：内容编辑          │ 右：实时预览（820px 模板画布）        │
│  基本信息             │                                      │
│  求职意向             │   <ResumeTemplateRenderer />         │
│  教育背景             │                                      │
│  技能特长             │                                      │
│  项目经历             │                                      │
│  工作/实习经历        │                                      │
│  自我评价             │                                      │
├──────────────────────┴──────────────────────────────────────┤
│ 版本历史抽屉（时间、版本号、原因、模板、预览/导出）           │
└─────────────────────────────────────────────────────────────┘
```

空状态：没有简历主体时，展示「创建我的第一份简历」；创建后若没有版本，展示「选择模板并生成」。

### 9.2 交互流程

1. **创建简历**：输入名称（可选）→ `POST /api/resumes`。
2. **生成**：点击「AI 生成」→ 弹出模板推荐（前 3 套 + 查看全部）→ 确认 → `POST /generate`。生成中显示进度/骨架，失败展示后端 message。
3. **预览**：选中版本后，`adapter.ts` 将 content 转成模板 JSON，`ResumeTemplateRenderer` 渲染。
4. **编辑**：左侧表单直接改 content 的副本；有改动时「保存为新版本」高亮。保存 → `POST /versions`（`MANUAL_EDIT`）→ 刷新版本列表。
5. **换模板**：打开模板库 → 点击模板即时预览（不落库）→「应用此模板」→ `POST /versions`（`TEMPLATE_SWITCH`，content 不变，仅 meta.templateId 变化）→ 新版本。
6. **版本历史**：列出所有版本；点击切换预览；每行有「导出 PDF / 导出 DOCX」。旧版本只读。
7. **导出 PDF**：`exportPdf.ts` 把预览区标记为打印区，设置 `document.title`，调用 `window.print()`；用户在打印对话框选择「另存为 PDF」。导出后恢复标题。
8. **导出 DOCX**：`POST /export`，按 `Content-Disposition` 文件名下载 blob。

### 9.3 打印样式

`views/Resume/print.css`：

```css
@media print {
  body * { visibility: hidden !important; }
  #resume-print-area, #resume-print-area * { visibility: visible !important; }
  #resume-print-area {
    position: absolute;
    left: 0;
    top: 0;
    margin: 0;
    transform: scale(0.968); /* 820px → A4 96dpi 794px */
    transform-origin: top left;
  }
  @page { size: A4; margin: 0; }
  .module-box { break-inside: avoid; }
}
```

- 打印区固定 820px 宽，与模板设计宽度一致。
- 模块级 `break-inside: avoid`，避免标题与内容被拆页。
- 若浏览器打印分页仍不理想，P1 再引入 `html2canvas + jsPDF` 作为备选；本期以浏览器打印为准。

### 9.4 前端类型与 API

`api/resume.ts` 定义与后端一致的 `ResumeSummary`、`ResumeDetail`、`ResumeVersion`、`ResumeContent`、`GenerateResumePayload`、`SaveVersionPayload` 等类型；所有接口走现有 `request.ts`，导出接口使用 `responseType: 'blob'`。

## 10. 文档与契约变更

| 文件 | 变更 |
|---|---|
| `docs/api.md` §7 | 重写简历接口：补充创建/生成/手动版本/导出请求响应；`format` 改为仅 DOCX；补充 `ResumeContent` schema；注明 PDF 由前端生成 |
| `docs/decision-record.md` | 新增 D17「简历内容模型与模板接入」、D18「导出方案调整：PDF 前端打印 + DOCX 后端 POI」；更新「尚未解决的问题」第 3、4 项为已定稿 |
| `docs/architecture.md` | 补充 resume 模块的模板渲染层、前端模板资源目录；说明 PDF/DOCX 分工 |
| `docs/database.md` | 不变（templateId 存在 content JSON，不加字段） |
| `docs/acceptance.md` | 补充 D6/D7 验收项：20 套模板、推荐、版本不可变、旧版本导出、DOCX 打开正常 |

## 11. 测试策略

### 11.1 后端

- `ResumeContentAssemblerTest`：
  - 只使用 CONFIRMED 数据；PENDING/REJECTED 不出现。
  - `experiences[].type` 的 WORK/INTERNSHIP 判定。
  - `yearsOfExperience` 计算。
  - 空档案时抛出业务异常（400）。
- `ResumeVersionServiceTest`：
  - 版本号递增；并发/重复版本号由唯一约束兜底。
  - 旧版本不可修改（服务层无 update 旧版本的方法）。
- `ResumeDocxExporterTest`：
  - 输出非空、以 `PK` 开头、可被 POI 重新读取。
  - 空章节跳过。
- `ResumeControllerTest`（MockMvc 或服务层）：
  - 非本人简历 403；不存在 404；versionId 缺失 400；format 非 DOCX 400。
- 现有 `mvn test` 全绿。

### 11.2 前端

- `adapter.test.ts`：
  - 字段映射正确；空列表隐藏模块；缺失字段隐藏。
  - 渲染树中不出现模板占位文本。
- `recommend.test.ts`：
  - Java/后端岗位推荐算法/Java 模板；运营岗位推荐新媒体模板；无目标岗位时返回通用模板。
- 组件冒烟测试：
  - 用一套样例 content 挂载 20 套模板，断言渲染出的模块数量 > 0、无异常。
- `npm run build` 通过（含 vue-tsc）。

### 11.3 手工验收

- Chrome/Edge 打印预览：A4 单页/多页分页正常，颜色与预览一致。
- Word 打开 DOCX：中文不乱码、章节完整、无空白页。
- 20 套模板逐一点击，检查空数据下不出现示例内容、无溢出遮挡。

## 12. 风险与缓解

| 风险 | 影响 | 缓解 |
|---|---|---|
| 组件复制后缺少全局依赖（svg-icon / DOMPurify / SCSS / iconfont） | 模板渲染失败或图标空白 | 按依赖闭包复制；20 套模板冒烟测试；首屏只加载选中模板 |
| 模板 JSON 自带示例数据泄露 | 违反「不编造」红线 | adapter 从零构造 dataSource；占位文本断言测试 |
| 打印分页/缩放不理想 | PDF 质量不达标 | 固定 820px + A4 缩放；`break-inside: avoid`；Chrome/Edge 实测；P1 备选 html2canvas |
| AI 生成编造内容 | 证据链可信度受损 | Java 组装事实 + AI 只改写文案 + FactGuard 校验 + 兜底 |
| 20 套模板体积大 | 首屏变慢 | 模板 JSON 按需加载；封面压缩；iconfont 懒加载 |
| 换模板产生大量版本 | 版本列表噪音 | UI 二次确认；`generationReason=TEMPLATE_SWITCH` 明确标注；版本号可读 |
| DOCX 中文乱码 | 导出不可用 | 设置 eastAsia 字体；用 Word/WPS 实测 |
| resume-design 升级导致组件漂移 | 后续维护困难 | vendor 目录与源版本固定；README 记录来源 commit/日期；不自动升级 |

## 13. 明确留给实现者的决定

- 20 套模板的具体视觉微调（如某套模板空数据时是否强制显示标题）由实现者按实际渲染效果决定。
- 编辑器表单的具体布局（折叠面板 / 标签页 / 弹窗）由实现者按 Element Plus 现有风格决定。
- 打印缩放的精确值（0.96~0.97）以 Chrome/Edge 实测为准。
- AI Prompt 的具体措辞由实现者按 `chat-extract-prompt.txt` 的风格编写，但必须包含「不得新增事实」「只输出 JSON」约束。
- 模板封面压缩比例与格式（WebP/PNG）由实现者按体积决定。
- 版本历史是否需要「以该版本继续编辑」按钮由实现者按工期决定；若做，必须走 `RESTORE` 新版本，不覆盖旧版本。

## 14. 验收标准

1. 演示账号完成建档后，可在 `/resume` 创建简历并生成版本；生成内容只包含已确认信息。
2. 模板推荐能根据目标岗位给出 3 套候选；20 套模板均可预览，空数据下不出现示例内容。
3. 切换模板/编辑内容均产生新版本；旧版本内容与模板保持不变。
4. 任意历史版本都能导出 PDF（打印）和 DOCX；DOCX 在 Word/WPS 中打开正常。
5. 两个账号互相访问简历返回 403；导出必须带 versionId。
6. `mvn test`、`npm test`、`npm run build` 全部通过。
7. `docs/api.md`、`docs/decision-record.md`、`docs/architecture.md` 已同步。
