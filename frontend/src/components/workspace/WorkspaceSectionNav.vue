<script setup lang="ts">
import { computed } from 'vue'
import { Refresh } from '@element-plus/icons-vue'

import type { SectionStatus } from '@/composables/useWorkspaceSectionStatus'

const props = withDefaults(
  defineProps<{
    sections: SectionStatus[]
    activeKey: string
    analyzing?: boolean
  }>(),
  { analyzing: false },
)

const emit = defineEmits<{ select: [key: string]; analyze: [] }>()

const filled = computed(() => props.sections.filter((item) => item.tone !== 'empty').length)
const active = computed(() => props.sections.find((item) => item.key === props.activeKey) ?? null)

const TONE_TEXT: Record<SectionStatus['tone'], string> = {
  empty: '未填写',
  unanalyzed: '待分析',
  good: '良好',
  warn: '待改进',
  risk: '需重点优化',
}
</script>

<template>
  <aside class="nav" aria-label="简历模块目录">
    <header class="nav__head">
      <div class="nav__title">简历模块</div>
      <div class="nav__progress">{{ filled }} / {{ sections.length }} 已填写</div>
    </header>

    <div class="nav__list">
      <button
        v-for="item in sections"
        :key="item.key"
        type="button"
        class="nav__item"
        :class="{ 'is-active': item.key === activeKey }"
        :data-testid="`section-nav-${item.key}`"
        @click="emit('select', item.key)"
      >
        <span class="nav__dot" :class="`nav__dot--${item.tone}`" />
        <span class="nav__label">{{ item.label }}</span>
        <span class="nav__meta">
          <span v-if="item.score !== null" class="nav__score">{{ item.score }}</span>
          <span v-else class="nav__state">{{ TONE_TEXT[item.tone] }}</span>
        </span>
      </button>
    </div>

    <footer class="nav__foot">
      <p v-if="active" class="nav__hint">
        当前：{{ active.label }}
        <template v-if="active.issueCount > 0"> · {{ active.issueCount }} 条待改进</template>
      </p>
      <el-button
        class="nav__analyze"
        size="small"
        :loading="analyzing"
        :disabled="!active || active.tone === 'empty'"
        @click="emit('analyze')"
      >
        <el-icon><Refresh /></el-icon>
        <span>分析当前区块</span>
      </el-button>
    </footer>
  </aside>
</template>

<style scoped>
.nav {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: var(--cf-card-bg);
  border-right: 1px solid var(--cf-border);
}

.nav__head {
  flex-shrink: 0;
  padding: var(--cf-space-3) var(--cf-space-3) var(--cf-space-2);
  border-bottom: 1px solid var(--cf-border);
}

.nav__title {
  font-size: var(--cf-font-sm);
  font-weight: 600;
  color: var(--cf-text);
}

.nav__progress {
  margin-top: 2px;
  font-size: var(--cf-font-xs);
  color: var(--cf-text-tertiary);
}

.nav__list {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: var(--cf-space-2);
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.nav__item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 8px 10px;
  border: 1px solid transparent;
  border-radius: var(--cf-radius-sm);
  background: transparent;
  font-family: inherit;
  font-size: var(--cf-font-sm);
  color: var(--cf-text-secondary);
  text-align: left;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
}

.nav__item:hover {
  background: var(--cf-bg);
  color: var(--cf-text);
}

.nav__item.is-active {
  background: var(--cf-primary-soft);
  color: var(--cf-primary);
  font-weight: 600;
}

.nav__dot {
  width: 7px;
  height: 7px;
  flex-shrink: 0;
  border-radius: 50%;
  background: var(--cf-border-strong);
}

.nav__dot--empty {
  background: var(--cf-border-strong);
}

.nav__dot--unanalyzed {
  background: var(--cf-text-tertiary);
}

.nav__dot--good {
  background: var(--cf-success);
}

.nav__dot--warn {
  background: var(--cf-warning);
}

.nav__dot--risk {
  background: var(--cf-danger);
}

.nav__label {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.nav__meta {
  flex-shrink: 0;
  font-size: 11px;
  color: var(--cf-text-tertiary);
  font-variant-numeric: tabular-nums;
}

.nav__score {
  color: var(--cf-primary);
  font-weight: 600;
}

.nav__foot {
  flex-shrink: 0;
  padding: var(--cf-space-2) var(--cf-space-3) var(--cf-space-3);
  border-top: 1px solid var(--cf-border);
}

.nav__hint {
  margin: 0 0 var(--cf-space-2);
  font-size: var(--cf-font-xs);
  color: var(--cf-text-tertiary);
  line-height: 1.5;
}

.nav__analyze {
  width: 100%;
}
</style>
