<script setup lang="ts">
import PagePlaceholder from '@/components/PagePlaceholder.vue'
</script>

<template>
  <PagePlaceholder
    title="岗位中心"
    description="浏览内置职位库（10~20 条模拟 JD），支持关键词/城市/薪资筛选，JD 经 AI 解析生成结构化技能要求。"
    :features="[
      '岗位列表 + 筛选（keyword/city/salary）',
      'JD 技能要求解析展示',
      '从岗位进入匹配分析页（/match/:jobId）',
      '岗位推荐（参考用户反馈排序，切片 4 开发）',
    ]"
  />
</template>
