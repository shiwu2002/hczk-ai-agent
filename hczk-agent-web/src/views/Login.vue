<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { BrainCircuit, Lock, User, ArrowRight } from 'lucide-vue-next'

const router = useRouter()
const authStore = useAuthStore()

const username = ref('')
const password = ref('')
const isLoading = ref(false)
const error = ref('')

async function handleLogin() {
  if (!username.value || !password.value) {
    error.value = '请输入用户名和密码'
    return
  }
  isLoading.value = true
  error.value = ''

  try {
    await authStore.login({ username: username.value, password: password.value })
    router.push(authStore.isAdmin ? '/admin' : '/')
  } catch (e) {
    error.value = e.message || '登录失败'
  } finally {
    isLoading.value = false
  }
}
</script>

<template>
  <div class="min-h-screen flex items-center justify-center relative overflow-hidden">
    <div class="absolute inset-0 bg-[#0a0f1c]">
      <div class="absolute top-1/4 left-1/4 w-96 h-96 bg-emerald-500/10 rounded-full blur-3xl animate-pulse-slow"></div>
      <div class="absolute bottom-1/4 right-1/4 w-80 h-80 bg-cyan-500/10 rounded-full blur-3xl animate-pulse-slow" style="animation-delay: 1.5s;"></div>
      <div class="absolute inset-0" style="background-image: radial-gradient(circle at 1px 1px, rgba(255,255,255,0.03) 1px, transparent 0); background-size: 40px 40px;"></div>
    </div>

    <div class="relative z-10 w-full max-w-md px-6">
      <div class="text-center mb-10">
        <div class="inline-flex items-center justify-center w-16 h-16 rounded-2xl bg-gradient-to-br from-emerald-500 to-cyan-500 mb-4 shadow-lg shadow-emerald-500/20">
          <BrainCircuit class="w-8 h-8 text-white" />
        </div>
        <h1 class="text-3xl font-bold text-white mb-2">桓宸智科AI平台</h1>
        <p class="text-slate-400">统一接入 · 智能管理 · 高效计费</p>
      </div>

      <div class="glass-card p-8">
        <h2 class="text-xl font-semibold text-white mb-6">欢迎回来</h2>

        <form @submit.prevent="handleLogin" class="space-y-5">
          <div>
            <label class="block text-sm font-medium text-slate-300 mb-2">用户名</label>
            <div class="relative">
              <User class="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-500" />
              <input v-model="username" type="text" class="input-field pl-10" placeholder="输入用户名" />
            </div>
          </div>

          <div>
            <label class="block text-sm font-medium text-slate-300 mb-2">密码</label>
            <div class="relative">
              <Lock class="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-500" />
              <input v-model="password" type="password" class="input-field pl-10" placeholder="输入密码" />
            </div>
          </div>

          <div v-if="error" class="text-red-400 text-sm">{{ error }}</div>

          <button type="submit" class="btn-primary w-full flex items-center justify-center gap-2 py-3" :disabled="isLoading">
            <span v-if="isLoading">登录中...</span>
            <span v-else class="flex items-center gap-2">登录 <ArrowRight class="w-4 h-4" /></span>
          </button>
        </form>

        <div class="mt-6 pt-6 border-t border-white/5 text-center">
          <p class="text-sm text-slate-500">
            测试账号: <span class="text-emerald-400">admin / admin123</span> 或 <span class="text-emerald-400">user / user123</span>
          </p>
        </div>
      </div>
    </div>
  </div>
</template>
