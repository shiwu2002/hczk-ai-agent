<!-- 管理后台 - API Keys 管理页面：为用户分配 API Key，通过智能体接入大模型 -->
<script setup>
import { ref, onMounted } from 'vue'
import { useApiStore } from '@/stores/api'
import { KeyRound, Plus, Copy, Trash2, Eye, EyeOff } from 'lucide-vue-next'

const api = useApiStore()

// 页面状态
const loading = ref(false)          // 列表加载中
const showAddModal = ref(false)     // 分配 API Key 弹窗
const showKey = ref({})             // 控制每个 Key 的显示/隐藏状态

const apiKeys = ref([])             // API Key 列表
const users = ref([])               // 用户列表（用于分配和显示归属用户）

// 新建 API Key 表单
const newKey = ref({ name: '', userId: '' })

onMounted(loadData)

/** 加载 API Key 列表和用户列表 */
async function loadData() {
  loading.value = true
  const [keysRes, usersRes] = await Promise.all([
    api.get('/api-keys'),
    api.get('/users')
  ])
  if (keysRes.code === 200) {
    apiKeys.value = keysRes.data || []
  }
  if (usersRes.code === 200) {
    users.value = usersRes.data || []
  }
  loading.value = false
}

/** 根据用户 ID 查找用户名 */
function getUserName(userId) {
  const user = users.value.find(u => u.id === userId)
  return user ? (user.name || user.username) : `用户${userId}`
}

/** 创建 API Key，调用后端生成密钥并分配给指定用户 */
async function createKey() {
  if (!newKey.value.name || !newKey.value.userId) {
    alert('请填写名称并选择用户')
    return
  }
  const res = await api.post(`/api-keys?userId=${newKey.value.userId}&name=${encodeURIComponent(newKey.value.name)}`)
  if (res.code === 200) {
    showAddModal.value = false
    newKey.value = { name: '', userId: '' }
    loadData()
  } else {
    alert(res.message || '创建失败')
  }
}

/** 删除 API Key（二次确认） */
async function deleteKey(id) {
  if (!confirm('确定删除该 API Key？')) return
  const res = await api.del(`/api-keys/${id}`)
  if (res.code === 200) {
    loadData()
  } else {
    alert(res.message || '删除失败')
  }
}

/** 复制 API Key 到剪贴板 */
function copyKey(key) {
  navigator.clipboard.writeText(key)
}

/** 切换 API Key 的显示/隐藏 */
function toggleShow(id) {
  showKey.value[id] = !showKey.value[id]
}
</script>

<template>
  <div class="space-y-6 animate-fade-in">
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-2xl font-bold text-white">API Keys 管理</h1>
        <p class="text-slate-400 mt-1">为用户分配 API Key，通过智能体接入大模型</p>
      </div>
      <button @click="showAddModal = true" class="btn-primary flex items-center gap-2">
        <Plus class="w-4 h-4" /> 分配 API Key
      </button>
    </div>

    <div class="glass-card p-6">
      <div v-if="loading" class="text-slate-500">加载中...</div>
      <div v-else-if="apiKeys.length === 0" class="text-slate-500">暂无 API Key</div>
      <div v-else class="overflow-x-auto">
        <table class="w-full">
          <thead>
            <tr class="border-b border-white/5">
              <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">名称</th>
              <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">所属用户</th>
              <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">API Key</th>
              <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">调用次数</th>
              <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">最后使用</th>
              <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">状态</th>
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
              <td class="py-4 text-sm text-slate-300">{{ getUserName(apiKey.userId) }}</td>
              <td class="py-4">
                <div class="flex items-center gap-2">
                  <code class="text-sm text-emerald-400 font-mono bg-emerald-500/5 px-2 py-1 rounded">
                    {{ showKey[apiKey.id] ? apiKey.apiKey : apiKey.apiKey?.slice(0, 12) + '...' + apiKey.apiKey?.slice(-4) }}
                  </code>
                  <button @click="toggleShow(apiKey.id)" class="p-1 rounded hover:bg-white/5 text-slate-400">
                    <component :is="showKey[apiKey.id] ? EyeOff : Eye" class="w-4 h-4" />
                  </button>
                  <button @click="copyKey(apiKey.apiKey)" class="p-1 rounded hover:bg-white/5 text-slate-400">
                    <Copy class="w-4 h-4" />
                  </button>
                </div>
              </td>
              <td class="py-4 text-sm text-slate-300">{{ (apiKey.totalCalls || 0).toLocaleString() }}</td>
              <td class="py-4 text-sm text-slate-400">{{ apiKey.lastUsedAt ? new Date(apiKey.lastUsedAt).toLocaleString() : '从未' }}</td>
              <td class="py-4">
                <span :class="['px-2 py-1 text-xs rounded-full border', apiKey.status === 'active' ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20' : 'bg-slate-500/10 text-slate-400 border-slate-500/20']">
                  {{ apiKey.status === 'active' ? '可用' : '禁用' }}
                </span>
              </td>
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

    <!-- 调用示例 -->
    <div class="glass-card p-6">
      <h3 class="text-lg font-semibold text-white mb-4">调用方式</h3>
      <div class="space-y-4">
        <div>
          <p class="text-sm text-slate-400 mb-2">cURL（通过智能体调用）</p>
          <div class="bg-[#0a0f1c] rounded-lg p-4 font-mono text-sm text-slate-300 overflow-x-auto">
            <p><span class="text-emerald-400">curl</span> http://localhost:8080/api/v1/chat/completions \</p>
            <p>  -H <span class="text-cyan-400">"Authorization: Bearer YOUR_API_KEY"</span> \</p>
            <p>  -H <span class="text-cyan-400">"Content-Type: application/json"</span> \</p>
            <p>  -d <span class="text-cyan-400">'{"agentId": 1, "messages": [{"role": "user", "content": "你好"}], "stream": true}'</span></p>
          </div>
        </div>
        <div>
          <p class="text-sm text-slate-400 mb-2">Python（OpenAI SDK 兼容）</p>
          <div class="bg-[#0a0f1c] rounded-lg p-4 font-mono text-sm text-slate-300 overflow-x-auto">
            <p><span class="text-purple-400">from</span> openai <span class="text-purple-400">import</span> OpenAI</p>
            <p>&nbsp;</p>
            <p>client = OpenAI(</p>
            <p>  api_key=<span class="text-cyan-400">"YOUR_API_KEY"</span>,</p>
            <p>  base_url=<span class="text-cyan-400">"http://localhost:8080/api/v1"</span></p>
            <p>)</p>
            <p>&nbsp;</p>
            <p>response = client.chat.completions.create(</p>
            <p>  model=<span class="text-cyan-400">"agent-1"</span>,  <span class="text-slate-500"># 格式: agent-{智能体ID}</span></p>
            <p>  messages=[{<span class="text-cyan-400">"role"</span>: <span class="text-cyan-400">"user"</span>, <span class="text-cyan-400">"content"</span>: <span class="text-cyan-400">"你好"</span>}]</p>
            <p>)</p>
          </div>
        </div>
      </div>
    </div>

    <!-- 分配 API Key 弹窗 -->
    <div v-if="showAddModal" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm">
      <div class="glass-card w-full max-w-md p-6 animate-slide-up">
        <h2 class="text-xl font-bold text-white mb-6">分配 API Key</h2>
        <div class="space-y-4">
          <div>
            <label class="block text-sm text-slate-300 mb-2">选择用户</label>
            <select v-model="newKey.userId" class="input-field">
              <option value="" disabled>请选择用户</option>
              <option v-for="user in users" :key="user.id" :value="user.id">{{ user.name || user.username }}</option>
            </select>
          </div>
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
