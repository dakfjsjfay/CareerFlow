<script setup lang="ts">
import { computed } from 'vue'
import { MagicStick, User, Warning } from '@element-plus/icons-vue'

import type { WorkspaceMessage } from '@/composables/useWorkspaceSession'
import { SECTION_LABELS } from '@/utils/resumeWizard'

const props = defineProps<{ message: WorkspaceMessage }>()

const emit = defineEmits<{
  compare: [message: WorkspaceMessage]
  apply: [message: WorkspaceMessage]
  reject: [message: WorkspaceMessage]
}>()

const isUser = computed(() => props.message.role === 'user')
const isSystem = computed(() => props.message.role === 'system')
const sectionLabel = computed(() =>
  props.message.sectionKey ? SECTION_LABELS[props.message.sectionKey] ?? props.message.sectionKey : '',
)
const warnings = computed(() => props.message.candidate?.warnings ?? [])
const analysis = computed(() => props.message.analysis ?? null)
const candidateStatus = computed(() => {
  if (props.message.applied) {
    return '已应用'
  }
  if (props.message.rejected) {
    return '已拒绝'
  }
  return '待确认'
})
</script>

<template>
  <div v-if="isSystem" class="msg-system">
    <span>{{ message.content }}</span>
  </div>

  <div v-else class="msg" :class="isUser ? 'msg--user' : 'msg--ai'">
    <span v-if="!isUser" class="msg__avatar">
      <el-icon><MagicStick /></el-icon>
    </span>
    <div class="msg__body">
      <div class="msg__bubble" :class="{ 'msg__bubble--error': message.kind === 'error' }">
        <p class="msg__text">{{ message.content }}</p>

        <div v-if="message.kind === 'candidate' && message.candidate" class="candidate">
          <div class="candidate__head">
            <span class="candidate__title">{{ sectionLabel }} 修改候选</span>
            <el-tag size="small" :type="message.applied ? 'success' : message.rejected ? 'info' : 'warning'">
              {{ candidateStatus }}
            </el-tag>
          </div>
          <ul v-if="warnings.length > 0" class="candidate__warnings">
            <li v-for="warning in warnings" :key="warning">
              <el-icon><Warning /></el-icon>
              <span>{{ warning }}</span>
            </li>
          </ul>
          <div class="candidate__actions">
            <el-button size="small" @click="emit('compare', message)">查看对比</el-button>
            <el-button
              size="small"
              type="primary"
              :disabled="message.applied || message.rejected"
              @click="emit('apply', message)"
            >
              应用修改
            </el-button>
            <el-button
              size="small"
              :disabled="message.applied || message.rejected"
              @click="emit('reject', message)"
            >
              拒绝
            </el-button>
          </div>
        </div>

        <div v-if="message.kind === 'analysis' && analysis" class="analysis">
          <div class="analysis__score">
            <span class="analysis__value">{{ analysis.score }}</span>
            <span class="analysis__unit">分</span>
          </div>
          <div class="analysis__lists">
            <div v-if="analysis.issues.length > 0" class="analysis__block">
              <h4>待改进</h4>
              <ul>
                <li v-for="issue in analysis.issues" :key="issue">{{ issue }}</li>
              </ul>
            </div>
            <div v-if="analysis.suggestions.length > 0" class="analysis__block">
              <h4>优化建议</h4>
              <ul>
                <li v-for="suggestion in analysis.suggestions" :key="suggestion">{{ suggestion }}</li>
              </ul>
            </div>
            <div v-if="analysis.missingFields.length > 0" class="analysis__block">
              <h4>缺失信息</h4>
              <ul>
                <li v-for="field in analysis.missingFields" :key="field">{{ field }}</li>
              </ul>
            </div>
          </div>
        </div>
      </div>
      <span class="msg__meta">
        <el-icon v-if="isUser"><User /></el-icon>
        {{ isUser ? '你' : 'AI 助手' }} · {{ message.time }}
      </span>
    </div>
  </div>
</template>

<style scoped>
.msg-system {
  align-self: center;
  max-width: 92%;
  padding: var(--cf-space-1) var(--cf-space-3);
  border-radius: 999px;
  background: var(--cf-bg);
  color: var(--cf-text-tertiary);
  font-size: var(--cf-font-xs);
  text-align: center;
}

.msg {
  display: flex;
  gap: var(--cf-space-2);
  align-items: flex-start;
}

.msg--user {
  flex-direction: row-reverse;
}

.msg__avatar {
  width: 28px;
  height: 28px;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--cf-primary-soft);
  color: var(--cf-primary);
}

.msg__body {
  display: flex;
  flex-direction: column;
  gap: var(--cf-space-1);
  min-width: 0;
  max-width: 88%;
}

.msg--user .msg__body {
  align-items: flex-end;
}

.msg__bubble {
  padding: var(--cf-space-3);
  border-radius: var(--cf-radius-md);
  background: var(--cf-card-bg);
  border: 1px solid var(--cf-border);
  box-shadow: var(--cf-shadow-sm);
}

.msg--user .msg__bubble {
  background: var(--cf-primary);
  border-color: var(--cf-primary);
  color: #fff;
}

.msg__bubble--error {
  border-color: var(--cf-danger);
  background: var(--cf-danger-soft);
  color: var(--cf-danger);
}

.msg__text {
  margin: 0;
  font-size: var(--cf-font-sm);
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.msg__meta {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: var(--cf-font-xs);
  color: var(--cf-text-tertiary);
}

.candidate {
  margin-top: var(--cf-space-3);
  padding-top: var(--cf-space-3);
  border-top: 1px dashed var(--cf-border);
}

.candidate__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--cf-space-2);
}

.candidate__title {
  font-size: var(--cf-font-sm);
  font-weight: 600;
  color: var(--cf-text);
}

.candidate__warnings {
  margin: var(--cf-space-2) 0 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.candidate__warnings li {
  display: flex;
  gap: 6px;
  align-items: flex-start;
  font-size: var(--cf-font-xs);
  color: var(--cf-warning);
  line-height: 1.6;
}

.candidate__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--cf-space-2);
  margin-top: var(--cf-space-3);
}

.analysis {
  margin-top: var(--cf-space-3);
  padding-top: var(--cf-space-3);
  border-top: 1px dashed var(--cf-border);
  display: flex;
  gap: var(--cf-space-4);
}

.analysis__score {
  display: flex;
  align-items: baseline;
  gap: 2px;
  color: var(--cf-primary);
}

.analysis__value {
  font-size: var(--cf-font-2xl);
  font-weight: 700;
}

.analysis__unit {
  font-size: var(--cf-font-xs);
}

.analysis__lists {
  display: flex;
  flex-direction: column;
  gap: var(--cf-space-2);
  min-width: 0;
}

.analysis__block h4 {
  margin: 0 0 2px;
  font-size: var(--cf-font-xs);
  color: var(--cf-text-secondary);
}

.analysis__block ul {
  margin: 0;
  padding-left: 16px;
  font-size: var(--cf-font-xs);
  line-height: 1.7;
  color: var(--cf-text-secondary);
}
</style>
