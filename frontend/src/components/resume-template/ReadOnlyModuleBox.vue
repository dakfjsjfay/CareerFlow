<template>
  <div
    v-if="module.customProps?.showModule !== false"
    class="module-box"
    :data-section="sectionKey || undefined"
  >
    <component :is="moduleComponents[module.componentName]" :module="module">
      <template v-if="module.customProps?.ModuleTitleCpt" #module-title>
        <component
          :is="moduleTitles[module.customProps.ModuleTitleCpt]"
          v-if="module.props?.title?.show !== false"
          :module="module"
        />
      </template>
    </component>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import { moduleTitles } from './moduleTitles'
import { moduleComponents } from './registerModules'
import type { TemplateModule } from './types'

const props = defineProps<{ module: TemplateModule }>()

/**
 * 简历工作区区块锚点：按模板模块类型标注 data-section，
 * 供右侧预览点击定位、左侧对话切换上下文使用，不修改模板渲染实现。
 */
const SECTION_BY_MODULE: Record<string, string> = {
  BaseInfo: 'BASIC',
  Custom: 'BASIC',
  JobIntention: 'JOB_INTENTION',
  EduBackground: 'EDUCATION',
  SkillSpecialties: 'SKILL',
  WorkExperience: 'EXPERIENCE',
  InternshipExperience: 'EXPERIENCE',
  ProjectExperience: 'PROJECT',
  SelfEvaluation: 'SELF_EVALUATION',
}

const sectionKey = computed(() => {
  const type = props.module.componentName.split('_')[0]
  return SECTION_BY_MODULE[type] ?? ''
})
</script>

<style scoped>
.module-box {
  display: flex;
  box-sizing: border-box;
  overflow: hidden;
  position: relative;
  width: 100%;
}
</style>
