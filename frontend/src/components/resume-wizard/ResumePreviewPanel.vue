<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

import TemplateGalleryDialog from '@/components/resume/TemplateGalleryDialog.vue'
import { loadTemplateById } from '@/components/resume-template/loadTemplate'
import { TEMPLATES } from '@/components/resume-template/manifest'
import ResumeTemplateRenderer from '@/components/resume-template/ResumeTemplateRenderer.vue'
import { useResumeDraftStore } from '@/stores/resumeDraft'
import { draftContentToPreview } from '@/utils/resumeWizard'

/** 右侧实时预览：固定 820px 的模板按容器宽度缩放，切换模板后立即重绘。 */
const store = useResumeDraftStore()

const TEMPLATE_WIDTH = 820

const galleryVisible = ref(false)
const scale = ref(0.5)
const naturalHeight = ref(820)
const stageRef = ref<HTMLElement | null>(null)
const canvasRef = ref<HTMLElement | null>(null)
let observer: ResizeObserver | null = null

const template = computed(() => loadTemplateById(store.content.preview.templateId))
const previewContent = computed(() => draftContentToPreview(store.content))
const resumeName = computed(
  () => store.draftName || store.content.basic.realName || '我的简历',
)
const templateName = computed(
  () =>
    store.content.preview.templateName ||
    TEMPLATES.find((item) => item.id === store.content.preview.templateId)?.name ||
    '默认模板',
)
const targetPosition = computed(() => store.content.direction.targetPosition ?? null)

function updateScale(): void {
  const stage = stageRef.value
  if (!stage || stage.clientWidth <= 0) {
    return
  }
  const available = stage.clientWidth - 24
  scale.value = Math.min(1, Math.max(0.2, available / TEMPLATE_WIDTH))
}

function measureHeight(): void {
  const canvas = canvasRef.value
  if (canvas && canvas.offsetHeight > 0) {
    naturalHeight.value = canvas.offsetHeight
  }
}

function refreshLayout(): void {
  updateScale()
  measureHeight()
}

function bindObserver(): void {
  if (typeof ResizeObserver === 'undefined') {
    return
  }
  observer = new ResizeObserver(() => refreshLayout())
  if (stageRef.value) {
    observer.observe(stageRef.value)
  }
  if (canvasRef.value) {
    observer.observe(canvasRef.value)
  }
}

function confirmTemplate(templateId: string): void {
  store.content.preview.templateId = templateId
  store.content.preview.templateName =
    TEMPLATES.find((item) => item.id === templateId)?.name ?? null
  galleryVisible.value = false
  void nextTick(refreshLayout)
}

watch(previewContent, () => {
  void nextTick(measureHeight)
})

onMounted(() => {
  refreshLayout()
  bindObserver()
})

onBeforeUnmount(() => {
  observer?.disconnect()
  observer = null
})
</script>

<template>
  <div class="preview-panel">
    <header class="preview-panel__header">
      <div class="preview-panel__title">
        <span class="preview-panel__name">实时预览</span>
        <span class="preview-panel__template">{{ templateName }}</span>
      </div>
      <div class="preview-panel__tools">
        <span class="preview-panel__scale">{{ Math.round(scale * 100) }}%</span>
        <el-button size="small" data-testid="preview-switch-template" @click="galleryVisible = true">
          切换模板
        </el-button>
      </div>
    </header>

    <div ref="stageRef" class="preview-panel__stage">
      <div
        class="preview-panel__scaler"
        :style="{ width: `${TEMPLATE_WIDTH * scale}px`, height: `${naturalHeight * scale}px` }"
      >
        <div
          ref="canvasRef"
          class="preview-panel__canvas"
          :style="{ transform: `scale(${scale})`, transformOrigin: 'top left' }"
        >
          <ResumeTemplateRenderer
            v-if="template"
            :template="template"
            :content="previewContent"
            :resume-name="resumeName"
          />
          <el-empty v-else description="模板加载失败，请切换其他模板" :image-size="80" />
        </div>
      </div>
      <p class="preview-panel__hint">预览已按右侧宽度自动缩放，导出时仍为原始尺寸。</p>
    </div>

    <TemplateGalleryDialog
      v-model="galleryVisible"
      mode="switch"
      :target-position="targetPosition"
      :current-template-id="store.content.preview.templateId"
      @confirm="confirmTemplate"
    />
  </div>
</template>

<style scoped>
.preview-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: var(--cf-card-bg);
  border: 1px solid var(--cf-border);
  border-radius: var(--cf-radius-md);
  overflow: hidden;
}

.preview-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--cf-space-2);
  padding: var(--cf-space-3) var(--cf-space-4);
  border-bottom: 1px solid var(--cf-border);
  flex-shrink: 0;
}

.preview-panel__title {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.preview-panel__name {
  font-size: var(--cf-font-sm);
  font-weight: 600;
  color: var(--cf-text);
}

.preview-panel__template {
  font-size: var(--cf-font-xs);
  color: var(--cf-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.preview-panel__tools {
  display: flex;
  align-items: center;
  gap: var(--cf-space-2);
  flex-shrink: 0;
}

.preview-panel__scale {
  font-size: var(--cf-font-xs);
  color: var(--cf-text-tertiary);
}

.preview-panel__stage {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: var(--cf-space-3);
  background: #eef2f7;
}

.preview-panel__scaler {
  margin: 0 auto;
  overflow: hidden;
  transition: width 0.15s ease, height 0.15s ease;
}

.preview-panel__canvas {
  width: 820px;
  background: #fff;
  box-shadow: 0 12px 28px rgb(15 23 42 / 12%);
}

.preview-panel__hint {
  margin-top: var(--cf-space-2);
  text-align: center;
  font-size: var(--cf-font-xs);
  color: var(--cf-text-tertiary);
}
</style>
