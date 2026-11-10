<template>
  <el-drawer
    :model-value="modelValue"
    title="版本历史"
    size="420px"
    :append-to-body="false"
    @update:model-value="close"
  >
    <div v-if="versions.length === 0" class="empty">还没有生成过版本</div>
    <div
      v-for="version in versions"
      :key="version.versionId"
      class="version-row"
      :class="{ active: version.versionId === activeVersionId }"
      :data-testid="`version-row-${version.versionId}`"
    >
      <div class="version-main">
        <div class="version-title">
          <span class="number">v{{ version.versionNumber }}</span>
          <span class="reason">{{ reasonLabel(version.generationReason) }}</span>
        </div>
        <div class="version-meta">
          <span>{{ formatTime(version.createdAt) }}</span>
          <span>模板 {{ version.templateId?.slice(0, 8) || '—' }}</span>
        </div>
      </div>
      <div class="version-actions">
        <el-button
          link
          type="primary"
          :data-testid="`version-select-${version.versionId}`"
          @click="emit('select', version.versionId)"
        >
          预览
        </el-button>
        <el-button
          link
          :data-testid="`version-export-${version.versionId}`"
          @click="emit('export', version.versionId)"
        >
          导出 DOCX
        </el-button>
      </div>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import type { ResumeVersionSummary } from '@/api/resume'

defineProps<{
  modelValue: boolean
  versions: ResumeVersionSummary[]
  activeVersionId: number | null
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'select', versionId: number): void
  (event: 'export', versionId: number): void
}>()

const REASON_LABELS: Record<string, string> = {
  INITIAL: '首次生成',
  JOB_TARGETED: '按岗位生成',
  OPTIMIZATION: '反向优化',
  REGENERATE: '重新生成',
  MANUAL_EDIT: '手动编辑',
  TEMPLATE_SWITCH: '切换模板',
  RESTORE: '恢复旧版本',
}

function reasonLabel(reason: string): string {
  return REASON_LABELS[reason] ?? reason
}

function formatTime(value: string | null): string {
  if (!value) {
    return ''
  }
  return value.replace('T', ' ').slice(0, 16)
}

function close(value = false): void {
  emit('update:modelValue', value)
}
</script>

<style scoped>
.empty {
  color: var(--cf-text-secondary);
  font-size: 13px;
  padding: 24px 0;
  text-align: center;
}

.version-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px;
  border: 1px solid var(--cf-border);
  border-radius: 8px;
  margin-bottom: 10px;
}

.version-row.active {
  border-color: var(--cf-primary);
  background: rgb(37 99 235 / 4%);
}

.version-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.number {
  font-weight: 700;
  color: var(--cf-primary);
}

.reason {
  font-size: 13px;
  color: var(--cf-text);
}

.version-meta {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: var(--cf-text-secondary);
  margin-top: 4px;
}

.version-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}
</style>
