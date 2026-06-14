<script setup>
import { ref, onMounted, computed } from 'vue'
import { useApiStore } from '@/stores/api'
import { BarChart3, TrendingUp, Zap, Wallet } from 'lucide-vue-next'

const api = useApiStore()
const loading = ref(false)
const usageRecords = ref([])
const hoveredPoint = ref(null)

onMounted(async () => {
  loading.value = true
  const res = await api.get('/billing/my-usage')
  if (res.code === 200) usageRecords.value = res.data || []
  loading.value = false
})

const stats = computed(() => {
  let totalInput = 0, totalOutput = 0, totalCost = 0
  for (const r of usageRecords.value) {
    totalInput += r.inputTokens || 0
    totalOutput += r.outputTokens || 0
    totalCost += Math.abs(Number(r.amount || 0))
  }
  return { totalInput, totalOutput, totalCost, count: usageRecords.value.length }
})

const statCards = computed(() => [
  { label: '总调用次数', value: stats.value.count.toLocaleString(), sub: '次', icon: Zap, color: 'cyan' },
  { label: '输入Token', value: stats.value.totalInput.toLocaleString(), sub: 'tokens', icon: BarChart3, color: 'cyan' },
  { label: '输出Token', value: stats.value.totalOutput.toLocaleString(), sub: 'tokens', icon: TrendingUp, color: 'amber' },
  { label: '总费用', value: '¥' + stats.value.totalCost.toFixed(2), sub: '元', icon: Wallet, color: 'emerald' }
])

const colorMap = {
  cyan: { bg: 'bg-cyan-500/10', text: 'text-cyan-400' },
  amber: { bg: 'bg-amber-500/10', text: 'text-amber-400' },
  emerald: { bg: 'bg-emerald-500/10', text: 'text-emerald-400' }
}

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

// Donut chart computed values
const donutRadius = 70
const donutStroke = 18
const donutCircumference = 2 * Math.PI * donutRadius

const donutData = computed(() => {
  const input = stats.value.totalInput
  const output = stats.value.totalOutput
  const total = input + output
  if (total === 0) return { inputRatio: 0, outputRatio: 0, total: 0, inputOffset: 0, outputOffset: 0 }
  const inputRatio = input / total
  const outputRatio = output / total
  const inputLength = inputRatio * donutCircumference
  const outputLength = outputRatio * donutCircumference
  return {
    inputRatio,
    outputRatio,
    total,
    inputLength,
    outputLength,
    inputOffset: 0,
    outputOffset: -inputLength
  }
})

function setHoveredPoint(dayIndex, type) {
  hoveredPoint.value = { dayIndex, type }
}

function clearHoveredPoint() {
  hoveredPoint.value = null
}
</script>

<template>
  <div class="space-y-6 animate-fade-in">
    <!-- Loading -->
    <div v-if="loading" class="flex items-center justify-center py-32">
      <div class="flex flex-col items-center gap-4">
        <div class="w-10 h-10 border-2 border-cyan-400/30 border-t-cyan-400 rounded-full animate-spin"></div>
        <p class="text-sm text-slate-400">加载中...</p>
      </div>
    </div>

    <template v-else>
      <div>
        <h1 class="text-2xl font-bold text-white">用量统计</h1>
        <p class="text-slate-400 mt-1">查看您的 Token 消耗详情与趋势</p>
      </div>

      <!-- Stats Cards -->
      <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        <div v-for="card in statCards" :key="card.label" class="glass-card p-6 glow-border">
          <div class="flex items-start justify-between">
            <div>
              <p class="text-sm text-slate-400">{{ card.label }}</p>
              <p class="text-2xl font-bold text-white mt-2">{{ card.value }}</p>
              <p v-if="card.sub" class="text-xs text-slate-500 mt-1">{{ card.sub }}</p>
            </div>
            <div :class="['w-10 h-10 rounded-lg flex items-center justify-center', colorMap[card.color].bg]">
              <component :is="card.icon" :class="['w-5 h-5', colorMap[card.color].text]" />
            </div>
          </div>
        </div>
      </div>

      <!-- Daily Trend Line Chart -->
      <div class="glass-card p-6">
        <div class="flex items-center justify-between mb-6">
          <div>
            <h3 class="text-lg font-semibold text-white">每日消耗趋势</h3>
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
            <!-- Area fill for input -->
            <polygon
              :points="`${chartPadding.left},${chartPadding.top + plotHeight} ${inputPoints} ${lineX(dailyData.length - 1)},${chartPadding.top + plotHeight}`"
              fill="url(#inputGradient)"
              opacity="0.15"
            />
            <!-- Area fill for output -->
            <polygon
              :points="`${chartPadding.left},${chartPadding.top + plotHeight} ${outputPoints} ${lineX(dailyData.length - 1)},${chartPadding.top + plotHeight}`"
              fill="url(#outputGradient)"
              opacity="0.15"
            />
            <!-- Gradient defs -->
            <defs>
              <linearGradient id="inputGradient" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stop-color="#06b6d4" />
                <stop offset="100%" stop-color="#06b6d4" stop-opacity="0" />
              </linearGradient>
              <linearGradient id="outputGradient" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stop-color="#10b981" />
                <stop offset="100%" stop-color="#10b981" stop-opacity="0" />
              </linearGradient>
            </defs>
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

      <!-- Donut Chart + Summary Row -->
      <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <!-- Donut Chart -->
        <div class="glass-card p-6 flex flex-col items-center justify-center">
          <h3 class="text-lg font-semibold text-white mb-6 self-start">Token 分布</h3>
          <div v-if="donutData.total === 0" class="flex items-center justify-center py-12">
            <p class="text-sm text-slate-500">暂无数据</p>
          </div>
          <template v-else>
            <div class="relative">
              <svg width="180" height="180" :viewBox="'0 0 180 180'">
                <!-- Background circle -->
                <circle
                  cx="90" cy="90"
                  :r="donutRadius"
                  fill="none"
                  stroke="rgba(148,163,184,0.08)"
                  :stroke-width="donutStroke"
                />
                <!-- Input arc -->
                <circle
                  cx="90" cy="90"
                  :r="donutRadius"
                  fill="none"
                  stroke="#06b6d4"
                  :stroke-width="donutStroke"
                  :stroke-dasharray="`${donutData.inputLength} ${donutCircumference - donutData.inputLength}`"
                  :stroke-dashoffset="donutData.inputOffset"
                  stroke-linecap="round"
                  transform="rotate(-90 90 90)"
                  class="transition-all duration-700"
                />
                <!-- Output arc -->
                <circle
                  cx="90" cy="90"
                  :r="donutRadius"
                  fill="none"
                  stroke="#10b981"
                  :stroke-width="donutStroke"
                  :stroke-dasharray="`${donutData.outputLength} ${donutCircumference - donutData.outputLength}`"
                  :stroke-dashoffset="donutData.outputOffset"
                  stroke-linecap="round"
                  transform="rotate(-90 90 90)"
                  class="transition-all duration-700"
                />
              </svg>
              <!-- Center text -->
              <div class="absolute inset-0 flex flex-col items-center justify-center">
                <span class="text-xl font-bold text-white">{{ formatTokenShort(donutData.total) }}</span>
                <span class="text-xs text-slate-400 mt-0.5">总 Token</span>
              </div>
            </div>
            <!-- Legend -->
            <div class="flex items-center gap-6 mt-6">
              <div class="flex items-center gap-2">
                <span class="w-3 h-3 rounded-full bg-cyan-500"></span>
                <span class="text-sm text-slate-300">输入</span>
                <span class="text-sm text-cyan-400 font-medium">{{ (donutData.inputRatio * 100).toFixed(1) }}%</span>
              </div>
              <div class="flex items-center gap-2">
                <span class="w-3 h-3 rounded-full bg-emerald-500"></span>
                <span class="text-sm text-slate-300">输出</span>
                <span class="text-sm text-emerald-400 font-medium">{{ (donutData.outputRatio * 100).toFixed(1) }}%</span>
              </div>
            </div>
          </template>
        </div>

        <!-- Quick Summary Cards -->
        <div class="lg:col-span-2 glass-card p-6">
          <h3 class="text-lg font-semibold text-white mb-6">消耗概览</h3>
          <div class="grid grid-cols-2 gap-4">
            <div class="rounded-xl bg-cyan-500/5 border border-cyan-500/10 p-4">
              <p class="text-sm text-slate-400">输入 Token</p>
              <p class="text-2xl font-bold text-cyan-400 mt-2">{{ stats.totalInput.toLocaleString() }}</p>
              <div class="mt-3 h-1.5 rounded-full bg-slate-700/50 overflow-hidden">
                <div
                  class="h-full rounded-full bg-gradient-to-r from-cyan-500 to-cyan-400 transition-all duration-700"
                  :style="{ width: donutData.total ? (stats.totalInput / donutData.total * 100) + '%' : '0%' }"
                ></div>
              </div>
            </div>
            <div class="rounded-xl bg-emerald-500/5 border border-emerald-500/10 p-4">
              <p class="text-sm text-slate-400">输出 Token</p>
              <p class="text-2xl font-bold text-emerald-400 mt-2">{{ stats.totalOutput.toLocaleString() }}</p>
              <div class="mt-3 h-1.5 rounded-full bg-slate-700/50 overflow-hidden">
                <div
                  class="h-full rounded-full bg-gradient-to-r from-emerald-500 to-emerald-400 transition-all duration-700"
                  :style="{ width: donutData.total ? (stats.totalOutput / donutData.total * 100) + '%' : '0%' }"
                ></div>
              </div>
            </div>
            <div class="rounded-xl bg-amber-500/5 border border-amber-500/10 p-4">
              <p class="text-sm text-slate-400">平均每次输入</p>
              <p class="text-2xl font-bold text-amber-400 mt-2">{{ stats.count ? Math.round(stats.totalInput / stats.count).toLocaleString() : '0' }}</p>
              <p class="text-xs text-slate-500 mt-1">tokens / 次</p>
            </div>
            <div class="rounded-xl bg-purple-500/5 border border-purple-500/10 p-4">
              <p class="text-sm text-slate-400">平均每次输出</p>
              <p class="text-2xl font-bold text-purple-400 mt-2">{{ stats.count ? Math.round(stats.totalOutput / stats.count).toLocaleString() : '0' }}</p>
              <p class="text-xs text-slate-500 mt-1">tokens / 次</p>
            </div>
          </div>
        </div>
      </div>

      <!-- Usage Detail Table -->
      <div class="glass-card p-6">
        <h3 class="text-lg font-semibold text-white mb-6">用量明细</h3>
        <div v-if="loading" class="flex items-center justify-center py-8">
          <div class="w-8 h-8 border-2 border-cyan-400/30 border-t-cyan-400 rounded-full animate-spin"></div>
        </div>
        <div v-else-if="usageRecords.length === 0" class="text-slate-500 text-center py-8">暂无数据</div>
        <div v-else class="overflow-x-auto">
          <table class="w-full">
            <thead>
              <tr class="border-b border-white/5">
                <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">时间</th>
                <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">输入Token</th>
                <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">输出Token</th>
                <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">费用</th>
                <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">详情</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-white/5">
              <tr v-for="record in usageRecords" :key="record.id" class="hover:bg-white/5 transition-colors">
                <td class="py-4 text-sm text-slate-500">{{ record.createdAt ? new Date(record.createdAt).toLocaleString() : '-' }}</td>
                <td class="py-4 text-sm text-cyan-400 font-medium">{{ record.inputTokens?.toLocaleString() || '0' }}</td>
                <td class="py-4 text-sm text-amber-400 font-medium">{{ record.outputTokens?.toLocaleString() || '0' }}</td>
                <td class="py-4 text-sm font-medium text-emerald-400">¥{{ Math.abs(Number(record.amount || 0)).toFixed(4) }}</td>
                <td class="py-4 text-sm text-slate-400 max-w-xs truncate" :title="record.detail">{{ record.detail || '-' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </template>
  </div>
</template>
