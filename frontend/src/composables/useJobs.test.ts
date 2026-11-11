import { beforeEach, describe, expect, it, vi } from 'vitest'

const { listJobs } = vi.hoisted(() => ({
  listJobs: vi.fn(),
}))

vi.mock('@/api/jobs', () => ({ listJobs }))

import type { JobPage, JobSummary } from '@/api/jobs'

import { useJobs } from './useJobs'

function job(id: number): JobSummary {
  return {
    id,
    company: '星云科技',
    position: 'Java 开发实习生',
    city: '深圳',
    salary: '200-300元/天',
    sourceType: 'MOCK',
    externalUrl: null,
    createdAt: '2026-11-10T10:00:00',
  }
}

function page(records: JobSummary[], total = records.length): JobPage {
  return { total, page: 1, size: 10, records }
}

function result(data: JobPage) {
  return { code: 200, message: 'success', data }
}

describe('useJobs', () => {
  beforeEach(() => {
    listJobs.mockReset()
  })

  it('loads the first page and stores metadata', async () => {
    listJobs.mockResolvedValue(result(page([job(1001)], 1)))

    const jobs = useJobs()
    await jobs.load()

    expect(listJobs).toHaveBeenCalledWith({
      page: 1,
      size: 10,
      keyword: undefined,
      city: undefined,
      salary: undefined,
    })
    expect(jobs.jobs.value).toHaveLength(1)
    expect(jobs.total.value).toBe(1)
  })

  it('searches from page one with trimmed filters', async () => {
    listJobs.mockResolvedValue(result(page([])))
    const jobs = useJobs()
    jobs.page.value = 3
    jobs.filters.keyword = '  Java '
    jobs.filters.city = ' 深圳 '
    jobs.filters.salary = ' 200 '

    await jobs.search()

    expect(listJobs).toHaveBeenCalledWith({
      page: 1,
      size: 10,
      keyword: 'Java',
      city: '深圳',
      salary: '200',
    })
  })

  it('resets filters and page', async () => {
    listJobs.mockResolvedValue(result(page([])))
    const jobs = useJobs()
    jobs.page.value = 4
    jobs.filters.keyword = 'Java'
    jobs.filters.city = '深圳'
    jobs.filters.salary = '200'

    await jobs.reset()

    expect(jobs.page.value).toBe(1)
    expect(jobs.filters.keyword).toBe('')
    expect(jobs.filters.city).toBe('')
    expect(jobs.filters.salary).toBe('')
    expect(listJobs).toHaveBeenCalledWith({
      page: 1,
      size: 10,
      keyword: undefined,
      city: undefined,
      salary: undefined,
    })
  })

  it('changes page and size', async () => {
    listJobs.mockResolvedValue(result(page([])))
    const jobs = useJobs()

    await jobs.changePage(2)
    expect(listJobs).toHaveBeenLastCalledWith({
      page: 2,
      size: 10,
      keyword: undefined,
      city: undefined,
      salary: undefined,
    })

    await jobs.changeSize(20)
    expect(jobs.page.value).toBe(1)
    expect(listJobs).toHaveBeenLastCalledWith({
      page: 1,
      size: 20,
      keyword: undefined,
      city: undefined,
      salary: undefined,
    })
  })
})
