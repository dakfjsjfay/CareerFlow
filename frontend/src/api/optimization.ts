import request, { type ApiResult } from './request'

export interface OptimizationTask {
  id: number
  jobId: number
  resumeVersionId: number
  chatSessionId: number
  targetSkill: string
  question: string | null
  status: string
  source: string | null
  createdAt: string
  updatedAt: string
}

export interface OptimizationReplyResult {
  reply: string
  nextQuestion: boolean
  taskStatus: string
  extractedInfo: Record<string, unknown>
  missingFields: string[]
}

export interface OptimizationConfirmResult {
  taskId: number
  taskStatus: string
  jobId: number
  resumeId: number
  resumeVersionId: number
  versionNumber: number
  evidenceCount: number
  updatedFields: string[]
}

/** 创建优化任务（点击匹配页差距项发起）；chatSessionId 不传则由后端新建追问会话 */
export function createOptimizationTask(payload: {
  jobId: number
  resumeVersionId: number
  targetSkill: string
  chatSessionId?: number
}) {
  return request.post('/optimization/tasks', payload) as unknown as Promise<
    ApiResult<OptimizationTask>
  >
}

/** 任务详情 */
export function getOptimizationTask(id: number) {
  return request.get(`/optimization/tasks/${id}`) as unknown as Promise<ApiResult<OptimizationTask>>
}

/** 回复 AI 追问（多轮） */
export function replyOptimizationTask(taskId: number, answer: string) {
  return request.post(`/optimization/tasks/${taskId}/reply`, { answer }) as unknown as Promise<
    ApiResult<OptimizationReplyResult>
  >
}

/** 确认补充信息 → 写入档案（证据 CONFIRMED）→ 触发简历重新生成 */
export function confirmOptimizationTask(taskId: number) {
  return request.post(`/optimization/tasks/${taskId}/confirm`) as unknown as Promise<
    ApiResult<OptimizationConfirmResult>
  >
}
