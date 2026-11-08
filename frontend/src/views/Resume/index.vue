<script setup lang="ts">
import PagePlaceholder from '@/components/PagePlaceholder.vue'
</script>

<template>
  <PagePlaceholder
    title="我的简历"
    description="基于已确认档案生成结构化简历，每次生成保留版本（永不覆盖），支持在线预览与 PDF/DOCX 导出。"
    :features="[
      'AI 生成结构化简历（内容与样式分离）',
      '版本管理（生成原因可追溯）',
      'PDF / DOCX 导出（指定版本）',
      '简历预览（切片 3 开发）',
    ]"
  />
</template>
