<template>
  <div class="page-box" :style="pageStyle">
    <div
      v-for="module in visibleModules"
      :key="module.id"
      class="create-template-elemet"
    >
      <ReadOnlyModuleBox :module="module" />
    </div>
  </div>

  <div class="line-top">
    <div :style="topLeftStyle"></div>
    <div :style="topRightStyle"></div>
  </div>
  <div :style="lineLeftStyle"></div>
  <div :style="lineRightStyle"></div>
  <div class="line-bottom">
    <div :style="bottomLeftStyle"></div>
    <div :style="bottomRightStyle"></div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import ReadOnlyModuleBox from '../ReadOnlyModuleBox.vue'
import type { TemplateJson } from '../types'
import { useGetCustomStyle } from '../vendor/hooks/useGetCustomStyle'
import { useGetPageStyle } from '../vendor/hooks/useGetPageStyle'

const props = defineProps<{ template: TemplateJson }>()

const pageStyle = useGetPageStyle(props.template)
const topLeftStyle = useGetCustomStyle(props.template, 'topLeftStyle')
const topRightStyle = useGetCustomStyle(props.template, 'topRightStyle')
const lineLeftStyle = useGetCustomStyle(props.template, 'lineLeftStyle')
const lineRightStyle = useGetCustomStyle(props.template, 'lineRightStyle')
const bottomLeftStyle = useGetCustomStyle(props.template, 'bottomLeftStyle')
const bottomRightStyle = useGetCustomStyle(props.template, 'bottomRightStyle')

const visibleModules = computed(() =>
  props.template.componentsTree.filter((module) => module.customProps?.showModule !== false),
)
</script>

<style scoped>
.create-template-elemet {
  width: 100%;
}

.line-top {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  z-index: 1;
  height: 30px;
  padding: 0;
  display: flex;
  align-items: center;
}

.line-bottom {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: 1;
  height: 30px;
  padding: 0;
  display: flex;
  align-items: center;
}
</style>
