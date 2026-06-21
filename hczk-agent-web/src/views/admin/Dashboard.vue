<script setup>
import { ref, onMounted, computed } from 'vue'
import { useApiStore } from '@/stores/api'
import {
  Users, KeyRound, Coins, Wallet, Activity, Cpu, BarChart3, TrendingUp, X,
  Zap, ArrowUpRight, Clock, Server, Eye, Bot, Brain, Database, MessageSquare,
  AlertTriangle, CheckCircle2, XCircle, Radio, Settings
} from 'lucide-vue-next'

const api = useApiStore()
const loading = ref(false)
const users = ref([])
const apiKeys = ref([])
const billingRecords = ref([])
const models = ref([])
const agents = ref([])
const skills = ref([])
const knowledgeCollections = ref([])
const chatLogs = ref([])
const retrievalStats = ref(null)
const chartHover = ref(-1)

onMounted(loadData)

async function loadData() {
  loading.value = true
  const [usersRes, keysRes, billRes, modelsRes, agentsRes, skillsRes, knowledgeRes, retrievalRes] = await Promise.all([
    api.get('/users'),
    api.get('/api-keys'),
    api.get('/billing/records'),
    api.get('/models'),
    api.get('/agents'),
    api.get('/platform/skills'),
    api.get('/knowledge/collections'),
    api.get('/retrieval-logs/stats').catch(() => ({ code: 0 }))
  ])
  if (usersRes.code === 200) users.value = usersRes.data || []
  if (keysRes.code === 200) apiKeys.value = keysRes.data || []
  if (billRes.code === 200) billingRecords.value = billRes.data || []
  if (modelsRes.code === 200) models.value = modelsRes.data || []
  if (agentsRes.code === 200) agents.value = agentsRes.data || []
  if (skillsRes.code === 200) skills.value = skillsRes.data || []
  if (knowledgeRes.code === 200) knowledgeCollections.value = knowledgeRes.data || []
  if (retrievalRes.code === 200) retrievalStats.value = retrievalRes.data
  loading.value = false
}

// Core metrics
const now = new Date()
const today = now.toDateString()
const todayTokens = computed(() => {
  let t = 0
  for (const r of billingRecords.value) {
    if (r.type === 'TOKEN_USAGE' && r.createdAt && new Date(r.createdAt).toDateString() === today) t += (r.inputTokens || 0) + (r.outputTokens || 0)
  }
  return t
})
const todayCost = computed(() => {
  let c = 0
  for (const r of billingRecords.value) {
    if (r.type === 'TOKEN_USAGE' && r.createdAt && new Date(r.createdAt).toDateString() === today) c += Math.abs(Number(r.amount || 0))
  }
  return c
})
const todayCalls = computed(() => billingRecords.value.filter(r => r.type === 'TOKEN_USAGE' && r.createdAt && new Date(r.createdAt).toDateString() === today).length)
const totalRecharge = computed(() => billingRecords.value.filter(r => r.type === 'RECHARGE').reduce((s, r) => s + Number(r.amount || 0), 0))
const totalCost = computed(() => billingRecords.value.filter(r => r.type === 'TOKEN_USAGE').reduce((s, r) => s + Math.abs(Number(r.amount || 0)), 0))
const onlineModels = computed(() => models.value.filter(m => m.status === 0).length)
const onlineAgents = computed(() => agents.value.filter(a => a.status === 0 || a.enabled === true).length)
const activeSkills = computed(() => skills.value.filter(s => s.enabled !== false).length)
const totalKnowledgeChunks = computed(() => knowledgeCollections.value.reduce((s, c) => s + (c.chunkCount || c.rowCount || 0), 0))

// Yesterday comparison
const yesterday = new Date(now); yesterday.setDate(yesterday.getDate() - 1)
const yesterdayStr = yesterday.toDateString()
const yesterdayTokens = computed(() => {
  let t = 0
  for (const r of billingRecords.value) {
    if (r.type === 'TOKEN_USAGE' && r.createdAt && new Date(r.createdAt).toDateString() === yesterdayStr) t += (r.inputTokens || 0) + (r.outputTokens || 0)
  }
  return t
})
const yesterdayCalls = computed(() => billingRecords.value.filter(r => r.type === 'TOKEN_USAGE' && r.createdAt && new Date(r.createdAt).toDateString() === yesterdayStr).length)
const tokenChange = computed(() => yesterdayTokens.value ? ((todayTokens.value - yesterdayTokens.value) / yesterdayTokens.value * 100).toFixed(1) : null)
const callChange = computed(() => yesterdayCalls.value ? ((todayCalls.value - yesterdayCalls.value) / yesterdayCalls.value * 100).toFixed(1) : null)

// Daily data for chart (last 30 days)
const dailyData = computed(() => {
  const days = []
  for (let i = 29; i >= 0; i--) {
    const d = new Date(now); d.setDate(d.getDate() - i)
    const dateStr = d.toDateString()
    const label = `${d.getMonth() + 1}/${d.getDate()}`
    const fullDate = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
    let input = 0, output = 0, cost = 0, calls = 0
    for (const r of billingRecords.value) {
      if (r.type === 'TOKEN_USAGE' && r.createdAt && new Date(r.createdAt).toDateString() === dateStr) {
        input += r.inputTokens || 0; output += r.outputTokens || 0; cost += Math.abs(Number(r.amount || 0)); calls++
      }
    }
    days.push({ label, fullDate, dateStr, input, output, total: input + output, cost, calls })
  }
  return days
})

// Chart layout
const chartW = 800, chartH = 200, chartPadL = 45, chartPadR = 10, chartPadT = 10, chartPadB = 25
const plotW = chartW - chartPadL - chartPadR, plotH = chartH - chartPadT - chartPadB

const chartMaxY = computed(() => Math.max(...dailyData.value.map(d => Math.max(d.input, d.output))) || 1)

const chartGridLines = computed(() => {
  const steps = 4, lines = []
  for (let i = 0; i <= steps; i++) {
    const val = (chartMaxY.value / steps) * i
    const y = chartPadT + plotH - (plotH * i / steps)
    lines.push({ y, label: fmtK(Math.round(val)) })
  }
  return lines
})

function smoothPath(points, close = false) {
  if (points.length < 2) return ''
  let path = `M ${points[0].x} ${points[0].y}`
  for (let i = 0; i < points.length - 1; i++) {
    const p0 = points[Math.max(i - 1, 0)], p1 = points[i], p2 = points[i + 1], p3 = points[Math.min(i + 2, points.length - 1)]
    path += ` C ${p1.x + (p2.x - p0.x) / 6} ${p1.y + (p2.y - p0.y) / 6}, ${p2.x - (p3.x - p1.x) / 6} ${p2.y - (p3.y - p1.y) / 6}, ${p2.x} ${p2.y}`
  }
  if (close) { const last = points[points.length - 1], first = points[0]; path += ` L ${last.x} ${chartPadT + plotH} L ${first.x} ${chartPadT + plotH} Z` }
  return path
}

function makePoints(field) {
  const data = dailyData.value, max = chartMaxY.value
  return data.map((d, i) => ({ x: chartPadL + (plotW / (data.length - 1)) * i, y: chartPadT + plotH - (d[field] / max * plotH), value: d[field], label: d.label }))
}

const inputPts = computed(() => makePoints('input'))
const outputPts = computed(() => makePoints('output'))
const inputLine = computed(() => smoothPath(inputPts.value))
const outputLine = computed(() => smoothPath(outputPts.value))
const inputArea = computed(() => smoothPath(inputPts.value, true))
const outputArea = computed(() => smoothPath(outputPts.value, true))

// Cost chart
const maxCost = computed(() => Math.max(...dailyData.value.map(d => d.cost)) || 0.01)
const costGridLines = computed(() => {
  const steps = 4, lines = []
  for (let i = 0; i <= steps; i++) {
    const val = (maxCost.value / steps) * i
    const y = chartPadT + plotH - (plotH * i / steps)
    lines.push({ y, label: '¥' + val.toFixed(val >= 1 ? 1 : 4) })
  }
  return lines
})
const costPts = computed(() => {
  const data = dailyData.value, max = maxCost.value
  return data.map((d, i) => ({ x: chartPadL + (plotW / (data.length - 1)) * i, y: chartPadT + plotH - (d.cost / max * plotH), value: d.cost, label: d.label }))
})
const costLine = computed(() => smoothPath(costPts.value))
const costArea = computed(() => smoothPath(costPts.value, true))

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
  const maxT = list.length ? list[0].tokens : 1
  return list.slice(0, 8).map(item => ({ ...item, percent: Math.round(item.tokens / maxT * 100), costStr: '¥' + item.cost.toFixed(4), tokenStr: fmtK(item.tokens) }))
})

// Agent status summary
const agentStatusMap = computed(() => {
  const map = {}
  for (const a of agents.value) {
    const type = a.type || 'unknown'
    if (!map[type]) map[type] = { type, total: 0, online: 0 }
    map[type].total++
    if (a.status === 0 || a.enabled === true) map[type].online++
  }
  return Object.values(map)
})

// Recent users
const recentUsers = computed(() => [...users.value].sort((a, b) => new Date(b.createdAt || 0) - new Date(a.createdAt || 0)).slice(0, 5))

// Quick nav items
const quickNav = [
  { label: '智能体', desc: `${agents.value.length} 个`, icon: Bot, to: '/admin/agents', color: 'cyan' },
  { label: '知识库', desc: `${knowledgeCollections.value.length} 个集合`, icon: Database, to: '/admin/knowledge', color: 'emerald' },
  { label: '模型', desc: `${onlineModels.value}/${models.value.length} 在线`, icon: Cpu, to: '/admin/models', color: 'amber' },
  { label: '用户', desc: `${users.value.length} 人`, icon: Users, to: '/admin/users', color: 'purple' }
]

const navColorMap = {
  cyan: { bg: 'bg-cyan-500/10', text: 'text-cyan-400' },
  emerald: { bg: 'bg-emerald-500/10', text: 'text-emerald-400' },
  amber: { bg: 'bg-amber-500/10', text: 'text-amber-400' },
  purple: { bg: 'bg-purple-500/10', text: 'text-purple-400' }
}

// Helpers
function fmtK(n) { if (!n) return '0'; if (n >= 1000000) return (n / 1000000).toFixed(1) + 'M'; if (n >= 1000) return (n / 1000).toFixed(1) + 'K'; return n.toString() }
function fmtCost(c) { return '¥' + Number(c || 0).toFixed(4) }
function fmtTime(d) { return d ? new Date(d).toLocaleString('zh-CN') : '-' }
function fmtRelative(d) {
  if (!d) return '-'; const diff = Date.now() - new Date(d).getTime()
  if (diff < 60000) return '刚刚'; if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'
  if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'; return Math.floor(diff / 86400000) + '天前'
}
function changeLabel(val) {
  if (val === null) return ''; const n = Number(val)
  return n >= 0 ? `+${n}%` : `${n}%`
}

function onChartHover(i) { chartHover.value = i }
function onChartLeave() { chartHover.value = -1 }
</script>

<template>
  <div class="space-y-5 animate-fade-in">
    <div v-if="loading" class="flex items-center justify-center py-32">
      <div class="w-10 h-10 border-2 border-emerald-500/30 border-t-emerald-500 rounded-full animate-spin"></div>
      <span class="ml-3 text-slate-400">加载中...</span>
    </div>

    <template v-else>
      <!-- Row 1: Core Metrics -->
      <div class="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-3">
        <div class="glass-card rounded-xl p-3.5">
          <div class="flex items-center justify-between mb-2">
            <span class="text-[11px] text-slate-500">今日调用</span>
            <Zap class="w-3.5 h-3.5 text-cyan-400" />
          </div>
          <p class="text-2xl font-bold text-white">{{ todayCalls }}</p>
          <p v-if="callChange !== null" class="text-[10px] mt-1" :class="Number(callChange) >= 0 ? 'text-emerald-400' : 'text-red-400'">{{ changeLabel(callChange) }} vs 昨日</p>
        </div>
        <div class="glass-card rounded-xl p-3.5">
          <div class="flex items-center justify-between mb-2">
            <span class="text-[11px] text-slate-500">今日Token</span>
            <Coins class="w-3.5 h-3.5 text-amber-400" />
          </div>
          <p class="text-2xl font-bold text-white">{{ fmtK(todayTokens) }}</p>
          <p v-if="tokenChange !== null" class="text-[10px] mt-1" :class="Number(tokenChange) >= 0 ? 'text-emerald-400' : 'text-red-400'">{{ changeLabel(tokenChange) }} vs 昨日</p>
        </div>
        <div class="glass-card rounded-xl p-3.5">
          <div class="flex items-center justify-between mb-2">
            <span class="text-[11px] text-slate-500">今日收入</span>
            <Wallet class="w-3.5 h-3.5 text-emerald-400" />
          </div>
          <p class="text-2xl font-bold text-emerald-400">¥{{ todayCost.toFixed(2) }}</p>
          <p class="text-[10px] text-slate-500 mt-1">累计 ¥{{ totalCost.toFixed(2) }}</p>
        </div>
        <div class="glass-card rounded-xl p-3.5">
          <div class="flex items-center justify-between mb-2">
            <span class="text-[11px] text-slate-500">总充值</span>
            <Wallet class="w-3.5 h-3.5 text-purple-400" />
          </div>
          <p class="text-2xl font-bold text-purple-400">¥{{ totalRecharge.toFixed(2) }}</p>
          <p class="text-[10px] text-slate-500 mt-1">{{ users.length }} 用户</p>
        </div>
        <div class="glass-card rounded-xl p-3.5">
          <div class="flex items-center justify-between mb-2">
            <span class="text-[11px] text-slate-500">在线模型</span>
            <Cpu class="w-3.5 h-3.5 text-cyan-400" />
          </div>
          <p class="text-2xl font-bold text-white">{{ onlineModels }}<span class="text-sm text-slate-500">/{{ models.length }}</span></p>
          <p class="text-[10px] text-slate-500 mt-1">{{ apiKeys.length }} 个 Key</p>
        </div>
        <div class="glass-card rounded-xl p-3.5">
          <div class="flex items-center justify-between mb-2">
            <span class="text-[11px] text-slate-500">智能体</span>
            <Bot class="w-3.5 h-3.5 text-emerald-400" />
          </div>
          <p class="text-2xl font-bold text-white">{{ onlineAgents }}<span class="text-sm text-slate-500">/{{ agents.length }}</span></p>
          <p class="text-[10px] text-slate-500 mt-1">{{ activeSkills }} 个 Skill</p>
        </div>
      </div>

      <!-- Row 2: Token Chart + Cost Chart -->
      <div class="grid grid-cols-1 lg:grid-cols-3 gap-5">
        <!-- Token Trend -->
        <div class="lg:col-span-2 glass-card rounded-2xl overflow-hidden">
          <div class="px-5 pt-4 pb-3 border-b border-white/5 flex items-center justify-between">
            <div class="flex items-center gap-3">
              <h3 class="text-sm font-semibold text-white">Token 趋势</h3>
              <div class="flex items-center gap-3 text-[11px] text-slate-400">
                <span class="flex items-center gap-1"><span class="w-2 h-2 rounded-full bg-cyan-400"></span>输入</span>
                <span class="flex items-center gap-1"><span class="w-2 h-2 rounded-full bg-emerald-400"></span>输出</span>
              </div>
            </div>
            <span class="text-[11px] text-slate-500">近30天</span>
          </div>
          <div class="p-4">
            <div class="relative overflow-hidden rounded-lg">
              <svg :viewBox="`0 0 ${chartW} ${chartH}`" class="w-full" preserveAspectRatio="xMidYMid meet" style="min-height:140px;max-height:180px">
                <defs>
                  <linearGradient id="aInG4" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stop-color="rgb(6,182,212)" stop-opacity="0.2" /><stop offset="100%" stop-color="rgb(6,182,212)" stop-opacity="0.01" /></linearGradient>
                  <linearGradient id="aOutG4" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stop-color="rgb(16,185,129)" stop-opacity="0.2" /><stop offset="100%" stop-color="rgb(16,185,129)" stop-opacity="0.01" /></linearGradient>
                </defs>
                <line v-for="line in chartGridLines" :key="line.y" :x1="chartPadL" :y1="line.y" :x2="chartW - chartPadR" :y2="line.y" stroke="rgba(255,255,255,0.04)" />
                <text v-for="line in chartGridLines" :key="'g'+line.y" :x="chartPadL - 5" :y="line.y + 3" text-anchor="end" fill="rgb(100,116,139)" font-size="8">{{ line.label }}</text>
                <text v-for="(d, i) in dailyData" :key="'x'+i" v-show="i % 10 === 0 || i === dailyData.length - 1" :x="inputPts[i]?.x" :y="chartPadT + plotH + 14" text-anchor="middle" fill="rgb(100,116,139)" font-size="7">{{ d.label }}</text>
                <rect v-for="(pt, i) in inputPts" :key="'h'+i" :x="pt.x - plotW / dailyData.length / 2" :y="chartPadT" :width="plotW / dailyData.length" :height="plotH" fill="transparent" class="cursor-pointer" @mouseenter="onChartHover(i)" @mouseleave="onChartLeave" />
                <rect v-if="chartHover >= 0" :x="inputPts[chartHover]?.x - plotW / dailyData.length / 2" :y="chartPadT" :width="plotW / dailyData.length" :height="plotH" fill="rgba(255,255,255,0.03)" />
                <path :d="inputArea" fill="url(#aInG4)" />
                <path :d="outputArea" fill="url(#aOutG4)" />
                <path :d="outputLine" fill="none" stroke="rgb(16,185,129)" stroke-width="1.5" stroke-linecap="round" />
                <path :d="inputLine" fill="none" stroke="rgb(6,182,212)" stroke-width="1.5" stroke-linecap="round" />
                <g v-if="chartHover >= 0">
                  <line :x1="inputPts[chartHover]?.x" :y1="chartPadT" :x2="inputPts[chartHover]?.x" :y2="chartPadT + plotH" stroke="rgba(255,255,255,0.1)" stroke-dasharray="3,3" />
                  <circle :cx="inputPts[chartHover]?.x" :cy="inputPts[chartHover]?.y" r="3" fill="rgb(6,182,212)" stroke="#0a0f1c" stroke-width="1.5" />
                  <circle :cx="outputPts[chartHover]?.x" :cy="outputPts[chartHover]?.y" r="3" fill="rgb(16,185,129)" stroke="#0a0f1c" stroke-width="1.5" />
                </g>
              </svg>
              <div v-if="chartHover >= 0" class="absolute pointer-events-none z-10"
                :style="{ left: (inputPts[chartHover]?.x / chartW * 100) + '%', top: '4px', transform: inputPts[chartHover]?.x > chartW * 0.7 ? 'translateX(-110%)' : 'translateX(10%)' }">
                <div class="bg-slate-800/95 border border-white/10 rounded-lg px-3 py-2 shadow-lg backdrop-blur-sm">
                  <p class="text-[10px] text-slate-400 mb-1">{{ dailyData[chartHover]?.fullDate }}</p>
                  <p class="text-xs text-cyan-400">输入 {{ (dailyData[chartHover]?.input || 0).toLocaleString() }}</p>
                  <p class="text-xs text-emerald-400">输出 {{ (dailyData[chartHover]?.output || 0).toLocaleString() }}</p>
                  <p class="text-xs text-amber-400 mt-0.5 pt-0.5 border-t border-white/5">费用 ¥{{ (dailyData[chartHover]?.cost || 0).toFixed(4) }}</p>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Cost Trend -->
        <div class="glass-card rounded-2xl overflow-hidden">
          <div class="px-5 pt-4 pb-3 border-b border-white/5 flex items-center gap-2">
            <span class="w-2 h-2 rounded-full bg-amber-400"></span>
            <h3 class="text-sm font-semibold text-white">费用趋势</h3>
            <span class="text-[11px] text-slate-500 ml-auto">近30天</span>
          </div>
          <div class="p-4">
            <div class="relative overflow-hidden rounded-lg">
              <svg :viewBox="`0 0 ${chartW} ${chartH}`" class="w-full" preserveAspectRatio="xMidYMid meet" style="min-height:140px;max-height:180px">
                <defs><linearGradient id="aCostG4" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stop-color="rgb(245,158,11)" stop-opacity="0.2" /><stop offset="100%" stop-color="rgb(245,158,11)" stop-opacity="0.01" /></linearGradient></defs>
                <line v-for="line in costGridLines" :key="line.y" :x1="chartPadL" :y1="line.y" :x2="chartW - chartPadR" :y2="line.y" stroke="rgba(255,255,255,0.04)" />
                <text v-for="line in costGridLines" :key="'g'+line.y" :x="chartPadL - 5" :y="line.y + 3" text-anchor="end" fill="rgb(100,116,139)" font-size="8">{{ line.label }}</text>
                <text v-for="(d, i) in dailyData" :key="'x'+i" v-show="i % 10 === 0 || i === dailyData.length - 1" :x="costPts[i]?.x" :y="chartPadT + plotH + 14" text-anchor="middle" fill="rgb(100,116,139)" font-size="7">{{ d.label }}</text>
                <rect v-for="(pt, i) in costPts" :key="'h'+i" :x="pt.x - plotW / dailyData.length / 2" :y="chartPadT" :width="plotW / dailyData.length" :height="plotH" fill="transparent" class="cursor-pointer" @mouseenter="onChartHover(i)" @mouseleave="onChartLeave" />
                <rect v-if="chartHover >= 0" :x="costPts[chartHover]?.x - plotW / dailyData.length / 2" :y="chartPadT" :width="plotW / dailyData.length" :height="plotH" fill="rgba(255,255,255,0.03)" />
                <path :d="costArea" fill="url(#aCostG4)" />
                <path :d="costLine" fill="none" stroke="rgb(245,158,11)" stroke-width="1.5" stroke-linecap="round" />
                <g v-if="chartHover >= 0">
                  <line :x1="costPts[chartHover]?.x" :y1="chartPadT" :x2="costPts[chartHover]?.x" :y2="chartPadT + plotH" stroke="rgba(255,255,255,0.1)" stroke-dasharray="3,3" />
                  <circle :cx="costPts[chartHover]?.x" :cy="costPts[chartHover]?.y" r="3" fill="rgb(245,158,11)" stroke="#0a0f1c" stroke-width="1.5" />
                </g>
              </svg>
              <div v-if="chartHover >= 0" class="absolute pointer-events-none z-10"
                :style="{ left: (costPts[chartHover]?.x / chartW * 100) + '%', top: '4px', transform: costPts[chartHover]?.x > chartW * 0.7 ? 'translateX(-110%)' : 'translateX(10%)' }">
                <div class="bg-slate-800/95 border border-white/10 rounded-lg px-3 py-2 shadow-lg backdrop-blur-sm">
                  <p class="text-[10px] text-slate-400">{{ dailyData[chartHover]?.fullDate }}</p>
                  <p class="text-xs font-medium text-amber-400">{{ fmtCost(dailyData[chartHover]?.cost) }}</p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Row 3: AI Capabilities + Model Usage -->
      <div class="grid grid-cols-1 lg:grid-cols-3 gap-5">
        <!-- AI Capabilities Overview -->
        <div class="glass-card rounded-2xl overflow-hidden">
          <div class="px-5 pt-4 pb-3 border-b border-white/5 flex items-center gap-2">
            <Brain class="w-4 h-4 text-cyan-400" />
            <h3 class="text-sm font-semibold text-white">AI 能力</h3>
          </div>
          <div class="p-4 space-y-2.5">
            <!-- Agents -->
            <router-link to="/admin/agents" class="flex items-center gap-3 p-3 rounded-xl bg-white/[0.02] hover:bg-white/[0.05] transition-colors group">
              <div class="w-9 h-9 rounded-lg bg-cyan-500/10 flex items-center justify-center"><Bot class="w-4 h-4 text-cyan-400" /></div>
              <div class="flex-1 min-w-0">
                <p class="text-sm text-white group-hover:text-cyan-400 transition-colors">智能体</p>
                <p class="text-[10px] text-slate-500">{{ onlineAgents }} 运行中 / {{ agents.value?.length || agents.length }} 总计</p>
              </div>
              <ArrowUpRight class="w-3.5 h-3.5 text-slate-600 group-hover:text-cyan-400 transition-colors" />
            </router-link>
            <!-- Skills -->
            <router-link to="/admin/skills" class="flex items-center gap-3 p-3 rounded-xl bg-white/[0.02] hover:bg-white/[0.05] transition-colors group">
              <div class="w-9 h-9 rounded-lg bg-emerald-500/10 flex items-center justify-center"><Zap class="w-4 h-4 text-emerald-400" /></div>
              <div class="flex-1 min-w-0">
                <p class="text-sm text-white group-hover:text-emerald-400 transition-colors">Skill 工具</p>
                <p class="text-[10px] text-slate-500">{{ activeSkills }} 已启用 / {{ skills.length }} 总计</p>
              </div>
              <ArrowUpRight class="w-3.5 h-3.5 text-slate-600 group-hover:text-emerald-400 transition-colors" />
            </router-link>
            <!-- Knowledge -->
            <router-link to="/admin/knowledge" class="flex items-center gap-3 p-3 rounded-xl bg-white/[0.02] hover:bg-white/[0.05] transition-colors group">
              <div class="w-9 h-9 rounded-lg bg-amber-500/10 flex items-center justify-center"><Database class="w-4 h-4 text-amber-400" /></div>
              <div class="flex-1 min-w-0">
                <p class="text-sm text-white group-hover:text-amber-400 transition-colors">知识库</p>
                <p class="text-[10px] text-slate-500">{{ knowledgeCollections.length }} 集合 / {{ fmtK(totalKnowledgeChunks) }} 分块</p>
              </div>
              <ArrowUpRight class="w-3.5 h-3.5 text-slate-600 group-hover:text-amber-400 transition-colors" />
            </router-link>
            <!-- Models -->
            <router-link to="/admin/models" class="flex items-center gap-3 p-3 rounded-xl bg-white/[0.02] hover:bg-white/[0.05] transition-colors group">
              <div class="w-9 h-9 rounded-lg bg-purple-500/10 flex items-center justify-center"><Cpu class="w-4 h-4 text-purple-400" /></div>
              <div class="flex-1 min-w-0">
                <p class="text-sm text-white group-hover:text-purple-400 transition-colors">模型</p>
                <p class="text-[10px] text-slate-500">{{ onlineModels }} 在线 / {{ models.length }} 总计</p>
              </div>
              <ArrowUpRight class="w-3.5 h-3.5 text-slate-600 group-hover:text-purple-400 transition-colors" />
            </router-link>
          </div>
        </div>

        <!-- Model Usage Breakdown -->
        <div class="lg:col-span-2 glass-card rounded-2xl overflow-hidden">
          <div class="px-5 pt-4 pb-3 border-b border-white/5 flex items-center gap-2">
            <BarChart3 class="w-4 h-4 text-emerald-400" />
            <h3 class="text-sm font-semibold text-white">模型使用分布</h3>
          </div>
          <div class="p-4">
            <div v-if="modelBreakdown.length === 0" class="text-center py-10 text-slate-500 text-xs">暂无调用数据</div>
            <div v-else class="space-y-3">
              <div v-for="item in modelBreakdown" :key="item.model" class="flex items-center gap-3">
                <span class="text-sm text-white w-36 truncate flex-shrink-0" :title="item.model">{{ item.model }}</span>
                <div class="flex-1 h-1.5 rounded-full bg-white/5 overflow-hidden">
                  <div class="h-full bg-gradient-to-r from-emerald-500 to-cyan-500 rounded-full transition-all" :style="{ width: item.percent + '%' }"></div>
                </div>
                <span class="text-[11px] text-slate-400 w-16 text-right flex-shrink-0">{{ item.tokenStr }}</span>
                <span class="text-[11px] text-slate-500 w-12 text-right flex-shrink-0">{{ item.calls }}次</span>
                <span class="text-[11px] text-emerald-400 w-20 text-right flex-shrink-0">{{ item.costStr }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Row 4: Recent Users + System Health -->
      <div class="grid grid-cols-1 lg:grid-cols-3 gap-5">
        <!-- Recent Users -->
        <div class="lg:col-span-2 glass-card rounded-2xl overflow-hidden">
          <div class="px-5 pt-4 pb-3 border-b border-white/5 flex items-center justify-between">
            <div class="flex items-center gap-2">
              <Users class="w-4 h-4 text-purple-400" />
              <h3 class="text-sm font-semibold text-white">最近注册用户</h3>
            </div>
            <router-link to="/admin/users" class="text-[11px] text-emerald-400 hover:text-emerald-300 flex items-center gap-0.5">查看全部<ArrowUpRight class="w-3 h-3" /></router-link>
          </div>
          <div class="p-4">
            <div v-if="recentUsers.length === 0" class="text-center py-8 text-slate-500 text-xs">暂无用户</div>
            <div v-else class="space-y-2">
              <div v-for="user in recentUsers" :key="user.userId" class="flex items-center gap-3 p-2.5 rounded-xl bg-white/[0.02] hover:bg-white/[0.04] transition-colors">
                <div class="w-8 h-8 rounded-lg bg-gradient-to-br from-emerald-500 to-cyan-500 flex items-center justify-center text-white text-xs font-bold flex-shrink-0">{{ (user.username || '?')[0] }}</div>
                <div class="flex-1 min-w-0">
                  <p class="text-sm text-white">{{ user.username || '未知' }}</p>
                  <p class="text-[10px] text-slate-500 truncate">{{ user.email || '-' }}</p>
                </div>
                <div class="text-right flex-shrink-0">
                  <p class="text-sm text-emerald-400">¥{{ (user.balance || 0).toFixed(2) }}</p>
                  <p class="text-[10px] text-slate-500">{{ fmtK(user.totalUsageTokens || 0) }} tokens</p>
                </div>
                <span class="text-[10px] text-slate-500 flex-shrink-0 w-14 text-right">{{ fmtRelative(user.createdAt) }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- System Status -->
        <div class="glass-card rounded-2xl overflow-hidden">
          <div class="px-5 pt-4 pb-3 border-b border-white/5 flex items-center gap-2">
            <Activity class="w-4 h-4 text-emerald-400" />
            <h3 class="text-sm font-semibold text-white">系统状态</h3>
          </div>
          <div class="p-4 space-y-3">
            <div class="flex items-center gap-3 p-3 rounded-xl bg-white/[0.02]">
              <CheckCircle2 class="w-4 h-4 text-emerald-400 flex-shrink-0" />
              <div class="flex-1">
                <p class="text-sm text-white">API 服务</p>
                <p class="text-[10px] text-slate-500">正常运行</p>
              </div>
              <span class="w-2 h-2 rounded-full bg-emerald-400 shadow-sm shadow-emerald-400/50"></span>
            </div>
            <div class="flex items-center gap-3 p-3 rounded-xl bg-white/[0.02]">
              <component :is="onlineModels > 0 ? CheckCircle2 : XCircle" :class="['w-4 h-4 flex-shrink-0', onlineModels > 0 ? 'text-emerald-400' : 'text-red-400']" />
              <div class="flex-1">
                <p class="text-sm text-white">模型服务</p>
                <p class="text-[10px] text-slate-500">{{ onlineModels }}/{{ models.length }} 可用</p>
              </div>
              <span :class="['w-2 h-2 rounded-full shadow-sm', onlineModels > 0 ? 'bg-emerald-400 shadow-emerald-400/50' : 'bg-red-400 shadow-red-400/50']"></span>
            </div>
            <div class="flex items-center gap-3 p-3 rounded-xl bg-white/[0.02]">
              <component :is="onlineAgents > 0 ? CheckCircle2 : XCircle" :class="['w-4 h-4 flex-shrink-0', onlineAgents > 0 ? 'text-emerald-400' : 'text-amber-400']" />
              <div class="flex-1">
                <p class="text-sm text-white">智能体</p>
                <p class="text-[10px] text-slate-500">{{ onlineAgents }}/{{ agents.length }} 运行中</p>
              </div>
              <span :class="['w-2 h-2 rounded-full shadow-sm', onlineAgents > 0 ? 'bg-emerald-400 shadow-emerald-400/50' : 'bg-amber-400 shadow-amber-400/50']"></span>
            </div>
            <div class="flex items-center gap-3 p-3 rounded-xl bg-white/[0.02]">
              <Database :class="['w-4 h-4 flex-shrink-0', knowledgeCollections.length > 0 ? 'text-emerald-400' : 'text-slate-500']" />
              <div class="flex-1">
                <p class="text-sm text-white">知识库</p>
                <p class="text-[10px] text-slate-500">{{ knowledgeCollections.length }} 集合 / {{ fmtK(totalKnowledgeChunks) }} 分块</p>
              </div>
              <span :class="['w-2 h-2 rounded-full shadow-sm', knowledgeCollections.length > 0 ? 'bg-emerald-400 shadow-emerald-400/50' : 'bg-slate-500']"></span>
            </div>
            <div class="flex items-center gap-3 p-3 rounded-xl bg-white/[0.02]">
              <Radio class="w-4 h-4 text-cyan-400 flex-shrink-0" />
              <div class="flex-1">
                <p class="text-sm text-white">检索服务</p>
                <p class="text-[10px] text-slate-500">RAG 检索链路</p>
              </div>
              <router-link to="/admin/retrieval-logs" class="text-[10px] text-cyan-400 hover:text-cyan-300">监控<ArrowUpRight class="w-2.5 h-2.5 inline" /></router-link>
            </div>
            <div class="flex items-center gap-3 p-3 rounded-xl bg-white/[0.02]">
              <MessageSquare class="w-4 h-4 text-purple-400 flex-shrink-0" />
              <div class="flex-1">
                <p class="text-sm text-white">对话记录</p>
                <p class="text-[10px] text-slate-500">用户对话存档</p>
              </div>
              <router-link to="/admin/chat-logs" class="text-[10px] text-purple-400 hover:text-purple-300">查看<ArrowUpRight class="w-2.5 h-2.5 inline" /></router-link>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>
