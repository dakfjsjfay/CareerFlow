import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({
  listApplications: vi.fn(),
  listFollowUps: vi.fn(),
  getApplication: vi.fn(),
  getApplicationHistory: vi.fn(),
  updateApplicationStatus: vi.fn(),
  handleFollowUp: vi.fn(),
}))

vi.mock('@/api/applications', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/applications')>()
  return { ...actual, ...api }
})

import ApplicationsPage from './index.vue'

const application = {
  id: 1,
  jobId: 10,
  company: '腾讯云智研发',
  position: 'Java 开发实习生',
  city: '深圳',
  salary: '8k-12k',
  resumeVersionId: 3,
  versionNumber: 2,
  status: 'APPLIED',
  appliedAt: '2026-11-05T10:00:00',
  nextFollowUpAt: null,
  lastFollowUpAt: null,
  updatedAt: '2026-11-05T10:00:00',
}

const followUp = {
  applicationId: 1,
  jobId: 10,
  company: '腾讯云智研发',
  position: 'Java 开发实习生',
  status: 'APPLIED',
  lastUpdateAt: '2026-11-05T10:00:00',
  daysNoResponse: 3,
}

function mountPage() {
  return mount(ApplicationsPage, {
    attachTo: document.body,
    global: { plugins: [ElementPlus] },
  })
}

describe('ApplicationsPage', () => {
  beforeEach(() => {
    Object.values(api).forEach((mock) => mock.mockReset())
    api.listApplications.mockResolvedValue({
      code: 200,
      message: 'success',
      data: { items: [application], total: 1, page: 1, size: 10 },
    })
    api.listFollowUps.mockResolvedValue({
      code: 200,
      message: 'success',
      data: { count: 1, items: [followUp] },
    })
  })

  it('renders applications with status and resume version', async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(api.listApplications).toHaveBeenCalledWith({ page: 1, size: 10, status: undefined })
    expect(wrapper.text()).toContain('Java 开发实习生')
    expect(wrapper.text()).toContain('腾讯云智研发')
    expect(wrapper.text()).toContain('已投递')
    expect(wrapper.text()).toContain('v2')
  })

  it('shows follow-up reminders with idle days', async () => {
    const wrapper = mountPage()
    await flushPromises()

    const followTab = wrapper
      .findAll('.el-tabs__item')
      .find((tab) => tab.text().includes('待跟进'))
    expect(followTab).toBeTruthy()
    await followTab!.trigger('click')
    await flushPromises()

    expect(wrapper.findAll('[data-testid="follow-item"]')).toHaveLength(1)
    expect(wrapper.text()).toContain('3 天未响应')
  })

  it('opens the detail drawer with status history', async () => {
    api.getApplication.mockResolvedValue({ code: 200, message: 'success', data: application })
    api.getApplicationHistory.mockResolvedValue({
      code: 200,
      message: 'success',
      data: [
        {
          id: 1,
          oldStatus: 'PENDING',
          newStatus: 'APPLIED',
          note: '模拟一键投递',
          createdAt: '2026-11-05T10:00:00',
        },
      ],
    })

    const wrapper = mountPage()
    await flushPromises()

    const detailButton = wrapper.findAll('button').find((button) => button.text() === '详情')
    expect(detailButton).toBeTruthy()
    await detailButton!.trigger('click')
    await flushPromises()
    // el-drawer 通过 teleport + transition 渲染，等待一帧后再断言
    await new Promise((resolve) => setTimeout(resolve, 50))
    await flushPromises()

    expect(api.getApplication).toHaveBeenCalledWith(1)
    expect(api.getApplicationHistory).toHaveBeenCalledWith(1)
    expect(document.body.textContent).toContain('模拟一键投递')
    wrapper.unmount()
  })
})
