import { beforeEach, describe, expect, it, vi } from 'vitest'

const { get, post } = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
}))

vi.mock('./request', () => ({
  default: { get, post },
}))

import { analyzeMatch, getMatchResult, recommendJobs } from './match'

describe('match api', () => {
  beforeEach(() => {
    get.mockReset()
    post.mockReset()
  })

  it('analyzes a job against a resume version', async () => {
    post.mockResolvedValue({ code: 200, message: 'success', data: { matchResultId: 1 } })

    await analyzeMatch({ jobId: 1, resumeVersionId: 3 })

    expect(post).toHaveBeenCalledWith('/match/analyze', { jobId: 1, resumeVersionId: 3 })
  })

  it('reads a match result', async () => {
    get.mockResolvedValue({ code: 200, message: 'success', data: { matchResultId: 1 } })

    await getMatchResult(1)

    expect(get).toHaveBeenCalledWith('/match/1')
  })

  it('recommends jobs with pagination params', async () => {
    get.mockResolvedValue({ code: 200, message: 'success', data: { items: [], total: 0 } })

    await recommendJobs()
    await recommendJobs(2, 20)

    expect(get).toHaveBeenNthCalledWith(1, '/match/jobs/recommend', { params: { page: 1, size: 10 } })
    expect(get).toHaveBeenNthCalledWith(2, '/match/jobs/recommend', { params: { page: 2, size: 20 } })
  })
})
