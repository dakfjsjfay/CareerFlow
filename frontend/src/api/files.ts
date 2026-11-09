import request, { type ApiResult } from './request'

export type AnalysisType = 'AUTO' | 'PROFILE' | 'PROJECT'

export interface FilePartInfo {
  fileName: string
  fileType: string
  truncated: boolean
}

export interface FileAnalysisResult {
  files: FilePartInfo[]
  fileName: string
  fileType: string
  analysisType: string
  truncated: boolean
  summary: string
  extractedInfo: Record<string, unknown>
  strengths: string[]
  resumeSuggestions: string[]
  jobGapSuggestions: string[]
  missingFields: string[]
  warnings: string[]
}

/** 上传 1~6 个资料文件并协同分析（ZIP 需单独上传，docs/api.md §15） */
export function analyzeFiles(
  files: File[],
  analysisType: AnalysisType = 'AUTO',
  jobDescription?: string,
) {
  const form = new FormData()
  files.forEach((file) => form.append('file', file))
  form.append('analysisType', analysisType)
  if (jobDescription) {
    form.append('jobDescription', jobDescription)
  }
  // 文件解析 + AI 分析可能超过全局 60 秒超时（尤其 PDF/扫描件），单独放宽到 5 分钟
  return request.post('/files/analyze', form, { timeout: 300000 }) as unknown as Promise<
    ApiResult<FileAnalysisResult>
  >
}
