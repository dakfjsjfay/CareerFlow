import request, { type ApiResult } from './request'

export type RequirementImportance = 'REQUIRED' | 'PREFERRED'

export interface JobRequirement {
  id: number
  skill: string
  importance: RequirementImportance | string
  weight: number
}

export interface JobSummary {
  id: number
  company: string
  position: string
  city: string | null
  salary: string | null
  sourceType: string
  externalUrl: string | null
  createdAt: string | null
}

export interface JobDetail extends JobSummary {
  jdContent: string | null
  updatedAt: string | null
  requirements: JobRequirement[]
}

export interface JobPage {
  total: number
  page: number
  size: number
  records: JobSummary[]
}

export interface JobQuery {
  page?: number
  size?: number
  keyword?: string
  city?: string
  salary?: string
}

export interface JobRequirementPayload {
  skill: string
  importance?: RequirementImportance
  weight?: number
}

/** 手动新增岗位（开发用途，docs/api.md §8） */
export interface JobCreatePayload {
  company: string
  position: string
  city?: string
  salary?: string
  jdContent?: string
  externalUrl?: string
  requirements?: JobRequirementPayload[]
}

export interface JdParseResult {
  position: string | null
  requirements: JobRequirement[]
}

export function listJobs(params: JobQuery = {}) {
  return request.get('/jobs', { params }) as unknown as Promise<ApiResult<JobPage>>
}

export function getJob(id: number) {
  return request.get(`/jobs/${id}`) as unknown as Promise<ApiResult<JobDetail>>
}

export function createJob(payload: JobCreatePayload) {
  return request.post('/jobs', payload) as unknown as Promise<ApiResult<JobDetail>>
}

/** 解析岗位 JD；传入 jdContent 时先覆盖 JD 再解析，便于人工修正后重试 */
export function parseJob(id: number, payload: { jdContent?: string } = {}) {
  return request.post(`/jobs/${id}/parse`, payload) as unknown as Promise<ApiResult<JdParseResult>>
}
