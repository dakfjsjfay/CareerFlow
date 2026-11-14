<script setup lang="ts">
/**
 * 列表页统一筛选栏：左侧插槽放筛选控件，右侧放操作按钮。
 */
</script>

<template>
  <div class="filter-bar">
    <div class="filter-bar__main">
      <slot />
    </div>
    <div v-if="$slots.actions" class="filter-bar__actions">
      <slot name="actions" />
    </div>
  </div>
</template>

<style scoped>
.filter-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--cf-space-3);
  padding: var(--cf-space-3) var(--cf-space-4);
  background: var(--cf-card-bg);
  border: 1px solid var(--cf-border);
  border-radius: var(--cf-radius-md);
}

.filter-bar__main {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--cf-space-2);
  min-width: 0;
}

.filter-bar__actions {
  display: flex;
  align-items: center;
  gap: var(--cf-space-2);
  flex-shrink: 0;
}

@media (max-width: 767px) {
  .filter-bar {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
