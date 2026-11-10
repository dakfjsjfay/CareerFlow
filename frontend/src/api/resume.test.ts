import { beforeEach, describe, expect, it, vi } from 'vitest'

const { get, post } = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
}))

vi.mock('./request', () => ({
  default: { get, post },
}))

import {
  createResume,
  exportResumeDocx,
  generateResume,
  getResume,
  getResumeVersion,
  listResumes,
  listResumeVersions,
  saveResumeVersion,
} from './resume'

describe('resume api', () => {
  beforeEach(() => {
    get.mockReset()
    post.mockReset()
  })

  it('creates a resume with an optional name', async () => {
    post.mockResolvedValue({ code: 200, message: 'success', data: { id: 1 } })

    await createResume()
    await createResume({ resumeName: 'Java 简历' })

    expect(post).toHaveBeenNthCalledWith(1, '/resumes', {})
    expect(post).toHaveBeenNthCalledWith(2, '/resumes', { resumeName: 'Java 简历' })
  })

  it('lists and reads resumes', async () => {
    get.mockResolvedValue({ code: 200, message: 'success', data: [] })

    await listResumes()
    await getResume(1)

    expect(get).toHaveBeenNthCalledWith(1, '/resumes')
    expect(get).toHaveBeenNthCalledWith(2, '/resumes/1')
  })

  it('generates a new version', async () => {
    post.mockResolvedValue({ code: 200, message: 'success', data: {} })

    await generateResume(1, { templateId: 'tpl-1', generationReason: 'INITIAL' })

    expect(post).toHaveBeenCalledWith('/resumes/1/generate', {
      templateId: 'tpl-1',
      generationReason: 'INITIAL',
    })
  })

  it('saves a manual version', async () => {
    post.mockResolvedValue({ code: 200, message: 'success', data: {} })
    const content = { meta: { templateId: 'tpl-1' } }

    await saveResumeVersion(1, {
      content: content as never,
      templateId: 'tpl-1',
      generationReason: 'MANUAL_EDIT',
    })

    expect(post).toHaveBeenCalledWith('/resumes/1/versions', {
      content,
      templateId: 'tpl-1',
      generationReason: 'MANUAL_EDIT',
    })
  })

  it('lists and reads versions', async () => {
    get.mockResolvedValue({ code: 200, message: 'success', data: [] })

    await listResumeVersions(1)
    await getResumeVersion(1, 3)

    expect(get).toHaveBeenNthCalledWith(1, '/resumes/1/versions')
    expect(get).toHaveBeenNthCalledWith(2, '/resumes/1/versions/3')
  })

  it('exports docx as a blob and returns the parsed filename', async () => {
    post.mockResolvedValue({ blob: new Blob(['x']), filename: '我的简历-v1.docx' })

    const result = await exportResumeDocx(1, 3)

    expect(post).toHaveBeenCalledWith(
      '/resumes/1/export',
      { versionId: 3, format: 'DOCX' },
      { responseType: 'blob' },
    )
    expect(result.filename).toBe('我的简历-v1.docx')
  })
})
