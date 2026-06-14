<!-- 管理后台 - API Keys 管理页面：为用户分配 API Key，接入大模型 -->
<script setup>
import { ref, onMounted } from 'vue'
import { useApiStore, API_BASE } from '@/stores/api'
import { KeyRound, Plus, Copy, Trash2, Eye, EyeOff, MessageSquare, Check } from 'lucide-vue-next'

const api = useApiStore()

// 页面状态
const loading = ref(false)
const showAddModal = ref(false)
const showTestModal = ref(false)
const showKey = ref({})
const copiedId = ref(null)          // 复制成功反馈

const apiKeys = ref([])
const users = ref([])
const models = ref([])

// 新建 API Key 表单
const newKey = ref({ name: '', userId: '' })

// 测试对话表单
const testForm = ref({
  apiKey: '',
  callType: 'auto',   // auto=自动选择，model=指定模型
  modelId: '',
  message: '',
  streaming: true,
  response: '',
  loading: false
})

onMounted(loadData)

/** 加载 API Key 列表、用户列表和模型列表 */
async function loadData() {
  loading.value = true
  const [keysRes, usersRes, modelsRes] = await Promise.all([
    api.get('/api-keys'),
    api.get('/users'),
    api.get('/models')
  ])
  if (keysRes.code === 200) {
    apiKeys.value = keysRes.data || []
  }
  if (usersRes.code === 200) {
    users.value = usersRes.data || []
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

/** 创建 API Key */
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
  if (!confirm('确定删除该 API Key？删除后无法恢复。')) return
  const res = await api.del(`/api-keys/${id}`)
  if (res.code === 200) {
    loadData()
  } else {
    alert(res.message || '删除失败')
  }
}

/** 复制 API Key 到剪贴板（带反馈） */
async function copyKey(key, id) {
  try {
    await navigator.clipboard.writeText(key)
    copiedId.value = id
    setTimeout(() => { copiedId.value = null }, 1500)
  } catch {
    alert('复制失败，请手动复制')
  }
}

/** 切换 API Key 的显示/隐藏 */
function toggleShow(id) {
  showKey.value[id] = !showKey.value[id]
}

/** 打开测试对话弹窗 */
function openTest(apiKeyObj) {
  testForm.value = {
    apiKey: apiKeyObj.apiKey,
    callType: 'auto',
    modelId: models.value.length > 0 ? models.value[0].id : '',
    message: '',
    streaming: true,
    response: '',
    loading: false
  }
  showTestModal.value = true
}

/** 执行测试对话 */
async function runTest() {
  if (!testForm.value.message.trim()) {
    alert('请输入消息')
    return
  }
  if (testForm.value.callType === 'model' && !testForm.value.modelId) {
    alert('请选择模型')
    return
  }

  testForm.value.response = ''
  testForm.value.loading = true

  try {
    const body = {
      messages: [{ role: 'user', content: testForm.value.message }],
      stream: testForm.value.streaming
    }
    if (testForm.value.callType === 'model') {
      body.model = `model-${testForm.value.modelId}`
    }

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

    const reader = res.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop()
      for (const line of lines) {
        if (line.startsWith('data:')) {
          const data = line.slice(5).trim()
          if (data === '[DONE]') continue
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
    <!-- 页面标题 -->
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-2xl font-bold text-white">API Keys 管理</h1>
        <p class="text-slate-400 mt-1">为用户分配 API Key，接入大模型服务</p>
      </div>
      <button @click="showAddModal = true" class="btn-primary flex items-center gap-2">
        <Plus class="w-4 h-4" /> 分配 API Key
      </button>
    </div>

    <!-- API Key 列表 -->
    <div class="glass-card p-6">
      <div v-if="loading" class="flex items-center justify-center py-12">
        <div class="w-6 h-6 border-2 border-emerald-500/30 border-t-emerald-500 rounded-full animate-spin"></div>
        <span class="ml-3 text-slate-400">加载中...</span>
      </div>
      <div v-else-if="apiKeys.length === 0" class="flex flex-col items-center justify-center py-12 text-slate-500">
        <KeyRound class="w-12 h-12 mb-3 opacity-30" />
        <p>暂无 API Key</p>
        <p class="text-sm mt-1">点击右上角按钮分配新的 API Key</p>
      </div>
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
                  <button @click="toggleShow(apiKey.id)" class="p-1 rounded hover:bg-white/5 text-slate-400" :title="showKey[apiKey.id] ? '隐藏' : '显示'">
                    <component :is="showKey[apiKey.id] ? EyeOff : Eye" class="w-4 h-4" />
                  </button>
                  <button @click="copyKey(apiKey.apiKey, apiKey.id)" class="p-1 rounded hover:bg-white/5" :class="copiedId === apiKey.id ? 'text-emerald-400' : 'text-slate-400'" :title="copiedId === apiKey.id ? '已复制' : '复制'">
                    <component :is="copiedId === apiKey.id ? Check : Copy" class="w-4 h-4" />
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
                  <button @click="deleteKey(apiKey.id)" class="p-2 rounded-lg bg-white/5 text-red-400 hover:bg-red-500/10" title="删除">
                    <Trash2 class="w-4 h-4" />
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- 调用说明 -->
    <div class="glass-card p-6">
      <h3 class="text-lg font-semibold text-white mb-4">调用说明</h3>
      <div class="space-y-4">
        <div class="text-sm text-slate-400 space-y-2">
          <p>接口地址：<code class="text-emerald-400 font-mono bg-emerald-500/5 px-1.5 py-0.5 rounded">/v1/chat/completions</code></p>
          <p>认证方式：<code class="text-emerald-400 font-mono bg-emerald-500/5 px-1.5 py-0.5 rounded">Authorization: Bearer YOUR_API_KEY</code></p>
          <p>兼容 OpenAI API 格式，支持流式（SSE）和非流式响应。</p>
        </div>
        <div class="text-sm text-slate-400 space-y-1">
          <p class="text-slate-300 font-medium">model 参数格式：</p>
          <ul class="list-disc list-inside space-y-0.5 ml-2">
            <li>不传 — 自动选择默认模型</li>
            <li><code class="text-cyan-400 font-mono">model-{ID}</code> — 指定模型 ID，如 <code class="text-cyan-400 font-mono">model-1</code></li>
            <li>模型名称 — 如 <code class="text-cyan-400 font-mono">qwen-plus</code></li>
          </ul>
        </div>
        <div>
          <p class="text-sm text-slate-400 mb-2">cURL 调用模板</p>
          <div class="bg-[#0a0f1c] rounded-lg p-4 font-mono text-sm text-slate-300 overflow-x-auto">
            <p><span class="text-emerald-400">curl</span> http://localhost:8080/api/v1/chat/completions \</p>
            <p>  -H <span class="text-cyan-400">"Authorization: Bearer YOUR_API_KEY"</span> \</p>
            <p>  -H <span class="text-cyan-400">"Content-Type: application/json"</span> \</p>
            <p>  -d <span class="text-cyan-400">'{"model": "model-1", "messages": [{"role": "user", "content": "你好"}], "stream": true}'</span></p>
          </div>
        </div>
      </div>
    </div>

    <!-- 分配 API Key 弹窗 -->
    <div v-if="showAddModal" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm" @click.self="showAddModal = false">
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
            <input v-model="newKey.name" class="input-field" placeholder="如：张三-生产环境" @keyup.enter="createKey" />
          </div>
        </div>
        <div class="flex gap-3 mt-6">
          <button @click="showAddModal = false" class="btn-secondary flex-1">取消</button>
          <button @click="createKey" class="btn-primary flex-1">生成并分配</button>
        </div>
      </div>
    </div>

    <!-- 测试对话弹窗 -->
    <div v-if="showTestModal" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm" @click.self="showTestModal = false">
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
                    @click="testForm.callType = 'auto'"
                    :class="['flex-1 py-2 px-3 rounded-lg text-sm font-medium transition-colors', testForm.callType === 'auto' ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30' : 'bg-white/5 text-slate-400 border border-white/10']"
                  >自动选择</button>
                  <button
                    @click="testForm.callType = 'model'"
                    :class="['flex-1 py-2 px-3 rounded-lg text-sm font-medium transition-colors', testForm.callType === 'model' ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30' : 'bg-white/5 text-slate-400 border border-white/10']"
                  >指定模型</button>
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
              <div>
                <label class="block text-sm text-slate-300 mb-2">输入消息</label>
                <textarea v-model="testForm.message" rows="3" class="input-field w-full resize-none" placeholder="输入测试问题..." @keydown.enter.ctrl="runTest"></textarea>
              </div>
              <div class="flex items-center gap-4">
                <label class="flex items-center gap-2 cursor-pointer">
                  <input v-model="testForm.streaming" type="checkbox" class="w-4 h-4 rounded border-slate-600 bg-slate-800 text-emerald-500" />
                  <span class="text-sm text-slate-300">流式响应</span>
                </label>
                <button @click="runTest" :disabled="testForm.loading || !testForm.message.trim() || (testForm.callType === 'model' && !testForm.modelId)" class="btn-primary flex-1">
                  {{ testForm.loading ? '请求中...' : (testForm.callType === 'auto' ? '发送（自动选模型）' : '发送') }}
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
