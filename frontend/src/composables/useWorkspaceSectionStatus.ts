import type { ResumeDraftContent, SectionAnalysis } from '@/api/resumeDraft'
import { SECTION_LABELS, type WorkspaceSectionKey } from '@/utils/resumeWizard'

/** 左栏模块目录展示顺序（与预览区块锚点一致） */
export const NAV_SECTIONS: WorkspaceSectionKey[] = [
  'BASIC',
  'JOB_INTENTION',
  'EDUCATION',
  'EXPERIENCE',
  'PROJECT',
  'SKILL',
  'SELF_EVALUATION',
  'PHOTO',
]

export type SectionTone = 'empty' | 'unanalyzed' | 'good' | 'warn' | 'risk'

export interface SectionStatus {
  key: WorkspaceSectionKey
  label: string
  tone: SectionTone
  text: string
  score: number | null
  issueCount: number
}

/** 确定性判断区块是否还没有内容，不调用 AI。 */
export function isSectionEmpty(
  content: ResumeDraftContent,
  key: WorkspaceSectionKey,
): boolean {
  switch (key) {
    case 'BASIC':
      return (
        !content.basic.realName.trim() &&
        !content.basic.phone.trim() &&
        !content.basic.email?.trim()
      )
    case 'JOB_INTENTION':
      return (
        !content.direction.targetPosition?.trim() &&
        !content.direction.targetCity?.trim() &&
        !content.direction.expectedSalary?.trim()
      )
    case 'EDUCATION':
      return !content.educations.some((item) => item.school.trim())
    case 'EXPERIENCE':
      return !content.experiences.some(
        (item) => item.company.trim() || item.position.trim() || item.description.trim(),
      )
    case 'PROJECT':
      return !content.projects.some(
        (item) => item.projectName.trim() || item.description.trim(),
      )
    case 'SKILL':
      return !content.skills.some((skill) => skill.trim())
    case 'SELF_EVALUATION':
      return (
        !content.soft.selfEvaluation.trim() &&
        !content.soft.strengths.some((item) => item.trim())
      )
    case 'PHOTO':
      return !content.photo.dataUrl
    default:
      return true
  }
}

function toneOf(score: number): { tone: SectionTone; text: string } {
  if (score >= 80) {
    return { tone: 'good', text: '良好' }
  }
  if (score >= 60) {
    return { tone: 'warn', text: '待改进' }
  }
  return { tone: 'risk', text: '需重点优化' }
}

/** 单个区块状态：未填写 > 有分析结果 > 待分析。 */
export function sectionStatus(
  content: ResumeDraftContent,
  key: WorkspaceSectionKey,
  analysis?: SectionAnalysis | null,
): SectionStatus {
  const label = SECTION_LABELS[key] ?? key
  if (isSectionEmpty(content, key)) {
    return { key, label, tone: 'empty', text: '未填写', score: null, issueCount: 0 }
  }
  if (analysis) {
    const { tone, text } = toneOf(analysis.score)
    return {
      key,
      label,
      tone,
      text,
      score: analysis.score,
      issueCount: analysis.issues.length,
    }
  }
  return { key, label, tone: 'unanalyzed', text: '待分析', score: null, issueCount: 0 }
}

/** 按固定顺序生成左栏目录项。 */
export function buildSectionStatuses(
  content: ResumeDraftContent,
  analyses: Partial<Record<WorkspaceSectionKey, SectionAnalysis>>,
): SectionStatus[] {
  return NAV_SECTIONS.map((key) => sectionStatus(content, key, analyses[key] ?? null))
}
