<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { computed, ref } from 'vue'

import {
  confirmEdit,
  getStarFollowUps,
  rejectEdit,
  structureStar,
  type EditHistoryItem,
  type StarFollowUp,
} from '@/api/resumeDraft'
import EmptyState from '@/components/common/EmptyState.vue'
import { useResumeDraftStore } from '@/stores/resumeDraft'
import { starCompleteness } from '@/utils/resumeWizard'

import CandidateDiffDialog from './CandidateDiffDialog.vue'

/** 第 4 步：逐条编辑 STAR，AI 结构化结果必须经用户确认后才写入草稿。 */
interface StarItem {
  id: string
  title: string
  subtitle: string
  star: { situation: string; task: string; action: string; result: string }
}

const store = useResumeDraftStore()

const STAR_FIELDS = [
  { key: 'situation', label: '情境（S）', placeholder: '事情发生在什么背景和环境下' },
  { key: 'task', label: '任务（T）', placeholder: '你当时需要完成什么目标或解决什么问题' },
  { key: 'action', label: '行动（A）', placeholder: '你具体做了哪些事情，用了什么方法' },
  { key: 'result', label: '结果（R）', placeholder: '最终取得了什么结果，尽量用数据说明' },
] as const

const QUESTION_LABELS: Record<string, string> = {
  SITUATION: '情境',
  TASK: '任务',
  ACTION: '行动',
  RESULT: '结果',
}

const diffVisible = ref(false)
const pendingHistory = ref<EditHistoryItem | null>(null)
const busy = ref(false)
const followUps = ref<Record<string, StarFollowUp>>({})
const followUpLoading = ref<Record<string, boolean>>({})
const structuring = ref<Record<string, boolean>>({})

const items = computed<StarItem[]>(() => [
  ...store.content.experiences.map((item) => ({
    id: item.id,
    title: item.company || item.position || '未命名经历',
    subtitle: `工作/实习经历${item.position ? ` · ${item.position}` : ''}`,
    star: item.star,
  })),
  ...store.content.projects.map((item) => ({
    id: item.id,
    title: item.projectName || '未命名项目',
    subtitle: `项目经历${item.role ? ` · ${item.role}` : ''}`,
    star: item.star,
  })),
])

async function goExperienceStep(): Promise<void> {
  await store.flush()
  store.setStep(3)
}

function questionsFor(id: string): StarFollowUp['questions'] {
  return followUps.value[id]?.questions ?? []
}

async function fetchFollowUps(item: StarItem): Promise<void> {
  if (store.draftId == null) {
    ElMessage.warning('草稿尚未准备就绪，请稍后再试')
    return
  }
  followUpLoading.value[item.id] = true
  try {
    const result = await getStarFollowUps(store.draftId, item.id)
    followUps.value[item.id] = result.data
    if (result.data.questions.length === 0) {
      ElMessage.success('这条经历的 STAR 信息已经比较完整')
    }
  } catch {
    ElMessage.error('追问建议获取失败，请稍后重试')
  } finally {
    followUpLoading.value[item.id] = false
  }
}

function hasAnyContent(item: StarItem): boolean {
  return Object.values(item.star).some((value) => value.trim())
}

async function structure(item: StarItem): Promise<void> {
  if (store.draftId == null) {
    ElMessage.warning('草稿尚未准备就绪，请稍后再试')
    return
  }
  if (!hasAnyContent(item)) {
    ElMessage.warning('请先填写 STAR 内容，再使用 AI 结构化')
    return
  }
  structuring.value[item.id] = true
  try {
    const result = await structureStar(store.draftId, item.id, {
      situation: item.star.situation,
      task: item.star.task,
      action: item.star.action,
      result: item.star.result,
    })
    pendingHistory.value = result.data
    diffVisible.value = true
  } catch (error) {
    ElMessage.error(describeAiError(error))
  } finally {
    structuring.value[item.id] = false
  }
}

function describeAiError(error: unknown): string {
  const status = (error as { response?: { status?: number } })?.response?.status
  if (status === 502) {
    return 'AI 服务暂时不可用，已保留你填写的内容，请稍后重试'
  }
  return 'AI 处理失败，已保留你填写的内容，请稍后重试'
}

async function handleConfirm(finalContent: unknown): Promise<void> {
  const history = pendingHistory.value
  if (!history) {
    return
  }
  busy.value = true
  try {
    await confirmEdit(history.id, finalContent)
    await store.refreshAfterApply()
    diffVisible.value = false
    pendingHistory.value = null
    ElMessage.success('已应用 AI 修改，内容已更新到简历')
  } catch {
    ElMessage.error('应用修改失败，请稍后重试')
  } finally {
    busy.value = false
  }
}

async function handleReject(): Promise<void> {
  const history = pendingHistory.value
  if (!history) {
    diffVisible.value = false
    return
  }
  busy.value = true
  try {
    await rejectEdit(history.id)
    diffVisible.value = false
    pendingHistory.value = null
    ElMessage.success('已拒绝本次修改，原文保持不变')
  } catch {
    ElMessage.error('拒绝修改失败，请稍后重试')
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section class="step-star">
    <header class="step-head">
      <h2 class="step-head__title">STAR 结构化</h2>
      <p class="step-head__desc">
        用情境、任务、行动、结果四要素把经历写清楚；AI 只生成候选，确认后才会写入简历。
      </p>
    </header>

    <EmptyState
      v-if="items.length === 0"
      description="还没有经历或项目，请先回到第 3 步补充"
      action-text="返回经历补充"
      @action="goExperienceStep"
    />

    <div v-else class="star-list">
      <article v-for="item in items" :key="item.id" class="star-card">
        <header class="star-card__head">
          <div class="star-card__text">
            <h3 class="star-card__title">{{ item.title }}</h3>
            <p class="star-card__subtitle">{{ item.subtitle }}</p>
          </div>
          <div class="star-card__progress">
            <el-progress
              :percentage="starCompleteness(item.star)"
              :stroke-width="6"
              :show-text="false"
            />
            <span class="star-card__percent">{{ starCompleteness(item.star) }}%</span>
          </div>
        </header>

        <div class="star-grid">
          <div v-for="field in STAR_FIELDS" :key="field.key" class="star-field">
            <label class="star-field__label">{{ field.label }}</label>
            <el-input
              v-model="item.star[field.key]"
              type="textarea"
              :rows="2"
              :placeholder="field.placeholder"
            />
          </div>
        </div>

        <div class="star-card__actions">
          <el-button
            size="small"
            :loading="followUpLoading[item.id]"
            @click="fetchFollowUps(item)"
          >
            获取追问建议
          </el-button>
          <el-button
            size="small"
            type="primary"
            plain
            :loading="structuring[item.id]"
            @click="structure(item)"
          >
            AI 结构化
          </el-button>
        </div>

        <el-alert
          v-if="followUps[item.id]"
          class="star-questions"
          type="info"
          :closable="false"
          title="可以补充这些信息"
        >
          <ul v-if="questionsFor(item.id).length > 0" class="star-questions__list">
            <li v-for="question in questionsFor(item.id)" :key="`${question.key}-${question.question}`">
              <span class="star-questions__tag">{{ QUESTION_LABELS[question.key] ?? '补充' }}</span>
              {{ question.question }}
            </li>
          </ul>
          <p v-else class="star-questions__empty">这条经历的 STAR 信息已经比较完整。</p>
        </el-alert>
      </article>
    </div>

    <CandidateDiffDialog
      v-model="diffVisible"
      :history="pendingHistory"
      :busy="busy"
      @confirm="handleConfirm"
      @reject="handleReject"
    />
  </section>
</template>

<style scoped>
.step-star {
  max-width: 900px;
}

.step-head {
  margin-bottom: var(--cf-space-4);
}

.step-head__title {
  margin: 0;
  font-size: var(--cf-font-lg);
  font-weight: 600;
  color: var(--cf-text);
}

.step-head__desc {
  margin: var(--cf-space-1) 0 0;
  font-size: var(--cf-font-sm);
  color: var(--cf-text-secondary);
}

.star-list {
  display: flex;
  flex-direction: column;
  gap: var(--cf-space-4);
}

.star-card {
  padding: var(--cf-space-4);
  border: 1px solid var(--cf-border);
  border-radius: var(--cf-radius-md);
  background: var(--cf-card-bg);
}

.star-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--cf-space-4);
  margin-bottom: var(--cf-space-3);
}

.star-card__title {
  margin: 0;
  font-size: var(--cf-font-base);
  font-weight: 600;
  color: var(--cf-text);
}

.star-card__subtitle {
  margin: var(--cf-space-1) 0 0;
  font-size: var(--cf-font-xs);
  color: var(--cf-text-secondary);
}

.star-card__progress {
  display: flex;
  align-items: center;
  gap: var(--cf-space-2);
  width: 150px;
  flex-shrink: 0;
}

.star-card__progress :deep(.el-progress) {
  flex: 1;
}

.star-card__percent {
  font-size: var(--cf-font-xs);
  color: var(--cf-text-secondary);
  width: 34px;
  text-align: right;
}

.star-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--cf-space-3);
}

.star-field {
  display: flex;
  flex-direction: column;
  gap: var(--cf-space-1);
}

.star-field__label {
  font-size: var(--cf-font-xs);
  color: var(--cf-text-secondary);
}

.star-card__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--cf-space-2);
  margin-top: var(--cf-space-3);
}

.star-questions {
  margin-top: var(--cf-space-3);
}

.star-questions__list {
  margin: 0;
  padding-left: 18px;
  font-size: var(--cf-font-sm);
  line-height: 1.8;
}

.star-questions__tag {
  display: inline-block;
  margin-right: var(--cf-space-1);
  padding: 0 6px;
  border-radius: 4px;
  background: var(--cf-primary-soft);
  color: var(--cf-primary);
  font-size: var(--cf-font-xs);
}

.star-questions__empty {
  margin: 0;
  font-size: var(--cf-font-sm);
}

@media (max-width: 767px) {
  .star-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .star-card__head {
    flex-direction: column;
  }

  .star-card__progress {
    width: 100%;
  }
}
</style>
