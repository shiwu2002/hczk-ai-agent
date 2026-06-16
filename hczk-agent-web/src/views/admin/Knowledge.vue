<script setup>
import { ref, onMounted, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useApiStore } from '@/stores/api'
import { useAuthStore } from '@/stores/auth'
import { API_BASE } from '@/stores/api'
import {
  Database, Search, Trash2, X,
  BarChart3, Rocket, AlertCircle, CheckCircle2,
  Clock, Hash, Eye, ListTree, Users, FileUp
} from 'lucide-vue-next'

const api = useApiStore()
const auth = useAuthStore()
const router = useRouter()
const loading = ref(false)
const activeTab = ref('collections')

// ---- User Selection (全局共享) ----
const users = ref([])
const selectedUserId = ref(null)
const usersLoading = ref(false)

function getAgentId() {
  if (!selectedUserId.value) return ''
  return String(selectedUserId.value)
}

function getUserLabel(item) {
  return `${item.username} (ID: ${item.userId})`
}

async function loadUsers() {
  usersLoading.value = true
  const res = await api.get('/knowledge/owners/users')
  if (res.code === 200) users.value = res.data || []
  usersLoading.value = false
}

// ---- Collections ----
const collections = ref([])
const collectionsLoading = ref(false)

// ---- Retrieve ----
const retrieveForm = ref({ query: '', collection: 'default', topK: 5 })
const retrieveResult = ref(null)
const retrieveLoading = ref(false)

// ---- Ingest File ----
const ingestFileForm = ref({ collection: 'default', title: '', docType: 'auto' })
const ingestFileList = ref([])
const ingestFileResult = ref(null)
const ingestFileLoading = ref(false)
const fileInputRef = ref(null)

const docTypeOptions = [
  { value: 'auto', label: '自动检测', desc: '自动识别问答格式或论文文本' },
  { value: 'qa', label: '问答(QA)', desc: 'Excel 第一列问题、第二列答案；文本按 Q:/问: 格式拆分' },
  { value: 'prose', label: '论文/散文', desc: '按句子分块，适合长文本、论文、文档' }
]

// ---- Recalculate ----
const recalcForm = ref({ collection: '' })
const recalcResult = ref(null)
const recalcLoading = ref(false)

onMounted(() => {
  loadCollections()
  loadUsers()
})

async function loadCollections() {
  collectionsLoading.value = true
  const res = await api.get('/knowledge/collections')
  if (res.code === 200) collections.value = res.data || []
  collectionsLoading.value = false
}

async function deleteCollection(c) {
  const displayName = c.name
  if (!confirm(`确定删除集合 ${displayName}？此操作不可撤销。`)) return
  const agentId = c.agentId || ''
  const collName = c.collectionName || ''
  if (!agentId || !collName) {
    alert('无法解析集合归属信息，请检查数据')
    return
  }
  const res = await api.del(`/knowledge/collections/${encodeURIComponent(collName)}?agentId=${encodeURIComponent(agentId)}`)
  if (res.code === 200) loadCollections()
  else alert(res.message || '删除失败')
}

function viewChunks(name) {
  router.push(`/admin/knowledge/${encodeURIComponent(name)}/chunks`)
}

// ---- Retrieve ----
async function doRetrieve() {
  const agentId = getAgentId()
  if (!agentId) { alert('请选择用户'); return }
  if (!retrieveForm.value.query.trim()) { alert('请输入检索内容'); return }
  retrieveLoading.value = true
  retrieveResult.value = null
  const res = await api.post('/knowledge/retrieve', {
    agentId: agentId,
    query: retrieveForm.value.query,
    collection: retrieveForm.value.collection,
    topK: retrieveForm.value.topK
  })
  retrieveResult.value = res
  retrieveLoading.value = false
}

// ---- Ingest File ----
function onFileSelect(event) {
  const files = Array.from(event.target.files || [])
  const allowedExts = ['.txt', '.pdf', '.docx', '.xlsx']
  for (const f of files) {
    const ext = '.' + f.name.split('.').pop().toLowerCase()
    if (!allowedExts.includes(ext)) {
      alert(`文件 ${f.name} 格式不支持，仅支持 ${allowedExts.join(', ')}`)
      continue
    }
    if (!ingestFileList.value.find(x => x.name === f.name)) {
      ingestFileList.value.push(f)
    }
  }
  if (fileInputRef.value) fileInputRef.value.value = ''
}

function removeFile(index) {
  ingestFileList.value.splice(index, 1)
}

function formatFileSize(bytes) {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1048576).toFixed(1) + ' MB'
}

function getFileIcon(name) {
  const ext = name.split('.').pop().toLowerCase()
  const m = { pdf: '📄', docx: '📝', txt: '📃', xlsx: '📊' }
  return m[ext] || '📎'
}

async function doIngestFile() {
  const agentId = getAgentId()
  if (!agentId) { alert('请选择用户'); return }
  if (ingestFileList.value.length === 0) { alert('请选择要上传的文件'); return }
  ingestFileLoading.value = true
  ingestFileResult.value = null

  const formData = new FormData()
  formData.append('agentId', agentId)
  formData.append('collection', ingestFileForm.value.collection)
  formData.append('docType', ingestFileForm.value.docType)
  if (ingestFileForm.value.title) {
    formData.append('title', ingestFileForm.value.title)
  }

  if (ingestFileList.value.length === 1) {
    formData.append('file', ingestFileList.value[0])
    try {
      const res = await fetch(`${API_BASE}/knowledge/ingest/file`, {
        method: 'POST',
        headers: { 'Authorization': `Bearer ${auth.token}` },
        body: formData
      })
      ingestFileResult.value = await res.json()
    } catch (e) {
      ingestFileResult.value = { code: 500, message: e.message }
    }
  } else {
    for (const f of ingestFileList.value) {
      formData.append('files', f)
    }
    try {
      const res = await fetch(`${API_BASE}/knowledge/ingest/files`, {
        method: 'POST',
        headers: { 'Authorization': `Bearer ${auth.token}` },
        body: formData
      })
      ingestFileResult.value = await res.json()
    } catch (e) {
      ingestFileResult.value = { code: 500, message: e.message }
    }
  }

  ingestFileLoading.value = false
  if (ingestFileResult.value?.code === 200) {
    ingestFileList.value = []
  }
  loadCollections()
}

// ---- Recalculate ----
async function doRecalculate() {
  const agentId = getAgentId()
  if (!agentId) { alert('请选择用户'); return }
  recalcLoading.value = true
  recalcResult.value = null
  const body = { agentId: agentId }
  if (recalcForm.value.collection) body.collection = recalcForm.value.collection
  const res = await api.post('/knowledge/recalculate', body)
  recalcResult.value = res
  recalcLoading.value = false
}

// ---- Helpers ----
function getStrategyLabel(s) {
  const m = { hybrid: '混合检索', vector: '向量检索', keyword: '关键词检索', fallback: '兜底回复' }
  return m[s] || s
}
function getStrategyColor(s) {
  const m = { hybrid: 'text-emerald-400', vector: 'text-blue-400', keyword: 'text-amber-400', fallback: 'text-red-400' }
  return m[s] || 'text-slate-400'
}
function fmtNum(n) { return n != null ? n.toLocaleString() : '-' }

const tabs = [
  { id: 'collections', label: '集合管理', icon: Database },
  { id: 'ingestFile', label: '文件上传', icon: FileUp },
  { id: 'retrieve', label: '检索测试', icon: Search },
  { id: 'recalculate', label: '评分重算', icon: BarChart3 }
]
</script>

<template>
  <div class="space-y-6 animate-fade-in">
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-2xl font-bold text-white">知识库管理</h1>
        <p class="text-slate-400 mt-1">管理 Milvus 向量知识库，包含文件上传、检索测试与相关性评分</p>
      </div>
    </div>

    <!-- Tabs -->
    <div class="flex gap-1 glass-panel p-1 rounded-lg w-fit">
      <button v-for="t in tabs" :key="t.id" @click="activeTab = t.id"
        :class="['flex items-center gap-2 px-4 py-2 rounded-md text-sm font-medium transition-all',
          activeTab === t.id ? 'bg-white/10 text-white' : 'text-slate-400 hover:text-white']">
        <component :is="t.icon" class="w-4 h-4" />
        {{ t.label }}
      </button>
    </div>

    <!-- ===================== Collections Tab ===================== -->
    <div v-if="activeTab === 'collections'" class="space-y-4 animate-fade-in">
      <div class="flex items-center justify-between">
        <h2 class="text-lg font-semibold text-white flex items-center gap-2">
          <ListTree class="w-5 h-5 text-emerald-400" /> 知识集合
        </h2>
        <button @click="loadCollections" class="btn-secondary text-sm flex items-center gap-1">
          <Clock class="w-4 h-4" /> 刷新
        </button>
      </div>

      <div v-if="collectionsLoading" class="text-slate-500 py-8 text-center">加载中...</div>
      <div v-else-if="collections.length === 0" class="glass-card p-12 text-center text-slate-500">
        <Database class="w-12 h-12 mx-auto mb-3 text-slate-600" />
        <p>暂无知识集合，请先通过「文件上传」导入数据</p>
      </div>

      <div v-else class="grid grid-cols-1 lg:grid-cols-2 gap-4">
        <div v-for="c in collections" :key="c.name" class="glass-card p-5 glow-border">
          <div class="flex items-start justify-between">
            <div class="flex items-start gap-3">
              <div class="w-10 h-10 rounded-lg bg-gradient-to-br from-emerald-500/20 to-cyan-500/20 flex items-center justify-center shrink-0">
                <Database class="w-5 h-5 text-emerald-400" />
              </div>
              <div>
                <h3 class="text-white font-semibold text-sm font-mono">{{ c.name }}</h3>
                <div class="flex items-center gap-4 mt-1 text-xs text-slate-400">
                  <span class="flex items-center gap-1"><Hash class="w-3 h-3" /> {{ fmtNum(c.rowCount) }} 条</span>
                  <span v-if="c.agentId" class="flex items-center gap-1"><Rocket class="w-3 h-3" /> {{ c.agentId }}</span>
                </div>
                <div v-if="c.ownerName" class="flex items-center gap-2 mt-1.5">
                  <span class="inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-medium bg-purple-500/10 text-purple-400">
                    <Users class="w-3 h-3" /> 用户
                  </span>
                  <span class="text-xs text-slate-300">{{ c.ownerName }}</span>
                </div>
              </div>
            </div>
            <div class="flex items-center gap-2">
              <button @click="viewChunks(c.name)" class="p-2 rounded-lg bg-white/5 text-slate-300 hover:bg-white/10 hover:text-emerald-400 transition-all" title="浏览分块">
                <Eye class="w-4 h-4" />
              </button>
              <button @click="deleteCollection(c)" class="p-2 rounded-lg bg-white/5 text-slate-300 hover:bg-red-500/10 hover:text-red-400 transition-all" title="删除集合">
                <Trash2 class="w-4 h-4" />
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ===================== Ingest File Tab ===================== -->
    <div v-if="activeTab === 'ingestFile'" class="space-y-4 animate-fade-in">
      <h2 class="text-lg font-semibold text-white flex items-center gap-2">
        <FileUp class="w-5 h-5 text-emerald-400" /> 文件上传
      </h2>
      <div class="glass-card p-6 space-y-4">
        <!-- 用户选择 -->
        <div class="p-4 rounded-lg bg-white/5 border border-white/10 space-y-3">
          <label class="block text-sm font-medium text-slate-200">归属用户</label>
          <select v-model="selectedUserId" class="input-field w-full" :disabled="usersLoading">
            <option :value="null" disabled>-- 请选择用户 --</option>
            <option v-for="item in users" :key="item.userId" :value="item.userId">
              {{ getUserLabel(item) }}
            </option>
          </select>
          <div v-if="selectedUserId" class="text-xs text-slate-400">
            Agent ID: <span class="text-emerald-400 font-mono">{{ getAgentId() }}</span>
          </div>
        </div>

        <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <label class="block text-sm text-slate-300 mb-1.5">集合名</label>
            <input v-model="ingestFileForm.collection" class="input-field" placeholder="default" />
          </div>
          <div>
            <label class="block text-sm text-slate-300 mb-1.5">标题（可选，默认使用文件名）</label>
            <input v-model="ingestFileForm.title" class="input-field" placeholder="留空则使用文件名" />
          </div>
        </div>

        <!-- 文档类型选择 -->
        <div class="p-4 rounded-lg bg-white/5 border border-white/10 space-y-3">
          <label class="block text-sm font-medium text-slate-200">文档类型</label>
          <div class="grid grid-cols-1 md:grid-cols-3 gap-3">
            <button v-for="opt in docTypeOptions" :key="opt.value"
              @click="ingestFileForm.docType = opt.value"
              :class="['p-3 rounded-lg text-left transition-all border',
                ingestFileForm.docType === opt.value
                  ? 'bg-emerald-500/10 border-emerald-500/30'
                  : 'bg-white/5 border-transparent hover:bg-white/10']">
              <p :class="['text-sm font-medium', ingestFileForm.docType === opt.value ? 'text-emerald-400' : 'text-slate-300']">
                {{ opt.label }}
              </p>
              <p class="text-xs text-slate-500 mt-1">{{ opt.desc }}</p>
            </button>
          </div>
        </div>

        <!-- 文件选择区域 -->
        <div>
          <input ref="fileInputRef" type="file" multiple accept=".txt,.pdf,.docx,.xlsx" class="hidden"
            @change="onFileSelect" />
          <div @click="fileInputRef?.click()"
            class="border-2 border-dashed border-white/10 rounded-lg p-8 text-center cursor-pointer hover:border-emerald-500/30 hover:bg-emerald-500/5 transition-all">
            <FileUp class="w-10 h-10 mx-auto mb-3 text-slate-500" />
            <p class="text-slate-300 font-medium">点击选择文件或拖拽文件到此处</p>
            <p class="text-xs text-slate-500 mt-2">支持 Word(.docx)、PDF(.pdf)、TXT(.txt)、Excel(.xlsx)，单文件最大 50MB</p>
          </div>
        </div>

        <!-- 已选文件列表 -->
        <div v-if="ingestFileList.length > 0" class="space-y-2">
          <div class="flex items-center justify-between">
            <span class="text-sm text-slate-300">已选 {{ ingestFileList.length }} 个文件</span>
            <button @click="ingestFileList = []" class="text-xs text-red-400 hover:text-red-300">清空全部</button>
          </div>
          <div v-for="(f, i) in ingestFileList" :key="f.name"
            class="flex items-center gap-3 p-3 rounded-lg bg-white/5 border border-white/5">
            <span class="text-lg">{{ getFileIcon(f.name) }}</span>
            <div class="flex-1 min-w-0">
              <p class="text-sm text-white truncate">{{ f.name }}</p>
              <p class="text-xs text-slate-500">{{ formatFileSize(f.size) }}</p>
            </div>
            <button @click="removeFile(i)" class="p-1 rounded hover:bg-white/10 text-slate-400 hover:text-red-400 transition-all">
              <X class="w-4 h-4" />
            </button>
          </div>
        </div>

        <button @click="doIngestFile" :disabled="ingestFileLoading || ingestFileList.length === 0"
          class="btn-primary flex items-center gap-2">
          <Rocket class="w-4 h-4" /> {{ ingestFileLoading ? '上传摄入中...' : `上传并摄入 (${ingestFileList.length} 个文件)` }}
        </button>
      </div>
      <div v-if="ingestFileResult" class="glass-card p-4 flex items-center gap-3">
        <CheckCircle2 v-if="ingestFileResult.code === 200" class="w-5 h-5 text-emerald-400" />
        <AlertCircle v-else class="w-5 h-5 text-red-400" />
        <div>
          <p :class="ingestFileResult.code === 200 ? 'text-emerald-400' : 'text-red-400'" class="font-medium">
            {{ ingestFileResult.code === 200 ? '摄入成功' : '摄入失败' }}
          </p>
          <div v-if="ingestFileResult.data" class="text-sm text-slate-400 mt-1">
            <template v-if="Array.isArray(ingestFileResult.data)">
              <div v-for="(r, i) in ingestFileResult.data" :key="i">
                文件{{ i + 1 }}：共计 {{ r.totalChunks }} 块，已入库 {{ r.ingestedChunks }}，跳过 {{ r.skippedChunks }}
              </div>
            </template>
            <template v-else>
              共计 {{ ingestFileResult.data.totalChunks }} 块，已入库 {{ ingestFileResult.data.ingestedChunks }}，跳过 {{ ingestFileResult.data.skippedChunks }}
            </template>
          </div>
          <p v-if="ingestFileResult.message" class="text-sm text-red-400 mt-1">{{ ingestFileResult.message }}</p>
        </div>
      </div>
    </div>

    <!-- ===================== Retrieve Tab ===================== -->
    <div v-if="activeTab === 'retrieve'" class="space-y-4 animate-fade-in">
      <h2 class="text-lg font-semibold text-white flex items-center gap-2">
        <Search class="w-5 h-5 text-emerald-400" /> 检索测试
      </h2>
      <div class="glass-card p-6 space-y-4">
        <!-- 用户选择 -->
        <div class="p-4 rounded-lg bg-white/5 border border-white/10 space-y-3">
          <label class="block text-sm font-medium text-slate-200">归属用户</label>
          <select v-model="selectedUserId" class="input-field w-full" :disabled="usersLoading">
            <option :value="null" disabled>-- 请选择用户 --</option>
            <option v-for="item in users" :key="item.userId" :value="item.userId">
              {{ getUserLabel(item) }}
            </option>
          </select>
        </div>

        <div class="grid grid-cols-2 md:grid-cols-3 gap-4">
          <div>
            <label class="block text-sm text-slate-300 mb-1.5">集合名</label>
            <input v-model="retrieveForm.collection" class="input-field" placeholder="default" />
          </div>
          <div>
            <label class="block text-sm text-slate-300 mb-1.5">TopK</label>
            <input v-model.number="retrieveForm.topK" type="number" min="1" max="50" class="input-field" />
          </div>
          <div class="flex items-end">
            <button @click="doRetrieve" :disabled="retrieveLoading" class="btn-primary w-full flex items-center justify-center gap-2">
              <Search class="w-4 h-4" /> {{ retrieveLoading ? '检索中...' : '检索' }}
            </button>
          </div>
        </div>
        <div>
          <label class="block text-sm text-slate-300 mb-1.5">查询文本</label>
          <textarea v-model="retrieveForm.query" rows="3" class="input-field" placeholder="输入要检索的问题..."></textarea>
        </div>
      </div>

      <div v-if="retrieveResult" class="space-y-4">
        <div class="flex items-center gap-3 text-sm">
          <span :class="['px-2 py-0.5 rounded font-medium', retrieveResult.code === 200 ? 'bg-emerald-500/10 text-emerald-400' : 'bg-red-500/10 text-red-400']">
            {{ retrieveResult.code === 200 ? '成功' : '失败' }}
          </span>
          <span v-if="retrieveResult.data" class="text-slate-400">
            策略：<span :class="getStrategyColor(retrieveResult.data.strategy)">{{ getStrategyLabel(retrieveResult.data.strategy) }}</span>
          </span>
          <span v-if="retrieveResult.data" class="text-slate-400">
            置信度：<span :class="retrieveResult.data.confidence >= 0.5 ? 'text-emerald-400' : 'text-amber-400'">{{ retrieveResult.data.confidence }}</span>
          </span>
          <span v-if="retrieveResult.data?.fallbackUsed" class="px-2 py-0.5 rounded text-xs bg-amber-500/10 text-amber-400">已降级</span>
        </div>
        <div v-if="retrieveResult.data?.results?.length">
          <div v-for="(r, i) in retrieveResult.data.results" :key="i" class="glass-panel p-4 mb-3">
            <div class="flex items-start justify-between gap-4">
              <div class="flex-1">
                <div class="flex items-center gap-2 mb-2">
                  <span class="px-1.5 py-0.5 text-xs rounded bg-white/5 text-slate-400 font-mono">#{{ i + 1 }}</span>
                  <span class="px-1.5 py-0.5 text-xs rounded" :class="getStrategyColor(retrieveResult.data.strategy) + ' bg-white/5'">{{ r.metadata?.matchType || '-' }}</span>
                  <span class="text-xs text-slate-500">得分 {{ r.score?.toFixed(3) }}</span>
                  <span v-if="r.metadata?.chunkType" class="text-xs text-slate-500">{{ r.metadata.chunkType }}</span>
                </div>
                <p class="text-sm text-slate-300 whitespace-pre-wrap">{{ r.content }}</p>
                <div v-if="r.metadata" class="flex flex-wrap gap-3 mt-2 text-xs text-slate-500">
                  <span v-if="r.metadata.title">标题: {{ r.metadata.title }}</span>
                  <span v-if="r.metadata.source">来源: {{ r.metadata.source }}</span>
                  <span>采纳: {{ r.metadata.adoptCount }} 命中: {{ r.metadata.hitCount }}</span>
                  <span>相关性: {{ r.metadata.relevanceScore?.toFixed(2) }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div v-else class="glass-card p-6 text-center text-slate-500">无检索结果</div>
      </div>
    </div>

    <!-- ===================== Recalculate Tab ===================== -->
    <div v-if="activeTab === 'recalculate'" class="space-y-4 animate-fade-in">
      <h2 class="text-lg font-semibold text-white flex items-center gap-2">
        <BarChart3 class="w-5 h-5 text-emerald-400" /> 相关性评分重算
      </h2>
      <div class="glass-card p-6 space-y-4">
        <!-- 用户选择 -->
        <div class="p-4 rounded-lg bg-white/5 border border-white/10 space-y-3">
          <label class="block text-sm font-medium text-slate-200">归属用户</label>
          <select v-model="selectedUserId" class="input-field w-full" :disabled="usersLoading">
            <option :value="null" disabled>-- 请选择用户 --</option>
            <option v-for="item in users" :key="item.userId" :value="item.userId">
              {{ getUserLabel(item) }}
            </option>
          </select>
        </div>

        <div>
          <label class="block text-sm text-slate-300 mb-1.5">集合名（留空则重算该用户全部集合）</label>
          <input v-model="recalcForm.collection" class="input-field" placeholder="default" />
        </div>
        <button @click="doRecalculate" :disabled="recalcLoading" class="btn-primary flex items-center gap-2">
          <BarChart3 class="w-4 h-4" /> {{ recalcLoading ? '重算中...' : '开始重算' }}
        </button>
      </div>
      <div v-if="recalcResult" class="glass-card p-4 flex items-center gap-3">
        <CheckCircle2 v-if="recalcResult.code === 200" class="w-5 h-5 text-emerald-400" />
        <AlertCircle v-else class="w-5 h-5 text-red-400" />
        <div>
          <p :class="recalcResult.code === 200 ? 'text-emerald-400' : 'text-red-400'" class="font-medium">
            {{ recalcResult.code === 200 ? '重算完成' : '重算失败' }}
          </p>
          <p v-if="recalcResult.data != null" class="text-sm text-slate-400 mt-1">已更新 {{ recalcResult.data }} 条数据</p>
        </div>
      </div>
    </div>

  </div>
</template>
