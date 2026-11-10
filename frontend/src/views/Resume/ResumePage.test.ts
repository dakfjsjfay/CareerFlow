import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import VueDOMPurifyHTML from 'vue-dompurify-html'

const api = vi.hoisted(() => ({
  createResume: vi.fn(),
  exportResumeDocx: vi.fn(),
  generateResume: vi.fn(),
  getResume: vi.fn(),
  getResumeVersion: vi.fn(),
  listResumeVersions: vi.fn(),
  listResumes: vi.fn(),
  saveResumeVersion: vi.fn(),
}))

vi.mock('@/api/resume', () => api)

import SvgIcon from '@/components/resume-template/SvgIcon.vue'
import type { ResumeContent } from '@/components/resume-template/types'

import ResumePage from './index.vue'

function content(selfEvaluation = '熟悉 Java。'): ResumeContent {
  return {
    meta: { templateId: '6746fe472f0052cb6a9365aa', warnings: [] },
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
      abstract: '软件工程在读。',
    },
    jobIntention: { targetPosition: 'Java 开发实习生', targetCity: '深圳', expectedSalary: '面议' },
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
    projects: [],
    experiences: [],
    selfEvaluation,
  }
}

const summary = {
  id: 1,
  resumeName: '我的简历',
  currentVersionId: 1,
  currentVersionNumber: 1,
  templateId: '6746fe472f0052cb6a9365aa',
  updatedAt: null,
}

const versionSummary = {
  versionId: 1,
  versionNumber: 1,
  generationReason: 'INITIAL',
  templateId: '6746fe472f0052cb6a9365aa',
  createdAt: '2026-11-10T12:00:00',
}

const version = {
  ...versionSummary,
  content: content(),
}

const detail = {
  id: 1,
  resumeName: '我的简历',
  currentVersionId: 1,
  currentVersionNumber: 1,
  currentVersion: version,
}

function mountPage() {
  return mount(ResumePage, {
    global: {
      plugins: [ElementPlus, VueDOMPurifyHTML],
      components: { SvgIcon },
    },
  })
}

function seedLoadedResume() {
  api.listResumes.mockResolvedValue({ code: 200, message: 'success', data: [summary] })
  api.getResume.mockResolvedValue({ code: 200, message: 'success', data: detail })
  api.listResumeVersions.mockResolvedValue({ code: 200, message: 'success', data: [versionSummary] })
  api.getResumeVersion.mockResolvedValue({ code: 200, message: 'success', data: version })
}

describe('ResumePage', () => {
  beforeEach(() => {
    Object.values(api).forEach((mock) => mock.mockReset())
  })

  it('creates the first resume from the empty state', async () => {
    api.listResumes.mockResolvedValue({ code: 200, message: 'success', data: [] })
    api.createResume.mockResolvedValue({ code: 200, message: 'success', data: summary })
    api.getResume.mockResolvedValue({ code: 200, message: 'success', data: detail })
    api.listResumeVersions.mockResolvedValue({
      code: 200,
      message: 'success',
      data: [versionSummary],
    })
    api.getResumeVersion.mockResolvedValue({ code: 200, message: 'success', data: version })

    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.text()).toContain('还没有简历')
    await wrapper.find('[data-testid="create-resume"]').trigger('click')
    await flushPromises()

    expect(api.createResume).toHaveBeenCalledWith({ resumeName: '我的简历' })
    expect(wrapper.text()).toContain('我的简历')
    expect(wrapper.text()).toContain('张同学')
  })

  it('renders the current version in the preview', async () => {
    seedLoadedResume()
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.text()).toContain('张同学')
    expect(wrapper.text()).toContain('Java')
  })

  it('generates a version after choosing a recommended template', async () => {
    seedLoadedResume()
    api.generateResume.mockResolvedValue({
      code: 200,
      message: 'success',
      data: { resumeId: 1, versionId: 2, versionNumber: 2, content: content('AI 版本') },
    })
    api.getResumeVersion.mockResolvedValue({
      code: 200,
      message: 'success',
      data: { ...version, versionId: 2, versionNumber: 2, content: content('AI 版本') },
    })

    const wrapper = mountPage()
    await flushPromises()

    await wrapper.find('[data-testid="ai-generate"]').trigger('click')
    await flushPromises()
    await wrapper.find('[data-testid="template-card-6746fe472f0052cb6a9365aa"]').trigger('click')
    await wrapper.find('[data-testid="confirm-template"]').trigger('click')
    await flushPromises()

    expect(api.generateResume).toHaveBeenCalledWith(1, {
      templateId: '6746fe472f0052cb6a9365aa',
      generationReason: 'INITIAL',
    })
  })

  it('saves edits as a manual version', async () => {
    seedLoadedResume()
    api.saveResumeVersion.mockResolvedValue({
      code: 200,
      message: 'success',
      data: { resumeId: 1, versionId: 2, versionNumber: 2, content: content() },
    })

    const wrapper = mountPage()
    await flushPromises()

    await wrapper.find('[data-testid="save-version"]').trigger('click')
    await flushPromises()

    expect(api.saveResumeVersion).toHaveBeenCalledWith(
      1,
      expect.objectContaining({ generationReason: 'MANUAL_EDIT' }),
    )
  })

  it('switching templates creates a TEMPLATE_SWITCH version', async () => {
    seedLoadedResume()
    api.saveResumeVersion.mockResolvedValue({
      code: 200,
      message: 'success',
      data: { resumeId: 1, versionId: 2, versionNumber: 2, content: content() },
    })

    const wrapper = mountPage()
    await flushPromises()

    await wrapper.find('[data-testid="open-templates"]').trigger('click')
    await flushPromises()
    await wrapper.find('[data-testid="template-card-674704bd2f0052cb6a9366c5"]').trigger('click')
    await wrapper.find('[data-testid="confirm-template"]').trigger('click')
    await flushPromises()

    expect(api.saveResumeVersion).toHaveBeenCalledWith(
      1,
      expect.objectContaining({ templateId: '674704bd2f0052cb6a9366c5', generationReason: 'TEMPLATE_SWITCH' }),
    )
  })
})
