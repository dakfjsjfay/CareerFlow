import { computed, ref } from 'vue'

import {
  confirmEvidence,
  listEvidence,
  type ConfirmEvidenceResult,
  type EvidenceItem,
} from '@/api/profile'
import { groupEvidence, type EvidenceGroup } from '@/utils/evidenceDisplay'

/** 聊天页证据确认流程：拉取 PENDING、自动打开、确认/拒绝后刷新。 */
export function useEvidenceConfirm() {
  const visible = ref(false)
  const submitting = ref(false)
  const items = ref<EvidenceItem[]>([])

  const groups = computed<EvidenceGroup[]>(() => groupEvidence(items.value))
  const pendingCount = computed(() => items.value.length)

  async function refresh(): Promise<void> {
    const res = await listEvidence('PENDING')
    items.value = res.data
  }

  /** 手动打开待确认列表。 */
  async function open(): Promise<void> {
    await refresh()
    visible.value = true
  }

  /**
   * 发送消息后调用：只有本次确实抽取到内容才拉取待确认列表，
   * 有 PENDING 证据时自动打开弹窗；空抽取不打扰用户。
   */
  async function afterSend(
    extractedInfo: Record<string, unknown> | null | undefined,
  ): Promise<void> {
    if (!hasExtractedInfo(extractedInfo)) {
      return
    }
    await refresh()
    if (items.value.length > 0) {
      visible.value = true
    }
  }

  async function confirmSelected(ids: number[]): Promise<ConfirmEvidenceResult> {
    return submit(ids, true)
  }

  async function rejectSelected(ids: number[]): Promise<ConfirmEvidenceResult> {
    return submit(ids, false)
  }

  async function submit(ids: number[], confirmed: boolean): Promise<ConfirmEvidenceResult> {
    submitting.value = true
    try {
      const res = await confirmEvidence(ids, confirmed)
      await refresh()
      if (items.value.length === 0) {
        visible.value = false
      }
      return res.data
    } finally {
      submitting.value = false
    }
  }

  return {
    visible,
    submitting,
    groups,
    pendingCount,
    refresh,
    open,
    afterSend,
    confirmSelected,
    rejectSelected,
  }
}

function hasExtractedInfo(value: Record<string, unknown> | null | undefined): boolean {
  return !!value && Object.values(value).some(hasContent)
}

function hasContent(value: unknown): boolean {
  if (value === null || value === undefined) {
    return false
  }
  if (Array.isArray(value)) {
    return value.some(hasContent)
  }
  if (typeof value === 'object') {
    return Object.values(value as Record<string, unknown>).some(hasContent)
  }
  if (typeof value === 'string') {
    return value.trim().length > 0
  }
  return true
}
