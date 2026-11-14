import request, { type ApiResult } from './request'

/** rate 为 0~1 的小数，前端统一格式化为百分比。 */
export interface AnalyticsSummary {
  totalApplications: number
  readCount: number
  interviewCount: number
  offerCount: number
  feedbackCount: number
  readRate: number
  interviewRate: number
  offerRate: number
}

export type FunnelStageKey = 'APPLIED' | 'READ' | 'INTERVIEW' | 'OFFER'

export interface FunnelStage {
  stage: FunnelStageKey
  label: string
  count: number
  rate: number
}

export interface VersionEffect {
  resumeVersionId: number
  versionNumber: number | null
  applicationCount: number
  readCount: number
  interviewCount: number
  offerCount: number
  readRate: number
  interviewRate: number
  offerRate: number
}

export type FeedbackType =
  | 'NOT_INTERESTED'
  | 'LOCATION_MISMATCH'
  | 'SALARY_MISMATCH'
  | 'SKILL_ALREADY_HAVE'

export interface FeedbackDistribution {
  feedbackType: FeedbackType | string
  label: string
  count: number
  rate: number
}

export interface FeedbackAnalytics {
  summary: AnalyticsSummary
  funnel: FunnelStage[]
  versionEffects: VersionEffect[]
  feedbackDistribution: FeedbackDistribution[]
}

export function getFeedbackAnalytics() {
  return request.get('/analytics/feedback') as unknown as Promise<ApiResult<FeedbackAnalytics>>
}
