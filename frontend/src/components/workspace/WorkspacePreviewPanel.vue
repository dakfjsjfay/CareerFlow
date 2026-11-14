<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { FullScreen, Minus, Plus, ZoomIn } from '@element-plus/icons-vue'

import { loadTemplateById } from '@/components/resume-template/loadTemplate'
import ResumeTemplateRenderer from '@/components/resume-template/ResumeTemplateRenderer.vue'
import type { ResumeContent } from '@/components/resume-template/types'
import type { WorkspaceSectionKey } from '@/utils/resumeWizard'

const props = withDefaults(
  defineProps<{
    content: ResumeContent
    resumeName: string
    templateId: string
    activeSection?: WorkspaceSectionKey | ''
    loading?: boolean
    empty?: boolean
  }>(),
  { activeSection: '', loading: false, empty: false },
)

const emit = defineEmits<{ 'select-section': [section: string] }>()

/** 模板设计宽度；A4 比例约为 210:297。 */
const TEMPLATE_WIDTH = 820
const A4_HEIGHT = Math.round((TEMPLATE_WIDTH * 297) / 210)

const stageRef = ref<HTMLElement | null>(null)
const canvasRef = ref<HTMLElement | null>(null)
const fitScale = ref(0.6)
const zoom = ref<number | null>(null)
const naturalHeight = ref(A4_HEIGHT)
const pageCount = ref(1)

let observer: ResizeObserver | null = null

const template = computed(() => loadTemplateById(props.templateId))
const scale = computed(() => zoom.value ?? fitScale.value)
const zoomLabel = computed(() => `${Math.round(scale.value * 100)}%`)
const pageBreaks = computed(() => {
  const lines: number[] = []
  for (let index = 1; index < pageCount.value; index += 1) {
    lines.push(index * A4_HEIGHT * scale.value)
  }
  return lines
})

function measureFit(): void {
  const stage = stageRef.value
  if (!stage || stage.clientWidth <= 0) {
    return
  }
  const available = stage.clientWidth - 48
  fitScale.value = Math.min(1, Math.max(0.2, available / TEMPLATE_WIDTH))
}

function measureCanvas(): void {
  const canvas = canvasRef.value
  if (!canvas || canvas.offsetHeight <= 0) {
    return
  }
  naturalHeight.value = canvas.offsetHeight
  pageCount.value = Math.max(1, Math.ceil(canvas.offsetHeight / A4_HEIGHT))
}

function refresh(): void {
  measureFit()
  measureCanvas()
}

function bindObserver(): void {
  if (typeof ResizeObserver === 'undefined') {
    return
  }
  observer = new ResizeObserver(() => refresh())
  if (stageRef.value) {
    observer.observe(stageRef.value)
  }
  if (canvasRef.value) {
    observer.observe(canvasRef.value)
  }
}

function zoomIn(): void {
  zoom.value = Math.min(1.6, Math.round((scale.value + 0.1) * 10) / 10)
}

function zoomOut(): void {
  zoom.value = Math.max(0.2, Math.round((scale.value - 0.1) * 10) / 10)
}

function zoomFit(): void {
  zoom.value = null
  void nextTick(refresh)
}

function handleClick(event: MouseEvent): void {
  const target = event.target as HTMLElement | null
  const sectionEl = target?.closest('[data-section]') as HTMLElement | null
  const key = sectionEl?.dataset.section
  if (key) {
    emit('select-section', key)
  }
}

/** 选中区块高亮并滚动到可视区域；仅操作 DOM，不改模板渲染逻辑。 */
function highlightActive(): void {
  const canvas = canvasRef.value
  if (!canvas) {
    return
  }
  canvas.querySelectorAll('[data-section].cf-section-active').forEach((element) => {
    element.classList.remove('cf-section-active')
  })
  if (!props.activeSection) {
    return
  }
  const target = canvas.querySelector(
    `[data-section="${props.activeSection}"]`,
  ) as HTMLElement | null
  if (!target) {
    return
  }
  target.classList.add('cf-section-active')
  const stage = stageRef.value
  if (stage) {
    const top = target.offsetTop * scale.value
    stage.scrollTo({ top: Math.max(0, top - 24), behavior: 'smooth' })
  }
}

watch(
  () => [props.content, props.templateId],
  () => {
    void nextTick(() => {
      refresh()
      highlightActive()
    })
  },
  { deep: true },
)

watch(
  () => props.activeSection,
  () => {
    void nextTick(highlightActive)
  },
)

watch(scale, () => {
  void nextTick(highlightActive)
})

onMounted(() => {
  refresh()
  bindObserver()
  void nextTick(highlightActive)
})

onBeforeUnmount(() => {
  observer?.disconnect()
  observer = null
})
</script>

<template>
  <section class="preview" aria-label="简历预览">
    <header class="preview__bar">
      <div class="preview__pages">
        <span class="preview__dot" />
        <span>{{ pageCount }} 页 · A4</span>
      </div>
      <div class="preview__tools">
        <el-tooltip content="缩小" placement="top">
          <el-button link :disabled="scale <= 0.2" @click="zoomOut">
            <el-icon><Minus /></el-icon>
          </el-button>
        </el-tooltip>
        <span class="preview__zoom">{{ zoomLabel }}</span>
        <el-tooltip content="放大" placement="top">
          <el-button link :disabled="scale >= 1.6" @click="zoomIn">
            <el-icon><Plus /></el-icon>
          </el-button>
        </el-tooltip>
        <el-tooltip content="适应宽度" placement="top">
          <el-button link :class="{ 'is-on': zoom === null }" @click="zoomFit">
            <el-icon><FullScreen /></el-icon>
          </el-button>
        </el-tooltip>
      </div>
    </header>

    <div ref="stageRef" class="preview__stage">
      <div class="preview__skeleton" v-if="loading">
        <div class="preview__paper preview__paper--skeleton" />
      </div>

      <el-empty
        v-else-if="!template"
        class="preview__error"
        description="模板加载失败，请在顶部重新选择模板"
        :image-size="90"
      />

      <div
        v-else
        class="preview__scaler"
        :style="{ width: `${TEMPLATE_WIDTH * scale}px`, height: `${naturalHeight * scale}px` }"
      >
        <div
          ref="canvasRef"
          class="preview__canvas"
          :class="{ 'preview__canvas--empty': empty }"
          :style="{ transform: `scale(${scale})`, transformOrigin: 'top left' }"
          @click="handleClick"
        >
          <ResumeTemplateRenderer
            :template="template"
            :content="content"
            :resume-name="resumeName"
          />
        </div>

        <span
          v-for="(line, index) in pageBreaks"
          :key="line"
          class="preview__page-break"
          :style="{ top: `${line}px` }"
        >
          <span class="preview__page-label">第 {{ index + 2 }} 页</span>
        </span>

        <div v-if="empty" class="preview__hint">
          <el-icon><ZoomIn /></el-icon>
          <p>在左侧补充信息后，这里会实时生成简历预览</p>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.preview {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: #f3f5f9;
}

.preview__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--cf-space-3);
  flex-shrink: 0;
  height: 40px;
  padding: 0 var(--cf-space-4);
  border-bottom: 1px solid var(--cf-border);
  background: var(--cf-card-bg);
}

.preview__pages {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: var(--cf-font-xs);
  color: var(--cf-text-secondary);
}

.preview__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--cf-success);
}

.preview__tools {
  display: inline-flex;
  align-items: center;
  gap: 2px;
}

.preview__zoom {
  min-width: 42px;
  text-align: center;
  font-size: var(--cf-font-xs);
  color: var(--cf-text-secondary);
  font-variant-numeric: tabular-nums;
}

.preview__tools :deep(.el-button.is-on) {
  color: var(--cf-primary);
}

.preview__stage {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: var(--cf-space-5) var(--cf-space-4);
}

.preview__scaler {
  position: relative;
  margin: 0 auto;
}

.preview__canvas {
  width: 820px;
  min-height: 820px;
  background: #fff;
  box-shadow: 0 10px 30px rgb(15 23 42 / 10%);
  transition: box-shadow 0.15s ease;
}

.preview__canvas--empty {
  min-height: 1160px;
}

.preview__canvas :deep([data-section]) {
  cursor: pointer;
  transition: outline-color 0.15s ease;
  outline: 1px solid transparent;
  outline-offset: -1px;
}

.preview__canvas :deep([data-section]:hover) {
  outline-color: rgb(37 99 235 / 35%);
}

.preview__canvas :deep([data-section].cf-section-active) {
  outline: 1px dashed var(--cf-primary);
  background: rgb(37 99 235 / 3%);
}

.preview__page-break {
  position: absolute;
  left: 0;
  right: 0;
  height: 0;
  border-top: 1px dashed rgb(100 116 139 / 45%);
  pointer-events: none;
}

.preview__page-label {
  position: absolute;
  right: 8px;
  top: 4px;
  padding: 1px 6px;
  border-radius: 999px;
  background: rgb(255 255 255 / 90%);
  color: var(--cf-text-tertiary);
  font-size: 11px;
}

.preview__hint {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--cf-space-2);
  color: var(--cf-text-tertiary);
  font-size: var(--cf-font-sm);
  pointer-events: none;
}

.preview__hint .el-icon {
  font-size: 28px;
  color: var(--cf-border-strong);
}

.preview__skeleton {
  display: flex;
  justify-content: center;
}

.preview__paper {
  width: min(720px, 100%);
  aspect-ratio: 210 / 297;
  border-radius: 4px;
  background: linear-gradient(100deg, #f8fafc 30%, #eef2f7 50%, #f8fafc 70%);
  background-size: 200% 100%;
  animation: preview-shimmer 1.4s ease-in-out infinite;
}

@keyframes preview-shimmer {
  from {
    background-position: 120% 0;
  }
  to {
    background-position: -20% 0;
  }
}

.preview__error {
  padding: var(--cf-space-6) 0;
}

@media (max-width: 1023px) {
  .preview__stage {
    padding: var(--cf-space-3);
  }
}
</style>
