<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Link, TopRight } from '@element-plus/icons-vue'
import {
  getCompany,
  getPlatformLinks,
  listCompanyJobs,
  type CompanyDetail,
  type PlatformLink,
} from '@/api/companies'
import type { JobSummary } from '@/api/jobs'

const route = useRoute()
const router = useRouter()

const companyId = Number(route.params.id)

const loading = ref(false)
const company = ref<CompanyDetail | null>(null)
const platformLinks = ref<PlatformLink[]>([])
const linksLoading = ref(false)

const jobs = ref<JobSummary[]>([])
const jobTotal = ref(0)
const jobPage = ref(1)
const jobSize = 6
const jobsLoading = ref(false)

function errorMessage(error: unknown, fallback: string): string {
  const err = error as { response?: { data?: { message?: string } } }
  return err.response?.data?.message || fallback
}

async function loadCompany(): Promise<void> {
  loading.value = true
  try {
    company.value = (await getCompany(companyId)).data
  } catch (error) {
    ElMessage.error(errorMessage(error, '企业详情加载失败'))
  } finally {
    loading.value = false
  }
}

async function loadPlatformLinks(): Promise<void> {
  linksLoading.value = true
  try {
    platformLinks.value = (await getPlatformLinks(companyId)).data
  } catch (error) {
    ElMessage.error(errorMessage(error, '平台入口加载失败'))
  } finally {
    linksLoading.value = false
  }
}

async function loadJobs(): Promise<void> {
  jobsLoading.value = true
  try {
    const res = await listCompanyJobs(companyId, jobPage.value, jobSize)
    jobs.value = res.data.items
    jobTotal.value = res.data.total
  } catch (error) {
    ElMessage.error(errorMessage(error, '关联岗位加载失败'))
  } finally {
    jobsLoading.value = false
  }
}

function openLink(url: string | null | undefined): void {
  if (url) {
    window.open(url, '_blank', 'noopener')
  }
}

function goMatch(jobId: number): void {
  router.push(`/match/${jobId}`)
}

function onJobPageChange(newPage: number): void {
  jobPage.value = newPage
  void loadJobs()
}

function initialOf(): string {
  const target = company.value
  if (!target) return '企'
  return (target.shortName || target.name).slice(0, 1)
}

function sourceLabel(source: string): string {
  if (source === 'OFFICIAL' || source === 'SEED') return '官方公开渠道整理'
  if (source === 'MIGRATED') return '历史数据迁移'
  if (source === 'ADMIN_IMPORT') return '管理员导入'
  return '手动录入'
}

function verificationLabel(status: string): string {
  if (status === 'VERIFIED') return '链接已核实'
  if (status === 'EXPIRED') return '链接已过期'
  return '待核实'
}

function verificationType(status: string): 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'VERIFIED') return 'success'
  if (status === 'EXPIRED') return 'danger'
  return 'info'
}

function batchLabel(batch: string | null): string {
  return batch || '未标注批次'
}

function statusLabel(status: string | null): string {
  if (status === 'CLOSED') return '已结束'
  if (status === 'SOON') return '即将开始'
  return '招聘中'
}

function statusType(status: string | null): 'success' | 'warning' | 'info' {
  if (status === 'CLOSED') return 'info'
  if (status === 'SOON') return 'warning'
  return 'success'
}

const recruitEntrances = computed(() => {
  const target = company.value
  return [
    {
      key: 'campus',
      label: '校园招聘',
      desc: '应届生 / 秋招 / 春招入口',
      url: target?.campusRecruitUrl ?? null,
    },
    {
      key: 'intern',
      label: '实习招聘',
      desc: '日常实习 / 暑期实习入口',
      url: target?.internRecruitUrl ?? null,
    },
    {
      key: 'social',
      label: '社会招聘',
      desc: '社招 / 有经验岗位入口',
      url: target?.socialRecruitUrl ?? null,
    },
  ]
})

onMounted(() => {
  void loadCompany()
  void loadPlatformLinks()
  void loadJobs()
})
</script>

<template>
  <div v-loading="loading" class="company-detail">
    <el-button link :icon="ArrowLeft" class="back-btn" @click="router.push('/companies')">
      返回企业库
    </el-button>

    <template v-if="company">
      <el-card shadow="never" class="hero">
        <div class="hero-head">
          <span class="hero-logo">{{ initialOf() }}</span>
          <div class="hero-main">
            <div class="hero-title">
              <h2 data-testid="company-name">{{ company.name }}</h2>
              <el-tag
                size="small"
                :type="verificationType(company.verificationStatus)"
                effect="plain"
                data-testid="company-verification"
              >
                {{ verificationLabel(company.verificationStatus) }}
              </el-tag>
            </div>
            <p v-if="company.shortName && company.shortName !== company.name" class="hero-short">
              简称：{{ company.shortName }}
            </p>
            <p v-if="company.description" class="hero-desc">{{ company.description }}</p>
            <div class="hero-tags">
              <el-tag v-if="company.industry" size="small" effect="plain">{{ company.industry }}</el-tag>
              <el-tag v-if="company.companyType" size="small" type="info" effect="plain">
                {{ company.companyType }}
              </el-tag>
              <el-tag v-if="company.scale" size="small" type="info" effect="plain">
                {{ company.scale }}
              </el-tag>
              <span v-if="company.city" class="hero-city">📍 {{ company.city }}</span>
            </div>
            <p class="hero-meta">
              信息来源：{{ sourceLabel(company.source) }}
              <span v-if="company.lastVerifiedAt"> · 最近核验：{{ company.lastVerifiedAt }}</span>
            </p>
          </div>
          <div class="hero-actions">
            <el-button v-if="company.website" :icon="Link" @click="openLink(company.website)">
              企业官网
            </el-button>
            <el-button
              v-if="company.recruitmentUrl"
              type="primary"
              :icon="TopRight"
              @click="openLink(company.recruitmentUrl)"
            >
              招聘官网
            </el-button>
          </div>
        </div>
      </el-card>

      <section class="section">
        <h3 class="section-title">官方招聘入口</h3>
        <div class="entrance-grid">
          <el-card
            v-for="entrance in recruitEntrances"
            :key="entrance.key"
            shadow="never"
            class="entrance-card"
          >
            <div class="entrance-head">
              <span class="entrance-label">{{ entrance.label }}</span>
              <el-tag v-if="!entrance.url" size="small" type="info" effect="plain">未提供</el-tag>
            </div>
            <p class="entrance-desc">{{ entrance.desc }}</p>
            <el-button
              v-if="entrance.url"
              size="small"
              type="primary"
              plain
              :icon="TopRight"
              @click="openLink(entrance.url)"
            >
              前往入口
            </el-button>
            <span v-else class="entrance-empty">暂无独立入口，可用下方平台搜索</span>
          </el-card>
        </div>
      </section>

      <section class="section">
        <h3 class="section-title">第三方招聘平台入口</h3>
        <el-alert
          type="warning"
          :closable="false"
          show-icon
          title="以下链接会跳出 CareerFlow，打开第三方招聘平台的搜索页；平台内投递与 CareerFlow 无关，系统不会自动投递。"
          class="platform-alert"
        />
        <div v-loading="linksLoading" class="platform-grid">
          <a
            v-for="link in platformLinks"
            :key="link.key"
            class="platform-card"
            :href="link.url"
            target="_blank"
            rel="noopener noreferrer"
          >
            <span class="platform-label">{{ link.label }}</span>
            <span class="platform-note">{{ link.note }}</span>
            <span class="platform-open">打开平台搜索 <el-icon><TopRight /></el-icon></span>
          </a>
        </div>
      </section>

      <section class="section">
        <div class="section-head">
          <h3 class="section-title">该企业在 CareerFlow 的岗位（{{ company.jobCount }}）</h3>
          <el-button link type="primary" @click="router.push('/jobs')">去岗位中心 →</el-button>
        </div>
        <div v-loading="jobsLoading">
          <el-empty
            v-if="!jobsLoading && jobs.length === 0"
            description="该企业暂无已录入岗位，可通过上方招聘入口或平台搜索查看"
          />
          <div v-else class="job-list">
            <div v-for="job in jobs" :key="job.id" class="job-row">
              <div class="job-main">
                <p class="job-position">{{ job.position }}</p>
                <p class="job-meta">
                  <span v-if="job.city">📍 {{ job.city }}</span>
                  <span v-if="job.salary">💰 {{ job.salary }}</span>
                  <span>{{ job.requirementCount }} 项技能要求</span>
                </p>
              </div>
              <div class="job-tags">
                <el-tag size="small" effect="plain">{{ batchLabel(job.recruitmentBatch) }}</el-tag>
                <el-tag size="small" :type="statusType(job.recruitmentStatus)" effect="plain">
                  {{ statusLabel(job.recruitmentStatus) }}
                </el-tag>
              </div>
              <el-button size="small" type="primary" plain @click="goMatch(job.id)">
                匹配分析
              </el-button>
            </div>
          </div>
          <div v-if="jobTotal > jobSize" class="pagination-row">
            <el-pagination
              layout="prev, pager, next, total"
              :total="jobTotal"
              :page-size="jobSize"
              :current-page="jobPage"
              @current-change="onJobPageChange"
            />
          </div>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.company-detail {
  display: flex;
  flex-direction: column;
  gap: var(--cf-space-4);
  min-height: 320px;
}

.back-btn {
  align-self: flex-start;
}

.hero {
  border-radius: var(--cf-radius-md);
}

.hero-head {
  display: flex;
  align-items: flex-start;
  gap: var(--cf-space-4);
  flex-wrap: wrap;
}

.hero-logo {
  width: 64px;
  height: 64px;
  flex-shrink: 0;
  border-radius: 14px;
  background: linear-gradient(135deg, var(--cf-primary), var(--cf-primary-light));
  color: #fff;
  font-weight: 700;
  font-size: 26px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.hero-main {
  flex: 1;
  min-width: 240px;
}

.hero-title {
  display: flex;
  align-items: center;
  gap: var(--cf-space-2);
  flex-wrap: wrap;
}

.hero-title h2 {
  margin: 0;
  font-size: 22px;
  color: var(--cf-text);
}

.hero-short,
.hero-desc {
  margin: 6px 0 0;
  font-size: var(--cf-font-sm);
  color: var(--cf-text-secondary);
}

.hero-tags {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--cf-space-2);
  margin-top: var(--cf-space-3);
}

.hero-city {
  font-size: var(--cf-font-xs);
  color: var(--cf-text-secondary);
}

.hero-meta {
  margin: var(--cf-space-2) 0 0;
  font-size: var(--cf-font-xs);
  color: var(--cf-text-tertiary);
}

.hero-actions {
  display: flex;
  gap: var(--cf-space-2);
  flex-wrap: wrap;
}

.section {
  display: flex;
  flex-direction: column;
  gap: var(--cf-space-3);
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.section-title {
  margin: 0;
  font-size: 16px;
  color: var(--cf-text);
}

.entrance-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: var(--cf-space-3);
}

.entrance-card {
  border-radius: var(--cf-radius-md);
}

.entrance-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.entrance-label {
  font-weight: 600;
  color: var(--cf-text);
}

.entrance-desc {
  margin: 6px 0 var(--cf-space-3);
  font-size: var(--cf-font-xs);
  color: var(--cf-text-secondary);
}

.entrance-empty {
  font-size: var(--cf-font-xs);
  color: var(--cf-text-tertiary);
}

.platform-alert {
  border-radius: var(--cf-radius-md);
}

.platform-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: var(--cf-space-3);
}

.platform-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: var(--cf-space-3);
  border: 1px solid var(--cf-border);
  border-radius: var(--cf-radius-md);
  background: var(--cf-card-bg);
  color: inherit;
  text-decoration: none;
  transition: border-color 0.15s ease, transform 0.15s ease;
}

.platform-card:hover {
  border-color: var(--cf-primary);
  transform: translateY(-2px);
}

.platform-label {
  font-weight: 600;
  color: var(--cf-text);
}

.platform-note {
  font-size: var(--cf-font-xs);
  color: var(--cf-text-tertiary);
  line-height: 1.5;
}

.platform-open {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: var(--cf-font-xs);
  color: var(--cf-primary);
}

.job-list {
  display: flex;
  flex-direction: column;
  gap: var(--cf-space-2);
}

.job-row {
  display: flex;
  align-items: center;
  gap: var(--cf-space-3);
  padding: var(--cf-space-3);
  border: 1px solid var(--cf-border);
  border-radius: var(--cf-radius-md);
  background: var(--cf-card-bg);
  flex-wrap: wrap;
}

.job-main {
  flex: 1;
  min-width: 200px;
}

.job-position {
  margin: 0;
  font-weight: 600;
  color: var(--cf-text);
}

.job-meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--cf-space-3);
  margin: 4px 0 0;
  font-size: var(--cf-font-xs);
  color: var(--cf-text-secondary);
}

.job-tags {
  display: flex;
  gap: var(--cf-space-2);
}

.pagination-row {
  display: flex;
  justify-content: center;
  margin-top: var(--cf-space-4);
}
</style>
