import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type { ResumeDraft } from '@/api/resumeDraft'
import { createEmptyDraftContent } from '@/utils/resumeWizard'

const api = vi.hoisted(() => ({
  abandonDraft: vi.fn(),
  completeDraft: vi.fn(),
  createDraft: vi.fn(),
  getDraft: vi.fn(),
  listDrafts: vi.fn(),
  saveDraft: vi.fn(),
}))

vi.mock('@/api/resumeDraft', () => api)

import { SAVE_INTERVAL_MS, useResumeDraftStore } from './resumeDraft'

function draft(overrides: Partial<ResumeDraft> = {}): ResumeDraft {
  return {
    id: 101,
    resumeId: null,
    draftName: null,
    currentStep: 1,
    totalSteps: 8,
    revision: 1,
    status: 'DRAFT',
    lastSavedAt: '2026-11-14T10:00:00',
    content: createEmptyDraftContent(),
    ...overrides,
  }
}

describe('resumeDraft store 自动保存', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    localStorage.clear()
    setActivePinia(createPinia())
    Object.values(api).forEach((mock) => mock.mockReset())
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('节流保存：多次修改只发一次请求，revision 随保存递增', async () => {
    api.createDraft.mockResolvedValue({ code: 200, message: 'success', data: draft() })
    api.saveDraft.mockResolvedValueOnce({
      code: 200,
      message: 'success',
      data: draft({ revision: 2 }),
    })

    const store = useResumeDraftStore()
    await store.initNew()

    expect(api.createDraft).toHaveBeenCalledTimes(1)
    expect(store.draftId).toBe(101)

    store.content.basic.realName = '张三'
    store.touch()
    store.content.basic.phone = '13800000000'
    store.touch()

    expect(api.saveDraft).not.toHaveBeenCalled()

    await vi.advanceTimersByTimeAsync(SAVE_INTERVAL_MS - 1)
    expect(api.saveDraft).not.toHaveBeenCalled()

    await vi.advanceTimersByTimeAsync(1)

    expect(api.saveDraft).toHaveBeenCalledTimes(1)
    expect(api.saveDraft.mock.calls[0][1]).toMatchObject({
      currentStep: 1,
      revision: 1,
    })
    expect(store.revision).toBe(2)
    expect(store.saveState).toBe('saved')

    api.saveDraft.mockResolvedValueOnce({
      code: 200,
      message: 'success',
      data: draft({ revision: 3 }),
    })

    store.content.basic.realName = '张三丰'
    store.touch()

    await vi.advanceTimersByTimeAsync(SAVE_INTERVAL_MS)

    expect(api.saveDraft).toHaveBeenCalledTimes(2)
    expect(api.saveDraft.mock.calls[1][1]).toMatchObject({ revision: 2 })
    expect(store.revision).toBe(3)
  })

  it('生成前等待正在进行的保存并补发最新内容', async () => {
    api.createDraft.mockResolvedValue({ code: 200, message: 'success', data: draft() })
    let resolveFirst: (value: unknown) => void = () => {}
    api.saveDraft
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            resolveFirst = resolve as (value: unknown) => void
          }),
      )
      .mockResolvedValueOnce({ code: 200, message: 'success', data: draft({ revision: 3 }) })
    api.completeDraft.mockResolvedValue({
      code: 200,
      message: 'success',
      data: { resumeId: 1, versionId: 2, versionNumber: 1, draftId: 101 },
    })

    const store = useResumeDraftStore()
    await store.initNew()

    store.content.basic.realName = '张三'
    store.touch()
    await vi.advanceTimersByTimeAsync(SAVE_INTERVAL_MS)
    expect(api.saveDraft).toHaveBeenCalledTimes(1)

    // 第一个保存请求仍在进行中时，用户改了内容并直接点生成
    store.content.basic.phone = '13800000000'
    store.touch()
    const completing = store.complete({ resumeName: '简历', templateId: 'tpl-1' })
    await Promise.resolve()
    expect(api.completeDraft).not.toHaveBeenCalled()

    resolveFirst({ code: 200, message: 'success', data: draft({ revision: 2 }) })
    await completing

    expect(api.saveDraft).toHaveBeenCalledTimes(2)
    expect(api.saveDraft.mock.calls[1][1]).toMatchObject({
      revision: 2,
      content: expect.objectContaining({ basic: expect.objectContaining({ phone: '13800000000' }) }),
    })
    expect(api.completeDraft).toHaveBeenCalledTimes(1)
  })
})
