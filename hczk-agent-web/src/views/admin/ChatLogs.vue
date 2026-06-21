<!--
  对话记录管理页面（管理员）
  功能：按用户分组查看对话记录，支持筛选、详情查看、文档导出
  三级视图：用户列表 → 对话记录 → 文档预览/下载
-->
<script setup>
import { ref, onMounted, computed } from 'vue'
import { useApiStore } from '@/stores/api'
import { MessageSquare, Search, Eye, X, User, Cpu, Clock, Coins, Filter, FileText, Download, ChevronRight, ArrowLeft, Users, Zap, ChevronLeft } from 'lucide-vue-next'

const api = useApiStore()

// ========== 数据状态 ==========
const chatLogs = ref([])
const users = ref([])
const loading = ref(false)
const showDetail = ref(false)
const selectedLog = ref(null)

// ========== 视图状态 ==========
const viewMode = ref('users')
const selectedUser = ref(null)
const userLogs = ref([])

// ========== 筛选条件 ==========
const filterModel = ref('')
const filterStatus = ref('')
const searchQuery = ref('')

const modelOptions = computed(() => {
  const models = [...new Set(chatLogs.value.map(l => l.modelName).filter(Boolean))]
  return models.sort()
})

const userGroups = computed(() => {
  const map = {}
  for (const log of chatLogs.value) {
    if (!map[log.userId]) {
      map[log.userId] = { userId: log.userId, logs: [], totalInput: 0, totalOutput: 0, totalCost: 0 }
    }
    map[log.userId].logs.push(log)
    map[log.userId].totalInput += log.inputTokens || 0
    map[log.userId].totalOutput += log.outputTokens || 0
    map[log.userId].totalCost += Math.abs(Number(log.cost || 0))
  }
  return Object.values(map).sort((a, b) => b.logs.length - a.logs.length)
})

const filteredUserGroups = computed(() => {
  if (!searchQuery.value) return userGroups.value
  const q = searchQuery.value.toLowerCase()
  return userGroups.value.filter(g => (userMap.value[g.userId] || '').toLowerCase().includes(q))
})

const filteredUserLogs = computed(() => {
  let logs = userLogs.value
  if (filterModel.value) logs = logs.filter(l => l.modelName === filterModel.value)
  if (filterStatus.value) logs = logs.filter(l => l.status === filterStatus.value)
  return logs
})

const userModelOptions = computed(() => {
  const models = [...new Set(userLogs.value.map(l => l.modelName).filter(Boolean))]
  return models.sort()
})

// ========== 统计数据 ==========
const totalConversations = computed(() => chatLogs.value.length)
const totalUsers = computed(() => userGroups.value.length)
const totalTokens = computed(() => {
  return chatLogs.value.reduce((s, l) => s + (l.inputTokens || 0) + (l.outputTokens || 0), 0)
})
const totalCost = computed(() => chatLogs.value.reduce((s, l) => s + Math.abs(Number(l.cost || 0)), 0))

// ========== 数据加载 ==========
async function loadChatLogs() {
  loading.value = true
  try {
    const res = await api.get('/chat-logs')
    chatLogs.value = res.data?.data || res.data || []
  } catch (e) { console.error('加载对话记录失败:', e) } finally { loading.value = false }
}

async function loadUsers() {
  try {
    const res = await api.get('/users')
    const data = res.data?.data || res.data || []
    users.value = Array.isArray(data) ? data : []
  } catch (e) { console.error('加载用户列表失败:', e) }
}

const userMap = computed(() => {
  const map = {}
  for (const u of users.value) map[u.userId] = u.username || u.email || ('用户' + u.userId)
  return map
})

// ========== 视图切换 ==========
function clickUser(group) {
  selectedUser.value = { id: group.userId, name: userMap.value[group.userId] || ('用户' + group.userId) }
  userLogs.value = group.logs
  filterModel.value = ''
  filterStatus.value = ''
  currentPage.value = 1
  viewMode.value = 'records'
}

function viewDetail(log) { selectedLog.value = log; showDetail.value = true }
function backToUsers() { viewMode.value = 'users'; selectedUser.value = null; userLogs.value = [] }
function showPreview() { viewMode.value = 'preview' }
function backToRecords() { viewMode.value = 'records' }

// ========== 分页 ==========
const currentPage = ref(1)
const pageSize = 8
const totalPages = computed(() => Math.max(1, Math.ceil(filteredUserLogs.value.length / pageSize)))
const pagedLogs = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return filteredUserLogs.value.slice(start, start + pageSize)
})
function goToPage(p) { if (p >= 1 && p <= totalPages.value) currentPage.value = p }

// ========== 文档导出 ==========
const docContent = computed(() => {
  if (!selectedUser.value || !filteredUserLogs.value.length) return ''
  const logs = filteredUserLogs.value
  const userName = selectedUser.value.name
  let doc = `# ${userName} 对话记录\n\n`
  doc += `> 导出时间：${new Date().toLocaleString('zh-CN')}\n`
  doc += `> 共 ${logs.length} 条对话记录\n`
  doc += `> 总输入Token：${logs.reduce((s, l) => s + (l.inputTokens || 0), 0).toLocaleString()}\n`
  doc += `> 总输出Token：${logs.reduce((s, l) => s + (l.outputTokens || 0), 0).toLocaleString()}\n`
  doc += `> 总费用：¥${logs.reduce((s, l) => s + Math.abs(Number(l.cost || 0)), 0).toFixed(6)}\n\n---\n\n`
  logs.forEach((log, i) => {
    doc += `## 对话 ${i + 1}\n\n- **模型**：${log.modelName || '-'}\n- **时间**：${formatTime(log.createdAt)}\n- **输入Token**：${log.inputTokens || 0} | **输出Token**：${log.outputTokens || 0}\n- **费用**：${formatCost(log.cost)} | **耗时**：${formatDuration(log.durationMs)}\n- **状态**：${log.status === 'success' ? '成功' : '失败'}\n\n### 用户输入\n\n${log.inputContent || '(空)'}\n\n### AI 输出\n\n${log.outputContent || '(空)'}\n\n`
    if (log.errorMessage) doc += `### 错误信息\n\n${log.errorMessage}\n\n`
    doc += `---\n\n`
  })
  return doc
})

function downloadDoc() {
  const content = docContent.value
  if (!content) return
  const blob = new Blob([content], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url; a.download = `${selectedUser.value.name}_对话记录_${new Date().toISOString().slice(0, 10)}.md`; a.click()
  URL.revokeObjectURL(url)
}

// ========== 格式化函数 ==========
function formatTime(dateStr) { if (!dateStr) return '-'; return new Date(dateStr).toLocaleString('zh-CN') }
function formatCost(cost) { if (!cost) return '¥0.00'; return '¥' + Number(cost).toFixed(2) }
function formatDuration(ms) { if (!ms) return '-'; return ms < 1000 ? ms + 'ms' : (ms / 1000).toFixed(1) + 's' }
function truncate(text, len = 40) { if (!text) return '-'; return text.length > len ? text.substring(0, len) + '...' : text }
function relativeTime(dateStr) {
  if (!dateStr) return '-'
  const diff = Date.now() - new Date(dateStr).getTime()
  const mins = Math.floor(diff / 60000)
  if (mins < 1) return '刚刚'; if (mins < 60) return `${mins}分钟前`
  const hours = Math.floor(mins / 60); if (hours < 24) return `${hours}小时前`
  const days = Math.floor(hours / 24); if (days < 30) return `${days}天前`
  return formatTime(dateStr)
}
function fmtK(n) { return n >= 10000 ? (n / 1000).toFixed(1) + 'K' : n >= 1000 ? (n / 1000).toFixed(1) + 'K' : n }

function renderMarkdown(md) {
  if (!md) return ''
  let html = md.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  html = html.replace(/^### (.+)$/gm, '<h3>$1</h3>').replace(/^## (.+)$/gm, '<h2>$1</h2>').replace(/^# (.+)$/gm, '<h1>$1</h1>')
  html = html.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
  html = html.replace(/^&gt; (.+)$/gm, '<blockquote>$1</blockquote>')
  html = html.replace(/^---$/gm, '<hr>')
  html = html.replace(/^- (.+)$/gm, '<li>$1</li>')
  html = html.replace(/((?:<li>.*<\/li>\n?)+)/g, '<ul>$1</ul>')
  html = html.replace(/^(?!<[hubhrol])((?!<).+)$/gm, '<p>$1</p>')
  html = html.replace(/<p>\s*<\/p>/g, '')
  return html
}

onMounted(() => { loadChatLogs(); loadUsers() })
</script>

<template>
  <div class="space-y-5 animate-fade-in">
    <!-- 顶部操作栏 -->
    <div class="flex items-center justify-between">
      <div class="flex items-center gap-3">
        <button v-if="viewMode !== 'users'" @click="viewMode === 'records' ? backToUsers() : backToRecords()" class="p-2 rounded-xl hover:bg-white/5 text-slate-400 hover:text-white transition-colors">
          <ArrowLeft class="w-5 h-5" />
        </button>
        <div>
          <h2 class="text-lg font-semibold text-white">
            {{ viewMode === 'users' ? '对话记录' : viewMode === 'records' ? selectedUser?.name : '文档预览' }}
          </h2>
          <p v-if="viewMode === 'records'" class="text-slate-500 text-xs mt-0.5">{{ filteredUserLogs.length }} 条记录</p>
        </div>
      </div>
      <div class="flex items-center gap-2">
        <button v-if="viewMode === 'records'" @click="showPreview" class="px-4 py-2 rounded-xl bg-gradient-to-r from-emerald-500 to-cyan-500 text-white text-sm font-medium flex items-center gap-2 shadow-lg shadow-emerald-500/20 hover:shadow-emerald-500/35 transition-all">
          <FileText class="w-4 h-4" /> 整理成文档
        </button>
        <button v-if="viewMode === 'preview'" @click="downloadDoc" class="px-4 py-2 rounded-xl bg-gradient-to-r from-emerald-500 to-cyan-500 text-white text-sm font-medium flex items-center gap-2 shadow-lg shadow-emerald-500/20 hover:shadow-emerald-500/35 transition-all">
          <Download class="w-4 h-4" /> 下载文档
        </button>
        <button @click="loadChatLogs" class="px-3 py-2 rounded-xl border border-white/10 text-slate-400 hover:bg-white/5 hover:text-white transition-colors flex items-center gap-1.5 text-sm">
          <Search class="w-3.5 h-3.5" /> 刷新
        </button>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div v-if="viewMode === 'users'" class="grid grid-cols-2 lg:grid-cols-4 gap-3">
      <div class="glass-card rounded-xl p-3.5 flex items-center gap-3">
        <div class="w-9 h-9 rounded-lg bg-emerald-500/10 flex items-center justify-center flex-shrink-0"><MessageSquare class="w-4 h-4 text-emerald-400" /></div>
        <div><p class="text-[11px] text-slate-500">总对话</p><p class="text-lg font-bold text-white leading-tight">{{ totalConversations.toLocaleString() }}</p></div>
      </div>
      <div class="glass-card rounded-xl p-3.5 flex items-center gap-3">
        <div class="w-9 h-9 rounded-lg bg-cyan-500/10 flex items-center justify-center flex-shrink-0"><Users class="w-4 h-4 text-cyan-400" /></div>
        <div><p class="text-[11px] text-slate-500">用户数</p><p class="text-lg font-bold text-white leading-tight">{{ totalUsers }}</p></div>
      </div>
      <div class="glass-card rounded-xl p-3.5 flex items-center gap-3">
        <div class="w-9 h-9 rounded-lg bg-amber-500/10 flex items-center justify-center flex-shrink-0"><Zap class="w-4 h-4 text-amber-400" /></div>
        <div><p class="text-[11px] text-slate-500">总Token</p><p class="text-lg font-bold text-white leading-tight">{{ fmtK(totalTokens) }}</p></div>
      </div>
      <div class="glass-card rounded-xl p-3.5 flex items-center gap-3">
        <div class="w-9 h-9 rounded-lg bg-purple-500/10 flex items-center justify-center flex-shrink-0"><Coins class="w-4 h-4 text-purple-400" /></div>
        <div><p class="text-[11px] text-slate-500">总费用</p><p class="text-lg font-bold text-white leading-tight">¥{{ totalCost.toFixed(2) }}</p></div>
      </div>
    </div>

    <!-- 加载中 -->
    <div v-if="loading" class="flex items-center justify-center py-32">
      <div class="w-8 h-8 border-2 border-emerald-500/30 border-t-emerald-500 rounded-full animate-spin"></div>
    </div>

    <!-- ========== 用户列表视图 ========== -->
    <template v-else-if="viewMode === 'users'">
      <!-- 搜索 -->
      <div class="relative max-w-sm">
        <Search class="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-500" />
        <input v-model="searchQuery" class="w-full pl-9 pr-3 py-2 rounded-lg bg-white/[0.04] border border-white/[0.08] text-sm text-white placeholder-slate-600 focus:outline-none focus:border-emerald-500/40 focus:ring-1 focus:ring-emerald-500/20 transition-all" placeholder="搜索用户..." />
      </div>

      <div v-if="!filteredUserGroups.length" class="glass-card rounded-2xl text-center py-16">
        <MessageSquare class="w-10 h-10 text-slate-600 mx-auto mb-3" />
        <p class="text-sm text-slate-500">{{ searchQuery ? '未找到匹配用户' : '暂无对话记录' }}</p>
      </div>

      <!-- 用户列表 -->
      <div v-else class="glass-card rounded-2xl overflow-hidden">
        <!-- 表头 -->
        <div class="hidden md:grid grid-cols-12 gap-3 px-5 py-3 border-b border-white/[0.06] text-[11px] font-semibold text-slate-500 uppercase tracking-wider">
          <span class="col-span-4">用户</span>
          <span class="col-span-2 text-center">对话数</span>
          <span class="col-span-2 text-center">输入Token</span>
          <span class="col-span-2 text-center">输出Token</span>
          <span class="col-span-1 text-center">费用</span>
          <span class="col-span-1"></span>
        </div>
        <!-- 行 -->
        <div v-for="group in filteredUserGroups" :key="group.userId"
          @click="clickUser(group)"
          class="grid grid-cols-12 gap-3 items-center px-5 py-3.5 border-b border-white/[0.03] last:border-b-0 hover:bg-white/[0.02] cursor-pointer transition-colors group">
          <div class="col-span-4 flex items-center gap-3 min-w-0">
            <div class="w-8 h-8 rounded-lg bg-gradient-to-br from-emerald-500 to-cyan-500 flex items-center justify-center text-white text-xs font-bold flex-shrink-0">
              {{ (userMap[group.userId] || '?')[0] }}
            </div>
            <span class="text-sm text-white truncate">{{ userMap[group.userId] || ('用户' + group.userId) }}</span>
          </div>
          <div class="col-span-2 text-center">
            <span class="text-sm text-white font-medium">{{ group.logs.length }}</span>
          </div>
          <div class="col-span-2 text-center">
            <span class="text-xs text-cyan-400">{{ fmtK(group.totalInput) }}</span>
          </div>
          <div class="col-span-2 text-center">
            <span class="text-xs text-amber-400">{{ fmtK(group.totalOutput) }}</span>
          </div>
          <div class="col-span-1 text-center">
            <span class="text-xs text-emerald-400">¥{{ group.totalCost.toFixed(2) }}</span>
          </div>
          <div class="col-span-1 flex justify-end">
            <ChevronRight class="w-4 h-4 text-slate-600 group-hover:text-emerald-400 transition-colors" />
          </div>
        </div>
      </div>
    </template>

    <!-- ========== 对话记录视图 ========== -->
    <template v-else-if="viewMode === 'records'">
      <!-- 筛选栏 -->
      <div class="flex flex-wrap items-center gap-2">
        <Filter class="w-3.5 h-3.5 text-slate-500" />
        <select v-model="filterModel" class="px-3 py-1.5 rounded-lg bg-white/[0.04] border border-white/[0.08] text-xs text-white focus:outline-none focus:border-emerald-500/40 transition-all">
          <option value="">全部模型</option>
          <option v-for="m in userModelOptions" :key="m" :value="m">{{ m }}</option>
        </select>
        <div class="flex items-center gap-1.5">
          <button v-for="s in [{ label: '全部', value: '' }, { label: '成功', value: 'success' }, { label: '失败', value: 'failed' }]" :key="s.value"
            @click="filterStatus = s.value"
            :class="['px-3 py-1.5 text-xs rounded-lg transition-all', filterStatus === s.value ? 'bg-emerald-500/15 text-emerald-400 border border-emerald-500/20' : 'bg-white/[0.03] text-slate-400 border border-white/[0.06] hover:bg-white/[0.06]']">
            {{ s.label }}
          </button>
        </div>
        <span class="text-xs text-slate-500 ml-auto">{{ filteredUserLogs.length }} 条</span>
      </div>

      <!-- 记录列表 -->
      <div v-if="pagedLogs.length > 0" class="glass-card rounded-2xl overflow-hidden">
        <!-- 表头 -->
        <div class="hidden lg:grid grid-cols-12 gap-2 px-5 py-3 border-b border-white/[0.06] text-[11px] font-semibold text-slate-500 uppercase tracking-wider">
          <span class="col-span-2">模型</span>
          <span class="col-span-3">输入内容</span>
          <span class="col-span-1 text-center">Token</span>
          <span class="col-span-1 text-center">费用</span>
          <span class="col-span-1 text-center">耗时</span>
          <span class="col-span-1 text-center">状态</span>
          <span class="col-span-2 text-center">时间</span>
          <span class="col-span-1"></span>
        </div>
        <!-- 行 -->
        <div v-for="log in pagedLogs" :key="log.id"
          class="grid grid-cols-12 gap-2 items-center px-5 py-3 border-b border-white/[0.03] last:border-b-0 hover:bg-white/[0.02] transition-colors">
          <!-- 模型 -->
          <div class="col-span-2 min-w-0">
            <span class="text-xs text-cyan-400 font-medium truncate block">{{ log.modelName || '-' }}</span>
          </div>
          <!-- 输入内容 -->
          <div class="col-span-3 min-w-0">
            <span class="text-xs text-slate-400 truncate block" :title="log.inputContent">{{ truncate(log.inputContent, 35) }}</span>
          </div>
          <!-- Token -->
          <div class="col-span-1 text-center">
            <span class="text-[11px] text-slate-300">{{ fmtK((log.inputTokens || 0) + (log.outputTokens || 0)) }}</span>
          </div>
          <!-- 费用 -->
          <div class="col-span-1 text-center">
            <span class="text-xs text-emerald-400">{{ formatCost(log.cost) }}</span>
          </div>
          <!-- 耗时 -->
          <div class="col-span-1 text-center">
            <span class="text-xs text-slate-400">{{ formatDuration(log.durationMs) }}</span>
          </div>
          <!-- 状态 -->
          <div class="col-span-1 text-center">
            <span class="inline-flex items-center gap-1">
              <span class="w-1.5 h-1.5 rounded-full" :class="log.status === 'success' ? 'bg-emerald-400' : 'bg-red-400'"></span>
              <span class="text-[11px]" :class="log.status === 'success' ? 'text-emerald-400' : 'text-red-400'">{{ log.status === 'success' ? '成功' : '失败' }}</span>
            </span>
          </div>
          <!-- 时间 -->
          <div class="col-span-2 text-center">
            <span class="text-[11px] text-slate-500" :title="formatTime(log.createdAt)">{{ relativeTime(log.createdAt) }}</span>
          </div>
          <!-- 操作 -->
          <div class="col-span-1 flex justify-end">
            <button @click="viewDetail(log)" class="p-1.5 rounded-lg hover:bg-white/5 text-slate-500 hover:text-emerald-400 transition-all" title="查看详情">
              <Eye class="w-3.5 h-3.5" />
            </button>
          </div>
        </div>
      </div>

      <!-- 空状态 -->
      <div v-else class="glass-card rounded-2xl text-center py-16">
        <MessageSquare class="w-10 h-10 text-slate-600 mx-auto mb-3" />
        <p class="text-sm text-slate-500">暂无匹配的对话记录</p>
      </div>

      <!-- 分页 -->
      <div v-if="filteredUserLogs.length > pageSize" class="flex items-center justify-between">
        <span class="text-xs text-slate-500">{{ filteredUserLogs.length }} 条 · 第 {{ currentPage }}/{{ totalPages }} 页</span>
        <div class="flex items-center gap-1">
          <button @click="goToPage(currentPage - 1)" :disabled="currentPage === 1" class="p-1.5 rounded-lg bg-white/5 text-slate-400 hover:bg-white/10 disabled:opacity-25 disabled:cursor-not-allowed transition-all"><ChevronLeft class="w-4 h-4" /></button>
          <template v-for="p in totalPages" :key="p">
            <button v-show="totalPages <= 7 || Math.abs(p - currentPage) < 2 || p === 1 || p === totalPages" @click="goToPage(p)"
              :class="['w-7 h-7 rounded-lg text-xs font-medium transition-all', p === currentPage ? 'bg-emerald-500/15 text-emerald-400' : 'bg-white/5 text-slate-400 hover:bg-white/10']">{{ p }}</button>
          </template>
          <button @click="goToPage(currentPage + 1)" :disabled="currentPage === totalPages" class="p-1.5 rounded-lg bg-white/5 text-slate-400 hover:bg-white/10 disabled:opacity-25 disabled:cursor-not-allowed transition-all"><ChevronRight class="w-4 h-4" /></button>
        </div>
      </div>
    </template>

    <!-- ========== 文档预览视图 ========== -->
    <template v-else-if="viewMode === 'preview'">
      <div class="glass-card rounded-2xl p-6 lg:p-8 max-w-4xl mx-auto">
        <div v-html="renderMarkdown(docContent)" class="markdown-body"></div>
      </div>
    </template>

    <!-- ========== 对话详情弹窗 ========== -->
    <div v-if="showDetail && selectedLog" class="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4" @click.self="showDetail = false">
      <div class="glass-card rounded-2xl w-full max-w-3xl max-h-[85vh] overflow-y-auto">
        <!-- 头部 -->
        <div class="flex items-center justify-between p-5 border-b border-white/5 sticky top-0 bg-[#0a0f1a]/90 backdrop-blur-md z-10">
          <div class="flex items-center gap-3">
            <Cpu class="w-4 h-4 text-cyan-400" />
            <h3 class="text-base font-semibold text-white">{{ selectedLog.modelName || '对话详情' }}</h3>
            <span class="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-medium" :class="selectedLog.status === 'success' ? 'bg-emerald-500/10 text-emerald-400' : 'bg-red-500/10 text-red-400'">
              <span class="w-1.5 h-1.5 rounded-full" :class="selectedLog.status === 'success' ? 'bg-emerald-400' : 'bg-red-400'"></span>
              {{ selectedLog.status === 'success' ? '成功' : '失败' }}
            </span>
          </div>
          <button @click="showDetail = false" class="p-1.5 rounded-lg hover:bg-white/5 text-slate-400 hover:text-white transition-colors"><X class="w-4 h-4" /></button>
        </div>

        <!-- 信息条 -->
        <div class="flex items-center gap-4 px-5 py-3 border-b border-white/5 text-xs text-slate-400 bg-white/[0.01]">
          <span class="flex items-center gap-1.5"><Clock class="w-3 h-3 text-amber-400" />{{ formatDuration(selectedLog.durationMs) }}</span>
          <span class="flex items-center gap-1.5"><Coins class="w-3 h-3 text-emerald-400" />{{ formatCost(selectedLog.cost) }}</span>
          <span class="flex items-center gap-1.5"><User class="w-3 h-3 text-purple-400" />{{ userMap[selectedLog.userId] || selectedLog.userId }}</span>
          <span class="ml-auto text-slate-500">{{ formatTime(selectedLog.createdAt) }}</span>
        </div>

        <!-- Token 条 -->
        <div class="grid grid-cols-3 gap-3 px-5 py-3 border-b border-white/5">
          <div class="text-center p-2 rounded-lg bg-cyan-500/5">
            <p class="text-[10px] text-slate-500">输入</p>
            <p class="text-sm font-bold text-cyan-400">{{ (selectedLog.inputTokens || 0).toLocaleString() }}</p>
          </div>
          <div class="text-center p-2 rounded-lg bg-amber-500/5">
            <p class="text-[10px] text-slate-500">输出</p>
            <p class="text-sm font-bold text-amber-400">{{ (selectedLog.outputTokens || 0).toLocaleString() }}</p>
          </div>
          <div class="text-center p-2 rounded-lg bg-emerald-500/5">
            <p class="text-[10px] text-slate-500">合计</p>
            <p class="text-sm font-bold text-emerald-400">{{ ((selectedLog.inputTokens || 0) + (selectedLog.outputTokens || 0)).toLocaleString() }}</p>
          </div>
        </div>

        <!-- 用户输入 -->
        <div class="p-5 border-b border-white/5">
          <h4 class="text-xs font-medium text-slate-400 mb-2 flex items-center gap-1.5">
            <span class="w-1.5 h-1.5 rounded-full bg-cyan-400"></span> 用户输入
          </h4>
          <div class="bg-[#0d1117] rounded-lg p-3.5 text-xs text-slate-300 whitespace-pre-wrap break-words max-h-48 overflow-y-auto leading-relaxed">{{ selectedLog.inputContent || '(空)' }}</div>
        </div>

        <!-- AI 输出 -->
        <div class="p-5">
          <h4 class="text-xs font-medium text-slate-400 mb-2 flex items-center gap-1.5">
            <span class="w-1.5 h-1.5 rounded-full bg-emerald-400"></span> AI 输出
          </h4>
          <div class="bg-[#0d1117] rounded-lg p-3.5 text-xs text-slate-300 whitespace-pre-wrap break-words max-h-72 overflow-y-auto leading-relaxed">{{ selectedLog.outputContent || '(空)' }}</div>
        </div>

        <!-- 错误信息 -->
        <div v-if="selectedLog.errorMessage" class="px-5 pb-5">
          <h4 class="text-xs font-medium text-red-400 mb-2">错误信息</h4>
          <div class="bg-red-500/5 border border-red-500/10 rounded-lg p-3.5 text-xs text-red-300">{{ selectedLog.errorMessage }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.markdown-body :deep(h1) { font-size: 1.5rem; font-weight: 700; color: #fff; margin-bottom: 1rem; padding-bottom: 0.5rem; border-bottom: 1px solid rgba(255,255,255,0.1); }
.markdown-body :deep(h2) { font-size: 1.2rem; font-weight: 600; color: #e2e8f0; margin-top: 1.5rem; margin-bottom: 0.75rem; }
.markdown-body :deep(h3) { font-size: 1rem; font-weight: 600; color: #94a3b8; margin-top: 1rem; margin-bottom: 0.5rem; }
.markdown-body :deep(p) { color: #cbd5e1; margin-bottom: 0.5rem; line-height: 1.7; }
.markdown-body :deep(blockquote) { border-left: 3px solid rgba(16,185,129,0.3); padding-left: 1rem; margin: 0.75rem 0; color: #94a3b8; }
.markdown-body :deep(hr) { border-color: rgba(255,255,255,0.08); margin: 1.5rem 0; }
.markdown-body :deep(ul) { list-style: disc; padding-left: 1.5rem; color: #cbd5e1; }
.markdown-body :deep(li) { margin-bottom: 0.25rem; }
.markdown-body :deep(strong) { color: #e2e8f0; }
</style>
