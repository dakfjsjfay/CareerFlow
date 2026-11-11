import { beforeEach, describe, expect, it, vi } from 'vitest'

const { get, post } = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
}))

vi.mock('./request', () => ({
  default: { get, post },
}))

import { createJob, getJob, listJobs, parseJob } from './jobs'

describe('jobs api', () => {
  beforeEach(() => {
    get.mockReset()
    post.mockReset()
  })

  it('lists jobs with filters and pagination', async () => {
    get.mockResolvedValue({ code: 200, message: 'success', data: {} })

    await listJobs({ page: 1, size: 10, keyword: 'Java', city: '深圳' })

    expect(get).toHaveBeenCalledWith('/jobs', {
      params: { page: 1, size: 10, keyword: 'Java', city: '深圳' },
    })
  })

  it('reads job detail', async () => {
    get.mockResolvedValue({ code: 200, message: 'success', data: {} })

    await getJob(1001)

    expect(get).toHaveBeenCalledWith('/jobs/1001')
  })

  it('creates a manual job', async () => {
    post.mockResolvedValue({ code: 200, message: 'success', data: {} })
    const payload = {
      company: '星云科技',
      position: 'Java 开发实习生',
      requirements: [{ skill: 'Java', importance: 'REQUIRED' as const, weight: 100 }],
    }

    await createJob(payload)

    expect(post).toHaveBeenCalledWith('/jobs', payload)
  })

  it('parses a job JD', async () => {
    post.mockResolvedValue({ code: 200, message: 'success', data: {} })

    await parseJob(1001)
    await parseJob(1001, { jdContent: '熟悉 Java' })

    expect(post).toHaveBeenNthCalledWith(1, '/jobs/1001/parse', {})
    expect(post).toHaveBeenNthCalledWith(2, '/jobs/1001/parse', { jdContent: '熟悉 Java' })
  })
})
