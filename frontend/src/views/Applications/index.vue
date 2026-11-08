<script setup lang="ts">
import PagePlaceholder from '@/components/PagePlaceholder.vue'
</script>

<template>
  <PagePlaceholder
    title="投递管理"
    description="模拟一键投递（关联具体简历版本），投递状态由后端状态机校验，每次流转记录历史；超期未更新自动进入待跟进提醒。"
    :features="[
      '投递记录列表 + 状态筛选',
      '状态更新（非法流转被拒绝）',
      '状态时间轴',
      '待跟进提醒 + 已跟进处理（切片 5 开发）',
    ]"
  />
</template>
