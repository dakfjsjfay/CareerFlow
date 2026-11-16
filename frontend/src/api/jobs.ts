import request, { type ApiResult } from './request'

export interface JobRequirement {
  id: number
  skill: string
  importance: string
  weight: number
}

export interface JobSummary {
  id: number
  company: string | null
  companyId: number | null
  position: string
  city: string | null
  salary: string | null
  sourceType: string
  /** 招聘批次：秋招 / 春招 / 实习 / 社招 */
  recruitmentBatch: string | null
  /** 招聘状态：OPEN / CLOSED / SOON */
  recruitmentStatus: string | null
  requirementCount: number
  topSkills: string[]
}

export interface JobDetail extends JobSummary {
  jdContent: string | null
  externalUrl: string | null
  requirements: JobRequirement[]
}

export interface JobParseResult {
  jobId: number
  position: string
  requirements: JobRequirement[]
}

export interface JobPage {
  items: JobSummary[]
  total: number
  page: number
  size: number
}

export interface JobListParams {
  page?: number
  size?: number
  keyword?: string
  city?: string
  salary?: string
  companyId?: number
  recruitmentBatch?: string
  recruitmentStatus?: string
}

export interface CreateJobPayload {
  position: string
  company?: string
  city?: string
  salary?: string
  jdContent?: string
  externalUrl?: string
}

export function listJobs(params: JobListParams = {}) {
  return request.get('/jobs', { params }) as unknown as Promise<ApiResult<JobPage>>
}

export function getJob(id: number) {
  return request.get(`/jobs/${id}`) as unknown as Promise<ApiResult<JobDetail>>
}

export function createJob(payload: CreateJobPayload) {
  return request.post('/jobs', payload) as unknown as Promise<ApiResult<JobSummary>>
}

export function parseJob(id: number, jdContent?: string) {
  return request.post(`/jobs/${id}/parse`, { jdContent }) as unknown as Promise<ApiResult<JobParseResult>>
}
