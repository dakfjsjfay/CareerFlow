<template>
  <div v-loading="loading" class="resume-page">
    <div v-if="resumes.length === 0" class="empty-state">
      <h2>还没有简历</h2>
      <p>从已确认的个人档案生成第一份结构化简历，之后可以随时切换模板、编辑内容、导出 PDF / DOCX。</p>
      <el-button type="primary" data-testid="create-resume" @click="handleCreate">
        创建我的第一份简历
      </el-button>
    </div>

    <template v-else-if="active">
      <div class="toolbar">
        <div class="toolbar-left">
          <span class="resume-name">{{ active.resumeName }}</span>
          <el-select
            v-if="versions.length > 0"
            :model-value="activeVersion?.versionId ?? undefined"
            size="small"
            class="version-select"
            @change="(value) => handleSelectVersion(Number(value))"
          >
            <el-option
              v-for="version in versions"
              :key="version.versionId"
              :label="`v${version.versionNumber} · ${reasonLabel(version.generationReason)}`"
              :value="version.versionId"
            />
          </el-select>
        </div>
        <div class="toolbar-actions">
          <el-button size="small" data-testid="open-templates" @click="openSwitch">
            模板库
          </el-button>
          <el-button size="small" data-testid="open-history" @click="historyVisible = true">
            版本历史
          </el-button>
          <el-button
            size="small"
            type="primary"
            data-testid="ai-generate"
            :loading="generating"
            @click="openGenerate"
          >
            AI 生成
          </el-button>
          <el-button
            size="small"
            data-testid="save-version"
            :loading="saving"
            :disabled="!editingContent"
            @click="handleSave"
          >
            保存新版本
          </el-button>
          <el-button
            size="small"
            data-testid="export-pdf"
            :disabled="!activeVersion"
            @click="handleExportPdf"
          >
            导出 PDF
          </el-button>
          <el-button
            size="small"
            data-testid="export-docx"
            :disabled="!activeVersion"
            @click="handleExportDocx()"
          >
            导出 DOCX
          </el-button>
        </div>
      </div>

      <div v-if="!activeVersion || !editingContent" class="empty-version">
        <h3>还没有生成简历版本</h3>
        <p>选择一套模板，AI 会根据已确认档案生成第一版。</p>
        <el-button type="primary" data-testid="first-generate" @click="openGenerate">
          选择模板并生成
        </el-button>
      </div>

      <div v-else class="workspace">
        <div class="editor-pane">
          <ResumeEditor :content="editingContent" />
        </div>
        <div class="preview-pane">
          <div id="resume-print-area" class="preview-canvas">
            <ResumeTemplateRenderer
              v-if="previewTemplate"
              :template="previewTemplate"
              :content="editingContent"
              :resume-name="active.resumeName"
            />
            <el-empty v-else description="模板加载失败，请重新选择模板" />
          </div>
        </div>
      </div>

      <TemplateGalleryDialog
        v-model="galleryVisible"
        :mode="galleryMode"
        :target-position="targetPosition"
        :current-template-id="activeTemplateId"
        @confirm="handleConfirmTemplate"
      />
      <VersionHistoryDrawer
        v-model="historyVisible"
        :versions="versions"
        :active-version-id="activeVersion?.versionId ?? null"
        @select="handleSelectVersion"
        @export="handleExportDocx"
      />
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'

import ResumeEditor from '@/components/resume/ResumeEditor.vue'
import TemplateGalleryDialog from '@/components/resume/TemplateGalleryDialog.vue'
import VersionHistoryDrawer from '@/components/resume/VersionHistoryDrawer.vue'
import { loadTemplateById } from '@/components/resume-template/loadTemplate'
import ResumeTemplateRenderer from '@/components/resume-template/ResumeTemplateRenderer.vue'
import { useResume } from '@/composables/useResume'
import { printResume } from '@/utils/exportPdf'

const {
  resumes,
  active,
  versions,
  activeVersion,
  editingContent,
  loading,
  generating,
  saving,
  loadResumes,
  create,
  selectVersion,
  generate,
  saveVersion,
  exportDocx,
} = useResume()

const galleryVisible = ref(false)
const galleryMode = ref<'generate' | 'switch'>('generate')
const historyVisible = ref(false)

const activeTemplateId = computed(
  () => activeVersion.value?.templateId ?? editingContent.value?.meta?.templateId ?? null,
)
const previewTemplate = computed(() => loadTemplateById(activeTemplateId.value))
const targetPosition = computed(
  () => editingContent.value?.jobIntention.targetPosition ?? null,
)

const REASON_LABELS: Record<string, string> = {
  INITIAL: '首次生成',
  JOB_TARGETED: '按岗位生成',
  OPTIMIZATION: '反向优化',
  REGENERATE: '重新生成',
  MANUAL_EDIT: '手动编辑',
  TEMPLATE_SWITCH: '切换模板',
  RESTORE: '恢复旧版本',
}

function reasonLabel(reason: string): string {
  return REASON_LABELS[reason] ?? reason
}

function errorMessage(error: unknown, fallback: string): string {
  const err = error as { response?: { data?: { message?: string } } }
  return err.response?.data?.message || (error instanceof Error ? error.message : '') || fallback
}

async function loadPage(): Promise<void> {
  try {
    await loadResumes()
  } catch (error) {
    ElMessage.error(errorMessage(error, '简历加载失败，请稍后重试'))
  }
}

async function handleCreate(): Promise<void> {
  try {
    await create('我的简历')
    ElMessage.success('简历已创建，请选择模板生成第一版')
  } catch (error) {
    ElMessage.error(errorMessage(error, '创建简历失败'))
  }
}

function openGenerate(): void {
  galleryMode.value = 'generate'
  galleryVisible.value = true
}

function openSwitch(): void {
  galleryMode.value = 'switch'
  galleryVisible.value = true
}

async function handleConfirmTemplate(templateId: string): Promise<void> {
  try {
    if (galleryMode.value === 'generate') {
      await generate(templateId, 'INITIAL')
      ElMessage.success('已生成新版本')
    } else {
      await saveVersion('TEMPLATE_SWITCH', templateId)
      ElMessage.success('已应用新模板，旧版本保持不变')
    }
  } catch (error) {
    ElMessage.error(
      errorMessage(error, galleryMode.value === 'generate' ? '生成失败' : '切换模板失败'),
    )
  }
}

async function handleSave(): Promise<void> {
  try {
    await saveVersion('MANUAL_EDIT')
    ElMessage.success('已保存为新版本')
  } catch (error) {
    ElMessage.error(errorMessage(error, '保存失败'))
  }
}

async function handleSelectVersion(versionId: number): Promise<void> {
  try {
    await selectVersion(versionId)
  } catch (error) {
    ElMessage.error(errorMessage(error, '版本加载失败'))
  }
}

async function handleExportDocx(versionId?: number): Promise<void> {
  const target = versionId ?? activeVersion.value?.versionId
  if (!target) {
    return
  }
  try {
    await exportDocx(target)
  } catch (error) {
    ElMessage.error(errorMessage(error, 'DOCX 导出失败'))
  }
}

function handleExportPdf(): void {
  if (!active.value || !activeVersion.value) {
    return
  }
  printResume(`${active.value.resumeName}-v${activeVersion.value.versionNumber}`)
}

onMounted(loadPage)
</script>

<style scoped>
.resume-page {
  min-height: 100%;
}

.empty-state,
.empty-version {
  background: var(--cf-card-bg);
  border: 1px solid var(--cf-border);
  border-radius: 12px;
  padding: 64px 24px;
  text-align: center;
}

.empty-state h2,
.empty-version h3 {
  margin-bottom: 10px;
  color: var(--cf-text);
}

.empty-state p,
.empty-version p {
  color: var(--cf-text-secondary);
  font-size: 14px;
  margin-bottom: 20px;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 16px;
  background: var(--cf-card-bg);
  border: 1px solid var(--cf-border);
  border-radius: 10px;
  margin-bottom: 16px;
  position: sticky;
  top: 0;
  z-index: 5;
}

.toolbar-left,
.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.resume-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--cf-text);
}

.version-select {
  width: 190px;
}

.workspace {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 880px;
  gap: 16px;
  align-items: start;
}

.editor-pane {
  min-width: 0;
}

.preview-pane {
  position: sticky;
  top: 76px;
  max-height: calc(100vh - 96px);
  overflow: auto;
  background: #eef2f7;
  border: 1px solid var(--cf-border);
  border-radius: 10px;
  padding: 16px;
}

.preview-canvas {
  width: 820px;
  margin: 0 auto;
  background: #fff;
  box-shadow: 0 18px 40px rgb(15 23 42 / 12%);
}

@media (max-width: 1500px) {
  .workspace {
    grid-template-columns: minmax(0, 1fr);
  }

  .preview-pane {
    position: static;
    max-height: none;
  }
}
</style>
