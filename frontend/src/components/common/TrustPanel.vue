<script setup lang="ts">
import { computed } from 'vue'
import StatusTag from './StatusTag.vue'
import SourceBadge from './SourceBadge.vue'

/**
 * 岗位可信度面板：来源、发布时间、核验时间、有效期、原招聘信息链接。
 * 演示岗位明确提示，避免被误认为真实招聘信息。
 */
const props = withDefaults(
  defineProps<{
    displayStatus?: string | null
    sourceType?: string | null
    sourceName?: string | null
    publishedAt?: string | null
    verifiedAt?: string | null
    validUntil?: string | null
    externalUrl?: string | null
  }>(),
  {
    displayStatus: '',
    sourceType: '',
    sourceName: '',
    publishedAt: null,
    verifiedAt: null,
    validUntil: null,
    externalUrl: null,
  },
)

const isDemo = computed(
  () => props.displayStatus === 'DEMO' || props.sourceType === 'DEMO' || props.sourceType === 'MOCK',
)

function formatDate(value: string | null | undefined): string {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value.slice(0, 10)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}
</script>

<template>
  <section class="trust-panel" :class="{ 'trust-panel--demo': isDemo }" data-testid="trust-panel">
    <header class="trust-panel__head">
      <span class="cf-eyebrow">数据来源与可信度</span>
      <StatusTag :status="displayStatus" />
    </header>

    <p v-if="isDemo" class="trust-panel__demo" data-testid="trust-demo-notice">
      演示数据，仅用于功能体验，不代表真实在招职位。
    </p>

    <dl class="trust-panel__grid">
      <div class="trust-panel__row">
        <dt>来源</dt>
        <dd><SourceBadge :source-type="sourceType" :source-name="sourceName" /></dd>
      </div>
      <div v-if="publishedAt" class="trust-panel__row">
        <dt>发布时间</dt>
        <dd>{{ formatDate(publishedAt) }}</dd>
      </div>
      <div v-if="verifiedAt" class="trust-panel__row">
        <dt>最近核验</dt>
        <dd>{{ formatDate(verifiedAt) }}</dd>
      </div>
      <div v-if="validUntil" class="trust-panel__row">
        <dt>有效期至</dt>
        <dd>{{ formatDate(validUntil) }}</dd>
      </div>
      <div class="trust-panel__row">
        <dt>原始招聘页</dt>
        <dd>
          <a
            v-if="externalUrl"
            :href="externalUrl"
            target="_blank"
            rel="noopener noreferrer"
            class="trust-panel__link"
            data-testid="trust-external-link"
          >
            查看原招聘信息
          </a>
          <span v-else class="cf-note">未提供</span>
        </dd>
      </div>
    </dl>
  </section>
</template>

<style scoped>
.trust-panel {
  border: 1px solid var(--cf-border);
  border-radius: var(--cf-radius-md);
  padding: var(--cf-space-4);
  background: var(--cf-surface-muted);
}

.trust-panel--demo {
  background: transparent;
  border-style: dashed;
  border-color: var(--cf-border-strong);
}

.trust-panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--cf-space-2);
}

.trust-panel__demo {
  margin-top: var(--cf-space-2);
  font-size: var(--cf-font-xs);
  color: var(--cf-warning);
}

.trust-panel__grid {
  margin-top: var(--cf-space-3);
  display: grid;
  gap: var(--cf-space-2);
}

.trust-panel__row {
  display: flex;
  align-items: baseline;
  gap: var(--cf-space-2);
  font-size: var(--cf-font-sm);
}

.trust-panel__row dt {
  flex-shrink: 0;
  width: 76px;
  color: var(--cf-text-tertiary);
  font-size: var(--cf-font-xs);
}

.trust-panel__row dd {
  color: var(--cf-text-secondary);
  min-width: 0;
}

.trust-panel__link {
  color: var(--cf-primary);
  font-size: var(--cf-font-sm);
}

.trust-panel__link:hover {
  text-decoration: underline;
}
</style>
