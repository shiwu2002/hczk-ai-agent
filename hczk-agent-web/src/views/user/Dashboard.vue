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
const hoveredPoint = ref(null)

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

const inputPoints = computed(() =>
  dailyData.value.map((d, i) => `${lineX(i)},${lineY(d.input)}`).join(' ')
)

const outputPoints = computed(() =>
  dailyData.value.map((d, i) => `${lineX(i)},${lineY(d.output)}`).join(' ')
)

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
            <svg :viewBox="`0 0 ${chartWidth} ${chartHeight}`" class="w-full" style="min-width: 500px;" preserveAspectRatio="xMidYMid meet">
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
              <!-- Area fill for input -->
              <polygon
                :points="`${chartPadding.left},${chartPadding.top + plotHeight} ${inputPoints} ${lineX(dailyData.length - 1)},${chartPadding.top + plotHeight}`"
                fill="url(#dashInputGradient)"
                opacity="0.15"
              />
              <!-- Area fill for output -->
              <polygon
                :points="`${chartPadding.left},${chartPadding.top + plotHeight} ${outputPoints} ${lineX(dailyData.length - 1)},${chartPadding.top + plotHeight}`"
                fill="url(#dashOutputGradient)"
                opacity="0.15"
              />
              <!-- Input line -->
              <polyline
                :points="inputPoints"
                fill="none"
                stroke="#06b6d4"
                stroke-width="2.5"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
              <!-- Output line -->
              <polyline
                :points="outputPoints"
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
                    :x="lineX(i) - 48"
                    :y="lineY(day.input) - 32"
                    width="96"
                    height="24"
                    rx="4"
                    fill="rgba(15,23,42,0.9)"
                    stroke="rgba(6,182,212,0.3)"
                    stroke-width="1"
                  />
                  <text
                    :x="lineX(i)"
                    :y="lineY(day.input) - 16"
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
                    :x="lineX(i) - 48"
                    :y="lineY(day.output) - 32"
                    width="96"
                    height="24"
                    rx="4"
                    fill="rgba(15,23,42,0.9)"
                    stroke="rgba(16,185,129,0.3)"
                    stroke-width="1"
                  />
                  <text
                    :x="lineX(i)"
                    :y="lineY(day.output) - 16"
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
