<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { MagicStick } from '@element-plus/icons-vue'

import ChatComposer from './ChatComposer.vue'
import ChatMessage from './ChatMessage.vue'
import type { WorkspaceMessage } from '@/composables/useWorkspaceSession'
import { SECTION_LABELS, type WorkspaceSectionKey } from '@/utils/resumeWizard'

const props = defineProps<{
  messages: WorkspaceMessage[]
  sending: boolean
  mode: 'CREATE' | 'EDIT'
  activeSection: WorkspaceSectionKey
  placeholder: string
  suggestions: string[]
}>()

const emit = defineEmits<{
  send: [text: string]
  compare: [message: WorkspaceMessage]
  apply: [message: WorkspaceMessage]
  reject: [message: WorkspaceMessage]
  upload: [payload: { name: string; text: string }]
}>()

const input = ref('')
const listRef = ref<HTMLElement | null>(null)

const sectionLabel = computed(() => SECTION_LABELS[props.activeSection] ?? '简历')

function scrollToBottom(): void {
  const list = listRef.value
  if (list) {
    list.scrollTop = list.scrollHeight
  }
}

watch(
  () => props.messages.length,
  () => {
    void nextTick(scrollToBottom)
  },
)

watch(
  () => props.sending,
  () => {
    void nextTick(scrollToBottom)
  },
)

function submit(): void {
  const value = input.value.trim()
  if (!value) {
    return
  }
  emit('send', value)
  input.value = ''
}

function onUpload(payload: { name: string; text: string }): void {
  const excerpt = payload.text.trim().slice(0, 2000)
  if (!excerpt) {
    emit('upload', payload)
    return
  }
  input.value = `${input.value}\n【附件 ${payload.name}】\n${excerpt}`.trim()
  emit('upload', payload)
}
</script>

<template>
  <section class="chat" aria-label="AI 对话区">
    <header class="chat__head">
      <span class="chat__avatar">
        <el-icon><MagicStick /></el-icon>
      </span>
      <div class="chat__intro">
        <span class="chat__name">CareerFlow 简历助手</span>
        <span class="chat__desc">
          {{ mode === 'CREATE' ? '按八步引导补全信息，不会编造你的经历' : '描述修改要求，AI 先出候选，确认后才写入' }}
        </span>
      </div>
      <el-tag size="small" effect="plain" class="chat__context">{{ sectionLabel }}</el-tag>
    </header>

    <div ref="listRef" class="chat__list" role="log" aria-live="polite">
      <ChatMessage
        v-for="message in messages"
        :key="message.id"
        :message="message"
        @compare="emit('compare', $event)"
        @apply="emit('apply', $event)"
        @reject="emit('reject', $event)"
      />

      <div v-if="sending" class="typing">
        <span class="typing__dot" />
        <span class="typing__dot" />
        <span class="typing__dot" />
        <span class="typing__label">AI 正在处理…</span>
      </div>
    </div>

    <slot name="form" />

    <ChatComposer
      v-model="input"
      :sending="sending"
      :placeholder="placeholder"
      :suggestions="suggestions"
      @send="submit"
      @upload="onUpload"
    />
  </section>
</template>

<style scoped>
.chat {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: var(--cf-card-bg);
  border-right: 1px solid var(--cf-border);
}

.chat__head {
  display: flex;
  align-items: center;
  gap: var(--cf-space-2);
  flex-shrink: 0;
  padding: var(--cf-space-3) var(--cf-space-4);
  border-bottom: 1px solid var(--cf-border);
}

.chat__avatar {
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  background: linear-gradient(135deg, var(--cf-primary), var(--cf-primary-light));
  color: #fff;
}

.chat__intro {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
}

.chat__name {
  font-size: var(--cf-font-sm);
  font-weight: 600;
  color: var(--cf-text);
}

.chat__desc {
  font-size: var(--cf-font-xs);
  color: var(--cf-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chat__context {
  flex-shrink: 0;
}

.chat__list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: var(--cf-space-3);
  padding: var(--cf-space-4);
  background: var(--cf-bg);
}

.typing {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: var(--cf-space-2) var(--cf-space-3);
  border-radius: var(--cf-radius-md);
  background: var(--cf-card-bg);
  border: 1px solid var(--cf-border);
  align-self: flex-start;
}

.typing__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--cf-text-tertiary);
  animation: typing-bounce 1.1s infinite ease-in-out;
}

.typing__dot:nth-child(2) {
  animation-delay: 0.15s;
}

.typing__dot:nth-child(3) {
  animation-delay: 0.3s;
}

.typing__label {
  margin-left: 4px;
  font-size: var(--cf-font-xs);
  color: var(--cf-text-tertiary);
}

@keyframes typing-bounce {
  0%,
  80%,
  100% {
    transform: translateY(0);
    opacity: 0.5;
  }
  40% {
    transform: translateY(-4px);
    opacity: 1;
  }
}
</style>
