<script setup>
import { ref, onMounted, computed } from 'vue'
import { useApiStore } from '@/stores/api'
import { Users, KeyRound, Coins, Wallet, Activity, Cpu, BarChart3, TrendingUp, X, ChevronLeft, ChevronRight } from 'lucide-vue-next'

const api = useApiStore()
const loading = ref(false)
const users = ref([])
const apiKeys = ref([])
const billingRecords = ref([])
const models = ref([])
const chartHover = ref(-1)
const showDayDetail = ref(false)
const dayDetailData = ref(null)
const showUserDetail = ref(false)
const userDetailData = ref(null)

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

// Daily token data for chart (last 30 days)
const dailyData = computed(() => {
  const days = []
  const now = new Date()
  for (let i = 29; i >= 0; i--) {
    const d = new Date(now)
    d.setDate(d.getDate() - i)
    const dateStr = d.toDateString()
    const label = `${d.getMonth() + 1}/${d.getDate()}`
    const fullDate = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
    let input = 0, output = 0, cost = 0
    for (const r of billingRecords.value) {
      if (r.type === 'TOKEN_USAGE' && r.createdAt && new Date(r.createdAt).toDateString() === dateStr) {
        input += r.inputTokens || 0
        output += r.outputTokens || 0
        cost += Math.abs(Number(r.amount || 0))
      }
    }
    days.push({ label, fullDate, dateStr, input, output, total: input + output, cost })
  }
  return days
})

const maxDaily = computed(() => {
  const m = Math.max(...dailyData.value.map(d => d.total))
  return m || 1
})

// Smooth bezier curve path generator
function smoothPath(points, close = false) {
  if (points.length < 2) return ''
  let path = `M ${points[0].x} ${points[0].y}`
  for (let i = 0; i < points.length - 1; i++) {
    const p0 = points[Math.max(i - 1, 0)]
    const p1 = points[i]
    const p2 = points[i + 1]
    const p3 = points[Math.min(i + 2, points.length - 1)]
    const cp1x = p1.x + (p2.x - p0.x) / 6
    const cp1y = p1.y + (p2.y - p0.y) / 6
    const cp2x = p2.x - (p3.x - p1.x) / 6
    const cp2y = p2.y - (p3.y - p1.y) / 6
    path += ` C ${cp1x} ${cp1y}, ${cp2x} ${cp2y}, ${p2.x} ${p2.y}`
  }
  if (close) {
    const last = points[points.length - 1]
    const first = points[0]
    path += ` L ${last.x} ${chartPadT + plotH} L ${first.x} ${chartPadT + plotH} Z`
  }
  return path
}

// Chart layout
const chartW = 800
const chartH = 200
const chartPadL = 45
const chartPadR = 15
const chartPadT = 10
const chartPadB = 25
const plotW = chartW - chartPadL - chartPadR
const plotH = chartH - chartPadT - chartPadB

const chartMaxY = computed(() => {
  const m = Math.max(...dailyData.value.map(d => Math.max(d.input, d.output)))
  return m || 1
})

const chartGridLines = computed(() => {
  const steps = 4
  const lines = []
  for (let i = 0; i <= steps; i++) {
    const val = (chartMaxY.value / steps) * i
    const y = chartPadT + plotH - (plotH * i / steps)
    lines.push({ y, label: formatTokens(Math.round(val)) })
  }
  return lines
})

function makePoints(field) {
  const data = dailyData.value
  const max = chartMaxY.value
  return data.map((d, i) => ({
    x: chartPadL + (plotW / (data.length - 1)) * i,
    y: chartPadT + plotH - (d[field] / max * plotH),
    value: d[field],
    label: d.label
  }))
}

const inputPts = computed(() => makePoints('input'))
const outputPts = computed(() => makePoints('output'))
const inputLine = computed(() => smoothPath(inputPts.value))
const outputLine = computed(() => smoothPath(outputPts.value))
const inputArea = computed(() => smoothPath(inputPts.value, true))
const outputArea = computed(() => smoothPath(outputPts.value, true))

// Cost chart
const maxCost = computed(() => {
  const m = Math.max(...dailyData.value.map(d => d.cost))
  return m || 0.01
})

const costGridLines = computed(() => {
  const steps = 4
  const lines = []
  for (let i = 0; i <= steps; i++) {
    const val = (maxCost.value / steps) * i
    const y = chartPadT + plotH - (plotH * i / steps)
    lines.push({ y, label: '¥' + val.toFixed(val >= 1 ? 1 : 4) })
  }
  return lines
})

const costPts = computed(() => {
  const data = dailyData.value
  const max = maxCost.value
  return data.map((d, i) => ({
    x: chartPadL + (plotW / (data.length - 1)) * i,
    y: chartPadT + plotH - (d.cost / max * plotH),
    value: d.cost,
    label: d.label
  }))
})
const costLine = computed(() => smoothPath(costPts.value))
const costArea = computed(() => smoothPath(costPts.value, true))

// Recent users
const recentUsers = computed(() => {
  return [...users.value]
    .sort((a, b) => new Date(b.createdAt || 0) - new Date(a.createdAt || 0))
    .slice(0, 5)
})

// Model usage breakdown
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

function formatCost(cost) {
  return '¥' + Number(cost || 0).toFixed(4)
}

function formatTime(dateStr) {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString('zh-CN')
}

// Chart interaction
function onChartHover(i) {
  chartHover.value = i
}
function onChartLeave() {
  chartHover.value = -1
}
function onChartClick(i) {
  openDayDetail(i)
}

// Open day detail
function openDayDetail(index) {
  const day = dailyData.value[index]
  if (!day) return

  const dayRecords = billingRecords.value.filter(r =>
    r.type === 'TOKEN_USAGE' && r.createdAt && new Date(r.createdAt).toDateString() === day.dateStr
  )

  const modelMap = {}
  for (const r of dayRecords) {
    const match = (r.detail || '').match(/模型\[(.+?)\]/)
    const model = match ? match[1] : '未知模型'
    if (!modelMap[model]) modelMap[model] = { model, input: 0, output: 0, cost: 0, calls: 0 }
    modelMap[model].input += r.inputTokens || 0
    modelMap[model].output += r.outputTokens || 0
    modelMap[model].cost += Math.abs(Number(r.amount || 0))
    modelMap[model].calls++
  }

  dayDetailData.value = {
    date: day.fullDate,
    label: day.label,
    input: day.input,
    output: day.output,
    total: day.total,
    cost: day.cost,
    records: dayRecords,
    models: Object.values(modelMap).sort((a, b) => b.calls - a.calls)
  }
  showDayDetail.value = true
}

// Open user detail with charts
function openUserDetail(user) {
  const userRecords = billingRecords.value.filter(r => r.userId === user.id && r.type === 'TOKEN_USAGE')

  // Daily data for this user (last 30 days)
  const now = new Date()
  const days = []
  for (let i = 29; i >= 0; i--) {
    const d = new Date(now)
    d.setDate(d.getDate() - i)
    const dateStr = d.toDateString()
    const label = `${d.getMonth() + 1}/${d.getDate()}`
    let input = 0, output = 0, cost = 0
    for (const r of userRecords) {
      if (r.createdAt && new Date(r.createdAt).toDateString() === dateStr) {
        input += r.inputTokens || 0
        output += r.outputTokens || 0
        cost += Math.abs(Number(r.amount || 0))
      }
    }
    days.push({ label, input, output, total: input + output, cost })
  }

  // Model breakdown for this user
  const modelMap = {}
  for (const r of userRecords) {
    const match = (r.detail || '').match(/模型\[(.+?)\]/)
    const model = match ? match[1] : '未知模型'
    if (!modelMap[model]) modelMap[model] = { model, tokens: 0, cost: 0, calls: 0 }
    modelMap[model].tokens += (r.inputTokens || 0) + (r.outputTokens || 0)
    modelMap[model].cost += Math.abs(Number(r.amount || 0))
    modelMap[model].calls++
  }

  const totalInput = userRecords.reduce((s, r) => s + (r.inputTokens || 0), 0)
  const totalOutput = userRecords.reduce((s, r) => s + (r.outputTokens || 0), 0)
  const totalCost = userRecords.reduce((s, r) => s + Math.abs(Number(r.amount || 0)), 0)

  // Mini chart points
  const cW = 300, cH = 80, cPL = 30, cPR = 5, cPT = 5, cPB = 15
  const cPW = cW - cPL - cPR, cPH = cH - cPT - cPB
  const maxT = Math.max(...days.map(d => d.total)) || 1
  const maxC = Math.max(...days.map(d => d.cost)) || 0.01

  function miniPts(field, max) {
    return days.map((d, i) => ({
      x: cPL + (cPW / (days.length - 1)) * i,
      y: cPT + cPH - (d[field] / max * cPH)
    }))
  }

  const tokenPts = miniPts('total', maxT)
  const costPtsUser = miniPts('cost', maxC)

  function miniPath(pts) {
    if (pts.length < 2) return ''
    let p = `M ${pts[0].x} ${pts[0].y}`
    for (let i = 0; i < pts.length - 1; i++) {
      const p0 = pts[Math.max(i - 1, 0)], p1 = pts[i], p2 = pts[i + 1], p3 = pts[Math.min(i + 2, pts.length - 1)]
      p += ` C ${p1.x + (p2.x - p0.x) / 6} ${p1.y + (p2.y - p0.y) / 6}, ${p2.x - (p3.x - p1.x) / 6} ${p2.y - (p3.y - p1.y) / 6}, ${p2.x} ${p2.y}`
    }
    return p
  }

  function miniArea(pts) {
    const line = miniPath(pts)
    return line + ` L ${pts[pts.length - 1].x} ${cPT + cPH} L ${pts[0].x} ${cPT + cPH} Z`
  }

  userDetailData.value = {
    user,
    totalInput,
    totalOutput,
    totalCost,
    totalCalls: userRecords.length,
    models: Object.values(modelMap).sort((a, b) => b.calls - a.calls),
    days,
    miniChart: { cW, cH, cPL, cPT, cPH, cPW, cPB, tokenPts, costPtsUser, miniPath, miniArea, maxT, maxC }
  }
  showUserDetail.value = true
}

const userMap = computed(() => {
  const map = {}
  for (const u of users.value) {
    map[u.id] = u.username || u.email || ('用户' + u.id)
  }
  return map
})
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
          <div class="mt-4"><span class="text-sm text-slate-500">{{ stat.sub }}</span></div>
        </div>
      </div>

      <!-- Charts Row -->
      <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        <!-- 输入Token趋势 -->
        <div class="glass-card p-5">
          <div class="flex items-center justify-between mb-3">
            <div class="flex items-center gap-2">
              <span class="w-2.5 h-2.5 rounded-full bg-cyan-400"></span>
              <h3 class="text-sm font-semibold text-white">输入 Token</h3>
            </div>
            <span class="text-xs text-slate-500">近30天</span>
          </div>
          <div class="relative overflow-hidden rounded-lg">
            <svg :viewBox="`0 0 ${chartW} ${chartH}`" class="w-full" preserveAspectRatio="xMidYMid meet" style="min-height: 120px; max-height: 140px">
              <defs>
                <linearGradient id="aInGrad2" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stop-color="rgb(6,182,212)" stop-opacity="0.35" />
                  <stop offset="100%" stop-color="rgb(6,182,212)" stop-opacity="0.01" />
                </linearGradient>
              </defs>
              <line v-for="line in chartGridLines" :key="line.y"
                :x1="chartPadL" :y1="line.y" :x2="chartW - chartPadR" :y2="line.y"
                stroke="rgba(255,255,255,0.04)" stroke-width="1" />
              <text v-for="line in chartGridLines" :key="'gl'+line.y"
                :x="chartPadL - 6" :y="line.y + 3" text-anchor="end"
                fill="rgb(100,116,139)" font-size="8">{{ line.label }}</text>
              <text v-for="(d, i) in dailyData" :key="'xl'+i"
                v-show="i % 10 === 0 || i === dailyData.length - 1"
                :x="inputPts[i]?.x" :y="chartPadT + plotH + 12" text-anchor="middle"
                fill="rgb(100,116,139)" font-size="7"
              >{{ d.label }}</text>
              <rect v-for="(pt, i) in inputPts" :key="'hz'+i"
                :x="pt.x - plotW / dailyData.length / 2" :y="chartPadT"
                :width="plotW / dailyData.length" :height="plotH"
                fill="transparent" class="cursor-pointer"
                @mouseenter="onChartHover(i)" @mouseleave="onChartLeave" @click="onChartClick(i)" />
              <path :d="inputArea" fill="url(#aInGrad2)" />
              <path :d="inputLine" fill="none" stroke="rgb(6,182,212)" stroke-width="1.5" stroke-linecap="round" />
              <g v-if="chartHover >= 0">
                <line :x1="inputPts[chartHover]?.x" :y1="chartPadT"
                  :x2="inputPts[chartHover]?.x" :y2="chartPadT + plotH"
                  stroke="rgba(255,255,255,0.1)" stroke-width="1" stroke-dasharray="3,3" />
                <circle :cx="inputPts[chartHover]?.x" :cy="inputPts[chartHover]?.y" r="3"
                  fill="rgb(6,182,212)" stroke="#fff" stroke-width="1.5" />
              </g>
            </svg>
            <div v-if="chartHover >= 0" class="absolute pointer-events-none z-10"
              :style="{ left: (inputPts[chartHover]?.x / chartW * 100) + '%', top: '4px', transform: inputPts[chartHover]?.x > chartW * 0.7 ? 'translateX(-110%)' : 'translateX(10%)' }">
              <div class="bg-slate-800/95 border border-white/10 rounded-md px-2.5 py-1.5 shadow-lg backdrop-blur-sm">
                <p class="text-[10px] text-slate-400">{{ dailyData[chartHover]?.fullDate }}</p>
                <p class="text-xs font-medium text-cyan-400">{{ (dailyData[chartHover]?.input || 0).toLocaleString() }} tokens</p>
              </div>
            </div>
          </div>
        </div>

        <!-- 输出Token趋势 -->
        <div class="glass-card p-5">
          <div class="flex items-center justify-between mb-3">
            <div class="flex items-center gap-2">
              <span class="w-2.5 h-2.5 rounded-full bg-emerald-400"></span>
              <h3 class="text-sm font-semibold text-white">输出 Token</h3>
            </div>
            <span class="text-xs text-slate-500">近30天</span>
          </div>
          <div class="relative overflow-hidden rounded-lg">
            <svg :viewBox="`0 0 ${chartW} ${chartH}`" class="w-full" preserveAspectRatio="xMidYMid meet" style="min-height: 120px; max-height: 140px">
              <defs>
                <linearGradient id="aOutGrad2" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stop-color="rgb(16,185,129)" stop-opacity="0.35" />
                  <stop offset="100%" stop-color="rgb(16,185,129)" stop-opacity="0.01" />
                </linearGradient>
              </defs>
              <line v-for="line in chartGridLines" :key="line.y"
                :x1="chartPadL" :y1="line.y" :x2="chartW - chartPadR" :y2="line.y"
                stroke="rgba(255,255,255,0.04)" stroke-width="1" />
              <text v-for="line in chartGridLines" :key="'gl'+line.y"
                :x="chartPadL - 6" :y="line.y + 3" text-anchor="end"
                fill="rgb(100,116,139)" font-size="8">{{ line.label }}</text>
              <text v-for="(d, i) in dailyData" :key="'xl'+i"
                v-show="i % 10 === 0 || i === dailyData.length - 1"
                :x="outputPts[i]?.x" :y="chartPadT + plotH + 12" text-anchor="middle"
                fill="rgb(100,116,139)" font-size="7"
              >{{ d.label }}</text>
              <rect v-for="(pt, i) in outputPts" :key="'hz'+i"
                :x="pt.x - plotW / dailyData.length / 2" :y="chartPadT"
                :width="plotW / dailyData.length" :height="plotH"
                fill="transparent" class="cursor-pointer"
                @mouseenter="onChartHover(i)" @mouseleave="onChartLeave" @click="onChartClick(i)" />
              <path :d="outputArea" fill="url(#aOutGrad2)" />
              <path :d="outputLine" fill="none" stroke="rgb(16,185,129)" stroke-width="1.5" stroke-linecap="round" />
              <g v-if="chartHover >= 0">
                <line :x1="outputPts[chartHover]?.x" :y1="chartPadT"
                  :x2="outputPts[chartHover]?.x" :y2="chartPadT + plotH"
                  stroke="rgba(255,255,255,0.1)" stroke-width="1" stroke-dasharray="3,3" />
                <circle :cx="outputPts[chartHover]?.x" :cy="outputPts[chartHover]?.y" r="3"
                  fill="rgb(16,185,129)" stroke="#fff" stroke-width="1.5" />
              </g>
            </svg>
            <div v-if="chartHover >= 0" class="absolute pointer-events-none z-10"
              :style="{ left: (outputPts[chartHover]?.x / chartW * 100) + '%', top: '4px', transform: outputPts[chartHover]?.x > chartW * 0.7 ? 'translateX(-110%)' : 'translateX(10%)' }">
              <div class="bg-slate-800/95 border border-white/10 rounded-md px-2.5 py-1.5 shadow-lg backdrop-blur-sm">
                <p class="text-[10px] text-slate-400">{{ dailyData[chartHover]?.fullDate }}</p>
                <p class="text-xs font-medium text-emerald-400">{{ (dailyData[chartHover]?.output || 0).toLocaleString() }} tokens</p>
              </div>
            </div>
          </div>
        </div>

        <!-- 费用趋势 -->
        <div class="glass-card p-5">
          <div class="flex items-center justify-between mb-3">
            <div class="flex items-center gap-2">
              <span class="w-2.5 h-2.5 rounded-full bg-amber-400"></span>
              <h3 class="text-sm font-semibold text-white">费用趋势</h3>
            </div>
            <span class="text-xs text-slate-500">近30天</span>
          </div>
          <div class="relative overflow-hidden rounded-lg">
            <svg :viewBox="`0 0 ${chartW} ${chartH}`" class="w-full" preserveAspectRatio="xMidYMid meet" style="min-height: 120px; max-height: 140px">
              <defs>
                <linearGradient id="aCostGrad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stop-color="rgb(245,158,11)" stop-opacity="0.35" />
                  <stop offset="100%" stop-color="rgb(245,158,11)" stop-opacity="0.01" />
                </linearGradient>
              </defs>
              <line v-for="line in costGridLines" :key="line.y"
                :x1="chartPadL" :y1="line.y" :x2="chartW - chartPadR" :y2="line.y"
                stroke="rgba(255,255,255,0.04)" stroke-width="1" />
              <text v-for="line in costGridLines" :key="'gl'+line.y"
                :x="chartPadL - 6" :y="line.y + 3" text-anchor="end"
                fill="rgb(100,116,139)" font-size="8">{{ line.label }}</text>
              <text v-for="(d, i) in dailyData" :key="'xl'+i"
                v-show="i % 10 === 0 || i === dailyData.length - 1"
                :x="costPts[i]?.x" :y="chartPadT + plotH + 12" text-anchor="middle"
                fill="rgb(100,116,139)" font-size="7"
              >{{ d.label }}</text>
              <rect v-for="(pt, i) in costPts" :key="'hz'+i"
                :x="pt.x - plotW / dailyData.length / 2" :y="chartPadT"
                :width="plotW / dailyData.length" :height="plotH"
                fill="transparent" class="cursor-pointer"
                @mouseenter="onChartHover(i)" @mouseleave="onChartLeave" @click="onChartClick(i)" />
              <path :d="costArea" fill="url(#aCostGrad)" />
              <path :d="costLine" fill="none" stroke="rgb(245,158,11)" stroke-width="1.5" stroke-linecap="round" />
              <g v-if="chartHover >= 0">
                <line :x1="costPts[chartHover]?.x" :y1="chartPadT"
                  :x2="costPts[chartHover]?.x" :y2="chartPadT + plotH"
                  stroke="rgba(255,255,255,0.1)" stroke-width="1" stroke-dasharray="3,3" />
                <circle :cx="costPts[chartHover]?.x" :cy="costPts[chartHover]?.y" r="3"
                  fill="rgb(245,158,11)" stroke="#fff" stroke-width="1.5" />
              </g>
            </svg>
            <div v-if="chartHover >= 0" class="absolute pointer-events-none z-10"
              :style="{ left: (costPts[chartHover]?.x / chartW * 100) + '%', top: '4px', transform: costPts[chartHover]?.x > chartW * 0.7 ? 'translateX(-110%)' : 'translateX(10%)' }">
              <div class="bg-slate-800/95 border border-white/10 rounded-md px-2.5 py-1.5 shadow-lg backdrop-blur-sm">
                <p class="text-[10px] text-slate-400">{{ dailyData[chartHover]?.fullDate }}</p>
                <p class="text-xs font-medium text-amber-400">{{ formatCost(dailyData[chartHover]?.cost) }}</p>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
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
                  <span class="status-dot" :class="model.status === 0 ? 'online' : 'offline'"></span>
                  <div>
                    <p class="text-sm font-medium text-white">{{ model.name }}</p>
                    <p class="text-xs text-slate-400">{{ model.provider }}</p>
                  </div>
                </div>
                <span :class="['px-2 py-0.5 text-xs rounded-full', model.status === 0 ? 'bg-emerald-500/10 text-emerald-400' : 'bg-slate-500/10 text-slate-400']">
                  {{ model.status === 0 ? '在线' : '离线' }}
                </span>
              </div>
              <div class="flex items-center justify-between text-xs text-slate-500">
                <span>{{ model.modelId }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- Model Usage Breakdown -->
        <div class="lg:col-span-2 glass-card p-6">
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
              <tr v-for="user in recentUsers" :key="user.id" class="hover:bg-white/5 transition-colors cursor-pointer" @click="openUserDetail(user)">
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

    <!-- User Detail Modal -->
    <div v-if="showUserDetail && userDetailData" class="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm" @click.self="showUserDetail = false">
      <div class="glass-card w-full max-w-3xl max-h-[85vh] overflow-y-auto m-4">
        <!-- Header -->
        <div class="flex items-center justify-between p-6 border-b border-white/5">
          <div class="flex items-center gap-4">
            <div class="w-12 h-12 rounded-full bg-gradient-to-br from-emerald-500 to-cyan-500 flex items-center justify-center text-white text-lg font-bold">
              {{ (userDetailData.user.name || userDetailData.user.username || '?')[0] }}
            </div>
            <div>
              <h3 class="text-lg font-semibold text-white">{{ userDetailData.user.name || userDetailData.user.username || '未知' }}</h3>
              <p class="text-sm text-slate-400">{{ userDetailData.user.email || '-' }} · 余额 ¥{{ (userDetailData.user.balance || 0).toFixed(2) }}</p>
            </div>
          </div>
          <button @click="showUserDetail = false" class="p-2 rounded-lg hover:bg-white/5 text-slate-400 hover:text-white">
            <X class="w-5 h-5" />
          </button>
        </div>

        <!-- Summary -->
        <div class="p-6 grid grid-cols-4 gap-4 border-b border-white/5">
          <div class="text-center p-3 rounded-lg bg-cyan-500/5 border border-cyan-500/10">
            <p class="text-xs text-slate-500 mb-1">输入Token</p>
            <p class="text-lg font-bold text-cyan-400">{{ userDetailData.totalInput.toLocaleString() }}</p>
          </div>
          <div class="text-center p-3 rounded-lg bg-emerald-500/5 border border-emerald-500/10">
            <p class="text-xs text-slate-500 mb-1">输出Token</p>
            <p class="text-lg font-bold text-emerald-400">{{ userDetailData.totalOutput.toLocaleString() }}</p>
          </div>
          <div class="text-center p-3 rounded-lg bg-amber-500/5 border border-amber-500/10">
            <p class="text-xs text-slate-500 mb-1">总费用</p>
            <p class="text-lg font-bold text-amber-400">{{ formatCost(userDetailData.totalCost) }}</p>
          </div>
          <div class="text-center p-3 rounded-lg bg-purple-500/5 border border-purple-500/10">
            <p class="text-xs text-slate-500 mb-1">调用次数</p>
            <p class="text-lg font-bold text-purple-400">{{ userDetailData.totalCalls }}</p>
          </div>
        </div>

        <!-- Mini Charts -->
        <div class="p-6 grid grid-cols-2 gap-4 border-b border-white/5">
          <div>
            <p class="text-xs text-slate-400 mb-2 flex items-center gap-1.5"><span class="w-2 h-2 rounded-full bg-cyan-400"></span>Token 消耗趋势（近30天）</p>
            <svg :viewBox="`0 0 ${userDetailData.miniChart.cW} ${userDetailData.miniChart.cH}`" class="w-full" preserveAspectRatio="xMidYMid meet">
              <defs>
                <linearGradient id="uTokGrad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stop-color="rgb(6,182,212)" stop-opacity="0.3" />
                  <stop offset="100%" stop-color="rgb(6,182,212)" stop-opacity="0.01" />
                </linearGradient>
              </defs>
              <path :d="userDetailData.miniChart.miniArea(userDetailData.miniChart.tokenPts)" fill="url(#uTokGrad)" />
              <path :d="userDetailData.miniChart.miniPath(userDetailData.miniChart.tokenPts)" fill="none" stroke="rgb(6,182,212)" stroke-width="1.5" stroke-linecap="round" />
            </svg>
          </div>
          <div>
            <p class="text-xs text-slate-400 mb-2 flex items-center gap-1.5"><span class="w-2 h-2 rounded-full bg-amber-400"></span>费用趋势（近30天）</p>
            <svg :viewBox="`0 0 ${userDetailData.miniChart.cW} ${userDetailData.miniChart.cH}`" class="w-full" preserveAspectRatio="xMidYMid meet">
              <defs>
                <linearGradient id="uCostGrad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stop-color="rgb(245,158,11)" stop-opacity="0.3" />
                  <stop offset="100%" stop-color="rgb(245,158,11)" stop-opacity="0.01" />
                </linearGradient>
              </defs>
              <path :d="userDetailData.miniChart.miniArea(userDetailData.miniChart.costPtsUser)" fill="url(#uCostGrad)" />
              <path :d="userDetailData.miniChart.miniPath(userDetailData.miniChart.costPtsUser)" fill="none" stroke="rgb(245,158,11)" stroke-width="1.5" stroke-linecap="round" />
            </svg>
          </div>
        </div>

        <!-- Model Breakdown -->
        <div v-if="userDetailData.models.length" class="p-6 border-b border-white/5">
          <h4 class="text-sm font-medium text-slate-400 mb-4 flex items-center gap-2">
            <TrendingUp class="w-4 h-4" /> 模型使用分布
          </h4>
          <div class="space-y-3">
            <div v-for="m in userDetailData.models" :key="m.model" class="flex items-center gap-3">
              <span class="text-sm text-white w-28 truncate">{{ m.model }}</span>
              <div class="flex-1 h-2 rounded-full bg-white/5 overflow-hidden">
                <div class="h-full bg-gradient-to-r from-cyan-500 to-emerald-500 rounded-full" :style="{ width: (m.calls / userDetailData.models[0].calls * 100) + '%' }"></div>
              </div>
              <span class="text-xs text-slate-400 w-16 text-right">{{ m.calls }} 次</span>
              <span class="text-xs text-cyan-400 w-20 text-right">{{ formatTokens(m.tokens) }}</span>
              <span class="text-xs text-emerald-400 w-20 text-right">{{ formatCost(m.cost) }}</span>
            </div>
          </div>
        </div>

        <!-- Recent Records -->
        <div class="p-6">
          <h4 class="text-sm font-medium text-slate-400 mb-4">最近调用记录</h4>
          <div class="space-y-2 max-h-48 overflow-y-auto">
            <div v-for="(r, i) in billingRecords.filter(r => r.userId === userDetailData.user.id && r.type === 'TOKEN_USAGE').slice(-10).reverse()" :key="i"
              class="flex items-center gap-3 p-3 rounded-lg bg-white/[0.02] hover:bg-white/[0.04]">
              <div class="flex-1 min-w-0">
                <p class="text-sm text-white truncate">{{ r.detail || '-' }}</p>
                <p class="text-xs text-slate-500 mt-0.5">{{ formatTime(r.createdAt) }}</p>
              </div>
              <div class="text-right flex-shrink-0">
                <div class="flex items-center gap-2 text-xs">
                  <span class="text-cyan-400">{{ r.inputTokens || 0 }} in</span>
                  <span class="text-emerald-400">{{ r.outputTokens || 0 }} out</span>
                </div>
                <p class="text-xs text-amber-400 mt-0.5">{{ formatCost(Math.abs(Number(r.amount || 0))) }}</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Day Detail Modal -->
    <div v-if="showDayDetail && dayDetailData" class="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm" @click.self="showDayDetail = false">
      <div class="glass-card w-full max-w-2xl max-h-[80vh] overflow-y-auto m-4">
        <div class="flex items-center justify-between p-6 border-b border-white/5">
          <div>
            <h3 class="text-lg font-semibold text-white">{{ dayDetailData.date }} 消耗详情</h3>
            <p class="text-sm text-slate-400 mt-1">共 {{ dayDetailData.total.toLocaleString() }} tokens，{{ dayDetailData.records.length }} 次调用</p>
          </div>
          <button @click="showDayDetail = false" class="p-2 rounded-lg hover:bg-white/5 text-slate-400 hover:text-white">
            <X class="w-5 h-5" />
          </button>
        </div>

        <div class="p-6 grid grid-cols-4 gap-4 border-b border-white/5">
          <div class="text-center p-3 rounded-lg bg-cyan-500/5 border border-cyan-500/10">
            <p class="text-xs text-slate-500 mb-1">输入Token</p>
            <p class="text-lg font-bold text-cyan-400">{{ dayDetailData.input.toLocaleString() }}</p>
          </div>
          <div class="text-center p-3 rounded-lg bg-emerald-500/5 border border-emerald-500/10">
            <p class="text-xs text-slate-500 mb-1">输出Token</p>
            <p class="text-lg font-bold text-emerald-400">{{ dayDetailData.output.toLocaleString() }}</p>
          </div>
          <div class="text-center p-3 rounded-lg bg-amber-500/5 border border-amber-500/10">
            <p class="text-xs text-slate-500 mb-1">调用次数</p>
            <p class="text-lg font-bold text-amber-400">{{ dayDetailData.records.length }}</p>
          </div>
          <div class="text-center p-3 rounded-lg bg-purple-500/5 border border-purple-500/10">
            <p class="text-xs text-slate-500 mb-1">费用</p>
            <p class="text-lg font-bold text-purple-400">{{ formatCost(dayDetailData.cost) }}</p>
          </div>
        </div>

        <div v-if="dayDetailData.models.length" class="p-6 border-b border-white/5">
          <h4 class="text-sm font-medium text-slate-400 mb-4 flex items-center gap-2">
            <TrendingUp class="w-4 h-4" /> 模型调用分布
          </h4>
          <div class="space-y-3">
            <div v-for="m in dayDetailData.models" :key="m.model" class="flex items-center gap-3">
              <span class="text-sm text-white w-28 truncate">{{ m.model }}</span>
              <div class="flex-1 h-2 rounded-full bg-white/5 overflow-hidden">
                <div class="h-full bg-gradient-to-r from-cyan-500 to-emerald-500 rounded-full" :style="{ width: (m.calls / dayDetailData.models[0].calls * 100) + '%' }"></div>
              </div>
              <span class="text-xs text-slate-400 w-16 text-right">{{ m.calls }} 次</span>
              <span class="text-xs text-cyan-400 w-20 text-right">{{ formatTokens(m.input + m.output) }}</span>
              <span class="text-xs text-emerald-400 w-20 text-right">{{ formatCost(m.cost) }}</span>
            </div>
          </div>
        </div>

        <div class="p-6">
          <h4 class="text-sm font-medium text-slate-400 mb-4">调用记录</h4>
          <div v-if="dayDetailData.records.length === 0" class="text-center py-4 text-slate-500">无记录</div>
          <div v-else class="space-y-2 max-h-60 overflow-y-auto">
            <div v-for="(r, i) in dayDetailData.records" :key="i" class="flex items-center gap-3 p-3 rounded-lg bg-white/[0.02] hover:bg-white/[0.04]">
              <div class="flex-1 min-w-0">
                <p class="text-sm text-white truncate">{{ r.detail || '-' }}</p>
                <p class="text-xs text-slate-500 mt-0.5">{{ formatTime(r.createdAt) }}</p>
              </div>
              <div class="text-right flex-shrink-0">
                <div class="flex items-center gap-2 text-xs">
                  <span class="text-cyan-400">{{ r.inputTokens || 0 }} in</span>
                  <span class="text-emerald-400">{{ r.outputTokens || 0 }} out</span>
                </div>
                <p class="text-xs text-amber-400 mt-0.5">{{ formatCost(Math.abs(Number(r.amount || 0))) }}</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
