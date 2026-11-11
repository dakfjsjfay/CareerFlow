<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getJob, type JobDetail } from '@/api/jobs'
import { analyzeMatch, type MatchAnalyzeResult } from '@/api/match'
import { listResumeVersions, listResumes, type ResumeSummary, type ResumeVersionSummary } from '@/api/resume'

const route = useRoute()
const router = useRouter()

const jobId = computed(() => Number(route.params.jobId))

const job = ref<JobDetail | null>(null)
const resumes = ref<ResumeSummary[]>([])
const versions = ref<ResumeVersionSummary[]>([])
const selectedResumeId = ref<number | null>(null)
const selectedVersionId = ref<number | null>(null)

const loading = ref(false)
const analyzing = ref(false)
const result = ref<MatchAnalyzeResult | null>(null)

function errorMessage(error: unknown, fallback: string): string {
  const err = error as { response?: { data?: { message?: string } } }
  return err.response?.data?.message || fallback
}

async function loadPage(): Promise<void> {
  loading.value = true
  try {
    const [jobRes, resumeRes] = await Promise.all([getJob(jobId.value), listResumes()])
    job.value = jobRes.data
    resumes.value = resumeRes.data
    if (resumes.value.length > 0) {
      const first = resumes.value[0]
      selectedResumeId.value = first.id
      await loadVersions(first.id)
      // 默认选中当前版本（或最新版本）
      const current = versions.value.find((v) => v.versionId === first.currentVersionId)
      selectedVersionId.value = (current ?? versions.value[0])?.versionId ?? null
    }
  } catch (error) {
    ElMessage.error(errorMessage(error, '页面加载失败，请稍后重试'))
  } finally {
    loading.value = false
  }
}

async function loadVersions(resumeId: number): Promise<void> {
  versions.value = []
  selectedVersionId.value = null
  try {
    const res = await listResumeVersions(resumeId)
    versions.value = res.data
    const resume = resumes.value.find((r) => r.id === resumeId)
    const current = versions.value.find((v) => v.versionId === resume?.currentVersionId)
    selectedVersionId.value = (current ?? versions.value[0])?.versionId ?? null
  } catch (error) {
    ElMessage.error(errorMessage(error, '版本列表加载失败'))
  }
}

function onResumeChange(resumeId: number): void {
  void loadVersions(resumeId)
}

async function handleAnalyze(): Promise<void> {
  if (!selectedVersionId.value) {
    ElMessage.warning('请先选择简历版本')
    return
  }
  analyzing.value = true
  try {
    result.value = (
      await analyzeMatch({ jobId: jobId.value, resumeVersionId: selectedVersionId.value })
    ).data
  } catch (error) {
    ElMessage.error(errorMessage(error, '匹配分析失败，请稍后重试'))
  } finally {
    analyzing.value = false
  }
}

function statusTag(status: string): 'success' | 'warning' | 'danger' | 'info' {
  switch (status) {
    case 'MATCHED':
      return 'success'
    case 'PARTIAL':
      return 'warning'
    case 'MISSING':
      return 'danger'
    default:
      return 'info'
  }
}

function statusLabel(status: string): string {
  switch (status) {
    case 'MATCHED':
      return '已满足'
    case 'PARTIAL':
      return '部分满足'
    case 'MISSING':
      return '未体现'
    default:
      return '待确认'
  }
}

function handleFollowUp(skill: string): void {
  // 步骤 3（反向优化）接入点：将跳转到对话页发起该技能的追问
  ElMessage.info(`「${skill}」的追问补充将在反向优化模块中开放`)
}

onMounted(() => {
  void loadPage()
})
</script>

<template>
  <div v-loading="loading" class="match-page">
    <header class="page-head">
      <div>
        <p class="eyebrow">MATCH</p>
        <h2>匹配分析</h2>
        <p class="subtitle">逐技能对比岗位要求与你的已确认证据，分数可解释——不是黑盒百分比。</p>
      </div>
      <el-button data-testid="back-jobs" @click="router.push('/jobs')">返回岗位中心</el-button>
    </header>

    <el-card v-if="job" shadow="never" class="job-card">
      <div class="job-card-head">
        <div>
          <p class="job-company">{{ job.company || '未注明公司' }}</p>
          <h3 class="job-position">{{ job.position }}</h3>
        </div>
        <div class="job-meta">
          <span v-if="job.city">📍 {{ job.city }}</span>
          <span v-if="job.salary">💰 {{ job.salary }}</span>
          <span>📋 {{ job.requirements.length }} 项技能要求</span>
        </div>
      </div>
    </el-card>

    <el-card v-if="resumes.length > 0" shadow="never" class="select-card">
      <div class="select-row">
        <el-select
          v-model="selectedResumeId"
          data-testid="resume-select"
          placeholder="选择简历"
          class="select-item"
          @change="onResumeChange"
        >
          <el-option v-for="r in resumes" :key="r.id" :label="r.resumeName" :value="r.id" />
        </el-select>
        <el-select
          v-model="selectedVersionId"
          data-testid="version-select"
          placeholder="选择版本"
          class="select-item"
        >
          <el-option
            v-for="v in versions"
            :key="v.versionId"
            :label="`v${v.versionNumber} · ${v.generationReason}`"
            :value="v.versionId"
          />
        </el-select>
        <el-button
          type="primary"
          data-testid="analyze"
          :loading="analyzing"
          :disabled="!selectedVersionId"
          @click="handleAnalyze"
        >
          开始分析
        </el-button>
      </div>
    </el-card>

    <el-empty
      v-else-if="!loading"
      description="还没有简历版本，先去「我的简历」生成一份吧"
    >
      <el-button type="primary" data-testid="goto-resume" @click="router.push('/resume')">
        去生成简历
      </el-button>
    </el-empty>

    <template v-if="result">
      <el-card shadow="never" class="score-card">
        <div class="score-row">
          <div class="score-box" data-testid="match-score">
            <span class="score-number">{{ result.matchScore }}</span>
            <span class="score-unit">分</span>
          </div>
          <div class="score-stats">
            <div class="stat-chip">
              <el-tag type="success" effect="plain">已满足 {{ result.matchedCount }}</el-tag>
              <el-tag type="warning" effect="plain">部分满足 {{ result.partialCount }}</el-tag>
              <el-tag type="danger" effect="plain">未体现 {{ result.missingCount }}</el-tag>
              <el-tag type="info" effect="plain">待确认 {{ result.unconfirmedCount }}</el-tag>
            </div>
            <p class="score-note">
              规则匹配分 = 加权公式 Σ(wᵢ·sᵢ)/Σwᵢ ×100%，不是招聘成功概率
            </p>
          </div>
        </div>
      </el-card>

      <el-card shadow="never" class="detail-card">
        <template #header>
          <div class="card-head">
            <h3>技能矩阵</h3>
            <span class="card-hint">每一项都有证据和依据</span>
          </div>
        </template>

        <div
          v-for="item in result.details"
          :key="item.skill"
          class="detail-row"
          data-testid="detail-row"
        >
          <div class="detail-skill">
            <span class="skill-name">{{ item.skill }}</span>
            <el-tag size="small" :type="statusTag(item.status)" effect="plain">
              {{ statusLabel(item.status) }}
            </el-tag>
          </div>
          <p class="detail-evidence">
            <template v-if="item.evidence">证据：{{ item.evidence }}</template>
            <template v-else>暂无证据</template>
          </p>
          <p v-if="item.suggestion" class="detail-suggestion">建议：{{ item.suggestion }}</p>
          <div v-if="item.status === 'MISSING' || item.status === 'PARTIAL'" class="detail-actions">
            <el-button
              size="small"
              type="primary"
              plain
              data-testid="follow-up"
              @click="handleFollowUp(item.skill)"
            >
              追问补充
            </el-button>
          </div>
        </div>
      </el-card>
    </template>
  </div>
</template>

<style scoped>
.page-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}

.eyebrow {
  font-size: 12px;
  letter-spacing: 2px;
  color: var(--cf-primary);
  font-weight: 700;
  margin-bottom: 4px;
}

.page-head h2 {
  font-size: 22px;
}

.subtitle {
  margin-top: 6px;
  color: var(--cf-text-secondary);
  font-size: 13px;
}

.job-card {
  margin-bottom: 16px;
}

.job-company {
  font-size: 13px;
  color: var(--cf-text-secondary);
}

.job-position {
  font-size: 18px;
  margin-top: 2px;
}

.job-meta {
  display: flex;
  gap: 14px;
  margin-top: 10px;
  font-size: 13px;
  color: var(--cf-text-secondary);
}

.select-card {
  margin-bottom: 16px;
}

.select-row {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}

.select-item {
  width: 260px;
}

.score-card {
  margin-bottom: 16px;
}

.score-row {
  display: flex;
  align-items: center;
  gap: 32px;
}

.score-box {
  display: flex;
  align-items: baseline;
  gap: 4px;
  padding: 16px 24px;
  background: #eff6ff;
  border-radius: 12px;
}

.score-number {
  font-size: 40px;
  font-weight: 700;
  color: var(--cf-primary);
}

.score-unit {
  color: var(--cf-text-secondary);
}

.stat-chip {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.score-note {
  margin-top: 8px;
  font-size: 12px;
  color: var(--cf-text-secondary);
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-head h3 {
  font-size: 16px;
}

.card-hint {
  font-size: 12px;
  color: var(--cf-text-secondary);
}

.detail-row {
  padding: 12px 0;
  border-bottom: 1px dashed var(--cf-border);
}

.detail-row:last-child {
  border-bottom: none;
}

.detail-skill {
  display: flex;
  align-items: center;
  gap: 10px;
}

.skill-name {
  font-weight: 600;
  font-size: 15px;
}

.detail-evidence {
  margin-top: 6px;
  font-size: 13px;
  color: var(--cf-text);
}

.detail-suggestion {
  margin-top: 4px;
  font-size: 13px;
  color: var(--cf-text-secondary);
}

.detail-actions {
  margin-top: 8px;
}
</style>
