import { describe, expect, it } from 'vitest'

import type { DraftExperience, ResumeDraftContent } from '@/api/resumeDraft'

import {
  applySectionToDraftContent,
  buildDirectionTags,
  createEmptyDraftContent,
  draftContentToPreview,
  moveItem,
  validateStep,
} from './resumeWizard'

function contentWithBasic(): ResumeDraftContent {
  return createEmptyDraftContent()
}

function experience(overrides: Partial<DraftExperience> = {}): DraftExperience {
  return {
    id: 'exp-1',
    kind: 'WORK',
    company: '某某科技',
    position: '后端实习生',
    type: 'WORK',
    startDate: '2025-07',
    endDate: '2025-10',
    description: '',
    achievements: '接口响应时间降低 30%',
    star: { situation: '', task: '', action: '', result: '' },
    skills: ['Java'],
    fieldSources: {},
    ...overrides,
  }
}

describe('validateStep', () => {
  it('要求第 1 步填写姓名与至少一种联系方式', () => {
    const content = contentWithBasic()

    expect(validateStep(content, 1)).toEqual(['请填写姓名', '请至少填写手机号或邮箱'])

    content.basic.realName = '张三'
    expect(validateStep(content, 1)).toEqual(['请至少填写手机号或邮箱'])

    content.basic.phone = '13800000000'
    expect(validateStep(content, 1)).toEqual([])
  })

  it('只填邮箱也可以通过第 1 步', () => {
    const content = contentWithBasic()
    content.basic.realName = '张三'
    content.basic.email = 'zhangsan@example.com'

    expect(validateStep(content, 1)).toEqual([])
  })

  it('第 3 步要求至少一条教育经历或项目/实习经历', () => {
    const content = contentWithBasic()

    expect(validateStep(content, 3)).toEqual(['请至少添加一条教育经历或项目/实习经历'])

    content.educations.push({
      id: 'edu-1',
      school: '深圳大学',
      major: '软件工程',
      degree: '本科',
      graduationYear: 2027,
      startDate: null,
      endDate: null,
      fieldSources: {},
    })

    expect(validateStep(content, 3)).toEqual([])
  })

  it('第 3 步只有项目经历时也视为通过', () => {
    const content = contentWithBasic()
    content.experiences.push(experience())

    expect(validateStep(content, 3)).toEqual([])
  })
})

describe('buildDirectionTags', () => {
  it('按行业、岗位、城市、薪资、技能生成标签并去重', () => {
    const direction = {
      industry: '互联网',
      targetPosition: 'Java 开发',
      targetCity: '深圳',
      expectedSalary: '8-12K',
      jobGoal: '希望做后端开发',
      directionTags: [],
    }

    expect(buildDirectionTags(direction, ['深圳', 'Java', 'MySQL', 'Redis'])).toEqual([
      '互联网',
      'Java 开发',
      '深圳',
      '8-12K',
      'Java',
      'MySQL',
    ])
  })

  it('最多返回 8 个标签（技能只取前 3 个）', () => {
    const direction = {
      industry: '互联网',
      targetPosition: 'Java 开发',
      targetCity: '深圳',
      expectedSalary: '8-12K',
      jobGoal: '',
      directionTags: [],
    }

    const tags = buildDirectionTags(direction, ['A', 'B', 'C', 'D', 'E', 'F', 'G'])

    expect(tags).toHaveLength(7)
  })
})

describe('moveItem', () => {
  it('把元素移动到目标位置', () => {
    expect(moveItem(['a', 'b', 'c', 'd'], 0, 2)).toEqual(['b', 'c', 'a', 'd'])
    expect(moveItem(['a', 'b', 'c', 'd'], 3, 1)).toEqual(['a', 'd', 'b', 'c'])
  })

  it('位置非法或相同时保持原顺序', () => {
    const list = ['a', 'b']
    expect(moveItem(list, 1, 1)).toEqual(['a', 'b'])
    expect(moveItem(list, -1, 1)).toEqual(['a', 'b'])
    expect(moveItem(list, 0, 5)).toEqual(['a', 'b'])
  })
})

describe('applySectionToDraftContent', () => {
  it('合并基础信息且不修改原对象', () => {
    const content = contentWithBasic()
    content.basic.realName = '张三'

    const next = applySectionToDraftContent(content, 'BASIC', {
      realName: '李四',
      city: '深圳',
    })

    expect(next.basic.realName).toBe('李四')
    expect(next.basic.city).toBe('深圳')
    expect(content.basic.realName).toBe('张三')
  })

  it('替换经历数组', () => {
    const content = contentWithBasic()

    const next = applySectionToDraftContent(content, 'EXPERIENCE', [experience()])

    expect(next.experiences).toHaveLength(1)
    expect(next.experiences[0].company).toBe('某某科技')
    expect(content.experiences).toHaveLength(0)
  })

  it('合并软性信息', () => {
    const content = contentWithBasic()

    const next = applySectionToDraftContent(content, 'SOFT', {
      selfEvaluation: '沟通顺畅',
      strengths: ['执行力'],
    })

    expect(next.soft.selfEvaluation).toBe('沟通顺畅')
    expect(next.soft.strengths).toEqual(['执行力'])
  })

  it('未知区块返回内容副本', () => {
    const content = contentWithBasic()

    const next = applySectionToDraftContent(content, 'UNKNOWN', { foo: 'bar' })

    expect(next).not.toBe(content)
    expect(next.basic).toEqual(content.basic)
  })
})

describe('draftContentToPreview', () => {
  it('把草稿字段映射为模板渲染内容', () => {
    const content = contentWithBasic()
    content.basic = {
      realName: '张三',
      phone: '13800000000',
      email: 'zhangsan@example.com',
      city: '深圳',
      school: '深圳大学',
      major: '软件工程',
      degree: '本科',
      graduationYear: 2027,
      educationStatus: '在读',
    }
    content.direction = {
      industry: '互联网',
      targetPosition: 'Java 开发',
      targetCity: '深圳',
      expectedSalary: '8-12K',
      jobGoal: '希望做后端开发',
      directionTags: ['互联网', '深圳'],
    }
    content.photo = { dataUrl: 'data:image/jpeg;base64,abc', assetId: null, width: 295, height: 413 }
    content.educations.push({
      id: 'edu-1',
      school: '深圳大学',
      major: '软件工程',
      degree: '本科',
      graduationYear: 2027,
      startDate: '2023-09',
      endDate: '2027-06',
      fieldSources: {},
    })
    content.experiences.push(
      experience({
        description: '',
        star: { situation: '项目背景', task: '负责接口', action: '', result: '性能提升 30%' },
      }),
    )
    content.projects.push({
      id: 'proj-1',
      projectName: '求职助手',
      role: '后端负责人',
      description: '',
      techStack: ['Java', 'MySQL'],
      highlights: ['支持八步创建'],
      startDate: null,
      endDate: null,
      star: { situation: '', task: '', action: '', result: '' },
      fieldSources: {},
    })
    content.skills = ['Java', '', 'MySQL']
    content.soft = { selfEvaluation: '', strengths: ['沟通', '执行'], suggestions: [] }
    content.preview.templateId = '6746fe472f0052cb6a9365aa'

    const preview = draftContentToPreview(content)

    expect(preview.meta.templateId).toBe('6746fe472f0052cb6a9365aa')
    expect(preview.personalInfo).toMatchObject({
      realName: '张三',
      phone: '13800000000',
      email: 'zhangsan@example.com',
      city: '深圳',
      avatar: 'data:image/jpeg;base64,abc',
      school: '深圳大学',
      major: '软件工程',
      degree: '本科',
      graduationYear: 2027,
    })
    expect(preview.jobIntention).toEqual({
      targetPosition: 'Java 开发',
      targetCity: '深圳',
      expectedSalary: '8-12K',
    })
    expect(preview.education).toEqual([
      {
        school: '深圳大学',
        major: '软件工程',
        degree: '本科',
        graduationYear: 2027,
        startDate: '2023-09',
        endDate: '2027-06',
      },
    ])
    expect(preview.experiences[0].company).toBe('某某科技')
    expect(preview.experiences[0].type).toBe('WORK')
    expect(preview.experiences[0].description).toBe('项目背景\n负责接口\n性能提升 30%')
    expect(preview.projects[0]).toMatchObject({
      projectName: '求职助手',
      role: '后端负责人',
      techStack: ['Java', 'MySQL'],
      highlights: ['支持八步创建'],
    })
    expect(preview.skills).toEqual(['Java', 'MySQL'])
    expect(preview.selfEvaluation).toBe('沟通；执行')
  })

  it('实习经历映射为 INTERNSHIP 类型', () => {
    const content = contentWithBasic()
    content.experiences.push(experience({ kind: 'INTERNSHIP' }))

    const preview = draftContentToPreview(content)

    expect(preview.experiences[0].type).toBe('INTERNSHIP')
  })
})
