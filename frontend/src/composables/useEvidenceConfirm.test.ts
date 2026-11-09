import { beforeEach, describe, expect, it, vi } from 'vitest'

const { listEvidence, confirmEvidence } = vi.hoisted(() => ({
  listEvidence: vi.fn(),
  confirmEvidence: vi.fn(),
}))

vi.mock('@/api/profile', () => ({
  listEvidence,
  confirmEvidence,
}))

import type { EvidenceItem } from '@/api/profile'

import { useEvidenceConfirm } from './useEvidenceConfirm'

function evidence(overrides: Partial<EvidenceItem> = {}): EvidenceItem {
  return {
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
    ...overrides,
  }
}

function apiResult<T>(data: T) {
  return { code: 200, message: 'success', data }
}

describe('useEvidenceConfirm', () => {
  beforeEach(() => {
    listEvidence.mockReset()
    confirmEvidence.mockReset()
  })

  it('fetches pending evidence and opens after a send with extracted info', async () => {
    listEvidence.mockResolvedValue(apiResult([evidence()]))

    const state = useEvidenceConfirm()
    await state.afterSend({ basic: { realName: '张三' }, skills: [] })

    expect(listEvidence).toHaveBeenCalledWith('PENDING')
    expect(state.visible.value).toBe(true)
    expect(state.pendingCount.value).toBe(1)
    expect(state.groups.value[0].title).toBe('基础信息')
    expect(state.groups.value[0].items[0].value).toBe('张三')
  })

  it('does not open when the send produced no pending evidence', async () => {
    listEvidence.mockResolvedValue(apiResult([]))

    const state = useEvidenceConfirm()
    await state.afterSend({ skills: ['Java'] })

    expect(listEvidence).toHaveBeenCalledWith('PENDING')
    expect(state.visible.value).toBe(false)
    expect(state.pendingCount.value).toBe(0)
  })

  it('skips the fetch when extracted info is empty', async () => {
    const state = useEvidenceConfirm()

    await state.afterSend({
      basic: {},
      education: {},
      skills: [],
      projects: [],
      experiences: [],
      jobIntention: {},
    })

    expect(listEvidence).not.toHaveBeenCalled()
    expect(state.visible.value).toBe(false)
  })

  it('confirms selected ids and closes when no pending evidence remains', async () => {
    listEvidence
      .mockResolvedValueOnce(apiResult([evidence()]))
      .mockResolvedValueOnce(apiResult([]))
    confirmEvidence.mockResolvedValue(
      apiResult({ confirmedCount: 1, updatedFields: ['realName'] }),
    )

    const state = useEvidenceConfirm()
    await state.open()
    const result = await state.confirmSelected([1])

    expect(confirmEvidence).toHaveBeenCalledWith([1], true)
    expect(result).toEqual({ confirmedCount: 1, updatedFields: ['realName'] })
    expect(state.pendingCount.value).toBe(0)
    expect(state.visible.value).toBe(false)
  })

  it('rejects selected ids and keeps the dialog open while pending evidence remains', async () => {
    listEvidence
      .mockResolvedValueOnce(apiResult([evidence({ id: 1 }), evidence({ id: 2, fieldName: 'phone', evidenceContent: '13800000000' })]))
      .mockResolvedValueOnce(apiResult([evidence({ id: 2, fieldName: 'phone', evidenceContent: '13800000000' })]))
    confirmEvidence.mockResolvedValue(apiResult({ confirmedCount: 0, updatedFields: [] }))

    const state = useEvidenceConfirm()
    await state.open()
    await state.rejectSelected([1])

    expect(confirmEvidence).toHaveBeenCalledWith([1], false)
    expect(state.pendingCount.value).toBe(1)
    expect(state.visible.value).toBe(true)
  })

  it('propagates confirm errors and keeps the dialog open', async () => {
    listEvidence.mockResolvedValue(apiResult([evidence()]))
    confirmEvidence.mockRejectedValue(new Error('409 已处理'))

    const state = useEvidenceConfirm()
    await state.open()

    await expect(state.confirmSelected([1])).rejects.toThrow('409 已处理')
    expect(state.visible.value).toBe(true)
    expect(state.pendingCount.value).toBe(1)
    expect(state.submitting.value).toBe(false)
  })

  it('refreshes the pending count without opening the dialog', async () => {
    listEvidence.mockResolvedValue(apiResult([evidence()]))

    const state = useEvidenceConfirm()
    await state.refresh()

    expect(state.pendingCount.value).toBe(1)
    expect(state.visible.value).toBe(false)
  })
})
