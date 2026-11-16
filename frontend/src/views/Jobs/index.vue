<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search, Upload } from '@element-plus/icons-vue'
import {
  getJob,
  importJob,
  listJobs,
  parseJob,
  verifyJob,
  type JobDetail,
  type JobSummary,
} from '@/api/jobs'
import { recommendJobs, type RecommendItem } from '@/api/match'
import { FEEDBACK_OPTIONS, submitFeedback, type FeedbackType } from '@/api/feedback'
import StatusTag from '@/components/common/StatusTag.vue'
import SourceBadge from '@/components/common/SourceBadge.vue'
import TrustPanel from '@/components/common/TrustPanel.vue'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const recommendVisible = ref(false)
const recommendLoading = ref(false)
const recommendItems = ref<RecommendItem[]>([])

const filters = reactive({
  keyword: '',
  city: '',
  salary: '',
  sourceType: '',
  verificationStatus: '',
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

// 右侧匹配摘要：选中岗位 + 详情（来源链接、技能要求）+ 推荐评分
const selectedJobId = ref<number | null>(null)
const selectedDetail = ref<JobDetail | null>(null)
const summaryLoading = ref(false)

const selectedJob = computed(
  () => jobs.value.find((job) => job.id === selectedJobId.value) ?? null,
)
const selectedRecommend = computed(
  () => recommendItems.value.find((item) => item.jobId === selectedJobId.value) ?? null,
)
const selectedSkills = computed(
  () => selectedDetail.value?.topSkills ?? selectedJob.value?.topSkills ?? [],
)

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
      sourceType: filters.sourceType || undefined,
      verificationStatus: filters.verificationStatus || undefined,
    })
    jobs.value = res.data.items
    total.value = res.data.total
    if (!jobs.value.some((job) => job.id === selectedJobId.value)) {
      selectedJobId.value = null
      selectedDetail.value = null
      const first = jobs.value[0]
      if (first) {
        void selectJob(first)
      }
    }
  } catch (error) {
    ElMessage.error(errorMessage(error, '岗位加载失败，请稍后重试'))
  } finally {
    loading.value = false
  }
}

async function selectJob(job: JobSummary): Promise<void> {
  if (selectedJobId.value === job.id) return
  selectedJobId.value = job.id
  selectedDetail.value = null
  summaryLoading.value = true
  try {
    const res = await getJob(job.id)
    if (selectedJobId.value === job.id) {
      selectedDetail.value = res.data
    }
  } catch (error) {
    ElMessage.error(errorMessage(error, '岗位详情加载失败'))
  } finally {
    summaryLoading.value = false
  }
}

/** 推荐评分依赖最新简历版本，失败时摘要只展示岗位自身信息，不打断浏览 */
async function loadRecommendedScores(): Promise<void> {
  try {
    const res = await recommendJobs(1, 50)
    recommendItems.value = res.data.items
  } catch {
    recommendItems.value = []
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
  filters.sourceType = ''
  filters.verificationStatus = ''
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

function goCompany(companyId: number | null): void {
  if (companyId) {
    router.push(`/companies/${companyId}`)
  }
}

const verifyingStatus = ref('')

async function handleVerify(status: 'VERIFIED' | 'EXPIRED' | 'UNVERIFIED'): Promise<void> {
  if (!drawerJob.value) return
  verifyingStatus.value = status
  try {
    const res = await verifyJob(drawerJob.value.id, { verificationStatus: status })
    drawerJob.value = res.data
    ElMessage.success('核验状态已更新')
    await loadJobs()
  } catch (error) {
    ElMessage.error(errorMessage(error, '核验失败，请稍后重试'))
  } finally {
    verifyingStatus.value = ''
  }
}

const importVisible = ref(false)
const importLoading = ref(false)
const importForm = reactive({
  position: '',
  company: '',
  city: '',
  salary: '',
  externalUrl: '',
  sourceName: '',
  jdContent: '',
  parseJd: true,
})

function resetImportForm(): void {
  importForm.position = ''
  importForm.company = ''
  importForm.city = ''
  importForm.salary = ''
  importForm.externalUrl = ''
  importForm.sourceName = ''
  importForm.jdContent = ''
  importForm.parseJd = true
}

async function submitImport(): Promise<void> {
  if (!importForm.position.trim()) {
    ElMessage.warning('请填写岗位名称')
    return
  }
  importLoading.value = true
  try {
    const res = await importJob({
      position: importForm.position.trim(),
      company: importForm.company.trim() || undefined,
      city: importForm.city.trim() || undefined,
      salary: importForm.salary.trim() || undefined,
      externalUrl: importForm.externalUrl.trim() || undefined,
      sourceName: importForm.sourceName.trim() || undefined,
      jdContent: importForm.jdContent.trim() || undefined,
      parseJd: importForm.parseJd && Boolean(importForm.jdContent.trim()),
    })
    ElMessage.success('岗位已导入（私有，仅本人可见）')
    importVisible.value = false
    resetImportForm()
    page.value = 1
    await loadJobs()
    await openDetail(res.data.id)
  } catch (error) {
    ElMessage.error(errorMessage(error, '导入失败，请稍后重试'))
  } finally {
    importLoading.value = false
  }
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

async function handleFeedback(command: unknown, jobId: number): Promise<void> {
  try {
    await submitFeedback({ jobId, feedbackType: command as FeedbackType })
    ElMessage.success('已记录反馈，后续推荐排序会据此调整')
  } catch (error) {
    ElMessage.error(errorMessage(error, '反馈提交失败，请稍后重试'))
  }
}

onMounted(() => {
  void loadJobs()
  void loadRecommendedScores()
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
        <el-select
          v-model="filters.sourceType"
          data-testid="jobs-source"
          placeholder="来源类型"
          clearable
          class="search-select"
        >
          <el-option label="演示数据" value="DEMO" />
          <el-option label="企业官方" value="OFFICIAL" />
          <el-option label="手动录入" value="MANUAL" />
          <el-option label="平台数据" value="PLATFORM" />
          <el-option label="批量导入" value="IMPORT" />
        </el-select>
        <el-select
          v-model="filters.verificationStatus"
          data-testid="jobs-verification"
          placeholder="可信度"
          clearable
          class="search-select"
        >
          <el-option label="演示数据" value="DEMO" />
          <el-option label="待核实" value="UNVERIFIED" />
          <el-option label="已核实" value="VERIFIED" />
          <el-option label="已过期" value="EXPIRED" />
        </el-select>
        <el-button type="primary" :icon="Search" data-testid="jobs-search" @click="handleSearch">
          搜索
        </el-button>
        <el-button data-testid="jobs-reset" @click="handleReset">重置</el-button>
        <el-button type="primary" plain data-testid="jobs-recommend" @click="openRecommend">
          智能推荐
        </el-button>
        <el-button :icon="Upload" data-testid="jobs-import" @click="importVisible = true">
          导入外部岗位
        </el-button>
      </div>
    </el-card>

    <div class="jobs-layout">
      <section v-loading="loading" class="jobs-list-pane">
        <el-empty v-if="!loading && jobs.length === 0" description="没有找到符合条件的岗位" />

        <div v-else class="job-list">
          <article
            v-for="job in jobs"
            :key="job.id"
            class="job-row"
            :class="{ 'job-row--active': job.id === selectedJobId }"
            data-testid="job-card"
            @click="selectJob(job)"
          >
            <div class="job-row-main">
              <div class="job-row-head">
                <h3 class="job-position">{{ job.position }}</h3>
                <div class="job-tag-group">
                  <StatusTag :status="job.displayStatus" />
                  <el-tag v-if="job.recruitmentBatch" size="small" effect="plain">
                    {{ job.recruitmentBatch }}
                  </el-tag>
                </div>
              </div>

              <p
                class="job-company"
                :class="{ 'job-company--link': job.companyId }"
                @click.stop="goCompany(job.companyId)"
              >
                {{ job.company || '未注明公司' }}
              </p>

              <div class="job-meta">
                <span v-if="job.city">{{ job.city }}</span>
                <span v-if="job.salary">{{ job.salary }}</span>
                <span>{{ job.requirementCount }} 项技能要求</span>
              </div>

              <div class="job-source-row">
                <SourceBadge :source-type="job.sourceType" :source-name="job.sourceName" />
                <span v-if="job.displayStatus === 'DEMO'" class="demo-hint">
                  演示数据，仅用于功能体验
                </span>
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
            </div>

            <div class="job-row-actions" @click.stop>
              <el-button size="small" data-testid="job-open" @click="openDetail(job.id)">
                查看详情
              </el-button>
              <el-button size="small" type="primary" plain @click="goMatch(job.id)">
                匹配分析
              </el-button>
              <el-dropdown
                trigger="click"
                data-testid="job-feedback"
                @command="(cmd: string | number | object) => handleFeedback(cmd, job.id)"
              >
                <el-button size="small" class="feedback-button">反馈</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item
                      v-for="option in FEEDBACK_OPTIONS"
                      :key="option.value"
                      :command="option.value"
                    >
                      {{ option.label }}
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </article>
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
      </section>

      <aside v-loading="summaryLoading" class="jobs-summary" data-testid="jobs-summary">
        <template v-if="selectedJob">
          <div class="summary-head">
            <SourceBadge :source-type="selectedJob.sourceType" :source-name="selectedJob.sourceName" />
            <StatusTag :status="selectedJob.displayStatus" />
          </div>

          <h3 class="summary-position">{{ selectedJob.position }}</h3>
          <p class="summary-company">{{ selectedJob.company || '未注明公司' }}</p>
          <div class="job-meta">
            <span v-if="selectedJob.city">{{ selectedJob.city }}</span>
            <span v-if="selectedJob.salary">{{ selectedJob.salary }}</span>
            <span v-if="selectedJob.recruitmentBatch">{{ selectedJob.recruitmentBatch }}</span>
          </div>

          <p v-if="selectedJob.displayStatus === 'DEMO'" class="summary-demo">
            演示数据，仅用于功能体验，不代表真实在招职位。
          </p>

          <div class="summary-score">
            <template v-if="selectedRecommend">
              <div class="summary-score__row">
                <span class="summary-score__value">{{ selectedRecommend.matchScore }}</span>
                <span class="summary-score__unit">/ 100 匹配度</span>
              </div>
              <ul class="summary-reasons">
                <li v-for="reason in selectedRecommend.rankReasons" :key="reason">{{ reason }}</li>
              </ul>
            </template>
            <p v-else class="cf-note">
              暂无匹配度：先在简历中心生成简历版本，再回到这里查看匹配评分与推荐理由。
            </p>
          </div>

          <div class="summary-block">
            <span class="cf-eyebrow">岗位技能要求</span>
            <div class="job-skills">
              <el-tag
                v-for="skill in selectedSkills"
                :key="skill"
                size="small"
                effect="plain"
                type="primary"
                class="skill-tag"
              >
                {{ skill }}
              </el-tag>
              <span v-if="selectedSkills.length === 0" class="no-skills">尚未解析技能要求</span>
            </div>
          </div>

          <TrustPanel
            v-if="selectedDetail"
            class="summary-trust"
            :display-status="selectedDetail.displayStatus"
            :source-type="selectedDetail.sourceType"
            :source-name="selectedDetail.sourceName"
            :published-at="selectedDetail.publishedAt"
            :verified-at="selectedDetail.verifiedAt"
            :valid-until="selectedDetail.validUntil"
            :external-url="selectedDetail.externalUrl"
          />

          <div class="summary-actions">
            <el-button type="primary" @click="goMatch(selectedJob.id)">匹配分析</el-button>
            <el-button @click="openDetail(selectedJob.id)">查看详情</el-button>
            <a
              v-if="selectedDetail?.externalUrl"
              class="summary-link"
              :href="selectedDetail.externalUrl"
              target="_blank"
              rel="noopener noreferrer"
            >
              原招聘信息
            </a>
          </div>
        </template>

        <el-empty v-else description="选择左侧岗位查看匹配摘要" :image-size="64" />
      </aside>
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

          <TrustPanel
            class="drawer-trust"
            :display-status="drawerJob.displayStatus"
            :source-type="drawerJob.sourceType"
            :source-name="drawerJob.sourceName"
            :published-at="drawerJob.publishedAt"
            :verified-at="drawerJob.verifiedAt"
            :valid-until="drawerJob.validUntil"
            :external-url="drawerJob.externalUrl"
          />

          <div
            v-if="userStore.isAdmin && drawerJob.displayStatus !== 'DEMO'"
            class="drawer-verify"
            data-testid="jobs-verify-actions"
          >
            <span class="cf-eyebrow">管理员核验</span>
            <div class="drawer-verify__actions">
              <el-button
                size="small"
                type="success"
                plain
                :loading="verifyingStatus === 'VERIFIED'"
                data-testid="jobs-verify-verified"
                @click="handleVerify('VERIFIED')"
              >
                标记已核实
              </el-button>
              <el-button
                size="small"
                :loading="verifyingStatus === 'EXPIRED'"
                @click="handleVerify('EXPIRED')"
              >
                标记已过期
              </el-button>
              <el-button
                size="small"
                text
                :loading="verifyingStatus === 'UNVERIFIED'"
                @click="handleVerify('UNVERIFIED')"
              >
                撤回核实
              </el-button>
            </div>
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

    <el-dialog
      v-model="importVisible"
      title="导入外部岗位"
      width="560px"
      data-testid="jobs-import-dialog"
    >
      <p class="import-note">
        粘贴招聘链接或 JD 文本，系统创建仅自己可见的岗位（待核实）。
        请勿从招聘平台批量抓取数据，仅导入你已获得的岗位信息。
      </p>
      <el-form label-width="88px" label-position="left">
        <el-form-item label="岗位名称" required>
          <el-input v-model="importForm.position" data-testid="import-position" placeholder="如 Java 开发实习生" />
        </el-form-item>
        <el-form-item label="公司">
          <el-input v-model="importForm.company" placeholder="如 字节跳动" />
        </el-form-item>
        <el-form-item label="城市 / 薪资">
          <div class="import-inline">
            <el-input v-model="importForm.city" placeholder="城市" />
            <el-input v-model="importForm.salary" placeholder="薪资" />
          </div>
        </el-form-item>
        <el-form-item label="招聘链接">
          <el-input
            v-model="importForm.externalUrl"
            data-testid="import-external-url"
            placeholder="https://..."
          />
        </el-form-item>
        <el-form-item label="来源名称">
          <el-input v-model="importForm.sourceName" placeholder="如 企业官网 / 用户粘贴，默认用户粘贴" />
        </el-form-item>
        <el-form-item label="JD 内容">
          <el-input
            v-model="importForm.jdContent"
            type="textarea"
            :rows="6"
            data-testid="import-jd"
            placeholder="粘贴岗位描述文本"
          />
        </el-form-item>
        <el-form-item label="AI 解析">
          <el-switch v-model="importForm.parseJd" :disabled="!importForm.jdContent.trim()" />
          <span class="import-hint">导入后立即解析技能要求</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="importVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="importLoading"
          data-testid="import-submit"
          @click="submitImport"
        >
          导入岗位
        </el-button>
      </template>
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

.jobs-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: var(--cf-space-4);
  align-items: start;
}

.jobs-list-pane {
  min-height: 200px;
  background: var(--cf-card-bg);
  border: 1px solid var(--cf-border);
  border-radius: var(--cf-radius-md);
  overflow: hidden;
}

.job-list {
  display: flex;
  flex-direction: column;
}

.job-row {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: var(--cf-space-4);
  padding: var(--cf-space-4);
  border-bottom: 1px solid var(--cf-border);
  cursor: pointer;
  transition: background 0.15s ease;
}

.job-row:last-child {
  border-bottom: none;
}

.job-row:hover {
  background: var(--cf-surface-muted);
}

.job-row--active {
  background: var(--cf-primary-soft);
  box-shadow: inset 3px 0 0 var(--cf-primary);
}

.job-row-main {
  flex: 1;
  min-width: 0;
}

.job-row-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--cf-space-3);
}

.job-row-actions {
  display: flex;
  align-items: flex-start;
  flex-wrap: wrap;
  gap: var(--cf-space-2);
  flex-shrink: 0;
}

.job-company {
  margin-top: 2px;
  font-size: var(--cf-font-sm);
  color: var(--cf-text-secondary);
}

.job-company--link {
  cursor: pointer;
  color: var(--cf-primary);
}

.job-company--link:hover {
  text-decoration: underline;
}

.job-tag-group {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  justify-content: flex-end;
}

.job-position {
  font-size: 15px;
  font-weight: 600;
}

.job-meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--cf-space-3);
  margin-top: var(--cf-space-2);
  font-size: var(--cf-font-xs);
  color: var(--cf-text-secondary);
}

.job-skills {
  margin-top: var(--cf-space-2);
  min-height: 22px;
}

.skill-tag {
  margin-right: 6px;
  margin-bottom: 4px;
}

.no-skills {
  font-size: var(--cf-font-xs);
  color: var(--cf-text-secondary);
}

/* 右侧匹配摘要：随选中岗位更新 */
.jobs-summary {
  position: sticky;
  top: calc(var(--cf-header-height) + var(--cf-space-4));
  min-height: 240px;
  padding: var(--cf-space-4);
  background: var(--cf-card-bg);
  border: 1px solid var(--cf-border);
  border-radius: var(--cf-radius-md);
}

.summary-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--cf-space-2);
}

.summary-position {
  margin-top: var(--cf-space-3);
  font-size: var(--cf-font-lg);
}

.summary-company {
  margin-top: 2px;
  font-size: var(--cf-font-sm);
  color: var(--cf-text-secondary);
}

.summary-demo {
  margin-top: var(--cf-space-3);
  font-size: var(--cf-font-xs);
  color: var(--cf-warning);
}

.summary-score {
  margin-top: var(--cf-space-3);
  padding: var(--cf-space-3);
  border-radius: var(--cf-radius-control);
  background: var(--cf-surface-muted);
}

.summary-score__row {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.summary-score__value {
  font-size: var(--cf-font-3xl);
  font-weight: 700;
  line-height: 1.1;
  color: var(--cf-primary);
}

.summary-score__unit {
  font-size: var(--cf-font-xs);
  color: var(--cf-text-secondary);
}

.summary-reasons {
  margin: var(--cf-space-2) 0 0 var(--cf-space-4);
  padding: 0;
  font-size: var(--cf-font-xs);
  line-height: 1.7;
  color: var(--cf-text-secondary);
}

.summary-block {
  margin-top: var(--cf-space-4);
  display: flex;
  flex-direction: column;
  gap: var(--cf-space-2);
}

.summary-trust {
  margin-top: var(--cf-space-4);
}

.summary-actions {
  margin-top: var(--cf-space-4);
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--cf-space-2);
}

.summary-link {
  font-size: var(--cf-font-sm);
  color: var(--cf-primary);
}

.summary-link:hover {
  text-decoration: underline;
}

@media (max-width: 1199px) and (min-width: 1024px) {
  .jobs-layout {
    grid-template-columns: minmax(0, 1fr) 300px;
  }
}

@media (max-width: 1023px) {
  .jobs-layout {
    grid-template-columns: 1fr;
  }

  /* 窄屏时摘要下沉到列表下方，保留匹配信息而不是直接隐藏 */
  .jobs-summary {
    position: static;
  }

  .job-row {
    flex-direction: column;
  }

  .job-row-actions {
    justify-content: flex-end;
  }
}

.feedback-button {
  margin-left: 0;
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

.search-select {
  width: 140px;
}

.job-source-row {
  display: flex;
  align-items: center;
  gap: var(--cf-space-2);
  margin-top: var(--cf-space-2);
  min-height: 20px;
  flex-wrap: wrap;
}

.demo-hint {
  font-size: var(--cf-font-xs);
  color: var(--cf-warning);
}

.drawer-trust {
  margin-top: var(--cf-space-4);
}

.drawer-verify {
  margin-top: var(--cf-space-3);
  display: flex;
  flex-direction: column;
  gap: var(--cf-space-2);
}

.drawer-verify__actions {
  display: flex;
  align-items: center;
  gap: var(--cf-space-2);
  flex-wrap: wrap;
}

.import-note {
  margin-bottom: var(--cf-space-4);
  padding: var(--cf-space-3);
  border-radius: var(--cf-radius-control);
  background: var(--cf-surface-muted);
  color: var(--cf-text-secondary);
  font-size: var(--cf-font-sm);
  line-height: 1.6;
}

.import-inline {
  display: flex;
  gap: var(--cf-space-2);
  width: 100%;
}

.import-hint {
  margin-left: var(--cf-space-3);
  font-size: var(--cf-font-xs);
  color: var(--cf-text-tertiary);
}
</style>
