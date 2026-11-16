import { describe, expect, it } from 'vitest'

import { createEmptyDraftContent } from '@/utils/resumeWizard'
import {
  buildSectionStatuses,
  isSectionEmpty,
  NAV_SECTIONS,
  sectionStatus,
} from './useWorkspaceSectionStatus'

function content() {
  return createEmptyDraftContent()
}

describe('isSectionEmpty', () => {
  it('空白草稿所有区块都视为未填写', () => {
    const draft = content()
    expect(NAV_SECTIONS.every((key) => isSectionEmpty(draft, key))).toBe(true)
  })

  it('填写姓名后基本信息不再为空', () => {
    const draft = content()
    draft.basic.realName = '张三'
    expect(isSectionEmpty(draft, 'BASIC')).toBe(false)
  })

  it('教育经历需要学校名才算已填写', () => {
    const draft = content()
    draft.educations = [
      {
        id: 'e1',
        school: '',
        major: '软件工程',
        degree: '本科',
        graduationYear: null,
        startDate: null,
        endDate: null,
        fieldSources: {},
      },
    ]
    expect(isSectionEmpty(draft, 'EDUCATION')).toBe(true)
    draft.educations[0].school = '深圳大学'
    expect(isSectionEmpty(draft, 'EDUCATION')).toBe(false)
  })

  it('技能为空白字符串时仍视为未填写', () => {
    const draft = content()
    draft.skills = ['  ']
    expect(isSectionEmpty(draft, 'SKILL')).toBe(true)
  })
})

describe('sectionStatus', () => {
  it('未填写优先级高于分析结果', () => {
    const status = sectionStatus(content(), 'BASIC', {
      sectionKey: 'BASIC',
      score: 95,
      issues: [],
      suggestions: [],
      missingFields: [],
      factsConfirmed: true,
    })
    expect(status.tone).toBe('empty')
    expect(status.text).toBe('未填写')
  })

  it('有内容未分析时为待分析', () => {
    const draft = content()
    draft.basic.realName = '张三'
    const status = sectionStatus(draft, 'BASIC')
    expect(status.tone).toBe('unanalyzed')
    expect(status.score).toBeNull()
  })

  it('按分数划分良好 / 待改进 / 需重点优化', () => {
    const draft = content()
    draft.basic.realName = '张三'
    const make = (score: number) => ({
      sectionKey: 'BASIC',
      score,
      issues: ['问题一', '问题二'],
      suggestions: [],
      missingFields: [],
      factsConfirmed: true,
    })

    expect(sectionStatus(draft, 'BASIC', make(85)).tone).toBe('good')
    expect(sectionStatus(draft, 'BASIC', make(70)).tone).toBe('warn')
    expect(sectionStatus(draft, 'BASIC', make(50)).tone).toBe('risk')
    const risk = sectionStatus(draft, 'BASIC', make(50))
    expect(risk.issueCount).toBe(2)
    expect(risk.label).toBe('基本信息')
  })
})

describe('buildSectionStatuses', () => {
  it('按固定顺序返回全部模块', () => {
    const items = buildSectionStatuses(content(), {})
    expect(items.map((item) => item.key)).toEqual(NAV_SECTIONS)
    expect(items).toHaveLength(8)
  })
})
