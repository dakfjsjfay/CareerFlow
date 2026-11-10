import { describe, expect, it } from 'vitest'

import { applyContent, collectRenderedText } from './adapter'
import { TEMPLATES } from './manifest'
import type { ResumeContent, TemplateJson } from './types'

const templateModules = import.meta.glob<{ default: TemplateJson }>('./templates/*.json', {
  eager: true,
})

function loadTemplate(id: string): TemplateJson {
  const module = templateModules[`./templates/${id}.json`]
  return structuredClone(module.default)
}

function content(overrides: Partial<ResumeContent> = {}): ResumeContent {
  return {
    meta: { templateId: 'tpl-1', templateName: '模板一', warnings: [] },
    personalInfo: {
      realName: '张同学',
      phone: '13800000000',
      email: null,
      city: '深圳',
      avatar: null,
      school: '深圳大学',
      major: '软件工程',
      degree: '本科',
      graduationYear: 2028,
      yearsOfExperience: 0,
      abstract: '软件工程在读，掌握 Java。',
    },
    jobIntention: {
      targetPosition: 'Java 开发实习生',
      targetCity: '深圳',
      expectedSalary: '面议',
    },
    education: [
      {
        school: '深圳大学',
        major: '软件工程',
        degree: '本科',
        graduationYear: 2028,
        startDate: null,
        endDate: null,
      },
    ],
    skills: ['Java', 'MySQL'],
    projects: [
      {
        projectName: '学生信息管理系统',
        role: '后端开发',
        description: '实现增删改查',
        techStack: ['Java', 'MySQL'],
        highlights: ['完成接口开发'],
        startDate: '2025-03-01',
        endDate: '2025-06-30',
      },
    ],
    experiences: [
      {
        company: '某科技有限公司',
        position: '后端实习生',
        type: 'INTERNSHIP',
        description: '参与接口开发',
        achievements: '完成 3 个接口',
        highlights: [],
        startDate: '2025-07-01',
        endDate: '2025-09-30',
      },
    ],
    selfEvaluation: '熟悉 Java 与 MySQL。',
    ...overrides,
  }
}

function modulesByType(template: TemplateJson, type: string) {
  return template.componentsTree.filter((module) => module.componentName.startsWith(`${type}_`))
}

describe('resume template adapter', () => {
  it('maps base info and hides fields the profile does not have', () => {
    const template = applyContent(loadTemplate('6746fe472f0052cb6a9365aa'), content(), '我的简历')
    const baseInfo = modulesByType(template, 'BaseInfo')[0]

    expect(baseInfo.dataSource.name.value).toBe('张同学')
    expect(baseInfo.dataSource.phoneNumber.value).toBe('13800000000')
    expect(baseInfo.dataSource.degree.value).toBe('本科')
    expect(baseInfo.dataSource.avatar.value).toBe('')
    expect(baseInfo.props.avatar.show).toBe(false)
    expect(baseInfo.props.age.show).toBe(false)
  })

  it('hides empty list modules', () => {
    const template = applyContent(
      loadTemplate('6746fe472f0052cb6a9365aa'),
      content({ projects: [], experiences: [], skills: [] }),
      '我的简历',
    )

    expect(modulesByType(template, 'ProjectExperience')[0].customProps.showModule).toBe(false)
    expect(modulesByType(template, 'WorkExperience')[0].customProps.showModule).toBe(false)
    expect(modulesByType(template, 'InternshipExperience')[0].customProps.showModule).toBe(false)
    expect(modulesByType(template, 'SkillSpecialties')[0].customProps.showModule).toBe(false)
  })

  it('splits experiences into work and internship modules', () => {
    const template = applyContent(
      loadTemplate('6746fe472f0052cb6a9365aa'),
      content({
        experiences: [
          {
            company: '某公司',
            position: '后端工程师',
            type: 'WORK',
            description: '开发接口',
            achievements: null,
            highlights: [],
            startDate: '2024-01-01',
            endDate: '2024-12-31',
          },
          {
            company: '另一公司',
            position: '实习生',
            type: 'INTERNSHIP',
            description: '联调接口',
            achievements: null,
            highlights: [],
            startDate: '2025-01-01',
            endDate: '2025-06-30',
          },
        ],
      }),
      '我的简历',
    )

    expect(modulesByType(template, 'WorkExperience')[0].dataSource.list.value).toHaveLength(1)
    expect(modulesByType(template, 'InternshipExperience')[0].dataSource.list.value).toHaveLength(1)
  })

  it('maps project fields and jobContent bullets', () => {
    const template = applyContent(loadTemplate('6746fe472f0052cb6a9365aa'), content(), '我的简历')
    const project = modulesByType(template, 'ProjectExperience')[0].dataSource.list.value[0]

    expect(project.companyName.value).toBe('学生信息管理系统')
    expect(project.posts.value).toBe('后端开发')
    expect(project.jobContent.value.length).toBeGreaterThan(0)
    expect(project.jobContent.value[0].content.value).toContain('实现增删改查')
  })

  it('escapes html in user descriptions', () => {
    const template = applyContent(
      loadTemplate('6746fe472f0052cb6a9365aa'),
      content({
        projects: [
          {
            projectName: 'XSS',
            role: '开发',
            description: '<script>alert(1)</script>',
            techStack: [],
            highlights: [],
            startDate: null,
            endDate: null,
          },
        ],
      }),
      '我的简历',
    )

    const text = collectRenderedText(template)
    expect(text).not.toContain('<script>')
    expect(text).toContain('&lt;script&gt;')
  })

  it('never leaks template placeholder data for any of the 20 templates', () => {
    const placeholders = ['化简', '小猪课堂', '1584775297@qq.com', '028-83032406']

    for (const meta of TEMPLATES) {
      const rendered = collectRenderedText(applyContent(loadTemplate(meta.id), content(), '我的简历'))
      for (const placeholder of placeholders) {
        expect(rendered, `${meta.name} leaked ${placeholder}`).not.toContain(placeholder)
      }
    }
  })
})
