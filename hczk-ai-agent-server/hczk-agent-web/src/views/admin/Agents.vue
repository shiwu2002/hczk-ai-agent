<script setup>
import { ref, onMounted } from 'vue'
import { useApiStore } from '@/stores/api'
import {
  Plus, Bot, MessageSquare, Settings2, Trash2, Copy,
  Power, ExternalLink
} from 'lucide-vue-next'

const api = useApiStore()
const loading = ref(false)
const showAddModal = ref(false)
const editingAgent = ref(null)

const agents = ref([])
const models = ref([])

const platforms = ref([
  { name: '美团', connected: true, icon: 'M' },
  { name: '抖音', connected: false, icon: 'D' }
])

const newAgent = ref({
  name: '',
  description: '',
  modelId: '',
  type: 'customer_service'
})

onMounted(loadData)

async function loadData() {
  loading.value = true
  const [agentRes, modelRes] = await Promise.all([
    api.get('/agents'),
    api.get('/models')
  ])
  if (agentRes.code === 200) agents.value = agentRes.data || []
  if (modelRes.code === 200) models.value = modelRes.data || []
  loading.value = false
}

async function toggleStatus(agent) {
  const res = await api.post(`/agents/${agent.id}/toggle`)
  if (res.code === 200) {
    agent.status = res.data.status
  } else {
    alert(res.message || '操作失败')
  }
}

async function deleteAgent(id) {
  if (!confirm('确定删除该智能体？')) return
  const res = await api.del(`/agents/${id}`)
  if (res.code === 200) {
    loadData()
  } else {
    alert(res.message || '删除失败')
  }
}

async function saveAgent() {
  const body = {
    name: newAgent.value.name,
    description: newAgent.value.description,
    model: { id: parseInt(newAgent.value.modelId) },
    type: newAgent.value.type
  }
  if (editingAgent.value) {
    const res = await api.put(`/agents/${editingAgent.value.id}`, body)
    if (res.code === 200) {
      showAddModal.value = false
      editingAgent.value = null
      resetForm()
      loadData()
    } else {
      alert(res.message || '更新失败')
    }
  } else {
    const res = await api.post('/agents', body)
    if (res.code === 200) {
      showAddModal.value = false
      resetForm()
      loadData()
    } else {
      alert(res.message || '创建失败')
    }
  }
}

function editAgent(agent) {
  editingAgent.value = { ...agent }
  newAgent.value = {
    name: agent.name,
    description: agent.description || '',
    modelId: agent.model?.id || '',
    type: agent.type || 'customer_service'
  }
  showAddModal.value = true
}

function resetForm() {
  newAgent.value = { name: '', description: '', modelId: '', type: 'customer_service' }
}
</script>

<template>
  <div class="space-y-6 animate-fade-in">
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-2xl font-bold text-white">智能体管理</h1>
        <p class="text-slate-400 mt-1">管理平台所有 AI 智能体及其配置</p>
      </div>
      <button @click="showAddModal = true; editingAgent = null; resetForm()" class="btn-primary flex items-center gap-2">
        <Plus class="w-4 h-4" /> 创建智能体
      </button>
    </div>

    <div v-if="loading" class="text-slate-500">加载中...</div>
    <!-- Agents List -->
    <div v-else class="grid grid-cols-1 gap-4">
      <div v-for="agent in agents" :key="agent.id" class="glass-card p-6 glow-border">
        <div class="flex items-start justify-between">
          <div class="flex items-start gap-4">
            <div class="w-12 h-12 rounded-xl bg-gradient-to-br from-emerald-500/20 to-cyan-500/20 flex items-center justify-center">
              <Bot class="w-6 h-6 text-emerald-400" />
            </div>
            <div>
              <div class="flex items-center gap-3">
                <h3 class="text-lg font-semibold text-white">{{ agent.name }}</h3>
                <span :class="['px-2 py-0.5 text-xs rounded-full border', agent.status === 'ACTIVE' ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20' : 'bg-slate-500/10 text-slate-400 border-slate-500/20']">
                  {{ agent.status === 'ACTIVE' ? '运行中' : '已停用' }}
                </span>
              </div>
              <p class="text-sm text-slate-400 mt-1">{{ agent.description || '无描述' }}</p>
              <div class="flex items-center gap-4 mt-3">
                <span class="text-xs text-slate-500 bg-white/5 px-2 py-1 rounded">{{ agent.model?.name || '-' }}</span>
                <span class="text-xs text-slate-500 flex items-center gap-1">
                  <MessageSquare class="w-3 h-3" /> {{ (agent.totalCalls || 0).toLocaleString() }} 次调用
                </span>
                <span class="text-xs text-slate-500">{{ agent.avgLatency || '-' }} 平均延迟</span>
              </div>
            </div>
          </div>
          <div class="flex items-center gap-2">
            <button @click="toggleStatus(agent)" :class="['p-2 rounded-lg transition-colors', agent.status === 'ACTIVE' ? 'bg-emerald-500/10 text-emerald-400' : 'bg-slate-500/10 text-slate-400']">
              <Power class="w-4 h-4" />
            </button>
            <button @click="editAgent(agent)" class="p-2 rounded-lg bg-white/5 text-slate-300 hover:bg-white/10">
              <Settings2 class="w-4 h-4" />
            </button>
            <button class="p-2 rounded-lg bg-white/5 text-slate-300 hover:bg-white/10">
              <ExternalLink class="w-4 h-4" />
            </button>
            <button @click="deleteAgent(agent.id)" class="p-2 rounded-lg bg-white/5 text-red-400 hover:bg-red-500/10">
              <Trash2 class="w-4 h-4" />
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- Platform Integration -->
    <div class="glass-card p-6">
      <h3 class="text-lg font-semibold text-white mb-4">平台对接状态</h3>
      <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div v-for="platform in platforms" :key="platform.name" class="flex items-center justify-between p-4 rounded-lg bg-white/5">
          <div class="flex items-center gap-3">
            <div class="w-10 h-10 rounded-lg bg-gradient-to-br from-yellow-500/20 to-red-500/20 flex items-center justify-center text-lg font-bold text-white">
              {{ platform.icon }}
            </div>
            <div>
              <p class="text-white font-medium">{{ platform.name }} 智能客服</p>
              <p class="text-sm text-slate-400">{{ platform.connected ? '已接入' : '未接入' }}</p>
            </div>
          </div>
          <button :class="['px-4 py-2 rounded-lg text-sm font-medium transition-colors', platform.connected ? 'bg-emerald-500/10 text-emerald-400' : 'btn-primary']">
            {{ platform.connected ? '管理' : '接入' }}
          </button>
        </div>
      </div>
    </div>

    <!-- Add/Edit Modal -->
    <div v-if="showAddModal" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm">
      <div class="glass-card w-full max-w-lg p-6 animate-slide-up">
        <h2 class="text-xl font-bold text-white mb-6">{{ editingAgent ? '编辑智能体' : '创建智能体' }}</h2>
        <div class="space-y-4">
          <div>
            <label class="block text-sm text-slate-300 mb-2">智能体名称</label>
            <input v-model="newAgent.name" class="input-field" placeholder="如：客服助手" />
          </div>
          <div>
            <label class="block text-sm text-slate-300 mb-2">描述</label>
            <textarea v-model="newAgent.description" class="input-field h-20 resize-none" placeholder="描述该智能体的用途..."></textarea>
          </div>
          <div class="grid grid-cols-2 gap-4">
            <div>
              <label class="block text-sm text-slate-300 mb-2">选择模型</label>
              <select v-model="newAgent.modelId" class="input-field">
                <option value="">请选择模型</option>
                <option v-for="m in models" :key="m.id" :value="m.id">{{ m.name }}</option>
              </select>
            </div>
            <div>
              <label class="block text-sm text-slate-300 mb-2">类型</label>
              <select v-model="newAgent.type" class="input-field">
                <option value="customer_service">客服</option>
                <option value="sales">销售</option>
                <option value="support">技术支持</option>
                <option value="custom">自定义</option>
              </select>
            </div>
          </div>
        </div>
        <div class="flex gap-3 mt-6">
          <button @click="showAddModal = false" class="btn-secondary flex-1">取消</button>
          <button @click="saveAgent" class="btn-primary flex-1">{{ editingAgent ? '保存' : '创建' }}</button>
        </div>
      </div>
    </div>
  </div>
</template>
