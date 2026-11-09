import type { EvidenceItem } from '@/api/profile'

type GroupKey =
  | 'basic'
  | 'education'
  | 'skills'
  | 'projects'
  | 'experiences'
  | 'intention'
  | 'other'

export interface EvidenceDisplayItem {
  id: number
  label: string
  value: string
  sourceContent: string | null
}

export interface EvidenceGroup {
  key: GroupKey
  title: string
  items: EvidenceDisplayItem[]
}

const GROUP_ORDER: Array<{ key: GroupKey; title: string }> = [
  { key: 'basic', title: '基础信息' },
  { key: 'education', title: '教育经历' },
  { key: 'skills', title: '技能' },
  { key: 'projects', title: '项目经历' },
  { key: 'experiences', title: '工作/实习经历' },
  { key: 'intention', title: '求职意向' },
  { key: 'other', title: '其他信息' },
]

const PROFILE_FIELDS: Record<string, { group: GroupKey; label: string }> = {
  real_name: { group: 'basic', label: '姓名' },
  phone: { group: 'basic', label: '电话' },
  school: { group: 'education', label: '学校' },
  major: { group: 'education', label: '专业' },
  degree: { group: 'education', label: '学历' },
  graduation_year: { group: 'education', label: '毕业年份' },
  target_position: { group: 'intention', label: '目标岗位' },
  target_city: { group: 'intention', label: '意向城市' },
  expected_salary: { group: 'intention', label: '期望薪资' },
}

/** 把后端证据列表整理成前端可展示的分组；未知类型保留原文，绝不丢数据。 */
export function groupEvidence(evidence: EvidenceItem[]): EvidenceGroup[] {
  const grouped = new Map<GroupKey, EvidenceDisplayItem[]>()

  for (const item of evidence) {
    const { group, label } = describe(item)
    const items = grouped.get(group) ?? []
    items.push({
      id: item.id,
      label,
      value: formatValue(item),
      sourceContent: item.sourceContent,
    })
    grouped.set(group, items)
  }

  return GROUP_ORDER.filter(({ key }) => grouped.has(key)).map(({ key, title }) => ({
    key,
    title,
    items: grouped.get(key)!,
  }))
}

function describe(item: EvidenceItem): { group: GroupKey; label: string } {
  if (item.entityType === 'PROFILE') {
    return PROFILE_FIELDS[item.fieldName] ?? { group: 'other', label: item.fieldName }
  }
  if (item.entityType === 'SKILL') {
    return { group: 'skills', label: '技能' }
  }
  if (item.entityType === 'PROJECT') {
    return { group: 'projects', label: '项目' }
  }
  if (item.entityType === 'EXPERIENCE') {
    return { group: 'experiences', label: '经历' }
  }
  return { group: 'other', label: item.fieldName }
}

function formatValue(item: EvidenceItem): string {
  if (item.entityType === 'PROJECT') {
    return formatProject(item.evidenceContent)
  }
  if (item.entityType === 'EXPERIENCE') {
    return formatExperience(item.evidenceContent)
  }
  return item.evidenceContent
}

function formatProject(content: string): string {
  const data = parseObject(content)
  if (!data) {
    return content
  }
  const techStack = Array.isArray(data.techStack)
    ? data.techStack.filter(isText).map((tech) => tech.trim())
    : []
  return [
    text(data.projectName),
    text(data.role),
    techStack.join('、'),
    formatRange(text(data.startDate), text(data.endDate)),
  ]
    .filter(Boolean)
    .join(' · ')
}

function formatExperience(content: string): string {
  const data = parseObject(content)
  if (!data) {
    return content
  }
  return [
    text(data.company),
    text(data.position),
    formatRange(text(data.startDate), text(data.endDate)),
  ]
    .filter(Boolean)
    .join(' · ')
}

function parseObject(content: string): Record<string, unknown> | null {
  try {
    const parsed: unknown = JSON.parse(content)
    if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
      return parsed as Record<string, unknown>
    }
    return null
  } catch {
    return null
  }
}

function text(value: unknown): string {
  return typeof value === 'string' ? value.trim() : ''
}

function isText(value: unknown): value is string {
  return typeof value === 'string' && value.trim().length > 0
}

function formatRange(start: string, end: string): string {
  if (start && end) {
    return `${start} ~ ${end}`
  }
  if (start) {
    return `${start} 至今`
  }
  if (end) {
    return `至 ${end}`
  }
  return ''
}
