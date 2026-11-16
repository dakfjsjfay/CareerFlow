import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

/**
 * 用户状态：token 与用户名持久化到 localStorage，供路由守卫与 Axios 拦截器使用。
 * 角色从 JWT payload 读取（后端登录时写入 role），仅用于前端展示控制，权限仍由后端校验。
 */
function readRole(token: string): string {
  if (!token) return ''
  try {
    const segment = token.split('.')[1] ?? ''
    const normalized = segment.replace(/-/g, '+').replace(/_/g, '/')
    const bytes = Uint8Array.from(atob(normalized), (char) => char.charCodeAt(0))
    const payload = JSON.parse(new TextDecoder().decode(bytes)) as { role?: string }
    return payload.role ?? ''
  } catch {
    return ''
  }
}

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('token') ?? '')
  const username = ref<string>(localStorage.getItem('username') ?? '')
  const role = computed(() => readRole(token.value))
  const isAdmin = computed(() => role.value === 'ADMIN')

  function setLogin(newToken: string, newUsername: string) {
    token.value = newToken
    username.value = newUsername
    localStorage.setItem('token', newToken)
    localStorage.setItem('username', newUsername)
  }

  function logout() {
    token.value = ''
    username.value = ''
    localStorage.removeItem('token')
    localStorage.removeItem('username')
  }

  return { token, username, role, isAdmin, setLogin, logout }
})
