<script setup>
import { ref, onMounted } from 'vue'
import { useApiStore } from '@/stores/api'
import {
  Users, Bot, KeyRound, Coins, TrendingUp,
  Activity, Zap, ArrowUpRight, ArrowDownRight
} from 'lucide-vue-next'

const api = useApiStore()
const loading = ref(false)

const stats = ref([
  { label: '注册用户', value: '0', change: '+12%', up: true, icon: Users },
  { label: '智能体数量', value: '0', change: '+8%', up: true, icon: Bot },
  { label: 'API Keys', value: '0', change: '+23%', up: true, icon: KeyRound },
  { label: '今日Token消耗', value: '0', change: '-5%', up: false, icon: Coins }
])

const recentUsers = ref([])
const modelStatus = ref([])

onMounted(loadData)

async function loadData() {
  loading.value = true
  const [usersRes, modelsRes] = await Promise.all([
    api.get('/users'),
    api.get('/models')
  ])
  if (usersRes.code === 200) {
    const users = usersRes.data || []
    stats.value[0].value = users.length.toString()
    recentUsers.value = users.slice(0, 4).map(u => ({
      name: u.name || u.username || '未知',
      email: u.email || '-',
      usage: '-',
      time: '-',
      status: u.status === 'ACTIVE' ? 'active' : 'offline'
    }))
  }
  if (modelsRes.code === 200) {
    const models = modelsRes.data || []
    stats.value[1].value = '-'
    modelStatus.value = models.map(m => ({
      name: m.name,
      provider: m.provider,
      status: m.status === 'ACTIVE' ? 'online' : 'warning',
      latency: '-',
      qps: '-'
    }))
  }
  loading.value = false
}
</script>

<template>
  <div class="space-y-6 animate-fade-in">
    <!-- Header -->
    <div>
      <h1 class="text-2xl font-bold text-white">仪表盘</h1>
      <p class="text-slate-400 mt-1">平台运营数据实时监控</p>
    </div>

    <!-- Stats Cards -->
    <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
      <div v-for="stat in stats" :key="stat.label" class="glass-card p-6 glow-border">
        <div class="flex items-start justify-between">
          <div>
            <p class="text-sm text-slate-400">{{ stat.label }}</p>
            <p class="text-2xl font-bold text-white mt-2">{{ stat.value }}</p>
          </div>
          <div class="w-10 h-10 rounded-lg bg-emerald-500/10 flex items-center justify-center">
            <component :is="stat.icon" class="w-5 h-5 text-emerald-400" />
          </div>
        </div>
        <div class="flex items-center gap-1 mt-4">
          <component :is="stat.up ? ArrowUpRight : ArrowDownRight" class="w-4 h-4" :class="stat.up ? 'text-emerald-400' : 'text-red-400'" />
          <span class="text-sm" :class="stat.up ? 'text-emerald-400' : 'text-red-400'">{{ stat.change }}</span>
          <span class="text-sm text-slate-500 ml-1">较昨日</span>
        </div>
      </div>
    </div>

    <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
      <!-- Chart Placeholder -->
      <div class="lg:col-span-2 glass-card p-6">
        <div class="flex items-center justify-between mb-6">
          <div>
            <h3 class="text-lg font-semibold text-white">Token 消耗趋势</h3>
            <p class="text-sm text-slate-400 mt-1">近7天平台整体 Token 使用量</p>
          </div>
          <div class="flex gap-2">
            <button class="px-3 py-1.5 text-xs rounded-lg bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">近7天</button>
            <button class="px-3 py-1.5 text-xs rounded-lg text-slate-400 hover:bg-white/5">近30天</button>
          </div>
        </div>
        <div class="h-64 flex items-end justify-between gap-3 px-2">
          <div v-for="(h, i) in [45, 62, 38, 75, 55, 88, 70]" :key="i" class="flex-1 flex flex-col items-center gap-2">
            <div class="w-full bg-gradient-to-t from-emerald-500/20 to-emerald-500/60 rounded-t-lg transition-all hover:from-emerald-500/30 hover:to-emerald-500/80" :style="{ height: h + '%' }"></div>
            <span class="text-xs text-slate-500">{{ ['周一', '周二', '周三', '周四', '周五', '周六', '周日'][i] }}</span>
          </div>
        </div>
      </div>

      <!-- Model Status -->
      <div class="glass-card p-6">
        <h3 class="text-lg font-semibold text-white mb-4">模型状态</h3>
        <div v-if="loading" class="text-slate-500">加载中...</div>
        <div v-else class="space-y-4">
          <div v-for="model in modelStatus" :key="model.name" class="flex items-center justify-between p-3 rounded-lg bg-white/5">
            <div class="flex items-center gap-3">
              <span class="status-dot" :class="model.status"></span>
              <div>
                <p class="text-sm font-medium text-white">{{ model.name }}</p>
                <p class="text-xs text-slate-400">{{ model.provider }}</p>
              </div>
            </div>
            <div class="text-right">
              <p class="text-sm text-slate-300">{{ model.latency }}</p>
              <p class="text-xs text-slate-500">{{ model.qps }} QPS</p>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Recent Users -->
    <div class="glass-card p-6">
      <div class="flex items-center justify-between mb-6">
        <h3 class="text-lg font-semibold text-white">最近活跃用户</h3>
        <button class="text-sm text-emerald-400 hover:text-emerald-300">查看全部</button>
      </div>
      <div v-if="loading" class="text-slate-500">加载中...</div>
      <div v-else class="overflow-x-auto">
        <table class="w-full">
          <thead>
            <tr class="border-b border-white/5">
              <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">用户</th>
              <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">用量</th>
              <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">状态</th>
              <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">时间</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-white/5">
            <tr v-for="user in recentUsers" :key="user.email" class="hover:bg-white/5 transition-colors">
              <td class="py-4">
                <div class="flex items-center gap-3">
                  <div class="w-8 h-8 rounded-full bg-gradient-to-br from-emerald-500 to-cyan-500 flex items-center justify-center text-white text-xs font-bold">
                    {{ user.name[0] }}
                  </div>
                  <div>
                    <p class="text-sm text-white">{{ user.name }}</p>
                    <p class="text-xs text-slate-400">{{ user.email }}</p>
                  </div>
                </div>
              </td>
              <td class="py-4 text-sm text-slate-300">{{ user.usage }}</td>
              <td class="py-4">
                <span class="px-2 py-1 text-xs rounded-full" :class="user.status === 'active' ? 'bg-emerald-500/10 text-emerald-400' : 'bg-slate-500/10 text-slate-400'">
                  {{ user.status === 'active' ? '在线' : '离线' }}
                </span>
              </td>
              <td class="py-4 text-sm text-slate-400">{{ user.time }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>
