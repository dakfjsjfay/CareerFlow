<script setup lang="ts">
import { computed } from 'vue'

/**
 * 统一状态标签：岗位可信度 / 招聘生命周期共用一套视觉。
 * 前端只传后端返回的 displayStatus，避免各页面自行推断状态语义。
 */
type StatusTone = 'neutral' | 'success' | 'warning' | 'danger' | 'info' | 'primary'

const props = withDefaults(
  defineProps<{
    status?: string | null
    label?: string
    tone?: StatusTone
    size?: 'small' | 'default'
    /** 演示数据等需要虚线边框的中性状态 */
    dashed?: boolean
  }>(),
  { status: '', label: '', tone: undefined, size: 'small', dashed: false },
)

const PRESETS: Record<string, { label: string; tone: StatusTone }> = {
  DEMO: { label: '演示数据', tone: 'neutral' },
  UNVERIFIED: { label: '待核实', tone: 'warning' },
  VERIFIED: { label: '已核实', tone: 'success' },
  EXPIRED: { label: '已过期', tone: 'neutral' },
  OPEN: { label: '招聘中', tone: 'success' },
  SOON: { label: '即将开始', tone: 'warning' },
  CLOSED: { label: '已关闭', tone: 'neutral' },
}

const preset = computed(() => PRESETS[props.status ?? ''])
const text = computed(() => props.label || preset.value?.label || props.status || '未知')
const resolvedTone = computed<StatusTone>(() => props.tone ?? preset.value?.tone ?? 'neutral')
const isDashed = computed(() => props.dashed || props.status === 'DEMO')
</script>

<template>
  <span
    class="status-tag"
    :class="[
      `status-tag--${resolvedTone}`,
      `status-tag--${size}`,
      { 'status-tag--dashed': isDashed },
    ]"
    :data-status="status || undefined"
  >
    <span class="status-tag__dot" />
    {{ text }}
  </span>
</template>

<style scoped>
.status-tag {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 1px 7px;
  border: 1px solid transparent;
  border-radius: var(--cf-radius-sm);
  font-size: var(--cf-font-xs);
  line-height: 18px;
  font-weight: 500;
  white-space: nowrap;
}

.status-tag--small {
  font-size: var(--cf-font-xs);
}

.status-tag--default {
  padding: 3px 9px;
  font-size: var(--cf-font-sm);
}

.status-tag__dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
  opacity: 0.85;
}

.status-tag--neutral {
  background: var(--cf-neutral-soft);
  color: var(--cf-neutral);
}

.status-tag--success {
  background: var(--cf-success-soft);
  color: var(--cf-success);
}

.status-tag--warning {
  background: var(--cf-warning-soft);
  color: var(--cf-warning);
}

.status-tag--danger {
  background: var(--cf-danger-soft);
  color: var(--cf-danger);
}

.status-tag--info {
  background: var(--cf-info-soft);
  color: var(--cf-info);
}

.status-tag--primary {
  background: var(--cf-primary-soft);
  color: var(--cf-primary);
}

.status-tag--dashed {
  background: transparent;
  border-style: dashed;
  border-color: var(--cf-border-strong);
}
</style>
