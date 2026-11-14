import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({
  getFeedbackAnalytics: vi.fn(),
}))

vi.mock('@/api/analytics', () => api)

const echarts = vi.hoisted(() => ({
  init: vi.fn(() => ({
    setOption: vi.fn(),
    resize: vi.fn(),
    dispose: vi.fn(),
  })),
}))

vi.mock('echarts', () => echarts)

const push = vi.fn()
vi.mock('vue-router', () => ({
  useRouter: () => ({ push }),
}))

import AnalyticsPage from './index.vue'

const payload = {
  summary: {
    totalApplications: 3,
    readCount: 2,
    interviewCount: 2,
    offerCount: 1,
    feedbackCount: 3,
    readRate: 0.6667,
    interviewRate: 0.6667,
    offerRate: 0.3333,
  },
  funnel: [
    { stage: 'APPLIED', label: '已投递', count: 3, rate: 1 },
    { stage: 'READ', label: '已读', count: 2, rate: 0.6667 },
    { stage: 'INTERVIEW', label: '面试', count: 2, rate: 0.6667 },
    { stage: 'OFFER', label: '录用', count: 1, rate: 0.3333 },
  ],
  versionEffects: [
    {
      resumeVersionId: 2,
      versionNumber: 2,
      applicationCount: 2,
      readCount: 2,
      interviewCount: 2,
      offerCount: 1,
      readRate: 1,
      interviewRate: 1,
      offerRate: 0.5,
    },
  ],
  feedbackDistribution: [
    { feedbackType: 'NOT_INTERESTED', label: '不感兴趣', count: 2, rate: 0.6667 },
    { feedbackType: 'LOCATION_MISMATCH', label: '地点不符', count: 1, rate: 0.3333 },
    { feedbackType: 'SALARY_MISMATCH', label: '薪资不符', count: 0, rate: 0 },
    { feedbackType: 'SKILL_ALREADY_HAVE', label: '技能已掌握', count: 0, rate: 0 },
  ],
}

function mountPage() {
  return mount(AnalyticsPage, {
    attachTo: document.body,
    global: { plugins: [ElementPlus] },
  })
}

describe('AnalyticsPage', () => {
  beforeEach(() => {
    api.getFeedbackAnalytics.mockReset()
    echarts.init.mockClear()
    push.mockReset()
  })

  it('renders funnel, version effects and feedback metrics', async () => {
    api.getFeedbackAnalytics.mockResolvedValue({ code: 200, message: 'success', data: payload })

    const wrapper = mountPage()
    await flushPromises()

    expect(api.getFeedbackAnalytics).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('总投递')
    expect(wrapper.text()).toContain('已读率')
    expect(wrapper.text()).toContain('66.7%')
    expect(wrapper.text()).toContain('录用率')
    expect(wrapper.text()).toContain('33.3%')
    expect(wrapper.text()).toContain('投递漏斗')
    expect(wrapper.text()).toContain('简历版本效果')
    expect(wrapper.text()).toContain('偏好反馈分布')
    expect(wrapper.text()).toContain('不感兴趣')
    expect(wrapper.text()).toContain('2 次')
    expect(echarts.init).toHaveBeenCalledTimes(3)
  })

  it('shows an empty state without initializing charts when there are no applications', async () => {
    api.getFeedbackAnalytics.mockResolvedValue({
      code: 200,
      message: 'success',
      data: {
        summary: {
          totalApplications: 0,
          readCount: 0,
          interviewCount: 0,
          offerCount: 0,
          feedbackCount: 0,
          readRate: 0,
          interviewRate: 0,
          offerRate: 0,
        },
        funnel: [
          { stage: 'APPLIED', label: '已投递', count: 0, rate: 0 },
          { stage: 'READ', label: '已读', count: 0, rate: 0 },
          { stage: 'INTERVIEW', label: '面试', count: 0, rate: 0 },
          { stage: 'OFFER', label: '录用', count: 0, rate: 0 },
        ],
        versionEffects: [],
        feedbackDistribution: [],
      },
    })

    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.text()).toContain('还没有投递数据')
    expect(echarts.init).not.toHaveBeenCalled()
  })
})
