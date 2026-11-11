import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({
  listJobs: vi.fn(),
  getJob: vi.fn(),
  createJob: vi.fn(),
  parseJob: vi.fn(),
}))

const { push } = vi.hoisted(() => ({ push: vi.fn() }))

vi.mock('@/api/jobs', () => api)
vi.mock('vue-router', () => ({ useRouter: () => ({ push }) }))

import type { JobDetail, JobSummary } from '@/api/jobs'

import JobsPage from './index.vue'

const job: JobSummary = {
  id: 1001,
  company: '星云科技',
  position: 'Java 开发实习生',
  city: '深圳',
  salary: '200-300元/天',
  sourceType: 'MOCK',
  externalUrl: null,
  createdAt: '2026-11-10T10:00:00',
}

const detail: JobDetail = {
  ...job,
  jdContent: '岗位职责：参与后端接口开发。任职要求：熟悉 Java 与 Spring Boot。',
  updatedAt: '2026-11-10T10:00:00',
  requirements: [
    { id: 1, skill: 'Java', importance: 'REQUIRED', weight: 60 },
    { id: 2, skill: 'Spring Boot', importance: 'PREFERRED', weight: 40 },
  ],
}

const ElDrawerStub = {
  props: ['modelValue'],
  template: '<div v-if="modelValue"><slot /><slot name="footer" /></div>',
}

function result<T>(data: T) {
  return { code: 200, message: 'success', data }
}

function mountPage() {
  return mount(JobsPage, {
    global: {
      plugins: [ElementPlus],
      stubs: { ElDrawer: ElDrawerStub },
    },
  })
}

function buttonByText(wrapper: ReturnType<typeof mountPage>, text: string) {
  const button = wrapper.findAll('button').find((candidate) => candidate.text().includes(text))
  if (!button) {
    throw new Error(`button not found: ${text}`)
  }
  return button
}

function seedJobs() {
  api.listJobs.mockResolvedValue(result({ total: 1, page: 1, size: 10, records: [job] }))
}

describe('JobsPage', () => {
  beforeEach(() => {
    Object.values(api).forEach((mock) => mock.mockReset())
    push.mockReset()
    class ResizeObserverMock {
      observe() {}
      unobserve() {}
      disconnect() {}
    }
    globalThis.ResizeObserver = ResizeObserverMock as never
  })

  it('loads jobs and renders the list', async () => {
    seedJobs()

    const wrapper = mountPage()
    await flushPromises()

    expect(api.listJobs).toHaveBeenCalledWith({
      page: 1,
      size: 10,
      keyword: undefined,
      city: undefined,
      salary: undefined,
    })
    expect(wrapper.text()).toContain('星云科技')
    expect(wrapper.text()).toContain('Java 开发实习生')
    expect(wrapper.find('.el-pagination__jump').exists()).toBe(true)
  })

  it('searches with entered filters', async () => {
    seedJobs()
    const wrapper = mountPage()
    await flushPromises()

    const input = wrapper.find('input[data-testid="job-keyword"]')
    await input.setValue('Java')
    await buttonByText(wrapper, '搜索').trigger('click')
    await flushPromises()

    expect(api.listJobs).toHaveBeenLastCalledWith({
      page: 1,
      size: 10,
      keyword: 'Java',
      city: undefined,
      salary: undefined,
    })
  })

  it('opens the detail drawer with requirements', async () => {
    seedJobs()
    api.getJob.mockResolvedValue(result(detail))
    const wrapper = mountPage()
    await flushPromises()

    await buttonByText(wrapper, '查看详情').trigger('click')
    await flushPromises()

    expect(api.getJob).toHaveBeenCalledWith(1001)
    expect(wrapper.text()).toContain('岗位职责')
    expect(wrapper.text()).toContain('Spring Boot')
  })

  it('navigates to match analysis', async () => {
    seedJobs()
    const wrapper = mountPage()
    await flushPromises()

    await buttonByText(wrapper, '匹配分析').trigger('click')

    expect(push).toHaveBeenCalledWith('/match/1001')
  })

  it('shows an empty state when no jobs match', async () => {
    api.listJobs.mockResolvedValue(result({ total: 0, page: 1, size: 10, records: [] }))

    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('[data-testid="job-empty"]').exists()).toBe(true)
  })
})
