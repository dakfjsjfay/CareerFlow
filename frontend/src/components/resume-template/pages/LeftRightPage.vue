<template>
  <div class="page-box" :style="pageStyle">
    <div class="resume-column left" :style="leftStyle">
      <div
        v-for="module in leftModules"
        :key="module.id"
        class="create-template-elemet"
      >
        <ReadOnlyModuleBox :module="module" />
      </div>
    </div>
    <div class="resume-column right" :style="rightStyle">
      <div
        v-for="module in rightModules"
        :key="module.id"
        class="create-template-elemet"
      >
        <ReadOnlyModuleBox :module="module" />
      </div>
    </div>
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
const leftStyle = useGetCustomStyle(props.template, 'left')
const rightStyle = useGetCustomStyle(props.template, 'right')

const visibleModules = computed(() =>
  props.template.componentsTree.filter((module) => module.customProps?.showModule !== false),
)
// 缺少 layout 的模块默认归左栏，避免在只读渲染时消失
const leftModules = computed(() =>
  visibleModules.value.filter((module) => module.customProps?.layout !== 'right'),
)
const rightModules = computed(() =>
  visibleModules.value.filter((module) => module.customProps?.layout === 'right'),
)
</script>

<style scoped>
.create-template-elemet {
  width: 100%;
}
</style>
