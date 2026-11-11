import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const jobApi = vi.hoisted(() => ({
  getJob: vi.fn(),
}))

const matchApi = vi.hoisted(() => ({
  analyzeMatch: vi.fn(),
}))

const resumeApi = vi.hoisted(() => ({
  listResumes: vi.fn(),
  listResumeVersions: vi.fn(),
}))

vi.mock('@/api/jobs', () => jobApi)
vi.mock('@/api/match', () => matchApi)
vi.mock('@/api/resume', () => resumeApi)

import MatchPage from './index.vue'

const job = {
  id: 1,
  company: '腾讯云智研发',
  position: 'Java 开发实习生',
  city: '深圳',
  salary: '8k-12k',
  sourceType: 'MOCK',
  requirementCount: 5,
  topSkills: ['Java', 'Spring Boot', 'MySQL'],
  jdContent: null,
  externalUrl: null,
  requirements: [],
}

const resume = { id: 1, resumeName: '我的简历', currentVersionId: 3, currentVersionNumber: 1, templateId: null, updatedAt: null }
const versions = [{ versionId: 3, versionNumber: 1, generationReason: 'INITIAL', templateId: null, createdAt: null }]

const analyzeResult = {
  matchResultId: 1,
  jobId: 1,
  resumeVersionId: 3,
  matchScore: 40,
  matchedCount: 1,
  partialCount: 1,
  missingCount: 3,
  unconfirmedCount: 0,
  details: [
    { skill: 'Java', status: 'MATCHED', evidence: '项目《学生信息管理系统》技术栈', suggestion: '保留' },
    { skill: 'Spring Boot', status: 'MISSING', evidence: null, suggestion: '发起追问确认是否学习过' },
    { skill: 'MySQL', status: 'PARTIAL', evidence: '个人技能记录中有学习经历', suggestion: '补充项目应用证据' },
  ],
}

const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/match/:jobId', name: 'Match', component: { template: '<div />' } },
    { path: '/resume', name: 'Resume', component: { template: '<div />' } },
  ],
})

async function mountPage() {
  await router.push('/match/1')
  await router.isReady()
  return mount(MatchPage, {
    global: {
      plugins: [ElementPlus, router],
    },
  })
}

describe('MatchPage', () => {
  beforeEach(() => {
    Object.values(jobApi).forEach((mock) => mock.mockReset())
    Object.values(matchApi).forEach((mock) => mock.mockReset())
    Object.values(resumeApi).forEach((mock) => mock.mockReset())
  })

  it('loads job and default version, then analyzes', async () => {
    jobApi.getJob.mockResolvedValue({ code: 200, message: 'success', data: job })
    resumeApi.listResumes.mockResolvedValue({ code: 200, message: 'success', data: [resume] })
    resumeApi.listResumeVersions.mockResolvedValue({ code: 200, message: 'success', data: versions })
    matchApi.analyzeMatch.mockResolvedValue({ code: 200, message: 'success', data: analyzeResult })

    const wrapper = await mountPage()
    await flushPromises()

    expect(wrapper.text()).toContain('Java 开发实习生')

    await wrapper.find('[data-testid="analyze"]').trigger('click')
    await flushPromises()

    expect(matchApi.analyzeMatch).toHaveBeenCalledWith({ jobId: 1, resumeVersionId: 3 })
    expect(wrapper.text()).toContain('40')
    expect(wrapper.text()).toContain('已满足 1')
    expect(wrapper.text()).toContain('未体现 3')
    expect(wrapper.text()).toContain('项目《学生信息管理系统》技术栈')
    expect(wrapper.findAll('[data-testid="detail-row"]')).toHaveLength(3)
  })

  it('shows the follow-up button only for gaps', async () => {
    jobApi.getJob.mockResolvedValue({ code: 200, message: 'success', data: job })
    resumeApi.listResumes.mockResolvedValue({ code: 200, message: 'success', data: [resume] })
    resumeApi.listResumeVersions.mockResolvedValue({ code: 200, message: 'success', data: versions })
    matchApi.analyzeMatch.mockResolvedValue({ code: 200, message: 'success', data: analyzeResult })

    const wrapper = await mountPage()
    await flushPromises()

    await wrapper.find('[data-testid="analyze"]').trigger('click')
    await flushPromises()

    // 3 条详情里：MISSING(Spring Boot) 与 PARTIAL(MySQL) 显示追问按钮，MATCHED(Java) 不显示
    expect(wrapper.findAll('[data-testid="follow-up"]')).toHaveLength(2)
  })

  it('prompts to create a resume when none exists', async () => {
    jobApi.getJob.mockResolvedValue({ code: 200, message: 'success', data: job })
    resumeApi.listResumes.mockResolvedValue({ code: 200, message: 'success', data: [] })

    const wrapper = await mountPage()
    await flushPromises()

    expect(wrapper.text()).toContain('还没有简历版本')
    expect(wrapper.find('[data-testid="goto-resume"]').exists()).toBe(true)
  })
})
