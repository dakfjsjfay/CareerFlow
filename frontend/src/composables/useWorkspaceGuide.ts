import type { ResumeDraftContent } from '@/api/resumeDraft'
import { starCompleteness, type WorkspaceSectionKey } from '@/utils/resumeWizard'

/**
 * 生成简历模式的前端引导引擎。
 * <p>
 * 只做确定性检测：读取草稿内容判断「下一步该问什么」，已提供的信息不重复询问，
 * 不调用 AI，也不编造任何用户未提供的事实。
 */
export interface WorkspaceGuide {
  /** 建议进入的步骤（1-8） */
  step: number
  section: WorkspaceSectionKey
  title: string
  question: string
  /** 关键缺失项，用于提示用户 */
  missing: string[]
  /** true 表示该步骤可跳过 */
  optional: boolean
  /** 八步所需信息是否已经齐全 */
  ready: boolean
}

const GUIDE_STEPS = [
  { step: 1, section: 'BASIC' as WorkspaceSectionKey, title: '基础信息' },
  { step: 2, section: 'JOB_INTENTION' as WorkspaceSectionKey, title: '画像与方向' },
  { step: 3, section: 'EXPERIENCE' as WorkspaceSectionKey, title: '经历补充' },
  { step: 4, section: 'EXPERIENCE' as WorkspaceSectionKey, title: 'STAR 结构化' },
  { step: 5, section: 'SELF_EVALUATION' as WorkspaceSectionKey, title: '软性信息' },
  { step: 6, section: 'PHOTO' as WorkspaceSectionKey, title: '证件照' },
  { step: 8, section: 'BASIC' as WorkspaceSectionKey, title: '生成简历' },
]

function basicMissing(content: ResumeDraftContent): string[] {
  const missing: string[] = []
  if (!content.basic.realName.trim()) {
    missing.push('姓名')
  }
  if (!content.basic.phone.trim() && !content.basic.email?.trim()) {
    missing.push('手机号或邮箱')
  }
  return missing
}

function hasExperienceContent(content: ResumeDraftContent): boolean {
  return (
    content.educations.some((item) => item.school.trim()) ||
    content.experiences.some((item) => item.company.trim() || item.position.trim()) ||
    content.projects.some((item) => item.projectName.trim())
  )
}

function needsStar(content: ResumeDraftContent): boolean {
  const items = [
    ...content.experiences.map((item) => item.star),
    ...content.projects.map((item) => item.star),
  ]
  if (items.length === 0) {
    return false
  }
  return items.some((star) => starCompleteness(star) === 0)
}

/** 计算当前草稿的下一步引导。 */
export function computeGuide(content: ResumeDraftContent): WorkspaceGuide {
  const missingBasic = basicMissing(content)
  if (missingBasic.length > 0) {
    return {
      step: 1,
      section: 'BASIC',
      title: '基础信息',
      question: `先认识一下：请补充${missingBasic.join('、')}，这些信息会显示在简历顶部。`,
      missing: missingBasic,
      optional: false,
      ready: false,
    }
  }

  if (!content.direction.targetPosition?.trim()) {
    return {
      step: 2,
      section: 'JOB_INTENTION',
      title: '画像与方向',
      question: '你想投什么岗位、在哪个城市？确认求职方向后，我才能帮你把内容写得更贴合目标。',
      missing: ['目标岗位'],
      optional: false,
      ready: false,
    }
  }

  if (!hasExperienceContent(content)) {
    return {
      step: 3,
      section: 'EXPERIENCE',
      title: '经历补充',
      question: '接下来补充一段教育、项目、实习或工作经历。哪怕只有一段校园项目，也能帮你写得更具体。',
      missing: ['教育 / 项目 / 实习经历'],
      optional: false,
      ready: false,
    }
  }

  if (needsStar(content)) {
    return {
      step: 4,
      section: 'EXPERIENCE',
      title: 'STAR 结构化',
      question:
        '有一段经历还缺少情境、任务、行动、结果的细节。补全后 AI 才能在不编造的前提下做专业表达润色。',
      missing: ['STAR 四要素'],
      optional: true,
      ready: false,
    }
  }

  if (!content.soft.selfEvaluation.trim() && content.soft.strengths.length === 0) {
    return {
      step: 5,
      section: 'SELF_EVALUATION',
      title: '软性信息',
      question: '最后补充一段自我评价或个人优势，用来概括你的做事风格与求职动机。',
      missing: ['自我评价 / 个人优势'],
      optional: true,
      ready: false,
    }
  }

  if (!content.photo.dataUrl) {
    return {
      step: 6,
      section: 'PHOTO',
      title: '证件照',
      question: '需要添加证件照吗？部分模板会展示照片，没有照片也可以直接生成简历。',
      missing: ['证件照'],
      optional: true,
      ready: false,
    }
  }

  return {
    step: 8,
    section: 'BASIC',
    title: '生成简历',
    question: '信息已经齐全，可以点击顶部「生成简历」创建新版本，之后随时可以在右侧预览里继续调整。',
    missing: [],
    optional: true,
    ready: true,
  }
}

/** 引导消息正文：保留标题行，便于对话气泡展示。 */
export function guideText(guide: WorkspaceGuide): string {
  if (guide.ready) {
    return `【第 8 步 · ${guide.title}】${guide.question}`
  }
  const suffix = guide.optional ? '（非必填，可以跳过）' : ''
  return `【第 ${guide.step} 步 · ${guide.title}】${guide.question}${suffix}`
}

/** 信息完整度（0-100），用于顶部和工作台的进度提示。 */
export function guideProgress(content: ResumeDraftContent): number {
  const total = GUIDE_STEPS.length
  let done = 0
  if (basicMissing(content).length === 0) done += 1
  if (content.direction.targetPosition?.trim()) done += 1
  if (hasExperienceContent(content)) done += 1
  if (!needsStar(content)) done += 1
  if (content.soft.selfEvaluation.trim() || content.soft.strengths.length > 0) done += 1
  if (content.photo.dataUrl) done += 1
  const guide = computeGuide(content)
  if (guide.ready) done += 1
  return Math.round((done / total) * 100)
}
