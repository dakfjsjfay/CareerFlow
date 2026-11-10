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
}))

import type { CompletenessResult, ProfileData } from '@/api/profile'

import { useProfile } from './useProfile'

function apiResult<T>(data: T) {
  return { code: 200, message: 'success', data }
}

const profile: ProfileData = {
  id: 1,
  realName: '张三',
  phone: null,
  school: null,
  major: null,
  degree: null,
  graduationYear: null,
  skills: [],
  targetPosition: null,
  targetCity: null,
  expectedSalary: null,
  projects: [],
  experiences: [],
}

const completeness: CompletenessResult = {
  overall: 50,
  dimensions: [{ name: '基础信息', score: 50 }],
}

describe('useProfile', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getProfile.mockResolvedValue(apiResult(profile))
    getCompleteness.mockResolvedValue(apiResult(completeness))
    updateProfile.mockResolvedValue(apiResult(profile))
    createProject.mockResolvedValue(apiResult({}))
    updateProject.mockResolvedValue(apiResult({}))
    deleteProject.mockResolvedValue(apiResult(null))
    createExperience.mockResolvedValue(apiResult({}))
    updateExperience.mockResolvedValue(apiResult({}))
    deleteExperience.mockResolvedValue(apiResult(null))
  })

  it('loads the profile and completeness together', async () => {
    const state = useProfile()

    await state.load()

    expect(getProfile).toHaveBeenCalledTimes(1)
    expect(getCompleteness).toHaveBeenCalledTimes(1)
    expect(state.profile.value).toEqual(profile)
    expect(state.completeness.value).toEqual(completeness)
    expect(state.loading.value).toBe(false)
  })

  it('clears the loading flag when loading fails', async () => {
    getProfile.mockRejectedValue(new Error('网络错误'))
    const state = useProfile()

    await expect(state.load()).rejects.toThrow('网络错误')
    expect(state.loading.value).toBe(false)
  })

  it('saves profile fields and refreshes completeness', async () => {
    const state = useProfile()
    await state.load()
    const updated = { ...profile, realName: '李四' }
    updateProfile.mockResolvedValue(apiResult(updated))
    getCompleteness.mockResolvedValue(apiResult({ ...completeness, overall: 60 }))

    await state.saveProfile({ realName: '李四' })

    expect(updateProfile).toHaveBeenCalledWith({ realName: '李四' })
    expect(state.profile.value?.realName).toBe('李四')
    expect(state.completeness.value?.overall).toBe(60)
    expect(state.saving.value).toBe(false)
  })

  it('creates a project when no id is given and reloads both views', async () => {
    const state = useProfile()
    await state.load()
    getProfile.mockClear()
    getCompleteness.mockClear()

    await state.saveProject(null, { projectName: '学生管理系统' })

    expect(createProject).toHaveBeenCalledWith({ projectName: '学生管理系统' })
    expect(updateProject).not.toHaveBeenCalled()
    expect(getProfile).toHaveBeenCalledTimes(1)
    expect(getCompleteness).toHaveBeenCalledTimes(1)
  })

  it('updates an existing project', async () => {
    const state = useProfile()

    await state.saveProject(21, { projectName: '学生管理系统' })

    expect(updateProject).toHaveBeenCalledWith(21, { projectName: '学生管理系统' })
    expect(createProject).not.toHaveBeenCalled()
  })

  it('deletes a project and reloads the profile', async () => {
    const state = useProfile()
    getProfile.mockClear()
    getCompleteness.mockClear()

    await state.removeProject(21)

    expect(deleteProject).toHaveBeenCalledWith(21)
    expect(getProfile).toHaveBeenCalledTimes(1)
    expect(getCompleteness).toHaveBeenCalledTimes(1)
  })

  it('creates, updates and deletes experiences', async () => {
    const state = useProfile()

    await state.saveExperience(null, { company: '某某科技' })
    await state.saveExperience(31, { company: '某某科技' })
    await state.removeExperience(31)

    expect(createExperience).toHaveBeenCalledWith({ company: '某某科技' })
    expect(updateExperience).toHaveBeenCalledWith(31, { company: '某某科技' })
    expect(deleteExperience).toHaveBeenCalledWith(31)
  })

  it('resets the saving flag when a save fails', async () => {
    updateProfile.mockRejectedValue(new Error('保存失败'))
    const state = useProfile()

    await expect(state.saveProfile({ realName: '李四' })).rejects.toThrow('保存失败')
    expect(state.saving.value).toBe(false)
  })
})
