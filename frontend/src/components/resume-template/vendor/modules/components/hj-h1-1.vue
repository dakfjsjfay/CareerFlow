<template>
  <h1 class="hj-p" :style="style">{{ title }}</h1>
</template>
<script lang="ts" setup>
// @ts-nocheck
  interface TP {
    title?: string;
    style?: any;
  }

  withDefaults(defineProps<TP>(), {
    title: '我是模块标题',
    style: {}
  });
</script>
<style lang="scss" scoped>
  .hj-p {
    display: flex;
    align-items: center;
  }
</style>
