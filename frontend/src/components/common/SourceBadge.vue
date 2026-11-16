<script setup lang="ts">
import { computed } from 'vue'

/**
 * 岗位来源标记：DEMO 明示「演示」，其余展示来源类型 + 可选来源名称。
 */
const props = withDefaults(
  defineProps<{
    sourceType?: string | null
    sourceName?: string | null
  }>(),
  { sourceType: '', sourceName: '' },
)

const LABELS: Record<string, string> = {
  DEMO: '演示',
  MOCK: '演示',
  OFFICIAL: '官方',
  MANUAL: '手动录入',
  PLATFORM: '平台',
  IMPORT: '批量导入',
}

const label = computed(() => LABELS[props.sourceType ?? ''] ?? '未知来源')
const isDemo = computed(() => props.sourceType === 'DEMO' || props.sourceType === 'MOCK')
</script>

<template>
  <span class="source-badge" :class="{ 'source-badge--demo': isDemo }" :title="sourceName || label">
    <span class="source-badge__type">{{ label }}</span>
    <span v-if="sourceName" class="source-badge__name">{{ sourceName }}</span>
  </span>
</template>

<style scoped>
.source-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 220px;
  font-size: var(--cf-font-xs);
  color: var(--cf-text-secondary);
}

.source-badge__type {
  padding: 1px 6px;
  border: 1px solid var(--cf-border);
  border-radius: var(--cf-radius-sm);
  background: var(--cf-surface-muted);
  white-space: nowrap;
}

.source-badge--demo .source-badge__type {
  border-style: dashed;
  border-color: var(--cf-border-strong);
  background: transparent;
  color: var(--cf-neutral);
}

.source-badge__name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
