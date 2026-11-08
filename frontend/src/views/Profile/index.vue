<script setup lang="ts">
import PagePlaceholder from '@/components/PagePlaceholder.vue'
</script>

<template>
  <PagePlaceholder
    title="个人档案"
    description="查看与编辑结构化档案：基础信息、教育经历、技能、项目经历、工作/实习经历与求职意向，展示六维度完整度雷达。"
    :features="[
      '档案编辑（手动修改也记录证据）',
      '项目经历 / 工作实习经历管理',
      '完整度雷达（6 维度自动计算）',
      '待确认信息列表（切片 2 开发）',
    ]"
  />
</template>
