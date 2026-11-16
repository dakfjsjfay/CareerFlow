import request, { type ApiResult } from './request'
import type { JobPage } from './jobs'

/** 企业列表项（后端 CompanySummaryVO） */
export interface CompanySummary {
  id: number
  name: string
  shortName: string | null
  industry: string | null
  companyType: string | null
  scale: string | null
  /** 总部城市 */
  city: string | null
  website: string | null
  recruitmentUrl: string | null
  campusRecruitUrl: string | null
  /** SEED / ADMIN_IMPORT / MANUAL */
  source: string
  /** VERIFIED / PENDING / EXPIRED */
  verificationStatus: string
  lastVerifiedAt: string | null
}

/** 企业详情（后端 CompanyDetailVO） */
export interface CompanyDetail extends CompanySummary {
  internRecruitUrl: string | null
  socialRecruitUrl: string | null
  logoUrl: string | null
  description: string | null
  sourceUrl: string | null
  jobCount: number
}

export interface CompanyPage {
  items: CompanySummary[]
  total: number
  page: number
  size: number
}

export interface CompanyListParams {
  page?: number
  size?: number
  keyword?: string
  industry?: string
  city?: string
  verificationStatus?: string
}

/** 第三方招聘平台入口：仅外部搜索页，不包含自动投递 */
export interface PlatformLink {
  key: string
  label: string
  url: string
  note: string
}

export function listCompanies(params: CompanyListParams = {}) {
  return request.get('/companies', { params }) as unknown as Promise<ApiResult<CompanyPage>>
}

export function getCompany(id: number) {
  return request.get(`/companies/${id}`) as unknown as Promise<ApiResult<CompanyDetail>>
}

export function listCompanyJobs(id: number, page = 1, size = 10) {
  return request.get(`/companies/${id}/jobs`, { params: { page, size } }) as unknown as Promise<
    ApiResult<JobPage>
  >
}

export function getPlatformLinks(id: number) {
  return request.get(`/companies/${id}/platform-links`) as unknown as Promise<
    ApiResult<PlatformLink[]>
  >
}

/** 管理员标记链接核验状态；普通用户调用会被后端拒绝（403） */
export function verifyCompany(id: number, status: 'VERIFIED' | 'PENDING' | 'EXPIRED') {
  return request.post(`/companies/${id}/verify`, null, { params: { status } }) as unknown as Promise<
    ApiResult<CompanyDetail>
  >
}
