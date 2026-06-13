import { defineStore } from 'pinia'
import { ref } from 'vue'
import { useAuthStore } from './auth'

export const API_BASE = 'http://localhost:8080/api'

export const useApiStore = defineStore('api', () => {
  const authStore = useAuthStore()

  async function handleResponse(res) {
    if (res.status === 401) {
      authStore.handleUnauthorized()
      throw new Error('登录已过期，请重新登录')
    }
    return res.json()
  }

  async function get(url) {
    const res = await fetch(`${API_BASE}${url}`, {
      headers: { 'Authorization': `Bearer ${authStore.token}` }
    })
    return handleResponse(res)
  }

  async function post(url, body) {
    const res = await fetch(`${API_BASE}${url}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${authStore.token}`
      },
      body: JSON.stringify(body)
    })
    return handleResponse(res)
  }

  async function put(url, body) {
    const res = await fetch(`${API_BASE}${url}`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${authStore.token}`
      },
      body: JSON.stringify(body)
    })
    return handleResponse(res)
  }

  async function del(url) {
    const res = await fetch(`${API_BASE}${url}`, {
      method: 'DELETE',
      headers: { 'Authorization': `Bearer ${authStore.token}` }
    })
    return handleResponse(res)
  }

  return { get, post, put, del }
})
