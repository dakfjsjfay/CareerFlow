<script setup lang="ts">
/**
 * 页面统一标题区：标题、描述、右侧操作区。
 * 所有一级页面使用，保证字号、间距一致。
 */
withDefaults(
  defineProps<{
    title: string
    description?: string
    bordered?: boolean
  }>(),
  { description: '', bordered: false },
)
</script>

<template>
  <div class="page-header" :class="{ 'page-header--bordered': bordered }">
    <div class="page-header__text">
      <h1 class="page-header__title">{{ title }}</h1>
      <p v-if="description" class="page-header__desc">{{ description }}</p>
    </div>
    <div v-if="$slots.actions" class="page-header__actions">
      <slot name="actions" />
    </div>
  </div>
</template>

<style scoped>
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--cf-space-4);
  padding-bottom: var(--cf-space-3);
}

.page-header--bordered {
  border-bottom: 1px solid var(--cf-border);
}

.page-header__text {
  min-width: 0;
}

.page-header__title {
  font-size: var(--cf-font-2xl);
  font-weight: 600;
  line-height: 1.3;
  color: var(--cf-text);
}

.page-header__desc {
  margin-top: var(--cf-space-1);
  font-size: var(--cf-font-sm);
  color: var(--cf-text-secondary);
}

.page-header__actions {
  display: flex;
  align-items: center;
  gap: var(--cf-space-2);
  flex-shrink: 0;
}

@media (max-width: 767px) {
  .page-header {
    flex-direction: column;
    align-items: stretch;
  }

  .page-header__title {
    font-size: var(--cf-font-xl);
  }
}
</style>
