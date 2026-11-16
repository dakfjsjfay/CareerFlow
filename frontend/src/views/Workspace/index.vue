<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch, type Component } from 'vue'
import { ElMessage } from 'element-plus'
import { ChatDotRound, Document, Files } from '@element-plus/icons-vue'
import { useRoute } from 'vue-router'

import CandidateDiffDialog from '@/components/resume-wizard/CandidateDiffDialog.vue'
import TemplateGalleryDialog from '@/components/resume/TemplateGalleryDialog.vue'
import VersionHistoryDrawer from '@/components/resume/VersionHistoryDrawer.vue'
import { loadTemplateById } from '@/components/resume-template/loadTemplate'
import WorkspaceChatPanel from '@/components/workspace/WorkspaceChatPanel.vue'
import WorkspaceDiffPanel from '@/components/workspace/WorkspaceDiffPanel.vue'
import WorkspacePreviewPanel from '@/components/workspace/WorkspacePreviewPanel.vue'
import WorkspaceSectionNav from '@/components/workspace/WorkspaceSectionNav.vue'
import WorkspaceSidePanel from '@/components/workspace/WorkspaceSidePanel.vue'
import WorkspaceStepForm from '@/components/workspace/WorkspaceStepForm.vue'
import WorkspaceToolbar from '@/components/workspace/WorkspaceToolbar.vue'
import {
  useWorkspaceSession,
  WORKSPACE_ACTIONS,
  type WorkspaceMessage,
} from '@/composables/useWorkspaceSession'
import { SECTION_LABELS, type WorkspaceSectionKey } from '@/utils/resumeWizard'

/**
 * 简历工作区：
 * - 生成模式：左侧 AI 对话 + 八步表单，右侧 A4 实时预览（两栏）
 * - 编辑模式：左侧模块目录 + 分析状态，中间预览与修改前后对比，右侧 AI 对话与操作记录（三栏）
 * <1024px 统一改为 tab 切换。
 */
const route = useRoute()
const session = useWorkspaceSession()

const {
  store,
  mode,
  resumeId,
  resumeName,
  ready,
  initError,
  messages,
  sending,
  applying,
  generating,
  exporting,
  activeSection,
  versions,
  activeVersionId,
  canUndo,
  formOpen,
  progress,
  previewContent,
  previewEmpty,
  draftName,
  jobId,
  jobOptions,
  jobLoading,
  sectionNavItems,
  pendingCandidate,
  pendingCandidateMessage,
  editHistory,
  historyLoading,
} = session

const compact = ref(false)
const tab = ref<string>('chat')

const galleryVisible = ref(false)
const historyVisible = ref(false)
const compareVisible = ref(false)
const compareMessage = ref<WorkspaceMessage | null>(null)

let lastBootKey = ''
let lastEditMode = session.mode.value === 'EDIT'

const editMode = computed(() => mode.value === 'EDIT')
const canExport = computed(() => resumeId.value != null && activeVersionId.value != null)
const versionLabel = computed(() => {
  const current = versions.value.find((item) => item.versionId === activeVersionId.value)
  return current ? `v${current.versionNumber}` : ''
})
const previewName = computed(
  () => draftName.value.trim() || resumeName.value.trim() || '我的简历',
)
const placeholder = computed(() =>
  mode.value === 'CREATE'
    ? '描述你的信息，或向 AI 提问，例如：帮我看看还缺什么'
    : `描述你希望如何修改「${SECTION_LABELS[activeSection.value] ?? '简历'}」`,
)
const suggestions = computed(() =>
  mode.value === 'CREATE'
    ? ['还缺哪些关键信息？', '我的求职方向怎么定？', '这段经历怎么写更专业？']
    : ['突出结果与量化数据', '改为 STAR 结构表达', '表达再精简一些'],
)

interface WorkspaceTab {
  key: string
  label: string
  icon: Component
}

const tabs = computed<WorkspaceTab[]>(() =>
  editMode.value
    ? [
        { key: 'sections', label: '模块', icon: Files },
        { key: 'preview', label: '预览', icon: Document },
        { key: 'ai', label: 'AI 助手', icon: ChatDotRound },
      ]
    : [
        { key: 'chat', label: 'AI 对话', icon: ChatDotRound },
        { key: 'preview', label: '简历预览', icon: Document },
      ],
)

function syncViewport(): void {
  compact.value = typeof window !== 'undefined' && window.innerWidth < 1024
}

function bootKey(): string {
  return `${String(route.name)}:${String(route.params.resumeId ?? '')}`
}

function runBoot(): void {
  lastBootKey = bootKey()
  void session.boot()
}

onMounted(() => {
  syncViewport()
  tab.value = editMode.value ? 'preview' : 'chat'
  window.addEventListener('resize', syncViewport)
  runBoot()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', syncViewport)
  void store.flush()
})

watch(
  () => bootKey(),
  (key) => {
    if (key !== lastBootKey) {
      runBoot()
    }
  },
)

watch(
  () => messages.value.length,
  () => {
    if (compact.value) {
      tab.value = editMode.value ? 'ai' : 'chat'
    }
  },
)

watch(editMode, (value) => {
  if (value !== lastEditMode) {
    lastEditMode = value
    tab.value = value ? 'preview' : 'chat'
  }
})

function handleSelectSection(section: string): void {
  session.selectSection(section as WorkspaceSectionKey, { silent: true })
  if (compact.value) {
    tab.value = 'preview'
  }
}

function openCompare(message: WorkspaceMessage): void {
  compareMessage.value = message
  compareVisible.value = true
}

async function confirmCompare(finalContent: unknown): Promise<void> {
  if (!compareMessage.value) {
    return
  }
  await session.applyCandidate(compareMessage.value, finalContent)
  compareVisible.value = false
}

async function rejectCompare(): Promise<void> {
  if (compareMessage.value) {
    await session.rejectCandidate(compareMessage.value)
  }
  compareVisible.value = false
}

function openPendingCompare(): void {
  if (pendingCandidateMessage.value) {
    openCompare(pendingCandidateMessage.value)
  }
}

function handleDiffAccept(finalContent: unknown): void {
  const message = pendingCandidateMessage.value
  if (message) {
    void session.applyCandidate(message, finalContent)
  }
}

function handleDiffReject(): void {
  const message = pendingCandidateMessage.value
  if (message) {
    void session.rejectCandidate(message)
  }
}

function handleJobChange(id: number | null): void {
  session.selectJob(id)
}

function handleTemplate(templateId: string): void {
  store.content.preview.templateId = templateId
  store.content.preview.templateName = loadTemplateById(templateId)?.name ?? null
  store.touch()
  galleryVisible.value = false
  ElMessage.success('已切换模板，预览已更新')
}

function handleVersionSelect(versionId: number): void {
  historyVisible.value = false
  void session.switchVersion(versionId)
}

function handleVersionExport(versionId: number): void {
  void session.exportFile('DOCX', versionId)
}
</script>

<template>
  <div class="workspace">
    <WorkspaceToolbar
      :mode="mode"
      v-model:draftName="draftName"
      :progress="progress"
      :generating="generating || applying"
      :exporting="exporting"
      :can-undo="canUndo"
      :can-export="canExport"
      :version-label="versionLabel"
      @back="session.exit"
      @generate="session.generate"
      @undo="session.undoLast"
      @export="session.exportFile"
      @templates="galleryVisible = true"
      @history="historyVisible = true"
      @form-mode="session.switchToFormMode"
    />

    <div v-if="compact" class="workspace__tabs">
      <button
        v-for="item in tabs"
        :key="item.key"
        type="button"
        class="workspace__tab"
        :class="{ 'is-active': tab === item.key }"
        @click="tab = item.key"
      >
        <el-icon><component :is="item.icon" /></el-icon>
        {{ item.label }}
      </button>
    </div>

    <div v-if="initError" class="workspace__error">
      <h3>工作区加载失败</h3>
      <p>{{ initError }}</p>
      <el-button type="primary" @click="session.retryBoot">重新加载</el-button>
    </div>

    <!-- 编辑模式：三栏分析编辑工作区 -->
    <div
      v-else-if="editMode"
      class="workspace__body workspace__body--edit"
      :class="{ 'workspace__body--compact': compact }"
    >
      <div
        v-show="!compact || tab === 'sections'"
        class="workspace__pane workspace__pane--nav"
      >
        <WorkspaceSectionNav
          :sections="sectionNavItems"
          :active-key="activeSection"
          :analyzing="sending"
          @select="handleSelectSection"
          @analyze="session.analyzeCurrentSection"
        />
      </div>

      <div
        v-show="!compact || tab === 'preview'"
        class="workspace__pane workspace__pane--preview"
      >
        <div class="workspace__preview-stack">
          <WorkspacePreviewPanel
            :content="previewContent"
            :resume-name="previewName"
            :template-id="store.content.preview.templateId"
            :active-section="activeSection"
            :loading="!ready"
            :empty="previewEmpty"
            @select-section="handleSelectSection"
          />
          <WorkspaceDiffPanel
            :candidate="pendingCandidate"
            :busy="applying"
            @accept="handleDiffAccept"
            @reject="handleDiffReject"
            @regenerate="session.regenerateCandidate"
            @edit="openPendingCompare"
          />
        </div>
      </div>

      <div v-show="!compact || tab === 'ai'" class="workspace__pane workspace__pane--side">
        <WorkspaceSidePanel
          :messages="messages"
          :actions="WORKSPACE_ACTIONS"
          :sending="sending"
          :applying="applying"
          :active-section="activeSection"
          :job-id="jobId"
          :job-options="jobOptions"
          :job-loading="jobLoading"
          :edit-history="editHistory"
          :history-loading="historyLoading"
          :can-undo="canUndo"
          @send="session.send"
          @action="session.runAction"
          @update:job-id="handleJobChange"
          @compare="openCompare"
          @apply="session.applyCandidate"
          @reject="session.rejectCandidate"
          @undo="session.undoLast"
          @refresh-history="session.loadEditHistory"
        />
      </div>
    </div>

    <!-- 生成模式：两栏，左侧对话 + 八步表单，右侧预览 -->
    <div v-else class="workspace__body" :class="{ 'workspace__body--compact': compact }">
      <div v-show="!compact || tab === 'chat'" class="workspace__pane workspace__pane--chat">
        <WorkspaceChatPanel
          :messages="messages"
          :sending="sending"
          :mode="mode"
          :active-section="activeSection"
          :placeholder="placeholder"
          :suggestions="suggestions"
          @send="session.send"
          @compare="openCompare"
          @apply="session.applyCandidate"
          @reject="session.rejectCandidate"
        >
          <template #form>
            <WorkspaceStepForm
              v-model:open="formOpen"
              :step="store.currentStep"
              :total="store.totalSteps"
              :mode="mode"
              :section-key="activeSection"
              :busy="sending || applying"
              @prev="session.prevStep"
              @next="session.nextStep"
              @analyze="session.analyzeCurrentSection"
              @rewrite="session.rewriteActiveSection"
              @form-mode="session.switchToFormMode"
            />
          </template>
        </WorkspaceChatPanel>
      </div>

      <div v-show="!compact || tab === 'preview'" class="workspace__pane workspace__pane--preview">
        <WorkspacePreviewPanel
          :content="previewContent"
          :resume-name="previewName"
          :template-id="store.content.preview.templateId"
          :active-section="activeSection"
          :loading="!ready"
          :empty="previewEmpty"
          @select-section="handleSelectSection"
        />
      </div>
    </div>

    <TemplateGalleryDialog
      v-model="galleryVisible"
      mode="switch"
      :target-position="store.content.direction.targetPosition"
      :current-template-id="store.content.preview.templateId"
      @confirm="handleTemplate"
    />

    <VersionHistoryDrawer
      v-model="historyVisible"
      :versions="versions"
      :active-version-id="activeVersionId"
      @select="handleVersionSelect"
      @export="handleVersionExport"
    />

    <CandidateDiffDialog
      v-model="compareVisible"
      :history="compareMessage?.candidate ?? null"
      :busy="applying"
      @confirm="confirmCompare"
      @reject="rejectCompare"
    />
  </div>
</template>

<style scoped>
.workspace {
  display: flex;
  flex-direction: column;
  height: 100vh;
  overflow: hidden;
  background: var(--cf-bg);
}

.workspace__tabs {
  display: flex;
  flex-shrink: 0;
  gap: var(--cf-space-1);
  padding: var(--cf-space-1) var(--cf-space-2);
  background: var(--cf-card-bg);
  border-bottom: 1px solid var(--cf-border);
}

.workspace__tab {
  flex: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 7px 12px;
  border: none;
  border-radius: var(--cf-radius-sm);
  background: transparent;
  font-family: inherit;
  font-size: var(--cf-font-sm);
  color: var(--cf-text-secondary);
  cursor: pointer;
}

.workspace__tab.is-active {
  background: var(--cf-primary-soft);
  color: var(--cf-primary);
  font-weight: 600;
}

.workspace__body {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(380px, 40%) minmax(0, 1fr);
}

.workspace__body--edit {
  grid-template-columns: minmax(200px, 20%) minmax(480px, 50%) minmax(320px, 30%);
}

.workspace__body--compact {
  grid-template-columns: minmax(0, 1fr);
}

.workspace__pane {
  min-width: 0;
  min-height: 0;
  height: 100%;
}

.workspace__preview-stack {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.workspace__preview-stack > :first-child {
  flex: 1;
  min-height: 0;
}

.workspace__error {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--cf-space-2);
  color: var(--cf-text-secondary);
}

.workspace__error h3 {
  margin: 0;
  color: var(--cf-text);
}

.workspace__error p {
  margin: 0;
  font-size: var(--cf-font-sm);
}

@media (max-width: 1439px) {
  .workspace__body--edit {
    grid-template-columns: 190px minmax(0, 1fr) 320px;
  }
}
</style>
