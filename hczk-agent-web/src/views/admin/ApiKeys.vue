<!-- 管理后台 - API Keys 管理页面：为用户分配 API Key，通过智能体接入大模型 -->
<script setup>
import { ref, onMounted } from 'vue'
import { useApiStore, API_BASE } from '@/stores/api'
import { KeyRound, Plus, Copy, Trash2, Eye, EyeOff, MessageSquare } from 'lucide-vue-next'

const api = useApiStore()

// 页面状态
const loading = ref(false)          // 列表加载中
const showAddModal = ref(false)     // 分配 API Key 弹窗
const showTestModal = ref(false)    // 测试对话弹窗
const showKey = ref({})             // 控制每个 Key 的显示/隐藏状态

const apiKeys = ref([])             // API Key 列表
const users = ref([])               // 用户列表（用于分配和显示归属用户）
const agents = ref([])              // 智能体列表（用于测试对话选择）
const models = ref([])              // 模型列表（用于测试对话选择）

// 新建 API Key 表单
const newKey = ref({ name: '', userId: '' })

// 测试对话表单
const testForm = ref({
  apiKey: '',       // 当前测试的 API Key
  callType: 'model', // 调用方式：model=直接选模型，agent=通过智能体
  agentId: '',      // 选择的智能体 ID
  modelId: '',      // 选择的模型 ID
  message: '',      // 测试消息
  streaming: true,  // 是否流式
  response: '',     // 模型回复
  loading: false    // 请求中
})

onMounted(loadData)

/** 加载 API Key 列表、用户列表、智能体列表和模型列表 */
async function loadData() {
  loading.value = true
  const [keysRes, usersRes, agentsRes, modelsRes] = await Promise.all([
    api.get('/api-keys'),
    api.get('/users'),
    api.get('/agents'),
    api.get('/models')
  ])
  if (keysRes.code === 200) {
    apiKeys.value = keysRes.data || []
  }
  if (usersRes.code === 200) {
    users.value = usersRes.data || []
  }
  if (agentsRes.code === 200) {
    agents.value = (agentsRes.data || []).filter(a => a.status === 'ACTIVE')
  }
  if (modelsRes.code === 200) {
    models.value = (modelsRes.data || []).filter(m => m.status === 'ACTIVE')
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

/**
 * 打开测试对话弹窗
 * 自动填充选中的 API Key，选择智能体
 */
function openTest(apiKeyObj) {
  testForm.value = {
    apiKey: apiKeyObj.apiKey,
    callType: 'model',
    agentId: agents.value.length > 0 ? agents.value[0].id : '',
    modelId: models.value.length > 0 ? models.value[0].id : '',
    message: '',
    streaming: true,
    response: '',
    loading: false
  }
  showTestModal.value = true
}

/**
 * 执行测试对话
 * 使用 API Key 认证调用 /v1/chat/completions 接口
 * 通过 ReadableStream 逐行解析 SSE 事件，实时拼接模型回复
 */
async function runTest() {
  if (!testForm.value.message.trim()) {
    alert('请输入消息')
    return
  }
  if (testForm.value.callType === 'agent' && !testForm.value.agentId) {
    alert('请选择智能体')
    return
  }
  if (testForm.value.callType === 'model' && !testForm.value.modelId) {
    alert('请选择模型')
    return
  }

  testForm.value.response = ''
  testForm.value.loading = true

  try {
    // 构建请求体：根据调用方式传 agentId 或 modelId
    const body = {
      messages: [{ role: 'user', content: testForm.value.message }],
      stream: testForm.value.streaming
    }
    if (testForm.value.callType === 'agent') {
      body.agentId = Number(testForm.value.agentId)
    } else {
      body.modelId = Number(testForm.value.modelId)
    }

    // 使用 API Key 认证，调用 OpenAI 兼容接口
    const res = await fetch(`${API_BASE}/v1/chat/completions`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${testForm.value.apiKey}`
      },
      body: JSON.stringify(body)
    })

    if (!res.ok) {
      const err = await res.json().catch(() => ({}))
      testForm.value.response = `请求失败 (${res.status}): ${err.message || res.statusText}`
      testForm.value.loading = false
      return
    }

    // 使用 ReadableStream 逐块读取 SSE 数据
    const reader = res.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      // 按换行符分割，逐行解析 SSE 事件
      const lines = buffer.split('\n')
      buffer = lines.pop()
      for (const line of lines) {
        if (line.startsWith('data:')) {
          const data = line.slice(5).trim()
          if (data === '[DONE]') continue
          // 兼容纯文本和 OpenAI JSON 格式
          try {
            const json = JSON.parse(data)
            const delta = json.choices?.[0]?.delta?.content
            if (delta) {
              testForm.value.response += delta
            }
          } catch {
            if (data) {
              testForm.value.response += data
            }
          }
        }
      }
    }
  } catch (e) {
    testForm.value.response = '请求异常: ' + e.message
  } finally {
    testForm.value.loading = false
  }
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
                <div class="flex items-center gap-1">
                  <button @click="openTest(apiKey)" class="p-2 rounded-lg bg-white/5 text-cyan-400 hover:bg-cyan-500/10" title="测试对话">
                    <MessageSquare class="w-4 h-4" />
                  </button>
                  <button @click="deleteKey(apiKey.id)" class="p-2 rounded-lg bg-white/5 text-red-400 hover:bg-red-500/10">
                    <Trash2 class="w-4 h-4" />
                  </button>
                </div>
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

    <!-- 测试对话弹窗 -->
    <div v-if="showTestModal" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm">
      <div class="glass-card w-full max-w-2xl p-6 animate-slide-up flex flex-col max-h-[80vh]">
        <h2 class="text-xl font-bold text-white mb-4">测试 API Key 对话</h2>

        <!-- API Key 信息 -->
        <div class="bg-slate-800/50 rounded-lg p-3 border border-white/5 mb-4">
          <div class="flex items-center gap-2 text-sm">
            <KeyRound class="w-4 h-4 text-emerald-400" />
            <span class="text-slate-400">当前 Key：</span>
            <code class="text-emerald-400 font-mono">{{ testForm.apiKey.slice(0, 12) }}...{{ testForm.apiKey.slice(-4) }}</code>
          </div>
        </div>

        <div class="flex-1 overflow-y-auto space-y-4 mb-4">
          <!-- 输入区域 -->
          <div class="bg-slate-800/50 rounded-lg p-4 border border-white/5">
            <div class="space-y-3">
              <!-- 调用方式切换 -->
              <div>
                <label class="block text-sm text-slate-300 mb-2">调用方式</label>
                <div class="flex gap-2">
                  <button
                    @click="testForm.callType = 'model'"
                    :class="['flex-1 py-2 px-3 rounded-lg text-sm font-medium transition-colors', testForm.callType === 'model' ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30' : 'bg-white/5 text-slate-400 border border-white/10']"
                  >直接选模型</button>
                  <button
                    @click="testForm.callType = 'agent'"
                    :class="['flex-1 py-2 px-3 rounded-lg text-sm font-medium transition-colors', testForm.callType === 'agent' ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30' : 'bg-white/5 text-slate-400 border border-white/10']"
                  >通过智能体</button>
                </div>
              </div>
              <!-- 选择模型 -->
              <div v-if="testForm.callType === 'model'">
                <label class="block text-sm text-slate-300 mb-2">选择模型</label>
                <select v-model="testForm.modelId" class="input-field">
                  <option value="" disabled>请选择模型</option>
                  <option v-for="model in models" :key="model.id" :value="model.id">{{ model.name }} ({{ model.modelId }})</option>
                </select>
              </div>
              <!-- 选择智能体 -->
              <div v-if="testForm.callType === 'agent'">
                <label class="block text-sm text-slate-300 mb-2">选择智能体</label>
                <select v-model="testForm.agentId" class="input-field">
                  <option value="" disabled>请选择智能体</option>
                  <option v-for="agent in agents" :key="agent.id" :value="agent.id">{{ agent.name }}</option>
                </select>
              </div>
              <div>
                <label class="block text-sm text-slate-300 mb-2">输入消息</label>
                <textarea v-model="testForm.message" rows="3" class="input-field w-full resize-none" placeholder="输入测试问题..."></textarea>
              </div>
              <div class="flex items-center gap-4">
                <label class="flex items-center gap-2 cursor-pointer">
                  <input v-model="testForm.streaming" type="checkbox" class="w-4 h-4 rounded border-slate-600 bg-slate-800 text-emerald-500" />
                  <span class="text-sm text-slate-300">流式响应</span>
                </label>
                <button @click="runTest" :disabled="testForm.loading || !testForm.message.trim() || (testForm.callType === 'model' && !testForm.modelId) || (testForm.callType === 'agent' && !testForm.agentId)" class="btn-primary flex-1">
                  {{ testForm.loading ? '请求中...' : '发送' }}
                </button>
              </div>
            </div>
          </div>

          <!-- 回复区域 -->
          <div v-if="testForm.response || testForm.loading" class="bg-slate-800/50 rounded-lg p-4 border border-white/5">
            <label class="block text-sm text-slate-300 mb-2">模型回复</label>
            <div class="text-sm text-slate-200 whitespace-pre-wrap leading-relaxed">
              {{ testForm.response }}
              <span v-if="testForm.loading" class="inline-block w-2 h-4 bg-emerald-400 animate-pulse ml-1"></span>
            </div>
          </div>
        </div>

        <div class="flex justify-end">
          <button @click="showTestModal = false" class="btn-secondary">关闭</button>
        </div>
      </div>
    </div>
  </div>
</template>
