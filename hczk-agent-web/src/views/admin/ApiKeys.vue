<script setup>
import { ref, onMounted, computed } from 'vue'
import { useApiStore } from '@/stores/api'
import { KeyRound, Plus, Search, Trash2, Copy, Check, X, Settings2 } from 'lucide-vue-next'

const api = useApiStore()
const loading = ref(false)
const showAddModal = ref(false)
const showEditModal = ref(false)
const searchQuery = ref('')

const users = ref([])
const apiKeys = ref([])
const models = ref([])

const newApiKey = ref({
  userId: '',
  name: '',
  unitPrice: 0,
  modelIds: []
})

const editingKey = ref(null)
const editForm = ref({
  name: '',
  unitPrice: 0,
  modelIds: []
})

const copiedKey = ref(null)

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
  if (modelRes.code === 200) models.value = (modelRes.data || []).filter(m => m.status === 'ACTIVE')
  loading.value = false
}

async function createApiKey() {
  if (!newApiKey.value.userId) {
    alert('请选择用户')
    return
  }
  if (!newApiKey.value.name) {
    alert('请输入密钥名称')
    return
  }

  const params = new URLSearchParams()
  params.append('userId', newApiKey.value.userId)
  params.append('name', newApiKey.value.name)
  params.append('unitPrice', newApiKey.value.unitPrice || 0)
  if (newApiKey.value.modelIds && newApiKey.value.modelIds.length > 0) {
    newApiKey.value.modelIds.forEach(id => params.append('modelIds', id))
  }

  const res = await api.post(`/api-keys?${params.toString()}`)
  if (res.code === 200) {
    showAddModal.value = false
    resetForm()
    loadData()
  } else {
    alert(res.message || '创建失败')
  }
}

function openEditModal(key) {
  editingKey.value = key
  editForm.value = {
    name: key.name || '',
    unitPrice: key.unitPrice || 0,
    modelIds: key.modelIds || []
  }
  showEditModal.value = true
}

async function updateApiKey() {
  if (!editingKey.value) return

  const params = new URLSearchParams()
  if (editForm.value.name) params.append('name', editForm.value.name)
  params.append('unitPrice', editForm.value.unitPrice || 0)
  if (editForm.value.modelIds && editForm.value.modelIds.length > 0) {
    editForm.value.modelIds.forEach(id => params.append('modelIds', id))
  }

  const res = await api.put(`/api-keys/${editingKey.value.id}?${params.toString()}`)
  if (res.code === 200) {
    showEditModal.value = false
    editingKey.value = null
    loadData()
  } else {
    alert(res.message || '更新失败')
  }
}

async function deleteApiKey(id) {
  if (!confirm('确定删除该 API Key？')) return
  const res = await api.del(`/api-keys/${id}`)
  if (res.code === 200) {
    loadData()
  } else {
    alert(res.message || '删除失败')
  }
}

function copyKey(key) {
  navigator.clipboard.writeText(key)
  copiedKey.value = key
  setTimeout(() => {
    copiedKey.value = null
  }, 2000)
}

function resetForm() {
  newApiKey.value = { userId: '', name: '', unitPrice: 0, modelIds: [] }
}

function getUserName(id) {
  return users.value.find(u => u.id === id)?.username || id
}

function getModelNames(modelIds) {
  if (!modelIds || modelIds.length === 0) return '全部模型'
  return modelIds.map(id => {
    const m = models.value.find(m => m.id === id)
    return m ? m.name : `#${id}`
  }).join('、')
}

const filteredApiKeys = computed(() => {
  if (!searchQuery.value) return apiKeys.value
  const q = searchQuery.value.toLowerCase()
  return apiKeys.value.filter(k =>
    (k.name || '').toLowerCase().includes(q) ||
    getUserName(k.userId).toLowerCase().includes(q)
  )
})
</script>

<template>
  <div class="space-y-6 animate-fade-in">
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-2xl font-bold text-white">API Keys</h1>
        <p class="text-slate-400 mt-1">管理用户的 API Key，用于访问智能体服务</p>
      </div>
      <button @click="showAddModal = true; resetForm()" class="btn-primary flex items-center gap-2">
        <Plus class="w-4 h-4" /> 创建 API Key
      </button>
    </div>

    <div class="flex gap-4">
      <div class="flex-1 relative">
        <Search class="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-500" />
        <input v-model="searchQuery" class="input-field pl-10" placeholder="搜索密钥名称..." />
      </div>
    </div>

    <div v-if="loading" class="text-slate-500">加载中...</div>
    <div v-else-if="apiKeys.length === 0" class="text-slate-500">暂无 API Key</div>
    <div v-else class="glass-card overflow-hidden">
      <table class="w-full">
        <thead>
          <tr class="border-b border-white/10">
            <th class="px-6 py-4 text-left text-sm font-medium text-slate-400">密钥名称</th>
            <th class="px-6 py-4 text-left text-sm font-medium text-slate-400">绑定用户</th>
            <th class="px-6 py-4 text-left text-sm font-medium text-slate-400">绑定模型</th>
            <th class="px-6 py-4 text-left text-sm font-medium text-slate-400">单价 (元/千Tokens)</th>
            <th class="px-6 py-4 text-left text-sm font-medium text-slate-400">调用次数</th>
            <th class="px-6 py-4 text-left text-sm font-medium text-slate-400">状态</th>
            <th class="px-6 py-4 text-left text-sm font-medium text-slate-400">创建时间</th>
            <th class="px-6 py-4 text-left text-sm font-medium text-slate-400">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="key in filteredApiKeys" :key="key.id" class="border-b border-white/5 hover:bg-white/5">
            <td class="px-6 py-4">
              <div class="flex items-center gap-2">
                <KeyRound class="w-4 h-4 text-emerald-400" />
                <span class="text-white font-medium">{{ key.name }}</span>
              </div>
            </td>
            <td class="px-6 py-4 text-slate-300">{{ getUserName(key.userId) }}</td>
            <td class="px-6 py-4 text-slate-300 text-sm">{{ getModelNames(key.modelIds) }}</td>
            <td class="px-6 py-4 text-amber-400">{{ key.unitPrice || 0 }}</td>
            <td class="px-6 py-4 text-slate-300">{{ key.totalCalls || 0 }}</td>
            <td class="px-6 py-4">
              <span :class="['px-2 py-0.5 text-xs rounded-full', key.status === 'active' ? 'bg-emerald-500/10 text-emerald-400' : 'bg-slate-500/10 text-slate-400']">
                {{ key.status === 'active' ? '启用' : '禁用' }}
              </span>
            </td>
            <td class="px-6 py-4 text-slate-500 text-sm">{{ key.createdAt }}</td>
            <td class="px-6 py-4">
              <div class="flex items-center gap-2">
                <button @click="copyKey(key.apiKey)" class="p-2 rounded-lg bg-white/5 text-slate-300 hover:bg-white/10" title="复制Key">
                  <component :is="copiedKey === key.apiKey ? Check : Copy" class="w-4 h-4" />
                </button>
                <button @click="openEditModal(key)" class="p-2 rounded-lg bg-white/5 text-slate-300 hover:bg-white/10" title="编辑">
                  <Settings2 class="w-4 h-4" />
                </button>
                <button @click="deleteApiKey(key.id)" class="p-2 rounded-lg bg-white/5 text-red-400 hover:bg-red-500/10" title="删除">
                  <Trash2 class="w-4 h-4" />
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="showAddModal" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm">
      <div class="glass-card w-full max-w-lg p-6 animate-slide-up">
        <div class="flex items-center justify-between mb-4">
          <h2 class="text-xl font-bold text-white">创建 API Key</h2>
          <button @click="showAddModal = false" class="p-1 text-slate-400 hover:text-white">
            <X class="w-5 h-5" />
          </button>
        </div>
        <div class="space-y-4">
          <div>
            <label class="block text-sm text-slate-300 mb-2">密钥名称 *</label>
            <input v-model="newApiKey.name" class="input-field" placeholder="如：生产环境密钥" />
          </div>
          <div>
            <label class="block text-sm text-slate-300 mb-2">绑定用户 *</label>
            <select v-model="newApiKey.userId" class="input-field">
              <option value="">请选择用户</option>
              <option v-for="u in users" :key="u.id" :value="u.id">{{ u.username }}</option>
            </select>
          </div>
          <div>
            <label class="block text-sm text-slate-300 mb-2">统一Token单价 (元/千Tokens)</label>
            <input v-model.number="newApiKey.unitPrice" type="number" step="0.001" min="0" class="input-field" placeholder="0" />
          </div>
          <div>
            <label class="block text-sm text-slate-300 mb-2">绑定的大模型（不选则允许全部）</label>
            <div class="max-h-40 overflow-y-auto space-y-2 p-3 rounded-lg bg-slate-800/50 border border-white/10">
              <div v-for="model in models" :key="model.id" class="flex items-center gap-2">
                <input
                  type="checkbox"
                  :id="'model-' + model.id"
                  :value="model.id"
                  v-model="newApiKey.modelIds"
                  class="w-4 h-4 rounded border-slate-600 bg-slate-800 text-emerald-500 focus:ring-emerald-500"
                />
                <label :for="'model-' + model.id" class="text-sm text-slate-300 cursor-pointer">{{ model.name }} ({{ model.provider }})</label>
              </div>
              <div v-if="models.length === 0" class="text-sm text-slate-500">暂无可用模型</div>
            </div>
          </div>
          <div class="p-3 rounded-lg bg-amber-500/10 border border-amber-500/20">
            <p class="text-sm text-amber-400">提示：API Key 生成后，用户可以通过该 Key 访问绑定的模型服务，按单价计费。</p>
          </div>
        </div>
        <div class="flex gap-3 mt-6">
          <button @click="showAddModal = false" class="btn-secondary flex-1">取消</button>
          <button @click="createApiKey" class="btn-primary flex-1">创建</button>
        </div>
      </div>
    </div>

    <!-- 编辑 API Key 弹窗 -->
    <div v-if="showEditModal" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm">
      <div class="glass-card w-full max-w-lg p-6 animate-slide-up">
        <div class="flex items-center justify-between mb-4">
          <h2 class="text-xl font-bold text-white">编辑 API Key</h2>
          <button @click="showEditModal = false" class="p-1 text-slate-400 hover:text-white">
            <X class="w-5 h-5" />
          </button>
        </div>
        <div class="space-y-4">
          <div>
            <label class="block text-sm text-slate-300 mb-2">密钥名称</label>
            <input v-model="editForm.name" class="input-field" placeholder="密钥名称" />
          </div>
          <div>
            <label class="block text-sm text-slate-300 mb-2">统一Token单价 (元/千Tokens)</label>
            <input v-model.number="editForm.unitPrice" type="number" step="0.001" min="0" class="input-field" />
          </div>
          <div>
            <label class="block text-sm text-slate-300 mb-2">绑定的大模型（不选则允许全部）</label>
            <div class="max-h-40 overflow-y-auto space-y-2 p-3 rounded-lg bg-slate-800/50 border border-white/10">
              <div v-for="model in models" :key="model.id" class="flex items-center gap-2">
                <input
                  type="checkbox"
                  :id="'edit-model-' + model.id"
                  :value="model.id"
                  v-model="editForm.modelIds"
                  class="w-4 h-4 rounded border-slate-600 bg-slate-800 text-emerald-500 focus:ring-emerald-500"
                />
                <label :for="'edit-model-' + model.id" class="text-sm text-slate-300 cursor-pointer">{{ model.name }} ({{ model.provider }})</label>
              </div>
              <div v-if="models.length === 0" class="text-sm text-slate-500">暂无可用模型</div>
            </div>
          </div>
        </div>
        <div class="flex gap-3 mt-6">
          <button @click="showEditModal = false" class="btn-secondary flex-1">取消</button>
          <button @click="updateApiKey" class="btn-primary flex-1">保存</button>
        </div>
      </div>
    </div>
  </div>
</template>