import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { createSession, listMessages, listSessions, sendMessage, listEvidence, confirmEvidence } =
  vi.hoisted(() => ({
    createSession: vi.fn(),
    listMessages: vi.fn(),
    listSessions: vi.fn(),
    sendMessage: vi.fn(),
    listEvidence: vi.fn(),
    confirmEvidence: vi.fn(),
  }))

const optimizationApi = vi.hoisted(() => ({
  getOptimizationTask: vi.fn(),
  replyOptimizationTask: vi.fn(),
  confirmOptimizationTask: vi.fn(),
}))

vi.mock('@/api/chat', () => ({ createSession, listMessages, listSessions, sendMessage }))
vi.mock('@/api/profile', () => ({ listEvidence, confirmEvidence }))
vi.mock('@/api/optimization', () => optimizationApi)

const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/chat', name: 'Chat', component: { template: '<div />' } },
    { path: '/resume', name: 'Resume', component: { template: '<div />' } },
    { path: '/match/:jobId', name: 'Match', component: { template: '<div />' } },
  ],
})

import type { EvidenceItem } from '@/api/profile'

import ChatPage from './index.vue'

const session = {
  id: 1,
  title: '测试对话',
  status: 'ACTIVE',
  createdAt: '2026-11-10T10:00:00',
  updatedAt: '2026-11-10T10:00:00',
}

const pendingEvidence: EvidenceItem = {
  id: 1,
  entityType: 'PROFILE',
  entityId: null,
  fieldName: 'real_name',
  sourceType: 'CHAT',
  sourceId: 10,
  sourceContent: '我叫张三',
  evidenceContent: '张三',
  confirmationStatus: 'PENDING',
  createdAt: '2026-11-10T10:00:00',
}

function apiResult<T>(data: T) {
  return { code: 200, message: 'success', data }
}

const ElDialogStub = {
  props: ['modelValue', 'title'],
  template:
    '<div v-if="modelValue"><div class="el-dialog__title">{{ title }}</div><slot /><slot name="footer" /></div>',
}

function mountPage() {
  return mount(ChatPage, {
    global: {
      plugins: [ElementPlus, router],
      stubs: {
        DocumentAnalyzer: true,
        ElDialog: ElDialogStub,
      },
    },
  })
}

const task = {
  id: 7,
  jobId: 1,
  resumeVersionId: 3,
  chatSessionId: 1,
  targetSkill: 'Spring Boot',
  question: '你学习过 Spring Boot 吗？',
  status: 'WAITING_USER',
  source: 'JD_GAP_ANALYSIS',
  createdAt: '2026-11-11T10:00:00',
  updatedAt: '2026-11-11T10:00:00',
}

function buttonByText(wrapper: ReturnType<typeof mountPage>, text: string) {
  const button = wrapper.findAll('button').find((candidate) => candidate.text().includes(text))
  if (!button) {
    throw new Error(`button not found: ${text}`)
  }
  return button
}

async function sendMessageFromPage(wrapper: ReturnType<typeof mountPage>) {
  await wrapper.find('textarea').setValue('我叫张三')
  await buttonByText(wrapper, '发送').trigger('click')
  await flushPromises()
}

describe('Chat page evidence confirmation', () => {
  beforeEach(async () => {
    vi.clearAllMocks()
    await router.push('/chat')
    listSessions.mockResolvedValue(apiResult([session]))
    listMessages.mockResolvedValue(apiResult([]))
    listEvidence.mockResolvedValue(apiResult([pendingEvidence]))
    confirmEvidence.mockResolvedValue(apiResult({ confirmedCount: 1, updatedFields: ['realName'] }))
    sendMessage.mockResolvedValue(
      apiResult({
        sessionId: 1,
        reply: '好的，已记录',
        extractedInfo: { basic: { realName: '张三' } },
        missingFields: [],
      }),
    )
  })

  it('shows the pending count on mount without opening the dialog', async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.text()).toContain('待确认 1')
    expect(wrapper.text()).not.toContain('姓名')
  })

  it('opens the confirmation dialog after a send that produced pending evidence', async () => {
    const wrapper = mountPage()
    await flushPromises()

    await sendMessageFromPage(wrapper)

    expect(sendMessage).toHaveBeenCalledWith(1, '我叫张三')
    expect(listEvidence).toHaveBeenCalledWith('PENDING')
    expect(wrapper.text()).toContain('姓名')
    expect(wrapper.text()).toContain('来源：我叫张三')
  })

  it('does not open the dialog when there is no pending evidence', async () => {
    listEvidence.mockResolvedValue(apiResult([]))
    const wrapper = mountPage()
    await flushPromises()

    await sendMessageFromPage(wrapper)

    expect(wrapper.text()).not.toContain('姓名')
    expect(wrapper.text()).not.toContain('待确认 1')
  })

  it('confirms selected evidence from the dialog and closes when nothing remains', async () => {
    listEvidence
      .mockResolvedValueOnce(apiResult([pendingEvidence]))
      .mockResolvedValueOnce(apiResult([pendingEvidence]))
      .mockResolvedValueOnce(apiResult([]))
    const wrapper = mountPage()
    await flushPromises()
    await sendMessageFromPage(wrapper)

    await wrapper.find('[data-testid="select-all"]').setValue(true)
    await buttonByText(wrapper, '确认选中').trigger('click')
    await flushPromises()

    expect(confirmEvidence).toHaveBeenCalledWith([1], true)
    expect(wrapper.text()).not.toContain('姓名')
  })

  it('rejects selected evidence from the dialog', async () => {
    const wrapper = mountPage()
    await flushPromises()
    await sendMessageFromPage(wrapper)

    await wrapper.find('[data-testid="evidence-1"]').setValue(true)
    await buttonByText(wrapper, '忽略选中').trigger('click')
    await flushPromises()

    expect(confirmEvidence).toHaveBeenCalledWith([1], false)
  })

  it('enters optimization task mode from taskId query and replies via optimization api', async () => {
    await router.push('/chat?taskId=7')
    await router.isReady()
    optimizationApi.getOptimizationTask.mockResolvedValue(apiResult(task))
    optimizationApi.replyOptimizationTask.mockResolvedValue(
      apiResult({
        reply: '好的，能说说你负责的部分吗？',
        nextQuestion: true,
        taskStatus: 'WAITING_USER',
        extractedInfo: {},
        missingFields: [],
      }),
    )

    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.text()).toContain('反向优化 · Spring Boot')
    expect(wrapper.find('[data-testid="optimization-banner"]').exists()).toBe(true)

    await sendMessageFromPage(wrapper)

    expect(optimizationApi.replyOptimizationTask).toHaveBeenCalledWith(7, '我叫张三')
    expect(sendMessage).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('好的，能说说你负责的部分吗？')
  })

  it('shows confirm button at USER_CONFIRMATION and confirms to regenerate resume', async () => {
    await router.push('/chat?taskId=7')
    await router.isReady()
    optimizationApi.getOptimizationTask.mockResolvedValue(
      apiResult({ ...task, status: 'USER_CONFIRMATION' }),
    )
    optimizationApi.confirmOptimizationTask.mockResolvedValue(
      apiResult({
        taskId: 7,
        taskStatus: 'RESUME_REGENERATED',
        jobId: 1,
        resumeId: 1,
        resumeVersionId: 5,
        versionNumber: 2,
        evidenceCount: 2,
        updatedFields: ['skills', 'project.techStack'],
      }),
    )

    const wrapper = mountPage()
    await flushPromises()

    const confirmButton = wrapper.find('[data-testid="confirm-task"]')
    expect(confirmButton.exists()).toBe(true)

    await confirmButton.trigger('click')
    await flushPromises()

    expect(optimizationApi.confirmOptimizationTask).toHaveBeenCalledWith(7)
    expect(wrapper.text()).toContain('反向优化完成')
    expect(wrapper.text()).toContain('v2')
    expect(wrapper.find('[data-testid="go-rematch"]').exists()).toBe(true)
  })
})
