<script setup>
import { ref, onMounted, computed } from 'vue'
import { useApiStore } from '@/stores/api'
import {
  Search, TrendingUp, Target, Clock, AlertTriangle,
  CheckCircle2, XCircle, Activity, Filter, ChevronLeft, ChevronRight,
  FileText, Bookmark, Hash, Database, Upload, Heart, ThumbsUp, ThumbsDown, AlertCircle
} from 'lucide-vue-next'

const api = useApiStore()

// Tab
const activeTab = ref('overview')

// 智能体列表
const agents = ref([])
const agentSearchKey = ref('')

// 统计数据
const stats = ref({})
const trend = ref([])
const loading = ref(false)

// 筛选
const filterAgentId = ref('')
const filterStrategy = ref('')
const filterHitSuccess = ref('')
const trendDays = ref(30)

// 分页
const records = ref([])
const page = ref(1)
const size = ref(20)
const total = ref(0)
const totalPages = ref(0)

// 详情
const detailRecord = ref(null)
const detailParsed = ref(null)

// 智能体排行
const agentRanking = ref([])

// 异常告警
const alerts = ref([])
const alertsTotal = ref(0)
const alertsPage = ref(1)

// 入库日志
const ingestLogs = ref([])
const ingestTotal = ref(0)
const ingestPage = ref(1)

// 服务健康
const serviceHealth = ref([])

// 过滤后的智能体列表
const filteredAgents = computed(() => {
  if (!agentSearchKey.value) return agents.value
  const key = agentSearchKey.value.toLowerCase()
  return agents.value.filter(a =>
    (a.name || '').toLowerCase().includes(key) ||
    (a.userId || '').toLowerCase().includes(key) ||
    (a.agentType || '').toLowerCase().includes(key)
  )
})

const strategyLabels = {
  hybrid: '混合检索', vector: '向量检索', keyword: '关键词检索', fallback: '兜底回复'
}
const strategyColors = {
  hybrid: 'text-emerald-400 bg-emerald-500/10',
  vector: 'text-blue-400 bg-blue-500/10',
  keyword: 'text-amber-400 bg-amber-500/10',
  fallback: 'text-red-400 bg-red-500/10'
}

async function loadStats() {
  try {
    const params = new URLSearchParams()
    if (filterAgentId.value) params.set('agentId', filterAgentId.value)
    const res = await api.get(`/retrieval-logs/stats?${params}`)
    if (res.code === 200) stats.value = res.data
  } catch (e) { console.error(e) }
}

async function loadTrend() {
  try {
    const params = new URLSearchParams()
    params.set('days', trendDays.value)
    if (filterAgentId.value) params.set('agentId', filterAgentId.value)
    const res = await api.get(`/retrieval-logs/trend?${params}`)
    if (res.code === 200) trend.value = res.data
  } catch (e) { console.error(e) }
}

async function loadRecords() {
  loading.value = true
  try {
    const params = new URLSearchParams()
    params.set('page', page.value)
    params.set('size', size.value)
    if (filterAgentId.value) params.set('agentId', filterAgentId.value)
    if (filterStrategy.value) params.set('strategy', filterStrategy.value)
    if (filterHitSuccess.value !== '') params.set('hitSuccess', filterHitSuccess.value)
    const res = await api.get(`/retrieval-logs?${params}`)
    if (res.code === 200) {
      records.value = res.data.records || []
      total.value = res.data.total || 0
      totalPages.value = res.data.pages || 0
    }
  } catch (e) { console.error(e) } finally { loading.value = false }
}

async function loadAll() {
  await Promise.all([loadStats(), loadTrend(), loadRecords()])
}

async function loadAgents() {
  try {
    const res = await api.get('/agents')
    if (res.code === 200 && Array.isArray(res.data)) {
      agents.value = res.data.filter(a => a.userId).sort((a, b) => (a.name || '').localeCompare(b.name || ''))
    }
  } catch (e) { console.error(e) }
}

async function loadAgentRanking() {
  try {
    const res = await api.get('/retrieval-logs/agent-ranking?days=7&limit=20')
    if (res.code === 200) agentRanking.value = res.data
  } catch (e) { console.error(e) }
}

async function loadAlerts() {
  try {
    const res = await api.get(`/retrieval-logs/alerts?page=${alertsPage.value}&size=20`)
    if (res.code === 200) {
      alerts.value = res.data.records || []
      alertsTotal.value = res.data.total || 0
    }
  } catch (e) { console.error(e) }
}

async function loadIngestLogs() {
  try {
    const res = await api.get(`/retrieval-logs/ingest-logs?page=${ingestPage.value}&size=20`)
    if (res.code === 200) {
      ingestLogs.value = res.data.records || []
      ingestTotal.value = res.data.total || 0
    }
  } catch (e) { console.error(e) }
}

async function loadServiceHealth() {
  try {
    const res = await api.get('/retrieval-logs/service-health?hours=24')
    if (res.code === 200) serviceHealth.value = res.data.services || []
  } catch (e) { console.error(e) }
}

async function adoptFeedback(id, adopted) {
  try {
    await api.post(`/retrieval-logs/${id}/adopt?adopted=${adopted}`)
    // 更新本地状态
    const r = records.value.find(r => r.id === id)
    if (r) r.adopted = adopted
    if (detailRecord.value && detailRecord.value.id === id) detailRecord.value.adopted = adopted
  } catch (e) { console.error(e) }
}

function applyFilter() {
  page.value = 1
  loadAll()
}

function changePage(p) {
  if (p < 1 || p > totalPages.value) return
  page.value = p
  loadRecords()
}

function viewDetail(record) {
  detailRecord.value = record
  try { detailParsed.value = JSON.parse(record.hitSources || '[]') } catch { detailParsed.value = [] }
}

function closeDetail() {
  detailRecord.value = null
  detailParsed.value = null
}

function formatTime(t) {
  if (!t) return '-'
  return new Date(t).toLocaleString('zh-CN')
}

function getAgentName(agentId) {
  const a = agents.value.find(a => a.userId === agentId)
  return a ? a.name : agentId
}

const maxTrendTotal = computed(() => Math.max(...trend.value.map(p => p.total), 1))

const tabs = [
  { key: 'overview', name: '检索概览', icon: Activity },
  { key: 'records', name: '检索记录', icon: Search },
  { key: 'ranking', name: '智能体排行', icon: TrendingUp },
  { key: 'alerts', name: '异常告警', icon: AlertTriangle },
  { key: 'ingest', name: '入库日志', icon: Upload },
  { key: 'health', name: '服务健康', icon: Heart }
]

onMounted(() => {
  loadAgents()
  loadAll()
  loadAgentRanking()
  loadAlerts()
  loadIngestLogs()
  loadServiceHealth()
})
</script>

<template>
  <div class="p-6 space-y-6">
    <!-- 标题与Tab -->
    <div class="flex items-center justify-between">
      <h1 class="text-2xl font-bold text-white flex items-center gap-2">
        <Activity class="w-6 h-6 text-emerald-400" /> RAG 检索监控
      </h1>
    </div>

    <!-- Tab 导航 -->
    <div class="flex gap-1 bg-white/5 p-1 rounded-lg">
      <button v-for="tab in tabs" :key="tab.key"
              @click="activeTab = tab.key"
              :class="['flex items-center gap-1.5 px-3 py-2 rounded-md text-sm transition',
                       activeTab === tab.key ? 'bg-emerald-500/20 text-emerald-400' : 'text-slate-400 hover:text-white hover:bg-white/5']">
        <component :is="tab.icon" class="w-4 h-4" /> {{ tab.name }}
      </button>
    </div>

    <!-- ==================== 检索概览 Tab ==================== -->
    <div v-if="activeTab === 'overview'" class="space-y-6 animate-fade-in">
      <!-- 筛选 -->
      <div class="flex items-center gap-3">
        <select v-model="filterAgentId" class="input-field w-56" @change="applyFilter">
          <option value="">全部智能体</option>
          <option v-for="agent in filteredAgents" :key="agent.userId" :value="agent.userId">
            {{ agent.name }}（{{ agent.agentType || '通用' }}）
          </option>
        </select>
        <input v-if="agents.length > 10" v-model="agentSearchKey" placeholder="搜索智能体..." class="input-field w-40" />
        <select v-model="trendDays" class="input-field w-24" @change="loadTrend">
          <option :value="7">7天</option>
          <option :value="30">30天</option>
          <option :value="90">90天</option>
        </select>
      </div>

      <!-- 统计卡片 -->
      <div class="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-8 gap-3">
        <div class="glass-card p-3">
          <div class="text-xs text-slate-500 mb-1">总检索</div>
          <div class="text-xl font-bold text-white">{{ stats.total || 0 }}</div>
        </div>
        <div class="glass-card p-3">
          <div class="text-xs text-slate-500 mb-1">命中</div>
          <div class="text-xl font-bold text-emerald-400">{{ stats.hitCount || 0 }}</div>
        </div>
        <div class="glass-card p-3">
          <div class="text-xs text-slate-500 mb-1">命中率</div>
          <div class="text-xl font-bold text-blue-400">{{ stats.hitRate || 0 }}%</div>
        </div>
        <div class="glass-card p-3">
          <div class="text-xs text-slate-500 mb-1">平均得分</div>
          <div class="text-xl font-bold text-purple-400">{{ stats.avgScore || 0 }}</div>
        </div>
        <div class="glass-card p-3">
          <div class="text-xs text-slate-500 mb-1">平均距离</div>
          <div class="text-xl font-bold text-cyan-400">{{ stats.avgDistance || 0 }}</div>
        </div>
        <div class="glass-card p-3">
          <div class="text-xs text-slate-500 mb-1">平均耗时</div>
          <div class="text-xl font-bold text-amber-400">{{ stats.avgDurationMs || 0 }}ms</div>
        </div>
        <div class="glass-card p-3">
          <div class="text-xs text-slate-500 mb-1">降级</div>
          <div class="text-xl font-bold text-orange-400">{{ stats.fallbackCount || 0 }}</div>
        </div>
        <div class="glass-card p-3">
          <div class="text-xs text-slate-500 mb-1">采纳率</div>
          <div class="text-xl font-bold text-emerald-400">
            {{ stats.adoptedCount && (stats.adoptedCount + (stats.rejectedCount || 0)) > 0
              ? Math.round(stats.adoptedCount / (stats.adoptedCount + (stats.rejectedCount || 0)) * 100) : '-' }}%
          </div>
        </div>
      </div>

      <!-- 策略 & 块类型分布 -->
      <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div class="glass-card p-4">
          <h3 class="text-sm font-semibold text-white mb-3">策略分布</h3>
          <div class="flex flex-wrap gap-2">
            <div v-for="(count, strategy) in stats.strategyDistribution" :key="strategy"
                 :class="['px-3 py-1.5 rounded-lg text-sm', strategyColors[strategy] || 'bg-white/5 text-slate-400']">
              {{ strategyLabels[strategy] || strategy }}: {{ count }}
            </div>
          </div>
        </div>
        <div class="glass-card p-4">
          <h3 class="text-sm font-semibold text-white mb-3">命中块类型分布</h3>
          <div class="flex flex-wrap gap-2">
            <div v-for="(count, type) in stats.chunkTypeDistribution" :key="type"
                 class="px-3 py-1.5 rounded-lg text-sm bg-white/5 text-slate-300">
              {{ type }}: {{ count }}
            </div>
          </div>
        </div>
      </div>

      <!-- 命中率趋势 -->
      <div class="glass-card p-4">
        <h3 class="text-sm font-semibold text-white mb-4 flex items-center gap-2">
          <TrendingUp class="w-4 h-4 text-emerald-400" /> 命中率 & 得分趋势（{{ trendDays }}天）
        </h3>
        <div v-if="trend.length" class="space-y-2">
          <div v-for="point in trend" :key="point.date" class="flex items-center gap-3">
            <span class="text-xs text-slate-500 w-24">{{ point.date }}</span>
            <div class="flex-1 bg-white/5 rounded h-6 overflow-hidden relative">
              <div class="h-full bg-emerald-500/30 transition-all" :style="{ width: (point.total / maxTrendTotal * 100) + '%' }"></div>
              <div class="absolute inset-0 flex items-center px-2 text-xs">
                <span class="text-slate-300">{{ point.total }}次</span>
                <span class="ml-2 text-emerald-400">{{ point.hit }}命中</span>
                <span class="ml-auto text-purple-400">得分{{ point.avgScore }}</span>
              </div>
            </div>
            <span :class="['text-xs font-medium w-12 text-right',
                          point.hitRate >= 70 ? 'text-emerald-400' : point.hitRate >= 40 ? 'text-amber-400' : 'text-red-400']">
              {{ point.hitRate }}%
            </span>
          </div>
        </div>
        <div v-else class="text-center text-slate-500 py-8">暂无趋势数据</div>
      </div>
    </div>

    <!-- ==================== 检索记录 Tab ==================== -->
    <div v-if="activeTab === 'records'" class="space-y-4 animate-fade-in">
      <div class="flex items-center gap-3">
        <select v-model="filterAgentId" class="input-field w-56" @change="applyFilter">
          <option value="">全部智能体</option>
          <option v-for="agent in filteredAgents" :key="agent.userId" :value="agent.userId">
            {{ agent.name }}（{{ agent.agentType || '通用' }}）
          </option>
        </select>
        <select v-model="filterStrategy" class="input-field w-32" @change="applyFilter">
          <option value="">全部策略</option>
          <option value="hybrid">混合检索</option>
          <option value="vector">向量检索</option>
          <option value="keyword">关键词检索</option>
          <option value="fallback">兜底回复</option>
        </select>
        <select v-model="filterHitSuccess" class="input-field w-32" @change="applyFilter">
          <option value="">全部状态</option>
          <option value="true">已命中</option>
          <option value="false">未命中</option>
        </select>
      </div>

      <div v-if="loading" class="text-center text-slate-500 py-8">加载中...</div>
      <div v-else-if="records.length" class="space-y-2">
        <div v-for="record in records" :key="record.id"
             class="p-3 rounded-lg bg-white/5 border border-white/10 hover:border-white/20 cursor-pointer transition"
             @click="viewDetail(record)">
          <div class="flex items-start justify-between gap-3">
            <div class="flex-1 min-w-0">
              <div class="flex items-center gap-2 mb-1">
                <span :class="['px-1.5 py-0.5 text-xs rounded', strategyColors[record.strategy] || 'bg-white/5 text-slate-400']">
                  {{ strategyLabels[record.strategy] || record.strategy }}
                </span>
                <span v-if="record.hitSuccess" class="px-1.5 py-0.5 text-xs rounded bg-emerald-500/10 text-emerald-400">命中</span>
                <span v-else class="px-1.5 py-0.5 text-xs rounded bg-red-500/10 text-red-400">未命中</span>
                <span class="text-xs text-slate-500">{{ record.hitCount }}/{{ record.topK }}</span>
                <span class="text-xs text-purple-400">得分 {{ record.avgScore?.toFixed(3) }}</span>
                <span class="text-xs text-cyan-400">距离 {{ record.avgDistance?.toFixed(3) }}</span>
              </div>
              <p class="text-sm text-slate-300 truncate">{{ record.query }}</p>
              <div class="flex flex-wrap gap-3 mt-1 text-xs text-slate-500">
                <span>{{ getAgentName(record.agentId) }}</span>
                <span>{{ record.collectionName }}</span>
                <span>{{ record.durationMs }}ms</span>
                <span>{{ formatTime(record.createdAt) }}</span>
              </div>
            </div>
            <div class="flex items-center gap-1">
              <button v-if="record.adopted === null" @click.stop="adoptFeedback(record.id, true)"
                      class="p-1 rounded hover:bg-emerald-500/10 text-slate-500 hover:text-emerald-400" title="采纳">
                <ThumbsUp class="w-4 h-4" />
              </button>
              <button v-if="record.adopted === null" @click.stop="adoptFeedback(record.id, false)"
                      class="p-1 rounded hover:bg-red-500/10 text-slate-500 hover:text-red-400" title="拒绝">
                <ThumbsDown class="w-4 h-4" />
              </button>
              <span v-if="record.adopted === true" class="text-emerald-400"><ThumbsUp class="w-4 h-4" /></span>
              <span v-if="record.adopted === false" class="text-red-400"><ThumbsDown class="w-4 h-4" /></span>
            </div>
          </div>
        </div>
      </div>
      <div v-else class="text-center text-slate-500 py-8">暂无检索记录</div>

      <!-- 分页 -->
      <div v-if="totalPages > 1" class="flex items-center justify-center gap-2">
        <button @click="changePage(page - 1)" :disabled="page <= 1" class="p-1.5 rounded bg-white/5 text-slate-400 disabled:opacity-30 hover:bg-white/10">
          <ChevronLeft class="w-4 h-4" />
        </button>
        <span class="text-sm text-slate-400">{{ page }} / {{ totalPages }}</span>
        <button @click="changePage(page + 1)" :disabled="page >= totalPages" class="p-1.5 rounded bg-white/5 text-slate-400 disabled:opacity-30 hover:bg-white/10">
          <ChevronRight class="w-4 h-4" />
        </button>
      </div>
    </div>

    <!-- ==================== 智能体排行 Tab ==================== -->
    <div v-if="activeTab === 'ranking'" class="animate-fade-in">
      <div class="glass-card overflow-hidden">
        <table class="w-full text-sm">
          <thead>
            <tr class="border-b border-white/10 text-slate-400 text-xs">
              <th class="p-3 text-left">智能体</th>
              <th class="p-3 text-right">检索次数</th>
              <th class="p-3 text-right">命中次数</th>
              <th class="p-3 text-right">命中率</th>
              <th class="p-3 text-right">平均得分</th>
              <th class="p-3 text-right">平均耗时</th>
              <th class="p-3 text-right">降级次数</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(item, i) in agentRanking" :key="item.agentId" class="border-b border-white/5 hover:bg-white/5">
              <td class="p-3 text-slate-300">
                <span class="text-slate-500 mr-2">#{{ i + 1 }}</span>
                {{ getAgentName(item.agentId) }}
              </td>
              <td class="p-3 text-right text-white">{{ item.total }}</td>
              <td class="p-3 text-right text-emerald-400">{{ item.hitCount }}</td>
              <td class="p-3 text-right" :class="item.hitRate >= 70 ? 'text-emerald-400' : item.hitRate >= 40 ? 'text-amber-400' : 'text-red-400'">
                {{ item.hitRate }}%
              </td>
              <td class="p-3 text-right text-purple-400">{{ item.avgScore }}</td>
              <td class="p-3 text-right text-amber-400">{{ item.avgDurationMs }}ms</td>
              <td class="p-3 text-right text-orange-400">{{ item.fallbackCount }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- ==================== 异常告警 Tab ==================== -->
    <div v-if="activeTab === 'alerts'" class="space-y-4 animate-fade-in">
      <div class="flex items-center gap-2 text-sm text-slate-400">
        <AlertCircle class="w-4 h-4 text-amber-400" /> 最近 24 小时异常检索（共 {{ alertsTotal }} 条）
      </div>
      <div v-if="alerts.length" class="space-y-2">
        <div v-for="alert in alerts" :key="alert.id"
             class="p-3 rounded-lg border" :class="alert.fallbackUsed ? 'bg-red-500/5 border-red-500/20' : 'bg-amber-500/5 border-amber-500/20'">
          <div class="flex items-center gap-2 mb-1">
            <span :class="['px-1.5 py-0.5 text-xs rounded', alert.fallbackUsed ? 'bg-red-500/10 text-red-400' : 'bg-amber-500/10 text-amber-400']">
              {{ alert.alertType }}
            </span>
            <span class="text-xs text-slate-500">{{ getAgentName(alert.agentId) }}</span>
            <span class="text-xs text-slate-500">{{ formatTime(alert.createdAt) }}</span>
          </div>
          <p class="text-sm text-slate-300 truncate">{{ alert.query }}</p>
        </div>
      </div>
      <div v-else class="text-center text-emerald-400 py-8">最近 24 小时无异常，一切正常</div>
    </div>

    <!-- ==================== 入库日志 Tab ==================== -->
    <div v-if="activeTab === 'ingest'" class="space-y-4 animate-fade-in">
      <div class="glass-card overflow-hidden">
        <table class="w-full text-sm">
          <thead>
            <tr class="border-b border-white/10 text-slate-400 text-xs">
              <th class="p-3 text-left">智能体</th>
              <th class="p-3 text-left">来源</th>
              <th class="p-3 text-center">类型</th>
              <th class="p-3 text-right">页数</th>
              <th class="p-3 text-right">分块数</th>
              <th class="p-3 text-center">OCR</th>
              <th class="p-3 text-center">LLM预处理</th>
              <th class="p-3 text-right">LLM耗时</th>
              <th class="p-3 text-right">总耗时</th>
              <th class="p-3 text-center">状态</th>
              <th class="p-3 text-right">时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="log in ingestLogs" :key="log.id" class="border-b border-white/5 hover:bg-white/5">
              <td class="p-3 text-slate-300">{{ getAgentName(log.agentId) }}</td>
              <td class="p-3 text-slate-400 max-w-32 truncate">{{ log.source || '-' }}</td>
              <td class="p-3 text-center text-slate-400">{{ log.fileType || '-' }}</td>
              <td class="p-3 text-right text-white">{{ log.totalPages || 0 }}</td>
              <td class="p-3 text-right text-white">{{ log.totalChunks || 0 }}</td>
              <td class="p-3 text-center">
                <span v-if="log.ocrUsed" class="text-amber-400">是</span>
                <span v-else class="text-slate-500">否</span>
              </td>
              <td class="p-3 text-center">
                <span v-if="log.llmPreprocessUsed" class="text-emerald-400">是</span>
                <span v-else class="text-slate-500">否</span>
              </td>
              <td class="p-3 text-right text-slate-400">{{ log.llmPreprocessMs ? log.llmPreprocessMs + 'ms' : '-' }}</td>
              <td class="p-3 text-right text-slate-400">{{ log.ingestDurationMs ? log.ingestDurationMs + 'ms' : '-' }}</td>
              <td class="p-3 text-center">
                <span v-if="log.status === 'success'" class="text-emerald-400">成功</span>
                <span v-else class="text-red-400">{{ log.status }}</span>
              </td>
              <td class="p-3 text-right text-slate-500 text-xs">{{ formatTime(log.createdAt) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- ==================== 服务健康 Tab ==================== -->
    <div v-if="activeTab === 'health'" class="space-y-4 animate-fade-in">
      <div class="text-sm text-slate-400">最近 24 小时服务调用健康度</div>
      <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <div v-for="svc in serviceHealth" :key="svc.serviceName" class="glass-card p-4">
          <div class="flex items-center justify-between mb-3">
            <span class="text-sm font-semibold text-white">{{ svc.serviceName }}</span>
            <span :class="['text-xs px-2 py-0.5 rounded', svc.successRate >= 99 ? 'bg-emerald-500/10 text-emerald-400' : svc.successRate >= 90 ? 'bg-amber-500/10 text-amber-400' : 'bg-red-500/10 text-red-400']">
              {{ svc.successRate }}%
            </span>
          </div>
          <div class="space-y-1 text-xs text-slate-400">
            <div class="flex justify-between"><span>总调用</span><span class="text-white">{{ svc.totalCalls }}</span></div>
            <div class="flex justify-between"><span>成功</span><span class="text-emerald-400">{{ svc.successCalls }}</span></div>
            <div class="flex justify-between"><span>失败</span><span class="text-red-400">{{ svc.failCalls }}</span></div>
            <div class="flex justify-between"><span>平均耗时</span><span class="text-amber-400">{{ svc.avgDurationMs }}ms</span></div>
          </div>
        </div>
      </div>
      <div v-if="!serviceHealth.length" class="text-center text-slate-500 py-8">暂无服务健康数据</div>
    </div>

    <!-- 详情弹窗 -->
    <div v-if="detailRecord" class="fixed inset-0 bg-black/60 z-50 flex items-center justify-center p-4" @click.self="closeDetail">
      <div class="glass-card max-w-2xl w-full max-h-[80vh] overflow-y-auto p-6">
        <div class="flex items-center justify-between mb-4">
          <h3 class="text-lg font-semibold text-white">检索详情</h3>
          <button @click="closeDetail" class="text-slate-400 hover:text-white"><XCircle class="w-5 h-5" /></button>
        </div>
        <div class="space-y-4">
          <div>
            <div class="text-xs text-slate-500 mb-1">查询内容</div>
            <p class="text-sm text-slate-300 bg-white/5 p-3 rounded-lg">{{ detailRecord.query }}</p>
          </div>
          <div class="grid grid-cols-2 gap-4">
            <div><div class="text-xs text-slate-500 mb-1">策略</div><span :class="['px-2 py-0.5 text-xs rounded', strategyColors[detailRecord.strategy]]">{{ strategyLabels[detailRecord.strategy] }}</span></div>
            <div><div class="text-xs text-slate-500 mb-1">命中</div><span v-if="detailRecord.hitSuccess" class="text-emerald-400">{{ detailRecord.hitCount }} 结果</span><span v-else class="text-red-400">未命中</span></div>
            <div><div class="text-xs text-slate-500 mb-1">得分范围</div><span class="text-slate-300 text-sm">{{ detailRecord.minScore?.toFixed(3) }} ~ {{ detailRecord.topScore?.toFixed(3) }} (avg {{ detailRecord.avgScore?.toFixed(3) }})</span></div>
            <div><div class="text-xs text-slate-500 mb-1">平均距离</div><span class="text-cyan-400 text-sm">{{ detailRecord.avgDistance?.toFixed(4) }}</span></div>
            <div><div class="text-xs text-slate-500 mb-1">耗时</div><span class="text-slate-300 text-sm">{{ detailRecord.durationMs }}ms</span></div>
            <div><div class="text-xs text-slate-500 mb-1">时间</div><span class="text-slate-300 text-sm">{{ formatTime(detailRecord.createdAt) }}</span></div>
          </div>
          <!-- 块类型分布 -->
          <div v-if="detailRecord.chunkTypeDist && detailRecord.chunkTypeDist !== '{}'">
            <div class="text-xs text-slate-500 mb-1">命中块类型分布</div>
            <div class="flex gap-2">
              <span v-for="(count, type) in (() => { try { return JSON.parse(detailRecord.chunkTypeDist) } catch { return {} } })()" :key="type"
                    class="px-2 py-0.5 text-xs rounded bg-white/5 text-slate-300">{{ type }}: {{ count }}</span>
            </div>
          </div>
          <!-- 采纳反馈 -->
          <div>
            <div class="text-xs text-slate-500 mb-1">采纳反馈</div>
            <div class="flex gap-2">
              <button @click="adoptFeedback(detailRecord.id, true)"
                      :class="['px-3 py-1 rounded text-sm', detailRecord.adopted === true ? 'bg-emerald-500/20 text-emerald-400' : 'bg-white/5 text-slate-400 hover:text-emerald-400']">
                <ThumbsUp class="w-4 h-4 inline" /> 采纳
              </button>
              <button @click="adoptFeedback(detailRecord.id, false)"
                      :class="['px-3 py-1 rounded text-sm', detailRecord.adopted === false ? 'bg-red-500/20 text-red-400' : 'bg-white/5 text-slate-400 hover:text-red-400']">
                <ThumbsDown class="w-4 h-4 inline" /> 拒绝
              </button>
            </div>
          </div>
          <!-- 命中块溯源 -->
          <div v-if="detailParsed && detailParsed.length">
            <div class="text-xs text-slate-500 mb-2">命中块溯源</div>
            <div class="space-y-2">
              <div v-for="(src, i) in detailParsed" :key="i" class="p-3 rounded-lg bg-white/5 border border-white/10">
                <div class="flex items-center gap-2 mb-1">
                  <span class="text-xs text-slate-400 font-mono">#{{ i + 1 }}</span>
                  <span class="text-xs text-slate-500">得分 {{ src.score?.toFixed(3) }}</span>
                  <span v-if="src.chunkType" class="px-1.5 py-0.5 text-xs rounded bg-white/5 text-slate-400">{{ src.chunkType }}</span>
                </div>
                <div class="flex flex-wrap gap-2">
                  <span v-if="src.source" class="inline-flex items-center gap-1 px-2 py-0.5 text-xs rounded bg-blue-500/10 text-blue-400"><FileText class="w-3 h-3" /> {{ src.source }}</span>
                  <span v-if="src.chapter" class="inline-flex items-center gap-1 px-2 py-0.5 text-xs rounded bg-purple-500/10 text-purple-400"><Bookmark class="w-3 h-3" /> {{ src.chapter }}</span>
                  <span v-if="src.page" class="inline-flex items-center gap-1 px-2 py-0.5 text-xs rounded bg-emerald-500/10 text-emerald-400"><Hash class="w-3 h-3" /> 第 {{ src.page }} 页</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
