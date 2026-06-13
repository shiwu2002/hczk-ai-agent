<script setup>
import { ref, onMounted } from 'vue'
import { useApiStore } from '@/stores/api'
import {
  Plus, Power, Settings2, Trash2, CheckCircle2, XCircle,
  Cpu, Gauge, DollarSign
} from 'lucide-vue-next'

const api = useApiStore()
const loading = ref(false)
const showAddModal = ref(false)
const editingModel = ref(null)

const models = ref([])

const newModel = ref({
  name: '',
  provider: '',
  modelId: '',
  apiBase: '',
  apiKey: '',
  pricing: { input: 0, output: 0 },
  maxTokens: 4096
})

onMounted(loadData)

async function loadData() {
  loading.value = true
  const res = await api.get('/models')
  if (res.code === 200) {
    models.value = res.data || []
  }
  loading.value = false
}

async function toggleStatus(model) {
  const res = await api.post(`/models/${model.id}/toggle`)
  if (res.code === 200) {
    model.status = res.data.status
  } else {
    alert(res.message || '操作失败')
  }
}

async function deleteModel(model) {
  if (!confirm('确定删除该模型？')) return
  const res = await api.del(`/models/${model.id}`)
  if (res.code === 200) {
    loadData()
  } else {
    alert(res.message || '删除失败')
  }
}

async function saveModel() {
  const body = editingModel.value ? { ...editingModel.value, ...newModel.value } : { ...newModel.value }
  if (editingModel.value) {
    const res = await api.put(`/models/${editingModel.value.id}`, body)
    if (res.code === 200) {
      showAddModal.value = false
      editingModel.value = null
      resetForm()
      loadData()
    } else {
      alert(res.message || '更新失败')
    }
  } else {
    const res = await api.post('/models', body)
    if (res.code === 200) {
      showAddModal.value = false
      resetForm()
      loadData()
    } else {
      alert(res.message || '创建失败')
    }
  }
}

function editModel(model) {
  editingModel.value = { ...model }
  newModel.value = {
    name: model.name,
    provider: model.provider,
    modelId: model.modelId,
    apiBase: model.apiBase,
    apiKey: model.apiKey || '',
    pricing: { input: model.pricing?.input || 0, output: model.pricing?.output || 0 },
    maxTokens: model.maxTokens || 4096
  }
  showAddModal.value = true
}

function resetForm() {
  newModel.value = {
    name: '', provider: '', modelId: '', apiBase: '', apiKey: '',
    pricing: { input: 0, output: 0 }, maxTokens: 4096
  }
}
</script>

<template>
  <div class="space-y-6 animate-fade-in">
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-2xl font-bold text-white">模型管理</h1>
        <p class="text-slate-400 mt-1">接入国内大模型，配置 API 与计费参数</p>
      </div>
      <button @click="showAddModal = true; editingModel = null; resetForm()" class="btn-primary flex items-center gap-2">
        <Plus class="w-4 h-4" /> 接入模型
      </button>
    </div>

    <div v-if="loading" class="text-slate-500">加载中...</div>
    <!-- Models Grid -->
    <div v-else class="grid grid-cols-1 lg:grid-cols-2 gap-6">
      <div v-for="model in models" :key="model.id" class="glass-card p-6 glow-border">
        <div class="flex items-start justify-between mb-4">
          <div class="flex items-center gap-3">
            <div class="w-10 h-10 rounded-xl bg-gradient-to-br from-emerald-500/20 to-cyan-500/20 flex items-center justify-center">
              <Cpu class="w-5 h-5 text-emerald-400" />
            </div>
            <div>
              <h3 class="text-lg font-semibold text-white">{{ model.name }}</h3>
              <p class="text-sm text-slate-400">{{ model.provider }}</p>
            </div>
          </div>
          <div class="flex items-center gap-2">
            <button
              @click="toggleStatus(model)"
              :class="['p-2 rounded-lg transition-colors', model.status === 'ACTIVE' ? 'bg-emerald-500/10 text-emerald-400' : 'bg-slate-500/10 text-slate-400']"
            >
              <Power class="w-4 h-4" />
            </button>
            <button @click="editModel(model)" class="p-2 rounded-lg bg-white/5 text-slate-300 hover:bg-white/10">
              <Settings2 class="w-4 h-4" />
            </button>
            <button @click="deleteModel(model)" class="p-2 rounded-lg bg-white/5 text-red-400 hover:bg-red-500/10">
              <Trash2 class="w-4 h-4" />
            </button>
          </div>
        </div>

        <div class="space-y-3">
          <div class="flex items-center justify-between py-2 border-b border-white/5">
            <span class="text-sm text-slate-400">模型 ID</span>
            <code class="text-sm text-emerald-400 font-mono">{{ model.modelId }}</code>
          </div>
          <div class="flex items-center justify-between py-2 border-b border-white/5">
            <span class="text-sm text-slate-400">API 地址</span>
            <span class="text-sm text-slate-300 font-mono truncate max-w-[200px]">{{ model.apiBase }}</span>
          </div>
          <div class="flex items-center justify-between py-2 border-b border-white/5">
            <span class="text-sm text-slate-400">输入价格</span>
            <span class="text-sm text-white">¥{{ model.pricing?.input || 0 }} / 1K tokens</span>
          </div>
          <div class="flex items-center justify-between py-2 border-b border-white/5">
            <span class="text-sm text-slate-400">输出价格</span>
            <span class="text-sm text-white">¥{{ model.pricing?.output || 0 }} / 1K tokens</span>
          </div>
          <div class="flex items-center justify-between py-2">
            <span class="text-sm text-slate-400">最大上下文</span>
            <span class="text-sm text-white">{{ ((model.maxTokens || 0) / 1024).toFixed(0) }}K tokens</span>
          </div>
        </div>

        <div class="mt-4 pt-4 border-t border-white/5 flex items-center justify-between">
          <div class="flex items-center gap-2">
            <Gauge class="w-4 h-4 text-slate-400" />
            <span class="text-sm text-slate-400">{{ model.qps || 0 }} QPS</span>
          </div>
          <span :class="['px-2 py-1 text-xs rounded-full border', model.status === 'ACTIVE' ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20' : 'bg-slate-500/10 text-slate-400 border-slate-500/20']">
            {{ model.status === 'ACTIVE' ? '运行中' : '已停用' }}
          </span>
        </div>
      </div>
    </div>

    <!-- Add/Edit Modal -->
    <div v-if="showAddModal" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm">
      <div class="glass-card w-full max-w-lg p-6 animate-slide-up">
        <h2 class="text-xl font-bold text-white mb-6">{{ editingModel ? '编辑模型' : '接入新模型' }}</h2>
        <div class="space-y-4">
          <div class="grid grid-cols-2 gap-4">
            <div>
              <label class="block text-sm text-slate-300 mb-2">模型名称</label>
              <input v-model="newModel.name" class="input-field" placeholder="如 DeepSeek-V3" />
            </div>
            <div>
              <label class="block text-sm text-slate-300 mb-2">提供商</label>
              <input v-model="newModel.provider" class="input-field" placeholder="如 DeepSeek" />
            </div>
          </div>
          <div>
            <label class="block text-sm text-slate-300 mb-2">模型 ID</label>
            <input v-model="newModel.modelId" class="input-field" placeholder="如 deepseek-chat" />
          </div>
          <div>
            <label class="block text-sm text-slate-300 mb-2">API Base URL</label>
            <input v-model="newModel.apiBase" class="input-field" placeholder="https://api.example.com/v1" />
          </div>
          <div>
            <label class="block text-sm text-slate-300 mb-2">API Key</label>
            <input v-model="newModel.apiKey" type="password" class="input-field" placeholder="sk-..." />
          </div>
          <div class="grid grid-cols-3 gap-4">
            <div>
              <label class="block text-sm text-slate-300 mb-2">输入价格 (元/1K)</label>
              <input v-model.number="newModel.pricing.input" type="number" step="0.001" class="input-field" />
            </div>
            <div>
              <label class="block text-sm text-slate-300 mb-2">输出价格 (元/1K)</label>
              <input v-model.number="newModel.pricing.output" type="number" step="0.001" class="input-field" />
            </div>
            <div>
              <label class="block text-sm text-slate-300 mb-2">最大 Tokens</label>
              <input v-model.number="newModel.maxTokens" type="number" class="input-field" />
            </div>
          </div>
        </div>
        <div class="flex gap-3 mt-6">
          <button @click="showAddModal = false" class="btn-secondary flex-1">取消</button>
          <button @click="saveModel" class="btn-primary flex-1">保存</button>
        </div>
      </div>
    </div>
  </div>
</template>
