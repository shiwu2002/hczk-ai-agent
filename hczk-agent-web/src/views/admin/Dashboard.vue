<script setup>
import { ref, onMounted, computed } from 'vue'
import { useApiStore } from '@/stores/api'
import { Users, KeyRound, Coins, Wallet, Activity, Cpu, BarChart3 } from 'lucide-vue-next'

const api = useApiStore()
const loading = ref(false)
const users = ref([])
const apiKeys = ref([])
const billingRecords = ref([])
const models = ref([])

onMounted(loadData)

async function loadData() {
  loading.value = true
  const [usersRes, keysRes, billRes, modelsRes] = await Promise.all([
    api.get('/users'),
    api.get('/api-keys'),
    api.get('/billing/records'),
    api.get('/models')
  ])
  if (usersRes.code === 200) users.value = usersRes.data || []
  if (keysRes.code === 200) apiKeys.value = keysRes.data || []
  if (billRes.code === 200) billingRecords.value = billRes.data || []
  if (modelsRes.code === 200) models.value = modelsRes.data || []
  loading.value = false
}

// Stats computed
const stats = computed(() => {
  const now = new Date()
  const today = now.toDateString()
  let todayTokens = 0, totalRecharge = 0
  for (const r of billingRecords.value) {
    if (r.type === 'TOKEN_USAGE') {
      const d = r.createdAt ? new Date(r.createdAt) : null
      if (d && d.toDateString() === today) todayTokens += (r.inputTokens || 0) + (r.outputTokens || 0)
    }
    if (r.type === 'RECHARGE') totalRecharge += Number(r.amount || 0)
  }
  return [
    { label: '注册用户', value: users.value.length.toString(), sub: '总用户数', icon: Users, color: 'emerald' },
    { label: 'API Keys', value: apiKeys.value.length.toString(), sub: '已分配', icon: KeyRound, color: 'cyan' },
    { label: '今日Token消耗', value: todayTokens.toLocaleString(), sub: 'tokens', icon: Coins, color: 'amber' },
    { label: '总充值金额', value: '¥' + totalRecharge.toFixed(2), sub: '累计', icon: Wallet, color: 'purple' }
  ]
})

const colorMap = {
  emerald: { bg: 'bg-emerald-500/10', text: 'text-emerald-400' },
  cyan: { bg: 'bg-cyan-500/10', text: 'text-cyan-400' },
  amber: { bg: 'bg-amber-500/10', text: 'text-amber-400' },
  purple: { bg: 'bg-purple-500/10', text: 'text-purple-400' }
}

// Daily token data for chart (last 7 days)
const dailyData = computed(() => {
  const days = []
  const now = new Date()
  for (let i = 6; i >= 0; i--) {
    const d = new Date(now)
    d.setDate(d.getDate() - i)
    const dateStr = d.toDateString()
    const label = `${d.getMonth() + 1}/${d.getDate()}`
    let input = 0, output = 0
    for (const r of billingRecords.value) {
      if (r.type === 'TOKEN_USAGE' && r.createdAt && new Date(r.createdAt).toDateString() === dateStr) {
        input += r.inputTokens || 0
        output += r.outputTokens || 0
      }
    }
    days.push({ label, input, output, total: input + output })
  }
  return days
})

const maxDaily = computed(() => {
  const m = Math.max(...dailyData.value.map(d => d.total))
  return m || 1
})

// Recent users sorted by createdAt
const recentUsers = computed(() => {
  return [...users.value]
    .sort((a, b) => new Date(b.createdAt || 0) - new Date(a.createdAt || 0))
    .slice(0, 5)
})

// Model usage breakdown from billing records
const modelBreakdown = computed(() => {
  const map = {}
  for (const r of billingRecords.value) {
    if (r.type !== 'TOKEN_USAGE') continue
    const match = (r.detail || '').match(/模型\[(.+?)\]/)
    const model = match ? match[1] : '未知模型'
    if (!map[model]) map[model] = { model, tokens: 0, cost: 0, calls: 0 }
    map[model].tokens += (r.inputTokens || 0) + (r.outputTokens || 0)
    map[model].cost += Math.abs(Number(r.amount || 0))
    map[model].calls++
  }
  const list = Object.values(map).sort((a, b) => b.tokens - a.tokens)
  const maxTokens = list.length ? list[0].tokens : 1
  return list.map(item => ({
    ...item,
    percent: Math.round(item.tokens / maxTokens * 100),
    costStr: '¥' + item.cost.toFixed(4),
    tokenStr: formatTokens(item.tokens)
  }))
})

function formatDate(createdAt) {
  if (!createdAt) return '-'
  const d = new Date(createdAt)
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

function formatTokens(n) {
  if (!n) return '0'
  if (n >= 1000000) return (n / 1000000).toFixed(1) + 'M'
  if (n >= 1000) return (n / 1000).toFixed(1) + 'K'
  return n.toString()
}
</script>

<template>
  <div class="space-y-6 animate-fade-in">
    <!-- Header -->
    <div>
      <h1 class="text-2xl font-bold text-white">仪表盘</h1>
      <p class="text-slate-400 mt-1">平台运营数据实时监控</p>
    </div>

    <!-- Loading -->
    <div v-if="loading" class="flex items-center justify-center py-32">
      <div class="w-10 h-10 border-2 border-emerald-500/30 border-t-emerald-500 rounded-full animate-spin"></div>
      <span class="ml-3 text-slate-400">加载中...</span>
    </div>

    <template v-else>
      <!-- Stats Cards -->
      <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        <div v-for="stat in stats" :key="stat.label" class="glass-card p-6 glow-border">
          <div class="flex items-start justify-between">
            <div>
              <p class="text-sm text-slate-400">{{ stat.label }}</p>
              <p class="text-2xl font-bold text-white mt-2">{{ stat.value }}</p>
            </div>
            <div class="w-10 h-10 rounded-lg flex items-center justify-center" :class="colorMap[stat.color].bg">
              <component :is="stat.icon" class="w-5 h-5" :class="colorMap[stat.color].text" />
            </div>
          </div>
          <div class="mt-4">
            <span class="text-sm text-slate-500">{{ stat.sub }}</span>
          </div>
        </div>
      </div>

      <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <!-- Token 消耗趋势 Chart -->
        <div class="lg:col-span-2 glass-card p-6">
          <div class="flex items-center justify-between mb-6">
            <div>
              <h3 class="text-lg font-semibold text-white">Token 消耗趋势</h3>
              <p class="text-sm text-slate-400 mt-1">近7天平台整体 Token 使用量</p>
            </div>
            <div class="flex items-center gap-4 text-xs">
              <span class="flex items-center gap-1.5"><span class="w-3 h-3 rounded-sm bg-cyan-500/70 inline-block"></span> 输入</span>
              <span class="flex items-center gap-1.5"><span class="w-3 h-3 rounded-sm bg-emerald-500/70 inline-block"></span> 输出</span>
            </div>
          </div>
          <div class="h-64 flex items-end justify-between gap-3 px-2">
            <div v-for="day in dailyData" :key="day.label" class="flex-1 flex flex-col items-center gap-2">
              <div class="w-full flex flex-col" style="height: 100%">
                <div class="flex-1 flex items-end">
                  <div class="w-full flex flex-col-reverse">
                    <div
                      class="w-full bg-gradient-to-t from-cyan-500/30 to-cyan-500/70 rounded-t-sm transition-all hover:from-cyan-500/40 hover:to-cyan-500/90"
                      :style="{ height: maxDaily ? (day.input / maxDaily * 200) + 'px' : '0px', minHeight: day.input ? '2px' : '0px' }"
                    ></div>
                    <div
                      class="w-full bg-gradient-to-t from-emerald-500/30 to-emerald-500/70 transition-all hover:from-emerald-500/40 hover:to-emerald-500/90"
                      :style="{ height: maxDaily ? (day.output / maxDaily * 200) + 'px' : '0px', minHeight: day.output ? '2px' : '0px' }"
                    ></div>
                  </div>
                </div>
              </div>
              <span class="text-xs text-slate-500 whitespace-nowrap">{{ day.label }}</span>
            </div>
          </div>
          <!-- Daily totals -->
          <div class="flex justify-between gap-3 px-2 mt-1">
            <div v-for="day in dailyData" :key="day.label" class="flex-1 text-center">
              <span class="text-xs text-slate-500">{{ formatTokens(day.total) }}</span>
            </div>
          </div>
        </div>

        <!-- Model Status -->
        <div class="glass-card p-6">
          <div class="flex items-center gap-2 mb-4">
            <Cpu class="w-5 h-5 text-slate-400" />
            <h3 class="text-lg font-semibold text-white">模型状态</h3>
          </div>
          <div v-if="models.length === 0" class="text-center py-8 text-slate-500">暂无模型</div>
          <div v-else class="space-y-3">
            <div v-for="model in models" :key="model.id" class="p-3 rounded-lg bg-white/5">
              <div class="flex items-center justify-between mb-2">
                <div class="flex items-center gap-2.5">
                  <span class="status-dot" :class="model.status === 'ACTIVE' ? 'online' : 'offline'"></span>
                  <div>
                    <p class="text-sm font-medium text-white">{{ model.name }}</p>
                    <p class="text-xs text-slate-400">{{ model.provider }}</p>
                  </div>
                </div>
                <span :class="['px-2 py-0.5 text-xs rounded-full', model.status === 'ACTIVE' ? 'bg-emerald-500/10 text-emerald-400' : 'bg-slate-500/10 text-slate-400']">
                  {{ model.status === 'ACTIVE' ? '在线' : '离线' }}
                </span>
              </div>
              <div class="flex items-center justify-between text-xs text-slate-500">
                <span>输入 ¥{{ model.inputPrice || 0 }}/1K</span>
                <span>输出 ¥{{ model.outputPrice || 0 }}/1K</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Model Usage Breakdown -->
      <div class="glass-card p-6">
        <div class="flex items-center gap-2 mb-6">
          <BarChart3 class="w-5 h-5 text-slate-400" />
          <h3 class="text-lg font-semibold text-white">模型使用分布</h3>
        </div>
        <div v-if="modelBreakdown.length === 0" class="text-center py-8 text-slate-500">暂无数据</div>
        <div v-else class="space-y-4">
          <div v-for="item in modelBreakdown" :key="item.model" class="flex items-center gap-4">
            <span class="text-sm text-white w-28 truncate" :title="item.model">{{ item.model }}</span>
            <div class="flex-1 h-2 rounded-full bg-white/5 overflow-hidden">
              <div class="h-full bg-gradient-to-r from-emerald-500 to-cyan-500 rounded-full transition-all" :style="{ width: item.percent + '%' }"></div>
            </div>
            <span class="text-sm text-slate-400 w-24 text-right">{{ item.tokenStr }} tokens</span>
            <span class="text-sm text-slate-400 w-16 text-right">{{ item.calls }} 次</span>
            <span class="text-sm text-emerald-400 w-24 text-right">{{ item.costStr }}</span>
          </div>
        </div>
      </div>

      <!-- Recent Users -->
      <div class="glass-card p-6">
        <div class="flex items-center justify-between mb-6">
          <div class="flex items-center gap-2">
            <Activity class="w-5 h-5 text-slate-400" />
            <h3 class="text-lg font-semibold text-white">最近活跃用户</h3>
          </div>
          <router-link to="/admin/users" class="text-sm text-emerald-400 hover:text-emerald-300">查看全部</router-link>
        </div>
        <div v-if="recentUsers.length === 0" class="text-center py-8 text-slate-500">暂无用户</div>
        <div v-else class="overflow-x-auto">
          <table class="w-full">
            <thead>
              <tr class="border-b border-white/5">
                <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">用户</th>
                <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">邮箱</th>
                <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">余额</th>
                <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">Token用量</th>
                <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">注册时间</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-white/5">
              <tr v-for="user in recentUsers" :key="user.id" class="hover:bg-white/5 transition-colors">
                <td class="py-4">
                  <div class="flex items-center gap-3">
                    <div class="w-8 h-8 rounded-full bg-gradient-to-br from-emerald-500 to-cyan-500 flex items-center justify-center text-white text-xs font-bold">
                      {{ (user.name || user.username || '?')[0] }}
                    </div>
                    <span class="text-sm text-white">{{ user.name || user.username || '未知' }}</span>
                  </div>
                </td>
                <td class="py-4 text-sm text-slate-300">{{ user.email || '-' }}</td>
                <td class="py-4 text-sm text-slate-300">¥{{ (user.balance || 0).toFixed(2) }}</td>
                <td class="py-4 text-sm text-slate-300">{{ formatTokens(user.totalUsageTokens || 0) }}</td>
                <td class="py-4 text-sm text-slate-400">{{ formatDate(user.createdAt) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </template>
  </div>
</template>
