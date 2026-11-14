<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Paperclip, Promotion } from '@element-plus/icons-vue'

const props = withDefaults(
  defineProps<{
    modelValue: string
    sending?: boolean
    disabled?: boolean
    placeholder?: string
    suggestions?: string[]
  }>(),
  {
    sending: false,
    disabled: false,
    placeholder: '',
    suggestions: () => [],
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
  send: []
  upload: [payload: { name: string; text: string }]
}>()

const TEXT_EXTENSIONS = ['txt', 'md', 'json', 'csv']
const inputRef = ref<HTMLTextAreaElement | null>(null)
const fileRef = ref<HTMLInputElement | null>(null)

function update(value: string): void {
  emit('update:modelValue', value)
}

function submit(): void {
  if (props.sending || props.disabled) {
    return
  }
  if (!props.modelValue.trim()) {
    ElMessage.warning('请先输入内容')
    return
  }
  emit('send')
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key !== 'Enter' || event.shiftKey || event.isComposing) {
    return
  }
  event.preventDefault()
  submit()
}

function triggerUpload(): void {
  fileRef.value?.click()
}

function onFileChange(event: Event): void {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) {
    return
  }
  const extension = file.name.split('.').pop()?.toLowerCase() ?? ''
  if (!TEXT_EXTENSIONS.includes(extension)) {
    ElMessage.info('工作区目前只支持 txt / md / json / csv 文本附件')
    return
  }
  const reader = new FileReader()
  reader.onload = () => {
    emit('upload', { name: file.name, text: String(reader.result ?? '') })
  }
  reader.onerror = () => {
    ElMessage.error('文件读取失败，请重试')
  }
  reader.readAsText(file)
}

function pick(value: string): void {
  update(value)
  inputRef.value?.focus()
}
</script>

<template>
  <div class="composer">
    <div v-if="suggestions.length > 0" class="composer__chips">
      <button
        v-for="item in suggestions"
        :key="item"
        type="button"
        class="chip"
        @click="pick(item)"
      >
        {{ item }}
      </button>
    </div>

    <div class="composer__box">
      <textarea
        ref="inputRef"
        class="composer__input"
        :value="modelValue"
        :placeholder="placeholder"
        :disabled="disabled || sending"
        rows="3"
        @input="update(($event.target as HTMLTextAreaElement).value)"
        @keydown="onKeydown"
      />
      <div class="composer__footer">
        <div class="composer__tools">
          <el-tooltip content="上传文本附件（txt / md / json / csv）" placement="top">
            <el-button link :disabled="disabled || sending" @click="triggerUpload">
              <el-icon><Paperclip /></el-icon>
            </el-button>
          </el-tooltip>
          <span class="composer__hint">Enter 发送 · Shift + Enter 换行</span>
        </div>
        <el-button
          type="primary"
          size="small"
          :loading="sending"
          :disabled="disabled"
          @click="submit"
        >
          <el-icon><Promotion /></el-icon>
          <span>发送</span>
        </el-button>
      </div>
    </div>
    <input ref="fileRef" type="file" class="composer__file" @change="onFileChange" />
  </div>
</template>

<style scoped>
.composer {
  display: flex;
  flex-direction: column;
  gap: var(--cf-space-2);
  padding: var(--cf-space-3);
  border-top: 1px solid var(--cf-border);
  background: var(--cf-card-bg);
}

.composer__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.chip {
  padding: 3px 10px;
  border: 1px solid var(--cf-border);
  border-radius: 999px;
  background: var(--cf-bg);
  color: var(--cf-text-secondary);
  font-family: inherit;
  font-size: var(--cf-font-xs);
  cursor: pointer;
  transition: border-color 0.15s ease, color 0.15s ease;
}

.chip:hover {
  border-color: var(--cf-primary);
  color: var(--cf-primary);
}

.composer__box {
  border: 1px solid var(--cf-border);
  border-radius: var(--cf-radius-md);
  background: var(--cf-card-bg);
  transition: border-color 0.15s ease;
}

.composer__box:focus-within {
  border-color: var(--cf-primary);
}

.composer__input {
  width: 100%;
  border: none;
  outline: none;
  resize: none;
  padding: var(--cf-space-3);
  font-family: inherit;
  font-size: var(--cf-font-sm);
  line-height: 1.6;
  color: var(--cf-text);
  background: transparent;
}

.composer__input::placeholder {
  color: var(--cf-text-tertiary);
}

.composer__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--cf-space-2);
  padding: 0 var(--cf-space-2) var(--cf-space-2);
}

.composer__tools {
  display: flex;
  align-items: center;
  gap: var(--cf-space-2);
  min-width: 0;
}

.composer__hint {
  font-size: var(--cf-font-xs);
  color: var(--cf-text-tertiary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.composer__file {
  display: none;
}

@media (max-width: 1023px) {
  .composer__hint {
    display: none;
  }
}
</style>
