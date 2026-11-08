import request, { type ApiResult } from './request'

export interface UserInfo {
  id: number
  username: string
  email?: string
}

export interface LoginResult {
  token: string
  user: UserInfo
}

export function register(data: { username: string; password: string; email?: string }) {
  return request.post('/auth/register', data) as unknown as Promise<ApiResult<null>>
}

export function login(data: { username: string; password: string }) {
  return request.post('/auth/login', data) as unknown as Promise<ApiResult<LoginResult>>
}

export function me() {
  return request.get('/auth/me') as unknown as Promise<ApiResult<UserInfo>>
}
