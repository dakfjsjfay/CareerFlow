<script setup lang="ts">
/**
 * 统一空状态：图标 + 说明 + 可选主操作。
 */
withDefaults(
  defineProps<{
    description?: string
    actionText?: string
  }>(),
  { description: '暂无数据', actionText: '' },
)

const emit = defineEmits<{ action: [] }>()
</script>

<template>
  <div class="empty-state">
    <div class="empty-state__icon">◇</div>
    <p class="empty-state__desc">{{ description }}</p>
    <el-button v-if="actionText" type="primary" plain @click="emit('action')">
      {{ actionText }}
    </el-button>
    <slot />
  </div>
</template>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--cf-space-3);
  padding: var(--cf-space-7) var(--cf-space-4);
  color: var(--cf-text-secondary);
}

.empty-state__icon {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: var(--cf-primary-soft);
  color: var(--cf-primary);
  font-size: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.empty-state__desc {
  font-size: var(--cf-font-base);
}
</style>
