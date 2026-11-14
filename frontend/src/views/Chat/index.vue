<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { FolderOpened, Plus, Promotion } from '@element-plus/icons-vue'
import {
  createSession,
  listMessages,
  listSessions,
  sendMessage,
  type ChatSession,
} from '@/api/chat'
import type { FileAnalysisResult } from '@/api/files'
import {
  confirmOptimizationTask,
  getOptimizationTask,
  replyOptimizationTask,
  type OptimizationConfirmResult,
  type OptimizationTask,
} from '@/api/optimization'
import DocumentAnalyzer from '@/components/DocumentAnalyzer.vue'
import EvidenceConfirmDialog from '@/components/EvidenceConfirmDialog.vue'
import { useEvidenceConfirm } from '@/composables/useEvidenceConfirm'

interface UiMessage {
  id: number
  role: 'USER' | 'ASSISTANT'
  content: string
  createdAt?: string
  missingFields?: string[]
}

const route = useRoute()
const router = useRouter()

const sessions = ref<ChatSession[]>([])
const activeSessionId = ref<number | null>(null)
const messages = ref<UiMessage[]>([])
const input = ref('')
const loadingSessions = ref(false)
const loadingMessages = ref(false)
const sending = ref(false)
const messageListRef = ref<HTMLElement>()
const showAnalyzer = ref(false)
const lastAnalysis = ref<FileAnalysisResult | null>(null)

// 反向优化任务模式（步骤 3）：从匹配页差距项「追问补充」进入 ?taskId=xx
const activeTask = ref<OptimizationTask | null>(null)
const confirming = ref(false)
const confirmResult = ref<OptimizationConfirmResult | null>(null)
const showConfirmResult = ref(false)

const {
  visible: evidenceVisible,
  submitting: evidenceSubmitting,
  groups: evidenceGroups,
  pendingCount: evidencePendingCount,
  refresh: refreshEvidence,
  open: openEvidence,
  afterSend: afterSendEvidence,
  confirmSelected: confirmSelectedEvidence,
  rejectSelected: rejectSelectedEvidence,
} = useEvidenceConfirm()

let tempId = -1

const activeSession = computed(
  () => sessions.value.find((s) => s.id === activeSessionId.value) ?? null,
)
const canSend = computed(
  () => !!activeSessionId.value && input.value.trim().length > 0 && !sending.value,
)
const showEmpty = computed(() => !loadingMessages.value && messages.value.length === 0 && !sending.value)

/** 当前会话是否为反向优化任务会话 */
const isTaskChat = computed(
  () => !!activeTask.value && activeTask.value.chatSessionId === activeSessionId.value,
)
const canConfirmTask = computed(
  () =>
    isTaskChat.value &&
    activeTask.value?.status === 'USER_CONFIRMATION' &&
    !confirming.value,
)

function taskStatusText(status: string): string {
  switch (status) {
    case 'WAITING_USER':
      return 'AI 正在等你回答追问'
    case 'USER_CONFIRMATION':
      return '信息已足够，点击右侧按钮确认'
    case 'RESUME_REGENERATED':
      return '已完成：档案已更新，新简历版本已生成'
    default:
      return status
  }
}

function errorMessage(error: unknown, fallback: string) {
  const err = error as { response?: { data?: { message?: string } } }
  return err.response?.data?.message || fallback
}

async function scrollToBottom() {
  await nextTick()
  const el = messageListRef.value
  if (el) {
    el.scrollTop = el.scrollHeight
  }
}

async function loadSessionList() {
  const res = await listSessions()
  sessions.value = res.data
}

async function loadMessages() {
  if (!activeSessionId.value) {
    messages.value = []
    return
  }
  loadingMessages.value = true
  try {
    const res = await listMessages(activeSessionId.value)
    messages.value = res.data.map((m) => ({
      id: m.id,
      role: m.role,
      content: m.content,
      createdAt: m.createdAt,
    }))
    await scrollToBottom()
  } finally {
    loadingMessages.value = false
  }
}

async function ensureSession() {
  await loadSessionList()
  if (!sessions.value.length) {
    const res = await createSession()
    sessions.value = [res.data]
  }
  if (!activeSessionId.value || !sessions.value.some((s) => s.id === activeSessionId.value)) {
    activeSessionId.value = sessions.value[0].id
  }
  await loadMessages()
}

async function selectSession(id: number) {
  if (id === activeSessionId.value || sending.value) return
  activeSessionId.value = id
  await loadMessages()
}

async function handleNewSession() {
  if (sending.value) return
  try {
    const res = await createSession()
    sessions.value = [res.data, ...sessions.value]
    activeSessionId.value = res.data.id
    messages.value = []
    input.value = ''
  } catch (error) {
    ElMessage.error(errorMessage(error, '新建对话失败，请稍后重试'))
  }
}

function openAnalyzer() {
  lastAnalysis.value = null
  showAnalyzer.value = true
}

function fillFromAnalysis() {
  if (!lastAnalysis.value?.resumeSuggestions.length) return
  input.value = lastAnalysis.value.resumeSuggestions.join('\n')
  showAnalyzer.value = false
  ElMessage.success('已填入输入框，可继续让 AI 帮你修改')
}

function onEnter(event: KeyboardEvent) {
  // 中文输入法确认候选词时按 Enter 不应发送
  if (event.isComposing) return
  event.preventDefault()
  handleSend()
}

async function handleSend() {
  if (!canSend.value || !activeSessionId.value) return

  const sessionId = activeSessionId.value
  const content = input.value.trim()
  const optimistic: UiMessage = { id: tempId--, role: 'USER', content }

  messages.value.push(optimistic)
  input.value = ''
  sending.value = true
  await scrollToBottom()

  try {
    if (isTaskChat.value && activeTask.value) {
      // 反向优化任务模式：回复 AI 追问（后端组织追问上下文 + 抽取）
      const res = await replyOptimizationTask(activeTask.value.id, content)
      messages.value.push({
        id: tempId--,
        role: 'ASSISTANT',
        content: res.data.reply,
        missingFields: res.data.missingFields,
      })
      activeTask.value.status = res.data.taskStatus
      if (res.data.taskStatus === 'USER_CONFIRMATION') {
        ElMessage.success('信息已足够，可以确认并生成新简历了')
      }
      try {
        await afterSendEvidence(res.data.extractedInfo)
      } catch (error) {
        ElMessage.error(errorMessage(error, '待确认信息加载失败，请稍后重试'))
      }
    } else {
      const res = await sendMessage(sessionId, content)
      messages.value.push({
        id: tempId--,
        role: 'ASSISTANT',
        content: res.data.reply,
        missingFields: res.data.missingFields,
      })
      // 首条消息会更新会话标题，刷新列表以便侧栏展示
      await loadSessionList()
      // 抽取结果进入待确认证据；证据加载失败不影响本轮消息已落库
      try {
        await afterSendEvidence(res.data.extractedInfo)
      } catch (error) {
        ElMessage.error(errorMessage(error, '待确认信息加载失败，请稍后重试'))
      }
    }
  } catch (error) {
    // AI 失败时后端不落库，前端同步撤回这条用户消息
    // 注意：不能用对象引用比较，响应式数组中的元素是代理对象
    messages.value = messages.value.filter((m) => m.id !== optimistic.id)
    input.value = content
    ElMessage.error(errorMessage(error, '发送失败，请稍后重试'))
  } finally {
    sending.value = false
    await scrollToBottom()
  }
}

async function handleConfirmEvidence(ids: number[]) {
  try {
    const result = await confirmSelectedEvidence(ids)
    ElMessage.success(`已确认 ${result.confirmedCount} 条信息`)
  } catch (error) {
    ElMessage.error(errorMessage(error, '确认失败，请稍后重试'))
    // 409 等冲突：刷新列表，移除已被其他请求处理的条目
    void refreshEvidence().catch(() => {})
  }
}

async function handleRejectEvidence(ids: number[]) {
  try {
    await rejectSelectedEvidence(ids)
    ElMessage.success(`已忽略 ${ids.length} 条信息`)
  } catch (error) {
    ElMessage.error(errorMessage(error, '操作失败，请稍后重试'))
    void refreshEvidence().catch(() => {})
  }
}

/** 确认补充信息：写档案（证据 CONFIRMED）+ 自动生成新简历版本 */
async function handleConfirmTask() {
  if (!activeTask.value || confirming.value) return
  confirming.value = true
  try {
    const res = await confirmOptimizationTask(activeTask.value.id)
    activeTask.value.status = res.data.taskStatus
    confirmResult.value = res.data
    showConfirmResult.value = true
  } catch (error) {
    ElMessage.error(errorMessage(error, '确认失败，请稍后重试'))
  } finally {
    confirming.value = false
  }
}

onMounted(async () => {
  loadingSessions.value = true
  try {
    // 反向优化入口：加载任务并切到任务关联的追问会话
    const taskIdParam = route.query.taskId
    if (taskIdParam) {
      try {
        activeTask.value = (await getOptimizationTask(Number(taskIdParam))).data
      } catch (error) {
        ElMessage.error(errorMessage(error, '优化任务加载失败'))
      }
    }
    await ensureSession()
    if (activeTask.value?.chatSessionId) {
      const target = activeTask.value.chatSessionId
      if (!sessions.value.some((s) => s.id === target)) {
        await loadSessionList()
      }
      activeSessionId.value = target
      await loadMessages()
    }
  } catch (error) {
    ElMessage.error(errorMessage(error, '加载对话失败，请确认后端已启动'))
  } finally {
    loadingSessions.value = false
  }
  // 同步待确认数量，不自动弹窗；失败不打扰
  void refreshEvidence().catch(() => {})
})
</script>

<template>
  <div class="chat-page">
    <aside class="session-panel">
      <div class="session-head">
        <span class="session-head-title">对话记录</span>
        <el-button type="primary" size="small" :icon="Plus" @click="handleNewSession">
          新建
        </el-button>
      </div>

      <div v-loading="loadingSessions" class="session-list">
        <button
          v-for="s in sessions"
          :key="s.id"
          type="button"
          class="session-item"
          :class="{ active: s.id === activeSessionId }"
          @click="selectSession(s.id)"
        >
          <span class="session-title">{{ s.title }}</span>
          <span class="session-status">{{ s.status === 'ACTIVE' ? '进行中' : '已结束' }}</span>
        </button>
        <el-empty
          v-if="!sessions.length && !loadingSessions"
          description="还没有对话"
          :image-size="60"
        />
      </div>
    </aside>

    <section class="chat-panel">
      <header class="chat-head">
        <div>
          <div class="chat-title">{{ activeSession?.title || 'AI 对话建档' }}</div>
          <div class="chat-sub">通过对话补充教育、技能、项目与实习经历</div>
        </div>
        <div class="chat-head-actions">
          <el-button
            v-if="evidencePendingCount > 0"
            size="small"
            type="warning"
            plain
            @click="openEvidence"
          >
            待确认 {{ evidencePendingCount }}
          </el-button>
          <el-tag size="small" effect="plain">证据链 · 不编造</el-tag>
        </div>
      </header>

      <div v-if="activeTask && isTaskChat" class="task-banner" data-testid="optimization-banner">
        <div class="task-info">
          <el-tag size="small" type="warning" effect="plain">
            反向优化 · {{ activeTask.targetSkill }}
          </el-tag>
          <span class="task-status">{{ taskStatusText(activeTask.status) }}</span>
        </div>
        <div class="task-actions">
          <el-button
            v-if="canConfirmTask"
            size="small"
            type="primary"
            data-testid="confirm-task"
            :loading="confirming"
            @click="handleConfirmTask"
          >
            确认补充并生成新简历
          </el-button>
          <el-button
            v-else-if="activeTask.status === 'RESUME_REGENERATED'"
            size="small"
            type="primary"
            @click="router.push(`/match/${activeTask.jobId}`)"
          >
            重新匹配看分数
          </el-button>
        </div>
      </div>

      <div ref="messageListRef" v-loading="loadingMessages" class="message-list">
        <div v-if="showEmpty" class="chat-empty">
          <div class="empty-title">你好，我是 CareerFlow 求职顾问</div>
          <p>
            可以这样开始：“我是计算机专业大二学生，学过 Java 和 MySQL，做过一个学生信息管理系统。”
          </p>
        </div>

        <div
          v-for="m in messages"
          :key="m.id"
          class="message-row"
          :class="m.role === 'USER' ? 'is-user' : 'is-assistant'"
        >
          <div class="avatar">{{ m.role === 'USER' ? '我' : 'AI' }}</div>
          <div class="bubble-wrap">
            <div class="bubble">{{ m.content }}</div>
            <div v-if="m.missingFields?.length" class="missing">
              <span class="missing-label">待补充：</span>
              <el-tag
                v-for="field in m.missingFields"
                :key="field"
                size="small"
                type="warning"
                effect="plain"
              >
                {{ field }}
              </el-tag>
            </div>
          </div>
        </div>

        <div v-if="sending" class="message-row is-assistant">
          <div class="avatar">AI</div>
          <div class="bubble typing">正在思考…</div>
        </div>
      </div>

      <footer class="composer">
        <el-input
          v-model="input"
          type="textarea"
          :rows="3"
          resize="none"
          :placeholder="isTaskChat ? '回答 AI 的追问，Enter 发送，Shift+Enter 换行' : '介绍你的经历，Enter 发送，Shift+Enter 换行'"
          :disabled="sending || !activeSessionId"
          @keydown.enter.exact="onEnter"
        />
        <div class="composer-bar">
          <div class="composer-left">
            <el-button type="primary" plain :icon="FolderOpened" @click="openAnalyzer">
              上传资料分析（最多 6 个）
            </el-button>
            <span class="hint">AI 只记录你明确说过的信息，未经确认不会写入档案</span>
          </div>
          <el-button
            type="primary"
            :icon="Promotion"
            :loading="sending"
            :disabled="!canSend"
            @click="handleSend"
          >
            发送
          </el-button>
        </div>
      </footer>
    </section>

    <el-dialog
      v-model="showAnalyzer"
      title="上传资料分析"
      width="760px"
      destroy-on-close
    >
      <DocumentAnalyzer compact @analyzed="lastAnalysis = $event" />
      <template #footer>
        <el-button @click="showAnalyzer = false">关闭</el-button>
        <el-button
          type="primary"
          :disabled="!lastAnalysis?.resumeSuggestions.length"
          @click="fillFromAnalysis"
        >
          将简历文案填入输入框
        </el-button>
      </template>
    </el-dialog>

    <EvidenceConfirmDialog
      v-model="evidenceVisible"
      :groups="evidenceGroups"
      :submitting="evidenceSubmitting"
      @confirm="handleConfirmEvidence"
      @reject="handleRejectEvidence"
    />

    <el-dialog v-model="showConfirmResult" title="反向优化完成" width="500px">
      <div class="confirm-result">
        <p class="confirm-line">
          本次确认写入 <strong>{{ confirmResult?.evidenceCount }}</strong> 条补充信息，更新字段：
        </p>
        <div class="confirm-fields">
          <el-tag
            v-for="field in confirmResult?.updatedFields"
            :key="field"
            size="small"
            type="success"
            effect="plain"
          >
            {{ field }}
          </el-tag>
        </div>
        <p class="confirm-line">
          已自动生成新简历版本：<strong>v{{ confirmResult?.versionNumber }}</strong>
          （旧版本完整保留）
        </p>
        <p class="confirm-hint">回到匹配分析页重新分析，即可看到分数变化。</p>
      </div>
      <template #footer>
        <el-button @click="showConfirmResult = false">关闭</el-button>
        <el-button @click="router.push('/resume')">查看简历</el-button>
        <el-button
          type="primary"
          data-testid="go-rematch"
          @click="router.push(`/match/${confirmResult?.jobId}`)"
        >
          重新匹配看分数
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.chat-page {
  height: calc(100vh - 108px);
  min-height: 520px;
  display: flex;
  gap: 16px;
}

.session-panel {
  width: 240px;
  flex-shrink: 0;
  background: var(--cf-card-bg);
  border: 1px solid var(--cf-border);
  border-radius: 10px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.session-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid var(--cf-border);
}

.session-head-title {
  font-size: 14px;
  font-weight: 600;
}

.session-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.session-item {
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  padding: 10px 12px;
  margin-bottom: 4px;
  border: none;
  border-radius: 8px;
  background: transparent;
  cursor: pointer;
  text-align: left;
  color: var(--cf-text);
  transition: background 0.15s;
}

.session-item:hover {
  background: #f3f6fc;
}

.session-item.active {
  background: #eff6ff;
}

.session-title {
  width: 100%;
  font-size: 13px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-status {
  font-size: 11px;
  color: var(--cf-text-secondary);
}

.chat-panel {
  flex: 1;
  min-width: 0;
  background: var(--cf-card-bg);
  border: 1px solid var(--cf-border);
  border-radius: 10px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.chat-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 20px;
  border-bottom: 1px solid var(--cf-border);
}

.chat-title {
  font-size: 15px;
  font-weight: 600;
}

.chat-sub {
  margin-top: 4px;
  font-size: 12px;
  color: var(--cf-text-secondary);
}

.chat-head-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.task-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 20px;
  background: #fffbeb;
  border-bottom: 1px solid #fde68a;
}

.task-info {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.task-status {
  font-size: 13px;
  color: #92400e;
}

.task-actions {
  flex-shrink: 0;
}

.confirm-result {
  font-size: 14px;
  line-height: 1.8;
}

.confirm-fields {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: 6px 0 10px;
}

.confirm-hint {
  color: var(--cf-text-secondary);
  font-size: 13px;
}

.composer-left {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  min-width: 0;
}

.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  background: linear-gradient(180deg, #fbfcff 0%, #ffffff 100%);
}

.chat-empty {
  max-width: 520px;
  margin: 12vh auto 0;
  text-align: center;
  color: var(--cf-text-secondary);
}

.empty-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--cf-text);
  margin-bottom: 12px;
}

.chat-empty p {
  font-size: 13px;
  line-height: 1.8;
}

.message-row {
  display: flex;
  gap: 10px;
  margin-bottom: 18px;
}

.message-row.is-user {
  flex-direction: row-reverse;
}

.avatar {
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  border-radius: 50%;
  background: #eff6ff;
  color: var(--cf-primary);
  font-size: 12px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.is-user .avatar {
  background: var(--cf-primary);
  color: #fff;
}

.bubble-wrap {
  max-width: 78%;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.is-user .bubble-wrap {
  align-items: flex-end;
}

.bubble {
  padding: 10px 14px;
  border-radius: 10px;
  background: #f3f6fc;
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.is-user .bubble {
  background: var(--cf-primary);
  color: #fff;
}

.bubble.typing {
  color: var(--cf-text-secondary);
}

.missing {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.missing-label {
  font-size: 12px;
  color: #b45309;
}

.composer {
  border-top: 1px solid var(--cf-border);
  padding: 12px 16px 14px;
}

.composer-bar {
  margin-top: 8px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.hint {
  font-size: 12px;
  color: var(--cf-text-secondary);
}
</style>
