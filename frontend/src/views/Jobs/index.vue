<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { getJob, listJobs, parseJob, type JobDetail, type JobSummary } from '@/api/jobs'
import { recommendJobs, type RecommendItem } from '@/api/match'

const router = useRouter()

const recommendVisible = ref(false)
const recommendLoading = ref(false)
const recommendItems = ref<RecommendItem[]>([])

const filters = reactive({
  keyword: '',
  city: '',
  salary: '',
})

const jobs = ref<JobSummary[]>([])
const total = ref(0)
const page = ref(1)
const size = 8
const loading = ref(false)

const drawerVisible = ref(false)
const drawerLoading = ref(false)
const drawerJob = ref<JobDetail | null>(null)
const parsing = ref(false)

function errorMessage(error: unknown, fallback: string): string {
  const err = error as { response?: { data?: { message?: string } } }
  return err.response?.data?.message || fallback
}

async function loadJobs(): Promise<void> {
  loading.value = true
  try {
    const res = await listJobs({
      page: page.value,
      size,
      keyword: filters.keyword.trim() || undefined,
      city: filters.city.trim() || undefined,
      salary: filters.salary.trim() || undefined,
    })
    jobs.value = res.data.items
    total.value = res.data.total
  } catch (error) {
    ElMessage.error(errorMessage(error, '岗位加载失败，请稍后重试'))
  } finally {
    loading.value = false
  }
}

function handleSearch(): void {
  page.value = 1
  void loadJobs()
}

function handleReset(): void {
  filters.keyword = ''
  filters.city = ''
  filters.salary = ''
  handleSearch()
}

function onPageChange(newPage: number): void {
  page.value = newPage
  void loadJobs()
}

async function openDetail(jobId: number): Promise<void> {
  drawerVisible.value = true
  drawerLoading.value = true
  try {
    drawerJob.value = (await getJob(jobId)).data
  } catch (error) {
    ElMessage.error(errorMessage(error, '岗位详情加载失败'))
  } finally {
    drawerLoading.value = false
  }
}

async function handleParse(): Promise<void> {
  if (!drawerJob.value) return
  parsing.value = true
  try {
    await parseJob(drawerJob.value.id)
    ElMessage.success('JD 解析完成')
    await openDetail(drawerJob.value.id)
  } catch (error) {
    ElMessage.error(errorMessage(error, 'JD 解析失败，请稍后重试'))
  } finally {
    parsing.value = false
  }
}

function goMatch(jobId: number): void {
  drawerVisible.value = false
  router.push(`/match/${jobId}`)
}

function openExternal(url: string | null): void {
  if (url) {
    window.open(url, '_blank', 'noopener')
  }
}

async function openRecommend(): Promise<void> {
  recommendVisible.value = true
  recommendLoading.value = true
  try {
    const res = await recommendJobs(1, 50)
    recommendItems.value = res.data.items
  } catch (error) {
    ElMessage.error(errorMessage(error, '推荐加载失败，请稍后重试'))
  } finally {
    recommendLoading.value = false
  }
}

function goMatchFromRecommend(jobId: number): void {
  recommendVisible.value = false
  router.push(`/match/${jobId}`)
}

onMounted(() => {
  void loadJobs()
})
</script>

<template>
  <div class="jobs-page">
    <header class="page-head">
      <div>
        <p class="eyebrow">JOBS</p>
        <h2>岗位中心</h2>
        <p class="subtitle">内置职位库 · JD 经 AI 解析生成结构化技能要求 · 点击「匹配分析」查看你与岗位的差距</p>
      </div>
    </header>

    <el-card shadow="never" class="search-card">
      <div class="search-row">
        <el-input
          v-model="filters.keyword"
          data-testid="jobs-keyword"
          placeholder="岗位 / 公司关键词"
          clearable
          class="search-input"
          @keyup.enter="handleSearch"
        />
        <el-input
          v-model="filters.city"
          data-testid="jobs-city"
          placeholder="城市，如 深圳"
          clearable
          class="search-input"
          @keyup.enter="handleSearch"
        />
        <el-input
          v-model="filters.salary"
          data-testid="jobs-salary"
          placeholder="薪资，如 8k"
          clearable
          class="search-input"
          @keyup.enter="handleSearch"
        />
        <el-button type="primary" :icon="Search" data-testid="jobs-search" @click="handleSearch">
          搜索
        </el-button>
        <el-button data-testid="jobs-reset" @click="handleReset">重置</el-button>
        <el-button type="primary" plain data-testid="jobs-recommend" @click="openRecommend">
          智能推荐
        </el-button>
      </div>
    </el-card>

    <div v-loading="loading" class="jobs-body">
      <el-empty v-if="!loading && jobs.length === 0" description="没有找到符合条件的岗位" />

      <div v-else class="job-grid">
        <el-card
          v-for="job in jobs"
          :key="job.id"
          shadow="hover"
          class="job-card"
          data-testid="job-card"
        >
          <div class="job-card-head">
            <div>
              <p class="job-company">{{ job.company || '未注明公司' }}</p>
              <h3 class="job-position">{{ job.position }}</h3>
            </div>
            <el-tag size="small" :type="job.sourceType === 'MOCK' ? 'info' : 'warning'">
              {{ job.sourceType === 'MOCK' ? '内置' : '手动' }}
            </el-tag>
          </div>

          <div class="job-meta">
            <span v-if="job.city">📍 {{ job.city }}</span>
            <span v-if="job.salary">💰 {{ job.salary }}</span>
            <span>📋 {{ job.requirementCount }} 项技能要求</span>
          </div>

          <div class="job-skills">
            <el-tag
              v-for="skill in job.topSkills"
              :key="skill"
              size="small"
              effect="plain"
              type="primary"
              class="skill-tag"
            >
              {{ skill }}
            </el-tag>
            <span v-if="job.topSkills.length === 0" class="no-skills">尚未解析技能要求</span>
          </div>

          <div class="job-card-actions">
            <el-button size="small" data-testid="job-open" @click="openDetail(job.id)">
              查看详情
            </el-button>
            <el-button size="small" type="primary" plain @click="goMatch(job.id)">
              匹配分析
            </el-button>
          </div>
        </el-card>
      </div>

      <div v-if="total > size" class="pagination-row">
        <el-pagination
          data-testid="jobs-pagination"
          layout="prev, pager, next, total"
          :total="total"
          :page-size="size"
          :current-page="page"
          @current-change="onPageChange"
        />
      </div>
    </div>

    <el-drawer v-model="drawerVisible" title="岗位详情" size="480px" data-testid="jobs-drawer">
      <div v-loading="drawerLoading" class="drawer-body">
        <template v-if="drawerJob">
          <h3 class="drawer-position">{{ drawerJob.position }}</h3>
          <p class="drawer-company">{{ drawerJob.company || '未注明公司' }}</p>

          <div class="drawer-meta">
            <span v-if="drawerJob.city">📍 {{ drawerJob.city }}</span>
            <span v-if="drawerJob.salary">💰 {{ drawerJob.salary }}</span>
          </div>

          <el-divider content-position="left">技能要求</el-divider>
          <div v-if="drawerJob.requirements.length" class="requirement-list">
            <div
              v-for="req in drawerJob.requirements"
              :key="req.id"
              class="requirement-item"
              data-testid="requirement-item"
            >
              <span class="requirement-skill">{{ req.skill }}</span>
              <el-tag
                size="small"
                :type="req.importance === 'REQUIRED' ? 'danger' : 'warning'"
                effect="plain"
              >
                {{ req.importance === 'REQUIRED' ? '必备' : '加分' }}
              </el-tag>
              <span class="requirement-weight">权重 {{ req.weight }}</span>
            </div>
          </div>
          <el-empty v-else description="暂无技能要求，点击下方按钮用 AI 解析 JD" :image-size="60" />

          <el-divider content-position="left">JD 原文</el-divider>
          <pre v-if="drawerJob.jdContent" class="jd-content" data-testid="jobs-jd-content">{{ drawerJob.jdContent }}</pre>
          <p v-else class="jd-empty">该岗位暂无 JD 原文</p>

          <div class="drawer-actions">
            <el-button
              type="primary"
              plain
              :loading="parsing"
              data-testid="jobs-parse"
              @click="handleParse"
            >
              AI 解析 JD
            </el-button>
            <el-button
              v-if="drawerJob.externalUrl"
              data-testid="jobs-external"
              @click="openExternal(drawerJob.externalUrl)"
            >
              查看原招聘页
            </el-button>
            <el-button type="primary" data-testid="jobs-match" @click="goMatch(drawerJob.id)">
              匹配分析
            </el-button>
          </div>
        </template>
      </div>
    </el-drawer>

    <el-dialog
      v-model="recommendVisible"
      title="为你推荐（按匹配度排序）"
      width="640px"
      data-testid="jobs-recommend-dialog"
    >
      <div v-loading="recommendLoading" class="recommend-body">
        <el-empty v-if="!recommendLoading && recommendItems.length === 0" description="暂无推荐岗位" />
        <div
          v-for="item in recommendItems"
          :key="item.jobId"
          class="recommend-item"
          data-testid="recommend-item"
        >
          <div class="recommend-main">
            <p class="recommend-position">{{ item.position }}</p>
            <p class="recommend-company">
              {{ item.company || '未注明公司' }}
              <span v-if="item.city">· {{ item.city }}</span>
              <span v-if="item.salary">· {{ item.salary }}</span>
            </p>
            <p v-for="reason in item.rankReasons" :key="reason" class="recommend-reason">
              ⚠️ {{ reason }}
            </p>
          </div>
          <div class="recommend-side">
            <span class="recommend-score" data-testid="recommend-score">{{ item.matchScore }}</span>
            <el-button size="small" type="primary" plain @click="goMatchFromRecommend(item.jobId)">
              去分析
            </el-button>
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-head {
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

.search-card {
  margin-bottom: 16px;
}

.search-row {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.search-input {
  width: 200px;
}

.jobs-body {
  min-height: 200px;
}

.job-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}

.job-card-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.job-company {
  font-size: 13px;
  color: var(--cf-text-secondary);
}

.job-position {
  font-size: 16px;
  margin-top: 2px;
}

.job-meta {
  display: flex;
  gap: 14px;
  margin-top: 10px;
  font-size: 13px;
  color: var(--cf-text-secondary);
}

.job-skills {
  margin-top: 10px;
  min-height: 24px;
}

.skill-tag {
  margin-right: 6px;
  margin-bottom: 4px;
}

.no-skills {
  font-size: 12px;
  color: var(--cf-text-secondary);
}

.job-card-actions {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}

.pagination-row {
  margin-top: 20px;
  display: flex;
  justify-content: center;
}

.drawer-body {
  min-height: 200px;
}

.drawer-position {
  font-size: 18px;
}

.drawer-company {
  margin-top: 4px;
  color: var(--cf-text-secondary);
}

.drawer-meta {
  display: flex;
  gap: 14px;
  margin-top: 10px;
  font-size: 13px;
  color: var(--cf-text-secondary);
}

.requirement-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 0;
  border-bottom: 1px dashed var(--cf-border);
}

.requirement-skill {
  font-weight: 600;
  min-width: 110px;
}

.requirement-weight {
  margin-left: auto;
  font-size: 12px;
  color: var(--cf-text-secondary);
}

.jd-content {
  background: var(--cf-bg);
  border-radius: 6px;
  padding: 12px;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
}

.jd-empty {
  color: var(--cf-text-secondary);
  font-size: 13px;
}

.drawer-actions {
  margin-top: 20px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.recommend-body {
  min-height: 120px;
}

.recommend-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 12px 0;
  border-bottom: 1px dashed var(--cf-border);
}

.recommend-item:last-child {
  border-bottom: none;
}

.recommend-position {
  font-weight: 600;
  font-size: 15px;
}

.recommend-company {
  margin-top: 2px;
  font-size: 13px;
  color: var(--cf-text-secondary);
}

.recommend-reason {
  margin-top: 4px;
  font-size: 12px;
  color: #b45309;
}

.recommend-side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
}

.recommend-score {
  font-size: 22px;
  font-weight: 700;
  color: var(--cf-primary);
}
</style>
