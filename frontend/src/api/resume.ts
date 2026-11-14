import type { ResumeContent } from '@/components/resume-template/types'

import request, { type ApiResult, type DownloadResult } from './request'

export interface ResumeSummary {
  id: number
  resumeName: string
  currentVersionId: number | null
  currentVersionNumber: number | null
  templateId: string | null
  updatedAt: string | null
}

export interface ResumeVersionSummary {
  versionId: number
  versionNumber: number
  generationReason: string
  templateId: string | null
  createdAt: string | null
}

export interface ResumeVersion extends ResumeVersionSummary {
  content: ResumeContent
}

export interface ResumeDetail {
  id: number
  resumeName: string
  currentVersionId: number | null
  currentVersionNumber: number | null
  currentVersion: ResumeVersion | null
}

export interface ResumeGenerateResult {
  resumeId: number
  versionId: number
  versionNumber: number
  content: ResumeContent
}

export interface GenerateResumePayload {
  templateId: string
  targetJobId?: number | null
  generationReason: string
}

export interface SaveVersionPayload {
  content: ResumeContent
  templateId: string
  generationReason: string
}

export function createResume(payload: { resumeName?: string } = {}) {
  return request.post('/resumes', payload) as unknown as Promise<ApiResult<ResumeSummary>>
}

export function listResumes() {
  return request.get('/resumes') as unknown as Promise<ApiResult<ResumeSummary[]>>
}

export function getResume(id: number) {
  return request.get(`/resumes/${id}`) as unknown as Promise<ApiResult<ResumeDetail>>
}

export function generateResume(id: number, payload: GenerateResumePayload) {
  return request.post(`/resumes/${id}/generate`, payload) as unknown as Promise<
    ApiResult<ResumeGenerateResult>
  >
}

export function saveResumeVersion(id: number, payload: SaveVersionPayload) {
  return request.post(`/resumes/${id}/versions`, payload) as unknown as Promise<
    ApiResult<ResumeGenerateResult>
  >
}

export function listResumeVersions(id: number) {
  return request.get(`/resumes/${id}/versions`) as unknown as Promise<
    ApiResult<ResumeVersionSummary[]>
  >
}

/** 删除简历（归属校验；已被投递记录引用时后端返回 409） */
export function deleteResume(id: number) {
  return request.delete(`/resumes/${id}`) as unknown as Promise<ApiResult<null>>
}

export function getResumeVersion(id: number, versionId: number) {
  return request.get(`/resumes/${id}/versions/${versionId}`) as unknown as Promise<
    ApiResult<ResumeVersion>
  >
}

export function exportResumeDocx(id: number, versionId: number) {
  return request.post(
    `/resumes/${id}/export`,
    { versionId, format: 'DOCX' },
    { responseType: 'blob' },
  ) as unknown as Promise<DownloadResult>
}

/** 导出 PDF：后端生成二进制，前端只负责触发下载 */
export function exportResumePdf(id: number, versionId: number) {
  return request.post(
    `/resumes/${id}/export`,
    { versionId, format: 'PDF' },
    { responseType: 'blob' },
  ) as unknown as Promise<DownloadResult>
}
