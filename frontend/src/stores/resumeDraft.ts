import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

import {
  abandonDraft,
  completeDraft,
  createDraft,
  getDraft,
  listDrafts,
  saveDraft,
  type DraftCompleteResult,
  type ResumeDraft,
  type ResumeDraftContent,
} from '@/api/resumeDraft'
import {
  createEmptyDraftContent,
  normalizeDraftContent,
  TOTAL_STEPS,
} from '@/utils/resumeWizard'

/** 自动保存间隔：需求要求不超过 3 秒，取 2.5 秒留出网络余量 */
export const SAVE_INTERVAL_MS = 2500

const DRAFT_ID_KEY = 'cf.resumeDraft.id'
const SNAPSHOT_KEY = 'cf.resumeDraft.snapshot'

export type SaveState = 'idle' | 'pending' | 'saving' | 'saved' | 'error'

interface LocalSnapshot {
  draftId: number
  revision: number
  currentStep: number
  content: ResumeDraftContent
  dirty: boolean
  savedAt: number
}

function readSnapshot(): LocalSnapshot | null {
  try {
    const raw = localStorage.getItem(SNAPSHOT_KEY)
    return raw ? (JSON.parse(raw) as LocalSnapshot) : null
  } catch {
    return null
  }
}

function writeSnapshot(snapshot: LocalSnapshot | null) {
  if (!snapshot) {
    localStorage.removeItem(SNAPSHOT_KEY)
    return
  }
  try {
    localStorage.setItem(SNAPSHOT_KEY, JSON.stringify(snapshot))
  } catch {
    // 超出 localStorage 配额时放弃快照，不影响后端保存
  }
}

/**
 * 八步简历草稿状态与自动保存（docs/2.0/ui-redesign.md §4.1）。
 * <p>
 * 保存策略：
 * 1. 任意字段变化 2.5 秒内节流保存到后端（不依赖用户点「下一步」）；
 * 2. 每次变化同步写入 localStorage 快照，保存失败或异常退出后可恢复；
 * 3. revision 乐观锁冲突时进入 error，由页面提示刷新。
 */
export const useResumeDraftStore = defineStore('resumeDraft', () => {
  const draftId = ref<number | null>(null)
  const draftName = ref('')
  const revision = ref(1)
  const currentStep = ref(1)
  const content = ref<ResumeDraftContent>(createEmptyDraftContent())
  const status = ref<'DRAFT' | 'COMPLETED' | 'ABANDONED'>('DRAFT')
  const saveState = ref<SaveState>('idle')
  const lastSavedAt = ref<string | null>(null)
  const errorMessage = ref('')
  const loading = ref(false)

  let timer: ReturnType<typeof setTimeout> | null = null
  let dirty = false
  /** 变更序号：保存期间若又有新改动，成功后仍保持 dirty，避免丢失 */
  let changeSeq = 0
  /** 保存串行链：保证「生成前保存」一定等前一个请求结束后再读取最新内容 */
  let saveChain: Promise<void> = Promise.resolve()
  let unloadBound = false

  const totalSteps = computed(() => TOTAL_STEPS)
  const isDraft = computed(() => status.value === 'DRAFT')

  function bindUnload() {
    if (unloadBound || typeof window === 'undefined') {
      return
    }
    unloadBound = true
    window.addEventListener('beforeunload', (event) => {
      if (!dirty || draftId.value == null) {
        return
      }
      persistSnapshot()
      // 仍有未落库改动时提示用户（浏览器会展示通用离开确认）
      event.preventDefault()
      event.returnValue = ''
    })
  }

  function persistSnapshot() {
    if (draftId.value == null) {
      return
    }
    writeSnapshot({
      draftId: draftId.value,
      revision: revision.value,
      currentStep: currentStep.value,
      content: content.value,
      dirty,
      savedAt: Date.now(),
    })
  }

  function hydrate(draft: ResumeDraft, local?: LocalSnapshot | null) {
    draftId.value = draft.id
    draftName.value = draft.draftName ?? ''
    revision.value = draft.revision
    status.value = draft.status
    lastSavedAt.value = draft.lastSavedAt
    const backendContent = normalizeDraftContent(draft.content)

    if (local && local.draftId === draft.id && local.dirty) {
      // 本地存在未成功落库的更新：优先恢复本地内容，但使用后端 revision 以便继续保存
      content.value = normalizeDraftContent(local.content)
      currentStep.value = Math.min(Math.max(local.currentStep, 1), TOTAL_STEPS)
      dirty = true
      saveState.value = 'pending'
      scheduleSave()
    } else {
      content.value = backendContent
      currentStep.value = Math.min(Math.max(draft.currentStep, 1), TOTAL_STEPS)
      dirty = false
      saveState.value = 'saved'
      persistSnapshot()
    }
    localStorage.setItem(DRAFT_ID_KEY, String(draft.id))
    bindUnload()
  }

  async function initNew(name = ''): Promise<ResumeDraft> {
    loading.value = true
    try {
      const result = await createDraft({ draftName: name || undefined })
      content.value = createEmptyDraftContent()
      hydrate(result.data, null)
      return result.data
    } finally {
      loading.value = false
    }
  }

  /** 读取最近一份未完成草稿；没有则返回 null */
  async function findRecoverable(): Promise<ResumeDraft | null> {
    try {
      const result = await listDrafts('DRAFT')
      const items = result.data ?? []
      if (items.length === 0) {
        return null
      }
      return items[0]
    } catch {
      return null
    }
  }

  async function load(id: number): Promise<ResumeDraft> {
    loading.value = true
    try {
      const result = await getDraft(id)
      hydrate(result.data, readSnapshot())
      return result.data
    } finally {
      loading.value = false
    }
  }

  /** 恢复上次编辑：优先使用 localStorage 记录的草稿 ID */
  async function restoreLast(): Promise<ResumeDraft | null> {
    const storedId = Number(localStorage.getItem(DRAFT_ID_KEY) ?? '')
    if (storedId) {
      try {
        return await load(storedId)
      } catch {
        localStorage.removeItem(DRAFT_ID_KEY)
      }
    }
    const latest = await findRecoverable()
    if (!latest) {
      return null
    }
    return load(latest.id)
  }

  /** 任意字段变化：标记脏数据 + 写入本地快照 + 安排自动保存 */
  function touch() {
    if (!isDraft.value) {
      return
    }
    dirty = true
    changeSeq += 1
    saveState.value = 'pending'
    persistSnapshot()
    scheduleSave()
  }

  function scheduleSave() {
    if (timer != null) {
      return
    }
    timer = setTimeout(() => {
      timer = null
      void flush()
    }, SAVE_INTERVAL_MS)
  }

  function cancelTimer() {
    if (timer != null) {
      clearTimeout(timer)
      timer = null
    }
  }

  /** 立即保存（步骤切换、离开页面、生成前调用）；串行执行，失败不抛出，由 saveState 反映 */
  function flush(): Promise<void> {
    cancelTimer()
    if (!dirty || draftId.value == null || !isDraft.value) {
      return saveChain
    }
    saveChain = saveChain.then(async () => {
      // 排队期间上一次保存可能已把内容落库，重新检查避免重复请求
      if (!dirty || draftId.value == null || !isDraft.value) {
        return
      }
      // 已被后续保存覆盖时取消旧的延时任务
      cancelTimer()
      saveState.value = 'saving'
      errorMessage.value = ''
      const seqAtSave = changeSeq
      try {
        const result = await saveDraft(draftId.value, {
          currentStep: currentStep.value,
          revision: revision.value,
          content: content.value,
          draftName: draftName.value || undefined,
        })
        revision.value = result.data.revision
        lastSavedAt.value = result.data.lastSavedAt
        // 保存期间若有新改动，保持 dirty 并安排下一次保存
        dirty = changeSeq !== seqAtSave
        saveState.value = dirty ? 'pending' : 'saved'
        if (dirty) {
          scheduleSave()
        }
        persistSnapshot()
      } catch (error) {
        saveState.value = 'error'
        errorMessage.value = describeError(error)
        persistSnapshot()
      }
    })
    return saveChain
  }

  function describeError(error: unknown): string {
    const response = (error as { response?: { status?: number; data?: { message?: string } } })
      ?.response
    if (response?.status === 409) {
      return '草稿已在其他页面被更新，请刷新后继续'
    }
    return response?.data?.message ?? '自动保存失败，内容已暂存到本地'
  }

  function setStep(step: number) {
    const next = Math.min(Math.max(step, 1), TOTAL_STEPS)
    if (next === currentStep.value) {
      return
    }
    currentStep.value = next
    content.value.meta = { ...content.value.meta, lastStep: next }
    touch()
  }

  /** 确认 AI 候选后刷新草稿（后端已写入并 revision+1） */
  async function refreshAfterApply() {
    if (draftId.value == null) {
      return
    }
    const result = await getDraft(draftId.value)
    const backendContent = normalizeDraftContent(result.data.content)
    revision.value = result.data.revision
    content.value = backendContent
    lastSavedAt.value = result.data.lastSavedAt
    dirty = false
    saveState.value = 'saved'
    persistSnapshot()
  }

  async function complete(payload: {
    resumeName: string
    templateId: string
    generationReason?: string
  }): Promise<DraftCompleteResult> {
    if (draftId.value == null) {
      throw new Error('草稿尚未创建')
    }
    // 生成前确保最新内容已经落库
    dirty = true
    await flush()
    const result = await completeDraft(draftId.value, payload)
    status.value = 'COMPLETED'
    writeSnapshot(null)
    localStorage.removeItem(DRAFT_ID_KEY)
    return result.data
  }

  async function abandon() {
    if (draftId.value == null) {
      return
    }
    cancelTimer()
    await abandonDraft(draftId.value)
    status.value = 'ABANDONED'
    writeSnapshot(null)
    localStorage.removeItem(DRAFT_ID_KEY)
  }

  function reset() {
    cancelTimer()
    draftId.value = null
    draftName.value = ''
    revision.value = 1
    currentStep.value = 1
    content.value = createEmptyDraftContent()
    status.value = 'DRAFT'
    saveState.value = 'idle'
    lastSavedAt.value = null
    errorMessage.value = ''
    dirty = false
    writeSnapshot(null)
    localStorage.removeItem(DRAFT_ID_KEY)
  }

  return {
    draftId,
    draftName,
    revision,
    currentStep,
    content,
    status,
    saveState,
    lastSavedAt,
    errorMessage,
    loading,
    totalSteps,
    isDraft,
    initNew,
    load,
    restoreLast,
    findRecoverable,
    touch,
    flush,
    setStep,
    refreshAfterApply,
    complete,
    abandon,
    reset,
  }
})
