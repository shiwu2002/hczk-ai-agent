<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import {
  LayoutDashboard, Bot, Server, Cpu, Link, KeyRound, Users,
  Receipt, MessageSquare, Plug, Settings, LogOut, ChevronRight, Shield, Database
} from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const navItems = [
  { path: '/admin/dashboard', name: '仪表盘', icon: LayoutDashboard },
  { path: '/admin/agents', name: '智能体管理', icon: Bot },
  { path: '/admin/skills', name: 'Skill 管理', icon: Server },
  { path: '/admin/knowledge', name: '知识库管理', icon: Database },
  { path: '/admin/models', name: '模型管理', icon: Cpu },
  { path: '/admin/bindings', name: '智能体绑定', icon: Link },
  { path: '/admin/apikeys', name: 'API Keys', icon: KeyRound },
  { path: '/admin/users', name: '用户管理', icon: Users },
  { path: '/admin/billing', name: '计费管理', icon: Receipt },
  { path: '/admin/chat-logs', name: '对话记录', icon: MessageSquare },
  { path: '/admin/platforms', name: '平台对接', icon: Plug },
  { path: '/admin/settings', name: '系统设置', icon: Settings }
]

function isActive(path) {
  return route.path === path
}

function logout() {
  authStore.logout()
  router.push('/login')
}
</script>

<template>
  <div class="min-h-screen flex bg-[#0a0f1c]">
    <!-- Sidebar -->
    <aside class="w-64 bg-[#111827]/80 border-r border-white/5 flex flex-col backdrop-blur-xl fixed h-full z-20">
      <!-- Logo -->
      <div class="p-6 border-b border-white/5">
        <div class="flex items-center gap-3">
          <div class="w-9 h-9 rounded-xl bg-gradient-to-br from-emerald-500 to-cyan-500 flex items-center justify-center">
            <Shield class="w-5 h-5 text-white" />
          </div>
          <div>
            <h1 class="font-bold text-white text-lg leading-tight">智联平台</h1>
            <p class="text-xs text-slate-500">管理控制台</p>
          </div>
        </div>
      </div>

      <!-- Nav -->
      <nav class="flex-1 p-4 space-y-1 overflow-y-auto">
        <router-link
          v-for="item in navItems"
          :key="item.path"
          :to="item.path"
          :class="['nav-item', isActive(item.path) ? 'active' : '']"
        >
          <component :is="item.icon" class="w-5 h-5" />
          <span class="text-sm font-medium">{{ item.name }}</span>
        </router-link>
      </nav>

      <!-- User -->
      <div class="p-4 border-t border-white/5">
        <div class="flex items-center gap-3 mb-3 p-3 rounded-lg bg-white/5">
          <div class="w-8 h-8 rounded-full bg-gradient-to-br from-emerald-500 to-cyan-500 flex items-center justify-center text-white text-sm font-bold">
            {{ authStore.currentUser?.name?.[0] || 'A' }}
          </div>
          <div>
            <p class="text-sm text-white font-medium">{{ authStore.currentUser?.name || 'Admin' }}</p>
            <p class="text-xs text-slate-500">超级管理员</p>
          </div>
        </div>
        <button @click="logout" class="nav-item w-full text-red-400 hover:text-red-300 hover:bg-red-500/5">
          <LogOut class="w-5 h-5" />
          <span class="text-sm font-medium">退出登录</span>
        </button>
      </div>
    </aside>

    <!-- Main Content -->
    <main class="flex-1 ml-64">
      <!-- Header -->
      <header class="h-16 border-b border-white/5 bg-[#0a0f1c]/80 backdrop-blur-xl flex items-center justify-between px-8 sticky top-0 z-10">
        <div class="flex items-center gap-2 text-sm text-slate-400">
          <span>管理员</span>
          <ChevronRight class="w-4 h-4" />
          <span class="text-white">{{ navItems.find(i => isActive(i.path))?.name || '' }}</span>
        </div>
        <div class="flex items-center gap-3">
          <span class="px-2 py-1 rounded text-xs font-medium bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
            系统运行正常
          </span>
        </div>
      </header>

      <!-- Page Content -->
      <div class="p-8">
        <RouterView />
      </div>
    </main>
  </div>
</template>
