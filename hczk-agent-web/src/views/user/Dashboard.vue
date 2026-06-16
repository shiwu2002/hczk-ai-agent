<script setup>
import { ref, onMounted, computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useApiStore } from '@/stores/api'
import { Wallet, BarChart3, KeyRound, Zap, ArrowUpRight, TrendingUp, TrendingDown } from 'lucide-vue-next'

const authStore = useAuthStore()
const api = useApiStore()
const loading = ref(true)
const usageRecords = ref([])
const allRecords = ref([])
const apiKeys = ref([])
const hoveredPoint = ref(null)

onMounted(async () => {
  loading.value = true
  try {
    const [userRes, usageRes, recordsRes, keysRes] = await Promise.all([
      api.get('/users/me'),
      api.get('/billing/my-usage'),
      api.get('/billing/my-records'),
      api.get('/api-keys/user/' + authStore.user?.userId)
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
    balance: { percent: 0, hasData: false }, // 余额无法从 usageRecords 计算
    tokens: { percent: calcPercent(todayTokens, yesterdayTokens), hasData: yesterdayTokens > 0 },
    apiKeys: { percent: 0, hasData: false }, // API Keys 数量无法按日对比
    calls: { percent: calcPercent(todayCalls, yesterdayCalls), hasData: yesterdayCalls > 0 }
  }
})

const stats = computed(() => {
  let totalInput = 0, totalOutput = 0
  for (const r of usageRecords.value) {
    totalInput += r.inputTokens || 0
    totalOutput += r.outputTokens || 0
  }
  const user = authStore.user
  const comp = yesterdayComparison.value
  return [
    { label: '账户余额', value: '¥' + (user?.balance || 0).toFixed(2), icon: Wallet, color: 'emerald', key: 'balance', trend: comp.balance },
    { label: '总Token消耗', value: (totalInput + totalOutput).toLocaleString(), sub: 'tokens', icon: BarChart3, color: 'cyan', key: 'tokens', trend: comp.tokens },
    { label: 'API Keys', value: apiKeys.value.length.toString(), sub: '个', icon: KeyRound, color: 'blue', key: 'apiKeys', trend: comp.apiKeys },
    { label: '总调用次数', value: usageRecords.value.length.toLocaleString(), sub: '次', icon: Zap, color: 'amber', key: 'calls', trend: comp.calls }
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

// 贝塞尔曲线平滑路径生成
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

// 贝塞尔曲线面积路径生成（闭合到底部）
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

// Tooltip 位置计算（防止超出视口）
function tooltipPosition(index, value, type) {
  const x = lineX(index)
  const y = lineY(value)
  const tooltipW = 96
  const tooltipH = 24
  const padding = 8

  // 水平位置：防止左右超出
  let tx = x - tooltipW / 2
  if (tx < chartPadding.left) tx = chartPadding.left
  if (tx + tooltipW > chartWidth - chartPadding.right) tx = chartWidth - chartPadding.right - tooltipW

  // 垂直位置：默认在点上方，空间不足则放下方
  let ty = y - tooltipH - padding
  if (ty < chartPadding.top) ty = y + padding

  return { x: tx, y: ty, textX: tx + tooltipW / 2, textY: ty + tooltipH / 2 + 4 }
}

function formatTokenShort(val) {
  if (val >= 1000000) return (val / 1000000).toFixed(1) + 'M'
  if (val >= 1000) return (val / 1000).toFixed(1) + 'K'
  return String(val)
}

function setHoveredPoint(dayIndex, type) {
  hoveredPoint.value = { dayIndex, type }
}

function clearHoveredPoint() {
  hoveredPoint.value = null
}

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
      <div class="grid grid-cols-2 lg:grid-cols-4 gap-4 md:gap-6">
        <div v-for="stat in stats" :key="stat.label" class="glass-card p-4 md:p-6 glow-border">
          <div class="flex items-start justify-between">
            <div class="min-w-0">
              <p class="text-xs md:text-sm text-slate-400">{{ stat.label }}</p>
              <p class="text-lg md:text-2xl font-bold text-white mt-1 md:mt-2 truncate">{{ stat.value }}</p>
              <p v-if="stat.sub" class="text-xs text-slate-500 mt-0.5">{{ stat.sub }}</p>
              <!-- 变化趋势 -->
              <div v-if="stat.trend?.hasData" class="flex items-center gap-1 mt-1.5">
                <component
                  :is="stat.trend.percent >= 0 ? TrendingUp : TrendingDown"
                  :class="['w-3.5 h-3.5', stat.trend.percent >= 0 ? 'text-emerald-400' : 'text-red-400']"
                />
                <span :class="['text-xs font-medium', stat.trend.percent >= 0 ? 'text-emerald-400' : 'text-red-400']">
                  {{ stat.trend.percent >= 0 ? '↑' : '↓' }}{{ Math.abs(stat.trend.percent) }}%
                </span>
                <span class="text-xs text-slate-500">较昨日</span>
              </div>
            </div>
            <div :class="['w-9 h-9 md:w-10 md:h-10 rounded-lg flex items-center justify-center flex-shrink-0', colorMap[stat.color].bg]">
              <component :is="stat.icon" :class="['w-4 h-4 md:w-5 md:h-5', colorMap[stat.color].text]" />
            </div>
          </div>
        </div>
      </div>

      <!-- Chart + Activity -->
      <div class="grid grid-cols-1 lg:grid-cols-3 gap-4 md:gap-6">
        <!-- Usage Trend -->
        <div class="lg:col-span-2 glass-card p-4 md:p-6">
          <div class="flex items-center justify-between mb-6">
            <div>
              <h3 class="text-lg font-semibold text-white">用量趋势</h3>
              <p class="text-sm text-slate-400 mt-1">近7天 Token 消耗</p>
            </div>
            <div class="flex items-center gap-4">
              <div class="flex items-center gap-1.5">
                <span class="w-3 h-3 rounded-full bg-cyan-500/70"></span>
                <span class="text-xs text-slate-400">输入</span>
              </div>
              <div class="flex items-center gap-1.5">
                <span class="w-3 h-3 rounded-full bg-emerald-500/70"></span>
                <span class="text-xs text-slate-400">输出</span>
              </div>
              <router-link to="/usage" class="text-sm text-emerald-400 hover:text-emerald-300 flex items-center gap-1 ml-2">详情 <ArrowUpRight class="w-4 h-4" /></router-link>
            </div>
          </div>
          <div v-if="dailyData.every(d => d.total === 0)" class="flex items-center justify-center py-12">
            <p class="text-sm text-slate-500">暂无消耗数据</p>
          </div>
          <div v-else class="w-full overflow-x-auto">
            <svg :viewBox="`0 0 ${chartWidth} ${chartHeight}`" class="w-full" style="min-width: 400px;" preserveAspectRatio="xMidYMid meet">
              <!-- Grid lines -->
              <line
                v-for="tick in lineYTicks"
                :key="'grid-' + tick.val"
                :x1="chartPadding.left"
                :y1="tick.y"
                :x2="chartWidth - chartPadding.right"
                :y2="tick.y"
                stroke="rgba(148,163,184,0.1)"
                stroke-dasharray="4 4"
              />
              <!-- Y-axis labels -->
              <text
                v-for="tick in lineYTicks"
                :key="'ylabel-' + tick.val"
                :x="chartPadding.left - 8"
                :y="tick.y + 4"
                text-anchor="end"
                fill="#64748b"
                font-size="11"
              >{{ formatTokenShort(tick.val) }}</text>
              <!-- X-axis labels -->
              <text
                v-for="(day, i) in dailyData"
                :key="'xlabel-' + i"
                :x="lineX(i)"
                :y="chartHeight - 8"
                text-anchor="middle"
                fill="#64748b"
                font-size="11"
              >{{ day.label }}</text>
              <!-- Gradient defs -->
              <defs>
                <linearGradient id="dashInputGradient" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stop-color="#06b6d4" />
                  <stop offset="100%" stop-color="#06b6d4" stop-opacity="0" />
                </linearGradient>
                <linearGradient id="dashOutputGradient" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stop-color="#10b981" />
                  <stop offset="100%" stop-color="#10b981" stop-opacity="0" />
                </linearGradient>
              </defs>
              <!-- Area fill for input (smooth) -->
              <path
                :d="inputAreaPath"
                fill="url(#dashInputGradient)"
                opacity="0.15"
              />
              <!-- Area fill for output (smooth) -->
              <path
                :d="outputAreaPath"
                fill="url(#dashOutputGradient)"
                opacity="0.15"
              />
              <!-- Input line (smooth bezier) -->
              <path
                :d="inputSmoothPath"
                fill="none"
                stroke="#06b6d4"
                stroke-width="2.5"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
              <!-- Output line (smooth bezier) -->
              <path
                :d="outputSmoothPath"
                fill="none"
                stroke="#10b981"
                stroke-width="2.5"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
              <!-- Input data points -->
              <g v-for="(day, i) in dailyData" :key="'ip-' + i">
                <circle
                  :cx="lineX(i)"
                  :cy="lineY(day.input)"
                  r="4"
                  fill="#0e1629"
                  stroke="#06b6d4"
                  stroke-width="2"
                  class="cursor-pointer transition-all duration-200"
                  :class="{ 'r-[6px]': hoveredPoint?.dayIndex === i && hoveredPoint?.type === 'input' }"
                  @mouseenter="setHoveredPoint(i, 'input')"
                  @mouseleave="clearHoveredPoint()"
                />
                <!-- Tooltip for input -->
                <g v-if="hoveredPoint?.dayIndex === i && hoveredPoint?.type === 'input'" class="pointer-events-none">
                  <rect
                    :x="tooltipPosition(i, day.input, 'input').x"
                    :y="tooltipPosition(i, day.input, 'input').y"
                    width="96"
                    height="24"
                    rx="4"
                    fill="rgba(15,23,42,0.9)"
                    stroke="rgba(6,182,212,0.3)"
                    stroke-width="1"
                  />
                  <text
                    :x="tooltipPosition(i, day.input, 'input').textX"
                    :y="tooltipPosition(i, day.input, 'input').textY"
                    text-anchor="middle"
                    fill="#06b6d4"
                    font-size="11"
                  >输入: {{ formatTokenShort(day.input) }}</text>
                </g>
              </g>
              <!-- Output data points -->
              <g v-for="(day, i) in dailyData" :key="'op-' + i">
                <circle
                  :cx="lineX(i)"
                  :cy="lineY(day.output)"
                  r="4"
                  fill="#0e1629"
                  stroke="#10b981"
                  stroke-width="2"
                  class="cursor-pointer transition-all duration-200"
                  :class="{ 'r-[6px]': hoveredPoint?.dayIndex === i && hoveredPoint?.type === 'output' }"
                  @mouseenter="setHoveredPoint(i, 'output')"
                  @mouseleave="clearHoveredPoint()"
                />
                <!-- Tooltip for output -->
                <g v-if="hoveredPoint?.dayIndex === i && hoveredPoint?.type === 'output'" class="pointer-events-none">
                  <rect
                    :x="tooltipPosition(i, day.output, 'output').x"
                    :y="tooltipPosition(i, day.output, 'output').y"
                    width="96"
                    height="24"
                    rx="4"
                    fill="rgba(15,23,42,0.9)"
                    stroke="rgba(16,185,129,0.3)"
                    stroke-width="1"
                  />
                  <text
                    :x="tooltipPosition(i, day.output, 'output').textX"
                    :y="tooltipPosition(i, day.output, 'output').textY"
                    text-anchor="middle"
                    fill="#10b981"
                    font-size="11"
                  >输出: {{ formatTokenShort(day.output) }}</text>
                </g>
              </g>
            </svg>
          </div>
        </div>

        <!-- Recent Activity -->
        <div class="glass-card p-4 md:p-6">
          <h3 class="text-lg font-semibold text-white mb-4">最近动态</h3>
          <div v-if="recentActivity.length === 0" class="text-sm text-slate-500 py-8 text-center">暂无记录</div>
          <div v-else class="space-y-3">
            <div v-for="(activity, i) in recentActivity" :key="i" class="flex items-start gap-3 group">
              <div :class="[
                'w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0 transition-transform duration-300 group-hover:scale-110',
                activity.type === 'RECHARGE' ? 'bg-emerald-500/10' : 'bg-white/5'
              ]">
                <component
                  :is="activity.type === 'RECHARGE' ? Wallet : Zap"
                  :class="[
                    'w-4 h-4 transition-all duration-300',
                    activity.type === 'RECHARGE' ? 'text-emerald-400 group-hover:rotate-12' : 'text-slate-400 group-hover:text-cyan-400'
                  ]"
                />
              </div>
              <div class="flex-1 min-w-0">
                <div class="flex items-center justify-between">
                  <p class="text-sm text-white truncate">{{ activity.action }}</p>
                  <span :class="[
                    'text-sm font-medium flex-shrink-0 ml-2 transition-colors duration-200',
                    activity.type === 'RECHARGE' ? 'text-emerald-400' : (activity.amount >= 0 ? 'text-cyan-400' : 'text-red-400')
                  ]">
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
