/**
 * 从本地 resume-design 参考仓库抽取 20 套模板及其渲染依赖。
 *
 * 运行：node scripts/vendor-resume-templates.mjs
 * 可选环境变量：
 *   RESUME_DESIGN_SRC   resume-design 源码目录
 *   RESUME_TEMPLATE_SRC 已抓取的模板 JSON 目录（<id>.json，内容为接口原始响应）
 *
 * 设计约束：
 * - 只复制 20 套模板实际用到的模块组件闭包 + 全部 ModuleTitle + IHJNewSchema 类型。
 * - hooks 由 Task 11 手写简化版，本脚本不复制原 hooks（原版依赖 Pinia store）。
 * - 所有导入改写到 CareerFlow 的路径；重复运行幂等。
 */
import fs from 'node:fs'
import path from 'node:path'

const ROOT = process.cwd()
const SRC =
  process.env.RESUME_DESIGN_SRC ||
  'C:/Users/zhengjingying/Documents/GitHub/_refs/resume-design-dl/resume-design-main'
const TEMPLATE_SRC =
  process.env.RESUME_TEMPLATE_SRC ||
  'C:/Users/zhengjingying/Documents/GitHub/_refs/template-json'
const OUT = path.join(ROOT, 'src/components/resume-template')
const VENDOR = path.join(OUT, 'vendor')
const DESIGNER = path.join(SRC, 'src/views/createTemplate/designer')
const MODULES = path.join(DESIGNER, 'modules')
const TYPES_FILE = path.join(SRC, 'src/views/createTemplate/types/IHJNewSchema.ts')

const TEMPLATES = [
  ['6746fe472f0052cb6a9365aa', ['通用', '经典']],
  ['674701372f0052cb6a93660b', ['通用', '时尚', '双栏']],
  ['674704bd2f0052cb6a9366c5', ['通用', '简洁']],
  ['674705f82f0052cb6a9366f9', ['通用', '创意']],
  ['674708c52f0052cb6a9367bb', ['通用', '简洁']],
  ['67470a292f0052cb6a936802', ['通用', '经典']],
  ['67470f622f0052cb6a9368a7', ['通用', '经典']],
  ['674ef7a1499a3d6818e5f3e9', ['通用', '简洁']],
  ['675038c5c016bbf823ea30eb', ['技术', '创意', '后端', 'java']],
  ['67503cd2c016bbf823ea3130', ['创意', '设计', '视觉']],
  ['67503de5c016bbf823ea317d', ['创意', '设计', '中国风']],
  ['67505d9ac016bbf823ea3497', ['通用', '时尚', '双栏']],
  ['6756f8ac8f0b47d968a0e98c', ['贸易', '买手', '外贸', '电商']],
  ['67584681ad08c962be2e9da4', ['会计', '财务', '金融']],
  ['676015175e80d518fa0c1985', ['算法', '后端', '技术', 'java']],
  ['67615d45e4dde1f4d601c8c3', ['运营', '新媒体', '内容']],
  ['676d4a717017be6da0dfef21', ['交互', '设计', '前端', 'ui']],
  ['67b5e1793370f88ef9258aa6', ['java', '后端', '后端开发', '技术']],
  ['6787c0990a3df5adfa37071d', ['通用', '简洁']],
  ['6880c88c20e3189056304800', ['文案', '策划', '运营', '内容']],
]

function ensureDir(dir) {
  fs.mkdirSync(dir, { recursive: true })
}

function readJson(file) {
  return JSON.parse(fs.readFileSync(file, 'utf8'))
}

function rewriteImports(text) {
  return text
    .replaceAll(
      '@/views/createTemplate/types/IHJNewSchema',
      '@/components/resume-template/vendor/types/IHJNewSchema',
    )
    .replaceAll('@/utils/common', '@/components/resume-template/util')
    .replaceAll('@/assets/images/people.jpg', '@/assets/resume-template/people.jpg')
    .replaceAll('@/assets/images/', '@/assets/resume-template/images/')
    .replaceAll(
      "import.meta.glob('/src/assets/createTemplateImages/*'",
      "import.meta.glob('/src/assets/resume-template/images/*'",
    )
    .replaceAll('/src/assets/createTemplateImages/', '/src/assets/resume-template/images/')
}

function copyRewritten(from, to) {
  ensureDir(path.dirname(to))
  let text = rewriteImports(fs.readFileSync(from, 'utf8'))

  // 第三方模板组件不参与本项目的严格类型检查；运行时仍需要补上 Vue API 导入
  if (to.endsWith('.vue')) {
    text = text.replace(/(<script[^>]*>)/, '$1\n// @ts-nocheck')
  } else if (to.endsWith('.ts')) {
    text = `// @ts-nocheck\n${text}`
  }
  text = text
    .split('\n')
    .filter((line) => !line.trim().startsWith('console.log('))
    .join('\n')

  const vueApis = ['ref', 'computed', 'reactive', 'watch', 'watchEffect',
    'onMounted', 'onBeforeUnmount', 'onUnmounted', 'nextTick', 'toRefs']
  const imported = new Set()
  for (const match of text.matchAll(/import\s*\{([^}]+)\}\s*from\s*['"]vue['"]/g)) {
    match[1].split(',').forEach((name) => imported.add(name.trim().split(/\s+as\s+/)[0]))
  }
  const missing = vueApis.filter((api) =>
    (text.includes(`${api}(`) || text.includes(`${api}<`)) && !imported.has(api))
  if (missing.length > 0) {
    const line = `import { ${missing.join(', ')} } from 'vue';`
    if (to.endsWith('.vue')) {
      text = text.replace(/(<script[^>]*>)/, `$1\n${line}`)
    } else {
      text = `${line}\n${text}`
    }
  }
  fs.writeFileSync(to, text)
}

function resolveImport(fromFile, spec) {
  if (spec.startsWith('@/views/createTemplate/')) {
    return path.join(SRC, 'src', spec.slice(2))
  }
  if (spec.startsWith('.')) {
    return path.resolve(path.dirname(fromFile), spec)
  }
  return null
}

function resolveFile(candidate) {
  for (const ext of ['', '.ts', '.vue', '.js']) {
    const file = candidate + ext
    if (fs.existsSync(file) && fs.statSync(file).isFile()) {
      return file
    }
  }
  return null
}

const copied = new Set()

function walk(file) {
  if (!file || copied.has(file)) {
    return
  }
  copied.add(file)
  const text = fs.readFileSync(file, 'utf8')
  for (const match of text.matchAll(/import\s+[^'"]+from\s+['"]([^'"]+)['"]/g)) {
    const resolved = resolveImport(file, match[1])
    if (!resolved) {
      continue
    }
    // 原 hooks 依赖 store，Task 11 会写简化版；types 与 modules 正常复制
    if (resolved.includes(`${path.sep}hooks${path.sep}`)) {
      continue
    }
    const target = resolveFile(resolved)
    if (target) {
      walk(target)
    }
  }
}

function copyVendorFile(file) {
  const relative = path.relative(DESIGNER, file)
  copyRewritten(file, path.join(VENDOR, relative))
}

// 1. 模板 JSON：只保留 data.template_json
ensureDir(path.join(OUT, 'templates'))
for (const [id] of TEMPLATES) {
  const raw = readJson(path.join(TEMPLATE_SRC, `${id}.json`))
  const template = raw.data?.template_json ?? raw.template_json
  if (!template) {
    throw new Error(`模板 ${id} 缺少 template_json`)
  }
  fs.writeFileSync(
    path.join(OUT, 'templates', `${id}.json`),
    JSON.stringify(template, null, 2),
  )
}

// 2. 模块组件闭包
for (const [id] of TEMPLATES) {
  const template = readJson(path.join(OUT, 'templates', `${id}.json`))
  for (const component of template.componentsTree ?? []) {
    const [type] = component.componentName.split('_')
    const file = resolveFile(path.join(MODULES, type, component.componentName))
    if (!file) {
      throw new Error(`找不到组件 ${component.componentName}`)
    }
    walk(file)
  }
}
// ModuleTitle 通过 customProps.ModuleTitleCpt 按名字引用，必须全量复制
for (const name of fs.readdirSync(path.join(MODULES, 'ModuleTitle'))) {
  walk(path.join(MODULES, 'ModuleTitle', name))
}
walk(TYPES_FILE)

for (const file of copied) {
  if (file === TYPES_FILE) {
    copyRewritten(file, path.join(VENDOR, 'types/IHJNewSchema.ts'))
  } else if (file.startsWith(DESIGNER)) {
    copyVendorFile(file)
  }
}

// 模块标题等组件还会引用 @/assets/images/*，一并复制到 resume-template/images
const referencedImages = new Set()
for (const file of copied) {
  for (const match of fs.readFileSync(file, 'utf8').matchAll(/@\/assets\/images\/([\w.-]+)/g)) {
    referencedImages.add(match[1])
  }
}

// 3. 静态资源
ensureDir(path.join(ROOT, 'src/assets/resume-template/images'))
ensureDir(path.join(ROOT, 'src/assets/resume-template/covers'))
ensureDir(path.join(ROOT, 'public/static/iconfont'))

fs.copyFileSync(
  path.join(SRC, 'src/assets/images/people.jpg'),
  path.join(ROOT, 'src/assets/resume-template/people.jpg'),
)
for (const image of ['baseInfo_2.png', 'baseInfo_5.png', 'baseInfo_6.png', 'title_bg.png']) {
  fs.copyFileSync(
    path.join(SRC, 'src/assets/createTemplateImages', image),
    path.join(ROOT, 'src/assets/resume-template/images', image),
  )
}
for (const image of referencedImages) {
  const from = path.join(SRC, 'src/assets/images', image)
  if (fs.existsSync(from)) {
    fs.copyFileSync(from, path.join(ROOT, 'src/assets/resume-template/images', image))
  }
}
fs.copyFileSync(
  path.join(SRC, 'public/static/iconfont/iconfont.js'),
  path.join(ROOT, 'public/static/iconfont/iconfont.js'),
)

// 4. 许可证与来源说明
const license = fs.readFileSync(path.join(SRC, 'LICENSE'), 'utf8')
fs.writeFileSync(
  path.join(OUT, 'LICENSE-resume-design.md'),
  `<!-- 来源：https://github.com/Hacker233/resume-design（MIT），复制日期：${new Date().toISOString().slice(0, 10)} -->\n\n${license}`,
)

// 5. 封面（失败不阻断，manifest 会回退到占位图）
const metadata = readJson(
  'C:/Users/zhengjingying/Documents/GitHub/_refs/template-meta.json',
)
const metaById = new Map(metadata.map((item) => [item.id, item]))
const covers = new Map()

for (const [id] of TEMPLATES) {
  // 已压缩的 JPEG 封面优先复用，避免重复下载 12MB 原图
  if (fs.existsSync(path.join(ROOT, 'src/assets/resume-template/covers', `${id}.jpg`))) {
    covers.set(id, `${id}.jpg`)
    continue
  }
  const url = metaById.get(id)?.cover
  if (!url) {
    covers.set(id, null)
    continue
  }
  try {
    const response = await fetch(url)
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`)
    }
    const contentType = response.headers.get('content-type') || ''
    const ext = contentType.includes('jpeg') || contentType.includes('jpg') ? 'jpg' : 'png'
    const file = `${id}.${ext}`
    fs.writeFileSync(
      path.join(ROOT, 'src/assets/resume-template/covers', file),
      Buffer.from(await response.arrayBuffer()),
    )
    covers.set(id, file)
    console.log(`cover ok: ${id}.${ext}`)
  } catch (error) {
    covers.set(id, null)
    console.warn(`cover failed: ${id} ${error.message}`)
  }
}

// 6. manifest.ts
const lines = [
  "import type { TemplateMeta } from './types'",
  '',
  'export const TEMPLATES: TemplateMeta[] = [',
]
for (const [id, keywords] of TEMPLATES) {
  const template = readJson(path.join(OUT, 'templates', `${id}.json`))
  const meta = metaById.get(id) ?? {}
  const pageName = template.props?.pageName ?? 'BasePage'
  const cover = covers.get(id)
  const coverExpr = cover
    ? `new URL('../../assets/resume-template/covers/${cover}', import.meta.url).href`
    : `new URL('../../assets/resume-template/covers/placeholder.png', import.meta.url).href`
  const keywordsText = keywords.map((word) => `'${word}'`).join(', ')
  lines.push(
    '  {',
    `    id: '${id}',`,
    `    name: ${JSON.stringify(meta.title ?? id)},`,
    `    style: ${JSON.stringify(meta.style ?? '')},`,
    `    industry: ${JSON.stringify(meta.industry ?? '')},`,
    `    post: ${JSON.stringify(meta.post ?? '')},`,
    `    pageName: '${pageName}',`,
    `    file: '${id}.json',`,
    `    cover: ${coverExpr},`,
    `    keywords: [${keywordsText}],`,
    '  },',
  )
}
lines.push(']', '')
fs.writeFileSync(path.join(OUT, 'manifest.ts'), lines.join('\n'))

console.log(`vendored ${copied.size} component files, ${TEMPLATES.length} templates`)
