<script setup>
import { ref, onMounted, computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useApiStore } from '@/stores/api'
import { Wallet, BarChart3, KeyRound, Zap, ArrowUpRight } from 'lucide-vue-next'

const authStore = useAuthStore()
const api = useApiStore()
const loading = ref(true)
const usageRecords = ref([])
const allRecords = ref([])
const apiKeys = ref([])

onMounted(async () => {
  loading.value = true
  try {
    const [userRes, usageRes, recordsRes, keysRes] = await Promise.all([
      api.get('/users/me'),
      api.get('/billing/my-usage'),
      api.get('/billing/my-records'),
      api.get('/api-keys/user/' + authStore.user?.id)
    ])
    if (userRes.code === 200) authStore.user = userRes.data
    if (usageRes.code === 200) usageRecords.value = usageRes.data || []
    if (recordsRes.code === 200) allRecords.value = recordsRes.data || []
    if (keysRes.code === 200) apiKeys.value = keysRes.data || []
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
})

const stats = computed(() => {
  let totalInput = 0, totalOutput = 0
  for (const r of usageRecords.value) {
    totalInput += r.inputTokens || 0
    totalOutput += r.outputTokens || 0
  }
  const user = authStore.user
  return [
    { label: '账户余额', value: '¥' + (user?.balance || 0).toFixed(2), icon: Wallet, color: 'emerald' },
    { label: '总Token消耗', value: (totalInput + totalOutput).toLocaleString(), sub: 'tokens', icon: BarChart3, color: 'cyan' },
    { label: 'API Keys', value: apiKeys.value.length.toString(), sub: '个', icon: KeyRound, color: 'blue' },
    { label: '总调用次数', value: usageRecords.value.length.toLocaleString(), sub: '次', icon: Zap, color: 'amber' }
  ]
})

const dailyData = computed(() => {
  const days = []
  const now = new Date()
  for (let i = 6; i >= 0; i--) {
    const d = new Date(now)
    d.setDate(d.getDate() - i)
    const dateStr = d.toDateString()
    const label = `${d.getMonth() + 1}/${d.getDate()}`
    let input = 0, output = 0
    for (const r of usageRecords.value) {
      if (r.createdAt && new Date(r.createdAt).toDateString() === dateStr) {
        input += r.inputTokens || 0
        output += r.outputTokens || 0
      }
    }
    days.push({ label, input, output, total: input + output })
  }
  return days
})

const maxDaily = computed(() => Math.max(...dailyData.value.map(d => d.total)) || 1)

const recentActivity = computed(() => {
  return allRecords.value.slice(0, 8).map(r => ({
    action: r.type === 'RECHARGE' ? '充值' : '模型调用',
    detail: r.detail || '-',
    time: r.createdAt ? new Date(r.createdAt).toLocaleString() : '-',
    amount: Number(r.amount || 0),
    type: r.type
  }))
})

const colorMap = {
  emerald: { bg: 'bg-emerald-500/10', text: 'text-emerald-400' },
  cyan: { bg: 'bg-cyan-500/10', text: 'text-cyan-400' },
  blue: { bg: 'bg-blue-500/10', text: 'text-blue-400' },
  amber: { bg: 'bg-amber-500/10', text: 'text-amber-400' }
}
</script>

<template>
  <div class="space-y-6 animate-fade-in">
    <!-- Loading -->
    <div v-if="loading" class="flex items-center justify-center py-32">
      <div class="flex flex-col items-center gap-4">
        <div class="w-10 h-10 border-2 border-emerald-400/30 border-t-emerald-400 rounded-full animate-spin"></div>
        <p class="text-sm text-slate-400">加载中...</p>
      </div>
    </div>

    <template v-else>
      <!-- Header -->
      <div>
        <h1 class="text-2xl font-bold text-white">仪表盘</h1>
        <p class="text-slate-400 mt-1">欢迎回来，{{ authStore.currentUser?.username || '用户' }}</p>
      </div>

      <!-- Stats Cards -->
      <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        <div v-for="stat in stats" :key="stat.label" class="glass-card p-6 glow-border">
          <div class="flex items-start justify-between">
            <div>
              <p class="text-sm text-slate-400">{{ stat.label }}</p>
              <p class="text-2xl font-bold text-white mt-2">{{ stat.value }}</p>
              <p v-if="stat.sub" class="text-xs text-slate-500 mt-1">{{ stat.sub }}</p>
            </div>
            <div :class="['w-10 h-10 rounded-lg flex items-center justify-center', colorMap[stat.color].bg]">
              <component :is="stat.icon" :class="['w-5 h-5', colorMap[stat.color].text]" />
            </div>
          </div>
        </div>
      </div>

      <!-- Chart + Activity -->
      <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <!-- Usage Trend -->
        <div class="lg:col-span-2 glass-card p-6">
          <div class="flex items-center justify-between mb-6">
            <div>
              <h3 class="text-lg font-semibold text-white">用量趋势</h3>
              <p class="text-sm text-slate-400 mt-1">近7天 Token 消耗</p>
            </div>
            <div class="flex items-center gap-4">
              <div class="flex items-center gap-1.5">
                <span class="w-3 h-3 rounded-sm bg-cyan-500/60"></span>
                <span class="text-xs text-slate-400">输入</span>
              </div>
              <div class="flex items-center gap-1.5">
                <span class="w-3 h-3 rounded-sm bg-emerald-500/60"></span>
                <span class="text-xs text-slate-400">输出</span>
              </div>
              <router-link to="/usage" class="text-sm text-emerald-400 hover:text-emerald-300 flex items-center gap-1 ml-2">详情 <ArrowUpRight class="w-4 h-4" /></router-link>
            </div>
          </div>
          <div class="h-56 flex items-end justify-between gap-3 px-2">
            <div v-for="day in dailyData" :key="day.label" class="flex-1 flex flex-col items-center gap-2">
              <div class="w-full flex flex-col" :style="{ height: Math.max((day.total / maxDaily) * 100, 2) + '%' }">
                <div
                  v-if="day.output > 0"
                  class="w-full bg-gradient-to-t from-emerald-500/30 to-emerald-500/60 rounded-t-lg"
                  :style="{ height: (day.output / day.total * 100) + '%' }"
                ></div>
                <div
                  v-if="day.input > 0"
                  class="w-full bg-gradient-to-t from-cyan-500/30 to-cyan-500/60"
                  :class="{ 'rounded-t-lg': day.output === 0 }"
                  :style="{ height: (day.input / day.total * 100) + '%' }"
                ></div>
              </div>
              <span class="text-xs text-slate-500">{{ day.label }}</span>
            </div>
          </div>
        </div>

        <!-- Recent Activity -->
        <div class="glass-card p-6">
          <h3 class="text-lg font-semibold text-white mb-4">最近动态</h3>
          <div v-if="recentActivity.length === 0" class="text-sm text-slate-500 py-8 text-center">暂无记录</div>
          <div v-else class="space-y-3">
            <div v-for="(activity, i) in recentActivity" :key="i" class="flex items-start gap-3">
              <div :class="['w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0', activity.type === 'RECHARGE' ? 'bg-emerald-500/10' : 'bg-white/5']">
                <component :is="activity.type === 'RECHARGE' ? Wallet : Zap" :class="['w-4 h-4', activity.type === 'RECHARGE' ? 'text-emerald-400' : 'text-slate-400']" />
              </div>
              <div class="flex-1 min-w-0">
                <div class="flex items-center justify-between">
                  <p class="text-sm text-white truncate">{{ activity.action }}</p>
                  <span :class="['text-sm font-medium flex-shrink-0 ml-2', activity.amount >= 0 ? 'text-emerald-400' : 'text-orange-400']">
                    {{ activity.amount >= 0 ? '+' : '' }}{{ activity.amount.toFixed(2) }}
                  </span>
                </div>
                <p class="text-xs text-slate-400 truncate">{{ activity.detail }}</p>
                <p class="text-xs text-slate-500 mt-0.5">{{ activity.time }}</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>
