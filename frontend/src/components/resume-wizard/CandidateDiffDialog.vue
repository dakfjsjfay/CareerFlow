<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import type { EditHistoryItem } from '@/api/resumeDraft'

/**
 * AI 候选修改对比：左侧展示原内容，右侧是可二次编辑的候选内容。
 * 只有用户确认后父组件才会落库，拒绝时原文保持不变。
 */
const props = withDefaults(
  defineProps<{
    modelValue: boolean
    history: EditHistoryItem | null
    busy?: boolean
  }>(),
  { busy: false },
)

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  confirm: [finalContent: unknown]
  reject: []
}>()

const editableText = ref('')
const error = ref('')

const beforeText = computed(() => formatContent(props.history?.beforeContent ?? null))
const warnings = computed(() => props.history?.warnings ?? [])

watch(
  () => [props.modelValue, props.history] as const,
  ([visible, history]) => {
    if (visible && history) {
      editableText.value = formatContent(history.afterContent)
      error.value = ''
    }
  },
  { immediate: true },
)

function formatContent(value: unknown): string {
  if (value == null) {
    return '（暂无内容）'
  }
  if (typeof value === 'string') {
    return value
  }
  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return String(value)
  }
}

function close(): void {
  emit('update:modelValue', false)
}

function confirm(): void {
  if (!editableText.value.trim()) {
    error.value = '修改后的内容不能为空'
    return
  }
  try {
    const parsed: unknown = JSON.parse(editableText.value)
    error.value = ''
    emit('confirm', parsed)
  } catch {
    error.value = '修改后的内容格式不正确，请保持 JSON 格式后再确认'
  }
}

function reject(): void {
  emit('reject')
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="AI 修改候选"
    width="820px"
    append-to-body
    @update:model-value="close"
  >
    <p class="diff-tip">
      AI 只生成候选内容，确认后才会写入简历；如果对结果不满意，可以拒绝并保留原文。
    </p>

    <div class="diff-grid">
      <section class="diff-column">
        <h4 class="diff-title">修改前</h4>
        <pre class="diff-content" data-testid="diff-before">{{ beforeText }}</pre>
      </section>
      <section class="diff-column">
        <h4 class="diff-title">修改后（可二次编辑）</h4>
        <el-input
          v-model="editableText"
          type="textarea"
          :rows="16"
          data-testid="diff-editable"
          placeholder="可以在这里调整 AI 生成的内容"
        />
      </section>
    </div>

    <el-alert
      v-if="warnings.length > 0"
      class="diff-warnings"
      type="warning"
      :closable="false"
      title="需要注意"
    >
      <ul class="diff-warning-list">
        <li v-for="warning in warnings" :key="warning">{{ warning }}</li>
      </ul>
    </el-alert>

    <p v-if="error" class="diff-error" data-testid="diff-error">{{ error }}</p>

    <template #footer>
      <el-button data-testid="diff-reject" :disabled="busy" @click="reject">拒绝此修改</el-button>
      <el-button
        type="primary"
        data-testid="diff-confirm"
        :loading="busy"
        :disabled="busy"
        @click="confirm"
      >
        确认并应用
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.diff-tip {
  margin: 0 0 var(--cf-space-3);
  font-size: var(--cf-font-sm);
  color: var(--cf-text-secondary);
}

.diff-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--cf-space-4);
}

.diff-title {
  margin: 0 0 var(--cf-space-2);
  font-size: var(--cf-font-sm);
  font-weight: 600;
  color: var(--cf-text);
}

.diff-content {
  min-height: 336px;
  max-height: 336px;
  margin: 0;
  padding: var(--cf-space-3);
  overflow: auto;
  border: 1px solid var(--cf-border);
  border-radius: var(--cf-radius-sm);
  background: var(--cf-bg);
  font-family: 'SFMono-Regular', Consolas, 'Microsoft YaHei', monospace;
  font-size: var(--cf-font-xs);
  line-height: 1.6;
  color: var(--cf-text-secondary);
  white-space: pre-wrap;
  word-break: break-word;
}

.diff-warnings {
  margin-top: var(--cf-space-3);
}

.diff-warning-list {
  margin: 0;
  padding-left: 18px;
  font-size: var(--cf-font-sm);
  line-height: 1.7;
}

.diff-error {
  margin: var(--cf-space-2) 0 0;
  font-size: var(--cf-font-sm);
  color: var(--cf-danger);
}

@media (max-width: 767px) {
  .diff-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .diff-content {
    min-height: 180px;
    max-height: 180px;
  }
}
</style>
