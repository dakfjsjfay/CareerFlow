import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * 用户状态：token 与用户名持久化到 localStorage，供路由守卫与 Axios 拦截器使用。
 */
export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('token') ?? '')
  const username = ref<string>(localStorage.getItem('username') ?? '')

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

  return { token, username, setLogin, logout }
})
