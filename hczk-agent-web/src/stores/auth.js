import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import router from '@/router'

// 后端 API 基础地址
export const API_BASE = 'http://localhost:8080/api'

/**
 * 公开接口请求封装（无需登录认证）
 * 用于登录、注册、发送验证码、忘记密码等接口
 * 自动处理 HTTP 错误状态码，解析后端返回的错误消息
 */
export async function publicFetch(url, options = {}) {
  const headers = {
    'Content-Type': 'application/json',
    ...options.headers
  }
  const res = await fetch(`${API_BASE}${url}`, { ...options, headers })
  if (!res.ok) {
    let message = '请求失败'
    try {
      const data = await res.json()
      message = data.message || message
    } catch {}
    throw new Error(message)
  }
  return res.json()
}

/**
 * 认证状态管理 Store
 * 管理用户登录状态、JWT Token、角色信息
 * Token 和用户信息持久化到 localStorage
 */
export const useAuthStore = defineStore('auth', () => {
  // 从 localStorage 恢复登录状态
  const user = ref(JSON.parse(localStorage.getItem('user') || 'null'))
  const token = ref(localStorage.getItem('token') || '')

  // 兼容旧缓存：旧格式 role 为字符串("ADMIN"/"USER")，新格式为后端返回的 Integer(0/1)
  // 检测到旧格式时清除缓存，强制重新登录以从后端获取正确的 role 值
  if (user.value && typeof user.value.role === 'string') {
    user.value = null
    token.value = ''
    localStorage.removeItem('user')
    localStorage.removeItem('token')
  }

  // 计算属性：是否已认证（依据 token 是否存在）
  const isAuthenticated = computed(() => !!token.value)
  // 计算属性：是否为管理员（后端返回 role=0 为管理员，role=1 为普通用户）
  const isAdmin = computed(() => user.value?.role === 0)
  // 计算属性：当前用户信息
  const currentUser = computed(() => user.value)

  /**
   * 处理未授权（401）情况
   * 清除本地登录状态并跳转到登录页
   */
  function handleUnauthorized() {
    logout()
    router.push('/login')
  }

  /**
   * 用户登录
   * 调用后端登录接口，成功后将 token 和用户信息存入 store 及 localStorage
   * @param {Object} credentials - 登录凭据 { username, password }
   * @returns {Object} 用户信息
   */
  async function login(credentials) {
    const res = await fetch(`${API_BASE}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(credentials)
    })
    // 处理 HTTP 层面的错误（如 500、404 等）
    if (!res.ok) {
      let message = '登录失败'
      try {
        const data = await res.json()
        message = data.message || message
      } catch {}
      throw new Error(message)
    }
    // 处理业务层面的错误（HTTP 200 但 code 非 200）
    const data = await res.json()
    if (data.code !== 200) {
      throw new Error(data.message || '登录失败')
    }
    // 保存登录状态
    const result = data.data
    user.value = result.user
    token.value = result.token
    localStorage.setItem('user', JSON.stringify(result.user))
    localStorage.setItem('token', result.token)
    return result.user
  }

  /**
   * 退出登录
   * 清除 store 和 localStorage 中的用户信息与 token
   */
  function logout() {
    user.value = null
    token.value = ''
    localStorage.removeItem('user')
    localStorage.removeItem('token')
  }

  return { user, token, isAuthenticated, isAdmin, currentUser, login, logout, handleUnauthorized }
})
