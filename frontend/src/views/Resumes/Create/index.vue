<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import LoadingSkeleton from '@/components/common/LoadingSkeleton.vue'
import DraftSaveIndicator from '@/components/resume-wizard/DraftSaveIndicator.vue'
import ResumePreviewPanel from '@/components/resume-wizard/ResumePreviewPanel.vue'
import StepBasic from '@/components/resume-wizard/StepBasic.vue'
import StepDirection from '@/components/resume-wizard/StepDirection.vue'
import StepExperience from '@/components/resume-wizard/StepExperience.vue'
import StepGenerate from '@/components/resume-wizard/StepGenerate.vue'
import StepNav from '@/components/resume-wizard/StepNav.vue'
import StepPhoto from '@/components/resume-wizard/StepPhoto.vue'
import StepPreview from '@/components/resume-wizard/StepPreview.vue'
import StepSoft from '@/components/resume-wizard/StepSoft.vue'
import StepStar from '@/components/resume-wizard/StepStar.vue'
import { useResumeDraftStore } from '@/stores/resumeDraft'
import { STEP_TITLES, validateStep } from '@/utils/resumeWizard'

/**
 * 八步简历创建（docs/2.0/ui-redesign.md §4.1）：
 * 顶部固定步骤条 + 中间步骤表单 + 右侧实时预览；<1024px 切换为两个标签页。
 */
const store = useResumeDraftStore()
const router = useRouter()
const route = useRoute()

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

const ready = ref(false)
const initError = ref('')
const isCompact = ref(false)
const mobileTab = ref<'form' | 'preview'>('form')
const formScrollRef = ref<HTMLElement | null>(null)

const currentComponent = computed(
  () => STEP_COMPONENTS[store.currentStep - 1] ?? STEP_COMPONENTS[0],
)

let mediaQuery: MediaQueryList | null = null

function syncCompact(): void {
  if (typeof window === 'undefined') {
    return
  }
  isCompact.value = mediaQuery ? mediaQuery.matches : window.innerWidth < 1024
}

function bindViewport(): void {
  if (typeof window === 'undefined') {
    return
  }
  if (typeof window.matchMedia === 'function') {
    mediaQuery = window.matchMedia('(max-width: 1023px)')
    mediaQuery.addEventListener('change', syncCompact)
  } else {
    window.addEventListener('resize', syncCompact)
  }
  syncCompact()
}

function unbindViewport(): void {
  if (typeof window === 'undefined') {
    return
  }
  if (mediaQuery) {
    mediaQuery.removeEventListener('change', syncCompact)
    mediaQuery = null
  } else {
    window.removeEventListener('resize', syncCompact)
  }
}

async function bootstrap(): Promise<void> {
  initError.value = ''
  try {
    const draftIdParam = Number(route.query.draftId ?? '')
    if (draftIdParam) {
      // 工作区「表单模式」入口：直接加载指定草稿，不再弹恢复确认
      await store.load(draftIdParam)
      ready.value = true
      return
    }
    const restored = await store.restoreLast()
    if (restored) {
      try {
        await ElMessageBox.confirm('检测到未完成的草稿，是否继续上次编辑？', '发现未完成草稿', {
          confirmButtonText: '继续编辑',
          cancelButtonText: '重新开始',
          type: 'info',
        })
        ElMessage.success('已恢复上次编辑的草稿')
      } catch {
        store.reset()
        await store.initNew()
      }
    } else {
      await store.initNew()
    }
    ready.value = true
  } catch (error) {
    const message = (error as { response?: { data?: { message?: string } } })?.response?.data
      ?.message
    initError.value = message || '草稿初始化失败，请检查网络后重试'
    ElMessage.error(initError.value)
  }
}

function retryBootstrap(): void {
  void bootstrap()
}

function scrollFormTop(): void {
  formScrollRef.value?.scrollTo({ top: 0, behavior: 'smooth' })
}

async function handleNext(): Promise<void> {
  if (store.currentStep >= store.totalSteps) {
    return
  }
  const missing = validateStep(store.content, store.currentStep)
  if (missing.length > 0) {
    ElMessage.warning(missing[0])
    return
  }
  await store.flush()
  store.setStep(store.currentStep + 1)
  mobileTab.value = 'form'
  scrollFormTop()
}

async function handlePrev(): Promise<void> {
  if (store.currentStep <= 1) {
    return
  }
  await store.flush()
  store.setStep(store.currentStep - 1)
  mobileTab.value = 'form'
  scrollFormTop()
}

async function handleStepSelect(step: number): Promise<void> {
  if (step >= store.currentStep) {
    return
  }
  await store.flush()
  store.setStep(step)
  mobileTab.value = 'form'
  scrollFormTop()
}

async function handleExit(): Promise<void> {
  await store.flush()
  void router.push('/resumes')
}

watch(
  () => store.content,
  () => {
    if (ready.value && store.isDraft) {
      store.touch()
    }
  },
  { deep: true },
)

onMounted(() => {
  bindViewport()
  void bootstrap()
})

onBeforeUnmount(() => {
  unbindViewport()
})
</script>

<template>
  <div class="wizard-page">
    <header class="wizard-topbar">
      <StepNav
        :current="store.currentStep"
        :total="store.totalSteps"
        :titles="STEP_TITLES"
        @select="handleStepSelect"
      />
      <div class="wizard-topbar__aside">
        <DraftSaveIndicator />
        <el-button link class="wizard-exit" data-testid="wizard-exit" @click="handleExit">
          退出
        </el-button>
      </div>
    </header>

    <div v-if="isCompact" class="wizard-tabs" role="tablist" aria-label="创建内容切换">
      <button
        type="button"
        role="tab"
        class="wizard-tab"
        :class="{ 'wizard-tab--active': mobileTab === 'form' }"
        :aria-selected="mobileTab === 'form'"
        @click="mobileTab = 'form'"
      >
        步骤表单
      </button>
      <button
        type="button"
        role="tab"
        class="wizard-tab"
        :class="{ 'wizard-tab--active': mobileTab === 'preview' }"
        :aria-selected="mobileTab === 'preview'"
        @click="mobileTab = 'preview'"
      >
        简历预览
      </button>
    </div>

    <div v-if="initError" class="wizard-init-error">
      <el-result icon="warning" title="草稿加载失败" :sub-title="initError">
        <template #extra>
          <el-button type="primary" @click="retryBootstrap">重新加载</el-button>
        </template>
      </el-result>
    </div>

    <div v-else class="wizard-body" :class="{ 'wizard-body--compact': isCompact }">
      <section
        v-show="!isCompact || mobileTab === 'form'"
        class="wizard-main"
        aria-label="当前步骤表单"
      >
        <div ref="formScrollRef" class="wizard-form">
          <LoadingSkeleton v-if="!ready" :rows="6" :height="20" />
          <component :is="currentComponent" v-else :key="store.currentStep" />
        </div>

        <footer class="wizard-footer">
          <span class="wizard-footer__step">
            第 {{ store.currentStep }} / {{ store.totalSteps }} 步 · 内容会自动保存
          </span>
          <div class="wizard-footer__actions">
            <el-button
              data-testid="wizard-prev"
              :disabled="!ready || store.currentStep <= 1"
              @click="handlePrev"
            >
              上一步
            </el-button>
            <el-button
              v-if="store.currentStep < store.totalSteps"
              type="primary"
              data-testid="wizard-next"
              :disabled="!ready"
              @click="handleNext"
            >
              下一步
            </el-button>
          </div>
        </footer>
      </section>

      <aside
        v-show="!isCompact || mobileTab === 'preview'"
        class="wizard-preview"
        aria-label="简历实时预览"
      >
        <ResumePreviewPanel v-if="ready" />
        <LoadingSkeleton v-else :rows="4" :height="18" />
      </aside>
    </div>
  </div>
</template>

<style scoped>
.wizard-page {
  display: flex;
  flex-direction: column;
  gap: var(--cf-space-3);
  height: calc(100vh - var(--cf-header-height) - 2 * var(--cf-space-5));
  min-height: 560px;
}

.wizard-topbar {
  display: flex;
  align-items: center;
  gap: var(--cf-space-4);
  flex-shrink: 0;
  padding: var(--cf-space-2) var(--cf-space-4);
  background: var(--cf-card-bg);
  border: 1px solid var(--cf-border);
  border-radius: var(--cf-radius-md);
  box-shadow: var(--cf-shadow-sm);
}

.wizard-topbar :deep(.step-nav) {
  flex: 1;
  min-width: 0;
}

.wizard-topbar__aside {
  display: flex;
  align-items: center;
  gap: var(--cf-space-3);
  flex-shrink: 0;
}

.wizard-exit {
  color: var(--cf-text-secondary);
  font-size: var(--cf-font-sm);
}

.wizard-tabs {
  display: flex;
  flex-shrink: 0;
  padding: 3px;
  gap: 3px;
  background: var(--cf-card-bg);
  border: 1px solid var(--cf-border);
  border-radius: var(--cf-radius-sm);
}

.wizard-tab {
  flex: 1;
  padding: 6px 12px;
  border: none;
  border-radius: calc(var(--cf-radius-sm) - 2px);
  background: transparent;
  color: var(--cf-text-secondary);
  font-family: inherit;
  font-size: var(--cf-font-sm);
  cursor: pointer;
}

.wizard-tab--active {
  background: var(--cf-primary-soft);
  color: var(--cf-primary);
  font-weight: 600;
}

.wizard-init-error {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--cf-card-bg);
  border: 1px solid var(--cf-border);
  border-radius: var(--cf-radius-md);
}

.wizard-body {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(0, 1fr) clamp(360px, 32vw, 520px);
  gap: var(--cf-space-4);
}

.wizard-body--compact {
  display: flex;
  flex-direction: column;
}

.wizard-main {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  background: var(--cf-card-bg);
  border: 1px solid var(--cf-border);
  border-radius: var(--cf-radius-md);
  overflow: hidden;
}

.wizard-form {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: var(--cf-space-5);
}

.wizard-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--cf-space-3);
  flex-shrink: 0;
  padding: var(--cf-space-3) var(--cf-space-5);
  border-top: 1px solid var(--cf-border);
  background: var(--cf-card-bg);
}

.wizard-footer__step {
  font-size: var(--cf-font-xs);
  color: var(--cf-text-tertiary);
}

.wizard-footer__actions {
  display: flex;
  gap: var(--cf-space-2);
}

.wizard-preview {
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.wizard-body--compact .wizard-main,
.wizard-body--compact .wizard-preview {
  flex: 1;
  min-height: 0;
}

.wizard-body--compact .wizard-preview {
  overflow: hidden;
}

@media (max-width: 1023px) {
  .wizard-page {
    height: calc(100vh - var(--cf-header-height) - 2 * var(--cf-space-5));
    min-height: 480px;
  }

  .wizard-topbar {
    flex-wrap: wrap;
    gap: var(--cf-space-2);
  }

  .wizard-topbar__aside {
    width: 100%;
    justify-content: space-between;
  }

  .wizard-form {
    padding: var(--cf-space-4);
  }

  .wizard-footer {
    padding: var(--cf-space-3) var(--cf-space-4);
  }

  .wizard-footer__step {
    display: none;
  }
}
</style>
