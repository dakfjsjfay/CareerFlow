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
