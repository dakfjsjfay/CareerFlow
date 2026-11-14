import request, { type ApiResult } from './request'

export type FeedbackType =
  | 'NOT_INTERESTED'
  | 'LOCATION_MISMATCH'
  | 'SALARY_MISMATCH'
  | 'SKILL_ALREADY_HAVE'

export const FEEDBACK_OPTIONS: { value: FeedbackType; label: string }[] = [
  { value: 'NOT_INTERESTED', label: '不感兴趣' },
  { value: 'LOCATION_MISMATCH', label: '地点不符' },
  { value: 'SALARY_MISMATCH', label: '薪资不符' },
  { value: 'SKILL_ALREADY_HAVE', label: '技能已掌握' },
]

export interface FeedbackPayload {
  jobId: number
  feedbackType: FeedbackType
  feedbackContent?: string
}

export interface FeedbackResult {
  feedbackId: number
}

export function submitFeedback(payload: FeedbackPayload) {
  return request.post('/feedback', payload) as unknown as Promise<ApiResult<FeedbackResult>>
}
