<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ArrowDown, ArrowUp } from '@element-plus/icons-vue'

import type { EditHistoryItem } from '@/api/resumeDraft'
import { SECTION_LABELS, type WorkspaceSectionKey } from '@/utils/resumeWizard'

/**
 * 中间栏内联对比：展示当前待确认候选的修改前 / 修改后。
 * 只有用户点击「接受修改」后父组件才会写入草稿。
 */
const props = withDefaults(
  defineProps<{
    candidate: EditHistoryItem | null
    busy?: boolean
  }>(),
  { busy: false },
)

const emit = defineEmits<{
  accept: [finalContent: unknown]
  reject: []
  regenerate: []
  edit: []
}>()

const expanded = ref(true)

watch(
  () => props.candidate?.id,
  (id) => {
    if (id) {
      expanded.value = true
    }
  },
)

const sectionLabel = computed(() => {
  const key = props.candidate?.sectionKey as WorkspaceSectionKey | undefined
  if (!key) {
    return '当前区块'
  }
  return SECTION_LABELS[key] ?? key
})

const beforeText = computed(() => formatContent(props.candidate?.beforeContent ?? null))
const afterText = computed(() => formatContent(props.candidate?.afterContent ?? null))
const warnings = computed(() => props.candidate?.warnings ?? [])

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
</script>

<template>
  <section class="diff" :class="{ 'diff--empty': !candidate, 'diff--collapsed': candidate && !expanded }">
    <header class="diff__head">
      <div class="diff__title">
        <span v-if="candidate">「{{ sectionLabel }}」修改候选</span>
        <span v-else>选中区块后，使用右侧操作生成修改候选</span>
      </div>
      <div class="diff__meta">
        <el-tag v-if="candidate" size="small" type="warning" effect="plain">待确认</el-tag>
        <span v-if="candidate && warnings.length > 0" class="diff__warn">
          {{ warnings.length }} 条提醒
        </span>
        <button
          v-if="candidate"
          type="button"
          class="diff__toggle"
          :aria-label="expanded ? '收起对比' : '展开对比'"
          @click="expanded = !expanded"
        >
          <el-icon><ArrowDown v-if="expanded" /><ArrowUp v-else /></el-icon>
        </button>
      </div>
    </header>

    <template v-if="candidate && expanded">
      <div class="diff__grid">
        <section class="diff__column">
          <h4 class="diff__column-title">修改前</h4>
          <pre class="diff__content">{{ beforeText }}</pre>
        </section>
        <section class="diff__column">
          <h4 class="diff__column-title">修改后</h4>
          <pre class="diff__content diff__content--after">{{ afterText }}</pre>
        </section>
      </div>

      <ul v-if="warnings.length > 0" class="diff__warnings">
        <li v-for="warning in warnings" :key="warning">{{ warning }}</li>
      </ul>

      <footer class="diff__foot">
        <el-button
          type="primary"
          size="small"
          :loading="busy"
          :disabled="busy"
          data-testid="inline-diff-accept"
          @click="emit('accept', candidate.afterContent)"
        >
          接受修改
        </el-button>
        <el-button size="small" :disabled="busy" data-testid="inline-diff-reject" @click="emit('reject')">
          拒绝
        </el-button>
        <el-button size="small" :disabled="busy" @click="emit('regenerate')">重新生成</el-button>
        <el-button size="small" text :disabled="busy" @click="emit('edit')">二次编辑</el-button>
      </footer>
    </template>
  </section>
</template>

<style scoped>
.diff {
  flex-shrink: 0;
  border-top: 1px solid var(--cf-border);
  background: var(--cf-card-bg);
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.diff--empty {
  padding: var(--cf-space-2) var(--cf-space-3);
}

.diff__head {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--cf-space-3);
  padding: var(--cf-space-2) var(--cf-space-3);
}

.diff--empty .diff__head {
  padding: 0;
}

.diff__title {
  font-size: var(--cf-font-sm);
  font-weight: 600;
  color: var(--cf-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.diff--empty .diff__title {
  font-weight: 400;
  color: var(--cf-text-tertiary);
}

.diff__meta {
  display: flex;
  align-items: center;
  gap: var(--cf-space-2);
  flex-shrink: 0;
}

.diff__warn {
  font-size: var(--cf-font-xs);
  color: var(--cf-warning);
}

.diff__toggle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border: none;
  border-radius: var(--cf-radius-sm);
  background: transparent;
  color: var(--cf-text-tertiary);
  cursor: pointer;
}

.diff__toggle:hover {
  background: var(--cf-bg);
  color: var(--cf-text);
}

.diff__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--cf-space-3);
  padding: 0 var(--cf-space-3) var(--cf-space-2);
  min-height: 0;
}

.diff__column {
  min-width: 0;
}

.diff__column-title {
  margin: 0 0 var(--cf-space-1);
  font-size: var(--cf-font-xs);
  font-weight: 600;
  color: var(--cf-text-secondary);
}

.diff__content {
  height: 148px;
  margin: 0;
  padding: var(--cf-space-2);
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

.diff__content--after {
  border-color: var(--cf-primary-light);
  background: var(--cf-primary-soft);
  color: var(--cf-text);
}

.diff__warnings {
  margin: 0 var(--cf-space-3) var(--cf-space-2);
  padding-left: 18px;
  font-size: var(--cf-font-xs);
  line-height: 1.6;
  color: var(--cf-warning);
}

.diff__foot {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: var(--cf-space-2);
  padding: 0 var(--cf-space-3) var(--cf-space-3);
}

@media (max-width: 1023px) {
  .diff__grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .diff__content {
    height: 112px;
  }
}
</style>
