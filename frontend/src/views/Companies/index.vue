<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Link, Search } from '@element-plus/icons-vue'
import { listCompanies, type CompanySummary } from '@/api/companies'

const router = useRouter()

const filters = reactive({
  keyword: '',
  industry: '',
  city: '',
})

const companies = ref<CompanySummary[]>([])
const total = ref(0)
const page = ref(1)
const size = 12
const loading = ref(false)

function errorMessage(error: unknown, fallback: string): string {
  const err = error as { response?: { data?: { message?: string } } }
  return err.response?.data?.message || fallback
}

async function loadCompanies(): Promise<void> {
  loading.value = true
  try {
    const res = await listCompanies({
      page: page.value,
      size,
      keyword: filters.keyword.trim() || undefined,
      industry: filters.industry.trim() || undefined,
      city: filters.city.trim() || undefined,
    })
    companies.value = res.data.items
    total.value = res.data.total
  } catch (error) {
    ElMessage.error(errorMessage(error, '企业列表加载失败，请稍后重试'))
  } finally {
    loading.value = false
  }
}

function handleSearch(): void {
  page.value = 1
  void loadCompanies()
}

function handleReset(): void {
  filters.keyword = ''
  filters.industry = ''
  filters.city = ''
  handleSearch()
}

function onPageChange(newPage: number): void {
  page.value = newPage
  void loadCompanies()
}

function goDetail(id: number): void {
  router.push(`/companies/${id}`)
}

function openLink(url: string | null): void {
  if (url) {
    window.open(url, '_blank', 'noopener')
  }
}

function initialOf(company: CompanySummary): string {
  return (company.shortName || company.name).slice(0, 1)
}

function sourceLabel(source: string): string {
  if (source === 'OFFICIAL' || source === 'SEED') return '官方整理'
  if (source === 'MIGRATED') return '批量迁移'
  if (source === 'ADMIN_IMPORT') return '管理员导入'
  return '手动录入'
}

function verificationLabel(status: string): string {
  if (status === 'VERIFIED') return '已核实'
  if (status === 'EXPIRED') return '链接已过期'
  return '待核实'
}

function verificationType(status: string): 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'VERIFIED') return 'success'
  if (status === 'EXPIRED') return 'danger'
  return 'info'
}

onMounted(() => {
  void loadCompanies()
})
</script>

<template>
  <div class="companies-page">
    <header class="page-head">
      <div>
        <p class="eyebrow">COMPANIES</p>
        <h2>企业库</h2>
        <p class="subtitle">
          200+ 企业官方招聘入口 · 校招 / 实习 / 社招分类 · 链接来源与核验状态可追溯
        </p>
      </div>
    </header>

    <el-card shadow="never" class="search-card">
      <div class="search-row">
        <el-input
          v-model="filters.keyword"
          data-testid="companies-keyword"
          placeholder="企业名称 / 简称"
          clearable
          class="search-input"
          @keyup.enter="handleSearch"
        />
        <el-input
          v-model="filters.industry"
          data-testid="companies-industry"
          placeholder="行业，如 互联网"
          clearable
          class="search-input"
          @keyup.enter="handleSearch"
        />
        <el-input
          v-model="filters.city"
          data-testid="companies-city"
          placeholder="总部城市，如 深圳"
          clearable
          class="search-input"
          @keyup.enter="handleSearch"
        />
        <el-button type="primary" :icon="Search" data-testid="companies-search" @click="handleSearch">
          搜索
        </el-button>
        <el-button data-testid="companies-reset" @click="handleReset">重置</el-button>
      </div>
    </el-card>

    <div v-loading="loading" class="companies-body">
      <el-empty
        v-if="!loading && companies.length === 0"
        description="没有找到符合条件的企业，试试换个关键词"
      />

      <div v-else class="company-grid">
        <el-card
          v-for="company in companies"
          :key="company.id"
          shadow="hover"
          class="company-card"
          data-testid="company-card"
          @click="goDetail(company.id)"
        >
          <div class="company-head">
            <span class="company-logo">{{ initialOf(company) }}</span>
            <div class="company-title">
              <h3 class="company-name">{{ company.shortName || company.name }}</h3>
              <p class="company-full-name">{{ company.name }}</p>
            </div>
            <el-tag size="small" :type="verificationType(company.verificationStatus)" effect="plain">
              {{ verificationLabel(company.verificationStatus) }}
            </el-tag>
          </div>

          <div class="company-meta">
            <el-tag v-if="company.industry" size="small" effect="plain">{{ company.industry }}</el-tag>
            <el-tag v-if="company.scale" size="small" type="info" effect="plain">
              {{ company.scale }}
            </el-tag>
            <span v-if="company.city" class="company-city">📍 {{ company.city }}</span>
          </div>

          <div class="company-links">
            <el-button
              v-if="company.campusRecruitUrl"
              size="small"
              type="primary"
              plain
              :icon="Link"
              @click.stop="openLink(company.campusRecruitUrl)"
            >
              校招入口
            </el-button>
            <el-button
              v-if="company.recruitmentUrl"
              size="small"
              plain
              :icon="Link"
              @click.stop="openLink(company.recruitmentUrl)"
            >
              招聘官网
            </el-button>
            <span v-if="!company.campusRecruitUrl && !company.recruitmentUrl" class="no-link">
              暂无公开招聘入口
            </span>
          </div>

          <div class="company-footer">
            <span class="company-source">来源：{{ sourceLabel(company.source) }}</span>
            <el-button link type="primary" data-testid="company-detail" @click.stop="goDetail(company.id)">
              企业详情 →
            </el-button>
          </div>
        </el-card>
      </div>

      <div v-if="total > size" class="pagination-row">
        <el-pagination
          data-testid="companies-pagination"
          layout="prev, pager, next, total"
          :total="total"
          :page-size="size"
          :current-page="page"
          @current-change="onPageChange"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.companies-page {
  display: flex;
  flex-direction: column;
  gap: var(--cf-space-4);
}

.page-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: var(--cf-space-4);
}

.eyebrow {
  margin: 0 0 4px;
  font-size: var(--cf-font-xs);
  letter-spacing: 0.12em;
  color: var(--cf-primary);
  font-weight: 600;
}

.page-head h2 {
  margin: 0 0 6px;
  font-size: 24px;
  color: var(--cf-text);
}

.subtitle {
  margin: 0;
  color: var(--cf-text-secondary);
  font-size: var(--cf-font-sm);
}

.search-card {
  border-radius: var(--cf-radius-md);
}

.search-row {
  display: flex;
  flex-wrap: wrap;
  gap: var(--cf-space-3);
  align-items: center;
}

.search-input {
  width: 220px;
}

.companies-body {
  min-height: 240px;
}

.company-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: var(--cf-space-4);
}

.company-card {
  border-radius: var(--cf-radius-md);
  cursor: pointer;
  transition: transform 0.15s ease;
}

.company-card:hover {
  transform: translateY(-2px);
}

.company-head {
  display: flex;
  align-items: flex-start;
  gap: var(--cf-space-3);
}

.company-logo {
  width: 44px;
  height: 44px;
  flex-shrink: 0;
  border-radius: 10px;
  background: linear-gradient(135deg, var(--cf-primary), var(--cf-primary-light));
  color: #fff;
  font-weight: 700;
  font-size: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.company-title {
  flex: 1;
  min-width: 0;
}

.company-name {
  margin: 0;
  font-size: 16px;
  color: var(--cf-text);
}

.company-full-name {
  margin: 4px 0 0;
  font-size: var(--cf-font-xs);
  color: var(--cf-text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.company-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--cf-space-2);
  margin: var(--cf-space-3) 0;
}

.company-city {
  font-size: var(--cf-font-xs);
  color: var(--cf-text-secondary);
}

.company-links {
  display: flex;
  flex-wrap: wrap;
  gap: var(--cf-space-2);
  min-height: 32px;
  align-items: center;
}

.no-link {
  font-size: var(--cf-font-xs);
  color: var(--cf-text-tertiary);
}

.company-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: var(--cf-space-3);
  padding-top: var(--cf-space-2);
  border-top: 1px solid var(--cf-border);
}

.company-source {
  font-size: var(--cf-font-xs);
  color: var(--cf-text-tertiary);
}

.pagination-row {
  display: flex;
  justify-content: center;
  margin-top: var(--cf-space-5);
}

@media (max-width: 767px) {
  .search-input {
    width: 100%;
  }
}
</style>
