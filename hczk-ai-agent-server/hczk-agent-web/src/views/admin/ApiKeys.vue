<script setup>
import { ref, onMounted } from 'vue'
import { useApiStore } from '@/stores/api'
import { KeyRound, Plus, Copy, Trash2, Eye, EyeOff, RefreshCw } from 'lucide-vue-next'

const api = useApiStore()
const loading = ref(false)
const showAddModal = ref(false)
const showKey = ref({})

const apiKeys = ref([])

onMounted(loadData)

async function loadData() {
  loading.value = true
  const res = await api.get('/api-keys')
  if (res.code === 200) {
    apiKeys.value = res.data || []
  }
  loading.value = false
}

function generateKey() {
  const chars = 'abcdef0123456789'
  let key = 'sk-hczk-'
  for (let i = 0; i < 24; i++) key += chars[Math.floor(Math.random() * chars.length)]
  return key
}

const newKey = ref({ name: '', userId: '' })

async function createKey() {
  const res = await api.post('/api-keys', {
    name: newKey.value.name,
    key: generateKey()
  })
  if (res.code === 200) {
    showAddModal.value = false
    newKey.value = { name: '', userId: '' }
    loadData()
  } else {
    alert(res.message || '创建失败')
  }
}

async function deleteKey(id) {
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
}

function toggleShow(id) {
  showKey.value[id] = !showKey.value[id]
}
</script>

<template>
  <div class="space-y-6 animate-fade-in">
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-2xl font-bold text-white">API Keys 管理</h1>
        <p class="text-slate-400 mt-1">为每个用户分配和管理 API Key，支持 OpenAI 格式调用</p>
      </div>
      <button @click="showAddModal = true" class="btn-primary flex items-center gap-2">
        <Plus class="w-4 h-4" /> 分配 API Key
      </button>
    </div>

    <div class="glass-card p-6">
      <div v-if="loading" class="text-slate-500">加载中...</div>
      <div v-else class="overflow-x-auto">
        <table class="w-full">
          <thead>
            <tr class="border-b border-white/5">
              <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">名称</th>
              <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">所属用户</th>
              <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">API Key</th>
              <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">调用次数</th>
              <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">最后使用</th>
              <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">操作</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-white/5">
            <tr v-for="apiKey in apiKeys" :key="apiKey.id" class="hover:bg-white/5 transition-colors">
              <td class="py-4">
                <div class="flex items-center gap-3">
                  <div class="w-8 h-8 rounded-lg bg-emerald-500/10 flex items-center justify-center">
                    <KeyRound class="w-4 h-4 text-emerald-400" />
                  </div>
                  <span class="text-sm text-white">{{ apiKey.name }}</span>
                </div>
              </td>
              <td class="py-4 text-sm text-slate-300">{{ apiKey.user?.name || apiKey.user?.username || '-' }}</td>
              <td class="py-4">
                <div class="flex items-center gap-2">
                  <code class="text-sm text-emerald-400 font-mono bg-emerald-500/5 px-2 py-1 rounded">
                    {{ showKey[apiKey.id] ? apiKey.key : apiKey.key?.slice(0, 12) + '...' + apiKey.key?.slice(-4) }}
                  </code>
                  <button @click="toggleShow(apiKey.id)" class="p-1 rounded hover:bg-white/5 text-slate-400">
                    <component :is="showKey[apiKey.id] ? EyeOff : Eye" class="w-4 h-4" />
                  </button>
                  <button @click="copyKey(apiKey.key)" class="p-1 rounded hover:bg-white/5 text-slate-400">
                    <Copy class="w-4 h-4" />
                  </button>
                </div>
              </td>
              <td class="py-4 text-sm text-slate-300">{{ (apiKey.calls || 0).toLocaleString() }}</td>
              <td class="py-4 text-sm text-slate-400">{{ apiKey.lastUsed || '从未' }}</td>
              <td class="py-4">
                <button @click="deleteKey(apiKey.id)" class="p-2 rounded-lg bg-white/5 text-red-400 hover:bg-red-500/10">
                  <Trash2 class="w-4 h-4" />
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- Info Card -->
    <div class="glass-card p-6">
      <h3 class="text-lg font-semibold text-white mb-4">调用方式</h3>
      <div class="space-y-4">
        <div>
          <p class="text-sm text-slate-400 mb-2">OpenAI 兼容格式</p>
          <div class="bg-[#0a0f1c] rounded-lg p-4 font-mono text-sm text-slate-300 overflow-x-auto">
            <p><span class="text-emerald-400">curl</span> https://api.hczk-ai.com/v1/chat/completions \</p>
            <p>  -H <span class="text-cyan-400">"Authorization: Bearer sk-hczk-xxx"</span> \</p>
            <p>  -H <span class="text-cyan-400">"Content-Type: application/json"</span> \</p>
            <p>  -d <span class="text-cyan-400">'{"model": "deepseek-chat", "messages": [{"role": "user", "content": "Hello"}]}'</span></p>
          </div>
        </div>
      </div>
    </div>

    <!-- Add Modal -->
    <div v-if="showAddModal" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm">
      <div class="glass-card w-full max-w-md p-6 animate-slide-up">
        <h2 class="text-xl font-bold text-white mb-6">分配 API Key</h2>
        <div class="space-y-4">
          <div>
            <label class="block text-sm text-slate-300 mb-2">Key 名称</label>
            <input v-model="newKey.name" class="input-field" placeholder="如：张三-生产环境" />
          </div>
        </div>
        <div class="flex gap-3 mt-6">
          <button @click="showAddModal = false" class="btn-secondary flex-1">取消</button>
          <button @click="createKey" class="btn-primary flex-1">生成并分配</button>
        </div>
      </div>
    </div>
  </div>
</template>
