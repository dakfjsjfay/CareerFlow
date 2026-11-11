import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { nextTick } from 'vue'
import VueDOMPurifyHTML from 'vue-dompurify-html'
import { describe, expect, it, vi } from 'vitest'

import { applyContent } from './adapter'
import { TEMPLATES } from './manifest'
import ResumeTemplateRenderer from './ResumeTemplateRenderer.vue'
import SvgIcon from './SvgIcon.vue'
import type { ResumeContent, TemplateJson } from './types'

const templateModules = import.meta.glob<{ default: TemplateJson }>('./templates/*.json', {
  eager: true,
})

function loadTemplate(id: string): TemplateJson {
  return structuredClone(templateModules[`./templates/${id}.json`].default)
}

function sampleContent(): ResumeContent {
  return {
    meta: { templateId: 'tpl-1', warnings: [] },
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
        highlights: [],
        startDate: null,
        endDate: null,
      },
    ],
    experiences: [],
    selfEvaluation: '熟悉 Java 与 MySQL。',
  }
}

function mountRenderer(templateId: string) {
  return mount(ResumeTemplateRenderer, {
    props: {
      template: loadTemplate(templateId),
      content: sampleContent(),
      resumeName: '我的简历',
    },
    global: {
      plugins: [ElementPlus, VueDOMPurifyHTML],
      components: { SvgIcon },
    },
  })
}

describe('ResumeTemplateRenderer', () => {
  it('renders a base page template with content', async () => {
    const wrapper = mountRenderer('6746fe472f0052cb6a9365aa')
    await nextTick()

    expect(wrapper.text()).toContain('张同学')
    expect(wrapper.text()).toContain('Java')
  })

  it('renders a two-column page and keeps modules missing layout', async () => {
    const template = loadTemplate('674701372f0052cb6a93660b')
    const first = template.componentsTree.find((module) => module.customProps?.showModule !== false)
    if (first) {
      first.customProps = { ...first.customProps, layout: undefined }
    }
    const applied = applyContent(template, sampleContent(), '我的简历')
    const expected = applied.componentsTree.filter(
      (module) => module.customProps?.showModule !== false,
    ).length

    const wrapper = mount(ResumeTemplateRenderer, {
      props: { template, content: sampleContent(), resumeName: '我的简历' },
      global: {
        plugins: [ElementPlus, VueDOMPurifyHTML],
        components: { SvgIcon },
      },
    })
    await nextTick()

    expect(wrapper.findAll('.create-template-elemet')).toHaveLength(expected)
    expect(wrapper.text()).toContain('张同学')
  })

  it('renders the Page_1 layout', async () => {
    const wrapper = mountRenderer('674ef7a1499a3d6818e5f3e9')
    await nextTick()

    expect(wrapper.text()).toContain('张同学')
    expect(wrapper.findAll('.line-top').length).toBe(1)
  })

  it('renders all 20 templates without unresolved components', async () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => undefined)
    try {
      for (const template of TEMPLATES) {
        const wrapper = mountRenderer(template.id)
        await nextTick()
        expect(wrapper.text().length, template.name).toBeGreaterThan(0)
        wrapper.unmount()
      }
      const unresolved = warn.mock.calls.filter((args) =>
        String(args[0]).includes('Failed to resolve component'),
      )
      expect(unresolved).toHaveLength(0)
    } finally {
      warn.mockRestore()
    }
  })
})
