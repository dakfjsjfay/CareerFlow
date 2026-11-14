<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Clock, CopyDocument, Delete, Document, Edit } from '@element-plus/icons-vue'

import {
  createResume,
  deleteResume,
  exportResumeDocx,
  getResume,
  listResumeVersions,
  listResumes,
  saveResumeVersion,
  type ResumeSummary,
  type ResumeVersionSummary,
} from '@/api/resume'
import { loadTemplateById } from '@/components/resume-template/loadTemplate'
import VersionHistoryDrawer from '@/components/resume/VersionHistoryDrawer.vue'
import { downloadBlob } from '@/composables/useResume'
import { DEFAULT_TEMPLATE_ID } from '@/utils/resumeWizard'

/**
 * 简历中心：只做列表与入口管理，具体编辑统一进入左右分屏工作区。
 */
const router = useRouter()

const resumes = ref<ResumeSummary[]>([])
const loading = ref(false)
const busyId = ref<number | null>(null)

const historyVisible = ref(false)
const historyResumeId = ref<number | null>(null)
const historyVersions = ref<ResumeVersionSummary[]>([])
const historyActiveId = ref<number | null>(null)

const isEmpty = computed(() => !loading.value && resumes.value.length === 0)

function errorMessage(error: unknown, fallback: string): string {
  const err = error as { response?: { status?: number; data?: { message?: string } } }
  if (err?.response?.status === 409) {
    return err.response.data?.message || '该简历已被投递记录引用，无法删除'
  }
  return err?.response?.data?.message || (error instanceof Error ? error.message : '') || fallback
}

function templateName(templateId: string | null): string {
  if (!templateId) {
    return '未选择模板'
  }
  return loadTemplateById(templateId)?.name ?? '自定义模板'
}

function formatTime(value: string | null): string {
  if (!value) {
    return '—'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  const diff = Date.now() - date.getTime()
  const minute = 60 * 1000
  if (diff < minute) {
    return '刚刚'
  }
  if (diff < 60 * minute) {
    return `${Math.floor(diff / minute)} 分钟前`
  }
  if (diff < 24 * 60 * minute) {
    return `${Math.floor(diff / (60 * minute))} 小时前`
  }
  return date.toLocaleDateString('zh-CN')
}

async function load(): Promise<void> {
  loading.value = true
  try {
    resumes.value = (await listResumes()).data
  } catch (error) {
    ElMessage.error(errorMessage(error, '简历加载失败，请稍后重试'))
  } finally {
    loading.value = false
  }
}

function goCreate(): void {
  void router.push('/workspace/new')
}

function goEdit(resume: ResumeSummary): void {
  void router.push(`/workspace/${resume.id}`)
}

async function handleDelete(resume: ResumeSummary): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `删除后该简历及其全部版本都无法恢复，确定删除「${resume.resumeName}」吗？`,
      '删除简历',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  busyId.value = resume.id
  try {
    await deleteResume(resume.id)
    ElMessage.success('简历已删除')
    await load()
  } catch (error) {
    ElMessage.error(errorMessage(error, '删除失败'))
  } finally {
    busyId.value = null
  }
}

async function handleCopy(resume: ResumeSummary): Promise<void> {
  busyId.value = resume.id
  try {
    const detail = (await getResume(resume.id)).data
    if (!detail.currentVersion) {
      ElMessage.warning('该简历还没有生成版本，暂不支持复制')
      return
    }
    const created = (
      await createResume({ resumeName: `${resume.resumeName} 副本` })
    ).data
    await saveResumeVersion(created.id, {
      content: detail.currentVersion.content,
      templateId: detail.currentVersion.templateId || DEFAULT_TEMPLATE_ID,
      generationReason: 'MANUAL_EDIT',
    })
    ElMessage.success('已复制为新简历，历史版本不会被修改')
    await load()
  } catch (error) {
    ElMessage.error(errorMessage(error, '复制失败'))
  } finally {
    busyId.value = null
  }
}

async function openHistory(resume: ResumeSummary): Promise<void> {
  historyResumeId.value = resume.id
  historyActiveId.value = resume.currentVersionId
  historyVisible.value = true
  try {
    historyVersions.value = (await listResumeVersions(resume.id)).data
  } catch (error) {
    historyVersions.value = []
    ElMessage.error(errorMessage(error, '版本加载失败'))
  }
}

function handleVersionSelect(versionId: number): void {
  historyVisible.value = false
  if (historyResumeId.value == null) {
    return
  }
  void router.push({
    path: `/workspace/${historyResumeId.value}`,
    query: { versionId: String(versionId) },
  })
}

async function handleVersionExport(versionId: number): Promise<void> {
  if (historyResumeId.value == null) {
    return
  }
  try {
    const result = await exportResumeDocx(historyResumeId.value, versionId)
    downloadBlob(result.blob, result.filename)
  } catch (error) {
    ElMessage.error(errorMessage(error, 'DOCX 导出失败'))
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="resume-center">
    <header class="center-head">
      <div>
        <h1>简历中心</h1>
        <p>所有简历都按版本独立保存，编辑与生成统一在左右分屏工作区完成。</p>
      </div>
      <el-button type="primary" data-testid="create-resume" @click="goCreate">
        <el-icon><Document /></el-icon>
        <span>新建简历</span>
      </el-button>
    </header>

    <div v-if="isEmpty" class="empty-state">
      <el-icon class="empty-state__icon"><Document /></el-icon>
      <h2>还没有简历</h2>
      <p>进入简历工作区，通过左侧 AI 引导补全信息，右侧实时生成可导出的简历。</p>
      <el-button type="primary" @click="goCreate">创建我的第一份简历</el-button>
    </div>

    <div v-else class="resume-grid">
      <article v-for="resume in resumes" :key="resume.id" class="resume-card">
        <div class="resume-card__head">
          <h3 class="resume-card__name" :title="resume.resumeName">{{ resume.resumeName }}</h3>
          <el-tag v-if="resume.currentVersionNumber" size="small" effect="plain">
            v{{ resume.currentVersionNumber }}
          </el-tag>
          <el-tag v-else size="small" type="info" effect="plain">草稿中</el-tag>
        </div>

        <dl class="resume-card__meta">
          <div>
            <dt>模板</dt>
            <dd>{{ templateName(resume.templateId) }}</dd>
          </div>
          <div>
            <dt>最近更新</dt>
            <dd>{{ formatTime(resume.updatedAt) }}</dd>
          </div>
        </dl>

        <div class="resume-card__actions">
          <el-button
            type="primary"
            plain
            size="small"
            data-testid="resume-edit"
            @click="goEdit(resume)"
          >
            <el-icon><Edit /></el-icon>
            <span>编辑简历</span>
          </el-button>
          <el-button size="small" data-testid="resume-history" @click="openHistory(resume)">
            <el-icon><Clock /></el-icon>
            <span>版本历史</span>
          </el-button>
          <el-tooltip content="基于当前版本复制一份新简历" placement="top">
            <el-button
              size="small"
              data-testid="resume-copy"
              :loading="busyId === resume.id"
              @click="handleCopy(resume)"
            >
              <el-icon><CopyDocument /></el-icon>
            </el-button>
          </el-tooltip>
          <el-tooltip content="删除简历及全部版本" placement="top">
            <el-button
              size="small"
              type="danger"
              plain
              data-testid="resume-delete"
              :loading="busyId === resume.id"
              @click="handleDelete(resume)"
            >
              <el-icon><Delete /></el-icon>
            </el-button>
          </el-tooltip>
        </div>
      </article>
    </div>

    <VersionHistoryDrawer
      v-model="historyVisible"
      :versions="historyVersions"
      :active-version-id="historyActiveId"
      @select="handleVersionSelect"
      @export="handleVersionExport"
    />
  </div>
</template>

<style scoped>
.resume-center {
  display: flex;
  flex-direction: column;
  gap: var(--cf-space-5);
  min-height: 100%;
}

.center-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--cf-space-4);
}

.center-head h1 {
  margin: 0;
  font-size: var(--cf-font-2xl);
  color: var(--cf-text);
}

.center-head p {
  margin: var(--cf-space-1) 0 0;
  font-size: var(--cf-font-sm);
  color: var(--cf-text-secondary);
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--cf-space-2);
  padding: 72px var(--cf-space-5);
  background: var(--cf-card-bg);
  border: 1px solid var(--cf-border);
  border-radius: var(--cf-radius-lg);
  text-align: center;
}

.empty-state__icon {
  font-size: 40px;
  color: var(--cf-border-strong);
}

.empty-state h2 {
  margin: 0;
  font-size: var(--cf-font-lg);
  color: var(--cf-text);
}

.empty-state p {
  margin: 0 0 var(--cf-space-2);
  max-width: 460px;
  font-size: var(--cf-font-sm);
  color: var(--cf-text-secondary);
  line-height: 1.7;
}

.resume-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: var(--cf-space-4);
}

.resume-card {
  display: flex;
  flex-direction: column;
  gap: var(--cf-space-3);
  padding: var(--cf-space-4);
  background: var(--cf-card-bg);
  border: 1px solid var(--cf-border);
  border-radius: var(--cf-radius-lg);
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.resume-card:hover {
  border-color: var(--cf-primary-light);
  box-shadow: var(--cf-shadow-sm);
}

.resume-card__head {
  display: flex;
  align-items: center;
  gap: var(--cf-space-2);
}

.resume-card__name {
  margin: 0;
  flex: 1;
  min-width: 0;
  font-size: var(--cf-font-md);
  font-weight: 600;
  color: var(--cf-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.resume-card__meta {
  display: flex;
  gap: var(--cf-space-5);
  margin: 0;
}

.resume-card__meta div {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.resume-card__meta dt {
  font-size: var(--cf-font-xs);
  color: var(--cf-text-tertiary);
}

.resume-card__meta dd {
  margin: 0;
  font-size: var(--cf-font-sm);
  color: var(--cf-text-secondary);
}

.resume-card__actions {
  display: flex;
  align-items: center;
  gap: var(--cf-space-2);
  padding-top: var(--cf-space-3);
  border-top: 1px solid var(--cf-border);
}

.resume-card__actions .el-button:first-child {
  flex: 1;
}

@media (max-width: 640px) {
  .center-head {
    flex-direction: column;
  }
}
</style>
