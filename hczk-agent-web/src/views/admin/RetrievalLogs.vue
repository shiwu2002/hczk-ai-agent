<script setup>
import { ref, onMounted, computed } from 'vue'
import { useApiStore } from '@/stores/api'
import {
  Search, TrendingUp, Target, Clock, AlertTriangle,
  CheckCircle2, XCircle, Activity, Filter, ChevronLeft, ChevronRight,
  FileText, Bookmark, Hash
} from 'lucide-vue-next'

const api = useApiStore()

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

const strategyLabels = {
  hybrid: '混合检索',
  vector: '向量检索',
  keyword: '关键词检索',
  fallback: '兜底回复'
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
  try {
    detailParsed.value = JSON.parse(record.hitSources || '[]')
  } catch { detailParsed.value = [] }
}

function closeDetail() {
  detailRecord.value = null
  detailParsed.value = null
}

function formatTime(t) {
  if (!t) return '-'
  return new Date(t).toLocaleString('zh-CN')
}

// 趋势图最大值（用于柱状图高度计算）
const maxTrendTotal = computed(() => {
  return Math.max(...trend.value.map(p => p.total), 1)
})

onMounted(() => { loadAll() })
</script>

<template>
  <div class="p-6 space-y-6">
    <!-- 标题与筛选 -->
    <div class="flex items-center justify-between">
      <h1 class="text-2xl font-bold text-white flex items-center gap-2">
        <Activity class="w-6 h-6 text-emerald-400" /> RAG 检索监控
      </h1>
      <div class="flex items-center gap-3">
        <input v-model="filterAgentId" placeholder="智能体ID筛选" class="input-field w-48" @keyup.enter="applyFilter" />
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
        <button @click="applyFilter" class="btn-primary">
          <Filter class="w-4 h-4" /> 筛选
        </button>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-4">
      <div class="glass-card p-4">
        <div class="flex items-center gap-2 text-slate-400 text-xs mb-1">
          <Search class="w-4 h-4" /> 总检索次数
        </div>
        <div class="text-2xl font-bold text-white">{{ stats.total || 0 }}</div>
      </div>
      <div class="glass-card p-4">
        <div class="flex items-center gap-2 text-slate-400 text-xs mb-1">
          <CheckCircle2 class="w-4 h-4 text-emerald-400" /> 命中次数
        </div>
        <div class="text-2xl font-bold text-emerald-400">{{ stats.hitCount || 0 }}</div>
      </div>
      <div class="glass-card p-4">
        <div class="flex items-center gap-2 text-slate-400 text-xs mb-1">
          <XCircle class="w-4 h-4 text-red-400" /> 未命中
        </div>
        <div class="text-2xl font-bold text-red-400">{{ stats.missCount || 0 }}</div>
      </div>
      <div class="glass-card p-4">
        <div class="flex items-center gap-2 text-slate-400 text-xs mb-1">
          <Target class="w-4 h-4 text-blue-400" /> 命中率
        </div>
        <div class="text-2xl font-bold text-blue-400">{{ stats.hitRate || 0 }}%</div>
      </div>
      <div class="glass-card p-4">
        <div class="flex items-center gap-2 text-slate-400 text-xs mb-1">
          <Clock class="w-4 h-4 text-amber-400" /> 平均耗时
        </div>
        <div class="text-2xl font-bold text-amber-400">{{ stats.avgDurationMs || 0 }}ms</div>
      </div>
      <div class="glass-card p-4">
        <div class="flex items-center gap-2 text-slate-400 text-xs mb-1">
          <AlertTriangle class="w-4 h-4 text-orange-400" /> 降级次数
        </div>
        <div class="text-2xl font-bold text-orange-400">{{ stats.fallbackCount || 0 }}</div>
      </div>
    </div>

    <!-- 策略分布 -->
    <div v-if="stats.strategyDistribution" class="glass-card p-4">
      <h3 class="text-sm font-semibold text-white mb-3 flex items-center gap-2">
        <TrendingUp class="w-4 h-4 text-emerald-400" /> 检索策略分布
      </h3>
      <div class="flex flex-wrap gap-3">
        <div v-for="(count, strategy) in stats.strategyDistribution" :key="strategy"
             :class="['px-3 py-1.5 rounded-lg text-sm', strategyColors[strategy] || 'bg-white/5 text-slate-400']">
          {{ strategyLabels[strategy] || strategy }}: {{ count }}
        </div>
      </div>
    </div>

    <!-- 命中率趋势图 -->
    <div class="glass-card p-4">
      <div class="flex items-center justify-between mb-4">
        <h3 class="text-sm font-semibold text-white flex items-center gap-2">
          <TrendingUp class="w-4 h-4 text-emerald-400" /> 命中率趋势（最近 {{ trendDays }} 天）
        </h3>
        <select v-model="trendDays" class="input-field w-24" @change="loadTrend">
          <option :value="7">7天</option>
          <option :value="30">30天</option>
          <option :value="90">90天</option>
        </select>
      </div>
      <div v-if="trend.length" class="space-y-2">
        <div v-for="point in trend" :key="point.date" class="flex items-center gap-3">
          <span class="text-xs text-slate-500 w-24">{{ point.date }}</span>
          <div class="flex-1 flex items-center gap-2">
            <div class="flex-1 bg-white/5 rounded h-6 overflow-hidden relative">
              <div class="h-full bg-emerald-500/30 transition-all"
                   :style="{ width: (point.total / maxTrendTotal * 100) + '%' }"></div>
              <div class="absolute inset-0 flex items-center px-2 text-xs">
                <span class="text-slate-300">{{ point.total }} 次</span>
                <span class="ml-auto text-emerald-400">{{ point.hit }} 命中</span>
              </div>
            </div>
            <span :class="['text-xs font-medium w-12 text-right',
                          point.hitRate >= 70 ? 'text-emerald-400' : point.hitRate >= 40 ? 'text-amber-400' : 'text-red-400']">
              {{ point.hitRate }}%
            </span>
          </div>
        </div>
      </div>
      <div v-else class="text-center text-slate-500 py-8">暂无趋势数据</div>
    </div>

    <!-- 检索记录列表 -->
    <div class="glass-card p-4">
      <h3 class="text-sm font-semibold text-white mb-3 flex items-center gap-2">
        <Search class="w-4 h-4 text-emerald-400" /> 检索记录
      </h3>
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
                <span v-if="record.fallbackUsed" class="px-1.5 py-0.5 text-xs rounded bg-amber-500/10 text-amber-400">降级</span>
                <span class="text-xs text-slate-500">{{ record.hitCount }}/{{ record.topK }} 结果</span>
              </div>
              <p class="text-sm text-slate-300 truncate">{{ record.query }}</p>
              <div class="flex flex-wrap gap-3 mt-1 text-xs text-slate-500">
                <span>智能体: {{ record.agentId }}</span>
                <span>集合: {{ record.collectionName }}</span>
                <span>耗时: {{ record.durationMs }}ms</span>
                <span>置信度: {{ record.confidence }}</span>
                <span>{{ formatTime(record.createdAt) }}</span>
              </div>
            </div>
            <div class="text-right">
              <div class="text-xs text-slate-500">最高分</div>
              <div class="text-sm font-medium text-slate-300">{{ record.topScore?.toFixed(3) }}</div>
            </div>
          </div>
        </div>
      </div>
      <div v-else class="text-center text-slate-500 py-8">暂无检索记录</div>

      <!-- 分页 -->
      <div v-if="totalPages > 1" class="flex items-center justify-center gap-2 mt-4">
        <button @click="changePage(page - 1)" :disabled="page <= 1"
                class="p-1.5 rounded bg-white/5 text-slate-400 disabled:opacity-30 hover:bg-white/10">
          <ChevronLeft class="w-4 h-4" />
        </button>
        <span class="text-sm text-slate-400">{{ page }} / {{ totalPages }}</span>
        <button @click="changePage(page + 1)" :disabled="page >= totalPages"
                class="p-1.5 rounded bg-white/5 text-slate-400 disabled:opacity-30 hover:bg-white/10">
          <ChevronRight class="w-4 h-4" />
        </button>
      </div>
    </div>

    <!-- 详情弹窗 -->
    <div v-if="detailRecord" class="fixed inset-0 bg-black/60 z-50 flex items-center justify-center p-4" @click.self="closeDetail">
      <div class="glass-card max-w-2xl w-full max-h-[80vh] overflow-y-auto p-6">
        <div class="flex items-center justify-between mb-4">
          <h3 class="text-lg font-semibold text-white">检索详情</h3>
          <button @click="closeDetail" class="text-slate-400 hover:text-white">
            <XCircle class="w-5 h-5" />
          </button>
        </div>

        <div class="space-y-4">
          <div>
            <div class="text-xs text-slate-500 mb-1">查询内容</div>
            <p class="text-sm text-slate-300 bg-white/5 p-3 rounded-lg">{{ detailRecord.query }}</p>
          </div>

          <div class="grid grid-cols-2 gap-4">
            <div>
              <div class="text-xs text-slate-500 mb-1">检索策略</div>
              <span :class="['px-2 py-0.5 text-xs rounded', strategyColors[detailRecord.strategy]]">
                {{ strategyLabels[detailRecord.strategy] }}
              </span>
            </div>
            <div>
              <div class="text-xs text-slate-500 mb-1">命中状态</div>
              <span v-if="detailRecord.hitSuccess" class="text-emerald-400 text-sm">命中 ({{ detailRecord.hitCount }} 结果)</span>
              <span v-else class="text-red-400 text-sm">未命中</span>
            </div>
            <div>
              <div class="text-xs text-slate-500 mb-1">最高得分</div>
              <span class="text-slate-300 text-sm">{{ detailRecord.topScore?.toFixed(4) }}</span>
            </div>
            <div>
              <div class="text-xs text-slate-500 mb-1">置信度</div>
              <span class="text-slate-300 text-sm">{{ detailRecord.confidence }}</span>
            </div>
            <div>
              <div class="text-xs text-slate-500 mb-1">检索耗时</div>
              <span class="text-slate-300 text-sm">{{ detailRecord.durationMs }}ms</span>
            </div>
            <div>
              <div class="text-xs text-slate-500 mb-1">时间</div>
              <span class="text-slate-300 text-sm">{{ formatTime(detailRecord.createdAt) }}</span>
            </div>
          </div>

          <!-- 命中块溯源 -->
          <div v-if="detailParsed && detailParsed.length">
            <div class="text-xs text-slate-500 mb-2">命中块溯源信息</div>
            <div class="space-y-2">
              <div v-for="(src, i) in detailParsed" :key="i"
                   class="p-3 rounded-lg bg-white/5 border border-white/10">
                <div class="flex items-center gap-2 mb-1">
                  <span class="text-xs text-slate-400 font-mono">#{{ i + 1 }}</span>
                  <span class="text-xs text-slate-500">得分 {{ src.score?.toFixed(3) }}</span>
                  <span v-if="src.chunkType" class="px-1.5 py-0.5 text-xs rounded bg-white/5 text-slate-400">{{ src.chunkType }}</span>
                </div>
                <div class="flex flex-wrap gap-2">
                  <span v-if="src.source" class="inline-flex items-center gap-1 px-2 py-0.5 text-xs rounded bg-blue-500/10 text-blue-400">
                    <FileText class="w-3 h-3" /> {{ src.source }}
                  </span>
                  <span v-if="src.chapter" class="inline-flex items-center gap-1 px-2 py-0.5 text-xs rounded bg-purple-500/10 text-purple-400">
                    <Bookmark class="w-3 h-3" /> {{ src.chapter }}
                  </span>
                  <span v-if="src.page" class="inline-flex items-center gap-1 px-2 py-0.5 text-xs rounded bg-emerald-500/10 text-emerald-400">
                    <Hash class="w-3 h-3" /> 第 {{ src.page }} 页
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
