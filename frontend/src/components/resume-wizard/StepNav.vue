<script setup lang="ts">
import { Check } from '@element-plus/icons-vue'

/**
 * 八步步骤条：当前步高亮，已完成步骤可点击回退。
 * 步骤切换由父组件统一处理（先保存再切步）。
 */
const props = defineProps<{
  current: number
  total: number
  titles: string[]
}>()

const emit = defineEmits<{ select: [step: number] }>()

function isDone(index: number): boolean {
  return index + 1 < props.current
}

function isActive(index: number): boolean {
  return index + 1 === props.current
}

function select(index: number): void {
  if (isDone(index)) {
    emit('select', index + 1)
  }
}
</script>

<template>
  <nav class="step-nav" aria-label="简历创建步骤">
    <ol class="step-nav__list">
      <li
        v-for="(title, index) in titles"
        :key="title"
        class="step-item"
        :class="{ 'step-item--active': isActive(index), 'step-item--done': isDone(index) }"
      >
        <button
          type="button"
          class="step-item__btn"
          :disabled="!isDone(index)"
          :aria-current="isActive(index) ? 'step' : undefined"
          :data-testid="`wizard-step-${index + 1}`"
          @click="select(index)"
        >
          <span class="step-item__index">
            <el-icon v-if="isDone(index)"><Check /></el-icon>
            <template v-else>{{ index + 1 }}</template>
          </span>
          <span class="step-item__title">{{ title }}</span>
        </button>
        <span
          v-if="index < total - 1"
          class="step-item__line"
          :class="{ 'step-item__line--done': isDone(index) }"
        />
      </li>
    </ol>
  </nav>
</template>

<style scoped>
.step-nav {
  min-width: 0;
}

.step-nav__list {
  display: flex;
  align-items: center;
  gap: 2px;
  margin: 0;
  padding: 0;
  list-style: none;
  overflow-x: auto;
  scrollbar-width: none;
}

.step-nav__list::-webkit-scrollbar {
  display: none;
}

.step-item {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

.step-item__btn {
  display: flex;
  align-items: center;
  gap: var(--cf-space-2);
  padding: var(--cf-space-1) var(--cf-space-2);
  border: none;
  border-radius: var(--cf-radius-sm);
  background: transparent;
  color: var(--cf-text-tertiary);
  font-size: var(--cf-font-sm);
  font-family: inherit;
  cursor: default;
  white-space: nowrap;
}

.step-item--done .step-item__btn {
  color: var(--cf-text-secondary);
  cursor: pointer;
}

.step-item--done .step-item__btn:hover {
  background: var(--cf-primary-soft);
  color: var(--cf-primary);
}

.step-item--active .step-item__btn {
  color: var(--cf-primary);
  font-weight: 600;
}

.step-item__index {
  width: 22px;
  height: 22px;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  border: 1px solid var(--cf-border-strong);
  background: var(--cf-card-bg);
  font-size: var(--cf-font-xs);
  line-height: 1;
}

.step-item--done .step-item__index {
  border-color: var(--cf-primary);
  background: var(--cf-primary);
  color: #fff;
}

.step-item--active .step-item__index {
  border-color: var(--cf-primary);
  background: var(--cf-primary-soft);
  color: var(--cf-primary);
}

.step-item__line {
  width: 18px;
  height: 1px;
  flex-shrink: 0;
  background: var(--cf-border);
}

.step-item__line--done {
  background: var(--cf-primary);
}

@media (max-width: 1279px) {
  .step-item__title {
    display: none;
  }

  .step-item--active .step-item__title {
    display: inline;
  }
}
</style>
