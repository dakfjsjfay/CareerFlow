import type {
  ResumeContent,
  ResumeEducation,
  ResumeExperience,
  ResumeProject,
  TemplateJson,
  TemplateModule,
} from './types'
import { escapeHtml, toParagraphs } from './util'

function isBlank(value: unknown): boolean {
  return value === null || value === undefined || String(value).trim() === ''
}

function deepClone<T>(value: T): T {
  // 模板来自 Vue reactive prop，structuredClone 无法克隆 Proxy；JSON 数据可以直接深拷贝
  return JSON.parse(JSON.stringify(value)) as T
}

function setField(module: TemplateModule, field: string, value: unknown, show?: boolean): void {
  const data = module.dataSource?.[field]
  if (data && typeof data === 'object') {
    data.value = value
  }
  if (show !== undefined && module.props?.[field]) {
    module.props[field].show = show
  }
}

function setShow(module: TemplateModule, field: string, show: boolean): void {
  if (module.props?.[field]) {
    module.props[field].show = show
  }
}

function setVisible(module: TemplateModule, visible: boolean, originallyShown: boolean): void {
  module.customProps = module.customProps ?? {}
  module.customProps.showModule = visible && originallyShown
}

function dateRange(start: string | null, end: string | null): [string | null, string | null] {
  return [start, end]
}

function applyBaseInfo(module: TemplateModule, content: ResumeContent, shown: boolean): void {
  const info = content.personalInfo
  setField(module, 'name', info.realName ?? '', !isBlank(info.realName))
  setField(module, 'phoneNumber', info.phone ?? '', !isBlank(info.phone))
  setField(module, 'email', info.email ?? '', !isBlank(info.email))
  setField(module, 'degree', info.degree ?? '', !isBlank(info.degree))
  setField(module, 'address', info.city ?? '', !isBlank(info.city))
  setField(module, 'avatar', info.avatar ?? '', !isBlank(info.avatar))
  // 档案没有年龄字段：始终隐藏，避免模板示例年龄泄露
  setField(module, 'age', '', false)
  const years = info.yearsOfExperience ?? 0
  setField(module, 'workService', years, years > 0)
  setField(module, 'abstract', info.abstract ?? '', !isBlank(info.abstract))

  setVisible(module, !isBlank(info.realName) || !isBlank(info.phone), shown)
}

function applyCustom(module: TemplateModule, content: ResumeContent, shown: boolean): void {
  const info = content.personalInfo
  setField(module, 'name', info.realName ?? '', !isBlank(info.realName))
  setField(module, 'abstract', info.abstract ?? '', !isBlank(info.abstract))
  setField(module, 'avatar', info.avatar ?? '', !isBlank(info.avatar))
  setVisible(module, !isBlank(info.realName) || !isBlank(info.abstract), shown)
}

function applyJobIntention(module: TemplateModule, content: ResumeContent, shown: boolean): void {
  const intention = content.jobIntention
  setField(module, 'intendedPositions', intention.targetPosition ?? '', !isBlank(intention.targetPosition))
  setField(module, 'intendedCity', intention.targetCity ?? '', !isBlank(intention.targetCity))
  setField(module, 'expectSalary', intention.expectedSalary ?? '', !isBlank(intention.expectedSalary))
  setField(module, 'jobStatus', '', false)
  setField(module, 'jobSearchType', '', false)

  const hasContent =
    !isBlank(intention.targetPosition) ||
    !isBlank(intention.targetCity) ||
    !isBlank(intention.expectedSalary)
  setVisible(module, hasContent, shown)
}

function applyEducation(module: TemplateModule, education: ResumeEducation[], shown: boolean): void {
  const items = education.map((item) => ({
    date: { value: dateRange(item.startDate, item.endDate) },
    schoolName: { value: item.school ?? '' },
    specialized: { value: item.major ?? '' },
    degree: { value: item.degree ?? '' },
    majorCourse: { value: '' },
  }))

  if (module.dataSource.list) {
    module.dataSource.list.value = items
  }
  const hasDates = education.some((item) => !isBlank(item.startDate) || !isBlank(item.endDate))
  setShow(module, 'date', hasDates)
  setShow(module, 'majorCourse', false)
  setVisible(module, items.length > 0, shown)
}

function applySkills(module: TemplateModule, skills: string[], shown: boolean): void {
  const items = skills
    .filter((skill) => !isBlank(skill))
    .map((skill) => ({
      skillName: { value: skill },
      proficiency: { value: '' },
      introduce: { value: skill },
    }))

  if (module.dataSource.list) {
    module.dataSource.list.value = items
  }
  // 档案没有熟练度，隐藏滑条，只展示技能名
  setShow(module, 'proficiency', false)
  setShow(module, 'skillName', true)
  setShow(module, 'introduce', true)
  setVisible(module, items.length > 0, shown)
}

function applyProjects(module: TemplateModule, projects: ResumeProject[], shown: boolean): void {
  const items = projects.map((project) => {
    const techStack = project.techStack?.filter((item) => !isBlank(item)) ?? []
    return {
      date: { value: dateRange(project.startDate, project.endDate) },
      contentTtile: { value: escapeHtml(project.projectName ?? '') },
      companyName: { value: project.projectName ?? '' },
      posts: { value: project.role ?? '' },
      jobContent: {
        value: toParagraphs([
          project.description,
          techStack.length > 0 ? `技术栈：${techStack.join('、')}` : null,
          ...(project.highlights ?? []),
        ]),
      },
    }
  })

  if (module.dataSource.list) {
    module.dataSource.list.value = items
  }
  const hasDates = projects.some(
    (project) => !isBlank(project.startDate) || !isBlank(project.endDate),
  )
  setShow(module, 'date', hasDates)
  setVisible(module, items.length > 0, shown)
}

function applyExperiences(
  module: TemplateModule,
  experiences: ResumeExperience[],
  shown: boolean,
): void {
  const items = experiences.map((experience) => ({
    date: { value: dateRange(experience.startDate, experience.endDate) },
    contentTtile: { value: escapeHtml(experience.position ?? '') },
    companyName: { value: experience.company ?? '' },
    posts: { value: experience.position ?? '' },
    jobContent: {
      value: toParagraphs([
        experience.description,
        experience.achievements,
        ...(experience.highlights ?? []),
      ]),
    },
  }))

  if (module.dataSource.list) {
    module.dataSource.list.value = items
  }
  const hasDates = experiences.some(
    (experience) => !isBlank(experience.startDate) || !isBlank(experience.endDate),
  )
  setShow(module, 'date', hasDates)
  setVisible(module, items.length > 0, shown)
}

function applyText(module: TemplateModule, text: string | null, shown: boolean): void {
  const hasText = !isBlank(text)
  if (module.dataSource.content) {
    module.dataSource.content.value = hasText ? escapeHtml(text) : ''
  }
  setVisible(module, hasText, shown)
}

/**
 * 把 CareerFlow 的 ResumeContent 映射成 resume-design 模板的 dataSource。
 * 所有模块的数据都从零重建，模板 JSON 里的示例数据不会进入成品。
 */
export function applyContent(
  template: TemplateJson,
  content: ResumeContent,
  resumeName: string,
): TemplateJson {
  const result = deepClone(template)
  const education = content.education ?? []
  const skills = content.skills ?? []
  const projects = content.projects ?? []
  const experiences = content.experiences ?? []

  for (const module of result.componentsTree) {
    const type = module.componentName.split('_')[0]
    const shown = module.customProps?.showModule !== false

    switch (type) {
      case 'ResumeTitle':
        setField(module, 'title', resumeName)
        setVisible(module, true, shown)
        break
      case 'BaseInfo':
        applyBaseInfo(module, content, shown)
        break
      case 'Custom':
        applyCustom(module, content, shown)
        break
      case 'JobIntention':
        applyJobIntention(module, content, shown)
        break
      case 'EduBackground':
        applyEducation(module, education, shown)
        break
      case 'SkillSpecialties':
        applySkills(module, skills, shown)
        break
      case 'WorkExperience':
        applyExperiences(
          module,
          experiences.filter((experience) => experience.type !== 'INTERNSHIP'),
          shown,
        )
        break
      case 'InternshipExperience':
        applyExperiences(
          module,
          experiences.filter((experience) => experience.type === 'INTERNSHIP'),
          shown,
        )
        break
      case 'ProjectExperience':
        applyProjects(module, projects, shown)
        break
      case 'SelfEvaluation':
        applyText(module, content.selfEvaluation, shown)
        break
      default:
        // 校园经历/奖项/爱好/作品展示没有数据来源，保留模板的隐藏状态
        setVisible(module, false, shown)
        break
    }
  }

  return result
}

/**
 * 测试辅助：收集模板 dataSource 中所有 value 文本，用于断言没有示例数据泄露。
 */
export function collectRenderedText(template: TemplateJson): string {
  const values: string[] = []
  for (const module of template.componentsTree) {
    if (module.customProps?.showModule === false) {
      continue
    }
    collectValues(module.dataSource, values)
  }
  return values.join(' ')
}

function collectValues(node: unknown, out: string[]): void {
  if (node === null || node === undefined) {
    return
  }
  if (Array.isArray(node)) {
    node.forEach((item) => collectValues(item, out))
    return
  }
  if (typeof node === 'object') {
    for (const [key, value] of Object.entries(node as Record<string, unknown>)) {
      if (key === 'value') {
        collectStrings(value, out)
      } else {
        collectValues(value, out)
      }
    }
  }
}

function collectStrings(node: unknown, out: string[]): void {
  if (typeof node === 'string') {
    out.push(node)
    return
  }
  if (Array.isArray(node)) {
    node.forEach((item) => collectStrings(item, out))
    return
  }
  if (node && typeof node === 'object') {
    Object.values(node as Record<string, unknown>).forEach((value) =>
      collectStrings(value, out),
    )
  }
}
