<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'

import { getJob, type JobDetail, type JobSummary } from '@/api/jobs'
import { useJobs } from '@/composables/useJobs'

const router = useRouter()
const {
  jobs,
  total,
  page,
  size,
  loading,
  filters,
  load,
  search,
  reset,
  changePage,
  changeSize,
} = useJobs()

const detailVisible = ref(false)
const detailLoading = ref(false)
const activeJob = ref<JobDetail | null>(null)

function errorMessage(error: unknown, fallback: string): string {
  const err = error as { response?: { data?: { message?: string } } }
  return err.response?.data?.message || fallback
}

function showError(error: unknown, fallback: string): void {
  ElMessage.error(errorMessage(error, fallback))
}

async function loadPage(): Promise<void> {
  try {
    await load()
  } catch (error) {
    showError(error, '岗位加载失败，请稍后重试')
  }
}

async function handleSearch(): Promise<void> {
  try {
    await search()
  } catch (error) {
    showError(error, '岗位搜索失败，请稍后重试')
  }
}

async function handleReset(): Promise<void> {
  try {
    await reset()
  } catch (error) {
    showError(error, '岗位加载失败，请稍后重试')
  }
}

async function handlePageChange(nextPage: number): Promise<void> {
  try {
    await changePage(nextPage)
  } catch (error) {
    showError(error, '岗位加载失败，请稍后重试')
  }
}

async function handleSizeChange(nextSize: number): Promise<void> {
  try {
    await changeSize(nextSize)
  } catch (error) {
    showError(error, '岗位加载失败，请稍后重试')
  }
}

async function openDetail(job: JobSummary): Promise<void> {
  detailVisible.value = true
  detailLoading.value = true
  activeJob.value = null
  try {
    const response = await getJob(job.id)
    activeJob.value = response.data
  } catch (error) {
    showError(error, '岗位详情加载失败，请稍后重试')
  } finally {
    detailLoading.value = false
  }
}

function openMatch(job: JobSummary | JobDetail): void {
  router.push(`/match/${job.id}`)
}

function importanceLabel(importance: string): string {
  return importance === 'PREFERRED' ? '加分项' : '硬性要求'
}

function importanceType(importance: string): 'danger' | 'warning' {
  return importance === 'PREFERRED' ? 'warning' : 'danger'
}

onMounted(loadPage)
</script>

<template>
  <div class="jobs-page">
    <header class="page-header">
      <div>
        <h2>岗位中心</h2>
        <p>内置职位库 · 共 {{ total }} 个岗位，选择岗位查看 JD 或进入匹配分析。</p>
      </div>
      <el-tag type="info" effect="plain">MOCK 职位库</el-tag>
    </header>

    <section class="filter-card">
      <el-input
        v-model="filters.keyword"
        data-testid="job-keyword"
        placeholder="搜索岗位或公司，例如 Java"
        clearable
        class="filter-keyword"
        @keyup.enter="handleSearch"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
      <el-input
        v-model="filters.city"
        data-testid="job-city"
        placeholder="城市，例如 深圳"
        clearable
        class="filter-input"
        @keyup.enter="handleSearch"
      />
      <el-input
        v-model="filters.salary"
        data-testid="job-salary"
        placeholder="薪资关键字，例如 200"
        clearable
        class="filter-input"
        @keyup.enter="handleSearch"
      />
      <el-button type="primary" data-testid="job-search" @click="handleSearch">
        <el-icon><Search /></el-icon>
        搜索
      </el-button>
      <el-button data-testid="job-reset" @click="handleReset">
        <el-icon><Refresh /></el-icon>
        重置
      </el-button>
    </section>

    <section v-loading="loading" class="list-card">
      <el-table
        v-if="jobs.length > 0"
        :data="jobs"
        data-testid="job-table"
        row-key="id"
        class="job-table"
        @row-click="openDetail"
      >
        <el-table-column prop="company" label="公司" min-width="140" />
        <el-table-column prop="position" label="岗位" min-width="180">
          <template #default="{ row }">
            <span class="position-cell">{{ row.position }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="city" label="城市" width="100">
          <template #default="{ row }">{{ row.city || '不限' }}</template>
        </el-table-column>
        <el-table-column prop="salary" label="薪资" width="140">
          <template #default="{ row }">{{ row.salary || '面议' }}</template>
        </el-table-column>
        <el-table-column prop="sourceType" label="来源" width="90">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.sourceType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" data-testid="job-detail" @click.stop="openDetail(row)">
              查看详情
            </el-button>
            <el-button link type="primary" data-testid="job-match" @click.stop="openMatch(row)">
              匹配分析
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty
        v-else-if="!loading"
        data-testid="job-empty"
        description="没有匹配的岗位，试试调整筛选条件"
      />

      <div v-if="total > 0" class="pagination">
        <el-pagination
          :current-page="page"
          :page-size="size"
          :page-sizes="[5, 10, 20]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </section>

    <el-drawer
      v-model="detailVisible"
      title="岗位详情"
      size="560px"
      data-testid="job-detail-drawer"
    >
      <div v-loading="detailLoading" class="detail-body">
        <template v-if="activeJob">
          <div class="detail-heading">
            <h3>{{ activeJob.position }}</h3>
            <el-tag type="info" effect="plain">{{ activeJob.company }}</el-tag>
          </div>
          <div class="detail-meta">
            <span>{{ activeJob.city || '城市不限' }}</span>
            <span>{{ activeJob.salary || '薪资面议' }}</span>
            <span>{{ activeJob.sourceType }}</span>
          </div>

          <section class="detail-section">
            <h4>岗位 JD</h4>
            <p class="jd-content">{{ activeJob.jdContent || '暂无 JD 内容' }}</p>
          </section>

          <section class="detail-section">
            <h4>技能要求</h4>
            <el-empty
              v-if="activeJob.requirements.length === 0"
              description="尚未解析技能要求"
              :image-size="60"
            />
            <el-table v-else :data="activeJob.requirements" size="small">
              <el-table-column prop="skill" label="技能" min-width="140" />
              <el-table-column prop="importance" label="重要程度" width="110">
                <template #default="{ row }">
                  <el-tag :type="importanceType(row.importance)" size="small" effect="plain">
                    {{ importanceLabel(row.importance) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="weight" label="权重" width="80" />
            </el-table>
          </section>
        </template>
      </div>

      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button
          v-if="activeJob"
          type="primary"
          data-testid="job-detail-match"
          @click="openMatch(activeJob)"
        >
          分析匹配
        </el-button>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.jobs-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.page-header h2 {
  font-size: 22px;
}

.page-header p {
  margin-top: 6px;
  color: var(--cf-text-secondary);
  font-size: 14px;
}

.filter-card,
.list-card {
  background: var(--cf-card-bg);
  border: 1px solid var(--cf-border);
  border-radius: 10px;
  padding: 16px;
}

.filter-card {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.filter-keyword {
  width: 280px;
}

.filter-input {
  width: 180px;
}

.list-card {
  min-height: 260px;
}

.job-table {
  width: 100%;
}

.position-cell {
  font-weight: 600;
}

.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.detail-body {
  min-height: 200px;
}

.detail-heading {
  display: flex;
  align-items: center;
  gap: 10px;
}

.detail-heading h3 {
  font-size: 18px;
}

.detail-meta {
  margin-top: 10px;
  display: flex;
  gap: 16px;
  color: var(--cf-text-secondary);
  font-size: 13px;
}

.detail-section {
  margin-top: 22px;
}

.detail-section h4 {
  font-size: 14px;
  margin-bottom: 10px;
}

.jd-content {
  white-space: pre-wrap;
  line-height: 1.8;
  color: #374151;
  font-size: 13px;
}

@media (max-width: 900px) {
  .filter-keyword,
  .filter-input {
    width: 100%;
  }
}
</style>
