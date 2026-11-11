import axios from 'axios'

/** 后端统一返回结构（docs/api.md §2）：{ code, message, data } */
export interface ApiResult<T = unknown> {
  code: number
  message: string
  data: T
}

/** 二进制下载结果：blob + 从 Content-Disposition 解析出的文件名 */
export interface DownloadResult {
  blob: Blob
  filename: string
}

export function parseFilename(disposition?: string): string {
  if (!disposition) {
    return 'resume.docx'
  }
  const utf8 = /filename\*=UTF-8''([^;]+)/i.exec(disposition)
  if (utf8?.[1]) {
    try {
      return decodeURIComponent(utf8[1])
    } catch {
      return utf8[1]
    }
  }
  const plain = /filename="?([^";]+)"?/i.exec(disposition)
  return plain?.[1] ?? 'resume.docx'
}

/**
 * Axios 实例：统一 baseURL、Token 注入、401 处理。
 * 所有接口请求都从这里发出，禁止在页面里直接用 axios。
 */
const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 60000,
})

// 请求拦截：自动带上 JWT
request.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截：统一解包 Result；401 时清理登录态，仅在受保护页面才跳转登录页
// （在登录/注册页本身收到 401 时不刷新页面，让页面正常展示错误提示）
request.interceptors.response.use(
  (response) => {
    if (response.config.responseType === 'blob') {
      return {
        blob: response.data as Blob,
        filename: parseFilename(String(response.headers?.['content-disposition'] ?? '')),
      } satisfies DownloadResult
    }
    return response.data
  },
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('username')
      const { pathname } = window.location
      if (pathname !== '/login' && pathname !== '/register') {
        window.location.href = '/login'
      }
    }
    return Promise.reject(error)
  },
)

export default request
