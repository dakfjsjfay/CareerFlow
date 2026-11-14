import request, { type ApiResult } from './request'

export interface MatchDetailItem {
  skill: string
  status: 'MATCHED' | 'PARTIAL' | 'MISSING' | 'UNCONFIRMED' | string
  evidence: string | null
  suggestion: string | null
}

export interface MatchAnalyzeResult {
  matchResultId: number
  jobId: number
  resumeVersionId: number
  matchScore: number
  matchedCount: number
  partialCount: number
  missingCount: number
  unconfirmedCount: number
  details: MatchDetailItem[]
}

export interface MatchResultDetail {
  matchResultId: number
  jobId: number
  resumeVersionId: number
  matchScore: number
  details: MatchDetailItem[]
}

export interface RecommendItem {
  jobId: number
  company: string | null
  position: string
  city: string | null
  salary: string | null
  matchScore: number
  rankReasons: string[]
}

export interface RecommendPage {
  items: RecommendItem[]
  total: number
  page: number
  size: number
}

export function analyzeMatch(payload: { jobId: number; resumeVersionId: number }) {
  return request.post('/match/analyze', payload) as unknown as Promise<ApiResult<MatchAnalyzeResult>>
}

export function getMatchResult(id: number) {
  return request.get(`/match/${id}`) as unknown as Promise<ApiResult<MatchResultDetail>>
}

export interface AdviceResult {
  matchScore: number
  strengths: string[]
  gaps: string[]
  pitchMessage: string
  suggestions: string[]
}

export function getMatchAdvice(payload: { jobId: number; resumeVersionId: number }) {
  return request.post('/match/advice', payload) as unknown as Promise<ApiResult<AdviceResult>>
}

export function recommendJobs(page = 1, size = 10) {
  return request.get('/match/jobs/recommend', { params: { page, size } }) as unknown as Promise<ApiResult<RecommendPage>>
}
