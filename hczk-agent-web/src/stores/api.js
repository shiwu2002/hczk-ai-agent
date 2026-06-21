import { defineStore } from 'pinia'
import { useAuthStore } from './auth'
import { useToastStore } from './toast'

/**
 * 后端 API 基础地址
 * 优先从环境变量 VITE_API_BASE 读取（生产部署通过 .env 配置），
 * 默认回退到本地开发地址
 */
export const API_BASE = import.meta.env.VITE_API_BASE || 'http://localhost:8080/api'

export const useApiStore = defineStore('api', () => {
  const authStore = useAuthStore()
  const toastStore = useToastStore()

  /**
   * 统一响应处理
   * - 401: 触发未授权处理并抛错
   * - 业务码非 200: 自动 toast 错误消息，但仍返回完整 response（保持调用方 code 判断兼容）
   * - 网络错误/解析失败: toast 提示并抛错
   */
  async function handleResponse(res) {
    if (res.status === 401) {
      authStore.handleUnauthorized()
      throw new Error('登录已过期，请重新登录')
    }
    let data
    try {
      data = await res.json()
    } catch (e) {
      const msg = `响应解析失败 (${res.status})`
      toastStore.error(msg)
      throw new Error(msg)
    }
    // 业务错误码统一提示（不抛错，保持调用方 res.code 判断兼容）
    if (data && typeof data.code === 'number' && data.code !== 200) {
      const msg = data.message || `请求失败 (code=${data.code})`
      toastStore.error(msg)
    }
    return data
  }

  async function get(url) {
    let res
    try {
      res = await fetch(`${API_BASE}${url}`, {
        headers: { 'Authorization': `Bearer ${authStore.token}` }
      })
    } catch (e) {
      toastStore.error('网络请求失败，请检查网络连接')
      throw e
    }
    return handleResponse(res)
  }

  async function post(url, body) {
    let res
    try {
      res = await fetch(`${API_BASE}${url}`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${authStore.token}`
        },
        body: JSON.stringify(body)
      })
    } catch (e) {
      toastStore.error('网络请求失败，请检查网络连接')
      throw e
    }
    return handleResponse(res)
  }

  async function put(url, body) {
    let res
    try {
      res = await fetch(`${API_BASE}${url}`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${authStore.token}`
        },
        body: JSON.stringify(body)
      })
    } catch (e) {
      toastStore.error('网络请求失败，请检查网络连接')
      throw e
    }
    return handleResponse(res)
  }

  async function del(url) {
    let res
    try {
      res = await fetch(`${API_BASE}${url}`, {
        method: 'DELETE',
        headers: { 'Authorization': `Bearer ${authStore.token}` }
      })
    } catch (e) {
      toastStore.error('网络请求失败，请检查网络连接')
      throw e
    }
    return handleResponse(res)
  }

  async function patch(url, body) {
    let res
    try {
      res = await fetch(`${API_BASE}${url}`, {
        method: 'PATCH',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${authStore.token}`
        },
        body: body ? JSON.stringify(body) : undefined
      })
    } catch (e) {
      toastStore.error('网络请求失败，请检查网络连接')
      throw e
    }
    return handleResponse(res)
  }

  return { get, post, put, del, patch }
})
