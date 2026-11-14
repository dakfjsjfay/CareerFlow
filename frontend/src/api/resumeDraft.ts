import request, { type ApiResult } from './request'

/** 八步草稿内容结构（与后端 DraftContentConverter 约定一致） */
export interface DraftBasic {
  realName: string
  phone: string
  email: string | null
  city: string | null
  school: string | null
  major: string | null
  degree: string | null
  graduationYear: number | null
  educationStatus: string | null
}

export interface DraftDirection {
  industry: string | null
  targetPosition: string | null
  targetCity: string | null
  expectedSalary: string | null
  jobGoal: string | null
  directionTags: string[]
}

export interface StarBlock {
  situation: string
  task: string
  action: string
  result: string
}

export interface DraftEducation {
  id: string
  school: string
  major: string
  degree: string
  graduationYear: number | null
  startDate: string | null
  endDate: string | null
  fieldSources: Record<string, string>
}

export interface DraftExperience {
  id: string
  kind: 'WORK' | 'INTERNSHIP' | 'PROJECT'
  company: string
  position: string
  type: string
  startDate: string | null
  endDate: string | null
  description: string
  achievements: string
  star: StarBlock
  skills: string[]
  fieldSources: Record<string, string>
}

export interface DraftProject {
  id: string
  projectName: string
  role: string
  description: string
  techStack: string[]
  highlights: string[]
  startDate: string | null
  endDate: string | null
  star: StarBlock
  fieldSources: Record<string, string>
}

export interface DraftSoft {
  selfEvaluation: string
  strengths: string[]
  suggestions: string[]
}

export interface DraftPhoto {
  dataUrl: string | null
  assetId: number | null
  width: number | null
  height: number | null
}

export interface DraftPreview {
  templateId: string
  templateName: string | null
  fontFamily: string
  fontSize: number
  lineHeight: number
  spacing: number
}

export interface ResumeDraftContent {
  schemaVersion: number
  basic: DraftBasic
  direction: DraftDirection
  educations: DraftEducation[]
  experiences: DraftExperience[]
  projects: DraftProject[]
  skills: string[]
  soft: DraftSoft
  photo: DraftPhoto
  preview: DraftPreview
  meta: Record<string, unknown>
}

export interface ResumeDraft {
  id: number
  resumeId: number | null
  draftName: string | null
  currentStep: number
  totalSteps: number
  revision: number
  status: 'DRAFT' | 'COMPLETED' | 'ABANDONED'
  lastSavedAt: string | null
  content: ResumeDraftContent
}

export interface DraftCompleteResult {
  resumeId: number
  versionId: number
  versionNumber: number
  draftId: number
}

export interface SectionAnalysis {
  sectionKey: string
  score: number
  issues: string[]
  suggestions: string[]
  missingFields: string[]
  factsConfirmed: boolean
}

export interface EditHistoryItem {
  id: number
  resumeId: number | null
  draftId: number | null
  sectionKey: string
  beforeContent: unknown
  afterContent: unknown
  source: string
  status: 'PENDING' | 'APPLIED' | 'REJECTED'
  warnings: string[]
  createdAt: string | null
  appliedAt: string | null
}

export interface StarFollowUp {
  experienceId: string
  completeness: number
  questions: Array<{ key: 'SITUATION' | 'TASK' | 'ACTION' | 'RESULT'; question: string }>
}

export function createDraft(payload: {
  resumeId?: number | null
  draftName?: string
  content?: Partial<ResumeDraftContent>
} = {}) {
  return request.post('/resume-drafts', payload) as unknown as Promise<ApiResult<ResumeDraft>>
}

export function listDrafts(status = 'DRAFT') {
  return request.get('/resume-drafts', { params: { status } }) as unknown as Promise<
    ApiResult<ResumeDraft[]>
  >
}

export function getDraft(id: number) {
  return request.get(`/resume-drafts/${id}`) as unknown as Promise<ApiResult<ResumeDraft>>
}

export function saveDraft(
  id: number,
  payload: { currentStep: number; revision: number; content: ResumeDraftContent; draftName?: string },
) {
  return request.put(`/resume-drafts/${id}`, payload) as unknown as Promise<ApiResult<ResumeDraft>>
}

export function abandonDraft(id: number) {
  return request.delete(`/resume-drafts/${id}`) as unknown as Promise<ApiResult<null>>
}

export function completeDraft(
  id: number,
  payload: { resumeName: string; templateId: string; generationReason?: string },
) {
  return request.post(`/resume-drafts/${id}/complete`, payload) as unknown as Promise<
    ApiResult<DraftCompleteResult>
  >
}

export function analyzeSection(
  draftId: number,
  sectionKey: string,
  payload: { focus?: string; jobId?: number | null } = {},
) {
  return request.post(`/resume-drafts/${draftId}/sections/${sectionKey}/analyze`, payload) as unknown as Promise<
    ApiResult<SectionAnalysis>
  >
}

export function rewriteSection(
  draftId: number,
  sectionKey: string,
  payload: { instruction?: string; jobId?: number | null } = {},
) {
  return request.post(`/resume-drafts/${draftId}/sections/${sectionKey}/rewrite`, payload) as unknown as Promise<
    ApiResult<EditHistoryItem>
  >
}

export function chatSection(
  draftId: number,
  sectionKey: string,
  payload: { message: string; history?: Array<{ role: string; content: string }> },
) {
  return request.post(`/resume-drafts/${draftId}/sections/${sectionKey}/chat`, payload) as unknown as Promise<
    ApiResult<{ reply: string; proposedEditHistoryId: number | null }>
  >
}

export function listDraftEditHistory(draftId: number) {
  return request.get(`/resume-drafts/${draftId}/edit-history`) as unknown as Promise<
    ApiResult<EditHistoryItem[]>
  >
}

export function getEditHistory(id: number) {
  return request.get(`/resume-edit-history/${id}`) as unknown as Promise<ApiResult<EditHistoryItem>>
}

export function confirmEdit(id: number, finalContent?: unknown) {
  return request.post(`/resume-edit-history/${id}/confirm`, { finalContent }) as unknown as Promise<
    ApiResult<EditHistoryItem>
  >
}

export function rejectEdit(id: number) {
  return request.post(`/resume-edit-history/${id}/reject`) as unknown as Promise<
    ApiResult<EditHistoryItem>
  >
}

export function getStarFollowUps(draftId: number, experienceId: string) {
  return request.get(
    `/resume-drafts/${draftId}/experiences/${experienceId}/followups`,
  ) as unknown as Promise<ApiResult<StarFollowUp>>
}

export function structureStar(
  draftId: number,
  experienceId: string,
  payload: { situation: string; task: string; action: string; result: string },
) {
  return request.post(
    `/resume-drafts/${draftId}/experiences/${experienceId}/star`,
    payload,
  ) as unknown as Promise<ApiResult<EditHistoryItem>>
}
