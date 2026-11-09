import { beforeEach, describe, expect, it, vi } from 'vitest'

const { get, post } = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
}))

vi.mock('./request', () => ({
  default: { get, post },
}))

import { confirmEvidence, listEvidence } from './profile'

describe('profile evidence api', () => {
  beforeEach(() => {
    get.mockReset()
    post.mockReset()
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
