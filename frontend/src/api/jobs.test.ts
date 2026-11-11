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

  it('lists jobs with filter params', async () => {
    get.mockResolvedValue({ code: 200, message: 'success', data: { items: [], total: 0 } })

    await listJobs()
    await listJobs({ page: 1, size: 8, keyword: 'Java', city: '深圳', salary: '8k' })

    expect(get).toHaveBeenNthCalledWith(1, '/jobs', { params: {} })
    expect(get).toHaveBeenNthCalledWith(2, '/jobs', {
      params: { page: 1, size: 8, keyword: 'Java', city: '深圳', salary: '8k' },
    })
  })

  it('reads a job detail and parses its JD', async () => {
    get.mockResolvedValue({ code: 200, message: 'success', data: { id: 1 } })
    post.mockResolvedValue({ code: 200, message: 'success', data: { jobId: 1 } })

    await getJob(1)
    await parseJob(1)
    await parseJob(1, '熟悉 Java、MySQL。')

    expect(get).toHaveBeenCalledWith('/jobs/1')
    expect(post).toHaveBeenNthCalledWith(1, '/jobs/1/parse', { jdContent: undefined })
    expect(post).toHaveBeenNthCalledWith(2, '/jobs/1/parse', { jdContent: '熟悉 Java、MySQL。' })
  })

  it('creates a manual job', async () => {
    post.mockResolvedValue({ code: 200, message: 'success', data: { id: 1 } })

    await createJob({ position: 'Java 开发实习生', company: '某公司' })

    expect(post).toHaveBeenCalledWith('/jobs', { position: 'Java 开发实习生', company: '某公司' })
  })
})
