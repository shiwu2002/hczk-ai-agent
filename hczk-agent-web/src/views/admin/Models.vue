<!-- 管理后台 - 模型管理页面：接入第三方大模型，配置 API 与计费参数，支持在线测试对话 -->
<script setup>
import { ref, onMounted } from 'vue'
import { useApiStore, API_BASE } from '@/stores/api'
import { useAuthStore } from '@/stores/auth'
import {
  Plus, Power, Settings2, Trash2, Cpu, Gauge, DollarSign, MessageSquare
} from 'lucide-vue-next'

const api = useApiStore()
const authStore = useAuthStore()

// 页面状态
const loading = ref(false)          // 列表加载中
const showAddModal = ref(false)     // 新增/编辑弹窗
const showTestModal = ref(false)    // 测试对话弹窗
const editingModel = ref(null)      // 当前编辑的模型（null 表示新增）
const testingModel = ref(null)      // 当前测试的模型

const models = ref([])

// 新增/编辑表单数据
const newModel = ref({
  name: '',
  provider: '',
  providerType: 'OPENAI_COMPATIBLE',  // 接口类型：OPENAI_COMPATIBLE / ANTHROPIC / MODELSCOPE
  modelId: '',
  apiBase: '',
  apiKey: '',
  inputPrice: 0,
  outputPrice: 0,
  maxTokens: 4096,
  thinking: false
})

// 测试对话表单数据
const testForm = ref({
  message: '',       // 测试输入内容
  streaming: true,   // 是否流式响应
  response: '',      // 模型回复内容
  loading: false     // 请求进行中
})

onMounted(loadData)

/** 加载模型列表 */
async function loadData() {
  loading.value = true
  const res = await api.get('/models')
  if (res.code === 200) {
    models.value = (res.data || []).map(m => ({
      ...m,
      pricing: { input: m.inputPrice || 0, output: m.outputPrice || 0 }
    }))
  }
  loading.value = false
}

/**
 * 切换模型启停状态
 * 调用后端 toggle 接口，成功后更新本地状态
 */
async function toggleStatus(model) {
  const res = await api.post(`/models/${model.id}/toggle`)
  if (res.code === 200) {
    model.status = res.data.status
  } else {
    alert(res.message || '操作失败')
  }
}

/**
 * 删除模型
 * 二次确认后调用后端删除接口，成功后刷新列表
 */
async function deleteModel(model) {
  if (!confirm('确定删除该模型？')) return
  const res = await api.del(`/models/${model.id}`)
  if (res.code === 200) {
    loadData()
  } else {
    alert(res.message || '删除失败')
  }
}

/**
     * 保存模型（新增或编辑）
     * 根据 editingModel 是否存在判断是新增还是编辑
     */
    async function saveModel() {
      if (!newModel.value.name.trim()) {
        alert('请输入模型名称')
        return
      }
      if (!newModel.value.provider.trim()) {
        alert('请输入提供商')
        return
      }
      if (!newModel.value.modelId.trim()) {
        alert('请输入模型ID')
        return
      }
      if (!newModel.value.inputPrice || newModel.value.inputPrice <= 0) {
        alert('请输入有效的输入价格（必须大于0）')
        return
      }
      if (!newModel.value.outputPrice || newModel.value.outputPrice <= 0) {
        alert('请输入有效的输出价格（必须大于0）')
        return
      }
      
      const body = editingModel.value
        ? { ...editingModel.value, ...newModel.value }
        : { ...newModel.value }

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

/**
 * 打开编辑弹窗
 * 将模型数据填充到表单中
 */
function editModel(model) {
  editingModel.value = { ...model }
  newModel.value = {
    name: model.name,
    provider: model.provider,
    providerType: model.providerType || 'OPENAI_COMPATIBLE',
    modelId: model.modelId,
    apiBase: model.apiBase || '',
    apiKey: model.apiKey || '',
    inputPrice: model.inputPrice || 0,
    outputPrice: model.outputPrice || 0,
    maxTokens: model.maxTokens || 4096,
    thinking: model.thinking || false
  }
  showAddModal.value = true
}

/** 重置表单为默认值 */
function resetForm() {
  newModel.value = {
    name: '', provider: '', providerType: 'OPENAI_COMPATIBLE', modelId: '', apiBase: '', apiKey: '',
    inputPrice: 0, outputPrice: 0, maxTokens: 4096, thinking: false
  }
}

/**
 * 打开测试对话弹窗
 * 初始化测试表单状态
 */
function openTest(model) {
  testingModel.value = model
  testForm.value = { message: '', streaming: true, response: '', loading: false }
  showTestModal.value = true
}

/**
 * 执行测试对话
 * 直接调用 /chat/completions 接口，支持 SSE 流式读取响应
 * 使用 ReadableStream 逐行解析 SSE 事件，实时拼接模型回复
 */
async function runTest() {
  if (!testForm.value.message.trim()) return
  testForm.value.response = ''
  testForm.value.loading = true

  try {
    // 直接使用 fetch 发起 SSE 流式请求（不走 api store，需手动处理流）
    const res = await fetch(`${API_BASE}/chat/completions`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${authStore.token}`
      },
      body: JSON.stringify({
        modelId: testingModel.value.id,
        message: testForm.value.message,
        stream: testForm.value.streaming
      })
    })

    if (!res.ok) {
      const err = await res.json().catch(() => ({}))
      testForm.value.response = `请求失败: ${err.message || res.statusText}`
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
      buffer = lines.pop()  // 保留未完成的行
      for (const line of lines) {
        if (line.startsWith('data:')) {
          const data = line.slice(5).trim()
          if (data === '[DONE]') continue
          // 后端发送纯文本 SSE（data:内容），非 OpenAI JSON 格式
          // 尝试 JSON 解析（兼容 OpenAI 格式），失败则直接作为文本
          try {
            const json = JSON.parse(data)
            const delta = json.choices?.[0]?.delta?.content
            if (delta) {
              testForm.value.response += delta
            }
          } catch {
            // 纯文本格式，直接拼接
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
    <!-- 页面标题与操作按钮 -->
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

    <!-- 模型卡片网格 -->
    <div v-else class="grid grid-cols-1 lg:grid-cols-2 gap-6">
      <div v-for="model in models" :key="model.id" class="glass-card p-6 glow-border">
        <!-- 卡片头部：模型名称 + 操作按钮 -->
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
          <!-- 操作按钮：启停、测试、编辑、删除 -->
          <div class="flex items-center gap-2">
            <button
              @click="toggleStatus(model)"
              :class="['p-2 rounded-lg transition-colors', model.status === 'ACTIVE' ? 'bg-emerald-500/10 text-emerald-400' : 'bg-slate-500/10 text-slate-400']"
            >
              <Power class="w-4 h-4" />
            </button>
            <button @click="openTest(model)" class="p-2 rounded-lg bg-white/5 text-slate-300 hover:bg-white/10" title="测试对话">
              <MessageSquare class="w-4 h-4" />
            </button>
            <button @click="editModel(model)" class="p-2 rounded-lg bg-white/5 text-slate-300 hover:bg-white/10">
              <Settings2 class="w-4 h-4" />
            </button>
            <button @click="deleteModel(model)" class="p-2 rounded-lg bg-white/5 text-red-400 hover:bg-red-500/10">
              <Trash2 class="w-4 h-4" />
            </button>
          </div>
        </div>

        <!-- 模型详情信息 -->
        <div class="space-y-3">
          <div class="flex items-center justify-between py-2 border-b border-white/5">
            <span class="text-sm text-slate-400">接口类型</span>
            <span class="text-sm text-cyan-400">{{ model.providerType || 'OPENAI_COMPATIBLE' }}</span>
          </div>
          <div class="flex items-center justify-between py-2 border-b border-white/5">
            <span class="text-sm text-slate-400">模型 ID</span>
            <code class="text-sm text-emerald-400 font-mono">{{ model.modelId }}</code>
          </div>
          <div class="flex items-center justify-between py-2 border-b border-white/5">
            <span class="text-sm text-slate-400">API 地址</span>
            <span class="text-sm text-slate-300 font-mono truncate max-w-[200px]">{{ model.apiBase || '默认' }}</span>
          </div>
          <div class="flex items-center justify-between py-2 border-b border-white/5">
            <span class="text-sm text-slate-400">输入价格</span>
            <span class="text-sm text-white">{{ model.pricing?.input || 0 }} 元 / 1K tokens</span>
          </div>
          <div class="flex items-center justify-between py-2 border-b border-white/5">
            <span class="text-sm text-slate-400">输出价格</span>
            <span class="text-sm text-white">{{ model.pricing?.output || 0 }} 元 / 1K tokens</span>
          </div>
          <div class="flex items-center justify-between py-2">
            <span class="text-sm text-slate-400">最大上下文</span>
            <span class="text-sm text-white">{{ ((model.maxTokens || 0) / 1024).toFixed(0) }}K tokens</span>
          </div>
        </div>

        <!-- 卡片底部：QPS 与状态标签 -->
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

    <!-- 新增/编辑模型弹窗 -->
    <div v-if="showAddModal" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm">
      <div class="glass-card w-full max-w-lg p-6 animate-slide-up">
        <h2 class="text-xl font-bold text-white mb-6">{{ editingModel ? '编辑模型' : '接入新模型' }}</h2>
        <div class="space-y-4">
          <!-- 模型名称 + 提供商 -->
          <div class="grid grid-cols-2 gap-4">
            <div>
              <label class="block text-sm text-slate-300 mb-2">模型名称</label>
              <input v-model="newModel.name" class="input-field" placeholder="请输入模型名称" />
            </div>
            <div>
              <label class="block text-sm text-slate-300 mb-2">提供商</label>
              <input v-model="newModel.provider" class="input-field" placeholder="请输入提供商" />
            </div>
          </div>
          <!-- 接口类型 + 模型 ID -->
          <div class="grid grid-cols-2 gap-4">
            <div>
              <label class="block text-sm text-slate-300 mb-2">接口类型</label>
              <select v-model="newModel.providerType" class="input-field">
                <option value="OPENAI_COMPATIBLE">OpenAI 兼容</option>
                <option value="ANTHROPIC">Anthropic</option>
                <option value="MODELSCOPE">ModelScope</option>
              </select>
            </div>
            <div>
              <label class="block text-sm text-slate-300 mb-2">模型 ID</label>
              <input v-model="newModel.modelId" class="input-field" placeholder="请输入模型 ID" />
            </div>
          </div>
          <!-- API Base URL -->
          <div>
            <label class="block text-sm text-slate-300 mb-2">API Base URL（不含 /chat/completions）</label>
            <input v-model="newModel.apiBase" class="input-field" placeholder="https://api.example.com/v1" />
          </div>
          <!-- API Key -->
          <div>
            <label class="block text-sm text-slate-300 mb-2">API Key</label>
            <input v-model="newModel.apiKey" type="password" class="input-field" placeholder="sk-..." />
          </div>
          <!-- 计费与上下文参数 -->
          <div class="grid grid-cols-3 gap-4">
            <div>
              <label class="block text-sm text-slate-300 mb-2">输入价格 (元/1K)</label>
              <input v-model.number="newModel.inputPrice" type="number" step="0.001" class="input-field" />
            </div>
            <div>
              <label class="block text-sm text-slate-300 mb-2">输出价格 (元/1K)</label>
              <input v-model.number="newModel.outputPrice" type="number" step="0.001" class="input-field" />
            </div>
            <div>
              <label class="block text-sm text-slate-300 mb-2">最大 Tokens</label>
              <input v-model.number="newModel.maxTokens" type="number" class="input-field" />
            </div>
          </div>
          <!-- 深度思考开关 -->
          <div class="flex items-center gap-2">
            <input id="thinking" v-model="newModel.thinking" type="checkbox" class="w-4 h-4 rounded border-slate-600 bg-slate-800 text-emerald-500 focus:ring-emerald-500" />
            <label for="thinking" class="text-sm text-slate-300">启用深度思考 (enable_thinking)</label>
          </div>
        </div>
        <div class="flex gap-3 mt-6">
          <button @click="showAddModal = false" class="btn-secondary flex-1">取消</button>
          <button @click="saveModel" class="btn-primary flex-1">保存</button>
        </div>
      </div>
    </div>

    <!-- 测试对话弹窗 -->
    <div v-if="showTestModal" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm">
      <div class="glass-card w-full max-w-2xl p-6 animate-slide-up flex flex-col max-h-[80vh]">
        <h2 class="text-xl font-bold text-white mb-4">测试对话 — {{ testingModel?.name }}</h2>
        <div class="flex-1 overflow-y-auto space-y-4 mb-4">
          <!-- 输入区域 -->
          <div class="bg-slate-800/50 rounded-lg p-4 border border-white/5">
            <label class="block text-sm text-slate-300 mb-2">输入内容</label>
            <textarea v-model="testForm.message" rows="3" class="input-field w-full resize-none" placeholder="输入测试问题..."></textarea>
            <div class="flex items-center gap-2 mt-2">
              <input id="stream" v-model="testForm.streaming" type="checkbox" class="w-4 h-4 rounded border-slate-600 bg-slate-800 text-emerald-500" />
              <label for="stream" class="text-sm text-slate-300">流式响应</label>
            </div>
            <button @click="runTest" :disabled="testForm.loading || !testForm.message.trim()" class="btn-primary mt-3 w-full">
              {{ testForm.loading ? '请求中...' : '发送' }}
            </button>
          </div>
          <!-- 回复区域 -->
          <div v-if="testForm.response || testForm.loading" class="bg-slate-800/50 rounded-lg p-4 border border-white/5">
            <label class="block text-sm text-slate-300 mb-2">模型回复</label>
            <div class="text-sm text-slate-200 whitespace-pre-wrap leading-relaxed">{{ testForm.response }}</div>
          </div>
        </div>
        <div class="flex justify-end">
          <button @click="showTestModal = false" class="btn-secondary">关闭</button>
        </div>
      </div>
    </div>
  </div>
</template>
