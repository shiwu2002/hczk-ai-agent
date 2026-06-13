import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

const API_BASE = 'http://localhost:8080/api'

export const useAuthStore = defineStore('auth', () => {
  const user = ref(JSON.parse(localStorage.getItem('user') || 'null'))
  const token = ref(localStorage.getItem('token') || '')

  const isAuthenticated = computed(() => !!token.value)
  const isAdmin = computed(() => user.value?.role === 'ADMIN')
  const currentUser = computed(() => user.value)

  async function login(credentials) {
    const res = await fetch(`${API_BASE}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(credentials)
    })
    const data = await res.json()
    if (data.code !== 200) {
      throw new Error(data.message || '登录失败')
    }
    const result = data.data
    user.value = result.user
    token.value = result.token
    localStorage.setItem('user', JSON.stringify(result.user))
    localStorage.setItem('token', result.token)
    return result.user
  }

  function logout() {
    user.value = null
    token.value = ''
    localStorage.removeItem('user')
    localStorage.removeItem('token')
  }

  async function fetchWithAuth(url, options = {}) {
    const headers = {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token.value}`,
      ...options.headers
    }
    const res = await fetch(`${API_BASE}${url}`, { ...options, headers })
    return res.json()
  }

  return { user, token, isAuthenticated, isAdmin, currentUser, login, logout, fetchWithAuth }
})
