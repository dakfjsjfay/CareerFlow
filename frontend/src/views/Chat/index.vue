<script setup lang="ts">
import PagePlaceholder from '@/components/PagePlaceholder.vue'
</script>

<template>
  <PagePlaceholder
    title="AI 对话建档"
    description="通过多轮对话收集教育经历、工作/实习经历、技能与项目经验，AI 自动抽取结构化信息并对缺失信息主动追问。"
    :features="[
      '多轮对话与历史保存（刷新后可恢复）',
      '结构化信息抽取 + 证据链',
      '缺失字段主动追问',
      '抽取结果确认/拒绝（切片 2 开发）',
    ]"
  />
</template>
