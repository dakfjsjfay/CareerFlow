<script setup lang="ts">
import PagePlaceholder from '@/components/PagePlaceholder.vue'
</script>

<template>
  <PagePlaceholder
    title="匹配分析"
    description="逐技能对比岗位要求与简历证据：已满足/部分满足/未体现/待确认，匹配分数可解释（加权公式），点击差距项发起 AI 追问。"
    :features="[
      '技能矩阵可视化（状态 + 证据 + 建议）',
      '可解释匹配评分',
      '点击差距项发起反向优化追问',
      '投递建议：匹配度分析 + AI 话术（切片 4~5 开发）',
    ]"
  />
</template>
