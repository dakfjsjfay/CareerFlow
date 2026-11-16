import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({
  listJobs: vi.fn(),
  getJob: vi.fn(),
  parseJob: vi.fn(),
  importJob: vi.fn(),
  verifyJob: vi.fn(),
  recommendJobs: vi.fn(),
}))

vi.mock('@/api/jobs', () => ({
  listJobs: api.listJobs,
  getJob: api.getJob,
  parseJob: api.parseJob,
  importJob: api.importJob,
  verifyJob: api.verifyJob,
}))

vi.mock('@/api/match', () => ({
  recommendJobs: api.recommendJobs,
}))

import JobsPage from './index.vue'

const summary = {
  id: 1,
  company: '腾讯云智研发',
  companyId: null,
  position: 'Java 开发实习生',
  city: '深圳',
  salary: '8k-12k',
  sourceType: 'DEMO',
  sourceName: '内置演示数据',
  publishedAt: null,
  verifiedAt: null,
  validUntil: null,
  displayStatus: 'DEMO',
  recruitmentBatch: null,
  recruitmentStatus: 'OPEN',
  requirementCount: 5,
  topSkills: ['Java', 'Spring Boot', 'MySQL'],
}

const detail = {
  ...summary,
  jdContent: '岗位职责：负责后端服务开发。岗位要求：熟悉 Java、Spring Boot、MySQL。',
  externalUrl: null,
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
      plugins: [ElementPlus, createPinia(), router],
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
    expect(wrapper.text()).toContain('演示数据')
    expect(wrapper.text()).toContain('内置演示数据')
  })

  it('updates the match summary when another job is selected', async () => {
    const second = {
      ...summary,
      id: 2,
      position: '前端开发工程师',
      company: '另一家公司',
      sourceType: 'OFFICIAL',
      sourceName: '企业官网',
      displayStatus: 'VERIFIED',
      topSkills: ['Vue', 'TypeScript'],
    }
    api.listJobs.mockResolvedValue({
      code: 200,
      message: 'success',
      data: { items: [summary, second], total: 2, page: 1, size: 8 },
    })
    api.getJob.mockImplementation((id: number) =>
      Promise.resolve({
        code: 200,
        message: 'success',
        data: id === 2 ? { ...detail, ...second } : detail,
      }),
    )
    api.recommendJobs.mockResolvedValue({
      code: 200,
      message: 'success',
      data: {
        items: [
          { jobId: 1, matchScore: 60, rankReasons: ['技能初步匹配'] },
          { jobId: 2, matchScore: 88, rankReasons: ['技能高度匹配', '城市一致'] },
        ],
        total: 2,
      },
    })

    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('[data-testid="jobs-summary"]').text()).toContain('Java 开发实习生')
    expect(wrapper.find('[data-testid="jobs-summary"]').text()).toContain('60')

    const rows = wrapper.findAll('[data-testid="job-card"]')
    await rows[1].trigger('click')
    await flushPromises()

    const summaryPanel = wrapper.find('[data-testid="jobs-summary"]')
    expect(summaryPanel.text()).toContain('前端开发工程师')
    expect(summaryPanel.text()).toContain('88')
    expect(summaryPanel.text()).toContain('技能高度匹配')
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
      sourceType: undefined,
      verificationStatus: undefined,
    })

    await wrapper.find('[data-testid="jobs-reset"]').trigger('click')
    await flushPromises()

    expect(api.listJobs).toHaveBeenLastCalledWith({
      page: 1,
      size: 8,
      keyword: undefined,
      city: undefined,
      salary: undefined,
      sourceType: undefined,
      verificationStatus: undefined,
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
    expect(wrapper.text()).toContain('数据来源与可信度')
    expect(wrapper.text()).toContain('演示数据，仅用于功能体验')

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
