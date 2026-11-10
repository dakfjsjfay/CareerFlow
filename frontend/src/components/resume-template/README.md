# resume-template（resume-design vendor）

本目录是 [resume-design / 猫步简历](https://github.com/Hacker233/resume-design) 的只读渲染层，
用于在 CareerFlow 中展示 20 套完整简历模板。resume-design 使用 MIT 协议，许可证见
`LICENSE-resume-design.md`。

## 目录

```
resume-template/
├── templates/                 # 20 份模板 JSON（只保留 data.template_json）
├── manifest.ts                # 模板元数据 + 封面 + 推荐关键词（脚本生成）
├── types.ts                   # TemplateMeta / TemplateJson / ResumeContent 类型
├── adapter.ts                 # CareerFlow content → 模板 dataSource（我们维护）
├── recommend.ts               # 按目标岗位推荐模板（我们维护）
├── util.ts                    # formatDate / escapeHtml / toParagraphs（我们维护）
├── registerModules.ts         # 127 个模块组件映射（脚本生成）
├── moduleTitles.ts            # 19 个模块标题映射（脚本生成）
├── ReadOnlyModuleBox.vue      # 只读模块渲染（我们维护）
├── ResumeTemplateRenderer.vue # 渲染入口（我们维护）
├── SvgIcon.vue                # 从 resume-design 复制
├── pages/                     # 只读页面布局（我们维护）
├── vendor/
│   ├── modules/               # resume-design 模块组件（第三方）
│   ├── types/IHJNewSchema.ts  # 第三方类型
│   └── hooks/                 # 从第三方 hook 简化而来（已去掉 Pinia/store/主题联动）
└── LICENSE-resume-design.md
```

## 与上游的差异

1. **只 vendor 20 套模板的依赖闭包**：`vendor/modules` 只包含这 20 套模板实际引用到的
   `<Type>_<N>.vue`，外加全部 `ModuleTitle`；`registerModules.ts` 由脚本生成。
2. **去设计器化**：不复制 `pages/BasePage.vue`、`ModuleBox.vue`、`registerModules.ts`
   等设计器实现；本目录的页面布局与 `ReadOnlyModuleBox.vue` 是只读版本，不依赖
   `pinia`、`vue-router`、`vuedraggable`。
3. **样式 hook 简化**：`vendor/hooks/useGetStyle.ts` 与 `useGetPageStyle.ts` 删除了
   主题色/字体联动的 `watch`，只保留 `css/customCss → 内联样式` 的映射。
4. **第三方组件加 `// @ts-nocheck`**：resume-design 使用自动导入和宽松类型，
   vendor 文件不参与本项目的严格类型检查；运行时所需的 Vue API 导入已补齐。
5. **导入路径改写**：
   - `@/views/createTemplate/types/IHJNewSchema` → `@/components/resume-template/vendor/types/IHJNewSchema`
   - `@/utils/common` → `@/components/resume-template/util`
   - `@/assets/images/*` → `@/assets/resume-template/images/*`
   - `import.meta.glob('/src/assets/createTemplateImages/*')` → `@/assets/resume-template/images/*`
6. **移除 `console.log`**：避免 20 套模板渲染时刷屏。

## 升级方式

```bash
# 先把 resume-design 源码与模板 JSON 放到本地
export RESUME_DESIGN_SRC=/path/to/resume-design
export RESUME_TEMPLATE_SRC=/path/to/template-json   # <id>.json，内容为接口原始响应

cd frontend
node scripts/vendor-resume-templates.mjs
```

脚本会重新生成 `templates/`、`vendor/`、`manifest.ts`、`registerModules.ts`、
`moduleTitles.ts` 与封面资源。**不要手工修改 `vendor/` 下的文件**；需要改行为时优先改
`adapter.ts`、`ReadOnlyModuleBox.vue` 或简化的 hooks。

封面在第一次下载后会被压缩为 400px 宽的 JPEG（`covers/<id>.jpg`）；脚本检测到已有
`.jpg` 会跳过下载，避免把 12MB 原图重新带进仓库。

## 数据约定

`adapter.ts` 从零构造每个模块的 `dataSource`，模板 JSON 自带的示例数据不会进入成品。
没有数据的模块通过 `customProps.showModule = false` 隐藏；字段缺失时同时隐藏对应
`props.<field>.show`。新增模板时，先确认它用到的组件已进入 `registerModules.ts`
（重新运行脚本即可）。
