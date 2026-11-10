import { beforeEach, describe, expect, it, vi } from 'vitest'

const { get, post, put, del } = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  del: vi.fn(),
}))

vi.mock('./request', () => ({
  default: { get, post, put, delete: del },
}))

import {
  confirmEvidence,
  createExperience,
  createProject,
  deleteExperience,
  deleteProject,
  getCompleteness,
  getProfile,
  listEvidence,
  updateExperience,
  updateProfile,
  updateProject,
} from './profile'

describe('profile evidence api', () => {
  beforeEach(() => {
    get.mockReset()
    post.mockReset()
    put.mockReset()
    del.mockReset()
  })

  it('lists pending evidence by default', async () => {
    get.mockResolvedValue({ code: 200, message: 'success', data: [] })

    await listEvidence()

    expect(get).toHaveBeenCalledWith('/profile/evidence', { params: { status: 'PENDING' } })
  })

  it('lists evidence by explicit status', async () => {
    get.mockResolvedValue({ code: 200, message: 'success', data: [] })

    await listEvidence('REJECTED')

    expect(get).toHaveBeenCalledWith('/profile/evidence', { params: { status: 'REJECTED' } })
  })

  it('confirms selected evidence ids', async () => {
    post.mockResolvedValue({
      code: 200,
      message: 'success',
      data: { confirmedCount: 2, updatedFields: ['skills'] },
    })

    await confirmEvidence([101, 102], true)

    expect(post).toHaveBeenCalledWith('/profile/evidence/confirm', {
      evidenceIds: [101, 102],
      confirmed: true,
    })
  })

  it('rejects selected evidence ids', async () => {
    post.mockResolvedValue({
      code: 200,
      message: 'success',
      data: { confirmedCount: 0, updatedFields: [] },
    })

    await confirmEvidence([103], false)

    expect(post).toHaveBeenCalledWith('/profile/evidence/confirm', {
      evidenceIds: [103],
      confirmed: false,
    })
  })
})

describe('profile api', () => {
  beforeEach(() => {
    get.mockReset()
    post.mockReset()
    put.mockReset()
    del.mockReset()
  })

  it('loads the confirmed profile', async () => {
    get.mockResolvedValue({ code: 200, message: 'success', data: {} })

    await getProfile()

    expect(get).toHaveBeenCalledWith('/profile')
  })

  it('updates profile fields', async () => {
    const payload = { realName: '张三', skills: ['Java'] }
    put.mockResolvedValue({ code: 200, message: 'success', data: {} })

    await updateProfile(payload)

    expect(put).toHaveBeenCalledWith('/profile', payload)
  })

  it('loads completeness scores', async () => {
    get.mockResolvedValue({ code: 200, message: 'success', data: {} })

    await getCompleteness()

    expect(get).toHaveBeenCalledWith('/profile/completeness')
  })

  it('creates and updates a project', async () => {
    const payload = { projectName: '学生管理系统', techStack: ['Java'] }
    post.mockResolvedValue({ code: 200, message: 'success', data: {} })
    put.mockResolvedValue({ code: 200, message: 'success', data: {} })

    await createProject(payload)
    await updateProject(21, payload)

    expect(post).toHaveBeenCalledWith('/profile/projects', payload)
    expect(put).toHaveBeenCalledWith('/profile/projects/21', payload)
  })

  it('deletes a project by id', async () => {
    del.mockResolvedValue({ code: 200, message: 'success', data: null })

    await deleteProject(21)

    expect(del).toHaveBeenCalledWith('/profile/projects/21')
  })

  it('creates and updates an experience', async () => {
    const payload = { company: '某某科技', position: '后端实习生' }
    post.mockResolvedValue({ code: 200, message: 'success', data: {} })
    put.mockResolvedValue({ code: 200, message: 'success', data: {} })

    await createExperience(payload)
    await updateExperience(31, payload)

    expect(post).toHaveBeenCalledWith('/profile/experiences', payload)
    expect(put).toHaveBeenCalledWith('/profile/experiences/31', payload)
  })

  it('deletes an experience by id', async () => {
    del.mockResolvedValue({ code: 200, message: 'success', data: null })

    await deleteExperience(31)

    expect(del).toHaveBeenCalledWith('/profile/experiences/31')
  })
})
