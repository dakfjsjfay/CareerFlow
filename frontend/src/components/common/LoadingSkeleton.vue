<script setup lang="ts">
/**
 * 骨架屏：列表/卡片加载态统一使用。
 */
withDefaults(defineProps<{ rows?: number; height?: number }>(), { rows: 3, height: 16 })
</script>

<template>
  <div class="skeleton" aria-busy="true" aria-label="加载中">
    <div
      v-for="index in rows"
      :key="index"
      class="skeleton__row"
      :style="{ height: `${height}px`, width: index === rows ? '60%' : '100%' }"
    />
  </div>
</template>

<style scoped>
.skeleton {
  display: flex;
  flex-direction: column;
  gap: var(--cf-space-3);
  padding: var(--cf-space-2) 0;
}

.skeleton__row {
  border-radius: var(--cf-radius-sm);
  background: linear-gradient(90deg, #eef2f7 25%, #f6f8fb 37%, #eef2f7 63%);
  background-size: 400% 100%;
  animation: skeleton-shimmer 1.4s ease infinite;
}

@keyframes skeleton-shimmer {
  0% {
    background-position: 100% 50%;
  }
  100% {
    background-position: 0 50%;
  }
}
</style>
