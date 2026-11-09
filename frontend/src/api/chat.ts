import request, { type ApiResult } from './request'

export interface ChatSession {
  id: number
  title: string
  status: string
  createdAt: string
  updatedAt: string
}

export interface ChatMessage {
  id: number
  role: 'USER' | 'ASSISTANT'
  content: string
  createdAt: string
}

export interface SendMessageResult {
  sessionId: number
  reply: string
  extractedInfo: Record<string, unknown>
  missingFields: string[]
}

/** 创建会话（标题可选） */
export function createSession(title?: string) {
  return request.post('/chat/sessions', title ? { title } : {}) as unknown as Promise<
    ApiResult<ChatSession>
  >
}

/** 会话列表 */
export function listSessions() {
  return request.get('/chat/sessions') as unknown as Promise<ApiResult<ChatSession[]>>
}

/** 历史消息（后端校验会话归属） */
export function listMessages(sessionId: number) {
  return request.get(`/chat/sessions/${sessionId}/messages`) as unknown as Promise<
    ApiResult<ChatMessage[]>
  >
}

/** 发送消息，由后端组织多轮上下文调用 AI */
export function sendMessage(sessionId: number, message: string) {
  return request.post('/chat/send', { sessionId, message }) as unknown as Promise<
    ApiResult<SendMessageResult>
  >
}
