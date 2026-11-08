<script setup lang="ts">
import PagePlaceholder from '@/components/PagePlaceholder.vue'
</script>

<template>
  <PagePlaceholder
    title="反馈分析"
    description="求职数据分析：投递漏斗、已读率/面试率、简历版本效果对比——反馈回流驱动下一轮简历优化与岗位推荐。"
    :features="[
      '投递漏斗（ECharts）',
      '简历版本效果对比',
      '偏好反馈对推荐排序的影响展示（P1）',
    ]"
  />
</template>
