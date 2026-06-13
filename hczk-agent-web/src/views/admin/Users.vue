<script setup>
import { ref, onMounted, computed } from 'vue'
import { useApiStore } from '@/stores/api'
import { Users, Search, Filter, MoreHorizontal, Wallet, BarChart3 } from 'lucide-vue-next'

const api = useApiStore()
const loading = ref(false)
const searchQuery = ref('')

const users = ref([])

onMounted(loadData)

async function loadData() {
  loading.value = true
  const res = await api.get('/users')
  if (res.code === 200) {
    users.value = res.data || []
  }
  loading.value = false
}

const filteredUsers = computed(() => {
  if (!searchQuery.value) return users.value
  const q = searchQuery.value.toLowerCase()
  return users.value.filter(u =>
    (u.name || u.username || '').toLowerCase().includes(q) ||
    (u.email || '').toLowerCase().includes(q)
  )
})
</script>

<template>
  <div class="space-y-6 animate-fade-in">
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-2xl font-bold text-white">用户管理</h1>
        <p class="text-slate-400 mt-1">管理平台所有注册用户，查看用量与余额</p>
      </div>
    </div>

    <!-- Search & Filter -->
    <div class="flex gap-4">
      <div class="flex-1 relative">
        <Search class="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-500" />
        <input v-model="searchQuery" class="input-field pl-10" placeholder="搜索用户名称或邮箱..." />
      </div>
      <button class="btn-secondary flex items-center gap-2">
        <Filter class="w-4 h-4" /> 筛选
      </button>
    </div>

    <!-- Users Table -->
    <div class="glass-card p-6">
      <div v-if="loading" class="text-slate-500">加载中...</div>
      <div v-else class="overflow-x-auto">
        <table class="w-full">
          <thead>
            <tr class="border-b border-white/5">
              <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">用户</th>
              <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">状态</th>
              <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">余额</th>
              <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">总用量</th>
              <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">智能体</th>
              <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">注册时间</th>
              <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">操作</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-white/5">
            <tr v-for="user in filteredUsers" :key="user.id" class="hover:bg-white/5 transition-colors">
              <td class="py-4">
                <div class="flex items-center gap-3">
                  <div class="w-9 h-9 rounded-full bg-gradient-to-br from-emerald-500 to-cyan-500 flex items-center justify-center text-white text-sm font-bold">
                    {{ (user.name || user.username || '?')[0] }}
                  </div>
                  <div>
                    <p class="text-sm text-white">{{ user.name || user.username }}</p>
                    <p class="text-xs text-slate-400">{{ user.email || '-' }}</p>
                  </div>
                </div>
              </td>
              <td class="py-4">
                <span :class="['px-2 py-1 text-xs rounded-full border', user.status === 'ACTIVE' ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20' : 'bg-slate-500/10 text-slate-400 border-slate-500/20']">
                  {{ user.status === 'ACTIVE' ? '正常' : '已冻结' }}
                </span>
              </td>
              <td class="py-4">
                <span class="text-sm font-medium" :class="(user.balance || 0) > 0 ? 'text-emerald-400' : 'text-slate-400'">
                  ¥{{ (user.balance || 0).toFixed(2) }}
                </span>
              </td>
              <td class="py-4 text-sm text-slate-300">{{ user.totalUsage || '-' }}</td>
              <td class="py-4 text-sm text-slate-300">{{ user.agents || 0 }} 个</td>
              <td class="py-4 text-sm text-slate-400">{{ user.joinDate || user.createdAt || '-' }}</td>
              <td class="py-4">
                <div class="flex items-center gap-2">
                  <button class="p-2 rounded-lg bg-white/5 text-slate-300 hover:bg-white/10" title="用量详情">
                    <BarChart3 class="w-4 h-4" />
                  </button>
                  <button class="p-2 rounded-lg bg-white/5 text-slate-300 hover:bg-white/10" title="充值">
                    <Wallet class="w-4 h-4" />
                  </button>
                  <button class="p-2 rounded-lg bg-white/5 text-slate-300 hover:bg-white/10">
                    <MoreHorizontal class="w-4 h-4" />
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>
