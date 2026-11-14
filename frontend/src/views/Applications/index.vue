<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  STATUS_LABELS,
  STATUS_TRANSITIONS,
  getApplication,
  getApplicationHistory,
  handleFollowUp,
  listApplications,
  listFollowUps,
  updateApplicationStatus,
  type ApplicationHistoryItem,
  type ApplicationItem,
  type ApplicationStatus,
  type FollowUpItem,
  type FollowUpList,
} from '@/api/applications'

const activeTab = ref<'list' | 'follow'>('list')

const items = ref<ApplicationItem[]>([])
const total = ref(0)
const page = ref(1)
const size = 10
const statusFilter = ref('')
const loading = ref(false)

const followUps = ref<FollowUpList>({ count: 0, items: [] })
const followLoading = ref(false)

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<ApplicationItem | null>(null)
const history = ref<ApplicationHistoryItem[]>([])

const statusDialogVisible = ref(false)
const statusSubmitting = ref(false)
const statusForm = reactive({
  id: 0,
  position: '',
  current: '' as ApplicationStatus | '',
  status: '',
  note: '',
  allowed: [] as ApplicationStatus[],
})

const followDialogVisible = ref(false)
const followSubmitting = ref(false)
const followForm = reactive({
  id: 0,
  position: '',
  note: '',
  nextFollowUpAt: '',
})

const statusOptions: ApplicationStatus[] = [
  'PENDING',
  'APPLIED',
  'READ',
  'INTERVIEW_INVITED',
  'INTERVIEWING',
  'OFFERED',
  'REJECTED',
  'WITHDRAWN',
  'FAILED',
  'NO_RESPONSE',
]

function errorMessage(error: unknown, fallback: string): string {
  const err = error as { response?: { data?: { message?: string } } }
  return err.response?.data?.message || fallback
}

function statusLabel(status: string): string {
  return STATUS_LABELS[status as ApplicationStatus] ?? status
}

function statusTagType(status: string): 'success' | 'warning' | 'danger' | 'info' | 'primary' {
  switch (status) {
    case 'OFFERED':
      return 'success'
    case 'REJECTED':
    case 'FAILED':
      return 'danger'
    case 'READ':
    case 'INTERVIEW_INVITED':
    case 'INTERVIEWING':
      return 'warning'
    case 'PENDING':
    case 'WITHDRAWN':
      return 'info'
    default:
      return 'primary'
  }
}

function formatTime(value: string | null): string {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 16)
}

async function loadApplications(): Promise<void> {
  loading.value = true
  try {
    const res = await listApplications({
      page: page.value,
      size,
      status: statusFilter.value || undefined,
    })
    items.value = res.data.items
    total.value = res.data.total
  } catch (error) {
    ElMessage.error(errorMessage(error, '投递记录加载失败，请稍后重试'))
  } finally {
    loading.value = false
  }
}

async function loadFollowUps(): Promise<void> {
  followLoading.value = true
  try {
    followUps.value = (await listFollowUps()).data
  } catch (error) {
    ElMessage.error(errorMessage(error, '待跟进提醒加载失败'))
  } finally {
    followLoading.value = false
  }
}

function handleFilterChange(): void {
  page.value = 1
  void loadApplications()
}

function onPageChange(newPage: number): void {
  page.value = newPage
  void loadApplications()
}

function refreshAll(): void {
  void loadApplications()
  void loadFollowUps()
}

async function openDetail(row: ApplicationItem): Promise<void> {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = row
  try {
    const [detailRes, historyRes] = await Promise.all([
      getApplication(row.id),
      getApplicationHistory(row.id),
    ])
    detail.value = detailRes.data
    history.value = historyRes.data
  } catch (error) {
    ElMessage.error(errorMessage(error, '投递详情加载失败'))
  } finally {
    detailLoading.value = false
  }
}

function openStatusDialog(row: ApplicationItem): void {
  const allowed = STATUS_TRANSITIONS[row.status] ?? []
  if (allowed.length === 0) {
    ElMessage.info('该状态为终态，无需再更新')
    return
  }
  statusForm.id = row.id
  statusForm.position = row.position
  statusForm.current = row.status
  statusForm.allowed = allowed
  statusForm.status = allowed[0]
  statusForm.note = ''
  statusDialogVisible.value = true
}

async function submitStatus(): Promise<void> {
  if (!statusForm.status) {
    ElMessage.warning('请选择新的状态')
    return
  }
  statusSubmitting.value = true
  try {
    await updateApplicationStatus(statusForm.id, {
      status: statusForm.status,
      note: statusForm.note.trim() || undefined,
    })
    ElMessage.success('状态已更新，并已重置跟进提醒')
    statusDialogVisible.value = false
    refreshAll()
    if (detailVisible.value && detail.value?.id === statusForm.id) {
      void openDetail(detail.value)
    }
  } catch (error) {
    ElMessage.error(errorMessage(error, '状态更新失败'))
  } finally {
    statusSubmitting.value = false
  }
}

function openFollowDialog(item: FollowUpItem): void {
  followForm.id = item.applicationId
  followForm.position = item.position
  followForm.note = ''
  followForm.nextFollowUpAt = ''
  followDialogVisible.value = true
}

async function submitFollowUp(): Promise<void> {
  followSubmitting.value = true
  try {
    await handleFollowUp(followForm.id, {
      action: 'COMPLETED',
      note: followForm.note.trim() || undefined,
      nextFollowUpAt: followForm.nextFollowUpAt || undefined,
    })
    ElMessage.success('已记录本次跟进')
    followDialogVisible.value = false
    refreshAll()
  } catch (error) {
    ElMessage.error(errorMessage(error, '处理提醒失败'))
  } finally {
    followSubmitting.value = false
  }
}

onMounted(() => {
  refreshAll()
})
</script>

<template>
  <div class="applications-page">
    <header class="page-head">
      <div>
        <p class="eyebrow">APPLICATIONS</p>
        <h2>投递管理</h2>
        <p class="subtitle">
          投递关联具体简历版本；状态由后端状态机校验，每次流转留痕，超期未更新自动进入待跟进提醒。
        </p>
      </div>
      <el-button data-testid="applications-refresh" @click="refreshAll">刷新</el-button>
    </header>

    <el-tabs v-model="activeTab" class="applications-tabs">
      <el-tab-pane name="list">
        <template #label>
          <span>我的投递<span class="tab-count">{{ total }}</span></span>
        </template>

        <el-card shadow="never">
          <div class="filter-row">
            <el-select
              v-model="statusFilter"
              data-testid="status-filter"
              placeholder="全部状态"
              clearable
              class="status-select"
              @change="handleFilterChange"
            >
              <el-option
                v-for="status in statusOptions"
                :key="status"
                :label="statusLabel(status)"
                :value="status"
              />
            </el-select>
          </div>

          <el-table
            v-loading="loading"
            :data="items"
            data-testid="applications-table"
            empty-text="还没有投递记录，去岗位中心选择岗位并生成投递建议吧"
          >
            <el-table-column label="岗位" min-width="200">
              <template #default="{ row }">
                <div class="job-cell">
                  <span class="job-position">{{ row.position }}</span>
                  <span class="job-company">
                    {{ row.company || '未注明公司' }}
                    <template v-if="row.city"> · {{ row.city }}</template>
                  </span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="简历版本" width="110">
              <template #default="{ row }">
                v{{ row.versionNumber ?? '-' }}
              </template>
            </el-table-column>
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <el-tag size="small" :type="statusTagType(row.status)" effect="plain">
                  {{ statusLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="投递时间" width="150">
              <template #default="{ row }">{{ formatTime(row.appliedAt) }}</template>
            </el-table-column>
            <el-table-column label="最近更新" width="150">
              <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <el-button size="small" link @click="openDetail(row)">详情</el-button>
                <el-button size="small" link type="primary" @click="openStatusDialog(row)">
                  更新状态
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <div v-if="total > size" class="pagination-row">
            <el-pagination
              layout="prev, pager, next, total"
              :total="total"
              :page-size="size"
              :current-page="page"
              @current-change="onPageChange"
            />
          </div>
        </el-card>
      </el-tab-pane>

      <el-tab-pane name="follow">
        <template #label>
          <span>
            待跟进
            <el-badge
              v-if="followUps.count > 0"
              :value="followUps.count"
              class="follow-badge"
            />
          </span>
        </template>

        <el-card v-loading="followLoading" shadow="never">
          <p class="follow-hint">
            规则：APPLIED / READ / INTERVIEW_INVITED / NO_RESPONSE 且距最近更新超过 3 天（或已到下次提醒时间）。
          </p>
          <el-empty
            v-if="followUps.count === 0"
            description="暂无需要跟进的投递"
            :image-size="80"
          />
          <div v-else class="follow-list">
            <div
              v-for="item in followUps.items"
              :key="item.applicationId"
              class="follow-item"
              data-testid="follow-item"
            >
              <div class="follow-main">
                <p class="trigger-position">{{ item.position }}</p>
                <p class="trigger-company">
                  {{ item.company || '未注明公司' }}
                  · 最近更新 {{ formatTime(item.lastUpdateAt) }}
                </p>
              </div>
              <div class="follow-side">
                <el-tag size="small" :type="statusTagType(item.status)" effect="plain">
                  {{ statusLabel(item.status) }}
                </el-tag>
                <span class="days" data-testid="follow-days">{{ item.daysNoResponse }} 天未响应</span>
                <el-button size="small" type="primary" plain @click="openFollowDialog(item)">
                  已跟进
                </el-button>
              </div>
            </div>
          </div>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <el-drawer v-model="detailVisible" title="投递详情" size="460px">
      <div v-loading="detailLoading" class="detail-body">
        <template v-if="detail">
          <h3 class="detail-position">{{ detail.position }}</h3>
          <p class="detail-company">{{ detail.company || '未注明公司' }}</p>
          <div class="detail-meta">
            <span v-if="detail.city">📍 {{ detail.city }}</span>
            <span v-if="detail.salary">💰 {{ detail.salary }}</span>
            <span>简历 v{{ detail.versionNumber ?? '-' }}</span>
          </div>
          <div class="detail-status">
            <span>当前状态</span>
            <el-tag :type="statusTagType(detail.status)" effect="plain">
              {{ statusLabel(detail.status) }}
            </el-tag>
          </div>

          <el-divider content-position="left">状态时间轴</el-divider>
          <el-timeline v-if="history.length">
            <el-timeline-item
              v-for="entry in history"
              :key="entry.id"
              :timestamp="formatTime(entry.createdAt)"
              placement="top"
            >
              <p class="timeline-status">
                {{ statusLabel(entry.oldStatus) }} → {{ statusLabel(entry.newStatus) }}
              </p>
              <p v-if="entry.note" class="timeline-note">{{ entry.note }}</p>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="暂无状态历史" :image-size="60" />

          <el-button
            class="detail-update"
            type="primary"
            plain
            @click="openStatusDialog(detail)"
          >
            更新状态
          </el-button>
        </template>
      </div>
    </el-drawer>

    <el-dialog v-model="statusDialogVisible" title="更新投递状态" width="460px">
      <el-form label-width="90px">
        <el-form-item label="岗位">
          <span>{{ statusForm.position }}</span>
        </el-form-item>
        <el-form-item label="当前状态">
          <el-tag :type="statusTagType(statusForm.current)" effect="plain">
            {{ statusLabel(statusForm.current) }}
          </el-tag>
        </el-form-item>
        <el-form-item label="新状态">
          <el-select v-model="statusForm.status" data-testid="status-select" class="full-width">
            <el-option
              v-for="status in statusForm.allowed"
              :key="status"
              :label="statusLabel(status)"
              :value="status"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="statusForm.note"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="可选：如 HR 反馈、面试安排等"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="statusDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="statusSubmitting" @click="submitStatus">
          确认更新
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="followDialogVisible" title="处理跟进提醒" width="460px">
      <el-form label-width="110px">
        <el-form-item label="岗位">
          <span>{{ followForm.position }}</span>
        </el-form-item>
        <el-form-item label="跟进说明">
          <el-input
            v-model="followForm.note"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="可选：如已发送邮件询问进度"
          />
        </el-form-item>
        <el-form-item label="下次提醒">
          <el-date-picker
            v-model="followForm.nextFollowUpAt"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="不填则默认 3 天后"
            class="full-width"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="followDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="followSubmitting" @click="submitFollowUp">
          确认已跟进
        </el-button>
      </template>
    </el-dialog>
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

.tab-count {
  margin-left: 6px;
  color: var(--cf-text-secondary);
  font-size: 12px;
}

.filter-row {
  margin-bottom: 12px;
}

.status-select {
  width: 180px;
}

.job-cell {
  display: flex;
  flex-direction: column;
}

.job-position {
  font-weight: 600;
}

.job-company {
  margin-top: 2px;
  font-size: 12px;
  color: var(--cf-text-secondary);
}

.pagination-row {
  margin-top: 16px;
  display: flex;
  justify-content: center;
}

.follow-hint {
  margin-bottom: 12px;
  font-size: 12px;
  color: var(--cf-text-secondary);
}

.follow-badge {
  margin-left: 6px;
}

.follow-list {
  display: flex;
  flex-direction: column;
}

.follow-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 12px 0;
  border-bottom: 1px dashed var(--cf-border);
}

.follow-item:last-child {
  border-bottom: none;
}

.trigger-position {
  font-weight: 600;
}

.trigger-company {
  margin-top: 2px;
  font-size: 12px;
  color: var(--cf-text-secondary);
}

.follow-side {
  display: flex;
  align-items: center;
  gap: 10px;
}

.days {
  font-size: 12px;
  color: #b45309;
}

.detail-body {
  min-height: 200px;
}

.detail-position {
  font-size: 18px;
}

.detail-company {
  margin-top: 4px;
  color: var(--cf-text-secondary);
}

.detail-meta {
  display: flex;
  gap: 12px;
  margin-top: 10px;
  font-size: 13px;
  color: var(--cf-text-secondary);
}

.detail-status {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 16px;
  font-size: 13px;
}

.timeline-status {
  font-size: 13px;
  font-weight: 600;
}

.timeline-note {
  margin-top: 4px;
  font-size: 12px;
  color: var(--cf-text-secondary);
}

.detail-update {
  margin-top: 16px;
}

.full-width {
  width: 100%;
}
</style>
