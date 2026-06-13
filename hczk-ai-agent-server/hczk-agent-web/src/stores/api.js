import { defineStore } from 'pinia'
import { ref } from 'vue'
import { useAuthStore } from './auth'

const API_BASE = 'http://localhost:8080/api'

export const useApiStore = defineStore('api', () => {
  const authStore = useAuthStore()

  async function get(url) {
    const res = await fetch(`${API_BASE}${url}`, {
      headers: { 'Authorization': `Bearer ${authStore.token}` }
    })
    return res.json()
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
    return res.json()
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
    return res.json()
  }

  async function del(url) {
    const res = await fetch(`${API_BASE}${url}`, {
      method: 'DELETE',
      headers: { 'Authorization': `Bearer ${authStore.token}` }
    })
    return res.json()
  }

  return { get, post, put, del }
})
