<script setup lang="ts">
defineProps<{
  title: string
  description: string
  features?: string[]
}>()
</script>

<template>
  <div class="page-placeholder">
    <div class="pp-header">
      <h2>{{ title }}</h2>
      <el-tag type="warning" size="small">骨架已就绪 · 切片开发中</el-tag>
    </div>
    <p class="pp-desc">{{ description }}</p>
    <ul v-if="features && features.length" class="pp-features">
      <li v-for="f in features" :key="f">{{ f }}</li>
    </ul>
  </div>
</template>

<style scoped>
.page-placeholder {
  background: var(--cf-card-bg);
  border: 1px solid var(--cf-border);
  border-radius: 8px;
  padding: 32px;
}

.pp-header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.pp-header h2 {
  font-size: 20px;
}

.pp-desc {
  margin-top: 12px;
  color: var(--cf-text-secondary);
  line-height: 1.7;
}

.pp-features {
  margin: 16px 0 0 20px;
  color: #475569;
  line-height: 2;
}
</style>
