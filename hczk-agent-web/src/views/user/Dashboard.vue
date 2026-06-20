<script setup>
import { ref, onMounted, computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useApiStore } from '@/stores/api'
import { Wallet, BarChart3, KeyRound, Zap, ArrowUpRight, TrendingUp, TrendingDown, Clock, Bot, Activity, Sparkles, ChevronRight, CreditCard } from 'lucide-vue-next'

const authStore = useAuthStore()
const api = useApiStore()
const loading = ref(true)
const usageRecords = ref([])
const allRecords = ref([])
const apiKeys = ref([])
const hoveredDay = ref(null)

onMounted(async () => {
  loading.value = true
  try {
    const [userRes, usageRes, recordsRes, keysRes] = await Promise.all([
      api.get('/users/me'),
      api.get('/billing/my-usage'),
      api.get('/billing/my-records'),
      api.get('/api-keys/user/' + authStore.currentUser?.userId)
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

// 根据时间生成问候语
const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour >= 5 && hour < 12) return '早上好'
  if (hour >= 12 && hour < 18) return '下午好'
  return '晚上好'
})

// 今日数据
const todayStats = computed(() => {
  const todayStr = new Date().toDateString()
  let input = 0, output = 0, calls = 0, cost = 0
  for (const r of usageRecords.value) {
    if (r.createdAt && new Date(r.createdAt).toDateString() === todayStr) {
      input += r.inputTokens || 0
      output += r.outputTokens || 0
      calls++
      cost += Math.abs(Number(r.amount || 0))
    }
  }
  return { input, output, total: input + output, calls, cost }
})

// 计算昨日对比数据
const yesterdayComparison = computed(() => {
  const now = new Date()
  const todayStr = now.toDateString()
  const yesterday = new Date(now)
  yesterday.setDate(yesterday.getDate() - 1)
  const yesterdayStr = yesterday.toDateString()

  let todayInput = 0, todayOutput = 0, todayCalls = 0
  let yesterdayInput = 0, yesterdayOutput = 0, yesterdayCalls = 0

  for (const r of usageRecords.value) {
    if (!r.createdAt) continue
    const d = new Date(r.createdAt).toDateString()
    if (d === todayStr) {
      todayInput += r.inputTokens || 0
      todayOutput += r.outputTokens || 0
      todayCalls++
    } else if (d === yesterdayStr) {
      yesterdayInput += r.inputTokens || 0
      yesterdayOutput += r.outputTokens || 0
      yesterdayCalls++
    }
  }

  const todayTokens = todayInput + todayOutput
  const yesterdayTokens = yesterdayInput + yesterdayOutput

  function calcPercent(current, previous) {
    if (previous === 0) return current > 0 ? 100 : 0
    return Math.round(((current - previous) / previous) * 100)
  }

  return {
    balance: { percent: 0, hasData: false },
    tokens: { percent: calcPercent(todayTokens, yesterdayTokens), hasData: yesterdayTokens > 0 },
    apiKeys: { percent: 0, hasData: false },
    calls: { percent: calcPercent(todayCalls, yesterdayCalls), hasData: yesterdayCalls > 0 }
  }
})

const stats = computed(() => {
  let totalInput = 0, totalOutput = 0
  for (const r of usageRecords.value) {
    totalInput += r.inputTokens || 0
    totalOutput += r.outputTokens || 0
  }
  const comp = yesterdayComparison.value
  // 今日消耗
  let todayCost = 0
  const todayStr = new Date().toDateString()
  for (const r of allRecords.value) {
    if (r.type !== 'RECHARGE' && r.createdAt && new Date(r.createdAt).toDateString() === todayStr) {
      todayCost += Math.abs(Number(r.amount || 0))
    }
  }
  return [
    { label: '今日消耗', value: '¥' + todayCost.toFixed(2), icon: Zap, color: 'amber', key: 'todayCost', trend: comp.calls },
    { label: '总Token消耗', value: (totalInput + totalOutput).toLocaleString(), sub: 'tokens', icon: BarChart3, color: 'cyan', key: 'tokens', trend: comp.tokens },
    { label: '总调用次数', value: usageRecords.value.length.toLocaleString(), sub: '次', icon: Activity, color: 'emerald', key: 'calls', trend: comp.calls }
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
    days.push({ label, input, output, total: input + output, isToday: i === 0 })
  }
  return days
})

const maxDaily = computed(() => Math.max(...dailyData.value.map(d => d.total)) || 1)

// Line chart computed values
const chartPadding = { top: 20, right: 20, bottom: 36, left: 50 }
const chartWidth = 700
const chartHeight = 240
const plotWidth = chartWidth - chartPadding.left - chartPadding.right
const plotHeight = chartHeight - chartPadding.top - chartPadding.bottom

const lineChartMax = computed(() => {
  const maxVal = Math.max(...dailyData.value.map(d => Math.max(d.input, d.output)))
  return maxVal || 1
})

const lineYTicks = computed(() => {
  const max = lineChartMax.value
  const ticks = []
  const count = 5
  for (let i = 0; i <= count; i++) {
    const val = Math.round((max / count) * i)
    const y = chartPadding.top + plotHeight - (i / count) * plotHeight
    ticks.push({ val, y })
  }
  return ticks
})

function lineX(index) {
  return chartPadding.left + (index / (dailyData.value.length - 1)) * plotWidth
}

function lineY(value) {
  return chartPadding.top + plotHeight - (value / lineChartMax.value) * plotHeight
}

function generateSmoothPath(data, valueKey) {
  const points = data.map((d, i) => ({ x: lineX(i), y: lineY(d[valueKey]) }))
  if (points.length < 2) return ''
  if (points.length === 2) {
    return `M${points[0].x},${points[0].y} L${points[1].x},${points[1].y}`
  }
  let path = `M${points[0].x},${points[0].y}`
  for (let i = 0; i < points.length - 1; i++) {
    const p0 = points[Math.max(0, i - 1)]
    const p1 = points[i]
    const p2 = points[i + 1]
    const p3 = points[Math.min(points.length - 1, i + 2)]
    const tension = 0.3
    const cp1x = p1.x + (p2.x - p0.x) * tension
    const cp1y = p1.y + (p2.y - p0.y) * tension
    const cp2x = p2.x - (p3.x - p1.x) * tension
    const cp2y = p2.y - (p3.y - p1.y) * tension
    path += ` C${cp1x},${cp1y} ${cp2x},${cp2y} ${p2.x},${p2.y}`
  }
  return path
}

function generateSmoothArea(data, valueKey) {
  const points = data.map((d, i) => ({ x: lineX(i), y: lineY(d[valueKey]) }))
  if (points.length < 2) return ''
  const bottomY = chartPadding.top + plotHeight
  let path = `M${points[0].x},${bottomY} L${points[0].x},${points[0].y}`
  for (let i = 0; i < points.length - 1; i++) {
    const p0 = points[Math.max(0, i - 1)]
    const p1 = points[i]
    const p2 = points[i + 1]
    const p3 = points[Math.min(points.length - 1, i + 2)]
    const tension = 0.3
    const cp1x = p1.x + (p2.x - p0.x) * tension
    const cp1y = p1.y + (p2.y - p0.y) * tension
    const cp2x = p2.x - (p3.x - p1.x) * tension
    const cp2y = p2.y - (p3.y - p1.y) * tension
    path += ` C${cp1x},${cp1y} ${cp2x},${cp2y} ${p2.x},${p2.y}`
  }
  path += ` L${points[points.length - 1].x},${bottomY} Z`
  return path
}

const inputSmoothPath = computed(() => generateSmoothPath(dailyData.value, 'input'))
const outputSmoothPath = computed(() => generateSmoothPath(dailyData.value, 'output'))
const inputAreaPath = computed(() => generateSmoothArea(dailyData.value, 'input'))
const outputAreaPath = computed(() => generateSmoothArea(dailyData.value, 'output'))

function formatTokenShort(val) {
  if (val >= 1000000) return (val / 1000000).toFixed(1) + 'M'
  if (val >= 1000) return (val / 1000).toFixed(1) + 'K'
  return String(val)
}

function setHoveredDay(index) {
  hoveredDay.value = index
}

function clearHoveredDay() {
  hoveredDay.value = null
}

function relativeTime(dateStr) {
  if (!dateStr) return ''
  const now = new Date()
  const date = new Date(dateStr)
  const diff = Math.floor((now - date) / 1000)
  if (diff < 60) return '刚刚'
  if (diff < 3600) return Math.floor(diff / 60) + '分钟前'
  if (diff < 86400) return Math.floor(diff / 3600) + '小时前'
  if (diff < 604800) return Math.floor(diff / 86400) + '天前'
  return ''
}

// 从详情中提取模型名称
function extractModelName(detail) {
  if (!detail) return ''
  const match = detail.match(/\[([^\]]+)\]/)
  return match ? match[1] : detail
}

// 智能动态：逐条显示，不显示模型名称
const recentActivity = computed(() => {
  return allRecords.value.slice(0, 6).map(r => {
    const isRecharge = r.type === 'RECHARGE'
    const tokens = (r.inputTokens || 0) + (r.outputTokens || 0)
    return {
      type: r.type,
      label: isRecharge ? '充值' : 'API 调用',
      sub: isRecharge ? (r.paymentMethod === 'alipay' ? '支付宝' : r.paymentMethod === 'wechat' ? '微信' : '') : (tokens > 0 ? `${tokens} tokens` : ''),
      amount: Number(r.amount || 0),
      relativeTime: relativeTime(r.createdAt),
      createdAt: r.createdAt
    }
  })
})

const colorMap = {
  emerald: { bg: 'bg-emerald-500/10', text: 'text-emerald-400', border: 'border-l-emerald-500', gradient: 'from-emerald-500/[0.03] to-transparent' },
  cyan: { bg: 'bg-cyan-500/10', text: 'text-cyan-400', border: 'border-l-cyan-500', gradient: 'from-cyan-500/[0.03] to-transparent' },
  blue: { bg: 'bg-blue-500/10', text: 'text-blue-400', border: 'border-l-blue-500', gradient: 'from-blue-500/[0.03] to-transparent' },
  amber: { bg: 'bg-amber-500/10', text: 'text-amber-400', border: 'border-l-amber-500', gradient: 'from-amber-500/[0.03] to-transparent' },
  purple: { bg: 'bg-purple-500/10', text: 'text-purple-400', border: 'border-l-purple-500', gradient: 'from-purple-500/[0.03] to-transparent' }
}

// 快捷操作
const quickActions = [
  { label: '我的智能体', desc: '查看运行状态', icon: Bot, to: '/agents', color: 'cyan' },
  { label: '用量统计', desc: 'Token 消耗详情', icon: BarChart3, to: '/usage', color: 'emerald' },
  { label: '充值中心', desc: '账户余额充值', icon: Wallet, to: '/recharge', color: 'amber' },
  { label: '账单明细', desc: '查看消费记录', icon: CreditCard, to: '/billing', color: 'purple' }
]
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
      <!-- Hero Banner + Stats -->
      <div class="relative rounded-2xl overflow-hidden">
        <div class="absolute inset-0 bg-gradient-to-br from-emerald-600/20 via-cyan-600/10 to-blue-600/5"></div>
        <div class="absolute inset-0 bg-[url('data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iNjAiIGhlaWdodD0iNjAiIHZpZXdCb3g9IjAgMCA2MCA2MCIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj48ZyBmaWxsPSJub25lIiBmaWxsLXJ1bGU9ImV2ZW5vZGQiPjxnIGZpbGw9IiNmZmYiIGZpbGwtb3BhY2l0eT0iMC4wMyI+PGNpcmNsZSBjeD0iMzAiIGN5PSIzMCIgcj0iMSIvPjwvZz48L2c+PC9zdmc+')] opacity-50"></div>
        <div class="relative p-6 md:p-8">
          <!-- Greeting -->
          <div class="flex items-center gap-4 mb-6">
            <div class="w-14 h-14 rounded-2xl bg-gradient-to-br from-emerald-500 to-cyan-500 flex items-center justify-center text-white text-xl font-bold shadow-lg shadow-emerald-500/20 flex-shrink-0">
              {{ authStore.currentUser?.username?.[0] || 'U' }}
            </div>
            <div>
              <h1 class="text-xl md:text-2xl font-bold text-white">
                {{ greeting }}，
                <span class="bg-gradient-to-r from-emerald-400 via-cyan-400 to-blue-400 bg-clip-text text-transparent">
                  {{ authStore.currentUser?.username || '用户' }}
                </span>
              </h1>
              <p class="text-sm text-slate-400 mt-1">{{ new Date().toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' }) }}</p>
            </div>
          </div>
          <!-- Unified Stats Grid -->
          <div class="grid grid-cols-2 md:grid-cols-5 gap-4">
            <div class="bg-white/[0.04] rounded-xl p-4 border border-white/[0.06] hover:border-white/[0.12] transition-all">
              <div class="flex items-center gap-2 mb-2">
                <div class="w-7 h-7 rounded-lg bg-amber-500/10 flex items-center justify-center">
                  <Zap class="w-3.5 h-3.5 text-amber-400" />
                </div>
                <span class="text-xs text-slate-400">今日消耗</span>
              </div>
              <p class="text-lg font-bold text-white">¥{{ todayStats.cost.toFixed(2) }}</p>
              <div v-if="yesterdayComparison.calls.hasData" class="flex items-center gap-1 mt-1">
                <span :class="['text-[11px] font-medium', yesterdayComparison.calls.percent >= 0 ? 'text-emerald-400' : 'text-red-400']">
                  {{ yesterdayComparison.calls.percent >= 0 ? '↑' : '↓' }}{{ Math.abs(yesterdayComparison.calls.percent) }}%
                </span>
              </div>
            </div>
            <div class="bg-white/[0.04] rounded-xl p-4 border border-white/[0.06] hover:border-white/[0.12] transition-all">
              <div class="flex items-center gap-2 mb-2">
                <div class="w-7 h-7 rounded-lg bg-cyan-500/10 flex items-center justify-center">
                  <BarChart3 class="w-3.5 h-3.5 text-cyan-400" />
                </div>
                <span class="text-xs text-slate-400">今日Token</span>
              </div>
              <p class="text-lg font-bold text-white">{{ todayStats.total.toLocaleString() }}</p>
              <div v-if="yesterdayComparison.tokens.hasData" class="flex items-center gap-1 mt-1">
                <span :class="['text-[11px] font-medium', yesterdayComparison.tokens.percent >= 0 ? 'text-emerald-400' : 'text-red-400']">
                  {{ yesterdayComparison.tokens.percent >= 0 ? '↑' : '↓' }}{{ Math.abs(yesterdayComparison.tokens.percent) }}%
                </span>
              </div>
            </div>
            <div class="bg-white/[0.04] rounded-xl p-4 border border-white/[0.06] hover:border-white/[0.12] transition-all">
              <div class="flex items-center gap-2 mb-2">
                <div class="w-7 h-7 rounded-lg bg-emerald-500/10 flex items-center justify-center">
                  <Activity class="w-3.5 h-3.5 text-emerald-400" />
                </div>
                <span class="text-xs text-slate-400">今日调用</span>
              </div>
              <p class="text-lg font-bold text-white">{{ todayStats.calls }}</p>
              <div v-if="yesterdayComparison.calls.hasData" class="flex items-center gap-1 mt-1">
                <span :class="['text-[11px] font-medium', yesterdayComparison.calls.percent >= 0 ? 'text-emerald-400' : 'text-red-400']">
                  {{ yesterdayComparison.calls.percent >= 0 ? '↑' : '↓' }}{{ Math.abs(yesterdayComparison.calls.percent) }}%
                </span>
              </div>
            </div>
            <div class="bg-white/[0.04] rounded-xl p-4 border border-white/[0.06] hover:border-white/[0.12] transition-all">
              <div class="flex items-center gap-2 mb-2">
                <div class="w-7 h-7 rounded-lg bg-blue-500/10 flex items-center justify-center">
                  <Wallet class="w-3.5 h-3.5 text-blue-400" />
                </div>
                <span class="text-xs text-slate-400">账户余额</span>
              </div>
              <p class="text-lg font-bold text-emerald-400">¥{{ (authStore.currentUser?.balance || 0).toFixed(2) }}</p>
            </div>
            <div class="bg-white/[0.04] rounded-xl p-4 border border-white/[0.06] hover:border-white/[0.12] transition-all col-span-2 md:col-span-1">
              <div class="flex items-center gap-2 mb-2">
                <div class="w-7 h-7 rounded-lg bg-purple-500/10 flex items-center justify-center">
                  <BarChart3 class="w-3.5 h-3.5 text-purple-400" />
                </div>
                <span class="text-xs text-slate-400">总Token消耗</span>
              </div>
              <p class="text-lg font-bold text-white">{{ stats[1]?.value || '0' }}</p>
              <p class="text-[11px] text-slate-500 mt-0.5">tokens</p>
            </div>
          </div>
        </div>
      </div>

      <!-- Main Content: Chart + Sidebar -->
      <div class="grid grid-cols-1 lg:grid-cols-3 gap-5">
        <!-- Usage Trend Chart -->
        <div class="lg:col-span-2 glass-card rounded-2xl overflow-hidden">
          <div class="h-1 bg-gradient-to-r from-cyan-500 via-emerald-500 to-blue-500"></div>
          <div class="p-5 md:p-6">
            <div class="flex items-center justify-between mb-5">
              <div>
                <h3 class="text-base font-semibold text-white">用量趋势</h3>
                <p class="text-xs text-slate-400 mt-0.5">近7天 Token 消耗</p>
              </div>
              <div class="flex items-center gap-3">
                <div class="flex items-center gap-1.5">
                  <span class="w-2.5 h-2.5 rounded-full bg-cyan-500/70"></span>
                  <span class="text-xs text-slate-400">输入</span>
                </div>
                <div class="flex items-center gap-1.5">
                  <span class="w-2.5 h-2.5 rounded-full bg-emerald-500/70"></span>
                  <span class="text-xs text-slate-400">输出</span>
                </div>
                <router-link to="/usage" class="text-xs text-emerald-400 hover:text-emerald-300 flex items-center gap-0.5 ml-1">详情<ArrowUpRight class="w-3 h-3" /></router-link>
              </div>
            </div>
            <div v-if="dailyData.every(d => d.total === 0)" class="flex items-center justify-center py-16">
              <div class="text-center">
                <Activity class="w-10 h-10 text-slate-600 mx-auto mb-2" />
                <p class="text-sm text-slate-500">暂无消耗数据</p>
                <p class="text-xs text-slate-600 mt-1">开始使用 API 后将显示趋势</p>
              </div>
            </div>
            <div v-else class="w-full overflow-x-auto">
              <svg :viewBox="`0 0 ${chartWidth} ${chartHeight}`" class="w-full" style="min-width: 400px;" preserveAspectRatio="xMidYMid meet" @mouseleave="clearHoveredDay()">
                <defs>
                  <linearGradient id="dashInputGradient" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stop-color="#06b6d4" stop-opacity="0.25" />
                    <stop offset="100%" stop-color="#06b6d4" stop-opacity="0" />
                  </linearGradient>
                  <linearGradient id="dashOutputGradient" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stop-color="#10b981" stop-opacity="0.25" />
                    <stop offset="100%" stop-color="#10b981" stop-opacity="0" />
                  </linearGradient>
                  <filter id="lineGlow">
                    <feGaussianBlur stdDeviation="2" result="blur" />
                    <feMerge><feMergeNode in="blur" /><feMergeNode in="SourceGraphic" /></feMerge>
                  </filter>
                </defs>

                <!-- Grid lines -->
                <line v-for="tick in lineYTicks" :key="'grid-' + tick.val" :x1="chartPadding.left" :y1="tick.y" :x2="chartWidth - chartPadding.right" :y2="tick.y" stroke="rgba(148,163,184,0.06)" stroke-dasharray="4 4" />
                <!-- Y-axis labels -->
                <text v-for="tick in lineYTicks" :key="'yl-' + tick.val" :x="chartPadding.left - 8" :y="tick.y + 4" text-anchor="end" fill="#475569" font-size="10">{{ formatTokenShort(tick.val) }}</text>
                <!-- X-axis labels -->
                <text v-for="(day, i) in dailyData" :key="'xl-' + i" :x="lineX(i)" :y="chartHeight - 8" text-anchor="middle" :fill="hoveredDay === i ? '#e2e8f0' : (day.isToday ? '#10b981' : '#475569')" :font-weight="hoveredDay === i || day.isToday ? '600' : '400'" font-size="10">{{ day.label }}</text>

                <!-- Hover column highlight -->
                <rect
                  v-if="hoveredDay !== null"
                  :x="lineX(hoveredDay) - plotWidth / dailyData.length / 2"
                  :y="chartPadding.top"
                  :width="plotWidth / dailyData.length"
                  :height="plotHeight"
                  fill="rgba(148,163,184,0.04)"
                  rx="4"
                />
                <!-- Vertical reference line -->
                <line
                  v-if="hoveredDay !== null"
                  :x1="lineX(hoveredDay)"
                  :y1="chartPadding.top"
                  :x2="lineX(hoveredDay)"
                  :y2="chartPadding.top + plotHeight"
                  stroke="rgba(148,163,184,0.15)"
                  stroke-dasharray="3 3"
                />

                <!-- Area fills -->
                <path :d="inputAreaPath" fill="url(#dashInputGradient)" />
                <path :d="outputAreaPath" fill="url(#dashOutputGradient)" />
                <!-- Lines with glow -->
                <path :d="inputSmoothPath" fill="none" stroke="#06b6d4" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" filter="url(#lineGlow)" />
                <path :d="outputSmoothPath" fill="none" stroke="#10b981" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" filter="url(#lineGlow)" />

                <!-- Data points - input -->
                <circle v-for="(day, i) in dailyData" :key="'ip-' + i"
                  :cx="lineX(i)" :cy="lineY(day.input)"
                  :r="hoveredDay === i ? 5 : 3.5"
                  :fill="hoveredDay === i ? '#06b6d4' : '#0e1629'"
                  :stroke="'#06b6d4'"
                  :stroke-width="hoveredDay === i ? 2.5 : 2"
                  class="transition-all duration-150"
                />
                <!-- Data points - output -->
                <circle v-for="(day, i) in dailyData" :key="'op-' + i"
                  :cx="lineX(i)" :cy="lineY(day.output)"
                  :r="hoveredDay === i ? 5 : 3.5"
                  :fill="hoveredDay === i ? '#10b981' : '#0e1629'"
                  :stroke="'#10b981'"
                  :stroke-width="hoveredDay === i ? 2.5 : 2"
                  class="transition-all duration-150"
                />

                <!-- Invisible hit areas for each day column -->
                <rect
                  v-for="(day, i) in dailyData" :key="'hit-' + i"
                  :x="lineX(i) - plotWidth / dailyData.length / 2"
                  :y="chartPadding.top"
                  :width="plotWidth / dailyData.length"
                  :height="plotHeight"
                  fill="transparent"
                  class="cursor-pointer"
                  @mouseenter="setHoveredDay(i)"
                />

                <!-- Unified tooltip -->
                <g v-if="hoveredDay !== null" class="pointer-events-none">
                  <g v-if="dailyData[hoveredDay]">
                    <rect
                      :x="Math.max(chartPadding.left, Math.min(lineX(hoveredDay) - 62, chartWidth - chartPadding.right - 124))"
                      :y="chartPadding.top - 4"
                      width="124"
                      height="56"
                      rx="8"
                      fill="rgba(15,23,42,0.92)"
                      stroke="rgba(148,163,184,0.12)"
                      stroke-width="1"
                    />
                    <!-- Date -->
                    <text
                      :x="Math.max(chartPadding.left + 10, Math.min(lineX(hoveredDay) - 62 + 10, chartWidth - chartPadding.right - 114))"
                      :y="chartPadding.top + 12"
                      fill="#94a3b8"
                      font-size="10"
                      font-weight="500"
                    >{{ dailyData[hoveredDay].label }}{{ dailyData[hoveredDay].isToday ? ' · 今日' : '' }}</text>
                    <!-- Input -->
                    <circle
                      :cx="Math.max(chartPadding.left + 14, Math.min(lineX(hoveredDay) - 62 + 14, chartWidth - chartPadding.right - 110))"
                      :cy="chartPadding.top + 26"
                      r="3"
                      fill="#06b6d4"
                    />
                    <text
                      :x="Math.max(chartPadding.left + 22, Math.min(lineX(hoveredDay) - 62 + 22, chartWidth - chartPadding.right - 102))"
                      :y="chartPadding.top + 30"
                      fill="#06b6d4"
                      font-size="11"
                      font-weight="500"
                    >输入 {{ formatTokenShort(dailyData[hoveredDay].input) }}</text>
                    <!-- Output -->
                    <circle
                      :cx="Math.max(chartPadding.left + 14, Math.min(lineX(hoveredDay) - 62 + 14, chartWidth - chartPadding.right - 110))"
                      :cy="chartPadding.top + 42"
                      r="3"
                      fill="#10b981"
                    />
                    <text
                      :x="Math.max(chartPadding.left + 22, Math.min(lineX(hoveredDay) - 62 + 22, chartWidth - chartPadding.right - 102))"
                      :y="chartPadding.top + 46"
                      fill="#10b981"
                      font-size="11"
                      font-weight="500"
                    >输出 {{ formatTokenShort(dailyData[hoveredDay].output) }}</text>
                  </g>
                </g>
              </svg>
            </div>
          </div>
        </div>

        <!-- Right Sidebar -->
        <div class="flex flex-col">
          <!-- Quick Actions -->
          <div class="glass-card rounded-2xl p-5 flex-1 flex flex-col">
            <h3 class="text-sm font-semibold text-white mb-3 flex items-center gap-2">
              <Sparkles class="w-4 h-4 text-emerald-400" />
              快捷操作
            </h3>
            <div class="grid grid-cols-2 gap-3 flex-1 content-center">
              <router-link
                v-for="action in quickActions"
                :key="action.label"
                :to="action.to"
                class="group flex flex-col items-center gap-3 p-5 rounded-xl bg-white/[0.02] border border-white/[0.04] hover:bg-white/[0.05] hover:border-white/[0.08] transition-all duration-200"
              >
                <div :class="[
                  'w-11 h-11 rounded-xl flex items-center justify-center transition-transform duration-200 group-hover:scale-110',
                  action.color === 'cyan' ? 'bg-cyan-500/10' : action.color === 'emerald' ? 'bg-emerald-500/10' : action.color === 'amber' ? 'bg-amber-500/10' : 'bg-blue-500/10'
                ]">
                  <component :is="action.icon" :class="[
                    'w-5 h-5',
                    action.color === 'cyan' ? 'text-cyan-400' : action.color === 'emerald' ? 'text-emerald-400' : action.color === 'amber' ? 'text-amber-400' : 'text-blue-400'
                  ]" />
                </div>
                <div class="text-center">
                  <p class="text-xs font-medium text-white">{{ action.label }}</p>
                  <p class="text-[10px] text-slate-500 mt-0.5">{{ action.desc }}</p>
                </div>
              </router-link>
            </div>
          </div>

        </div>
      </div>
    </template>
  </div>
</template>
