<script setup>
import { ref, onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useApiStore } from '@/stores/api'
import { Bot, Plus, Settings2, ExternalLink, Power, Trash2 } from 'lucide-vue-next'

const authStore = useAuthStore()
const api = useApiStore()

const agents = ref([])
const models = ref([])
const loading = ref(false)
const showCreate = ref(false)
const newAgent = ref({ name: '', description: '', modelId: '' })

onMounted(loadData)

async function loadData() {
  loading.value = true
  const [agentRes, modelRes] = await Promise.all([
    api.get(`/agents/user/${authStore.user?.id}`),
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
  }
}

async function deleteAgent(id) {
  if (!confirm('确定删除该智能体？')) return
  const res = await api.del(`/agents/${id}`)
  if (res.code === 200) loadData()
}

async function createAgent() {
  if (!newAgent.value.name || !newAgent.value.modelId) {
    alert('请填写名称和选择模型')
    return
  }
  const res = await api.post('/agents', {
    name: newAgent.value.name,
    description: newAgent.value.description,
    modelId: parseInt(newAgent.value.modelId),
    userId: authStore.user?.id
  })
  if (res.code === 200) {
    showCreate.value = false
    newAgent.value = { name: '', description: '', modelId: '' }
    loadData()
  }
}
</script>

<template>
  <div class="space-y-6 animate-fade-in">
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-2xl font-bold text-white">我的智能体</h1>
        <p class="text-slate-400 mt-1">管理您的 AI 智能体配置与调用</p>
      </div>
      <button @click="showCreate = true" class="btn-primary flex items-center gap-2">
        <Plus class="w-4 h-4" /> 新建智能体
      </button>
    </div>

    <div v-if="showCreate" class="glass-card p-6 glow-border">
      <h3 class="text-lg font-semibold text-white mb-4">新建智能体</h3>
      <div class="space-y-4">
        <div>
          <label class="block text-sm text-slate-300 mb-2">名称</label>
          <input v-model="newAgent.name" class="input-field" placeholder="智能体名称" />
        </div>
        <div>
          <label class="block text-sm text-slate-300 mb-2">描述</label>
          <input v-model="newAgent.description" class="input-field" placeholder="描述" />
        </div>
        <div>
          <label class="block text-sm text-slate-300 mb-2">绑定模型</label>
          <select v-model="newAgent.modelId" class="input-field">
            <option value="">请选择模型</option>
            <option v-for="m in models" :key="m.id" :value="m.id">{{ m.name }} ({{ m.provider }})</option>
          </select>
        </div>
        <div class="flex gap-3">
          <button @click="createAgent" class="btn-primary">创建</button>
          <button @click="showCreate = false" class="btn-secondary">取消</button>
        </div>
      </div>
    </div>

    <div v-if="loading" class="text-slate-500">加载中...</div>
    <div v-else-if="agents.length === 0" class="text-slate-500">暂无智能体</div>
    <div class="grid grid-cols-1 gap-4">
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
                <span class="text-xs text-slate-500">{{ (agent.totalCalls || 0).toLocaleString() }} 次调用</span>
                <span class="text-xs text-slate-500">{{ (agent.totalTokens || 0).toLocaleString() }} tokens</span>
              </div>
            </div>
          </div>
          <div class="flex items-center gap-2">
            <button @click="toggleStatus(agent)" :class="['p-2 rounded-lg transition-colors', agent.status === 'ACTIVE' ? 'bg-emerald-500/10 text-emerald-400' : 'bg-slate-500/10 text-slate-400']">
              <Power class="w-4 h-4" />
            </button>
            <button class="p-2 rounded-lg bg-white/5 text-slate-300 hover:bg-white/10">
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
  </div>
</template>
