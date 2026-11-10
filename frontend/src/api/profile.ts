import request, { type ApiResult } from './request'

export type EvidenceStatus = 'PENDING' | 'CONFIRMED' | 'REJECTED'

/** 与后端 EvidenceVO 对应（docs/api.md §6.1） */
export interface EvidenceItem {
  id: number
  entityType: string
  entityId: number | null
  fieldName: string
  sourceType: string
  sourceId: number | null
  sourceContent: string | null
  evidenceContent: string
  confirmationStatus: string
  createdAt: string
}

export interface ConfirmEvidenceResult {
  confirmedCount: number
  updatedFields: string[]
}

/** 档案（只含已确认数据，docs/api.md §6.4） */
export interface ProfileProject {
  id: number
  projectName: string
  role: string | null
  description: string | null
  techStack: string[] | null
  startDate: string | null
  endDate: string | null
}

export interface ProfileExperience {
  id: number
  company: string
  position: string | null
  description: string | null
  achievements: string | null
  startDate: string | null
  endDate: string | null
}

export interface ProfileData {
  id: number | null
  realName: string | null
  phone: string | null
  school: string | null
  major: string | null
  degree: string | null
  graduationYear: number | null
  skills: string[]
  targetPosition: string | null
  targetCity: string | null
  expectedSalary: string | null
  projects: ProfileProject[]
  experiences: ProfileExperience[]
}

export interface DimensionScore {
  name: string
  score: number
}

export interface CompletenessResult {
  overall: number
  dimensions: DimensionScore[]
}

/** PUT /api/profile：字段可选，null 不修改，空串清空，skills 整体替换 */
export interface ProfileUpdatePayload {
  realName?: string
  phone?: string
  school?: string
  major?: string
  degree?: string
  graduationYear?: number | null
  skills?: string[]
  targetPosition?: string
  targetCity?: string
  expectedSalary?: string
}

export interface ProjectPayload {
  projectName: string
  role?: string
  description?: string
  techStack?: string[]
  startDate?: string | null
  endDate?: string | null
}

export interface ExperiencePayload {
  company: string
  position?: string
  description?: string
  achievements?: string
  startDate?: string | null
  endDate?: string | null
}

/** 获取已确认档案（无档案时后端返回空结构） */
export function getProfile() {
  return request.get('/profile') as unknown as Promise<ApiResult<ProfileData>>
}

/** 手动更新档案（每个实际变更字段写 MANUAL + CONFIRMED 证据） */
export function updateProfile(payload: ProfileUpdatePayload) {
  return request.put('/profile', payload) as unknown as Promise<ApiResult<ProfileData>>
}

/** 完整度：6 维度 + overall */
export function getCompleteness() {
  return request.get('/profile/completeness') as unknown as Promise<ApiResult<CompletenessResult>>
}

export function createProject(payload: ProjectPayload) {
  return request.post('/profile/projects', payload) as unknown as Promise<
    ApiResult<ProfileProject>
  >
}

export function updateProject(id: number, payload: ProjectPayload) {
  return request.put(`/profile/projects/${id}`, payload) as unknown as Promise<
    ApiResult<ProfileProject>
  >
}

export function deleteProject(id: number) {
  return request.delete(`/profile/projects/${id}`) as unknown as Promise<ApiResult<null>>
}

export function createExperience(payload: ExperiencePayload) {
  return request.post('/profile/experiences', payload) as unknown as Promise<
    ApiResult<ProfileExperience>
  >
}

export function updateExperience(id: number, payload: ExperiencePayload) {
  return request.put(`/profile/experiences/${id}`, payload) as unknown as Promise<
    ApiResult<ProfileExperience>
  >
}

export function deleteExperience(id: number) {
  return request.delete(`/profile/experiences/${id}`) as unknown as Promise<ApiResult<null>>
}

/** 证据列表（默认待确认） */
export function listEvidence(status: EvidenceStatus = 'PENDING') {
  return request.get('/profile/evidence', { params: { status } }) as unknown as Promise<
    ApiResult<EvidenceItem[]>
  >
}

/** 确认或拒绝证据 */
export function confirmEvidence(evidenceIds: number[], confirmed: boolean) {
  return request.post('/profile/evidence/confirm', { evidenceIds, confirmed }) as unknown as Promise<
    ApiResult<ConfirmEvidenceResult>
  >
}
