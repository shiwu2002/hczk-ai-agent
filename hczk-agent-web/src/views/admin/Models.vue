<!-- 管理后台 - 模型管理页面：接入第三方大模型，配置 API 与计费参数 -->
<script setup>
import { ref, computed, onMounted } from 'vue'
import { useApiStore } from '@/stores/api'
import {
  Plus, Power, Settings2, Trash2, Cpu, Gauge, ChevronLeft, ChevronRight,
  Search, Eye, EyeOff, KeyRound, Globe, Zap, Brain, ArrowUpRight, X, Check, Copy
} from 'lucide-vue-next'

const api = useApiStore()

const loading = ref(false)
const showAddModal = ref(false)
const editingModel = ref(null)
const models = ref([])
const searchQuery = ref('')
const statusFilter = ref('all')
const showApiKeyMap = ref({})
const copiedId = ref(null)

// 分页
const currentPage = ref(1)
const pageSize = 9
const filteredModels = computed(() => {
  let list = models.value
  if (searchQuery.value) {
    const q = searchQuery.value.toLowerCase()
    list = list.filter(m => (m.name || '').toLowerCase().includes(q) || (m.modelId || '').toLowerCase().includes(q) || (m.provider || '').toLowerCase().includes(q))
  }
  if (statusFilter.value === 'online') list = list.filter(m => m.status === 0)
  if (statusFilter.value === 'offline') list = list.filter(m => m.status !== 0)
  return list
})
const totalPages = computed(() => Math.max(1, Math.ceil(filteredModels.value.length / pageSize)))
const pagedModels = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return filteredModels.value.slice(start, start + pageSize)
})
function goToPage(page) { if (page >= 1 && page <= totalPages.value) currentPage.value = page }

const onlineCount = computed(() => models.value.filter(m => m.status === 0).length)
const providerStats = computed(() => {
  const map = {}
  for (const m of models.value) {
    const p = m.provider || '未知'
    if (!map[p]) map[p] = 0
    map[p]++
  }
  return Object.entries(map).sort((a, b) => b[1] - a[1]).slice(0, 5)
})

const newModel = ref({
  name: '', provider: '', providerType: 'OPENAI_COMPATIBLE', modelId: '',
  apiBase: '', apiKey: '', maxTokens: 4096, thinking: false
})

onMounted(loadData)

async function loadData() {
  loading.value = true
  const res = await api.get('/models')
  if (res.code === 200) models.value = res.data || []
  loading.value = false
}

async function toggleStatus(model) {
  const res = await api.post(`/models/${model.id}/toggle`)
  if (res.code === 200) model.status = res.data.status
  else alert(res.message || '操作失败')
}

async function deleteModel(model) {
  if (!confirm('确定删除该模型？')) return
  const res = await api.del(`/models/${model.id}`)
  if (res.code === 200) loadData()
  else alert(res.message || '删除失败')
}

async function saveModel() {
  if (!newModel.value.name.trim()) { alert('请输入模型名称'); return }
  if (!newModel.value.provider.trim()) { alert('请输入提供商'); return }
  if (!newModel.value.modelId.trim()) { alert('请输入模型ID'); return }
  const body = editingModel.value ? { ...editingModel.value, ...newModel.value } : { ...newModel.value }
  if (editingModel.value) {
    const res = await api.put(`/models/${editingModel.value.id}`, body)
    if (res.code === 200) { showAddModal.value = false; editingModel.value = null; resetForm(); loadData() }
    else alert(res.message || '更新失败')
  } else {
    const res = await api.post('/models', body)
    if (res.code === 200) { showAddModal.value = false; resetForm(); loadData() }
    else alert(res.message || '创建失败')
  }
}

function editModel(model) {
  editingModel.value = { ...model }
  newModel.value = {
    name: model.name, provider: model.provider, providerType: model.providerType || 'OPENAI_COMPATIBLE',
    modelId: model.modelId, apiBase: model.apiBase || '', apiKey: model.apiKey || '',
    maxTokens: model.maxTokens || 4096, thinking: model.thinking || false
  }
  showAddModal.value = true
}

function resetForm() {
  newModel.value = { name: '', provider: '', providerType: 'OPENAI_COMPATIBLE', modelId: '', apiBase: '', apiKey: '', maxTokens: 4096, thinking: false }
}

function toggleApiKey(id) { showApiKeyMap.value[id] = !showApiKeyMap.value[id] }
function maskKey(key) {
  if (!key) return '-'
  if (key.length <= 8) return '****'
  return key.slice(0, 4) + '****' + key.slice(-4)
}

function copyModelId(modelId) {
  navigator.clipboard.writeText(modelId)
  copiedId.value = modelId
  setTimeout(() => copiedId.value = null, 1500)
}

const providerTypeLabel = { OPENAI_COMPATIBLE: 'OpenAI 兼容', ANTHROPIC: 'Anthropic', MODELSCOPE: 'ModelScope' }
const providerTypeColor = { OPENAI_COMPATIBLE: 'text-cyan-400', ANTHROPIC: 'text-amber-400', MODELSCOPE: 'text-purple-400' }
</script>

<template>
  <div class="space-y-5 animate-fade-in">
    <!-- Top Stats -->
    <div class="grid grid-cols-2 md:grid-cols-4 gap-3">
      <div class="glass-card rounded-xl p-3.5">
        <div class="flex items-center justify-between mb-1">
          <span class="text-[11px] text-slate-500">模型总数</span>
          <Cpu class="w-3.5 h-3.5 text-cyan-400" />
        </div>
        <p class="text-2xl font-bold text-white">{{ models.length }}</p>
      </div>
      <div class="glass-card rounded-xl p-3.5">
        <div class="flex items-center justify-between mb-1">
          <span class="text-[11px] text-slate-500">在线运行</span>
          <Zap class="w-3.5 h-3.5 text-emerald-400" />
        </div>
        <p class="text-2xl font-bold text-emerald-400">{{ onlineCount }}</p>
      </div>
      <div class="glass-card rounded-xl p-3.5">
        <div class="flex items-center justify-between mb-1">
          <span class="text-[11px] text-slate-500">已停用</span>
          <Power class="w-3.5 h-3.5 text-slate-400" />
        </div>
        <p class="text-2xl font-bold text-slate-400">{{ models.length - onlineCount }}</p>
      </div>
      <div class="glass-card rounded-xl p-3.5">
        <div class="flex items-center justify-between mb-1">
          <span class="text-[11px] text-slate-500">提供商</span>
          <Globe class="w-3.5 h-3.5 text-amber-400" />
        </div>
        <p class="text-2xl font-bold text-white">{{ providerStats.length }}</p>
      </div>
    </div>

    <!-- Toolbar -->
    <div class="flex items-center gap-3 flex-wrap">
      <div class="relative flex-1 min-w-[200px] max-w-sm">
        <Search class="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-500" />
        <input v-model="searchQuery" class="w-full pl-9 pr-3 py-2 rounded-lg bg-white/[0.04] border border-white/[0.08] text-sm text-white placeholder-slate-600 focus:outline-none focus:border-emerald-500/40 focus:ring-1 focus:ring-emerald-500/20 transition-all" placeholder="搜索模型名称、ID、提供商..." />
      </div>
      <div class="flex items-center gap-1.5">
        <button @click="statusFilter = 'all'" :class="['px-3 py-1.5 text-xs rounded-lg transition-all', statusFilter === 'all' ? 'bg-emerald-500/15 text-emerald-400 border border-emerald-500/20' : 'bg-white/[0.03] text-slate-400 border border-white/[0.06] hover:bg-white/[0.06]']">全部</button>
        <button @click="statusFilter = 'online'" :class="['px-3 py-1.5 text-xs rounded-lg transition-all', statusFilter === 'online' ? 'bg-emerald-500/15 text-emerald-400 border border-emerald-500/20' : 'bg-white/[0.03] text-slate-400 border border-white/[0.06] hover:bg-white/[0.06]']">在线</button>
        <button @click="statusFilter = 'offline'" :class="['px-3 py-1.5 text-xs rounded-lg transition-all', statusFilter === 'offline' ? 'bg-emerald-500/15 text-emerald-400 border border-emerald-500/20' : 'bg-white/[0.03] text-slate-400 border border-white/[0.06] hover:bg-white/[0.06]']">离线</button>
      </div>
      <button @click="showAddModal = true; editingModel = null; resetForm()" class="ml-auto px-4 py-2 text-sm font-medium rounded-xl bg-gradient-to-r from-emerald-500 to-cyan-500 text-white shadow-lg shadow-emerald-500/20 hover:shadow-emerald-500/35 transition-all flex items-center gap-2">
        <Plus class="w-4 h-4" /> 接入模型
      </button>
    </div>

    <!-- Loading -->
    <div v-if="loading" class="flex items-center justify-center py-20">
      <div class="w-8 h-8 border-2 border-emerald-500/30 border-t-emerald-500 rounded-full animate-spin"></div>
    </div>

    <!-- Model Cards Grid -->
    <div v-else-if="pagedModels.length > 0" class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
      <div v-for="model in pagedModels" :key="model.id"
        :class="['glass-card rounded-xl overflow-hidden transition-all hover:-translate-y-0.5', model.status === 0 ? '' : 'opacity-60']">
        <!-- Top color bar -->
        <div :class="['h-1', model.status === 0 ? 'bg-gradient-to-r from-emerald-500 to-cyan-500' : 'bg-slate-600']"></div>
        <div class="p-4">
          <!-- Header -->
          <div class="flex items-start justify-between mb-3">
            <div class="flex items-center gap-2.5 min-w-0">
              <div :class="['w-9 h-9 rounded-lg flex items-center justify-center flex-shrink-0', model.status === 0 ? 'bg-emerald-500/10' : 'bg-slate-500/10']">
                <Cpu :class="['w-4 h-4', model.status === 0 ? 'text-emerald-400' : 'text-slate-500']" />
              </div>
              <div class="min-w-0">
                <h3 class="text-sm font-semibold text-white truncate">{{ model.name }}</h3>
                <p class="text-[11px] text-slate-500 truncate">{{ model.provider }}</p>
              </div>
            </div>
            <span :class="['text-[10px] px-1.5 py-0.5 rounded font-medium flex-shrink-0', model.status === 0 ? 'bg-emerald-500/10 text-emerald-400' : 'bg-slate-500/10 text-slate-500']">
              {{ model.status === 0 ? '在线' : '离线' }}
            </span>
          </div>

          <!-- Info rows -->
          <div class="space-y-2 mb-3">
            <div class="flex items-center gap-2 text-[11px]">
              <span class="text-slate-500 w-14 flex-shrink-0">模型ID</span>
              <code class="text-cyan-400 font-mono truncate flex-1">{{ model.modelId }}</code>
              <button @click="copyModelId(model.modelId)" class="text-slate-500 hover:text-cyan-400 transition-colors flex-shrink-0">
                <Copy v-if="copiedId !== model.modelId" class="w-3 h-3" />
                <Check v-else class="w-3 h-3 text-emerald-400" />
              </button>
            </div>
            <div class="flex items-center gap-2 text-[11px]">
              <span class="text-slate-500 w-14 flex-shrink-0">接口</span>
              <span :class="providerTypeColor[model.providerType] || 'text-slate-400'">{{ providerTypeLabel[model.providerType] || model.providerType }}</span>
            </div>
            <div class="flex items-center gap-2 text-[11px]">
              <span class="text-slate-500 w-14 flex-shrink-0">API</span>
              <span class="text-slate-400 font-mono truncate flex-1">{{ model.apiBase || '默认' }}</span>
            </div>
            <div class="flex items-center gap-2 text-[11px]">
              <span class="text-slate-500 w-14 flex-shrink-0">Key</span>
              <div class="flex items-center gap-1.5 flex-1 min-w-0">
                <code class="text-slate-400 font-mono truncate">{{ showApiKeyMap[model.id] ? (model.apiKey || '-') : maskKey(model.apiKey) }}</code>
                <button @click="toggleApiKey(model.id)" class="text-slate-500 hover:text-slate-300 transition-colors flex-shrink-0">
                  <Eye v-if="!showApiKeyMap[model.id]" class="w-3 h-3" />
                  <EyeOff v-else class="w-3 h-3" />
                </button>
              </div>
            </div>
          </div>

          <!-- Tags -->
          <div class="flex items-center gap-1.5 mb-3 flex-wrap">
            <span class="px-1.5 py-0.5 text-[10px] rounded bg-white/[0.04] text-slate-400">{{ ((model.maxTokens || 0) / 1024).toFixed(0) }}K ctx</span>
            <span v-if="model.thinking" class="px-1.5 py-0.5 text-[10px] rounded bg-amber-500/10 text-amber-400 flex items-center gap-0.5"><Brain class="w-2.5 h-2.5" />思考</span>
            <span v-if="model.qps" class="px-1.5 py-0.5 text-[10px] rounded bg-white/[0.04] text-slate-400">{{ model.qps }} QPS</span>
          </div>

          <!-- Actions -->
          <div class="flex items-center gap-2 pt-3 border-t border-white/5">
            <button @click="toggleStatus(model)"
              :class="['flex-1 py-1.5 text-xs font-medium rounded-lg transition-all flex items-center justify-center gap-1.5',
                model.status === 0 ? 'bg-emerald-500/10 text-emerald-400 hover:bg-emerald-500/20' : 'bg-slate-500/10 text-slate-400 hover:bg-slate-500/20']">
              <Power class="w-3 h-3" /> {{ model.status === 0 ? '停用' : '启用' }}
            </button>
            <button @click="editModel(model)" class="flex-1 py-1.5 text-xs font-medium rounded-lg bg-white/[0.04] text-slate-300 hover:bg-white/[0.08] transition-all flex items-center justify-center gap-1.5">
              <Settings2 class="w-3 h-3" /> 编辑
            </button>
            <button @click="deleteModel(model)" class="py-1.5 px-3 text-xs font-medium rounded-lg bg-white/[0.04] text-red-400 hover:bg-red-500/10 transition-all flex items-center justify-center gap-1.5">
              <Trash2 class="w-3 h-3" />
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- Empty -->
    <div v-else class="text-center py-20">
      <Cpu class="w-10 h-10 text-slate-600 mx-auto mb-3" />
      <p class="text-sm text-slate-500">{{ searchQuery ? '未找到匹配的模型' : '暂无模型，点击上方按钮接入' }}</p>
    </div>

    <!-- Pagination -->
    <div v-if="filteredModels.length > pageSize" class="flex items-center justify-between pt-2">
      <span class="text-xs text-slate-500">{{ filteredModels.length }} 个模型 · 第 {{ currentPage }}/{{ totalPages }} 页</span>
      <div class="flex items-center gap-1">
        <button @click="goToPage(currentPage - 1)" :disabled="currentPage === 1" class="p-1.5 rounded-lg bg-white/5 text-slate-400 hover:bg-white/10 disabled:opacity-25 disabled:cursor-not-allowed transition-all"><ChevronLeft class="w-4 h-4" /></button>
        <template v-for="p in totalPages" :key="p">
          <button v-show="totalPages <= 7 || Math.abs(p - currentPage) < 2 || p === 1 || p === totalPages" @click="goToPage(p)"
            :class="['w-7 h-7 rounded-lg text-xs font-medium transition-all', p === currentPage ? 'bg-emerald-500/15 text-emerald-400' : 'bg-white/5 text-slate-400 hover:bg-white/10']">{{ p }}</button>
        </template>
        <button @click="goToPage(currentPage + 1)" :disabled="currentPage === totalPages" class="p-1.5 rounded-lg bg-white/5 text-slate-400 hover:bg-white/10 disabled:opacity-25 disabled:cursor-not-allowed transition-all"><ChevronRight class="w-4 h-4" /></button>
      </div>
    </div>

    <!-- Add/Edit Modal -->
    <div v-if="showAddModal" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm" @click.self="showAddModal = false">
      <div class="glass-card w-full max-w-lg rounded-2xl overflow-hidden animate-slide-up">
        <div class="flex items-center justify-between px-5 py-4 border-b border-white/5">
          <h2 class="text-base font-semibold text-white">{{ editingModel ? '编辑模型' : '接入新模型' }}</h2>
          <button @click="showAddModal = false" class="p-1.5 rounded-lg hover:bg-white/5 text-slate-400 hover:text-white transition-colors"><X class="w-4 h-4" /></button>
        </div>
        <div class="p-5 space-y-4">
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-[11px] text-slate-500 mb-1.5">模型名称</label>
              <input v-model="newModel.name" class="w-full px-3 py-2 rounded-lg bg-white/[0.04] border border-white/[0.08] text-sm text-white placeholder-slate-600 focus:outline-none focus:border-emerald-500/40 focus:ring-1 focus:ring-emerald-500/20 transition-all" placeholder="如 qwen3.7-plus" />
            </div>
            <div>
              <label class="block text-[11px] text-slate-500 mb-1.5">提供商</label>
              <input v-model="newModel.provider" class="w-full px-3 py-2 rounded-lg bg-white/[0.04] border border-white/[0.08] text-sm text-white placeholder-slate-600 focus:outline-none focus:border-emerald-500/40 focus:ring-1 focus:ring-emerald-500/20 transition-all" placeholder="如 阿里云" />
            </div>
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-[11px] text-slate-500 mb-1.5">接口类型</label>
              <select v-model="newModel.providerType" class="w-full px-3 py-2 rounded-lg bg-white/[0.04] border border-white/[0.08] text-sm text-white focus:outline-none focus:border-emerald-500/40 focus:ring-1 focus:ring-emerald-500/20 transition-all">
                <option value="OPENAI_COMPATIBLE">OpenAI 兼容</option>
                <option value="ANTHROPIC">Anthropic</option>
                <option value="MODELSCOPE">ModelScope</option>
              </select>
            </div>
            <div>
              <label class="block text-[11px] text-slate-500 mb-1.5">模型 ID</label>
              <input v-model="newModel.modelId" class="w-full px-3 py-2 rounded-lg bg-white/[0.04] border border-white/[0.08] text-sm text-white placeholder-slate-600 focus:outline-none focus:border-emerald-500/40 focus:ring-1 focus:ring-emerald-500/20 transition-all" placeholder="如 qwen-plus" />
            </div>
          </div>
          <div>
            <label class="block text-[11px] text-slate-500 mb-1.5">API Base URL（不含 /chat/completions）</label>
            <input v-model="newModel.apiBase" class="w-full px-3 py-2 rounded-lg bg-white/[0.04] border border-white/[0.08] text-sm text-white placeholder-slate-600 focus:outline-none focus:border-emerald-500/40 focus:ring-1 focus:ring-emerald-500/20 transition-all" placeholder="https://api.example.com/v1" />
          </div>
          <div>
            <label class="block text-[11px] text-slate-500 mb-1.5">API Key</label>
            <input v-model="newModel.apiKey" type="password" class="w-full px-3 py-2 rounded-lg bg-white/[0.04] border border-white/[0.08] text-sm text-white placeholder-slate-600 focus:outline-none focus:border-emerald-500/40 focus:ring-1 focus:ring-emerald-500/20 transition-all" placeholder="sk-..." />
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-[11px] text-slate-500 mb-1.5">最大 Tokens</label>
              <input v-model.number="newModel.maxTokens" type="number" class="w-full px-3 py-2 rounded-lg bg-white/[0.04] border border-white/[0.08] text-sm text-white focus:outline-none focus:border-emerald-500/40 focus:ring-1 focus:ring-emerald-500/20 transition-all" />
            </div>
            <div class="flex items-end pb-1">
              <label class="flex items-center gap-2 cursor-pointer">
                <input v-model="newModel.thinking" type="checkbox" class="w-4 h-4 rounded border-slate-600 bg-slate-800 text-emerald-500 focus:ring-emerald-500" />
                <span class="text-sm text-slate-300">启用深度思考</span>
              </label>
            </div>
          </div>
        </div>
        <div class="flex gap-3 px-5 py-4 border-t border-white/5">
          <button @click="showAddModal = false" class="flex-1 py-2.5 text-sm font-medium rounded-xl bg-white/[0.06] border border-white/[0.08] text-white hover:bg-white/[0.1] transition-all">取消</button>
          <button @click="saveModel" class="flex-1 py-2.5 text-sm font-medium rounded-xl bg-gradient-to-r from-emerald-500 to-cyan-500 text-white shadow-lg shadow-emerald-500/20 hover:shadow-emerald-500/35 transition-all">保存</button>
        </div>
      </div>
    </div>
  </div>
</template>
