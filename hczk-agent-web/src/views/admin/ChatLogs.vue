<script setup>
import { ref, onMounted, computed } from 'vue'
import { useApiStore } from '@/stores/api'
import { MessageSquare, Search, Eye, X, User, Cpu, Clock, Coins, Filter, FileText, Download, ChevronRight, ArrowLeft } from 'lucide-vue-next'

const api = useApiStore()
const chatLogs = ref([])
const users = ref([])
const loading = ref(false)
const showDetail = ref(false)
const selectedLog = ref(null)

// View state: 'users' | 'records' | 'preview'
const viewMode = ref('users')
const selectedUser = ref(null)
const userLogs = ref([])

// Filters
const filterModel = ref('')
const filterStatus = ref('')

const modelOptions = computed(() => {
  const models = [...new Set(chatLogs.value.map(l => l.modelName).filter(Boolean))]
  return models.sort()
})

// Group logs by user
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

// Filtered user logs
const filteredUserLogs = computed(() => {
  let logs = userLogs.value
  if (filterModel.value) logs = logs.filter(l => l.modelName === filterModel.value)
  if (filterStatus.value) logs = logs.filter(l => l.status === filterStatus.value)
  return logs
})

// User model options (for current user's logs)
const userModelOptions = computed(() => {
  const models = [...new Set(userLogs.value.map(l => l.modelName).filter(Boolean))]
  return models.sort()
})

async function loadChatLogs() {
  loading.value = true
  try {
    const res = await api.get('/chat-logs')
    chatLogs.value = res.data?.data || res.data || []
  } catch (e) {
    console.error('加载对话记录失败:', e)
  } finally {
    loading.value = false
  }
}

async function loadUsers() {
  try {
    const res = await api.get('/users')
    const data = res.data?.data || res.data || []
    users.value = Array.isArray(data) ? data : []
  } catch (e) {
    console.error('加载用户列表失败:', e)
  }
}

const userMap = computed(() => {
  const map = {}
  for (const u of users.value) {
    map[u.userId] = u.username || u.email || ('用户' + u.userId)
  }
  return map
})

function clickUser(group) {
  selectedUser.value = { id: group.userId, name: userMap.value[group.userId] || ('用户' + group.userId) }
  userLogs.value = group.logs
  filterModel.value = ''
  filterStatus.value = ''
  viewMode.value = 'records'
}

function viewDetail(log) {
  selectedLog.value = log
  showDetail.value = true
}

function backToUsers() {
  viewMode.value = 'users'
  selectedUser.value = null
  userLogs.value = []
}

// Generate document text
const docContent = computed(() => {
  if (!selectedUser.value || !filteredUserLogs.value.length) return ''
  const logs = filteredUserLogs.value
  const userName = selectedUser.value.name
  let doc = `# ${userName} 对话记录\n\n`
  doc += `> 导出时间：${new Date().toLocaleString('zh-CN')}\n`
  doc += `> 共 ${logs.length} 条对话记录\n`
  doc += `> 总输入Token：${logs.reduce((s, l) => s + (l.inputTokens || 0), 0).toLocaleString()}\n`
  doc += `> 总输出Token：${logs.reduce((s, l) => s + (l.outputTokens || 0), 0).toLocaleString()}\n`
  doc += `> 总费用：¥${logs.reduce((s, l) => s + Math.abs(Number(l.cost || 0)), 0).toFixed(6)}\n\n`
  doc += `---\n\n`

  logs.forEach((log, i) => {
    doc += `## 对话 ${i + 1}\n\n`
    doc += `- **模型**：${log.modelName || '-'}\n`
    doc += `- **时间**：${formatTime(log.createdAt)}\n`
    doc += `- **输入Token**：${log.inputTokens || 0} | **输出Token**：${log.outputTokens || 0}\n`
    doc += `- **费用**：${formatCost(log.cost)} | **耗时**：${formatDuration(log.durationMs)}\n`
    doc += `- **状态**：${log.status === 'success' ? '成功' : '失败'}\n\n`
    doc += `### 用户输入\n\n${log.inputContent || '(空)'}\n\n`
    doc += `### AI 输出\n\n${log.outputContent || '(空)'}\n\n`
    if (log.errorMessage) {
      doc += `### 错误信息\n\n${log.errorMessage}\n\n`
    }
    doc += `---\n\n`
  })

  return doc
})

function showPreview() {
  viewMode.value = 'preview'
}

function backToRecords() {
  viewMode.value = 'records'
}

function downloadDoc() {
  const content = docContent.value
  if (!content) return
  const blob = new Blob([content], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${selectedUser.value.name}_对话记录_${new Date().toISOString().slice(0, 10)}.md`
  a.click()
  URL.revokeObjectURL(url)
}

function formatTime(dateStr) {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString('zh-CN')
}

function formatCost(cost) {
  if (!cost) return '¥0.000000'
  return '¥' + Number(cost).toFixed(6)
}

function formatDuration(ms) {
  if (!ms) return '-'
  if (ms < 1000) return ms + 'ms'
  return (ms / 1000).toFixed(1) + 's'
}

function truncate(text, len = 50) {
  if (!text) return '-'
  return text.length > len ? text.substring(0, len) + '...' : text
}

// Simple markdown to HTML renderer
function renderMarkdown(md) {
  if (!md) return ''
  let html = md
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  // Headers
  html = html.replace(/^### (.+)$/gm, '<h3>$1</h3>')
  html = html.replace(/^## (.+)$/gm, '<h2>$1</h2>')
  html = html.replace(/^# (.+)$/gm, '<h1>$1</h1>')
  // Bold
  html = html.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
  // Blockquote
  html = html.replace(/^&gt; (.+)$/gm, '<blockquote>$1</blockquote>')
  // HR
  html = html.replace(/^---$/gm, '<hr>')
  // List items
  html = html.replace(/^- (.+)$/gm, '<li>$1</li>')
  // Wrap consecutive li in ul
  html = html.replace(/((?:<li>.*<\/li>\n?)+)/g, '<ul>$1</ul>')
  // Paragraphs (lines not already wrapped)
  html = html.replace(/^(?!<[hubhrol])((?!<).+)$/gm, '<p>$1</p>')
  // Clean up empty paragraphs
  html = html.replace(/<p>\s*<\/p>/g, '')
  return html
}

onMounted(() => {
  loadChatLogs()
  loadUsers()
})
</script>

<template>
  <div>
    <!-- Header -->
    <div class="flex items-center justify-between mb-8">
      <div class="flex items-center gap-3">
        <button v-if="viewMode !== 'users'" @click="viewMode === 'records' ? backToUsers() : backToRecords()" class="p-2 rounded-lg hover:bg-white/5 text-slate-400 hover:text-white transition-colors">
          <ArrowLeft class="w-5 h-5" />
        </button>
        <div>
          <h1 class="text-2xl font-bold text-white">
            {{ viewMode === 'users' ? '对话记录' : viewMode === 'records' ? selectedUser?.name + ' 的对话' : '文档预览' }}
          </h1>
          <p class="text-slate-400 text-sm mt-1">
            {{ viewMode === 'users' ? '按用户查看输入输出内容存档' : viewMode === 'records' ? `共 ${filteredUserLogs.length} 条记录` : '可下载为 Markdown 文档' }}
          </p>
        </div>
      </div>
      <div class="flex items-center gap-2">
        <button v-if="viewMode === 'records'" @click="showPreview" class="btn-primary flex items-center gap-2">
          <FileText class="w-4 h-4" />
          整理成文档
        </button>
        <button v-if="viewMode === 'preview'" @click="downloadDoc" class="btn-primary flex items-center gap-2">
          <Download class="w-4 h-4" />
          下载文档
        </button>
        <button @click="loadChatLogs" class="px-4 py-2 rounded-lg border border-white/10 text-slate-300 hover:bg-white/5 transition-colors flex items-center gap-2">
          <Search class="w-4 h-4" />
          刷新
        </button>
      </div>
    </div>

    <!-- Loading -->
    <div v-if="loading" class="flex items-center justify-center py-32">
      <div class="w-10 h-10 border-2 border-emerald-500/30 border-t-emerald-500 rounded-full animate-spin"></div>
      <span class="ml-3 text-slate-400">加载中...</span>
    </div>

    <!-- Users View -->
    <template v-else-if="viewMode === 'users'">
      <div v-if="!userGroups.length" class="glass-card text-center py-16">
        <MessageSquare class="w-12 h-12 text-slate-600 mx-auto mb-3" />
        <p class="text-slate-500">暂无对话记录</p>
      </div>
      <div v-else class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        <div v-for="group in userGroups" :key="group.userId"
          @click="clickUser(group)"
          class="glass-card p-5 cursor-pointer hover:bg-white/[0.03] transition-all hover:border-emerald-500/20 group">
          <div class="flex items-center gap-4 mb-4">
            <div class="w-10 h-10 rounded-full bg-gradient-to-br from-emerald-500 to-cyan-500 flex items-center justify-center text-white text-sm font-bold">
              {{ (userMap[group.userId] || '?')[0] }}
            </div>
            <div class="flex-1 min-w-0">
              <p class="text-sm font-semibold text-white truncate">{{ userMap[group.userId] || ('用户' + group.userId) }}</p>
              <p class="text-xs text-slate-500">{{ group.logs.length }} 条对话</p>
            </div>
            <ChevronRight class="w-4 h-4 text-slate-500 group-hover:text-emerald-400 transition-colors" />
          </div>
          <div class="grid grid-cols-3 gap-3">
            <div class="text-center p-2 rounded-lg bg-cyan-500/5">
              <p class="text-xs text-slate-500">输入</p>
              <p class="text-sm font-bold text-cyan-400">{{ group.totalInput >= 1000 ? (group.totalInput / 1000).toFixed(1) + 'K' : group.totalInput }}</p>
            </div>
            <div class="text-center p-2 rounded-lg bg-emerald-500/5">
              <p class="text-xs text-slate-500">输出</p>
              <p class="text-sm font-bold text-emerald-400">{{ group.totalOutput >= 1000 ? (group.totalOutput / 1000).toFixed(1) + 'K' : group.totalOutput }}</p>
            </div>
            <div class="text-center p-2 rounded-lg bg-amber-500/5">
              <p class="text-xs text-slate-500">费用</p>
              <p class="text-sm font-bold text-amber-400">¥{{ group.totalCost.toFixed(4) }}</p>
            </div>
          </div>
        </div>
      </div>
    </template>

    <!-- Records View -->
    <template v-else-if="viewMode === 'records'">
      <!-- Filters -->
      <div class="glass-card p-4 mb-6 flex flex-wrap items-center gap-4">
        <Filter class="w-4 h-4 text-slate-400" />
        <select v-model="filterModel" class="input-field w-40">
          <option value="">全部模型</option>
          <option v-for="m in userModelOptions" :key="m" :value="m">{{ m }}</option>
        </select>
        <select v-model="filterStatus" class="input-field w-32">
          <option value="">全部状态</option>
          <option value="success">成功</option>
          <option value="failed">失败</option>
        </select>
        <span class="text-sm text-slate-400 ml-auto">共 {{ filteredUserLogs.length }} 条记录</span>
      </div>

      <!-- Records Table -->
      <div class="glass-card overflow-hidden">
        <table class="w-full">
          <thead>
            <tr class="border-b border-white/5">
              <th class="text-left text-xs font-medium text-slate-400 uppercase px-6 py-4">模型</th>
              <th class="text-left text-xs font-medium text-slate-400 uppercase px-6 py-4">输入内容</th>
              <th class="text-left text-xs font-medium text-slate-400 uppercase px-6 py-4">输入Token</th>
              <th class="text-left text-xs font-medium text-slate-400 uppercase px-6 py-4">输出Token</th>
              <th class="text-left text-xs font-medium text-slate-400 uppercase px-6 py-4">费用</th>
              <th class="text-left text-xs font-medium text-slate-400 uppercase px-6 py-4">耗时</th>
              <th class="text-left text-xs font-medium text-slate-400 uppercase px-6 py-4">状态</th>
              <th class="text-left text-xs font-medium text-slate-400 uppercase px-6 py-4">时间</th>
              <th class="text-left text-xs font-medium text-slate-400 uppercase px-6 py-4">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="log in filteredUserLogs" :key="log.id" class="border-b border-white/5 hover:bg-white/[0.02] transition-colors">
              <td class="px-6 py-4">
                <span class="text-sm text-cyan-400">{{ log.modelName || '-' }}</span>
              </td>
              <td class="px-6 py-4">
                <span class="text-sm text-slate-300" :title="log.inputContent">{{ truncate(log.inputContent, 40) }}</span>
              </td>
              <td class="px-6 py-4">
                <span class="text-sm text-cyan-400">{{ log.inputTokens || 0 }}</span>
              </td>
              <td class="px-6 py-4">
                <span class="text-sm text-amber-400">{{ log.outputTokens || 0 }}</span>
              </td>
              <td class="px-6 py-4">
                <span class="text-sm text-emerald-400">{{ formatCost(log.cost) }}</span>
              </td>
              <td class="px-6 py-4">
                <span class="text-sm text-slate-300">{{ formatDuration(log.durationMs) }}</span>
              </td>
              <td class="px-6 py-4">
                <span v-if="log.status === 'success'" class="px-2 py-1 rounded text-xs font-medium bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">成功</span>
                <span v-else class="px-2 py-1 rounded text-xs font-medium bg-red-500/10 text-red-400 border border-red-500/20">失败</span>
              </td>
              <td class="px-6 py-4">
                <span class="text-sm text-slate-400">{{ formatTime(log.createdAt) }}</span>
              </td>
              <td class="px-6 py-4">
                <button @click="viewDetail(log)" class="p-2 rounded-lg hover:bg-white/5 text-slate-400 hover:text-emerald-400 transition-colors" title="查看详情">
                  <Eye class="w-4 h-4" />
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>

    <!-- Preview View -->
    <template v-else-if="viewMode === 'preview'">
      <div class="glass-card p-8 max-w-4xl mx-auto">
        <!-- Rendered Markdown -->
        <div class="prose prose-invert max-w-none">
          <div v-html="renderMarkdown(docContent)" class="markdown-body"></div>
        </div>
      </div>
    </template>

    <!-- Detail Modal -->
    <div v-if="showDetail && selectedLog" class="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm" @click.self="showDetail = false">
      <div class="glass-card w-full max-w-3xl max-h-[85vh] overflow-y-auto m-4">
        <div class="flex items-center justify-between p-6 border-b border-white/5">
          <h3 class="text-lg font-semibold text-white">对话详情</h3>
          <button @click="showDetail = false" class="p-2 rounded-lg hover:bg-white/5 text-slate-400 hover:text-white">
            <X class="w-5 h-5" />
          </button>
        </div>

        <div class="p-6 grid grid-cols-2 md:grid-cols-4 gap-4 border-b border-white/5">
          <div class="flex items-center gap-2">
            <Cpu class="w-4 h-4 text-cyan-400" />
            <div>
              <p class="text-xs text-slate-500">模型</p>
              <p class="text-sm text-white">{{ selectedLog.modelName || '-' }}</p>
            </div>
          </div>
          <div class="flex items-center gap-2">
            <Clock class="w-4 h-4 text-amber-400" />
            <div>
              <p class="text-xs text-slate-500">耗时</p>
              <p class="text-sm text-white">{{ formatDuration(selectedLog.durationMs) }}</p>
            </div>
          </div>
          <div class="flex items-center gap-2">
            <Coins class="w-4 h-4 text-emerald-400" />
            <div>
              <p class="text-xs text-slate-500">费用</p>
              <p class="text-sm text-white">{{ formatCost(selectedLog.cost) }}</p>
            </div>
          </div>
          <div class="flex items-center gap-2">
            <User class="w-4 h-4 text-purple-400" />
            <div>
              <p class="text-xs text-slate-500">用户</p>
              <p class="text-sm text-white">{{ userMap[selectedLog.userId] || selectedLog.userId }}</p>
            </div>
          </div>
        </div>

        <div class="p-6 grid grid-cols-4 gap-4 border-b border-white/5">
          <div class="text-center p-3 rounded-lg bg-cyan-500/5 border border-cyan-500/10">
            <p class="text-xs text-slate-500 mb-1">输入Token</p>
            <p class="text-lg font-bold text-cyan-400">{{ selectedLog.inputTokens || 0 }}</p>
          </div>
          <div class="text-center p-3 rounded-lg bg-amber-500/5 border border-amber-500/10">
            <p class="text-xs text-slate-500 mb-1">输出Token</p>
            <p class="text-lg font-bold text-amber-400">{{ selectedLog.outputTokens || 0 }}</p>
          </div>
          <div class="text-center p-3 rounded-lg bg-emerald-500/5 border border-emerald-500/10">
            <p class="text-xs text-slate-500 mb-1">总Token</p>
            <p class="text-lg font-bold text-emerald-400">{{ (selectedLog.inputTokens || 0) + (selectedLog.outputTokens || 0) }}</p>
          </div>
          <div class="text-center p-3 rounded-lg bg-purple-500/5 border border-purple-500/10">
            <p class="text-xs text-slate-500 mb-1">状态</p>
            <p class="text-lg font-bold" :class="selectedLog.status === 'success' ? 'text-emerald-400' : 'text-red-400'">
              {{ selectedLog.status === 'success' ? '成功' : '失败' }}
            </p>
          </div>
        </div>

        <div class="p-6 border-b border-white/5">
          <h4 class="text-sm font-medium text-slate-400 mb-3 flex items-center gap-2">
            <span class="w-2 h-2 rounded-full bg-cyan-400"></span> 用户输入
          </h4>
          <div class="bg-[#0d1117] rounded-lg p-4 text-sm text-slate-300 whitespace-pre-wrap break-words max-h-60 overflow-y-auto">
            {{ selectedLog.inputContent || '(空)' }}
          </div>
        </div>

        <div class="p-6">
          <h4 class="text-sm font-medium text-slate-400 mb-3 flex items-center gap-2">
            <span class="w-2 h-2 rounded-full bg-emerald-400"></span> AI 输出
          </h4>
          <div class="bg-[#0d1117] rounded-lg p-4 text-sm text-slate-300 whitespace-pre-wrap break-words max-h-96 overflow-y-auto">
            {{ selectedLog.outputContent || '(空)' }}
          </div>
        </div>

        <div v-if="selectedLog.errorMessage" class="px-6 pb-6">
          <h4 class="text-sm font-medium text-red-400 mb-3">错误信息</h4>
          <div class="bg-red-500/5 border border-red-500/10 rounded-lg p-4 text-sm text-red-300">
            {{ selectedLog.errorMessage }}
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.markdown-body :deep(h1) {
  font-size: 1.5rem;
  font-weight: 700;
  color: #fff;
  margin-bottom: 1rem;
  padding-bottom: 0.5rem;
  border-bottom: 1px solid rgba(255,255,255,0.1);
}
.markdown-body :deep(h2) {
  font-size: 1.2rem;
  font-weight: 600;
  color: #e2e8f0;
  margin-top: 1.5rem;
  margin-bottom: 0.75rem;
}
.markdown-body :deep(h3) {
  font-size: 1rem;
  font-weight: 600;
  color: #94a3b8;
  margin-top: 1rem;
  margin-bottom: 0.5rem;
}
.markdown-body :deep(p) {
  color: #cbd5e1;
  margin-bottom: 0.5rem;
  line-height: 1.7;
}
.markdown-body :deep(blockquote) {
  border-left: 3px solid rgba(16,185,129,0.3);
  padding-left: 1rem;
  margin: 0.75rem 0;
  color: #94a3b8;
}
.markdown-body :deep(hr) {
  border-color: rgba(255,255,255,0.08);
  margin: 1.5rem 0;
}
.markdown-body :deep(ul) {
  list-style: disc;
  padding-left: 1.5rem;
  color: #cbd5e1;
}
.markdown-body :deep(li) {
  margin-bottom: 0.25rem;
}
.markdown-body :deep(strong) {
  color: #e2e8f0;
}
</style>
