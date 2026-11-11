import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({
  listJobs: vi.fn(),
  getJob: vi.fn(),
  parseJob: vi.fn(),
}))

vi.mock('@/api/jobs', () => api)

import JobsPage from './index.vue'

const summary = {
  id: 1,
  company: '腾讯云智研发',
  position: 'Java 开发实习生',
  city: '深圳',
  salary: '8k-12k',
  sourceType: 'MOCK',
  requirementCount: 5,
  topSkills: ['Java', 'Spring Boot', 'MySQL'],
}

const detail = {
  ...summary,
  jdContent: '岗位职责：负责后端服务开发。岗位要求：熟悉 Java、Spring Boot、MySQL。',
  externalUrl: 'https://example.com/job/1',
  requirements: [
    { id: 1, skill: 'Java', importance: 'REQUIRED', weight: 30 },
    { id: 2, skill: 'Spring Boot', importance: 'REQUIRED', weight: 25 },
    { id: 3, skill: 'MySQL', importance: 'REQUIRED', weight: 20 },
  ],
}

const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/match/:jobId', name: 'Match', component: { template: '<div />' } },
  ],
})

function mountPage() {
  return mount(JobsPage, {
    global: {
      plugins: [ElementPlus, router],
    },
  })
}

describe('JobsPage', () => {
  beforeEach(() => {
    Object.values(api).forEach((mock) => mock.mockReset())
  })

  it('renders the job list from the api', async () => {
    api.listJobs.mockResolvedValue({
      code: 200,
      message: 'success',
      data: { items: [summary], total: 1, page: 1, size: 8 },
    })

    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.text()).toContain('Java 开发实习生')
    expect(wrapper.text()).toContain('腾讯云智研发')
    expect(wrapper.text()).toContain('Java')
    expect(wrapper.text()).toContain('5 项技能要求')
  })

  it('searches with filter values and resets them', async () => {
    api.listJobs.mockResolvedValue({
      code: 200,
      message: 'success',
      data: { items: [], total: 0, page: 1, size: 8 },
    })

    const wrapper = mountPage()
    await flushPromises()

    await wrapper.find('[data-testid="jobs-keyword"]').setValue('Java')
    await wrapper.find('[data-testid="jobs-city"]').setValue('深圳')
    await wrapper.find('[data-testid="jobs-search"]').trigger('click')
    await flushPromises()

    expect(api.listJobs).toHaveBeenLastCalledWith({
      page: 1,
      size: 8,
      keyword: 'Java',
      city: '深圳',
      salary: undefined,
    })

    await wrapper.find('[data-testid="jobs-reset"]').trigger('click')
    await flushPromises()

    expect(api.listJobs).toHaveBeenLastCalledWith({
      page: 1,
      size: 8,
      keyword: undefined,
      city: undefined,
      salary: undefined,
    })
  })

  it('opens the detail drawer and parses the JD', async () => {
    api.listJobs.mockResolvedValue({
      code: 200,
      message: 'success',
      data: { items: [summary], total: 1, page: 1, size: 8 },
    })
    api.getJob.mockResolvedValue({ code: 200, message: 'success', data: detail })
    api.parseJob.mockResolvedValue({ code: 200, message: 'success', data: { jobId: 1 } })

    const wrapper = mountPage()
    await flushPromises()

    await wrapper.find('[data-testid="job-open"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('岗位职责：负责后端服务开发')
    expect(wrapper.findAll('[data-testid="requirement-item"]')).toHaveLength(3)
    expect(wrapper.text()).toContain('必备')
    expect(wrapper.text()).toContain('权重 30')

    await wrapper.find('[data-testid="jobs-parse"]').trigger('click')
    await flushPromises()

    expect(api.parseJob).toHaveBeenCalledWith(1)
  })

  it('navigates to the match page from the card and drawer', async () => {
    api.listJobs.mockResolvedValue({
      code: 200,
      message: 'success',
      data: { items: [summary], total: 1, page: 1, size: 8 },
    })
    api.getJob.mockResolvedValue({ code: 200, message: 'success', data: detail })

    const wrapper = mountPage()
    await flushPromises()

    await wrapper.find('[data-testid="job-open"]').trigger('click')
    await flushPromises()

    const push = vi.spyOn(router, 'push')
    await wrapper.find('[data-testid="jobs-match"]').trigger('click')
    await flushPromises()

    expect(push).toHaveBeenCalledWith('/match/1')
  })
})
