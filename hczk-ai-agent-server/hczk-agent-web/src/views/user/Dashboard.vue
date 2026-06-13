<script setup>
import { ref, onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useApiStore } from '@/stores/api'
import { Wallet, BarChart3, Bot, Zap, ArrowUpRight, Clock } from 'lucide-vue-next'

const authStore = useAuthStore()
const api = useApiStore()

const stats = ref([
  { label: '账户余额', value: '¥0.00', icon: Wallet, color: 'emerald' },
  { label: '今日消耗', value: '0 tokens', icon: BarChart3, color: 'cyan' },
  { label: '我的智能体', value: '0 个', icon: Bot, color: 'blue' },
  { label: 'API 调用', value: '0 次', icon: Zap, color: 'yellow' }
])
const quickAgents = ref([])
const recentActivity = ref([])
const loading = ref(true)

onMounted(async () => {
  loading.value = true
  try {
    const userRes = await api.get('/users/me')
    if (userRes.code === 200) {
      const u = userRes.data
      authStore.user = u
      stats.value[0].value = '¥' + (u.balance || 0).toFixed(2)
      stats.value[3].value = (u.totalUsageTokens || 0).toLocaleString() + ' tokens'
    }

    const agentRes = await api.get(`/agents/user/${authStore.user?.id}`)
    if (agentRes.code === 200) {
      quickAgents.value = (agentRes.data || []).slice(0, 3)
      stats.value[2].value = (agentRes.data || []).length + ' 个'
    }

    const billRes = await api.get(`/billing/records/${authStore.user?.id}`)
    if (billRes.code === 200) {
      recentActivity.value = (billRes.data || []).slice(0, 5).map(b => ({
        action: b.type === 'RECHARGE' ? '充值' : '调用',
        detail: b.detail || '-',
        time: b.createdAt ? new Date(b.createdAt).toLocaleString() : '-',
        type: b.type === 'RECHARGE' ? 'recharge' : 'api'
      }))
    }
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="space-y-6 animate-fade-in">
    <div>
      <h1 class="text-2xl font-bold text-white">仪表盘</h1>
      <p class="text-slate-400 mt-1">欢迎回来，{{ authStore.currentUser?.username || '用户' }}</p>
    </div>

    <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
      <div v-for="stat in stats" :key="stat.label" class="glass-card p-6 glow-border">
        <div class="flex items-start justify-between">
          <div>
            <p class="text-sm text-slate-400">{{ stat.label }}</p>
            <p class="text-2xl font-bold text-white mt-2">{{ stat.value }}</p>
          </div>
          <div :class="['w-10 h-10 rounded-lg flex items-center justify-center', 'bg-' + stat.color + '-500/10']">
            <component :is="stat.icon" :class="['w-5 h-5', 'text-' + stat.color + '-400']" />
          </div>
        </div>
      </div>
    </div>

    <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
      <div class="lg:col-span-2 glass-card p-6">
        <div class="flex items-center justify-between mb-6">
          <div>
            <h3 class="text-lg font-semibold text-white">用量趋势</h3>
            <p class="text-sm text-slate-400 mt-1">近7天 Token 消耗</p>
          </div>
          <router-link to="/usage" class="text-sm text-emerald-400 hover:text-emerald-300 flex items-center gap-1">详情 <ArrowUpRight class="w-4 h-4" /></router-link>
        </div>
        <div class="h-56 flex items-end justify-between gap-3 px-2">
          <div v-for="(h, i) in [35, 52, 28, 65, 45, 78, 60]" :key="i" class="flex-1 flex flex-col items-center gap-2">
            <div class="w-full bg-gradient-to-t from-cyan-500/20 to-cyan-500/60 rounded-t-lg transition-all hover:from-cyan-500/30 hover:to-cyan-500/80" :style="{ height: h + '%' }"></div>
            <span class="text-xs text-slate-500">{{ ['周一','周二','周三','周四','周五','周六','周日'][i] }}</span>
          </div>
        </div>
      </div>

      <div class="glass-card p-6">
        <h3 class="text-lg font-semibold text-white mb-4">最近动态</h3>
        <div v-if="recentActivity.length === 0" class="text-sm text-slate-500">暂无记录</div>
        <div class="space-y-4">
          <div v-for="(activity, i) in recentActivity" :key="i" class="flex items-start gap-3">
            <div :class="['w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0', activity.type === 'recharge' ? 'bg-emerald-500/10' : 'bg-white/5']">
              <component :is="activity.type === 'recharge' ? Wallet : Zap" :class="['w-4 h-4', activity.type === 'recharge' ? 'text-emerald-400' : 'text-slate-400']" />
            </div>
            <div class="flex-1 min-w-0">
              <p class="text-sm text-white truncate">{{ activity.action }}</p>
              <p class="text-xs text-slate-400">{{ activity.detail }}</p>
            </div>
            <span class="text-xs text-slate-500 flex-shrink-0">{{ activity.time }}</span>
          </div>
        </div>
      </div>
    </div>

    <div class="glass-card p-6">
      <div class="flex items-center justify-between mb-6">
        <h3 class="text-lg font-semibold text-white">我的智能体</h3>
        <router-link to="/agents" class="text-sm text-emerald-400 hover:text-emerald-300">查看全部</router-link>
      </div>
      <div v-if="quickAgents.length === 0" class="text-sm text-slate-500">暂无智能体</div>
      <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div v-for="agent in quickAgents" :key="agent.id" class="p-4 rounded-lg bg-white/5 hover:bg-white/10 transition-colors">
          <div class="flex items-center justify-between mb-3">
            <div class="w-9 h-9 rounded-lg bg-emerald-500/10 flex items-center justify-center">
              <Bot class="w-5 h-5 text-emerald-400" />
            </div>
            <span :class="['w-2 h-2 rounded-full', agent.status === 'ACTIVE' ? 'bg-emerald-400 shadow shadow-emerald-400/50' : 'bg-slate-500']"></span>
          </div>
          <p class="text-sm font-medium text-white">{{ agent.name }}</p>
          <p class="text-xs text-slate-400 mt-1">{{ agent.model?.name || '-' }}</p>
          <p class="text-xs text-slate-500 mt-2">{{ (agent.totalCalls || 0).toLocaleString() }} 次调用</p>
        </div>
      </div>
    </div>
  </div>
</template>
