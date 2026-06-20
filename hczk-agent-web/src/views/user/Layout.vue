<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import {
  LayoutDashboard, Bot, BarChart3, CreditCard, Wallet,
  UserCircle, LogOut, ChevronRight, Sparkles, Menu, X,
  ChevronDown
} from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const sidebarOpen = ref(false)
const userMenuOpen = ref(false)

// 点击外部关闭用户菜单
function handleClickOutside(e) {
  if (userMenuOpen.value && !e.target.closest('.user-menu-container')) {
    userMenuOpen.value = false
  }
}
onMounted(() => document.addEventListener('click', handleClickOutside))
onUnmounted(() => document.removeEventListener('click', handleClickOutside))

// 一级分组 + 二级菜单
const navGroups = [
  {
    label: '概览',
    icon: LayoutDashboard,
    items: [
      { path: '/dashboard', name: '仪表盘', icon: LayoutDashboard }
    ]
  },
  {
    label: 'AI 服务',
    icon: Bot,
    items: [
      { path: '/agents', name: '智能体对话', icon: Bot }
    ]
  },
  {
    label: '账户',
    icon: Wallet,
    items: [
      { path: '/usage', name: '用量统计', icon: BarChart3 },
      { path: '/billing', name: '账单明细', icon: CreditCard },
      { path: '/recharge', name: '充值中心', icon: Wallet }
    ]
  }
]

// 展开状态：默认全部收缩
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

// 判断分组中是否有活跃项
function isGroupActive(group) {
  return group.items.some(item => route.path === item.path)
}

function isActive(path) {
  return route.path === path
}

function logout() {
  authStore.logout()
  router.push('/login')
}

// 获取当前页面名称（含分组名）
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
    <div v-if="sidebarOpen" class="fixed inset-0 bg-black/60 backdrop-blur-sm z-30 lg:hidden" @click="sidebarOpen = false"></div>

    <!-- Sidebar -->
    <aside :class="[
      'w-72 bg-gradient-to-b from-[#0f172a] to-[#0a0f1c] border-r border-white/[0.06] flex flex-col fixed h-full z-40 transition-transform duration-300',
      sidebarOpen ? 'translate-x-0' : '-translate-x-full lg:translate-x-0'
    ]">
      <!-- Logo -->
      <div class="p-6 pb-5">
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-3">
            <div class="w-10 h-10 rounded-xl bg-gradient-to-br from-emerald-500 to-cyan-500 flex items-center justify-center shadow-lg shadow-emerald-500/20">
              <Sparkles class="w-5 h-5 text-white" />
            </div>
            <div>
              <h1 class="font-bold text-white text-lg leading-tight tracking-tight">桓宸智科</h1>
              <p class="text-[11px] text-slate-500 mt-0.5">用户控制台</p>
            </div>
          </div>
          <button @click="sidebarOpen = false" class="lg:hidden p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-white/5 transition-colors">
            <X class="w-5 h-5" />
          </button>
        </div>
      </div>

      <!-- Nav -->
      <nav class="flex-1 px-3 overflow-y-auto">
        <div v-for="(group, gi) in navGroups" :key="group.label">
          <!-- 分组间分隔线 -->
          <div v-if="gi > 0" class="my-2 mx-2 border-t border-white/[0.04]"></div>
          <!-- 一级分组标题 -->
          <button
            @click="toggleGroup(group.label)"
            :class="[
              'w-full flex items-center justify-between px-3 py-2.5 rounded-lg text-sm font-semibold transition-all duration-200',
              isGroupActive(group) ? 'text-white' : 'text-slate-400 hover:text-slate-200 hover:bg-white/[0.03]'
            ]"
          >
            <span>{{ group.label }}</span>
            <ChevronDown :class="['w-3.5 h-3.5 transition-transform duration-200', isGroupExpanded(group.label) ? '' : '-rotate-90']" />
          </button>
          <!-- 二级菜单项 -->
          <div v-show="isGroupExpanded(group.label)" class="space-y-0.5 pl-3 pb-1">
            <router-link
              v-for="item in group.items"
              :key="item.path"
              :to="item.path"
              @click="sidebarOpen = false"
              :class="[
                'flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm transition-all duration-200 group',
                isActive(item.path)
                  ? 'bg-gradient-to-r from-emerald-500/15 to-cyan-500/10 text-white shadow-sm shadow-emerald-500/5'
                  : 'text-slate-400 hover:text-slate-200 hover:bg-white/[0.04]'
              ]"
            >
              <component :is="item.icon" :class="[
                'w-[18px] h-[18px] transition-colors duration-200',
                isActive(item.path) ? 'text-emerald-400' : 'text-slate-500 group-hover:text-slate-300'
              ]" />
              <span class="font-medium">{{ item.name }}</span>
              <div v-if="isActive(item.path)" class="ml-auto w-1.5 h-1.5 rounded-full bg-emerald-400"></div>
            </router-link>
          </div>
        </div>
      </nav>

      <!-- User Balance -->
      <div class="p-4 border-t border-white/[0.04]">
        <div class="rounded-xl bg-gradient-to-br from-emerald-500/[0.08] to-cyan-500/[0.05] border border-emerald-500/10 p-4">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-[11px] text-slate-500 uppercase tracking-wider font-medium">账户余额</p>
              <p class="text-lg font-bold text-emerald-400 mt-1">¥{{ authStore.currentUser?.balance?.toFixed(2) || '0.00' }}</p>
            </div>
            <div class="w-9 h-9 rounded-lg bg-emerald-500/10 flex items-center justify-center">
              <Wallet class="w-4.5 h-4.5 text-emerald-400" />
            </div>
          </div>
        </div>
      </div>
    </aside>

    <!-- Main Content -->
    <main class="flex-1 lg:ml-72 min-w-0">
      <!-- Header -->
      <header class="h-16 border-b border-white/[0.04] bg-[#0a0f1c]/80 backdrop-blur-xl flex items-center justify-between px-5 lg:px-8 sticky top-0 z-10">
        <div class="flex items-center gap-3">
          <button @click="sidebarOpen = true" class="lg:hidden p-2 -ml-2 rounded-lg text-slate-400 hover:text-white hover:bg-white/5 transition-colors">
            <Menu class="w-5 h-5" />
          </button>
          <div class="flex items-center gap-2 text-sm">
            <span class="text-slate-500 hidden sm:inline">用户端</span>
            <ChevronRight class="w-3.5 h-3.5 text-slate-600 hidden sm:block" />
            <span class="text-white font-medium">{{ currentNavName }}</span>
          </div>
        </div>
        <div class="relative user-menu-container">
          <button @click="userMenuOpen = !userMenuOpen" class="flex items-center gap-2.5 px-2.5 py-1.5 rounded-xl hover:bg-white/[0.04] transition-colors">
            <div class="w-8 h-8 rounded-full bg-gradient-to-br from-emerald-500 to-cyan-500 flex items-center justify-center text-white text-sm font-bold shadow-sm shadow-emerald-500/20">
              {{ authStore.currentUser?.username?.[0] || 'U' }}
            </div>
            <div class="hidden sm:block text-left">
              <p class="text-sm text-white font-medium leading-tight">{{ authStore.currentUser?.username || 'User' }}</p>
              <p class="text-[11px] text-slate-500">普通用户</p>
            </div>
            <ChevronDown :class="['w-3.5 h-3.5 text-slate-500 transition-transform duration-200', userMenuOpen ? 'rotate-180' : '']" />
          </button>
          <!-- 下拉菜单 -->
          <transition
            enter-active-class="transition duration-150 ease-out"
            enter-from-class="opacity-0 scale-95 -translate-y-1"
            enter-to-class="opacity-100 scale-100 translate-y-0"
            leave-active-class="transition duration-100 ease-in"
            leave-from-class="opacity-100 scale-100 translate-y-0"
            leave-to-class="opacity-0 scale-95 -translate-y-1"
          >
            <div v-if="userMenuOpen" class="absolute right-0 top-full mt-2 w-56 rounded-xl bg-[#1e293b]/95 backdrop-blur-xl border border-white/10 shadow-xl shadow-black/30 py-1 z-50">
              <div class="px-4 py-3 border-b border-white/5">
                <p class="text-sm text-white font-medium">{{ authStore.currentUser?.username || 'User' }}</p>
                <p class="text-xs text-slate-400 mt-0.5">{{ authStore.currentUser?.email || '' }}</p>
              </div>
              <div class="py-1">
                <router-link to="/profile" @click="userMenuOpen = false" class="flex items-center gap-2.5 px-4 py-2.5 text-sm text-slate-300 hover:bg-white/5 hover:text-white transition-colors">
                  <UserCircle class="w-4 h-4" />
                  个人中心
                </router-link>
              </div>
              <div class="border-t border-white/5 py-1">
                <button @click="logout(); userMenuOpen = false" class="w-full flex items-center gap-2.5 px-4 py-2.5 text-sm text-red-400 hover:bg-red-500/10 transition-colors">
                  <LogOut class="w-4 h-4" />
                  退出登录
                </button>
              </div>
            </div>
          </transition>
        </div>
      </header>

      <!-- Page Content -->
      <div class="p-5 lg:p-8">
        <RouterView />
      </div>
    </main>
  </div>
</template>
