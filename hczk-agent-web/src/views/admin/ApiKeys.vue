<!--
  API Keys 管理页面（管理员）
  功能：API密钥的创建、编辑、删除、复制，支持搜索和分页
-->
<script setup>
import { ref, onMounted, computed } from 'vue'
import { useApiStore } from '@/stores/api'
import {
  KeyRound, Plus, Search, Trash2, Copy, Check, X, Settings2,
  ChevronLeft, ChevronRight, Eye, EyeOff, Users, Zap, Coins, ShieldCheck
} from 'lucide-vue-next'

const api = useApiStore()

const loading = ref(false)
const showAddModal = ref(false)
const showEditModal = ref(false)
const searchQuery = ref('')
const statusFilter = ref('all')

const users = ref([])
const apiKeys = ref([])
const models = ref([])

const newApiKey = ref({ userId: '', name: '', unitPrice: 0, modelIds: [] })
const editingKey = ref(null)
const editForm = ref({ name: '', unitPrice: 0, modelIds: [] })
const copiedKey = ref(null)
const showKeyMap = ref({})

onMounted(loadData)

async function loadData() {
  loading.value = true
  const [userRes, apiKeyRes, modelRes] = await Promise.all([
    api.get('/users'),
    api.get('/api-keys'),
    api.get('/models')
  ])
  if (userRes.code === 200) users.value = userRes.data || []
  if (apiKeyRes.code === 200) apiKeys.value = apiKeyRes.data || []
  if (modelRes.code === 200) models.value = (modelRes.data || []).filter(m => m.status === 0)
  loading.value = false
}

async function createApiKey() {
  if (!newApiKey.value.userId) { alert('请选择用户'); return }
  if (!newApiKey.value.name) { alert('请输入密钥名称'); return }
  const params = new URLSearchParams()
  params.append('userId', newApiKey.value.userId)
  params.append('name', newApiKey.value.name)
  params.append('unitPrice', newApiKey.value.unitPrice || 0)
  if (newApiKey.value.modelIds?.length > 0) newApiKey.value.modelIds.forEach(id => params.append('modelIds', id))
  const res = await api.post(`/api-keys?${params.toString()}`)
  if (res.code === 200) { showAddModal.value = false; resetForm(); loadData() }
  else alert(res.message || '创建失败')
}

function openEditModal(key) {
  editingKey.value = key
  editForm.value = { name: key.name || '', unitPrice: key.unitPrice || 0, modelIds: key.modelIds || [] }
  showEditModal.value = true
}

async function updateApiKey() {
  if (!editingKey.value) return
  const params = new URLSearchParams()
  if (editForm.value.name) params.append('name', editForm.value.name)
  params.append('unitPrice', editForm.value.unitPrice || 0)
  if (editForm.value.modelIds?.length > 0) editForm.value.modelIds.forEach(id => params.append('modelIds', id))
  const res = await api.put(`/api-keys/${editingKey.value.id}?${params.toString()}`)
  if (res.code === 200) { showEditModal.value = false; editingKey.value = null; loadData() }
  else alert(res.message || '更新失败')
}

async function deleteApiKey(id) {
  if (!confirm('确定删除该 API Key？')) return
  const res = await api.del(`/api-keys/${id}`)
  if (res.code === 200) loadData()
  else alert(res.message || '删除失败')
}

function copyKey(key) {
  navigator.clipboard.writeText(key)
  copiedKey.value = key
  setTimeout(() => { copiedKey.value = null }, 2000)
}

function resetForm() { newApiKey.value = { userId: '', name: '', unitPrice: 0, modelIds: [] } }

function getUserName(id) { return users.value.find(u => u.userId === id)?.username || users.value.find(u => u.id === id)?.username || id }
function getModelNames(modelIds) {
  if (!modelIds || modelIds.length === 0) return '全部模型'
  return modelIds.map(id => { const m = models.value.find(m => m.id === id); return m ? m.name : `#${id}` }).join('、')
}
function maskKey(key) {
  if (!key) return '-'
  if (key.length <= 12) return '****'
  return key.slice(0, 8) + '****' + key.slice(-4)
}
function toggleShowKey(id) { showKeyMap.value[id] = !showKeyMap.value[id] }
function fmtRelative(d) {
  if (!d) return '-'; const diff = Date.now() - new Date(d).getTime()
  if (diff < 60000) return '刚刚'; if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'
  if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'; return Math.floor(diff / 86400000) + '天前'
}

const filteredApiKeys = computed(() => {
  let list = apiKeys.value
  if (searchQuery.value) {
    const q = searchQuery.value.toLowerCase()
    list = list.filter(k => (k.name || '').toLowerCase().includes(q) || getUserName(k.userId).toLowerCase().includes(q))
  }
  if (statusFilter.value === 'active') list = list.filter(k => k.status === 0)
  if (statusFilter.value === 'disabled') list = list.filter(k => k.status !== 0)
  return list
})

const currentPage = ref(1)
const pageSize = 8
const totalPages = computed(() => Math.max(1, Math.ceil(filteredApiKeys.value.length / pageSize)))
const pagedApiKeys = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return filteredApiKeys.value.slice(start, start + pageSize)
})
function goToPage(page) { if (page >= 1 && page <= totalPages.value) currentPage.value = page }

const activeCount = computed(() => apiKeys.value.filter(k => k.status === 0).length)
const totalCalls = computed(() => apiKeys.value.reduce((s, k) => s + (k.totalCalls || 0), 0))
const boundUsers = computed(() => new Set(apiKeys.value.map(k => k.userId)).size)
</script>

<template>
  <div class="space-y-5 animate-fade-in">
    <!-- Top Stats -->
    <div class="grid grid-cols-2 md:grid-cols-4 gap-3">
      <div class="glass-card rounded-xl p-3.5">
        <div class="flex items-center justify-between mb-1">
          <span class="text-[11px] text-slate-500">密钥总数</span>
          <KeyRound class="w-3.5 h-3.5 text-cyan-400" />
        </div>
        <p class="text-2xl font-bold text-white">{{ apiKeys.length }}</p>
      </div>
      <div class="glass-card rounded-xl p-3.5">
        <div class="flex items-center justify-between mb-1">
          <span class="text-[11px] text-slate-500">启用中</span>
          <ShieldCheck class="w-3.5 h-3.5 text-emerald-400" />
        </div>
        <p class="text-2xl font-bold text-emerald-400">{{ activeCount }}</p>
      </div>
      <div class="glass-card rounded-xl p-3.5">
        <div class="flex items-center justify-between mb-1">
          <span class="text-[11px] text-slate-500">总调用</span>
          <Zap class="w-3.5 h-3.5 text-amber-400" />
        </div>
        <p class="text-2xl font-bold text-white">{{ totalCalls.toLocaleString() }}</p>
      </div>
      <div class="glass-card rounded-xl p-3.5">
        <div class="flex items-center justify-between mb-1">
          <span class="text-[11px] text-slate-500">绑定用户</span>
          <Users class="w-3.5 h-3.5 text-purple-400" />
        </div>
        <p class="text-2xl font-bold text-white">{{ boundUsers }}</p>
      </div>
    </div>

    <!-- Toolbar -->
    <div class="flex items-center gap-3 flex-wrap">
      <div class="relative flex-1 min-w-[200px] max-w-sm">
        <Search class="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-500" />
        <input v-model="searchQuery" class="w-full pl-9 pr-3 py-2 rounded-lg bg-white/[0.04] border border-white/[0.08] text-sm text-white placeholder-slate-600 focus:outline-none focus:border-emerald-500/40 focus:ring-1 focus:ring-emerald-500/20 transition-all" placeholder="搜索名称或用户..." />
      </div>
      <div class="flex items-center gap-1.5">
        <button @click="statusFilter = 'all'" :class="['px-3 py-1.5 text-xs rounded-lg transition-all', statusFilter === 'all' ? 'bg-emerald-500/15 text-emerald-400 border border-emerald-500/20' : 'bg-white/[0.03] text-slate-400 border border-white/[0.06] hover:bg-white/[0.06]']">全部</button>
        <button @click="statusFilter = 'active'" :class="['px-3 py-1.5 text-xs rounded-lg transition-all', statusFilter === 'active' ? 'bg-emerald-500/15 text-emerald-400 border border-emerald-500/20' : 'bg-white/[0.03] text-slate-400 border border-white/[0.06] hover:bg-white/[0.06]']">启用</button>
        <button @click="statusFilter = 'disabled'" :class="['px-3 py-1.5 text-xs rounded-lg transition-all', statusFilter === 'disabled' ? 'bg-emerald-500/15 text-emerald-400 border border-emerald-500/20' : 'bg-white/[0.03] text-slate-400 border border-white/[0.06] hover:bg-white/[0.06]']">禁用</button>
      </div>
      <button @click="showAddModal = true; resetForm()" class="ml-auto px-4 py-2 text-sm font-medium rounded-xl bg-gradient-to-r from-emerald-500 to-cyan-500 text-white shadow-lg shadow-emerald-500/20 hover:shadow-emerald-500/35 transition-all flex items-center gap-2">
        <Plus class="w-4 h-4" /> 创建 API Key
      </button>
    </div>

    <!-- Loading -->
    <div v-if="loading" class="flex items-center justify-center py-20">
      <div class="w-8 h-8 border-2 border-emerald-500/30 border-t-emerald-500 rounded-full animate-spin"></div>
    </div>

    <!-- Key List -->
    <div v-else-if="pagedApiKeys.length > 0" class="glass-card rounded-2xl overflow-hidden">
      <!-- List Header -->
      <div class="hidden md:grid grid-cols-12 gap-3 px-5 py-3 border-b border-white/[0.06] text-[11px] font-semibold text-slate-500 uppercase tracking-wider">
        <span class="col-span-3">密钥</span>
        <span class="col-span-2">绑定用户</span>
        <span class="col-span-3">Key</span>
        <span class="col-span-1">模型</span>
        <span class="col-span-1">单价</span>
        <span class="col-span-1">状态</span>
        <span class="col-span-1 text-right">操作</span>
      </div>
      <!-- List Rows -->
      <div v-for="key in pagedApiKeys" :key="key.id"
        :class="['group border-b border-white/[0.03] last:border-b-0 transition-colors hover:bg-white/[0.02]', key.status !== 0 ? 'opacity-50' : '']">
        <!-- Desktop Row -->
        <div class="hidden md:grid grid-cols-12 gap-3 items-center px-5 py-3.5">
          <!-- Name -->
          <div class="col-span-3 flex items-center gap-2.5 min-w-0">
            <div :class="['w-7 h-7 rounded-lg flex items-center justify-center flex-shrink-0', key.status === 0 ? 'bg-emerald-500/10' : 'bg-slate-500/10']">
              <KeyRound :class="['w-3.5 h-3.5', key.status === 0 ? 'text-emerald-400' : 'text-slate-500']" />
            </div>
            <div class="min-w-0">
              <p class="text-sm text-white font-medium truncate">{{ key.name }}</p>
              <p class="text-[10px] text-slate-500">{{ key.totalCalls || 0 }} 次调用 · {{ fmtRelative(key.createdAt) }}</p>
            </div>
          </div>
          <!-- User -->
          <div class="col-span-2">
            <span class="text-sm text-slate-300">{{ getUserName(key.userId) }}</span>
          </div>
          <!-- Key Value -->
          <div class="col-span-3">
            <div class="flex items-center gap-1.5 px-2.5 py-1.5 rounded-md bg-white/[0.02] border border-white/[0.04]">
              <code class="text-[11px] font-mono text-slate-400 truncate flex-1">{{ showKeyMap[key.id] ? (key.apiKey || '-') : maskKey(key.apiKey) }}</code>
              <button @click="toggleShowKey(key.id)" class="text-slate-500 hover:text-slate-300 transition-colors flex-shrink-0">
                <Eye v-if="!showKeyMap[key.id]" class="w-3 h-3" />
                <EyeOff v-else class="w-3 h-3" />
              </button>
              <button @click="copyKey(key.apiKey)" class="text-slate-500 hover:text-cyan-400 transition-colors flex-shrink-0">
                <Check v-if="copiedKey === key.apiKey" class="w-3 h-3 text-emerald-400" />
                <Copy v-else class="w-3 h-3" />
              </button>
            </div>
          </div>
          <!-- Models -->
          <div class="col-span-1">
            <span class="text-[11px] text-slate-400 truncate block" :title="getModelNames(key.modelIds)">{{ key.modelIds?.length || 0 ? key.modelIds.length + '个' : '全部' }}</span>
          </div>
          <!-- Price -->
          <div class="col-span-1">
            <span class="text-sm text-amber-400">¥{{ key.unitPrice || 0 }}</span>
          </div>
          <!-- Status -->
          <div class="col-span-1">
            <span :class="['inline-flex items-center gap-1 text-[10px] px-1.5 py-0.5 rounded font-medium', key.status === 0 ? 'bg-emerald-500/10 text-emerald-400' : 'bg-slate-500/10 text-slate-500']">
              <span :class="['w-1.5 h-1.5 rounded-full', key.status === 0 ? 'bg-emerald-400' : 'bg-slate-500']"></span>
              {{ key.status === 0 ? '启用' : '禁用' }}
            </span>
          </div>
          <!-- Actions -->
          <div class="col-span-1 flex items-center justify-end gap-1">
            <button @click="openEditModal(key)" class="p-1.5 rounded-md hover:bg-white/[0.06] text-slate-400 hover:text-white transition-colors" title="编辑"><Settings2 class="w-3.5 h-3.5" /></button>
            <button @click="deleteApiKey(key.id)" class="p-1.5 rounded-md hover:bg-red-500/10 text-slate-400 hover:text-red-400 transition-colors" title="删除"><Trash2 class="w-3.5 h-3.5" /></button>
          </div>
        </div>
        <!-- Mobile Row -->
        <div class="md:hidden p-4 space-y-3">
          <div class="flex items-center justify-between">
            <div class="flex items-center gap-2.5 min-w-0">
              <div :class="['w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0', key.status === 0 ? 'bg-emerald-500/10' : 'bg-slate-500/10']">
                <KeyRound :class="['w-4 h-4', key.status === 0 ? 'text-emerald-400' : 'text-slate-500']" />
              </div>
              <div class="min-w-0">
                <p class="text-sm text-white font-medium truncate">{{ key.name }}</p>
                <p class="text-[10px] text-slate-500">{{ getUserName(key.userId) }}</p>
              </div>
            </div>
            <span :class="['text-[10px] px-1.5 py-0.5 rounded font-medium', key.status === 0 ? 'bg-emerald-500/10 text-emerald-400' : 'bg-slate-500/10 text-slate-500']">{{ key.status === 0 ? '启用' : '禁用' }}</span>
          </div>
          <div class="flex items-center gap-1.5 px-2.5 py-1.5 rounded-md bg-white/[0.02] border border-white/[0.04]">
            <code class="text-[11px] font-mono text-slate-400 truncate flex-1">{{ showKeyMap[key.id] ? (key.apiKey || '-') : maskKey(key.apiKey) }}</code>
            <button @click="toggleShowKey(key.id)" class="text-slate-500 hover:text-slate-300"><Eye v-if="!showKeyMap[key.id]" class="w-3 h-3" /><EyeOff v-else class="w-3 h-3" /></button>
            <button @click="copyKey(key.apiKey)" class="text-slate-500 hover:text-cyan-400"><Check v-if="copiedKey === key.apiKey" class="w-3 h-3 text-emerald-400" /><Copy v-else class="w-3 h-3" /></button>
          </div>
          <div class="flex items-center justify-between text-[11px]">
            <span class="text-slate-500">{{ getModelNames(key.modelIds) }}</span>
            <span class="text-amber-400">¥{{ key.unitPrice || 0 }}/千Tokens</span>
          </div>
          <div class="flex items-center justify-between">
            <span class="text-[10px] text-slate-500">{{ key.totalCalls || 0 }} 次调用 · {{ fmtRelative(key.createdAt) }}</span>
            <div class="flex items-center gap-1">
              <button @click="openEditModal(key)" class="p-1.5 rounded-md hover:bg-white/[0.06] text-slate-400"><Settings2 class="w-3.5 h-3.5" /></button>
              <button @click="deleteApiKey(key.id)" class="p-1.5 rounded-md hover:bg-red-500/10 text-red-400"><Trash2 class="w-3.5 h-3.5" /></button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Empty -->
    <div v-else class="text-center py-20">
      <KeyRound class="w-10 h-10 text-slate-600 mx-auto mb-3" />
      <p class="text-sm text-slate-500">{{ searchQuery ? '未找到匹配的 API Key' : '暂无 API Key，点击上方按钮创建' }}</p>
    </div>

    <!-- Pagination -->
    <div v-if="filteredApiKeys.length > pageSize" class="flex items-center justify-between pt-2">
      <span class="text-xs text-slate-500">{{ filteredApiKeys.length }} 个 Key · 第 {{ currentPage }}/{{ totalPages }} 页</span>
      <div class="flex items-center gap-1">
        <button @click="goToPage(currentPage - 1)" :disabled="currentPage === 1" class="p-1.5 rounded-lg bg-white/5 text-slate-400 hover:bg-white/10 disabled:opacity-25 disabled:cursor-not-allowed transition-all"><ChevronLeft class="w-4 h-4" /></button>
        <template v-for="p in totalPages" :key="p">
          <button v-show="totalPages <= 7 || Math.abs(p - currentPage) < 2 || p === 1 || p === totalPages" @click="goToPage(p)"
            :class="['w-7 h-7 rounded-lg text-xs font-medium transition-all', p === currentPage ? 'bg-emerald-500/15 text-emerald-400' : 'bg-white/5 text-slate-400 hover:bg-white/10']">{{ p }}</button>
        </template>
        <button @click="goToPage(currentPage + 1)" :disabled="currentPage === totalPages" class="p-1.5 rounded-lg bg-white/5 text-slate-400 hover:bg-white/10 disabled:opacity-25 disabled:cursor-not-allowed transition-all"><ChevronRight class="w-4 h-4" /></button>
      </div>
    </div>

    <!-- Create Modal -->
    <div v-if="showAddModal" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm" @click.self="showAddModal = false">
      <div class="glass-card w-full max-w-lg rounded-2xl overflow-hidden animate-slide-up">
        <div class="flex items-center justify-between px-5 py-4 border-b border-white/5">
          <h2 class="text-base font-semibold text-white">创建 API Key</h2>
          <button @click="showAddModal = false" class="p-1.5 rounded-lg hover:bg-white/5 text-slate-400 hover:text-white transition-colors"><X class="w-4 h-4" /></button>
        </div>
        <div class="p-5 space-y-4">
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-[11px] text-slate-500 mb-1.5">密钥名称 *</label>
              <input v-model="newApiKey.name" class="w-full px-3 py-2 rounded-lg bg-white/[0.04] border border-white/[0.08] text-sm text-white placeholder-slate-600 focus:outline-none focus:border-emerald-500/40 focus:ring-1 focus:ring-emerald-500/20 transition-all" placeholder="如：生产环境密钥" />
            </div>
            <div>
              <label class="block text-[11px] text-slate-500 mb-1.5">绑定用户 *</label>
              <select v-model="newApiKey.userId" class="w-full px-3 py-2 rounded-lg bg-white/[0.04] border border-white/[0.08] text-sm text-white focus:outline-none focus:border-emerald-500/40 focus:ring-1 focus:ring-emerald-500/20 transition-all">
                <option value="">请选择用户</option>
                <option v-for="u in users" :key="u.userId" :value="u.userId">{{ u.username }}</option>
              </select>
            </div>
          </div>
          <div>
            <label class="block text-[11px] text-slate-500 mb-1.5">Token 单价 (元/千Tokens)</label>
            <input v-model.number="newApiKey.unitPrice" type="number" step="0.001" min="0" class="w-full px-3 py-2 rounded-lg bg-white/[0.04] border border-white/[0.08] text-sm text-white placeholder-slate-600 focus:outline-none focus:border-emerald-500/40 focus:ring-1 focus:ring-emerald-500/20 transition-all" placeholder="0" />
          </div>
          <div>
            <label class="block text-[11px] text-slate-500 mb-1.5">绑定模型（不选则允许全部）</label>
            <div class="max-h-36 overflow-y-auto space-y-1.5 p-3 rounded-lg bg-white/[0.02] border border-white/[0.06]">
              <div v-for="model in models" :key="model.id" class="flex items-center gap-2">
                <input type="checkbox" :id="'model-' + model.id" :value="model.id" v-model="newApiKey.modelIds" class="w-3.5 h-3.5 rounded border-slate-600 bg-slate-800 text-emerald-500 focus:ring-emerald-500" />
                <label :for="'model-' + model.id" class="text-xs text-slate-300 cursor-pointer">{{ model.name }} <span class="text-slate-500">({{ model.provider }})</span></label>
              </div>
              <div v-if="models.length === 0" class="text-xs text-slate-500">暂无可用模型</div>
            </div>
          </div>
          <div class="p-2.5 rounded-lg bg-amber-500/5 border border-amber-500/10">
            <p class="text-[11px] text-amber-400/80">API Key 生成后，用户可通过该 Key 访问绑定的模型服务，按单价计费。</p>
          </div>
        </div>
        <div class="flex gap-3 px-5 py-4 border-t border-white/5">
          <button @click="showAddModal = false" class="flex-1 py-2.5 text-sm font-medium rounded-xl bg-white/[0.06] border border-white/[0.08] text-white hover:bg-white/[0.1] transition-all">取消</button>
          <button @click="createApiKey" class="flex-1 py-2.5 text-sm font-medium rounded-xl bg-gradient-to-r from-emerald-500 to-cyan-500 text-white shadow-lg shadow-emerald-500/20 hover:shadow-emerald-500/35 transition-all">创建</button>
        </div>
      </div>
    </div>

    <!-- Edit Modal -->
    <div v-if="showEditModal" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm" @click.self="showEditModal = false">
      <div class="glass-card w-full max-w-lg rounded-2xl overflow-hidden animate-slide-up">
        <div class="flex items-center justify-between px-5 py-4 border-b border-white/5">
          <h2 class="text-base font-semibold text-white">编辑 API Key</h2>
          <button @click="showEditModal = false" class="p-1.5 rounded-lg hover:bg-white/5 text-slate-400 hover:text-white transition-colors"><X class="w-4 h-4" /></button>
        </div>
        <div class="p-5 space-y-4">
          <div>
            <label class="block text-[11px] text-slate-500 mb-1.5">密钥名称</label>
            <input v-model="editForm.name" class="w-full px-3 py-2 rounded-lg bg-white/[0.04] border border-white/[0.08] text-sm text-white placeholder-slate-600 focus:outline-none focus:border-emerald-500/40 focus:ring-1 focus:ring-emerald-500/20 transition-all" />
          </div>
          <div>
            <label class="block text-[11px] text-slate-500 mb-1.5">Token 单价 (元/千Tokens)</label>
            <input v-model.number="editForm.unitPrice" type="number" step="0.001" min="0" class="w-full px-3 py-2 rounded-lg bg-white/[0.04] border border-white/[0.08] text-sm text-white focus:outline-none focus:border-emerald-500/40 focus:ring-1 focus:ring-emerald-500/20 transition-all" />
          </div>
          <div>
            <label class="block text-[11px] text-slate-500 mb-1.5">绑定模型（不选则允许全部）</label>
            <div class="max-h-36 overflow-y-auto space-y-1.5 p-3 rounded-lg bg-white/[0.02] border border-white/[0.06]">
              <div v-for="model in models" :key="model.id" class="flex items-center gap-2">
                <input type="checkbox" :id="'edit-model-' + model.id" :value="model.id" v-model="editForm.modelIds" class="w-3.5 h-3.5 rounded border-slate-600 bg-slate-800 text-emerald-500 focus:ring-emerald-500" />
                <label :for="'edit-model-' + model.id" class="text-xs text-slate-300 cursor-pointer">{{ model.name }} <span class="text-slate-500">({{ model.provider }})</span></label>
              </div>
              <div v-if="models.length === 0" class="text-xs text-slate-500">暂无可用模型</div>
            </div>
          </div>
        </div>
        <div class="flex gap-3 px-5 py-4 border-t border-white/5">
          <button @click="showEditModal = false" class="flex-1 py-2.5 text-sm font-medium rounded-xl bg-white/[0.06] border border-white/[0.08] text-white hover:bg-white/[0.1] transition-all">取消</button>
          <button @click="updateApiKey" class="flex-1 py-2.5 text-sm font-medium rounded-xl bg-gradient-to-r from-emerald-500 to-cyan-500 text-white shadow-lg shadow-emerald-500/20 hover:shadow-emerald-500/35 transition-all">保存</button>
        </div>
      </div>
    </div>
  </div>
</template>
