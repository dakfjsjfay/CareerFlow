import { ElMessageBox, ElMessage } from 'element-plus'
import { computed, nextTick, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { listJobs, type JobSummary } from '@/api/jobs'
import {
  analyzeSection,
  chatSection,
  confirmEdit,
  createDraft,
  listDraftEditHistory,
  rejectEdit,
  rewriteSection,
  type EditHistoryItem,
  type SectionAnalysis,
} from '@/api/resumeDraft'
import {
  exportResumeDocx,
  exportResumePdf,
  getResume,
  getResumeVersion,
  listResumeVersions,
  type ResumeVersionSummary,
} from '@/api/resume'
import { downloadBlob } from '@/composables/useResume'
import { useResumeDraftStore } from '@/stores/resumeDraft'
import {
  applySectionToDraftContent,
  draftContentToPreview,
  isDraftEmpty,
  resumeContentToDraftContent,
  sectionForStep,
  sectionToStep,
  SECTION_LABELS,
  type WorkspaceSectionKey,
} from '@/utils/resumeWizard'

import { computeGuide, guideProgress, guideText } from './useWorkspaceGuide'
import { buildSectionStatuses } from './useWorkspaceSectionStatus'

export type WorkspaceMode = 'CREATE' | 'EDIT'

export interface WorkspaceMessage {
  id: string
  role: 'ai' | 'user' | 'system'
  content: string
  time: string
  kind: 'text' | 'guide' | 'candidate' | 'analysis' | 'error'
  sectionKey?: WorkspaceSectionKey
  candidate?: EditHistoryItem
  analysis?: SectionAnalysis
  /** 生成该候选时使用的指令，用于「重新生成」 */
  instruction?: string
  applied?: boolean
  rejected?: boolean
}

export type WorkspaceActionId =
  | 'POLISH'
  | 'STAR'
  | 'QUANTIFY'
  | 'KEYWORDS'
  | 'CONCISE'
  | 'JOB_FIT'
  | 'FACT_CHECK'

export interface WorkspaceAction {
  id: WorkspaceActionId
  label: string
  kind: 'rewrite' | 'analyze'
  instruction?: string
  focus?: string
  requiresJob?: boolean
}

/** 区块级快捷操作，全部复用现有 analyze / rewrite 接口。 */
export const WORKSPACE_ACTIONS: WorkspaceAction[] = [
  {
    id: 'POLISH',
    label: '一键润色',
    kind: 'rewrite',
    instruction: '在不编造事实的前提下，用更专业、结果导向的表达润色该区块，保持事实不变。',
  },
  {
    id: 'STAR',
    label: 'STAR 结构化',
    kind: 'rewrite',
    instruction: '请按 STAR（情境/任务/行动/结果）结构重写该区块，只使用已有事实，不要编造数据。',
  },
  {
    id: 'QUANTIFY',
    label: '量化成果建议',
    kind: 'rewrite',
    instruction: '在不编造数据的前提下，把已有成果改成更具体、可量化的表达；没有数据的地方保持原样。',
  },
  {
    id: 'KEYWORDS',
    label: '关键词优化',
    kind: 'rewrite',
    instruction: '结合目标岗位补充相关的专业关键词，保持原有事实不变。',
  },
  {
    id: 'CONCISE',
    label: '语句精简',
    kind: 'rewrite',
    instruction: '在保留全部关键事实的前提下精简表达，压缩到原长度约 70%。',
  },
  {
    id: 'JOB_FIT',
    label: '岗位适配分析',
    kind: 'analyze',
    focus: '岗位适配',
    requiresJob: true,
  },
  {
    id: 'FACT_CHECK',
    label: '事实与证据检查',
    kind: 'analyze',
    focus: '事实与证据',
  },
]

interface UndoEntry {
  sectionKey: string
  before: unknown
  label: string
}

/** 后端区块分析/改写支持的 sectionKey（求职意向归入基础信息处理）。 */
const REWRITE_SECTIONS: WorkspaceSectionKey[] = [
  'BASIC',
  'EDUCATION',
  'EXPERIENCE',
  'PROJECT',
  'SKILL',
  'SELF_EVALUATION',
  'PHOTO',
]

const BRIDGE_KEY_PREFIX = 'cf.workspace.draft.'

function bridgeKey(resumeId: number): string {
  return `${BRIDGE_KEY_PREFIX}${resumeId}`
}

function messageId(): string {
  return `m${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`
}

function nowTime(): string {
  return new Date().toLocaleTimeString('zh-CN', { hour12: false })
}

function describeError(error: unknown, fallback: string): string {
  const err = error as { response?: { status?: number; data?: { message?: string } } }
  if (err?.response?.status === 409) {
    return '内容已在其他页面更新，请刷新页面后重试'
  }
  return err?.response?.data?.message || (error instanceof Error ? error.message : '') || fallback
}

/**
 * 简历工作区会话：生成/修改双模式共用同一套草稿、预览与候选确认链路。
 * <p>
 * 约束：
 * 1. 不新增后端接口，全部复用 resume-drafts / resume-edit-history / resume versions；
 * 2. AI 只产出候选，必须用户确认后才写入草稿；
 * 3. 生成简历只新增不可变版本，不覆盖历史版本。
 */
export function useWorkspaceSession() {
  const store = useResumeDraftStore()
  const route = useRoute()
  const router = useRouter()

  const mode = ref<WorkspaceMode>('CREATE')
  const resumeId = ref<number | null>(null)
  const resumeName = ref('')
  const ready = ref(false)
  const initError = ref('')
  const messages = ref<WorkspaceMessage[]>([])
  const sending = ref(false)
  const applying = ref(false)
  const generating = ref(false)
  const exporting = ref(false)
  const activeSection = ref<WorkspaceSectionKey>('BASIC')
  const undoStack = ref<UndoEntry[]>([])
  const versions = ref<ResumeVersionSummary[]>([])
  const activeVersionId = ref<number | null>(null)
  const formOpen = ref(true)
  const lastApplied = ref('')
  const jobId = ref<number | null>(null)
  const jobOptions = ref<JobSummary[]>([])
  const jobLoading = ref(false)
  const analyses = ref<Partial<Record<WorkspaceSectionKey, SectionAnalysis>>>({})
  const editHistory = ref<EditHistoryItem[]>([])
  const historyLoading = ref(false)

  let guideTimer: ReturnType<typeof setTimeout> | null = null
  let lastGuideStep = 0
  /** 草稿装载期间不触发 touch，避免刚加载就产生一次无意义的保存 */
  let hydrating = false

  const guide = computed(() => computeGuide(store.content))
  const progress = computed(() => guideProgress(store.content))
  const previewContent = computed(() => draftContentToPreview(store.content))
  const previewEmpty = computed(() => isDraftEmpty(store.content))
  const canUndo = computed(() => undoStack.value.length > 0)
  const sectionNavItems = computed(() => buildSectionStatuses(store.content, analyses.value))
  /** 最新一条待确认候选，供中间栏内联对比展示 */
  const pendingCandidateMessage = computed<WorkspaceMessage | null>(() => {
    for (let index = messages.value.length - 1; index >= 0; index -= 1) {
      const item = messages.value[index]
      if (item.kind === 'candidate' && item.candidate && !item.applied && !item.rejected) {
        return item
      }
    }
    return null
  })
  const pendingCandidate = computed(() => pendingCandidateMessage.value?.candidate ?? null)
  const jobLabel = computed(() => {
    const job = jobOptions.value.find((item) => item.id === jobId.value)
    if (!job) {
      return ''
    }
    return job.company ? `${job.position} · ${job.company}` : job.position
  })
  const draftName = computed({
    get: () => store.draftName,
    set: (value: string) => {
      store.draftName = value
      store.touch()
    },
  })

  function pushMessage(
    role: WorkspaceMessage['role'],
    content: string,
    kind: WorkspaceMessage['kind'] = 'text',
    extra: Partial<WorkspaceMessage> = {},
  ): WorkspaceMessage {
    const message: WorkspaceMessage = {
      id: messageId(),
      role,
      content,
      time: nowTime(),
      kind,
      ...extra,
    }
    messages.value.push(message)
    return message
  }

  function pushAi(content: string, kind: WorkspaceMessage['kind'] = 'text', extra = {}) {
    return pushMessage('ai', content, kind, extra)
  }

  function resetConversation(): void {
    messages.value = []
    undoStack.value = []
    analyses.value = {}
    lastGuideStep = 0
  }

  /** 区块内容变化后，旧的诊断结果不再可信，清掉避免误导。 */
  function clearAnalysis(key: string): void {
    const next = { ...analyses.value }
    delete next[key as WorkspaceSectionKey]
    analyses.value = next
  }

  function greet(): void {
    if (mode.value === 'CREATE') {
      if (guide.value.ready) {
        pushAi(
          '你好，我是 CareerFlow 简历助手。左侧继续用对话或表单补充信息，右侧会实时生成简历预览。',
          'text',
        )
        pushAi(guideText(guide.value), 'guide', { sectionKey: guide.value.section })
        lastGuideStep = guide.value.step
      } else {
        pushAi(
          '你好，我是 CareerFlow 简历助手。我会按八个步骤引导你补全信息，已经填写过的内容不会重复询问；右侧会实时展示简历效果。',
          'text',
        )
        pushAi(guideText(guide.value), 'guide', { sectionKey: guide.value.section })
        lastGuideStep = guide.value.step
      }
      activeSection.value = guide.value.section
      return
    }
    pushAi(
      `已加载「${resumeName.value || '当前简历'}」。在中间预览里点击任意区块，或直接使用右侧快捷操作，我会先生成候选，确认后才会写入简历。`,
      'text',
    )
  }

  function currentStep(): number {
    return store.currentStep
  }

  async function bootCreate(): Promise<void> {
    resetConversation()
    const draftIdParam = Number(route.query.draftId ?? '')
    if (draftIdParam) {
      await store.load(draftIdParam)
    } else {
      const recovered = await store.restoreLast()
      if (recovered) {
        try {
          await ElMessageBox.confirm('检测到未完成的草稿，是否继续上次编辑？', '发现未完成草稿', {
            confirmButtonText: '继续编辑',
            cancelButtonText: '重新开始',
            type: 'info',
          })
        } catch {
          store.reset()
          await store.initNew()
        }
      } else {
        await store.initNew()
      }
    }
    activeSection.value = sectionForStep(currentStep())
    greet()
  }

  async function loadVersions(id: number): Promise<void> {
    try {
      versions.value = (await listResumeVersions(id)).data
    } catch {
      versions.value = []
    }
  }

  /** 关联岗位候选列表，供右栏岗位选择器使用。 */
  async function loadJobs(): Promise<void> {
    if (jobOptions.value.length > 0 || jobLoading.value) {
      return
    }
    jobLoading.value = true
    try {
      const result = await listJobs({ page: 1, size: 50 })
      jobOptions.value = result.data.items ?? []
    } catch {
      jobOptions.value = []
    } finally {
      jobLoading.value = false
    }
  }

  function selectJob(id: number | null): void {
    jobId.value = id
  }

  /** 操作记录只读展示，来自后端 resume_edit_history。 */
  async function loadEditHistory(): Promise<void> {
    if (store.draftId == null) {
      editHistory.value = []
      return
    }
    historyLoading.value = true
    try {
      editHistory.value = (await listDraftEditHistory(store.draftId)).data ?? []
    } catch {
      editHistory.value = []
    } finally {
      historyLoading.value = false
    }
  }

  /** 草稿桥接：把指定版本内容还原为可编辑草稿，不改动历史版本。 */
  async function bridgeFromVersion(versionId: number | null): Promise<void> {
    if (resumeId.value == null) {
      throw new Error('缺少简历 ID')
    }
    const version = versionId ? (await getResumeVersion(resumeId.value, versionId)).data : null
    const content = resumeContentToDraftContent(version?.content)
    const created = await createDraft({
      resumeId: resumeId.value,
      draftName: resumeName.value || undefined,
      content,
    })
    localStorage.setItem(bridgeKey(resumeId.value), String(created.data.id))
    await store.load(created.data.id)
    activeVersionId.value = versionId
  }

  async function bootEdit(id: number): Promise<void> {
    resetConversation()
    resumeId.value = id
    mode.value = 'EDIT'
    const detail = await getResume(id)
    resumeName.value = detail.data.resumeName
    store.draftName = detail.data.resumeName
    await loadVersions(id)
    const queryVersion = Number(route.query.versionId ?? '')
    activeVersionId.value =
      queryVersion || detail.data.currentVersionId || versions.value[0]?.versionId || null

    if (queryVersion) {
      localStorage.removeItem(bridgeKey(id))
      await bridgeFromVersion(queryVersion)
      greet()
      return
    }

    const stored = Number(localStorage.getItem(bridgeKey(id)) ?? '')
    if (stored) {
      try {
        const draft = await store.load(stored)
        if (draft.status === 'DRAFT' && draft.resumeId === id) {
          greet()
          return
        }
      } catch {
        // 草稿已失效，重新桥接
      }
      localStorage.removeItem(bridgeKey(id))
    }
    await bridgeFromVersion(activeVersionId.value)
    greet()
  }

  async function guardedHydrate(task: () => Promise<void>): Promise<void> {
    hydrating = true
    try {
      await task()
    } finally {
      await nextTick()
      hydrating = false
    }
  }

  async function boot(): Promise<void> {
    ready.value = false
    initError.value = ''
    await guardedHydrate(async () => {
      try {
        const param = Number(route.params.resumeId ?? '')
        if (route.name === 'WorkspaceEdit' && param) {
          await bootEdit(param)
          await Promise.all([loadJobs(), loadEditHistory()])
        } else {
          mode.value = 'CREATE'
          resumeId.value = null
          resumeName.value = ''
          await bootCreate()
        }
        ready.value = true
      } catch (error) {
        initError.value = describeError(error, '工作区初始化失败，请稍后重试')
        ElMessage.error(initError.value)
      }
    })
  }

  /** 草稿内容变化后，步骤前进时才推送新的引导，避免刷屏与重复询问。 */
  function scheduleGuide(): void {
    if (mode.value !== 'CREATE' || !ready.value) {
      return
    }
    if (guideTimer) {
      clearTimeout(guideTimer)
    }
    guideTimer = setTimeout(() => {
      guideTimer = null
      const next = guide.value
      if (next.step > lastGuideStep) {
        lastGuideStep = next.step
        pushAi(guideText(next), 'guide', { sectionKey: next.section })
        activeSection.value = next.section
      }
    }, 1200)
  }

  watch(
    () => store.content,
    () => {
      if (hydrating || !ready.value || !store.isDraft) {
        return
      }
      store.touch()
      scheduleGuide()
    },
    { deep: true },
  )

  function selectSection(key: WorkspaceSectionKey, options: { silent?: boolean } = {}): void {
    if (!SECTION_LABELS[key]) {
      return
    }
    activeSection.value = key
    formOpen.value = true
    store.setStep(sectionToStep(key))
    if (options.silent) {
      return
    }
    pushMessage('system', `已切换到「${SECTION_LABELS[key]}」上下文，可以继续提问或描述修改要求。`, 'text')
  }

  function openStep(step: number): void {
    store.setStep(step)
    activeSection.value = sectionForStep(step)
    formOpen.value = true
  }

  async function nextStep(): Promise<void> {
    await store.flush()
    store.setStep(Math.min(store.currentStep + 1, store.totalSteps))
    activeSection.value = sectionForStep(store.currentStep)
  }

  async function prevStep(): Promise<void> {
    await store.flush()
    store.setStep(Math.max(store.currentStep - 1, 1))
    activeSection.value = sectionForStep(store.currentStep)
  }

  function apiSection(key: WorkspaceSectionKey): string {
    return key === 'JOB_INTENTION' ? 'BASIC' : key
  }

  async function requestRewrite(
    instruction: string,
    target?: WorkspaceSectionKey,
  ): Promise<void> {
    if (store.draftId == null) {
      return
    }
    const section = target ?? activeSection.value
    if (!REWRITE_SECTIONS.includes(section)) {
      pushAi(
        `「${SECTION_LABELS[section]}」暂不支持 AI 自动改写，请在左侧表单里直接调整，或切换到其他区块。`,
        'text',
      )
      return
    }
    sending.value = true
    try {
      const result = await rewriteSection(store.draftId, apiSection(section), {
        instruction,
        jobId: jobId.value,
      })
      pushAi(
        `已生成「${SECTION_LABELS[section]}」修改候选。AI 只产出候选，确认后才会写入简历。`,
        'candidate',
        { candidate: result.data, sectionKey: section, instruction },
      )
      await loadEditHistory()
    } catch (error) {
      pushAi(`AI 修改失败：${describeError(error, '请稍后重试')}`, 'error')
    } finally {
      sending.value = false
    }
  }

  /** 生成简历模式下的「AI 润色此区块」：同样只产出候选，需用户确认。 */
  async function rewriteActiveSection(): Promise<void> {
    await requestRewrite('在不编造事实的前提下，用更专业、结果导向的表达润色该区块，保持事实不变。')
  }

  async function send(text: string): Promise<void> {
    const value = text.trim()
    if (!value || sending.value) {
      return
    }
    if (store.draftId == null) {
      ElMessage.warning('草稿尚未就绪，请稍后重试')
      return
    }
    pushMessage('user', value)
    const section = activeSection.value

    if (mode.value === 'EDIT') {
      await requestRewrite(value)
      return
    }

    sending.value = true
    try {
      const history = messages.value
        .filter((item) => item.role !== 'system' && item.kind !== 'candidate')
        .slice(-8)
        .map((item) => ({
          role: item.role === 'user' ? 'user' : 'assistant',
          content: item.content,
        }))
      const result = await chatSection(store.draftId, apiSection(section), {
        message: value,
        history,
      })
      pushAi(result.data.reply || '我暂时没有更多补充，你可以继续完善左侧信息。', 'text')
    } catch (error) {
      pushAi(`AI 暂时没有响应：${describeError(error, '请稍后重试')}`, 'error')
    } finally {
      sending.value = false
    }
  }

  async function analyzeSectionByKey(
    key: WorkspaceSectionKey = activeSection.value,
    focus?: string,
  ): Promise<void> {
    if (store.draftId == null || sending.value) {
      return
    }
    sending.value = true
    try {
      const result = await analyzeSection(store.draftId, apiSection(key), {
        focus,
        jobId: jobId.value,
      })
      analyses.value = { ...analyses.value, [key]: result.data }
      pushAi(`「${SECTION_LABELS[key]}」诊断完成：`, 'analysis', {
        analysis: result.data,
        sectionKey: key,
      })
    } catch (error) {
      pushAi(`区块诊断失败：${describeError(error, '请稍后重试')}`, 'error')
    } finally {
      sending.value = false
    }
  }

  async function analyzeCurrentSection(): Promise<void> {
    await analyzeSectionByKey(activeSection.value)
  }

  /** 右栏快捷操作统一入口：改写类产出候选，分析类只诊断。 */
  async function runAction(id: WorkspaceActionId): Promise<void> {
    const action = WORKSPACE_ACTIONS.find((item) => item.id === id)
    if (!action) {
      return
    }
    if (action.requiresJob && jobId.value == null) {
      ElMessage.warning('请先在右栏选择关联岗位，再进行岗位适配分析')
      return
    }
    if (action.kind === 'analyze') {
      await analyzeSectionByKey(activeSection.value, action.focus)
      return
    }
    await requestRewrite(action.instruction ?? '', activeSection.value)
  }

  /** 用同一指令重新生成当前候选；旧候选标记为拒绝，避免多条待确认。 */
  async function regenerateCandidate(): Promise<void> {
    const message = pendingCandidateMessage.value
    if (!message?.candidate) {
      return
    }
    const section = (message.sectionKey ?? activeSection.value) as WorkspaceSectionKey
    const instruction =
      message.instruction ?? WORKSPACE_ACTIONS[0].instruction ?? '请润色该区块'
    try {
      await rejectEdit(message.candidate.id)
      message.rejected = true
    } catch {
      // 旧候选可能已被处理，忽略后继续生成新候选
    }
    activeSection.value = section
    await requestRewrite(instruction, section)
  }

  async function applyCandidate(
    message: WorkspaceMessage,
    finalContent?: unknown,
  ): Promise<void> {
    const candidate = message.candidate
    if (!candidate || applying.value || message.applied) {
      return
    }
    applying.value = true
    try {
      await confirmEdit(candidate.id, finalContent)
      await store.refreshAfterApply()
      clearAnalysis(candidate.sectionKey)
      undoStack.value.push({
        sectionKey: candidate.sectionKey,
        before: candidate.beforeContent,
        label: SECTION_LABELS[candidate.sectionKey] ?? candidate.sectionKey,
      })
      message.applied = true
      lastApplied.value = `已应用到「${SECTION_LABELS[candidate.sectionKey] ?? candidate.sectionKey}」`
      pushAi('修改已写入简历草稿，中间预览已同步更新。如需回退，可点击顶部「撤销上一步」。', 'text')
      await loadEditHistory()
      ElMessage.success('修改已应用')
    } catch (error) {
      pushAi(`应用修改失败：${describeError(error, '请稍后重试')}`, 'error')
    } finally {
      applying.value = false
    }
  }

  async function rejectCandidate(message: WorkspaceMessage): Promise<void> {
    const candidate = message.candidate
    if (!candidate || message.rejected) {
      return
    }
    try {
      await rejectEdit(candidate.id)
      message.rejected = true
      pushAi('已拒绝本次修改，简历保持原样。', 'text')
      await loadEditHistory()
    } catch (error) {
      pushAi(`拒绝修改失败：${describeError(error, '请稍后重试')}`, 'error')
    }
  }

  async function undoLast(): Promise<void> {
    const entry = undoStack.value.pop()
    if (!entry) {
      return
    }
    store.content = applySectionToDraftContent(store.content, entry.sectionKey, entry.before)
    store.touch()
    clearAnalysis(entry.sectionKey)
    lastApplied.value = `已撤销「${entry.label}」`
    pushAi(`已撤销对「${entry.label}」的修改，预览已恢复为修改前内容。`, 'text')
    ElMessage.success('已撤销上一步修改')
  }

  function resolvedResumeName(): string {
    const name = store.draftName.trim() || resumeName.value.trim()
    if (name) {
      return name
    }
    return store.content.basic.realName.trim()
      ? `${store.content.basic.realName.trim()}的简历`
      : '我的简历'
  }

  async function generate(): Promise<void> {
    if (generating.value || store.draftId == null) {
      return
    }
    if (!store.content.basic.realName.trim()) {
      ElMessage.warning('请先补充姓名')
      openStep(1)
      return
    }
    if (!store.content.basic.phone.trim() && !store.content.basic.email?.trim()) {
      ElMessage.warning('请至少填写手机号或邮箱')
      openStep(1)
      return
    }
    generating.value = true
    try {
      const result = await store.complete({
        resumeName: resolvedResumeName(),
        templateId: store.content.preview.templateId,
        generationReason: mode.value === 'CREATE' ? 'INITIAL' : 'REGENERATE',
      })
      ElMessage.success(`已生成新版本 v${result.versionNumber}`)
      if (mode.value === 'CREATE') {
        await router.replace(`/workspace/${result.resumeId}`)
        return
      }
      localStorage.removeItem(bridgeKey(result.resumeId))
      ready.value = false
      await guardedHydrate(() => bootEdit(result.resumeId))
      ready.value = true
      await loadEditHistory()
    } catch (error) {
      ElMessage.error(describeError(error, '生成简历失败，请稍后重试'))
      pushAi(`生成失败：${describeError(error, '请稍后重试')}`, 'error')
    } finally {
      generating.value = false
    }
  }

  async function exportFile(format: 'PDF' | 'DOCX', versionId?: number): Promise<void> {
    const target = versionId ?? activeVersionId.value
    if (resumeId.value == null || target == null) {
      ElMessage.warning('请先生成简历版本后再导出')
      return
    }
    exporting.value = true
    try {
      const result =
        format === 'PDF'
          ? await exportResumePdf(resumeId.value, target)
          : await exportResumeDocx(resumeId.value, target)
      downloadBlob(result.blob, result.filename)
    } catch (error) {
      ElMessage.error(describeError(error, `${format} 导出失败`))
    } finally {
      exporting.value = false
    }
  }

  async function switchVersion(versionId: number): Promise<void> {
    if (resumeId.value == null || versionId === activeVersionId.value) {
      return
    }
    try {
      await ElMessageBox.confirm(
        '切换版本会用该版本内容重新建立可编辑草稿，当前未应用的修改将丢失，是否继续？',
        '切换版本',
        { confirmButtonText: '继续', cancelButtonText: '取消', type: 'warning' },
      )
    } catch {
      return
    }
    try {
      if (store.draftId != null && store.isDraft) {
        await store.abandon().catch(() => undefined)
      }
      localStorage.removeItem(bridgeKey(resumeId.value))
      resetConversation()
      await guardedHydrate(async () => {
        await bridgeFromVersion(versionId)
      })
      await loadEditHistory()
      pushAi(`已切换到 v${versions.value.find((item) => item.versionId === versionId)?.versionNumber ?? ''}，可以继续修改。`, 'text')
    } catch (error) {
      ElMessage.error(describeError(error, '版本切换失败'))
    }
  }

  async function exit(): Promise<void> {
    await store.flush()
    void router.push('/resumes')
  }

  async function switchToFormMode(): Promise<void> {
    await store.flush()
    const query = store.draftId ? { draftId: String(store.draftId) } : undefined
    void router.push({ path: '/resumes/create', query })
  }

  return {
    store,
    mode,
    resumeId,
    resumeName,
    ready,
    initError,
    messages,
    sending,
    applying,
    generating,
    exporting,
    activeSection,
    versions,
    activeVersionId,
    canUndo,
    lastApplied,
    formOpen,
    guide,
    progress,
    previewContent,
    previewEmpty,
    draftName,
    jobId,
    jobOptions,
    jobLoading,
    jobLabel,
    sectionNavItems,
    pendingCandidate,
    pendingCandidateMessage,
    editHistory,
    historyLoading,
    loadJobs,
    selectJob,
    loadEditHistory,
    boot,
    retryBoot: boot,
    selectSection,
    openStep,
    nextStep,
    prevStep,
    send,
    rewriteActiveSection,
    analyzeCurrentSection,
    analyzeSectionByKey,
    runAction,
    regenerateCandidate,
    applyCandidate,
    rejectCandidate,
    undoLast,
    generate,
    exportFile,
    switchVersion,
    exit,
    switchToFormMode,
  }
}
