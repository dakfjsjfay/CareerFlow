<template>
  <div v-if="module.customProps?.showModule !== false" class="module-box">
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
import { moduleTitles } from './moduleTitles'
import { moduleComponents } from './registerModules'
import type { TemplateModule } from './types'

defineProps<{ module: TemplateModule }>()
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
