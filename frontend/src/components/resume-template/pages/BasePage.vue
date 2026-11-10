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
</template>

<script setup lang="ts">
import { computed } from 'vue'

import ReadOnlyModuleBox from '../ReadOnlyModuleBox.vue'
import type { TemplateJson } from '../types'
import { useGetPageStyle } from '../vendor/hooks/useGetPageStyle'

const props = defineProps<{ template: TemplateJson }>()

const pageStyle = useGetPageStyle(props.template)
const visibleModules = computed(() =>
  props.template.componentsTree.filter((module) => module.customProps?.showModule !== false),
)
</script>

<style scoped>
.create-template-elemet {
  width: 100%;
}
</style>
