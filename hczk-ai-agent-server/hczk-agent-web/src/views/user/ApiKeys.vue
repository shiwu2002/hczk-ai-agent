<script setup>
import { ref, onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useApiStore } from '@/stores/api'
import { KeyRound, Plus, Copy, Eye, EyeOff, Trash2, RefreshCw } from 'lucide-vue-next'

const authStore = useAuthStore()
const api = useApiStore()

const showKey = ref({})
const apiKeys = ref([])
const loading = ref(false)
const showCreate = ref(false)
const newKeyName = ref('')

onMounted(loadData)

async function loadData() {
  loading.value = true
  const res = await api.get(`/api-keys/user/${authStore.user?.id}`)
  if (res.code === 200) apiKeys.value = res.data || []
  loading.value = false
}

function copyKey(key) {
  navigator.clipboard.writeText(key)
  alert('API Key 已复制到剪贴板')
}

function toggleShow(id) {
  showKey.value[id] = !showKey.value[id]
}

async function deleteKey(id) {
  if (!confirm('确定删除该 API Key？')) return
  const res = await api.del(`/api-keys/${id}`)
  if (res.code === 200) loadData()
}

async function createKey() {
  if (!newKeyName.value) {
    alert('请输入密钥名称')
    return
  }
  const res = await api.post(`/api-keys?userId=${authStore.user?.id}&name=${encodeURIComponent(newKeyName.value)}`)
  if (res.code === 200) {
    showCreate.value = false
    newKeyName.value = ''
    loadData()
  }
}
</script>

<template>
  <div class="space-y-6 animate-fade-in">
    <div>
      <h1 class="text-2xl font-bold text-white">API Keys</h1>
      <p class="text-slate-400 mt-1">管理您的 API Key，用于 OpenAI 兼容格式调用</p>
    </div>

    <button @click="showCreate = true" class="btn-primary flex items-center gap-2">
      <Plus class="w-4 h-4" /> 新建 API Key
    </button>

    <div v-if="showCreate" class="glass-card p-6 glow-border">
      <h3 class="text-lg font-semibold text-white mb-4">新建 API Key</h3>
      <div class="flex gap-3">
        <input v-model="newKeyName" class="input-field flex-1" placeholder="密钥名称，如：生产环境" />
        <button @click="createKey" class="btn-primary">创建</button>
        <button @click="showCreate = false" class="btn-secondary">取消</button>
      </div>
    </div>

    <div v-if="loading" class="text-slate-500">加载中...</div>
    <div v-else-if="apiKeys.length === 0" class="text-slate-500">暂无 API Key</div>
    <div class="space-y-4">
      <div v-for="apiKey in apiKeys" :key="apiKey.id" class="glass-card p-6 glow-border">
        <div class="flex items-start justify-between">
          <div class="flex-1">
            <div class="flex items-center gap-3 mb-3">
              <div class="w-9 h-9 rounded-lg bg-emerald-500/10 flex items-center justify-center">
                <KeyRound class="w-5 h-5 text-emerald-400" />
              </div>
              <div>
                <h3 class="text-white font-medium">{{ apiKey.name }}</h3>
                <p class="text-xs text-slate-400">创建于 {{ apiKey.createdAt ? new Date(apiKey.createdAt).toLocaleDateString() : '-' }}</p>
              </div>
            </div>
            <div class="flex items-center gap-3">
              <code class="text-sm text-emerald-400 font-mono bg-emerald-500/5 px-3 py-2 rounded-lg">
                {{ showKey[apiKey.id] ? apiKey.apiKey : apiKey.apiKey?.slice(0, 14) + '...' + apiKey.apiKey?.slice(-8) }}
              </code>
              <button @click="toggleShow(apiKey.id)" class="p-2 rounded-lg bg-white/5 text-slate-400 hover:bg-white/10">
                <component :is="showKey[apiKey.id] ? EyeOff : Eye" class="w-4 h-4" />
              </button>
              <button @click="copyKey(apiKey.apiKey)" class="p-2 rounded-lg bg-white/5 text-slate-400 hover:bg-white/10">
                <Copy class="w-4 h-4" />
              </button>
            </div>
          </div>
          <div class="flex items-center gap-2">
            <button @click="deleteKey(apiKey.id)" class="p-2 rounded-lg bg-white/5 text-red-400 hover:bg-red-500/10">
              <Trash2 class="w-4 h-4" />
            </button>
          </div>
        </div>
        <div class="mt-4 pt-4 border-t border-white/5 flex items-center gap-4 text-sm text-slate-400">
          <span>{{ (apiKey.totalCalls || 0).toLocaleString() }} 次调用</span>
          <span v-if="apiKey.lastUsedAt">最近使用 {{ new Date(apiKey.lastUsedAt).toLocaleString() }}</span>
        </div>
      </div>
    </div>

    <div class="glass-card p-6">
      <h3 class="text-lg font-semibold text-white mb-4">调用示例</h3>
      <div class="space-y-4">
        <div>
          <p class="text-sm text-slate-400 mb-2">cURL</p>
          <div class="bg-[#0a0f1c] rounded-lg p-4 font-mono text-sm text-slate-300 overflow-x-auto">
            <p><span class="text-emerald-400">curl</span> http://localhost:8080/api/v1/chat/completions \</p>
            <p>  -H <span class="text-cyan-400">"Authorization: Bearer YOUR_API_KEY"</span> \</p>
            <p>  -H <span class="text-cyan-400">"Content-Type: application/json"</span> \</p>
            <p>  -d <span class="text-cyan-400">'{"model": "your-model-id", "messages": [{"role": "user", "content": "Hello"}]}'</span></p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
