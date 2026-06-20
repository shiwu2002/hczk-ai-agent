<script setup>
import { ref, onMounted, computed } from 'vue'
import { useApiStore } from '@/stores/api'
import { BarChart3, TrendingUp, Zap, Wallet, CalendarDays, Activity, Coins, Hash, ArrowUpRight, ArrowDownRight, Minus, Cpu, Gauge, PieChart } from 'lucide-vue-next'

const api = useApiStore()
const loading = ref(false)
const usageRecords = ref([])

onMounted(async () => {
  loading.value = true
  const res = await api.get('/billing/my-usage')
  if (res.code === 200) usageRecords.value = res.data || []
  loading.value = false
})

// ====== 基础统计 ======
const stats = computed(() => {
  let totalInput = 0, totalOutput = 0, totalCost = 0
  for (const r of usageRecords.value) {
    totalInput += r.inputTokens || 0
    totalOutput += r.outputTokens || 0
    totalCost += Math.abs(Number(r.amount || 0))
  }
  return { totalInput, totalOutput, totalCost, count: usageRecords.value.length }
})

// ====== 每日数据 ======
const dailyData = computed(() => {
  const days = []
  const now = new Date()
  for (let i = 6; i >= 0; i--) {
    const d = new Date(now)
    d.setDate(d.getDate() - i)
    const dateStr = d.toDateString()
    const label = `${d.getMonth() + 1}/${d.getDate()}`
    const isToday = i === 0
    let input = 0, output = 0, cost = 0, calls = 0
    for (const r of usageRecords.value) {
      if (r.createdAt && new Date(r.createdAt).toDateString() === dateStr) {
        input += r.inputTokens || 0
        output += r.outputTokens || 0
        cost += Math.abs(Number(r.amount || 0))
        calls++
      }
    }
    days.push({ label, input, output, cost, calls, total: input + output, isToday })
  }
  return days
})

// ====== 日环比变化 ======
const dayOverDay = computed(() => {
  const today = dailyData.value.find(d => d.isToday)
  const yesterday = dailyData.value.filter(d => !d.isToday).slice(-1)[0]
  if (!today || !yesterday) return { calls: 0, tokens: 0, cost: 0 }
  const calc = (curr, prev) => {
    if (prev === 0) return curr > 0 ? 100 : 0
    return Math.round(((curr - prev) / prev) * 100)
  }
  return {
    calls: calc(today.calls, yesterday.calls),
    tokens: calc(today.total, yesterday.total),
    cost: calc(today.cost, yesterday.cost)
  }
})

function trendIcon(val) {
  if (val > 0) return ArrowUpRight
  if (val < 0) return ArrowDownRight
  return Minus
}

function trendColor(val) {
  if (val > 0) return 'text-emerald-400'
  if (val < 0) return 'text-red-400'
  return 'text-slate-400'
}

// ====== Token 效率分析 ======
const efficiency = computed(() => {
  const avgInput = stats.value.count ? Math.round(stats.value.totalInput / stats.value.count) : 0
  const avgOutput = stats.value.count ? Math.round(stats.value.totalOutput / stats.value.count) : 0
  const outputRatio = (stats.value.totalInput + stats.value.totalOutput) > 0
    ? (stats.value.totalOutput / (stats.value.totalInput + stats.value.totalOutput) * 100).toFixed(1)
    : 0
  const costPerCall = stats.value.count ? (stats.value.totalCost / stats.value.count).toFixed(4) : 0
  const costPer1kTokens = (stats.value.totalInput + stats.value.totalOutput) > 0
    ? (stats.value.totalCost / (stats.value.totalInput + stats.value.totalOutput) * 1000).toFixed(4)
    : 0
  return { avgInput, avgOutput, outputRatio, costPerCall, costPer1kTokens }
})

// ====== 每日费用趋势（柱状图） ======
const barChartWidth = 600
const barChartHeight = 180
const barPadding = { top: 16, right: 16, bottom: 28, left: 44 }
const barPlotW = barChartWidth - barPadding.left - barPadding.right
const barPlotH = barChartHeight - barPadding.top - barPadding.bottom

const barMaxCost = computed(() => {
  const max = Math.max(...dailyData.value.map(d => d.cost))
  return max || 1
})

function barX(index) {
  const barW = barPlotW / dailyData.value.length
  return barPadding.left + index * barW + barW * 0.15
}

function barW() {
  return (barPlotW / dailyData.value.length) * 0.7
}

function barH(cost) {
  return (cost / barMaxCost.value) * barPlotH
}

function barY(cost) {
  return barPadding.top + barPlotH - barH(cost)
}

// ====== 每日汇总 ======
const dailySummary = computed(() => {
  return dailyData.value.slice().reverse()
})

function formatTokenShort(val) {
  if (val >= 1000000) return (val / 1000000).toFixed(1) + 'M'
  if (val >= 1000) return (val / 1000).toFixed(1) + 'K'
  return String(val)
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
      <!-- Row 1: 日环比 + 效率指标 -->
      <div class="grid grid-cols-1 md:grid-cols-2 gap-5">
        <!-- 日环比变化 -->
        <div class="glass-card rounded-2xl p-5">
          <div class="flex items-center gap-2 mb-4">
            <TrendingUp class="w-4 h-4 text-cyan-400" />
            <h3 class="text-sm font-semibold text-white">日环比变化</h3>
            <span class="text-[10px] text-slate-500">今日 vs 昨日</span>
          </div>
          <div class="space-y-3">
            <div class="flex items-center justify-between p-3 rounded-xl bg-white/[0.03] border border-white/[0.05]">
              <div class="flex items-center gap-2.5">
                <div class="w-8 h-8 rounded-lg bg-cyan-500/10 flex items-center justify-center"><Zap class="w-4 h-4 text-cyan-400" /></div>
                <span class="text-sm text-slate-300">调用次数</span>
              </div>
              <div class="flex items-center gap-1.5">
                <component :is="trendIcon(dayOverDay.calls)" :class="['w-4 h-4', trendColor(dayOverDay.calls)]" />
                <span :class="['text-sm font-semibold', trendColor(dayOverDay.calls)]">{{ Math.abs(dayOverDay.calls) }}%</span>
              </div>
            </div>
            <div class="flex items-center justify-between p-3 rounded-xl bg-white/[0.03] border border-white/[0.05]">
              <div class="flex items-center gap-2.5">
                <div class="w-8 h-8 rounded-lg bg-amber-500/10 flex items-center justify-center"><Hash class="w-4 h-4 text-amber-400" /></div>
                <span class="text-sm text-slate-300">Token 消耗</span>
              </div>
              <div class="flex items-center gap-1.5">
                <component :is="trendIcon(dayOverDay.tokens)" :class="['w-4 h-4', trendColor(dayOverDay.tokens)]" />
                <span :class="['text-sm font-semibold', trendColor(dayOverDay.tokens)]">{{ Math.abs(dayOverDay.tokens) }}%</span>
              </div>
            </div>
            <div class="flex items-center justify-between p-3 rounded-xl bg-white/[0.03] border border-white/[0.05]">
              <div class="flex items-center gap-2.5">
                <div class="w-8 h-8 rounded-lg bg-emerald-500/10 flex items-center justify-center"><Wallet class="w-4 h-4 text-emerald-400" /></div>
                <span class="text-sm text-slate-300">费用支出</span>
              </div>
              <div class="flex items-center gap-1.5">
                <component :is="trendIcon(dayOverDay.cost)" :class="['w-4 h-4', trendColor(dayOverDay.cost)]" />
                <span :class="['text-sm font-semibold', trendColor(dayOverDay.cost)]">{{ Math.abs(dayOverDay.cost) }}%</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 效率指标 -->
        <div class="glass-card rounded-2xl p-5">
          <div class="flex items-center gap-2 mb-4">
            <Gauge class="w-4 h-4 text-amber-400" />
            <h3 class="text-sm font-semibold text-white">效率指标</h3>
          </div>
          <div class="space-y-4">
            <!-- 平均每次输入 -->
            <div>
              <div class="flex items-center justify-between mb-1">
                <span class="text-xs text-slate-400">平均每次输入</span>
                <span class="text-xs text-cyan-400 font-medium">{{ efficiency.avgInput.toLocaleString() }} tokens</span>
              </div>
              <div class="h-2 bg-white/5 rounded-full overflow-hidden">
                <div class="h-full rounded-full bg-gradient-to-r from-cyan-600 to-cyan-400 transition-all duration-700" :style="{ width: Math.min(100, efficiency.avgInput / 500 * 100) + '%' }"></div>
              </div>
            </div>
            <!-- 平均每次输出 -->
            <div>
              <div class="flex items-center justify-between mb-1">
                <span class="text-xs text-slate-400">平均每次输出</span>
                <span class="text-xs text-emerald-400 font-medium">{{ efficiency.avgOutput.toLocaleString() }} tokens</span>
              </div>
              <div class="h-2 bg-white/5 rounded-full overflow-hidden">
                <div class="h-full rounded-full bg-gradient-to-r from-emerald-600 to-emerald-400 transition-all duration-700" :style="{ width: Math.min(100, efficiency.avgOutput / 500 * 100) + '%' }"></div>
              </div>
            </div>
            <!-- 输出占比 -->
            <div>
              <div class="flex items-center justify-between mb-1">
                <span class="text-xs text-slate-400">输出 Token 占比</span>
                <span class="text-xs text-amber-400 font-medium">{{ efficiency.outputRatio }}%</span>
              </div>
              <div class="h-2 bg-white/5 rounded-full overflow-hidden">
                <div class="h-full rounded-full bg-gradient-to-r from-amber-600 to-amber-400 transition-all duration-700" :style="{ width: efficiency.outputRatio + '%' }"></div>
              </div>
            </div>
            <!-- 单次调用成本 -->
            <div class="pt-3 border-t border-white/5 grid grid-cols-2 gap-3">
              <div class="text-center p-2.5 rounded-lg bg-white/[0.03]">
                <p class="text-[10px] text-slate-500 mb-1">单次调用成本</p>
                <p class="text-base font-bold text-white">¥{{ efficiency.costPerCall }}</p>
              </div>
              <div class="text-center p-2.5 rounded-lg bg-white/[0.03]">
                <p class="text-[10px] text-slate-500 mb-1">每千Token成本</p>
                <p class="text-base font-bold text-white">¥{{ efficiency.costPer1kTokens }}</p>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Row 2: 费用趋势柱状图 + Token 分布 -->
      <div class="grid grid-cols-1 lg:grid-cols-4 gap-5">
        <!-- 费用趋势 -->
        <div class="lg:col-span-3 glass-card rounded-2xl overflow-hidden">
          <div class="h-1 bg-gradient-to-r from-emerald-500 via-amber-500 to-cyan-500"></div>
          <div class="p-5 md:p-6">
            <div class="flex items-center justify-between mb-4">
              <div class="flex items-center gap-2">
                <Wallet class="w-4 h-4 text-emerald-400" />
                <h3 class="text-sm font-semibold text-white">每日费用趋势</h3>
                <span class="text-[11px] text-slate-500">近7天</span>
              </div>
              <div class="text-right">
                <p class="text-lg font-bold text-emerald-400">¥{{ stats.totalCost.toFixed(2) }}</p>
                <p class="text-[10px] text-slate-500">7天总费用</p>
              </div>
            </div>
            <div v-if="dailyData.every(d => d.cost === 0)" class="flex items-center justify-center py-12">
              <div class="text-center">
                <Wallet class="w-10 h-10 text-slate-600 mx-auto mb-2" />
                <p class="text-sm text-slate-500">暂无费用数据</p>
              </div>
            </div>
            <div v-else class="w-full overflow-x-auto">
              <svg :viewBox="`0 0 ${barChartWidth} ${barChartHeight}`" class="w-full" style="min-width: 400px;" preserveAspectRatio="xMidYMid meet">
                <defs>
                  <linearGradient id="barGrad" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stop-color="#10b981" stop-opacity="0.8" />
                    <stop offset="100%" stop-color="#10b981" stop-opacity="0.2" />
                  </linearGradient>
                </defs>
                <!-- Grid lines -->
                <line v-for="i in 4" :key="'bg-' + i" :x1="barPadding.left" :y1="barPadding.top + (i - 1) * barPlotH / 3" :x2="barChartWidth - barPadding.right" :y2="barPadding.top + (i - 1) * barPlotH / 3" stroke="rgba(148,163,184,0.06)" />
                <!-- Y labels -->
                <text v-for="i in 4" :key="'by-' + i" :x="barPadding.left - 6" :y="barPadding.top + (i - 1) * barPlotH / 3 + 4" text-anchor="end" fill="#475569" font-size="9">¥{{ ((barMaxCost - (i - 1) * barMaxCost / 3)).toFixed(1) }}</text>
                <!-- Bars -->
                <g v-for="(day, i) in dailyData" :key="'bar-' + i">
                  <rect
                    :x="barX(i)"
                    :y="barY(day.cost)"
                    :width="barW()"
                    :height="Math.max(0, barH(day.cost))"
                    :fill="day.isToday ? 'url(#barGrad)' : 'rgba(16,185,129,0.25)'"
                    :rx="day.cost > 0 ? 4 : 0"
                    class="transition-all duration-300"
                  />
                  <!-- Cost label on bar -->
                  <text
                    v-if="day.cost > 0"
                    :x="barX(i) + barW() / 2"
                    :y="barY(day.cost) - 4"
                    text-anchor="middle"
                    :fill="day.isToday ? '#10b981' : '#64748b'"
                    font-size="9"
                    font-weight="500"
                  >¥{{ day.cost.toFixed(2) }}</text>
                  <!-- X label -->
                  <text
                    :x="barX(i) + barW() / 2"
                    :y="barChartHeight - 6"
                    text-anchor="middle"
                    :fill="day.isToday ? '#10b981' : '#475569'"
                    :font-weight="day.isToday ? '600' : '400'"
                    font-size="10"
                  >{{ day.isToday ? '今日' : day.label }}</text>
                </g>
              </svg>
            </div>
          </div>
        </div>

        <!-- Token 分布 -->
        <div class="glass-card rounded-2xl p-5 flex flex-col">
          <div class="flex items-center gap-2 mb-4">
            <PieChart class="w-4 h-4 text-cyan-400" />
            <h3 class="text-sm font-semibold text-white">Token 分布</h3>
          </div>
          <div class="flex-1 flex flex-col items-center justify-center">
            <div v-if="(stats.totalInput + stats.totalOutput) === 0" class="py-8">
              <p class="text-xs text-slate-500">暂无数据</p>
            </div>
            <template v-else>
              <div class="relative mb-4">
                <svg width="140" height="140" viewBox="0 0 140 140">
                  <circle cx="70" cy="70" r="50" fill="none" stroke="rgba(148,163,184,0.06)" stroke-width="12" />
                  <circle cx="70" cy="70" r="50" fill="none" stroke="#06b6d4" stroke-width="12"
                    :stroke-dasharray="(stats.totalInput / (stats.totalInput + stats.totalOutput)) * 314.16 + ' ' + 314.16"
                    stroke-linecap="round" transform="rotate(-90 70 70)"
                    style="filter: drop-shadow(0 0 3px rgba(6,182,212,0.3))"
                  />
                  <circle cx="70" cy="70" r="50" fill="none" stroke="#10b981" stroke-width="12"
                    :stroke-dasharray="(stats.totalOutput / (stats.totalInput + stats.totalOutput)) * 314.16 + ' ' + 314.16"
                    :stroke-dashoffset="-(stats.totalInput / (stats.totalInput + stats.totalOutput)) * 314.16"
                    stroke-linecap="round" transform="rotate(-90 70 70)"
                    style="filter: drop-shadow(0 0 3px rgba(16,185,129,0.3))"
                  />
                </svg>
                <div class="absolute inset-0 flex flex-col items-center justify-center">
                  <span class="text-base font-bold text-white">{{ formatTokenShort(stats.totalInput + stats.totalOutput) }}</span>
                  <span class="text-[10px] text-slate-400">总 Token</span>
                </div>
              </div>
              <div class="w-full space-y-2">
                <div class="flex items-center justify-between">
                  <div class="flex items-center gap-2">
                    <span class="w-3 h-3 rounded-full bg-cyan-500"></span>
                    <span class="text-xs text-slate-400">输入</span>
                  </div>
                  <div class="text-right">
                    <span class="text-xs text-cyan-400 font-medium">{{ formatTokenShort(stats.totalInput) }}</span>
                    <span class="text-[10px] text-slate-500 ml-1">{{ (stats.totalInput / (stats.totalInput + stats.totalOutput) * 100).toFixed(0) }}%</span>
                  </div>
                </div>
                <div class="flex items-center justify-between">
                  <div class="flex items-center gap-2">
                    <span class="w-3 h-3 rounded-full bg-emerald-500"></span>
                    <span class="text-xs text-slate-400">输出</span>
                  </div>
                  <div class="text-right">
                    <span class="text-xs text-emerald-400 font-medium">{{ formatTokenShort(stats.totalOutput) }}</span>
                    <span class="text-[10px] text-slate-500 ml-1">{{ (stats.totalOutput / (stats.totalInput + stats.totalOutput) * 100).toFixed(0) }}%</span>
                  </div>
                </div>
              </div>
            </template>
          </div>
        </div>
      </div>

      <!-- Row 3: 每日汇总 -->
      <div class="glass-card rounded-2xl p-5 md:p-6">
        <div class="flex items-center justify-between mb-4">
          <div class="flex items-center gap-2">
            <CalendarDays class="w-4 h-4 text-cyan-400" />
            <h3 class="text-sm font-semibold text-white">每日汇总</h3>
          </div>
          <span class="text-[11px] text-slate-500">近7天</span>
        </div>
        <div v-if="dailySummary.length === 0" class="text-center py-8">
          <Activity class="w-8 h-8 text-slate-600 mx-auto mb-2" />
          <p class="text-xs text-slate-500">暂无消耗数据</p>
        </div>
        <div v-else>
          <!-- Desktop table -->
          <div class="hidden md:block overflow-x-auto">
            <table class="w-full">
              <thead>
                <tr class="border-b border-white/[0.06]">
                  <th class="text-left text-[11px] font-semibold text-slate-500 uppercase tracking-wider pb-3 pl-2">日期</th>
                  <th class="text-left text-[11px] font-semibold text-slate-500 uppercase tracking-wider pb-3">调用</th>
                  <th class="text-left text-[11px] font-semibold text-slate-500 uppercase tracking-wider pb-3">输入 Token</th>
                  <th class="text-left text-[11px] font-semibold text-slate-500 uppercase tracking-wider pb-3">输出 Token</th>
                  <th class="text-left text-[11px] font-semibold text-slate-500 uppercase tracking-wider pb-3">总 Token</th>
                  <th class="text-right text-[11px] font-semibold text-slate-500 uppercase tracking-wider pb-3 pr-2">费用</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-white/[0.03]">
                <tr
                  v-for="day in dailySummary"
                  :key="day.label"
                  class="hover:bg-white/[0.03] transition-colors"
                  :class="day.isToday ? 'bg-cyan-500/[0.04]' : ''"
                >
                  <td class="py-3 pl-2 text-sm whitespace-nowrap">
                    <span :class="day.isToday ? 'text-cyan-400 font-medium' : 'text-slate-400'">{{ day.isToday ? '今日' : day.label }}</span>
                  </td>
                  <td class="py-3 text-sm text-white font-medium">{{ day.calls }}</td>
                  <td class="py-3 text-sm text-cyan-400">{{ formatTokenShort(day.input) }}</td>
                  <td class="py-3 text-sm text-emerald-400">{{ formatTokenShort(day.output) }}</td>
                  <td class="py-3 text-sm text-slate-300 font-medium">{{ formatTokenShort(day.total) }}</td>
                  <td class="py-3 pr-2 text-sm text-emerald-400 font-medium text-right">¥{{ day.cost.toFixed(2) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <!-- Mobile cards -->
          <div class="block md:hidden space-y-2.5">
            <div
              v-for="day in dailySummary"
              :key="day.label"
              :class="[
                'rounded-xl p-3.5 border',
                day.isToday ? 'bg-cyan-500/[0.06] border-cyan-500/15' : 'bg-white/[0.02] border-white/5'
              ]"
            >
              <div class="flex items-center justify-between mb-2">
                <span :class="['text-xs font-medium', day.isToday ? 'text-cyan-400' : 'text-slate-400']">
                  {{ day.isToday ? '今日' : day.label }}
                </span>
                <span class="text-sm text-emerald-400 font-medium">¥{{ day.cost.toFixed(2) }}</span>
              </div>
              <div class="grid grid-cols-3 gap-2 text-center">
                <div>
                  <p class="text-[10px] text-slate-500">调用</p>
                  <p class="text-sm text-white font-medium">{{ day.calls }}</p>
                </div>
                <div>
                  <p class="text-[10px] text-slate-500">输入</p>
                  <p class="text-sm text-cyan-400 font-medium">{{ formatTokenShort(day.input) }}</p>
                </div>
                <div>
                  <p class="text-[10px] text-slate-500">输出</p>
                  <p class="text-sm text-emerald-400 font-medium">{{ formatTokenShort(day.output) }}</p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>
