<script setup>
import { useRouter } from 'vue-router'
import { Home, AlertCircle } from 'lucide-vue-next'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

function goHome() {
  router.push(authStore.isAdmin ? '/admin/dashboard' : '/dashboard')
}
</script>

<template>
  <div class="min-h-screen flex items-center justify-center bg-bg-primary px-4">
    <div class="glass-card p-12 text-center max-w-md w-full">
      <AlertCircle class="w-16 h-16 text-amber-400 mx-auto mb-4" />
      <h1 class="text-5xl font-bold text-text-primary mb-2">404</h1>
      <p class="text-text-secondary mb-6">抱歉，您访问的页面不存在</p>
      <button class="btn-primary inline-flex items-center gap-2" @click="goHome">
        <Home class="w-4 h-4" />
        返回首页
      </button>
    </div>
  </div>
</template>
