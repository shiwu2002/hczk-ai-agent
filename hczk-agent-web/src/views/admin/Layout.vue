<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import {
  LayoutDashboard, Bot, Server, Cpu, KeyRound, Users,
  Receipt, MessageSquare, Plug, Settings, LogOut, ChevronRight, Shield, Database, FlaskConical,
  Activity, ChevronDown, Sparkles, Menu, X
} from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const sidebarOpen = ref(false)
const userMenuOpen = ref(false)

// 点击外部关闭用户菜单
function handleClickOutside(e) {
  const menu = document.querySelector('[ref="userMenuRef"]')
  if (userMenuOpen.value && !e.target.closest('.relative')) {
    userMenuOpen.value = false
  }
}
onMounted(() => document.addEventListener('click', handleClickOutside))
onUnmounted(() => document.removeEventListener('click', handleClickOutside))

// 一级分组 + 二级菜单
const navGroups = [
  {
    label: '概览',
    items: [
      { path: '/admin/dashboard', name: '仪表盘', icon: LayoutDashboard }
    ]
  },
  {
    label: 'AI 能力',
    items: [
      { path: '/admin/agents', name: '智能体管理', icon: Bot },
      { path: '/admin/skills', name: 'Skill 管理', icon: Server },
      { path: '/admin/knowledge', name: '知识库管理', icon: Database },
      { path: '/admin/retrieval-logs', name: '检索监控', icon: Activity }
    ]
  },
  {
    label: '模型中心',
    items: [
      { path: '/admin/models', name: '模型管理', icon: Cpu },
      { path: '/admin/model-test', name: '模型测试', icon: FlaskConical }
    ]
  },
  {
    label: '运营管理',
    items: [
      { path: '/admin/apikeys', name: 'API Keys', icon: KeyRound },
      { path: '/admin/users', name: '用户管理', icon: Users },
      { path: '/admin/billing', name: '计费管理', icon: Receipt },
      { path: '/admin/chat-logs', name: '对话记录', icon: MessageSquare }
    ]
  },
  {
    label: '系统配置',
    items: [
      { path: '/admin/platforms', name: '平台对接', icon: Plug },
      { path: '/admin/settings', name: '系统设置', icon: Settings }
    ]
  }
]

// 展开状态：默认全部展开
const expandedGroups = ref(new Set())

function toggleGroup(label) {
  if (expandedGroups.value.has(label)) {
    expandedGroups.value.delete(label)
  } else {
    expandedGroups.value.add(label)
  }
}

function isGroupExpanded(label) {
  return expandedGroups.value.has(label)
}

function isActive(path) {
  return route.path === path || route.path.startsWith(path + '/')
}

function logout() {
  authStore.logout()
  router.push('/login')
}

const currentNavName = computed(() => {
  for (const group of navGroups) {
    const found = group.items.find(i => isActive(i.path))
    if (found) return found.name
  }
  return ''
})
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
              <Shield class="w-5 h-5 text-white" />
            </div>
            <div>
              <h1 class="font-bold text-white text-lg leading-tight">桓宸智科</h1>
              <p class="text-xs text-slate-500">管理控制台</p>
            </div>
          </div>
          <button @click="sidebarOpen = false" class="lg:hidden p-1 text-slate-400 hover:text-white">
            <X class="w-5 h-5" />
          </button>
        </div>
      </div>

      <!-- Nav -->
      <nav class="flex-1 p-3 overflow-y-auto">
        <div v-for="(group, gi) in navGroups" :key="group.label">
          <!-- 分组间分隔线 -->
          <div v-if="gi > 0" class="my-2 mx-3 border-t border-white/5"></div>
          <!-- 一级分组标题 -->
          <button
            @click="toggleGroup(group.label)"
            class="w-full flex items-center justify-between px-3 py-2 text-sm font-semibold text-slate-400 hover:text-slate-200 transition-colors"
          >
            <span>{{ group.label }}</span>
            <ChevronDown :class="['w-3.5 h-3.5 transition-transform duration-200', isGroupExpanded(group.label) ? '' : '-rotate-90']" />
          </button>
          <!-- 二级菜单项 -->
          <div v-show="isGroupExpanded(group.label)" class="space-y-0.5 pl-3">
            <router-link
              v-for="item in group.items"
              :key="item.path"
              :to="item.path"
              @click="sidebarOpen = false"
              :class="['nav-item', isActive(item.path) ? 'active' : '']"
            >
              <component :is="item.icon" class="w-5 h-5" />
              <span class="text-sm font-medium">{{ item.name }}</span>
            </router-link>
          </div>
        </div>
      </nav>

      <!-- User -->
      <div class="p-4 border-t border-white/5">
        <div class="glass-panel p-3 mb-3">
          <div class="flex items-center justify-between">
            <span class="text-xs text-slate-400">账户余额</span>
            <span class="text-sm font-semibold text-emerald-400">¥{{ authStore.currentUser?.balance?.toFixed(2) || '0.00' }}</span>
          </div>
        </div>
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
            <span class="hidden sm:inline">管理员</span>
            <ChevronRight class="w-4 h-4 hidden sm:block" />
            <span class="text-white">{{ currentNavName }}</span>
          </div>
        </div>
        <div class="relative" ref="userMenuRef">
          <button @click="userMenuOpen = !userMenuOpen" class="flex items-center gap-2 px-2 py-1.5 rounded-lg hover:bg-white/5 transition-colors">
            <div class="w-8 h-8 rounded-full bg-gradient-to-br from-emerald-500 to-cyan-500 flex items-center justify-center text-white text-sm font-bold">
              {{ authStore.currentUser?.username?.[0] || 'A' }}
            </div>
            <div class="hidden sm:block text-left">
              <p class="text-sm text-white font-medium leading-tight">{{ authStore.currentUser?.username || 'Admin' }}</p>
              <p class="text-xs text-slate-500">超级管理员</p>
            </div>
          </button>
          <!-- 下拉菜单 -->
          <div v-if="userMenuOpen" class="absolute right-0 top-full mt-1 w-48 rounded-xl bg-[#1e293b]/95 backdrop-blur-xl border border-white/10 shadow-xl shadow-black/20 py-1 z-50">
            <div class="px-4 py-3 border-b border-white/5">
              <p class="text-sm text-white font-medium">{{ authStore.currentUser?.username || 'Admin' }}</p>
              <p class="text-xs text-slate-400 mt-0.5">{{ authStore.currentUser?.email || '' }}</p>
            </div>
            <button @click="logout(); userMenuOpen = false" class="w-full flex items-center gap-2 px-4 py-2.5 text-sm text-red-400 hover:bg-red-500/10 transition-colors">
              <LogOut class="w-4 h-4" />
              退出登录
            </button>
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
