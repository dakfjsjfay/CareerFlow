<template>
  <div class="resume-template-renderer">
    <component :is="pageComponent" :key="renderKey" :template="appliedTemplate" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'

import { applyContent } from './adapter'
import { loadIconfont } from './loadIconfont'
import BasePage from './pages/BasePage.vue'
import LeftRightPage from './pages/LeftRightPage.vue'
import Page_1 from './pages/Page_1.vue'
import type { ResumeContent, TemplateJson } from './types'

const props = defineProps<{
  template: TemplateJson
  content: ResumeContent
  resumeName: string
}>()

const appliedTemplate = computed(() =>
  applyContent(props.template, props.content, props.resumeName),
)

const renderKey = computed(
  () => `${props.template.id}-${props.template.props?.pageName}-${props.content?.meta?.templateId ?? ''}`,
)

const pageComponent = computed(() => {
  switch (props.template.props?.pageName) {
    case 'LeftRightPage':
      return LeftRightPage
    case 'Page_1':
      return Page_1
    default:
      return BasePage
  }
})

onMounted(loadIconfont)
</script>

<style scoped>
.resume-template-renderer {
  position: relative;
  width: 820px;
  min-height: 820px;
  background: #fff;
  overflow: hidden;
}
</style>
