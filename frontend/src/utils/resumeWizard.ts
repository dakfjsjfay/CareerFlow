import type { ResumeContent as TemplateResumeContent } from '@/components/resume-template/types'
import type {
  DraftDirection,
  DraftExperience,
  DraftProject,
  ResumeDraftContent,
  StarBlock,
} from '@/api/resumeDraft'

export const TOTAL_STEPS = 8

export const STEP_TITLES = [
  '基础信息',
  '画像与方向',
  '经历补充',
  'STAR 结构化',
  '软性信息',
  '证件照',
  '预览微调',
  '生成简历',
]

export const DEFAULT_TEMPLATE_ID = '6746fe472f0052cb6a9365aa'

export function emptyStar(): StarBlock {
  return { situation: '', task: '', action: '', result: '' }
}

export function createEmptyDraftContent(): ResumeDraftContent {
  return {
    schemaVersion: 1,
    basic: {
      realName: '',
      phone: '',
      email: null,
      city: null,
      school: null,
      major: null,
      degree: null,
      graduationYear: null,
      educationStatus: null,
    },
    direction: {
      industry: null,
      targetPosition: null,
      targetCity: null,
      expectedSalary: null,
      jobGoal: null,
      directionTags: [],
    },
    educations: [],
    experiences: [],
    projects: [],
    skills: [],
    soft: { selfEvaluation: '', strengths: [], suggestions: [] },
    photo: { dataUrl: null, assetId: null, width: null, height: null },
    preview: {
      templateId: DEFAULT_TEMPLATE_ID,
      templateName: null,
      fontFamily: 'Helvetica Neue',
      fontSize: 14,
      lineHeight: 1.6,
      spacing: 16,
    },
    meta: { lastStep: 1 },
  }
}

/**
 * 兼容后端返回的旧草稿/缺字段数据：补齐所有必需字段，避免页面因 undefined 崩溃。
 */
export function normalizeDraftContent(raw: unknown): ResumeDraftContent {
  const base = createEmptyDraftContent()
  if (!raw || typeof raw !== 'object') {
    return base
  }
  const input = raw as Partial<ResumeDraftContent>
  return {
    schemaVersion: input.schemaVersion ?? 1,
    basic: { ...base.basic, ...(input.basic ?? {}) },
    direction: {
      ...base.direction,
      ...(input.direction ?? {}),
      directionTags: input.direction?.directionTags ?? [],
    },
    educations: (input.educations ?? []).map((item) => ({
      id: item.id ?? newId(),
      school: item.school ?? '',
      major: item.major ?? '',
      degree: item.degree ?? '',
      graduationYear: item.graduationYear ?? null,
      startDate: item.startDate ?? null,
      endDate: item.endDate ?? null,
      fieldSources: item.fieldSources ?? {},
    })),
    experiences: (input.experiences ?? []).map((item) => ({
      id: item.id ?? newId(),
      kind: item.kind ?? 'WORK',
      company: item.company ?? '',
      position: item.position ?? '',
      type: item.type ?? 'WORK',
      startDate: item.startDate ?? null,
      endDate: item.endDate ?? null,
      description: item.description ?? '',
      achievements: item.achievements ?? '',
      star: { ...emptyStar(), ...(item.star ?? {}) },
      skills: item.skills ?? [],
      fieldSources: item.fieldSources ?? {},
    })),
    projects: (input.projects ?? []).map((item) => ({
      id: item.id ?? newId(),
      projectName: item.projectName ?? '',
      role: item.role ?? '',
      description: item.description ?? '',
      techStack: item.techStack ?? [],
      highlights: item.highlights ?? [],
      startDate: item.startDate ?? null,
      endDate: item.endDate ?? null,
      star: { ...emptyStar(), ...(item.star ?? {}) },
      fieldSources: item.fieldSources ?? {},
    })),
    skills: input.skills ?? [],
    soft: { ...base.soft, ...(input.soft ?? {}) },
    photo: { ...base.photo, ...(input.photo ?? {}) },
    preview: { ...base.preview, ...(input.preview ?? {}) },
    meta: input.meta ?? {},
  }
}

export function newId(): string {
  return `e${Date.now().toString(36)}${Math.random().toString(36).slice(2, 8)}`
}

/** 由行业/岗位/城市/技能确定性生成求职方向标签，不调用 AI。 */
export function buildDirectionTags(direction: DraftDirection, skills: string[]): string[] {
  const tags: string[] = []
  const push = (value: string | null | undefined) => {
    const text = value?.trim()
    if (text && !tags.includes(text)) {
      tags.push(text)
    }
  }
  push(direction.industry)
  push(direction.targetPosition)
  push(direction.targetCity)
  if (direction.expectedSalary?.trim()) {
    push(`${direction.expectedSalary.trim()}`)
  }
  skills
    .filter((skill) => skill.trim())
    .slice(0, 3)
    .forEach((skill) => push(skill.trim()))
  return tags.slice(0, 8)
}

/** 单个经历 STAR 完成度（0-100），用于步骤 4 的提示。 */
export function starCompleteness(star: StarBlock): number {
  const keys: Array<keyof StarBlock> = ['situation', 'task', 'action', 'result']
  const filled = keys.filter((key) => star[key]?.trim()).length
  return Math.round((filled / keys.length) * 100)
}

export function experienceCompleteness(experience: DraftExperience): number {
  return starCompleteness(experience.star)
}

export function projectCompleteness(project: DraftProject): number {
  return starCompleteness(project.star)
}

/** 步骤校验：返回缺失项文案，空数组表示可进入下一步。 */
export function validateStep(content: ResumeDraftContent, step: number): string[] {
  const missing: string[] = []
  if (step === 1) {
    if (!content.basic.realName.trim()) missing.push('请填写姓名')
    if (!content.basic.phone.trim() && !content.basic.email?.trim()) {
      missing.push('请至少填写手机号或邮箱')
    }
  }
  if (step === 2) {
    if (!content.direction.targetPosition?.trim()) missing.push('请选择目标岗位')
  }
  if (step === 3) {
    const hasEducation = content.educations.some((item) => item.school.trim())
    const hasExperience = content.experiences.length > 0 || content.projects.length > 0
    if (!hasEducation && !hasExperience) {
      missing.push('请至少添加一条教育经历或项目/实习经历')
    }
  }
  if (step === 8) {
    if (!content.soft.selfEvaluation.trim() && content.soft.strengths.length === 0) {
      missing.push('建议先补充自我评价或至少一条个人优势')
    }
  }
  return missing
}

/**
 * 把 AI 候选内容合并回草稿指定区块（确认后才调用）。
 * 返回新的 content 对象，便于触发响应式更新。
 */
export function applySectionToDraftContent(
  content: ResumeDraftContent,
  sectionKey: string,
  afterContent: unknown,
): ResumeDraftContent {
  const next: ResumeDraftContent = JSON.parse(JSON.stringify(content))
  switch (sectionKey) {
    case 'BASIC':
      next.basic = { ...next.basic, ...(afterContent as object) }
      break
    case 'EDUCATION':
      if (Array.isArray(afterContent)) {
        next.educations = afterContent as ResumeDraftContent['educations']
      }
      break
    case 'PROJECT':
      if (Array.isArray(afterContent)) {
        next.projects = afterContent as ResumeDraftContent['projects']
      }
      break
    case 'EXPERIENCE':
      if (Array.isArray(afterContent)) {
        next.experiences = afterContent as ResumeDraftContent['experiences']
      }
      break
    case 'SKILL':
      if (Array.isArray(afterContent)) {
        next.skills = afterContent as string[]
      }
      break
    case 'SELF_EVALUATION':
    case 'SOFT':
      next.soft = { ...next.soft, ...(afterContent as object) }
      break
    case 'PHOTO':
      next.photo = { ...next.photo, ...(afterContent as object) }
      break
    default:
      break
  }
  return next
}

/**
 * 实时预览：把八步草稿映射成模板渲染器需要的 ResumeContent。
 * 与后端 DraftContentConverter 保持同样的字段映射，避免预览与导出不一致。
 */
export function draftContentToPreview(content: ResumeDraftContent): TemplateResumeContent {
  const basic = content.basic
  const direction = content.direction
  const starText = (star: StarBlock): string =>
    [star.situation, star.task, star.action, star.result].filter((item) => item?.trim()).join('\n')

  return {
    meta: {
      templateId: content.preview.templateId,
      templateName: content.preview.templateName,
      generatedAt: null,
      source: 'WIZARD',
      generationReason: 'INITIAL',
      targetJobId: null,
      warnings: [],
    },
    personalInfo: {
      realName: basic.realName || null,
      phone: basic.phone || null,
      email: basic.email || null,
      city: basic.city || null,
      avatar: content.photo.dataUrl || null,
      school: basic.school || null,
      major: basic.major || null,
      degree: basic.degree || null,
      graduationYear: basic.graduationYear ?? null,
      yearsOfExperience: null,
      abstract: content.soft.selfEvaluation?.slice(0, 60) || null,
    },
    jobIntention: {
      targetPosition: direction.targetPosition || null,
      targetCity: direction.targetCity || null,
      expectedSalary: direction.expectedSalary || null,
    },
    education: content.educations.map((item) => ({
      school: item.school || null,
      major: item.major || null,
      degree: item.degree || null,
      graduationYear: item.graduationYear ?? null,
      startDate: item.startDate || null,
      endDate: item.endDate || null,
    })),
    skills: content.skills.filter((skill) => skill.trim()),
    projects: content.projects.map((project) => ({
      projectName: project.projectName || null,
      role: project.role || null,
      description: project.description || starText(project.star) || null,
      techStack: project.techStack,
      highlights: project.highlights,
      startDate: project.startDate || null,
      endDate: project.endDate || null,
    })),
    experiences: content.experiences.map((experience) => ({
      company: experience.company || null,
      position: experience.position || null,
      type: experience.kind === 'INTERNSHIP' ? 'INTERNSHIP' : 'WORK',
      description: experience.description || starText(experience.star) || null,
      achievements: experience.achievements || null,
      highlights: [],
      startDate: experience.startDate || null,
      endDate: experience.endDate || null,
    })),
    selfEvaluation:
      content.soft.selfEvaluation ||
      (content.soft.strengths.length > 0 ? content.soft.strengths.join('；') : ''),
  }
}

/** 拖拽排序：把 from 位置的元素移动到 to 位置。 */
export function moveItem<T>(list: T[], from: number, to: number): T[] {
  if (from === to || from < 0 || to < 0 || from >= list.length || to >= list.length) {
    return list
  }
  const next = [...list]
  const [item] = next.splice(from, 1)
  next.splice(to, 0, item)
  return next
}

/** 简历工作区可定位/可修改的区块（与后端 sectionKey 约定一致）。 */
export type WorkspaceSectionKey =
  | 'BASIC'
  | 'JOB_INTENTION'
  | 'EDUCATION'
  | 'EXPERIENCE'
  | 'PROJECT'
  | 'SKILL'
  | 'SELF_EVALUATION'
  | 'PHOTO'

export const SECTION_LABELS: Record<string, string> = {
  BASIC: '基本信息',
  JOB_INTENTION: '求职意向',
  EDUCATION: '教育背景',
  EXPERIENCE: '工作 / 实习经历',
  PROJECT: '项目经历',
  SKILL: '专业技能',
  SELF_EVALUATION: '自我评价',
  PHOTO: '证件照',
}

const SECTION_STEP: Record<WorkspaceSectionKey, number> = {
  BASIC: 1,
  JOB_INTENTION: 2,
  EDUCATION: 3,
  EXPERIENCE: 3,
  PROJECT: 3,
  SKILL: 5,
  SELF_EVALUATION: 5,
  PHOTO: 6,
}

export function sectionToStep(key: string): number {
  return SECTION_STEP[key as WorkspaceSectionKey] ?? 1
}

export function sectionForStep(step: number): WorkspaceSectionKey {
  switch (step) {
    case 1:
      return 'BASIC'
    case 2:
      return 'JOB_INTENTION'
    case 3:
    case 4:
      return 'EXPERIENCE'
    case 5:
      return 'SELF_EVALUATION'
    case 6:
      return 'PHOTO'
    default:
      return 'BASIC'
  }
}

/**
 * 修改简历模式「草稿桥接」：把已生成的版本内容还原成可编辑草稿。
 * 只做确定性字段映射，不调用任何 AI，也不修改历史版本。
 */
export function resumeContentToDraftContent(
  content: TemplateResumeContent | null | undefined,
): ResumeDraftContent {
  const base = createEmptyDraftContent()
  if (!content) {
    return base
  }
  const info = content.personalInfo
  const intention = content.jobIntention
  const direction: DraftDirection = {
    industry: null,
    targetPosition: intention?.targetPosition ?? null,
    targetCity: intention?.targetCity ?? null,
    expectedSalary: intention?.expectedSalary ?? null,
    jobGoal: null,
    directionTags: [],
  }
  const skills = (content.skills ?? []).filter((skill) => skill?.trim())
  direction.directionTags = buildDirectionTags(direction, skills)

  return {
    schemaVersion: 1,
    basic: {
      realName: info.realName ?? '',
      phone: info.phone ?? '',
      email: info.email ?? null,
      city: info.city ?? null,
      school: info.school ?? null,
      major: info.major ?? null,
      degree: info.degree ?? null,
      graduationYear: info.graduationYear ?? null,
      educationStatus: null,
    },
    direction,
    educations: (content.education ?? []).map((item) => ({
      id: newId(),
      school: item.school ?? '',
      major: item.major ?? '',
      degree: item.degree ?? '',
      graduationYear: item.graduationYear ?? null,
      startDate: item.startDate ?? null,
      endDate: item.endDate ?? null,
      fieldSources: { school: 'VERSION', major: 'VERSION', degree: 'VERSION' },
    })),
    experiences: (content.experiences ?? []).map((item) => ({
      id: newId(),
      kind: item.type === 'INTERNSHIP' ? 'INTERNSHIP' : 'WORK',
      company: item.company ?? '',
      position: item.position ?? '',
      type: item.type === 'INTERNSHIP' ? 'INTERNSHIP' : 'WORK',
      startDate: item.startDate ?? null,
      endDate: item.endDate ?? null,
      description: item.description ?? '',
      achievements: item.achievements ?? '',
      star: emptyStar(),
      skills: [],
      fieldSources: { company: 'VERSION', position: 'VERSION', description: 'VERSION' },
    })),
    projects: (content.projects ?? []).map((item) => ({
      id: newId(),
      projectName: item.projectName ?? '',
      role: item.role ?? '',
      description: item.description ?? '',
      techStack: (item.techStack ?? []).filter((tech) => tech?.trim()),
      highlights: (item.highlights ?? []).filter((line) => line?.trim()),
      startDate: item.startDate ?? null,
      endDate: item.endDate ?? null,
      star: emptyStar(),
      fieldSources: { projectName: 'VERSION', description: 'VERSION' },
    })),
    skills,
    soft: {
      selfEvaluation: content.selfEvaluation ?? '',
      strengths: [],
      suggestions: [],
    },
    photo: {
      dataUrl: info.avatar ?? null,
      assetId: null,
      width: null,
      height: null,
    },
    preview: {
      ...base.preview,
      templateId: content.meta?.templateId || base.preview.templateId,
      templateName: content.meta?.templateName ?? null,
    },
    meta: { lastStep: 1, source: 'VERSION_BRIDGE' },
  }
}

/** 判断草稿是否还是空白，用于右侧预览的空状态。 */
export function isDraftEmpty(content: ResumeDraftContent): boolean {
  const hasEducation = content.educations.some((item) => item.school.trim())
  const hasExperience = content.experiences.some(
    (item) => item.company.trim() || item.position.trim(),
  )
  const hasProject = content.projects.some((item) => item.projectName.trim())
  return (
    !content.basic.realName.trim() &&
    !content.direction.targetPosition?.trim() &&
    !hasEducation &&
    !hasExperience &&
    !hasProject &&
    content.skills.length === 0 &&
    !content.soft.selfEvaluation.trim()
  )
}
