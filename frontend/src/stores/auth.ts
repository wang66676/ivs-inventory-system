import { defineStore } from 'pinia'
import { authApi } from '../api/auth'
import type { CurrentUser, LoginRequest } from '../types/auth'

const TOKEN_KEY = 'ivs_token'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) ?? '',
    user: null as CurrentUser | null,
    initialized: false,
  }),
  getters: {
    isAuthenticated: (state) => Boolean(state.token),
    can: (state) => (permission: string) => state.user?.permissions.includes(permission) ?? false,
  },
  actions: {
    async login(command: LoginRequest) {
      const response = await authApi.login(command)
      this.token = response.token
      this.user = response
      this.initialized = true
      localStorage.setItem(TOKEN_KEY, response.token)
      return response
    },
    async loadCurrentUser() {
      if (!this.token) return null
      this.user = await authApi.me()
      this.initialized = true
      return this.user
    },
    async logout() {
      try {
        if (this.token) await authApi.logout()
      } finally {
        this.clear()
      }
    },
    clear() {
      this.token = ''
      this.user = null
      this.initialized = true
      localStorage.removeItem(TOKEN_KEY)
      // 退出登录/会话失效时清空当前会话的列宽，重新进入系统恢复初始化宽度。
      window.dispatchEvent(new CustomEvent('ivs:session-cleared'))
    },
  },
})
