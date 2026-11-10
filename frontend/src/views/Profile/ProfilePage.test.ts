import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus, { ElMessageBox } from 'element-plus'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const {
  getProfile,
  getCompleteness,
  updateProfile,
  createProject,
  updateProject,
  deleteProject,
  createExperience,
  updateExperience,
  deleteExperience,
  listEvidence,
  confirmEvidence,
} = vi.hoisted(() => ({
  getProfile: vi.fn(),
  getCompleteness: vi.fn(),
  updateProfile: vi.fn(),
  createProject: vi.fn(),
  updateProject: vi.fn(),
  deleteProject: vi.fn(),
  createExperience: vi.fn(),
  updateExperience: vi.fn(),
  deleteExperience: vi.fn(),
  listEvidence: vi.fn(),
  confirmEvidence: vi.fn(),
}))

vi.mock('@/api/profile', () => ({
  getProfile,
  getCompleteness,
  updateProfile,
  createProject,
  updateProject,
  deleteProject,
  createExperience,
  updateExperience,
  deleteExperience,
  listEvidence,
  confirmEvidence,
}))

import type { CompletenessResult, EvidenceItem, ProfileData } from '@/api/profile'

import ProfilePage from './index.vue'

const profile: ProfileData = {
  id: 1,
  realName: '张三',
  phone: '13800000000',
  school: '某某大学',
  major: '计算机科学与技术',
  degree: '本科',
  graduationYear: 2027,
  skills: ['Java', 'MySQL'],
  targetPosition: 'Java 后端',
  targetCity: '杭州',
  expectedSalary: '8-12K',
  projects: [
    {
      id: 21,
      projectName: '学生管理系统',
      role: '后端开发',
      description: '增删改查',
      techStack: ['Java', 'MySQL'],
      startDate: '2025-03-01',
      endDate: '2025-06-30',
    },
  ],
  experiences: [
    {
      id: 31,
      company: '某某科技',
      position: '后端实习生',
      description: '参与接口开发',
      achievements: '完成 3 个接口',
      startDate: '2025-07-01',
      endDate: '2025-09-30',
    },
  ],
}

const completeness: CompletenessResult = {
  overall: 67,
  dimensions: [
    { name: '基础信息', score: 100 },
    { name: '教育经历', score: 75 },
    { name: '技能信息', score: 50 },
    { name: '项目经历', score: 100 },
    { name: '工作/实习经历', score: 100 },
    { name: '求职意向', score: 80 },
  ],
}

const pendingEvidence: EvidenceItem = {
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
}

function apiResult<T>(data: T) {
  return { code: 200, message: 'success', data }
}

const stubs = {
  CompletenessRadar: {
    props: ['overall', 'dimensions'],
    template: '<div data-testid="radar">{{ overall }}</div>',
  },
  DocumentAnalyzer: true,
  ElDialog: {
    props: ['modelValue', 'title'],
    template:
      '<div v-if="modelValue"><h3>{{ title }}</h3><slot /><slot name="footer" /></div>',
  },
}

function mountPage() {
  return mount(ProfilePage, {
    global: {
      plugins: [ElementPlus],
      stubs,
    },
  })
}

describe('Profile page', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getProfile.mockResolvedValue(apiResult(profile))
    getCompleteness.mockResolvedValue(apiResult(completeness))
    listEvidence.mockResolvedValue(apiResult([]))
    updateProfile.mockImplementation((payload: Record<string, unknown>) =>
      Promise.resolve(apiResult({ ...profile, ...payload })),
    )
    createProject.mockResolvedValue(apiResult({}))
    updateProject.mockResolvedValue(apiResult({}))
    deleteProject.mockResolvedValue(apiResult(null))
    createExperience.mockResolvedValue(apiResult({}))
    updateExperience.mockResolvedValue(apiResult({}))
    deleteExperience.mockResolvedValue(apiResult(null))
    confirmEvidence.mockResolvedValue(
      apiResult({ confirmedCount: 1, updatedFields: ['realName'] }),
    )
  })

  it('loads the confirmed profile and completeness radar', async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(getProfile).toHaveBeenCalledTimes(1)
    expect(getCompleteness).toHaveBeenCalledTimes(1)
    expect(wrapper.get('[data-testid="realName"]').element).toHaveProperty('value', '张三')
    expect(wrapper.text()).toContain('学生管理系统')
    expect(wrapper.text()).toContain('某某科技')
    expect(wrapper.get('[data-testid="radar"]').text()).toBe('67')
  })

  it('saves edited profile fields and refreshes completeness', async () => {
    const wrapper = mountPage()
    await flushPromises()

    await wrapper.get('[data-testid="realName"]').setValue('李四')
    await wrapper.get('[data-testid="save-profile"]').trigger('click')
    await flushPromises()

    expect(updateProfile).toHaveBeenCalledWith(
      expect.objectContaining({ realName: '李四', phone: '13800000000' }),
    )
    expect(getCompleteness).toHaveBeenCalledTimes(2)
    expect(wrapper.get('[data-testid="realName"]').element).toHaveProperty('value', '李四')
  })

  it('creates a project from the add dialog and reloads the page', async () => {
    const wrapper = mountPage()
    await flushPromises()

    await wrapper.get('[data-testid="add-project"]').trigger('click')
    await wrapper.get('[data-testid="projectName"]').setValue('校园二手交易平台')
    await wrapper.get('[data-testid="save-project"]').trigger('click')
    await flushPromises()

    expect(createProject).toHaveBeenCalledWith(
      expect.objectContaining({ projectName: '校园二手交易平台' }),
    )
    expect(getProfile).toHaveBeenCalledTimes(2)
    expect(getCompleteness).toHaveBeenCalledTimes(2)
  })

  it('updates an existing project using its id', async () => {
    const wrapper = mountPage()
    await flushPromises()

    await wrapper.get('[data-testid="edit-project-21"]').trigger('click')
    await wrapper.get('[data-testid="projectName"]').setValue('学生管理系统 v2')
    await wrapper.get('[data-testid="save-project"]').trigger('click')
    await flushPromises()

    expect(updateProject).toHaveBeenCalledWith(
      21,
      expect.objectContaining({ projectName: '学生管理系统 v2' }),
    )
    expect(createProject).not.toHaveBeenCalled()
  })

  it('deletes a project after confirmation', async () => {
    vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm' as never)
    const wrapper = mountPage()
    await flushPromises()

    await wrapper.get('[data-testid="delete-project-21"]').trigger('click')
    await flushPromises()

    expect(ElMessageBox.confirm).toHaveBeenCalled()
    expect(deleteProject).toHaveBeenCalledWith(21)
    expect(getProfile).toHaveBeenCalledTimes(2)
  })

  it('creates an experience from the add dialog', async () => {
    const wrapper = mountPage()
    await flushPromises()

    await wrapper.get('[data-testid="add-experience"]').trigger('click')
    await wrapper.get('[data-testid="company"]').setValue('新公司')
    await wrapper.get('[data-testid="save-experience"]').trigger('click')
    await flushPromises()

    expect(createExperience).toHaveBeenCalledWith(
      expect.objectContaining({ company: '新公司' }),
    )
  })

  it('shows pending evidence and reloads the profile after confirmation', async () => {
    listEvidence
      .mockResolvedValueOnce(apiResult([pendingEvidence]))
      .mockResolvedValueOnce(apiResult([pendingEvidence]))
      .mockResolvedValueOnce(apiResult([]))
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.text()).toContain('待确认 1')
    await wrapper.get('[data-testid="open-evidence"]').trigger('click')
    await flushPromises()
    await wrapper.find('[data-testid="select-all"]').setValue(true)
    const confirmButton = wrapper
      .findAll('button')
      .find((candidate) => candidate.text().includes('确认选中'))
    await confirmButton?.trigger('click')
    await flushPromises()

    expect(confirmEvidence).toHaveBeenCalledWith([1], true)
    expect(getProfile).toHaveBeenCalledTimes(2)
  })
})
