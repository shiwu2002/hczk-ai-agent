<script setup>
import { ref, onMounted } from 'vue'
import { useApiStore } from '@/stores/api'
import { KeyRound, Plus, Search, Trash2, Copy, Check, X } from 'lucide-vue-next'

const api = useApiStore()
const loading = ref(false)
const showAddModal = ref(false)
const searchQuery = ref('')

const users = ref([])
const apiKeys = ref([])

const newApiKey = ref({
  userId: '',
  name: ''
})

const copiedKey = ref(null)

onMounted(loadData)

async function loadData() {
  loading.value = true
  const [userRes, apiKeyRes] = await Promise.all([
    api.get('/users'),
    api.get('/api-keys')
  ])
  if (userRes.code === 200) users.value = userRes.data || []
  if (apiKeyRes.code === 200) apiKeys.value = apiKeyRes.data || []
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
  
  const res = await api.post(`/api-keys?userId=${newApiKey.value.userId}&name=${encodeURIComponent(newApiKey.value.name)}`)
  if (res.code === 200) {
    showAddModal.value = false
    resetForm()
    loadData()
  } else {
    alert(res.message || '创建失败')
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
  newApiKey.value = { userId: '', name: '' }
}

function getUserName(id) {
  return users.value.find(u => u.id === id)?.username || id
}
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
            <th class="px-6 py-4 text-left text-sm font-medium text-slate-400">调用次数</th>
            <th class="px-6 py-4 text-left text-sm font-medium text-slate-400">状态</th>
            <th class="px-6 py-4 text-left text-sm font-medium text-slate-400">创建时间</th>
            <th class="px-6 py-4 text-left text-sm font-medium text-slate-400">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="key in apiKeys" :key="key.id" class="border-b border-white/5 hover:bg-white/5">
            <td class="px-6 py-4">
              <div class="flex items-center gap-2">
                <KeyRound class="w-4 h-4 text-emerald-400" />
                <span class="text-white font-medium">{{ key.name }}</span>
              </div>
            </td>
            <td class="px-6 py-4 text-slate-300">{{ getUserName(key.userId) }}</td>
            <td class="px-6 py-4 text-slate-300">{{ key.totalCalls || 0 }}</td>
            <td class="px-6 py-4">
              <span :class="['px-2 py-0.5 text-xs rounded-full', key.status === 'active' ? 'bg-emerald-500/10 text-emerald-400' : 'bg-slate-500/10 text-slate-400']">
                {{ key.status === 'active' ? '启用' : '禁用' }}
              </span>
            </td>
            <td class="px-6 py-4 text-slate-500 text-sm">{{ key.createdAt }}</td>
            <td class="px-6 py-4">
              <div class="flex items-center gap-2">
                <button @click="copyKey(key.apiKey)" class="p-2 rounded-lg bg-white/5 text-slate-300 hover:bg-white/10">
                  <component :is="copiedKey === key.apiKey ? Check : Copy" class="w-4 h-4" />
                </button>
                <button @click="deleteApiKey(key.id)" class="p-2 rounded-lg bg-white/5 text-red-400 hover:bg-red-500/10">
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
          <div class="p-3 rounded-lg bg-amber-500/10 border border-amber-500/20">
            <p class="text-sm text-amber-400">提示：API Key 生成后，用户可以通过该 Key 访问自己绑定的智能体服务。</p>
          </div>
        </div>
        <div class="flex gap-3 mt-6">
          <button @click="showAddModal = false" class="btn-secondary flex-1">取消</button>
          <button @click="createApiKey" class="btn-primary flex-1">创建</button>
        </div>
      </div>
    </div>
  </div>
</template>