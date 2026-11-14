<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ChatDotRound,
  DataAnalysis,
  Document,
  Files,
  Plus,
  Promotion,
  Suitcase,
  User,
} from '@element-plus/icons-vue'

import {
  listApplications,
  listFollowUps,
  STATUS_LABELS,
  type ApplicationStatus,
  type FollowUpList,
} from '@/api/applications'
import { listResumes, type ResumeSummary } from '@/api/resume'
import { loadTemplateById } from '@/components/resume-template/loadTemplate'
import { useUserStore } from '@/stores/user'

/**
 * 工作台：欢迎区 + 最近简历 + 求职进度 + 待跟进提醒 + 常用入口。
 * 不做大面积宣传 Banner，重点是把用户带回简历工作区。
 */
const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const resumes = ref<ResumeSummary[]>([])
const followUps = ref<FollowUpList>({ count: 0, items: [] })
const applicationTotal = ref(0)

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了'
  if (hour < 12) return '早上好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

const displayName = computed(() => userStore.username || '同学')
const recentResumes = computed(() => resumes.value.slice(0, 3))
const followUpItems = computed(() => followUps.value.items.slice(0, 3))

const quickLinks = [
  { path: '/resumes', title: '简历中心', desc: '管理与版本', icon: Document },
  { path: '/jobs', title: '岗位中心', desc: '浏览在招岗位', icon: Suitcase },
  { path: '/applications', title: '投递管理', desc: '状态与提醒', icon: Promotion },
  { path: '/chat', title: 'AI 对话建档', desc: '补充个人档案', icon: ChatDotRound },
  { path: '/profile', title: '个人档案', desc: '经历与证据链', icon: User },
  { path: '/analytics', title: '数据分析', desc: '就业数据洞察', icon: DataAnalysis },
]

function templateName(templateId: string | null): string {
  if (!templateId) {
    return '未选择模板'
  }
  return loadTemplateById(templateId)?.name ?? '自定义模板'
}

function statusLabel(status: ApplicationStatus): string {
  return STATUS_LABELS[status] ?? status
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
  if (diff < minute) return '刚刚'
  if (diff < 60 * minute) return `${Math.floor(diff / minute)} 分钟前`
  if (diff < 24 * 60 * minute) return `${Math.floor(diff / (60 * minute))} 小时前`
  return date.toLocaleDateString('zh-CN')
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const [resumeResult, followUpResult, applicationResult] = await Promise.all([
      listResumes().catch(() => null),
      listFollowUps().catch(() => null),
      listApplications({ page: 1, size: 1 }).catch(() => null),
    ])
    resumes.value = resumeResult?.data ?? []
    followUps.value = followUpResult?.data ?? { count: 0, items: [] }
    applicationTotal.value = applicationResult?.data.total ?? 0
  } finally {
    loading.value = false
  }
}

function goWorkspace(resume: ResumeSummary): void {
  void router.push(`/workspace/${resume.id}`)
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="dashboard">
    <section class="welcome">
      <div class="welcome__text">
        <h1>{{ greeting }}，{{ displayName }}</h1>
        <p>继续完善简历、跟进投递进度，把求职流程一次做完。</p>
      </div>
      <div class="welcome__actions">
        <el-button type="primary" data-testid="dashboard-new-resume" @click="router.push('/workspace/new')">
          <el-icon><Plus /></el-icon>
          <span>新建简历</span>
        </el-button>
        <el-button @click="router.push('/jobs')">浏览岗位</el-button>
      </div>
    </section>

    <section class="stats">
      <button type="button" class="stat" @click="router.push('/resumes')">
        <span class="stat__label">简历</span>
        <span class="stat__value">{{ resumes.length }}</span>
        <span class="stat__unit">份</span>
      </button>
      <button type="button" class="stat" @click="router.push('/applications')">
        <span class="stat__label">投递</span>
        <span class="stat__value">{{ applicationTotal }}</span>
        <span class="stat__unit">条</span>
      </button>
      <button type="button" class="stat" :class="{ 'stat--warn': followUps.count > 0 }" @click="router.push('/applications')">
        <span class="stat__label">待跟进</span>
        <span class="stat__value">{{ followUps.count }}</span>
        <span class="stat__unit">条</span>
      </button>
    </section>

    <div class="panels">
      <section class="panel">
        <header class="panel__head">
          <h2>最近编辑的简历</h2>
          <el-button link size="small" @click="router.push('/resumes')">查看全部</el-button>
        </header>

        <div v-if="recentResumes.length === 0" class="panel__empty">
          <p>还没有简历，从八个步骤开始创建第一份。</p>
          <el-button size="small" type="primary" @click="router.push('/workspace/new')">
            新建简历
          </el-button>
        </div>

        <ul v-else class="recent-list">
          <li v-for="resume in recentResumes" :key="resume.id" class="recent-list__item">
            <span class="recent-list__icon">
              <el-icon><Files /></el-icon>
            </span>
            <div class="recent-list__main">
              <button
                type="button"
                class="recent-list__name"
                :title="resume.resumeName"
                @click="goWorkspace(resume)"
              >
                {{ resume.resumeName }}
              </button>
              <span class="recent-list__meta">
                {{ resume.currentVersionNumber ? `v${resume.currentVersionNumber}` : '草稿中' }}
                · {{ templateName(resume.templateId) }} · {{ formatTime(resume.updatedAt) }}
              </span>
            </div>
            <el-button size="small" @click="goWorkspace(resume)">继续编辑</el-button>
          </li>
        </ul>
      </section>

      <section
        class="panel"
        :class="{ 'panel--warn': followUps.count > 0 }"
        data-testid="follow-up-reminder"
      >
        <header class="panel__head">
          <h2>待跟进投递</h2>
          <el-button link size="small" @click="router.push('/applications')">查看全部</el-button>
        </header>

        <div v-if="followUpItems.length === 0" class="panel__empty">
          <p>暂无需要跟进的投递，超 3 天未更新的投递会出现在这里。</p>
        </div>

        <ul v-else class="follow-list">
          <li v-for="item in followUpItems" :key="item.applicationId" class="follow-list__item">
            <div class="follow-list__main">
              <span class="follow-list__position">{{ item.position }}</span>
              <span class="follow-list__meta">
                {{ item.company || '未知企业' }} · {{ statusLabel(item.status) }}
              </span>
            </div>
            <el-button size="small" data-testid="goto-applications" @click="router.push('/applications')">
              去处理
            </el-button>
          </li>
        </ul>
      </section>
    </div>

    <section class="quick">
      <h2>常用功能</h2>
      <div class="quick-grid">
        <button
          v-for="link in quickLinks"
          :key="link.path"
          type="button"
          class="quick-card"
          @click="router.push(link.path)"
        >
          <el-icon class="quick-card__icon"><component :is="link.icon" /></el-icon>
          <span class="quick-card__title">{{ link.title }}</span>
          <span class="quick-card__desc">{{ link.desc }}</span>
        </button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: var(--cf-space-5);
  min-height: 100%;
}

.welcome {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--cf-space-4);
  padding: var(--cf-space-5);
  background: var(--cf-card-bg);
  border: 1px solid var(--cf-border);
  border-radius: var(--cf-radius-lg);
}

.welcome h1 {
  margin: 0;
  font-size: var(--cf-font-2xl);
  color: var(--cf-text);
}

.welcome p {
  margin: var(--cf-space-1) 0 0;
  font-size: var(--cf-font-sm);
  color: var(--cf-text-secondary);
}

.welcome__actions {
  display: flex;
  gap: var(--cf-space-2);
  flex-shrink: 0;
}

.stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--cf-space-4);
}

.stat {
  display: flex;
  align-items: baseline;
  gap: 6px;
  padding: var(--cf-space-4);
  border: 1px solid var(--cf-border);
  border-radius: var(--cf-radius-lg);
  background: var(--cf-card-bg);
  font-family: inherit;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.15s ease;
}

.stat:hover {
  border-color: var(--cf-primary-light);
}

.stat__label {
  font-size: var(--cf-font-sm);
  color: var(--cf-text-secondary);
}

.stat__value {
  margin-left: auto;
  font-size: var(--cf-font-2xl);
  font-weight: 700;
  color: var(--cf-text);
  font-variant-numeric: tabular-nums;
}

.stat__unit {
  font-size: var(--cf-font-xs);
  color: var(--cf-text-tertiary);
}

.stat--warn .stat__value {
  color: var(--cf-warning);
}

.panels {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--cf-space-4);
}

.panel {
  display: flex;
  flex-direction: column;
  gap: var(--cf-space-3);
  padding: var(--cf-space-4);
  background: var(--cf-card-bg);
  border: 1px solid var(--cf-border);
  border-radius: var(--cf-radius-lg);
}

.panel--warn {
  border-color: #fcd34d;
  background: #fffdf5;
}

.panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.panel__head h2 {
  margin: 0;
  font-size: var(--cf-font-md);
  color: var(--cf-text);
}

.panel__empty {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--cf-space-2);
  padding: var(--cf-space-3) 0;
  color: var(--cf-text-tertiary);
  font-size: var(--cf-font-sm);
}

.panel__empty p {
  margin: 0;
  line-height: 1.7;
}

.recent-list,
.follow-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
}

.recent-list__item,
.follow-list__item {
  display: flex;
  align-items: center;
  gap: var(--cf-space-3);
  padding: var(--cf-space-2) 0;
  border-bottom: 1px solid var(--cf-border);
}

.recent-list__item:last-child,
.follow-list__item:last-child {
  border-bottom: none;
}

.recent-list__icon {
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--cf-radius-sm);
  background: var(--cf-primary-soft);
  color: var(--cf-primary);
}

.recent-list__main,
.follow-list__main {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  flex: 1;
}

.recent-list__name {
  border: none;
  background: transparent;
  padding: 0;
  font-family: inherit;
  font-size: var(--cf-font-sm);
  font-weight: 600;
  color: var(--cf-text);
  text-align: left;
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recent-list__name:hover {
  color: var(--cf-primary);
}

.recent-list__meta,
.follow-list__meta {
  font-size: var(--cf-font-xs);
  color: var(--cf-text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.follow-list__position {
  font-size: var(--cf-font-sm);
  font-weight: 600;
  color: var(--cf-text);
}

.quick h2 {
  margin: 0 0 var(--cf-space-3);
  font-size: var(--cf-font-md);
  color: var(--cf-text);
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(170px, 1fr));
  gap: var(--cf-space-3);
}

.quick-card {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: var(--cf-space-3);
  border: 1px solid var(--cf-border);
  border-radius: var(--cf-radius-md);
  background: var(--cf-card-bg);
  font-family: inherit;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.15s ease, transform 0.15s ease;
}

.quick-card:hover {
  border-color: var(--cf-primary-light);
  transform: translateY(-2px);
}

.quick-card__icon {
  color: var(--cf-primary);
  font-size: 18px;
}

.quick-card__title {
  font-size: var(--cf-font-sm);
  font-weight: 600;
  color: var(--cf-text);
}

.quick-card__desc {
  font-size: var(--cf-font-xs);
  color: var(--cf-text-tertiary);
}

@media (max-width: 1023px) {
  .panels {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 640px) {
  .welcome {
    flex-direction: column;
    align-items: flex-start;
  }

  .stats {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
