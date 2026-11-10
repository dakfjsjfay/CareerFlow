import { beforeEach, describe, expect, it, vi } from 'vitest'

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

import type { ResumeContent } from '@/components/resume-template/types'
import { useResume } from './useResume'

function content(selfEvaluation = '熟悉 Java。'): ResumeContent {
  return {
    meta: { templateId: 'tpl-1', warnings: [] },
    personalInfo: {
      realName: '张同学',
      phone: null,
      email: null,
      city: null,
      avatar: null,
      school: '深圳大学',
      major: '软件工程',
      degree: '本科',
      graduationYear: 2028,
      yearsOfExperience: 0,
      abstract: null,
    },
    jobIntention: { targetPosition: 'Java', targetCity: '深圳', expectedSalary: '面议' },
    education: [],
    skills: ['Java'],
    projects: [],
    experiences: [],
    selfEvaluation,
  }
}

const version = (versionId = 1, number = 1, selfEvaluation = '熟悉 Java。') => ({
  versionId,
  versionNumber: number,
  generationReason: 'INITIAL',
  templateId: 'tpl-1',
  content: content(selfEvaluation),
  createdAt: null,
})

const versionSummary = (versionId = 1, number = 1) => ({
  versionId,
  versionNumber: number,
  generationReason: 'INITIAL',
  templateId: 'tpl-1',
  createdAt: null,
})

const summary = {
  id: 1,
  resumeName: '我的简历',
  currentVersionId: 1,
  currentVersionNumber: 1,
  templateId: 'tpl-1',
  updatedAt: null,
}

function detailWith(versionId = 1, number = 1) {
  return {
    id: 1,
    resumeName: '我的简历',
    currentVersionId: versionId,
    currentVersionNumber: number,
    currentVersion: version(versionId, number),
  }
}

function stubDownload() {
  Object.defineProperty(URL, 'createObjectURL', {
    configurable: true,
    value: vi.fn(() => 'blob:mock'),
  })
  Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: vi.fn() })
  return vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined)
}

describe('useResume', () => {
  beforeEach(() => {
    Object.values(api).forEach((mock) => mock.mockReset())
    api.listResumes.mockResolvedValue({ code: 200, message: 'success', data: [summary] })
    api.getResume.mockResolvedValue({ code: 200, message: 'success', data: detailWith() })
    api.listResumeVersions.mockResolvedValue({
      code: 200,
      message: 'success',
      data: [versionSummary()],
    })
    api.getResumeVersion.mockImplementation((_id: number, versionId: number) =>
      Promise.resolve({ code: 200, message: 'success', data: version(versionId, versionId) }),
    )
  })

  it('loads resumes and selects the current version', async () => {
    const resume = useResume()

    await resume.loadResumes()

    expect(resume.resumes.value).toEqual([summary])
    expect(resume.active.value?.id).toBe(1)
    expect(resume.activeVersion.value?.versionId).toBe(1)
    expect(resume.editingContent.value?.meta.templateId).toBe('tpl-1')
  })

  it('generates a new version and selects it', async () => {
    const resume = useResume()
    await resume.loadResumes()

    api.generateResume.mockResolvedValue({
      code: 200,
      message: 'success',
      data: { resumeId: 1, versionId: 2, versionNumber: 2, content: content('新版本') },
    })
    api.getResumeVersion.mockResolvedValue({
      code: 200,
      message: 'success',
      data: version(2, 2, '新版本'),
    })

    await resume.generate('tpl-2', 'INITIAL')

    expect(api.generateResume).toHaveBeenCalledWith(1, {
      templateId: 'tpl-2',
      generationReason: 'INITIAL',
    })
    expect(resume.activeVersion.value?.versionId).toBe(2)
    expect(resume.editingContent.value?.selfEvaluation).toBe('新版本')
  })

  it('keeps the current version when generation fails', async () => {
    const resume = useResume()
    await resume.loadResumes()
    const before = resume.activeVersion.value

    api.generateResume.mockRejectedValue(new Error('AI 失败'))

    await expect(resume.generate('tpl-2')).rejects.toThrow('AI 失败')
    expect(resume.activeVersion.value).toBe(before)
    expect(resume.generating.value).toBe(false)
  })

  it('saves edits as a new version', async () => {
    const resume = useResume()
    await resume.loadResumes()
    resume.editingContent.value!.selfEvaluation = '手动修改'

    api.saveResumeVersion.mockResolvedValue({
      code: 200,
      message: 'success',
      data: { resumeId: 1, versionId: 5, versionNumber: 5, content: content('手动修改') },
    })
    api.getResumeVersion.mockResolvedValue({
      code: 200,
      message: 'success',
      data: version(5, 5, '手动修改'),
    })

    await resume.saveVersion('MANUAL_EDIT', 'tpl-1')

    expect(api.saveResumeVersion).toHaveBeenCalledWith(1, expect.objectContaining({
      templateId: 'tpl-1',
      generationReason: 'MANUAL_EDIT',
    }))
    expect(resume.activeVersion.value?.versionId).toBe(5)
  })

  it('downloads a docx for the requested version', async () => {
    const resume = useResume()
    await resume.loadResumes()
    const click = stubDownload()
    api.exportResumeDocx.mockResolvedValue({
      blob: new Blob(['x']),
      filename: '我的简历-v1.docx',
    })

    await resume.exportDocx(1)

    expect(api.exportResumeDocx).toHaveBeenCalledWith(1, 1)
    expect(URL.createObjectURL).toHaveBeenCalled()
    expect(click).toHaveBeenCalled()
    click.mockRestore()
  })
})
