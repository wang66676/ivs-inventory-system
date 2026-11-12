import axios from 'axios'

const fallbackBaseURL = import.meta.env.VITE_API_BASE_URL || '/api'
const liveConfigEnabled = import.meta.env.VITE_LIVE_API_CONFIG === 'true'

function isUsableApiBaseUrl(value: unknown): value is string {
  return typeof value === 'string'
    && (value === '/api' || /^https:\/\/[a-z0-9.-]+\/api$/i.test(value))
}

async function loadApiBaseUrl() {
  if (!liveConfigEnabled) return fallbackBaseURL

  const controller = new AbortController()
  const timeout = window.setTimeout(() => controller.abort(), 5000)
  try {
    const configUrl = `${import.meta.env.BASE_URL}public-api.json?t=${Date.now()}`
    const response = await fetch(configUrl, { cache: 'no-store', signal: controller.signal })
    if (!response.ok) return fallbackBaseURL
    const config = await response.json() as { apiBaseUrl?: unknown }
    return isUsableApiBaseUrl(config.apiBaseUrl) ? config.apiBaseUrl : fallbackBaseURL
  } catch {
    return fallbackBaseURL
  } finally {
    window.clearTimeout(timeout)
  }
}

// 页面加载时即读取线上 API 配置，避免登录时额外等待。
const apiBaseUrlReady = loadApiBaseUrl()

export const http = axios.create({
  baseURL: fallbackBaseURL,
  timeout: 60000,
  headers: {
    'Content-Type': 'application/json',
  },
})

http.interceptors.request.use(async (config) => {
  config.baseURL = await apiBaseUrlReady
  const token = localStorage.getItem('ivs_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('ivs_token')
      const loginPath = `${import.meta.env.BASE_URL}login`
      if (!window.location.pathname.endsWith('/login')) {
        window.location.href = import.meta.env.VITE_ROUTER_MODE === 'hash'
          ? `${import.meta.env.BASE_URL}#/login`
          : loginPath
      }
    }
    const message = error.response?.data?.message ?? error.message ?? '请求失败'
    return Promise.reject(new Error(message))
  },
)
