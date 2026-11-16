import request, { type ApiResult } from './request'

export interface JobRequirement {
  id: number
  skill: string
  importance: string
  weight: number
}

/** 岗位统一展示状态，由后端计算，前端不自行推断 */
export type JobDisplayStatus = 'DEMO' | 'UNVERIFIED' | 'VERIFIED' | 'EXPIRED' | 'CLOSED'

export interface JobSummary {
  id: number
  company: string | null
  companyId: number | null
  position: string
  city: string | null
  salary: string | null
  /** DEMO / MANUAL / OFFICIAL / PLATFORM / IMPORT（兼容旧值 MOCK） */
  sourceType: string
  /** 来源名称，如「企业官网」 */
  sourceName: string | null
  /** 原招聘信息发布时间 */
  publishedAt: string | null
  /** 最近核验时间 */
  verifiedAt: string | null
  /** 有效期截止 */
  validUntil: string | null
  displayStatus: JobDisplayStatus | string
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
  /** DEMO / UNVERIFIED / VERIFIED / EXPIRED */
  verificationStatus?: string
  /** DEMO / MANUAL / OFFICIAL / PLATFORM / IMPORT */
  sourceType?: string
  onlyVerified?: boolean
}

export interface CreateJobPayload {
  position: string
  company?: string
  city?: string
  salary?: string
  jdContent?: string
  externalUrl?: string
}

export interface VerifyJobPayload {
  verificationStatus: 'UNVERIFIED' | 'VERIFIED' | 'EXPIRED'
  sourceName?: string
  externalUrl?: string
  publishedAt?: string
  validUntil?: string
}

export interface ImportJobPayload {
  position: string
  company?: string
  city?: string
  salary?: string
  jdContent?: string
  externalUrl?: string
  sourceName?: string
  publishedAt?: string
  parseJd?: boolean
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

export function importJob(payload: ImportJobPayload) {
  return request.post('/jobs/import', payload) as unknown as Promise<ApiResult<JobSummary>>
}

export function verifyJob(id: number, payload: VerifyJobPayload) {
  return request.post(`/jobs/${id}/verify`, payload) as unknown as Promise<ApiResult<JobDetail>>
}

export function parseJob(id: number, jdContent?: string) {
  return request.post(`/jobs/${id}/parse`, { jdContent }) as unknown as Promise<ApiResult<JobParseResult>>
}
