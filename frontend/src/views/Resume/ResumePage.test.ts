import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus, { ElMessageBox } from 'element-plus'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import VueDOMPurifyHTML from 'vue-dompurify-html'

const api = vi.hoisted(() => ({
  createResume: vi.fn(),
  deleteResume: vi.fn(),
  exportResumeDocx: vi.fn(),
  getResume: vi.fn(),
  listResumeVersions: vi.fn(),
  listResumes: vi.fn(),
  saveResumeVersion: vi.fn(),
}))

const routerPush = vi.hoisted(() => vi.fn())

vi.mock('@/api/resume', () => api)
vi.mock('vue-router', () => ({ useRouter: () => ({ push: routerPush }) }))

import ResumePage from './index.vue'

const summary = {
  id: 1,
  resumeName: '我的简历',
  currentVersionId: 11,
  currentVersionNumber: 2,
  templateId: '6746fe472f0052cb6a9365aa',
  updatedAt: '2026-11-14T10:00:00',
}

const versionSummary = {
  versionId: 11,
  versionNumber: 2,
  generationReason: 'MANUAL_EDIT',
  templateId: '6746fe472f0052cb6a9365aa',
  createdAt: '2026-11-14T10:00:00',
}

const content = {
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
  education: [],
  skills: [],
  projects: [],
  experiences: [],
  selfEvaluation: '',
}

function mountPage() {
  return mount(ResumePage, {
    global: {
      plugins: [ElementPlus, VueDOMPurifyHTML],
    },
  })
}

describe('ResumePage（简历中心列表）', () => {
  beforeEach(() => {
    Object.values(api).forEach((mock) => mock.mockReset())
    routerPush.mockReset()
  })

  it('空状态引导进入新建简历工作区', async () => {
    api.listResumes.mockResolvedValue({ code: 200, message: 'success', data: [] })

    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.text()).toContain('还没有简历')
    await wrapper.find('[data-testid="create-resume"]').trigger('click')

    expect(routerPush).toHaveBeenCalledWith('/workspace/new')
  })

  it('展示简历列表并进入工作区编辑', async () => {
    api.listResumes.mockResolvedValue({ code: 200, message: 'success', data: [summary] })

    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.text()).toContain('我的简历')
    expect(wrapper.text()).toContain('v2')

    await wrapper.find('[data-testid="resume-edit"]').trigger('click')
    expect(routerPush).toHaveBeenCalledWith('/workspace/1')
  })

  it('删除简历前二次确认，并调用删除接口', async () => {
    api.listResumes.mockResolvedValue({ code: 200, message: 'success', data: [summary] })
    api.deleteResume.mockResolvedValue({ code: 200, message: 'success', data: null })
    vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm' as never)

    const wrapper = mountPage()
    await flushPromises()

    await wrapper.find('[data-testid="resume-delete"]').trigger('click')
    await flushPromises()

    expect(api.deleteResume).toHaveBeenCalledWith(1)
  })

  it('复制简历会创建新简历并写入当前版本内容', async () => {
    api.listResumes.mockResolvedValue({ code: 200, message: 'success', data: [summary] })
    api.getResume.mockResolvedValue({
      code: 200,
      message: 'success',
      data: { ...summary, currentVersion: { ...versionSummary, content } },
    })
    api.createResume.mockResolvedValue({
      code: 200,
      message: 'success',
      data: { id: 2, resumeName: '我的简历 副本' },
    })
    api.saveResumeVersion.mockResolvedValue({
      code: 200,
      message: 'success',
      data: { resumeId: 2, versionId: 20, versionNumber: 1, content },
    })

    const wrapper = mountPage()
    await flushPromises()

    await wrapper.find('[data-testid="resume-copy"]').trigger('click')
    await flushPromises()

    expect(api.createResume).toHaveBeenCalledWith({ resumeName: '我的简历 副本' })
    expect(api.saveResumeVersion).toHaveBeenCalledWith(
      2,
      expect.objectContaining({ generationReason: 'MANUAL_EDIT' }),
    )
  })

  it('版本历史选择后带 versionId 进入工作区', async () => {
    api.listResumes.mockResolvedValue({ code: 200, message: 'success', data: [summary] })
    api.listResumeVersions.mockResolvedValue({
      code: 200,
      message: 'success',
      data: [versionSummary],
    })

    const wrapper = mountPage()
    await flushPromises()

    await wrapper.find('[data-testid="resume-history"]').trigger('click')
    await flushPromises()

    expect(api.listResumeVersions).toHaveBeenCalledWith(1)
    expect(wrapper.text()).toContain('版本历史')
  })
})
