import { defineStore } from 'pinia'
import { ref } from 'vue'

let idSeq = 0

/**
 * 全局 Toast 通知 Store
 * 统一管理成功/错误/警告/信息提示，避免各页面重复实现
 */
export const useToastStore = defineStore('toast', () => {
  const toasts = ref([])

  /**
   * 添加一条 toast
   * @param {string} message - 提示内容
   * @param {'success'|'error'|'warning'|'info'} type - 类型
   * @param {number} duration - 自动关闭时长(ms)，默认 3000
   */
  function push(message, type = 'info', duration = 3000) {
    const id = ++idSeq
    toasts.value.push({ id, message, type })
    if (duration > 0) {
      setTimeout(() => remove(id), duration)
    }
    return id
  }

  function success(message, duration) {
    return push(message, 'success', duration)
  }

  function error(message, duration = 5000) {
    return push(message, 'error', duration)
  }

  function warning(message, duration) {
    return push(message, 'warning', duration)
  }

  function info(message, duration) {
    return push(message, 'info', duration)
  }

  function remove(id) {
    const idx = toasts.value.findIndex(t => t.id === id)
    if (idx > -1) toasts.value.splice(idx, 1)
  }

  return { toasts, push, success, error, warning, info, remove }
})
