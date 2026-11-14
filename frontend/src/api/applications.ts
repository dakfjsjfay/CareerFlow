import request, { type ApiResult } from './request'

export type ApplicationStatus =
  | 'PENDING'
  | 'APPLIED'
  | 'READ'
  | 'INTERVIEW_INVITED'
  | 'INTERVIEWING'
  | 'OFFERED'
  | 'REJECTED'
  | 'WITHDRAWN'
  | 'FAILED'
  | 'NO_RESPONSE'

export interface ApplicationItem {
  id: number
  jobId: number
  company: string | null
  position: string
  city: string | null
  salary: string | null
  resumeVersionId: number
  versionNumber: number | null
  status: ApplicationStatus
  appliedAt: string | null
  nextFollowUpAt: string | null
  lastFollowUpAt: string | null
  updatedAt: string | null
}

export interface ApplicationPage {
  items: ApplicationItem[]
  total: number
  page: number
  size: number
}

export interface ApplicationHistoryItem {
  id: number
  oldStatus: ApplicationStatus
  newStatus: ApplicationStatus
  note: string | null
  createdAt: string | null
}

export interface FollowUpItem {
  applicationId: number
  jobId: number
  company: string | null
  position: string
  status: ApplicationStatus
  lastUpdateAt: string | null
  daysNoResponse: number
}

export interface FollowUpList {
  count: number
  items: FollowUpItem[]
}

/** 状态机允许的下一状态（与后端 ApplicationStatus 保持一致）。 */
export const STATUS_TRANSITIONS: Record<ApplicationStatus, ApplicationStatus[]> = {
  PENDING: ['APPLIED', 'WITHDRAWN'],
  APPLIED: ['READ', 'FAILED', 'WITHDRAWN', 'NO_RESPONSE'],
  READ: ['INTERVIEW_INVITED', 'NO_RESPONSE'],
  INTERVIEW_INVITED: ['INTERVIEWING', 'NO_RESPONSE'],
  INTERVIEWING: ['OFFERED', 'REJECTED'],
  OFFERED: [],
  REJECTED: [],
  WITHDRAWN: [],
  FAILED: [],
  NO_RESPONSE: ['READ', 'INTERVIEW_INVITED', 'REJECTED'],
}

export const STATUS_LABELS: Record<ApplicationStatus, string> = {
  PENDING: '待投递',
  APPLIED: '已投递',
  READ: '已读',
  INTERVIEW_INVITED: '面试邀请',
  INTERVIEWING: '面试中',
  OFFERED: '已录用',
  REJECTED: '已拒绝',
  WITHDRAWN: '已撤回',
  FAILED: '投递失败',
  NO_RESPONSE: '暂无响应',
}

export function createApplication(payload: { jobId: number; resumeVersionId: number }) {
  return request.post('/applications', payload) as unknown as Promise<ApiResult<ApplicationItem>>
}

export function listApplications(params: {
  page?: number
  size?: number
  status?: string
} = {}) {
  return request.get('/applications', { params }) as unknown as Promise<ApiResult<ApplicationPage>>
}

export function getApplication(id: number) {
  return request.get(`/applications/${id}`) as unknown as Promise<ApiResult<ApplicationItem>>
}

export function updateApplicationStatus(id: number, payload: { status: string; note?: string }) {
  return request.put(`/applications/${id}/status`, payload) as unknown as Promise<ApiResult<ApplicationItem>>
}

export function getApplicationHistory(id: number) {
  return request.get(`/applications/${id}/history`) as unknown as Promise<ApiResult<ApplicationHistoryItem[]>>
}

export function listFollowUps() {
  return request.get('/applications/follow-ups') as unknown as Promise<ApiResult<FollowUpList>>
}

export function handleFollowUp(id: number, payload: { action: 'COMPLETED'; note?: string; nextFollowUpAt?: string }) {
  return request.post(`/applications/${id}/follow-up`, payload) as unknown as Promise<ApiResult<ApplicationItem>>
}
