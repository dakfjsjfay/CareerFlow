<script setup lang="ts">
import { computed } from 'vue'
import { ArrowDown, ArrowUp, Close, MagicStick, Operation } from '@element-plus/icons-vue'

import StepBasic from '@/components/resume-wizard/StepBasic.vue'
import StepDirection from '@/components/resume-wizard/StepDirection.vue'
import StepExperience from '@/components/resume-wizard/StepExperience.vue'
import StepGenerate from '@/components/resume-wizard/StepGenerate.vue'
import StepPhoto from '@/components/resume-wizard/StepPhoto.vue'
import StepPreview from '@/components/resume-wizard/StepPreview.vue'
import StepSoft from '@/components/resume-wizard/StepSoft.vue'
import StepStar from '@/components/resume-wizard/StepStar.vue'
import { STEP_TITLES, type WorkspaceSectionKey } from '@/utils/resumeWizard'

const props = defineProps<{
  step: number
  total: number
  open: boolean
  mode: 'CREATE' | 'EDIT'
  sectionKey: WorkspaceSectionKey
  busy: boolean
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  prev: []
  next: []
  analyze: []
  rewrite: []
  'form-mode': []
}>()

const STEP_COMPONENTS = [
  StepBasic,
  StepDirection,
  StepExperience,
  StepStar,
  StepSoft,
  StepPhoto,
  StepPreview,
  StepGenerate,
]

const currentComponent = computed(() => STEP_COMPONENTS[props.step - 1] ?? STEP_COMPONENTS[0])
const title = computed(() => STEP_TITLES[props.step - 1] ?? '编辑')
const canAnalyze = computed(() => props.sectionKey !== 'JOB_INTENTION')
</script>

<template>
  <section class="step-form" :class="{ 'step-form--closed': !open }">
    <header class="step-form__head">
      <button type="button" class="step-form__toggle" @click="emit('update:open', !open)">
        <span class="step-form__index">{{ step }}</span>
        <span class="step-form__title">{{ title }}</span>
        <span class="step-form__total">/ {{ total }}</span>
        <el-icon class="step-form__arrow">
          <component :is="open ? ArrowUp : ArrowDown" />
        </el-icon>
      </button>
      <div class="step-form__actions">
        <el-tooltip content="用完整表单模式编辑" placement="top">
          <el-button link size="small" @click="emit('form-mode')">
            <el-icon><Operation /></el-icon>
          </el-button>
        </el-tooltip>
        <el-tooltip
          :content="canAnalyze ? 'AI 诊断此区块' : '求职意向暂不支持 AI 诊断'"
          placement="top"
        >
          <el-button
            link
            size="small"
            :disabled="!canAnalyze || busy"
            @click="emit('analyze')"
          >
            <el-icon><MagicStick /></el-icon>
          </el-button>
        </el-tooltip>
        <el-button link size="small" @click="emit('update:open', false)">
          <el-icon><Close /></el-icon>
        </el-button>
      </div>
    </header>

    <div v-if="open" class="step-form__body">
      <component :is="currentComponent" :key="step" />
    </div>

    <footer v-if="open" class="step-form__footer">
      <div class="step-form__nav">
        <el-button size="small" :disabled="step <= 1" @click="emit('prev')">上一步</el-button>
        <el-button v-if="step < total" size="small" type="primary" @click="emit('next')">
          下一步
        </el-button>
      </div>
      <el-button
        size="small"
        :disabled="busy || !canAnalyze"
        @click="emit('rewrite')"
      >
        AI 润色此区块
      </el-button>
    </footer>
  </section>
</template>

<style scoped>
.step-form {
  flex-shrink: 0;
  border-top: 1px solid var(--cf-border);
  background: var(--cf-card-bg);
  display: flex;
  flex-direction: column;
  max-height: 52%;
  min-height: 0;
}

.step-form--closed {
  max-height: none;
}

.step-form__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--cf-space-2);
  padding: var(--cf-space-1) var(--cf-space-2) var(--cf-space-1) var(--cf-space-3);
  flex-shrink: 0;
}

.step-form__toggle {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 6px;
  border: none;
  border-radius: var(--cf-radius-sm);
  background: transparent;
  font-family: inherit;
  font-size: var(--cf-font-sm);
  color: var(--cf-text);
  cursor: pointer;
}

.step-form__toggle:hover {
  background: var(--cf-bg);
}

.step-form__index {
  width: 20px;
  height: 20px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--cf-primary-soft);
  color: var(--cf-primary);
  font-size: var(--cf-font-xs);
  font-weight: 600;
}

.step-form__title {
  font-weight: 600;
}

.step-form__total {
  color: var(--cf-text-tertiary);
  font-size: var(--cf-font-xs);
}

.step-form__arrow {
  color: var(--cf-text-tertiary);
  font-size: 12px;
}

.step-form__actions {
  display: inline-flex;
  align-items: center;
  gap: 2px;
}

.step-form__body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: var(--cf-space-3) var(--cf-space-4) var(--cf-space-4);
}

.step-form__body :deep(.step-head) {
  display: none;
}

.step-form__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--cf-space-2);
  padding: var(--cf-space-2) var(--cf-space-4);
  border-top: 1px solid var(--cf-border);
  flex-shrink: 0;
}

.step-form__nav {
  display: inline-flex;
  gap: var(--cf-space-2);
}
</style>
