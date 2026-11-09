import { describe, expect, it } from 'vitest'
import type { EvidenceItem } from '@/api/profile'

import { groupEvidence } from './evidenceDisplay'

function evidence(overrides: Partial<EvidenceItem> = {}): EvidenceItem {
  return {
    id: 1,
    entityType: 'PROFILE',
    entityId: null,
    fieldName: 'real_name',
    sourceType: 'CHAT',
    sourceId: 10,
    sourceContent: '我叫张三',
    evidenceContent: '张三',
    confirmationStatus: 'PENDING',
    createdAt: '2026-11-10T10:00:00',
    ...overrides,
  }
}

describe('groupEvidence', () => {
  it('groups mixed evidence by dimension in display order', () => {
    const groups = groupEvidence([
      evidence({ id: 1, fieldName: 'target_city', evidenceContent: '杭州' }),
      evidence({ id: 2, entityType: 'SKILL', fieldName: 'skills', evidenceContent: 'Java' }),
      evidence({
        id: 3,
        entityType: 'PROJECT',
        fieldName: 'project',
        evidenceContent: JSON.stringify({
          projectName: '学生管理系统',
          role: '后端开发',
          techStack: ['Java', 'MySQL'],
          startDate: '2025-03',
          endDate: '2025-06',
        }),
      }),
      evidence({ id: 4, fieldName: 'real_name', evidenceContent: '张三' }),
      evidence({ id: 5, fieldName: 'school', evidenceContent: '某某大学' }),
      evidence({
        id: 6,
        entityType: 'EXPERIENCE',
        fieldName: 'experience',
        evidenceContent: JSON.stringify({
          company: '某某科技',
          position: '后端实习生',
          startDate: '2025-07',
        }),
      }),
      evidence({ id: 7, fieldName: 'graduation_year', evidenceContent: '2027' }),
    ])

    expect(
      groups.map((group) => ({
        title: group.title,
        items: group.items.map((item) => ({ label: item.label, value: item.value })),
      })),
    ).toEqual([
      { title: '基础信息', items: [{ label: '姓名', value: '张三' }] },
      {
        title: '教育经历',
        items: [
          { label: '学校', value: '某某大学' },
          { label: '毕业年份', value: '2027' },
        ],
      },
      { title: '技能', items: [{ label: '技能', value: 'Java' }] },
      {
        title: '项目经历',
        items: [
          {
            label: '项目',
            value: '学生管理系统 · 后端开发 · Java、MySQL · 2025-03 ~ 2025-06',
          },
        ],
      },
      {
        title: '工作/实习经历',
        items: [{ label: '经历', value: '某某科技 · 后端实习生 · 2025-07 至今' }],
      },
      { title: '求职意向', items: [{ label: '意向城市', value: '杭州' }] },
    ])
  })

  it('falls back to raw content when project JSON is malformed', () => {
    const groups = groupEvidence([
      evidence({ entityType: 'PROJECT', fieldName: 'project', evidenceContent: '{not-json' }),
    ])

    expect(groups).toHaveLength(1)
    expect(groups[0].items[0].value).toBe('{not-json')
  })

  it('keeps unknown entity types in the other group', () => {
    const groups = groupEvidence([
      evidence({ entityType: 'CERTIFICATE', fieldName: 'cert', evidenceContent: 'CET-6' }),
    ])

    expect(groups).toEqual([
      expect.objectContaining({
        title: '其他信息',
        items: [expect.objectContaining({ label: 'cert', value: 'CET-6' })],
      }),
    ])
  })

  it('formats a project with only a name without trailing separators', () => {
    const groups = groupEvidence([
      evidence({
        entityType: 'PROJECT',
        fieldName: 'project',
        evidenceContent: '{"projectName":"课程设计"}',
      }),
    ])

    expect(groups[0].items[0].value).toBe('课程设计')
  })

  it('returns no groups for an empty list', () => {
    expect(groupEvidence([])).toEqual([])
  })
})
