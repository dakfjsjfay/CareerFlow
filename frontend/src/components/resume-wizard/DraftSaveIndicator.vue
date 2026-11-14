<script setup lang="ts">
import { computed } from 'vue'

import { useResumeDraftStore } from '@/stores/resumeDraft'

/** 草稿保存状态：保存中 / 已保存（含最近保存时间） / 保存失败（可重试）。 */
const store = useResumeDraftStore()

const label = computed(() => {
  switch (store.saveState) {
    case 'saving':
      return '正在保存…'
    case 'pending':
      return '等待自动保存…'
    case 'error':
      return store.errorMessage || '自动保存失败'
    case 'saved':
      return store.lastSavedAt ? `已保存 ${formatTime(store.lastSavedAt)}` : '已保存'
    default:
      return '草稿加载中…'
  }
})

const tone = computed(() => {
  if (store.saveState === 'error') {
    return 'error'
  }
  if (store.saveState === 'saved') {
    return 'saved'
  }
  return 'pending'
})

function formatTime(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  const pad = (input: number) => String(input).padStart(2, '0')
  return `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

function retry(): void {
  void store.flush()
}
</script>

<template>
  <div class="save-indicator" :class="`save-indicator--${tone}`" data-testid="draft-save-indicator">
    <span class="save-indicator__dot" />
    <span class="save-indicator__label">{{ label }}</span>
    <el-button
      v-if="store.saveState === 'error'"
      link
      type="danger"
      size="small"
      data-testid="draft-save-retry"
      @click="retry"
    >
      重试
    </el-button>
  </div>
</template>

<style scoped>
.save-indicator {
  display: inline-flex;
  align-items: center;
  gap: var(--cf-space-2);
  min-width: 0;
  font-size: var(--cf-font-xs);
  color: var(--cf-text-secondary);
}

.save-indicator__dot {
  width: 7px;
  height: 7px;
  flex-shrink: 0;
  border-radius: 50%;
  background: var(--cf-text-tertiary);
}

.save-indicator--pending .save-indicator__dot {
  background: var(--cf-warning);
}

.save-indicator--saved .save-indicator__dot {
  background: var(--cf-success);
}

.save-indicator--error .save-indicator__dot {
  background: var(--cf-danger);
}

.save-indicator--error .save-indicator__label {
  color: var(--cf-danger);
}

.save-indicator__label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 240px;
}
</style>
