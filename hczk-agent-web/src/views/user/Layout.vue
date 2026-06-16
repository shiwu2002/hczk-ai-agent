<script setup>
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import {
  LayoutDashboard, Bot, BarChart3, CreditCard, Wallet,
  UserCircle, LogOut, ChevronRight, Sparkles, Menu, X
} from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const sidebarOpen = ref(false)

const navItems = [
  { path: '/dashboard', name: '仪表盘', icon: LayoutDashboard },
  { path: '/agents', name: '我的智能体', icon: Bot },
  { path: '/usage', name: '用量统计', icon: BarChart3 },
  { path: '/billing', name: '账单明细', icon: CreditCard },
  { path: '/recharge', name: '充值中心', icon: Wallet },
  { path: '/profile', name: '个人中心', icon: UserCircle }
]

function isActive(path) {
  return route.path === path
}

function logout() {
  authStore.logout()
  router.push('/login')
}

const currentNavName = computed(() => navItems.find(i => isActive(i.path))?.name || '')
</script>

<template>
  <div class="min-h-screen flex bg-[#0a0f1c]">
    <!-- 移动端遮罩 -->
    <div v-if="sidebarOpen" class="fixed inset-0 bg-black/50 z-30 lg:hidden" @click="sidebarOpen = false"></div>

    <!-- Sidebar -->
    <aside :class="[
      'w-64 bg-[#111827]/80 border-r border-white/5 flex flex-col backdrop-blur-xl fixed h-full z-40 transition-transform duration-300',
      sidebarOpen ? 'translate-x-0' : '-translate-x-full lg:translate-x-0'
    ]">
      <!-- Logo -->
      <div class="p-6 border-b border-white/5">
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-3">
            <div class="w-9 h-9 rounded-xl bg-gradient-to-br from-emerald-500 to-cyan-500 flex items-center justify-center">
              <Sparkles class="w-5 h-5 text-white" />
            </div>
            <div>
              <h1 class="font-bold text-white text-lg leading-tight">智联平台</h1>
              <p class="text-xs text-slate-500">用户控制台</p>
            </div>
          </div>
          <button @click="sidebarOpen = false" class="lg:hidden p-1 text-slate-400 hover:text-white">
            <X class="w-5 h-5" />
          </button>
        </div>
      </div>

      <!-- Nav -->
      <nav class="flex-1 p-4 space-y-1 overflow-y-auto">
        <router-link
          v-for="item in navItems"
          :key="item.path"
          :to="item.path"
          @click="sidebarOpen = false"
          :class="['nav-item', isActive(item.path) ? 'active' : '']"
        >
          <component :is="item.icon" class="w-5 h-5" />
          <span class="text-sm font-medium">{{ item.name }}</span>
        </router-link>
      </nav>

      <!-- User -->
      <div class="p-4 border-t border-white/5">
        <div class="glass-panel p-3 mb-3">
          <div class="flex items-center justify-between">
            <span class="text-xs text-slate-400">账户余额</span>
            <span class="text-sm font-semibold text-emerald-400">¥{{ authStore.currentUser?.balance?.toFixed(2) || '0.00' }}</span>
          </div>
        </div>
        <button @click="logout" class="nav-item w-full text-red-400 hover:text-red-300 hover:bg-red-500/5">
          <LogOut class="w-5 h-5" />
          <span class="text-sm font-medium">退出登录</span>
        </button>
      </div>
    </aside>

    <!-- Main Content -->
    <main class="flex-1 lg:ml-64 min-w-0">
      <!-- Header -->
      <header class="h-16 border-b border-white/5 bg-[#0a0f1c]/80 backdrop-blur-xl flex items-center justify-between px-4 lg:px-8 sticky top-0 z-10">
        <div class="flex items-center gap-3">
          <button @click="sidebarOpen = true" class="lg:hidden p-2 -ml-2 rounded-lg text-slate-400 hover:text-white hover:bg-white/5">
            <Menu class="w-5 h-5" />
          </button>
          <div class="flex items-center gap-2 text-sm text-slate-400">
            <span class="hidden sm:inline">用户端</span>
            <ChevronRight class="w-4 h-4 hidden sm:block" />
            <span class="text-white">{{ currentNavName }}</span>
          </div>
        </div>
        <div class="flex items-center gap-4">
          <div class="flex items-center gap-2">
            <div class="w-8 h-8 rounded-full bg-gradient-to-br from-emerald-500 to-cyan-500 flex items-center justify-center text-white text-sm font-bold">
              {{ authStore.currentUser?.name?.[0] || 'U' }}
            </div>
            <span class="text-sm text-slate-300 hidden sm:inline">{{ authStore.currentUser?.name || 'User' }}</span>
          </div>
        </div>
      </header>

      <!-- Page Content -->
      <div class="p-4 lg:p-8">
        <RouterView />
      </div>
    </main>
  </div>
</template>
