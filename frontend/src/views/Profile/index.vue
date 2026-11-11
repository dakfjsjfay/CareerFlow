<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Edit, Plus } from '@element-plus/icons-vue'

import type {
  ExperiencePayload,
  ProfileExperience,
  ProfileProject,
  ProfileUpdatePayload,
  ProjectPayload,
} from '@/api/profile'
import CompletenessRadar from '@/components/CompletenessRadar.vue'
import DocumentAnalyzer from '@/components/DocumentAnalyzer.vue'
import EvidenceConfirmDialog from '@/components/EvidenceConfirmDialog.vue'
import ExperienceDialog from '@/components/ExperienceDialog.vue'
import ProfileInfoForm from '@/components/ProfileInfoForm.vue'
import ProjectDialog from '@/components/ProjectDialog.vue'
import { useEvidenceConfirm } from '@/composables/useEvidenceConfirm'
import { useProfile } from '@/composables/useProfile'

const {
  profile,
  completeness,
  loading,
  saving,
  load,
  saveProfile,
  saveProject,
  removeProject,
  saveExperience,
  removeExperience,
} = useProfile()

const {
  visible: evidenceVisible,
  submitting: evidenceSubmitting,
  groups: evidenceGroups,
  pendingCount,
  refresh: refreshEvidence,
  open: openEvidence,
  confirmSelected: confirmSelectedEvidence,
  rejectSelected: rejectSelectedEvidence,
} = useEvidenceConfirm()

const projectDialogVisible = ref(false)
const editingProject = ref<ProfileProject | null>(null)
const experienceDialogVisible = ref(false)
const editingExperience = ref<ProfileExperience | null>(null)

const projects = computed(() => profile.value?.projects ?? [])
const experiences = computed(() => profile.value?.experiences ?? [])
const dimensions = computed(() => completeness.value?.dimensions ?? [])
const overall = computed(() => completeness.value?.overall ?? 0)

function errorMessage(error: unknown, fallback: string): string {
  const err = error as { response?: { data?: { message?: string } } }
  return err.response?.data?.message || fallback
}

function formatMonth(value: string | null): string {
  if (!value) {
    return ''
  }
  const [year, month] = value.split('-')
  return `${year}.${month}`
}

function formatRange(start: string | null, end: string | null): string {
  if (!start && !end) {
    return ''
  }
  return `${formatMonth(start) || '未知'} - ${end ? formatMonth(end) : '至今'}`
}

async function loadPage(): Promise<void> {
  try {
    await load()
  } catch (error) {
    ElMessage.error(errorMessage(error, '档案加载失败，请稍后重试'))
  }
}

async function loadPendingCount(): Promise<void> {
  try {
    await refreshEvidence()
  } catch {
    // 待确认数量是辅助信息，不影响档案主流程
  }
}

onMounted(() => {
  loadPage()
  loadPendingCount()
})

async function handleSaveProfile(payload: ProfileUpdatePayload): Promise<void> {
  try {
    await saveProfile(payload)
    ElMessage.success('档案已保存')
  } catch (error) {
    ElMessage.error(errorMessage(error, '档案保存失败'))
  }
}

function openAddProject(): void {
  editingProject.value = null
  projectDialogVisible.value = true
}

function openEditProject(project: ProfileProject): void {
  editingProject.value = project
  projectDialogVisible.value = true
}

async function handleSaveProject(payload: ProjectPayload): Promise<void> {
  try {
    await saveProject(editingProject.value?.id ?? null, payload)
    projectDialogVisible.value = false
    ElMessage.success('项目经历已保存')
  } catch (error) {
    ElMessage.error(errorMessage(error, '项目经历保存失败'))
  }
}

async function handleDeleteProject(project: ProfileProject): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确定删除项目「${project.projectName}」吗？相关证据也会一并删除。`,
      '删除项目',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await removeProject(project.id)
    ElMessage.success('项目经历已删除')
  } catch (error) {
    ElMessage.error(errorMessage(error, '项目经历删除失败'))
  }
}

function openAddExperience(): void {
  editingExperience.value = null
  experienceDialogVisible.value = true
}

function openEditExperience(experience: ProfileExperience): void {
  editingExperience.value = experience
  experienceDialogVisible.value = true
}

async function handleSaveExperience(payload: ExperiencePayload): Promise<void> {
  try {
    await saveExperience(editingExperience.value?.id ?? null, payload)
    experienceDialogVisible.value = false
    ElMessage.success('工作/实习经历已保存')
  } catch (error) {
    ElMessage.error(errorMessage(error, '工作/实习经历保存失败'))
  }
}

async function handleDeleteExperience(experience: ProfileExperience): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确定删除「${experience.company}」这段经历吗？相关证据也会一并删除。`,
      '删除经历',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await removeExperience(experience.id)
    ElMessage.success('工作/实习经历已删除')
  } catch (error) {
    ElMessage.error(errorMessage(error, '工作/实习经历删除失败'))
  }
}

async function handleOpenEvidence(): Promise<void> {
  try {
    await openEvidence()
  } catch (error) {
    ElMessage.error(errorMessage(error, '待确认信息加载失败'))
  }
}

async function handleEvidenceConfirm(ids: number[]): Promise<void> {
  try {
    const result = await confirmSelectedEvidence(ids)
    ElMessage.success(`已确认 ${result.confirmedCount} 条信息`)
    await load()
  } catch (error) {
    ElMessage.error(errorMessage(error, '确认失败，请刷新后重试'))
  }
}

async function handleEvidenceReject(ids: number[]): Promise<void> {
  try {
    await rejectSelectedEvidence(ids)
    ElMessage.success('已忽略选中信息')
  } catch (error) {
    ElMessage.error(errorMessage(error, '操作失败，请刷新后重试'))
  }
}
</script>

<template>
  <div class="profile-page">
    <header class="page-head">
      <div>
        <p class="eyebrow">PROFILE</p>
        <h2>个人档案</h2>
        <p class="subtitle">
          对话抽取与手动填写都会形成可追溯证据；只有确认后的信息才会写入档案，并用于后续简历生成。
        </p>
      </div>
      <div v-if="pendingCount > 0" class="head-meta">
        <span class="pending-dot" />
        <span>待确认 {{ pendingCount }} 条</span>
        <el-button link type="primary" data-testid="open-evidence" @click="handleOpenEvidence">
          去确认
        </el-button>
      </div>
    </header>

    <div class="profile-grid">
      <main class="main-col">
        <el-card v-loading="loading" shadow="never" class="card">
          <template #header>
            <div class="card-head">
              <h3>档案信息</h3>
              <span class="card-hint">只展示已确认数据</span>
            </div>
          </template>
          <ProfileInfoForm :profile="profile" :saving="saving" @save="handleSaveProfile" />
        </el-card>

        <el-card shadow="never" class="card">
          <template #header>
            <div class="card-head">
              <h3>
                项目经历
                <span class="count">{{ projects.length }}</span>
              </h3>
              <el-button
                type="primary"
                plain
                :icon="Plus"
                data-testid="add-project"
                @click="openAddProject"
              >
                添加项目
              </el-button>
            </div>
          </template>

          <p v-if="!projects.length" class="empty">
            还没有项目经历。写下做过的项目和技术栈，项目维度完整度会随之提升。
          </p>
          <article v-for="project in projects" :key="project.id" class="entity-row">
            <div class="entity-main">
              <div class="entity-title-row">
                <h4>{{ project.projectName }}</h4>
                <span v-if="project.role" class="entity-role">{{ project.role }}</span>
                <span class="entity-dates">
                  {{ formatRange(project.startDate, project.endDate) }}
                </span>
              </div>
              <p v-if="project.description" class="entity-desc">{{ project.description }}</p>
              <div v-if="project.techStack?.length" class="tags">
                <el-tag
                  v-for="tech in project.techStack"
                  :key="tech"
                  size="small"
                  effect="plain"
                >
                  {{ tech }}
                </el-tag>
              </div>
            </div>
            <div class="entity-actions">
              <el-button
                link
                type="primary"
                :icon="Edit"
                :data-testid="`edit-project-${project.id}`"
                @click="openEditProject(project)"
              >
                编辑
              </el-button>
              <el-button
                link
                type="danger"
                :icon="Delete"
                :data-testid="`delete-project-${project.id}`"
                @click="handleDeleteProject(project)"
              >
                删除
              </el-button>
            </div>
          </article>
        </el-card>

        <el-card shadow="never" class="card">
          <template #header>
            <div class="card-head">
              <h3>
                工作/实习经历
                <span class="count">{{ experiences.length }}</span>
              </h3>
              <el-button
                type="primary"
                plain
                :icon="Plus"
                data-testid="add-experience"
                @click="openAddExperience"
              >
                添加经历
              </el-button>
            </div>
          </template>

          <p v-if="!experiences.length" class="empty">
            还没有工作/实习经历。确实没有相关经历时，也可以在对话中明确说明“目前没有”。
          </p>
          <article
            v-for="experience in experiences"
            :key="experience.id"
            class="entity-row"
          >
            <div class="entity-main">
              <div class="entity-title-row">
                <h4>{{ experience.company }}</h4>
                <span v-if="experience.position" class="entity-role">
                  {{ experience.position }}
                </span>
                <span class="entity-dates">
                  {{ formatRange(experience.startDate, experience.endDate) }}
                </span>
              </div>
              <p v-if="experience.description" class="entity-desc">
                {{ experience.description }}
              </p>
              <p v-if="experience.achievements" class="entity-desc entity-achievement">
                成果：{{ experience.achievements }}
              </p>
            </div>
            <div class="entity-actions">
              <el-button
                link
                type="primary"
                :icon="Edit"
                :data-testid="`edit-experience-${experience.id}`"
                @click="openEditExperience(experience)"
              >
                编辑
              </el-button>
              <el-button
                link
                type="danger"
                :icon="Delete"
                :data-testid="`delete-experience-${experience.id}`"
                @click="handleDeleteExperience(experience)"
              >
                删除
              </el-button>
            </div>
          </article>
        </el-card>

        <el-collapse class="import-collapse">
          <el-collapse-item name="import" title="从资料导入（可选）">
            <p class="import-hint">
              上传简历、项目资料或代码包，AI 会生成候选信息，但不会自动写入档案。
              候选信息需要在对话中确认为证据后才会进入档案。
            </p>
            <DocumentAnalyzer compact />
          </el-collapse-item>
        </el-collapse>
      </main>

      <aside class="side-col">
        <el-card shadow="never" class="card radar-card">
          <CompletenessRadar :overall="overall" :dimensions="dimensions" />
          <ul v-if="dimensions.length" class="dimension-list">
            <li v-for="dimension in dimensions" :key="dimension.name">
              <span class="dimension-name">{{ dimension.name }}</span>
              <span class="dimension-bar">
                <i :style="{ width: `${dimension.score}%` }" />
              </span>
              <span class="dimension-score">{{ dimension.score }}</span>
            </li>
          </ul>
        </el-card>

        <el-card shadow="never" class="card tips-card">
          <h3>完善建议</h3>
          <p>
            优先补齐 0 分维度；技能至少填写 3 项，并在对话中确认技能来源，技能维度才会计为满分。
          </p>
        </el-card>
      </aside>
    </div>

    <ProjectDialog
      v-model="projectDialogVisible"
      :project="editingProject"
      :submitting="saving"
      @save="handleSaveProject"
    />
    <ExperienceDialog
      v-model="experienceDialogVisible"
      :experience="editingExperience"
      :submitting="saving"
      @save="handleSaveExperience"
    />
    <EvidenceConfirmDialog
      v-model="evidenceVisible"
      :groups="evidenceGroups"
      :submitting="evidenceSubmitting"
      @confirm="handleEvidenceConfirm"
      @reject="handleEvidenceReject"
    />
  </div>
</template>

<style scoped>
.profile-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.eyebrow {
  margin-bottom: 6px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.12em;
  color: var(--cf-primary);
}

.page-head h2 {
  font-size: 22px;
  font-weight: 650;
  letter-spacing: -0.01em;
}

.subtitle {
  margin-top: 6px;
  max-width: 680px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--cf-text-secondary);
}

.head-meta {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  font-size: 13px;
  color: var(--cf-text-secondary);
  background: #fff;
  border: 1px solid var(--cf-border);
  border-radius: 999px;
}

.pending-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #fab219;
}

.profile-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 18px;
  align-items: start;
}

.main-col {
  display: flex;
  flex-direction: column;
  gap: 18px;
  min-width: 0;
}

.side-col {
  position: sticky;
  top: 16px;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.card {
  border: 1px solid var(--cf-border);
  border-radius: 12px;
}

.card :deep(.el-card__header) {
  padding: 16px 20px;
  border-bottom: 1px solid var(--cf-border);
}

.card :deep(.el-card__body) {
  padding: 20px;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.card-head h3 {
  font-size: 15px;
  font-weight: 600;
  color: var(--cf-text);
}

.count {
  display: inline-flex;
  min-width: 22px;
  height: 22px;
  align-items: center;
  justify-content: center;
  margin-left: 4px;
  padding: 0 7px;
  font-size: 12px;
  font-weight: 500;
  color: var(--cf-text-secondary);
  background: #f1f5f9;
  border-radius: 999px;
}

.card-hint {
  font-size: 12px;
  color: var(--cf-text-secondary);
}

.entity-row {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  padding: 16px 0;
}

.entity-row:first-of-type {
  padding-top: 0;
}

.entity-row + .entity-row {
  border-top: 1px solid var(--cf-border);
}

.entity-main {
  min-width: 0;
}

.entity-title-row {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 8px 10px;
}

.entity-title-row h4 {
  font-size: 14px;
  font-weight: 600;
  color: var(--cf-text);
}

.entity-role {
  padding: 2px 8px;
  font-size: 12px;
  color: var(--cf-primary-dark);
  background: #eff6ff;
  border-radius: 999px;
}

.entity-dates {
  font-size: 12px;
  color: var(--cf-text-secondary);
}

.entity-desc {
  margin-top: 7px;
  font-size: 13px;
  line-height: 1.7;
  color: #374151;
  white-space: pre-wrap;
}

.entity-achievement {
  color: #4b5563;
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 9px;
}

.entity-actions {
  display: flex;
  flex-shrink: 0;
  gap: 2px;
}

.empty {
  padding: 18px 0;
  font-size: 13px;
  line-height: 1.7;
  color: var(--cf-text-secondary);
  text-align: center;
}

.import-collapse {
  padding: 4px 18px;
  background: #fff;
  border: 1px solid var(--cf-border);
  border-radius: 12px;
}

.import-hint {
  margin-bottom: 14px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--cf-text-secondary);
}

.radar-card :deep(.el-card__body) {
  padding-top: 18px;
}

.dimension-list {
  display: flex;
  flex-direction: column;
  gap: 11px;
  padding: 14px 0 0;
  margin: 8px 0 0;
  list-style: none;
  border-top: 1px solid var(--cf-border);
}

.dimension-list li {
  display: grid;
  grid-template-columns: 1fr 72px 28px;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}

.dimension-name {
  color: #374151;
}

.dimension-bar {
  height: 6px;
  overflow: hidden;
  background: #eef2f7;
  border-radius: 999px;
}

.dimension-bar i {
  display: block;
  height: 100%;
  background: var(--cf-primary);
  border-radius: 999px;
}

.dimension-score {
  color: var(--cf-text-secondary);
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.tips-card h3 {
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 600;
}

.tips-card p {
  font-size: 12px;
  line-height: 1.8;
  color: var(--cf-text-secondary);
}

@media (max-width: 1080px) {
  .profile-grid {
    grid-template-columns: 1fr;
  }

  .side-col {
    position: static;
  }
}
</style>
