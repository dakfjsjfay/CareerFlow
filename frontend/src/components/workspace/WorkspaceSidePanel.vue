<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { MagicStick, Refresh, Timer } from '@element-plus/icons-vue'

import type { EditHistoryItem } from '@/api/resumeDraft'
import type { JobSummary } from '@/api/jobs'
import type {
  WorkspaceAction,
  WorkspaceActionId,
  WorkspaceMessage,
} from '@/composables/useWorkspaceSession'
import { SECTION_LABELS, type WorkspaceSectionKey } from '@/utils/resumeWizard'
import ChatComposer from './ChatComposer.vue'
import ChatMessage from './ChatMessage.vue'

/**
 * 右栏：关联岗位 + AI 对话（含快捷操作） + 操作记录。
 * 对话消息只负责展示与转发，写入动作由父级会话层统一处理。
 */
const props = withDefaults(
  defineProps<{
    messages: WorkspaceMessage[]
    actions: WorkspaceAction[]
    sending?: boolean
    applying?: boolean
    activeSection?: string
    jobId?: number | null
    jobOptions?: JobSummary[]
    jobLoading?: boolean
    editHistory?: EditHistoryItem[]
    historyLoading?: boolean
    canUndo?: boolean
  }>(),
  {
    sending: false,
    applying: false,
    activeSection: 'BASIC',
    jobId: null,
    jobOptions: () => [],
    jobLoading: false,
    editHistory: () => [],
    historyLoading: false,
    canUndo: false,
  },
)

const emit = defineEmits<{
  send: [text: string]
  upload: [payload: { name: string; text: string }]
  action: [id: WorkspaceActionId]
  'update:jobId': [id: number | null]
  apply: [message: WorkspaceMessage]
  reject: [message: WorkspaceMessage]
  compare: [message: WorkspaceMessage]
  undo: []
  refreshHistory: []
}>()

const tab = ref<'chat' | 'history'>('chat')
const input = ref('')
const listRef = ref<HTMLElement | null>(null)

const selectedJob = computed({
  get: () => props.jobId,
  set: (value: number | null) => emit('update:jobId', value ?? null),
})

const sectionLabel = computed(
  () => SECTION_LABELS[props.activeSection as WorkspaceSectionKey] ?? props.activeSection,
)

const STATUS_TEXT: Record<EditHistoryItem['status'], string> = {
  PENDING: '待确认',
  APPLIED: '已应用',
  REJECTED: '已拒绝',
}

function scrollToBottom(): void {
  nextTick(() => {
    const el = listRef.value
    if (el) {
      el.scrollTop = el.scrollHeight
    }
  })
}

watch(() => props.messages.length, scrollToBottom, { immediate: true })
watch(tab, (value) => {
  if (value === 'chat') {
    scrollToBottom()
  } else {
    emit('refreshHistory')
  }
})

function submit(): void {
  const text = input.value.trim()
  if (!text || props.sending) {
    return
  }
  emit('send', text)
  input.value = ''
}

function onUpload(payload: { name: string; text: string }): void {
  emit('upload', payload)
  input.value = `${input.value}${input.value ? '\n' : ''}以下是我上传的内容（${payload.name}）：\n${payload.text}`
}

function formatTime(value: string | null): string {
  if (!value) {
    return '-'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return date.toLocaleString('zh-CN', { hour12: false })
}

function jobLabelOf(job: JobSummary): string {
  return job.company ? `${job.position} · ${job.company}` : job.position
}
</script>

<template>
  <aside class="side">
    <header class="side__head">
      <div class="side__title">
        <el-icon><MagicStick /></el-icon>
        <span>AI 简历助手</span>
      </div>
      <el-select
        v-model="selectedJob"
        class="side__job"
        size="small"
        clearable
        filterable
        :loading="jobLoading"
        placeholder="关联岗位（可选）"
        data-testid="job-select"
      >
        <el-option
          v-for="job in jobOptions"
          :key="job.id"
          :label="jobLabelOf(job)"
          :value="job.id"
        />
      </el-select>
    </header>

    <el-tabs v-model="tab" class="side__tabs">
      <el-tab-pane label="AI 对话" name="chat">
        <div class="side__chat">
          <div class="side__actions">
            <span class="side__context">当前区块：{{ sectionLabel }}</span>
            <div class="side__chips">
              <button
                v-for="action in actions"
                :key="action.id"
                type="button"
                class="side__chip"
                :data-testid="`action-${action.id}`"
                :disabled="sending"
                @click="emit('action', action.id)"
              >
                {{ action.label }}
              </button>
            </div>
          </div>

          <div ref="listRef" class="side__messages">
            <ChatMessage
              v-for="message in messages"
              :key="message.id"
              :message="message"
              @apply="emit('apply', $event)"
              @reject="emit('reject', $event)"
              @compare="emit('compare', $event)"
            />
            <div v-if="sending" class="side__typing" aria-label="AI 正在生成">
              <span /><span /><span />
            </div>
          </div>

          <ChatComposer
            v-model="input"
            :sending="sending"
            placeholder="描述修改要求，或直接点上方快捷操作"
            @send="submit"
            @upload="onUpload"
          />
        </div>
      </el-tab-pane>

      <el-tab-pane label="操作记录" name="history">
        <div class="side__history">
          <div v-if="canUndo" class="side__undo">
            <span>本次会话有可撤销的修改</span>
            <el-button size="small" text type="primary" @click="emit('undo')">撤销最近一次</el-button>
          </div>

          <div v-if="historyLoading" class="side__history-state">加载中…</div>
          <div v-else-if="editHistory.length === 0" class="side__history-state">
            还没有修改记录。接受一次 AI 修改后会在这里留痕。
          </div>

          <ul v-else class="side__history-list">
            <li v-for="item in editHistory" :key="item.id" class="history-item">
              <div class="history-item__head">
                <span class="history-item__section">
                  {{ SECTION_LABELS[item.sectionKey as WorkspaceSectionKey] ?? item.sectionKey }}
                </span>
                <el-tag
                  size="small"
                  effect="plain"
                  :type="item.status === 'APPLIED' ? 'success' : item.status === 'PENDING' ? 'warning' : 'info'"
                >
                  {{ STATUS_TEXT[item.status] }}
                </el-tag>
              </div>
              <div class="history-item__time">
                <el-icon><Timer /></el-icon>
                <span>{{ formatTime(item.appliedAt ?? item.createdAt) }}</span>
              </div>
              <ul v-if="item.warnings.length > 0" class="history-item__warnings">
                <li v-for="warning in item.warnings" :key="warning">{{ warning }}</li>
              </ul>
            </li>
          </ul>

          <el-button class="side__refresh" size="small" text :loading="historyLoading" @click="emit('refreshHistory')">
            <el-icon><Refresh /></el-icon>
            <span>刷新记录</span>
          </el-button>
        </div>
      </el-tab-pane>
    </el-tabs>
  </aside>
</template>

<style scoped>
.side {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: var(--cf-card-bg);
  border-left: 1px solid var(--cf-border);
}

.side__head {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: var(--cf-space-2);
  padding: var(--cf-space-3);
  border-bottom: 1px solid var(--cf-border);
}

.side__title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: var(--cf-font-sm);
  font-weight: 600;
  color: var(--cf-text);
}

.side__job {
  width: 100%;
}

.side__tabs {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding: 0 var(--cf-space-3);
}

.side__tabs :deep(.el-tabs__header) {
  margin-bottom: var(--cf-space-2);
  flex-shrink: 0;
}

.side__tabs :deep(.el-tabs__content) {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.side__tabs :deep(.el-tab-pane) {
  height: 100%;
  overflow: hidden;
}

.side__chat {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.side__actions {
  flex-shrink: 0;
  padding-bottom: var(--cf-space-2);
  border-bottom: 1px solid var(--cf-border);
}

.side__context {
  display: block;
  margin-bottom: 6px;
  font-size: var(--cf-font-xs);
  color: var(--cf-text-tertiary);
}

.side__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.side__chip {
  padding: 3px 9px;
  border: 1px solid var(--cf-border);
  border-radius: 999px;
  background: var(--cf-card-bg);
  font-family: inherit;
  font-size: var(--cf-font-xs);
  color: var(--cf-text-secondary);
  cursor: pointer;
  transition: border-color 0.15s ease, color 0.15s ease;
}

.side__chip:hover:not(:disabled) {
  border-color: var(--cf-primary-light);
  color: var(--cf-primary);
}

.side__chip:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.side__messages {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: var(--cf-space-3) 0;
  display: flex;
  flex-direction: column;
  gap: var(--cf-space-2);
}

.side__typing {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 8px 12px;
  width: fit-content;
  border-radius: var(--cf-radius-md);
  background: var(--cf-bg);
}

.side__typing span {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--cf-text-tertiary);
  animation: side-blink 1s infinite ease-in-out;
}

.side__typing span:nth-child(2) {
  animation-delay: 0.15s;
}

.side__typing span:nth-child(3) {
  animation-delay: 0.3s;
}

@keyframes side-blink {
  0%,
  80%,
  100% {
    opacity: 0.25;
  }
  40% {
    opacity: 1;
  }
}

.side__history {
  height: 100%;
  overflow: auto;
  padding-bottom: var(--cf-space-3);
  display: flex;
  flex-direction: column;
  gap: var(--cf-space-2);
}

.side__undo {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--cf-space-2);
  padding: var(--cf-space-2);
  border-radius: var(--cf-radius-sm);
  background: var(--cf-primary-soft);
  font-size: var(--cf-font-xs);
  color: var(--cf-primary);
}

.side__history-state {
  padding: var(--cf-space-4) 0;
  font-size: var(--cf-font-xs);
  color: var(--cf-text-tertiary);
  text-align: center;
  line-height: 1.7;
}

.side__history-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.history-item {
  padding: var(--cf-space-2) var(--cf-space-3);
  border: 1px solid var(--cf-border);
  border-radius: var(--cf-radius-sm);
}

.history-item__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--cf-space-2);
}

.history-item__section {
  font-size: var(--cf-font-xs);
  font-weight: 600;
  color: var(--cf-text);
}

.history-item__time {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 4px;
  font-size: 11px;
  color: var(--cf-text-tertiary);
}

.history-item__warnings {
  margin: 4px 0 0;
  padding-left: 16px;
  font-size: 11px;
  line-height: 1.6;
  color: var(--cf-warning);
}

.side__refresh {
  align-self: center;
}
</style>
