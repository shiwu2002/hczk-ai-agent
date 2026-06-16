<!-- 管理后台 - 模型测试页面：独立的对话测试工具，支持多轮对话、模型切换、流式响应 -->
<script setup>
import { ref, onMounted, nextTick, computed } from 'vue'
import { useApiStore, API_BASE } from '@/stores/api'
import { Send, Trash2, Cpu, Loader2, Copy, Check, KeyRound, Eye, EyeOff } from 'lucide-vue-next'

const api = useApiStore()

// ====== 页面状态 ======
const apiKeyInput = ref('')          // 手动填入的API Key
const showApiKey = ref(false)        // 是否显示API Key明文
const messages = ref([])             // 对话消息列表 [{role, content, timestamp}]
const inputText = ref('')            // 输入框内容
const isStreaming = ref(false)        // 是否正在流式响应中
const streamingContent = ref('')     // 当前流式响应内容
const streamingReasoning = ref('')   // 当前流式思考内容
const chatContainer = ref(null)      // 对话容器DOM引用
const currentModelName = ref('')     // 当前使用的模型名称（从响应中获取）

// ====== 计算属性 ======
const hasApiKey = computed(() => apiKeyInput.value.trim().length > 0)

// ====== 生命周期 ======
onMounted(() => {
  // 不再需要加载模型列表，API Key已绑定模型
})

// ====== 方法 ======

/** 滚动到底部 */
async function scrollToBottom() {
  await nextTick()
  if (chatContainer.value) {
    chatContainer.value.scrollTop = chatContainer.value.scrollHeight
  }
}

/** 发送消息 */
async function sendMessage() {
  const text = inputText.value.trim()
  if (!text || isStreaming.value) return

  if (!hasApiKey.value) {
    alert('请先填入API Key')
    return
  }

  // 添加用户消息到对话列表
  messages.value.push({
    role: 'user',
    content: text,
    timestamp: new Date().toLocaleTimeString()
  })
  inputText.value = ''
  await scrollToBottom()

  // 开始流式请求（使用API Key认证，不传model字段，由后端根据API Key绑定的模型自动选择）
  isStreaming.value = true
  streamingContent.value = ''
  streamingReasoning.value = ''

  try {
    const res = await fetch(`${API_BASE}/v1/chat/completions`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${apiKeyInput.value.trim()}`
      },
      body: JSON.stringify({
        messages: [{ role: 'user', content: text }],
        stream: true
      })
    })

    if (!res.ok) {
      const err = await res.json().catch(() => ({}))
      messages.value.push({
        role: 'error',
        content: `请求失败: ${err.message || res.statusText}`,
        timestamp: new Date().toLocaleTimeString()
      })
      isStreaming.value = false
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
      const lines = buffer.split('\n')
      buffer = lines.pop()

      for (const line of lines) {
        const trimmed = line.trim()
        if (trimmed.startsWith('data:')) {
          const data = trimmed.slice(5).trim()
          if (data === '[DONE]') continue

          try {
            const json = JSON.parse(data)
            // 从响应中提取模型名称
            if (json.model && !currentModelName.value) {
              currentModelName.value = json.model
            }
            // 处理思考内容（reasoning_content）
            const reasoningDelta = json.choices?.[0]?.delta?.reasoning_content
            if (reasoningDelta) {
              streamingReasoning.value += reasoningDelta
              await scrollToBottom()
            }
            // 处理回复内容（content）
            const delta = json.choices?.[0]?.delta?.content
            if (delta) {
              streamingContent.value += delta
              await scrollToBottom()
            }
          } catch {
            // 非JSON数据，直接追加
            if (data) {
              streamingContent.value += data
              await scrollToBottom()
            }
          }
        }
      }
    }

    // 流式结束，将完整回复添加到消息列表
    if (streamingContent.value || streamingReasoning.value) {
      messages.value.push({
        role: 'assistant',
        content: streamingContent.value,
        reasoning: streamingReasoning.value,
        timestamp: new Date().toLocaleTimeString(),
        model: currentModelName.value
      })
    }
  } catch (e) {
    messages.value.push({
      role: 'error',
      content: `请求异常: ${e.message}`,
      timestamp: new Date().toLocaleTimeString()
    })
  } finally {
    isStreaming.value = false
    streamingContent.value = ''
    streamingReasoning.value = ''
    currentModelName.value = ''
    await scrollToBottom()
  }
}

/** 清空对话 */
function clearChat() {
  messages.value = []
  streamingContent.value = ''
}

/** 复制消息内容 */
const copiedIndex = ref(-1)
async function copyMessage(content, index) {
  try {
    await navigator.clipboard.writeText(content)
    copiedIndex.value = index
    setTimeout(() => { copiedIndex.value = -1 }, 2000)
  } catch (e) {
    const textarea = document.createElement('textarea')
    textarea.value = content
    document.body.appendChild(textarea)
    textarea.select()
    document.execCommand('copy')
    document.body.removeChild(textarea)
    copiedIndex.value = index
    setTimeout(() => { copiedIndex.value = -1 }, 2000)
  }
}

/** 复制API Key */
const copiedKey = ref(false)
async function copyApiKey() {
  if (!apiKeyInput.value.trim()) return
  try {
    await navigator.clipboard.writeText(apiKeyInput.value.trim())
    copiedKey.value = true
    setTimeout(() => { copiedKey.value = false }, 2000)
  } catch (e) { /* ignore */ }
}

/** 键盘事件：Enter 发送，Shift+Enter 换行 */
function handleKeydown(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    sendMessage()
  }
}

/** 快捷提问模板 */
const quickPrompts = [
  '你好，请介绍一下你自己',
  '用Python写一个快速排序算法',
  '解释一下什么是大语言模型',
  '写一首关于春天的诗',
  '帮我分析一下React和Vue的优缺点'
]
</script>

<template>
  <div class="h-full flex flex-col animate-fade-in">
    <!-- 顶部工具栏 -->
    <div class="flex items-center justify-between px-6 py-4 border-b border-white/5">
      <div>
        <h1 class="text-2xl font-bold text-white">模型测试</h1>
        <p class="text-slate-400 mt-1 text-sm">填入API Key进行对话测试，自动使用API Key绑定的模型</p>
      </div>
      <div class="flex items-center gap-3">
        <!-- API Key 输入框 -->
        <div class="relative flex items-center">
          <KeyRound class="absolute left-2.5 w-4 h-4 text-amber-400 pointer-events-none z-10" />
          <input
            v-model="apiKeyInput"
            :type="showApiKey ? 'text' : 'password'"
            placeholder="填入 API Key"
            class="input-field pl-9 pr-20 min-w-[280px]"
          />
          <div class="absolute right-2 flex items-center gap-1">
            <button @click="showApiKey = !showApiKey" class="text-slate-500 hover:text-slate-300 p-0.5" title="显示/隐藏">
              <EyeOff v-if="showApiKey" class="w-3.5 h-3.5" />
              <Eye v-else class="w-3.5 h-3.5" />
            </button>
            <button @click="copyApiKey" class="text-slate-500 hover:text-slate-300 p-0.5" title="复制">
              <Check v-if="copiedKey" class="w-3.5 h-3.5 text-emerald-400" />
              <Copy v-else class="w-3.5 h-3.5" />
            </button>
          </div>
        </div>
        <!-- 清空对话 -->
        <button @click="clearChat" class="btn-secondary flex items-center gap-2" :disabled="messages.length === 0">
          <Trash2 class="w-4 h-4" /> 清空
        </button>
      </div>
    </div>

    <!-- 当前配置信息 -->
    <div class="px-6 py-2 bg-slate-800/30 border-b border-white/5 flex items-center gap-4 text-sm">
      <!-- API Key 信息 -->
      <div v-if="hasApiKey" class="flex items-center gap-2">
        <KeyRound class="w-4 h-4 text-amber-400" />
        <span class="text-slate-400 font-mono text-xs">
          {{ showApiKey ? apiKeyInput : apiKeyInput.substring(0, 12) + '****' }}
        </span>
        <span class="text-emerald-400 text-xs">已填入</span>
      </div>
      <!-- 未填入提示 -->
      <div v-if="!hasApiKey" class="flex items-center gap-2 text-amber-400">
        <KeyRound class="w-4 h-4" />
        <span class="text-sm">请先填入API Key</span>
      </div>
    </div>

    <!-- 对话区域 -->
    <div ref="chatContainer" class="flex-1 overflow-y-auto px-6 py-4 space-y-4">
      <!-- 空状态 -->
      <div v-if="messages.length === 0 && !isStreaming" class="flex flex-col items-center justify-center h-full text-center">
        <div class="w-16 h-16 rounded-2xl bg-gradient-to-br from-emerald-500/20 to-cyan-500/20 flex items-center justify-center mb-4">
          <Cpu class="w-8 h-8 text-emerald-400" />
        </div>
        <h3 class="text-lg font-semibold text-white mb-2">开始测试对话</h3>
        <p class="text-slate-400 text-sm mb-2">填入API Key，输入问题开始测试</p>
        <p class="text-amber-400/60 text-xs mb-6">将自动使用API Key绑定的模型，对话通过API Key计费</p>
        <!-- 快捷提问 -->
        <div class="flex flex-wrap gap-2 max-w-xl justify-center">
          <button
            v-for="prompt in quickPrompts"
            :key="prompt"
            @click="inputText = prompt"
            class="px-3 py-1.5 text-sm rounded-lg bg-white/5 text-slate-300 border border-white/10 hover:bg-white/10 hover:border-emerald-500/30 transition-colors"
          >
            {{ prompt }}
          </button>
        </div>
      </div>

      <!-- 消息列表 -->
      <template v-for="(msg, index) in messages" :key="index">
        <!-- 用户消息 -->
        <div v-if="msg.role === 'user'" class="flex justify-end">
          <div class="max-w-[70%] bg-emerald-500/10 border border-emerald-500/20 rounded-2xl rounded-tr-sm px-4 py-3">
            <div class="text-sm text-white whitespace-pre-wrap leading-relaxed">{{ msg.content }}</div>
            <div class="text-xs text-slate-500 mt-1 text-right">{{ msg.timestamp }}</div>
          </div>
        </div>

        <!-- 助手消息 -->
        <div v-else-if="msg.role === 'assistant'" class="flex justify-start">
          <div class="max-w-[70%] bg-slate-800/50 border border-white/5 rounded-2xl rounded-tl-sm px-4 py-3">
            <div v-if="msg.model" class="text-xs text-emerald-400 mb-1 flex items-center gap-1">
              <Cpu class="w-3 h-3" /> {{ msg.model }}
            </div>
            <!-- 思考过程（可折叠） -->
            <div v-if="msg.reasoning" class="mb-2">
              <details class="group">
                <summary class="text-xs text-amber-400/70 cursor-pointer hover:text-amber-400 transition-colors flex items-center gap-1">
                  <span class="transition-transform group-open:rotate-90">&#9654;</span>
                  思考过程 ({{ msg.reasoning.length }}字)
                </summary>
                <div class="mt-1 p-2 bg-slate-900/50 rounded-lg text-xs text-slate-400 whitespace-pre-wrap leading-relaxed max-h-40 overflow-y-auto">{{ msg.reasoning }}</div>
              </details>
            </div>
            <div class="text-sm text-slate-200 whitespace-pre-wrap leading-relaxed">{{ msg.content }}</div>
            <div class="flex items-center justify-between mt-2">
              <div class="text-xs text-slate-500">{{ msg.timestamp }}</div>
              <button @click="copyMessage(msg.content, index)" class="text-slate-500 hover:text-slate-300 transition-colors">
                <Check v-if="copiedIndex === index" class="w-3.5 h-3.5 text-emerald-400" />
                <Copy v-else class="w-3.5 h-3.5" />
              </button>
            </div>
          </div>
        </div>

        <!-- 错误消息 -->
        <div v-else-if="msg.role === 'error'" class="flex justify-center">
          <div class="bg-red-500/10 border border-red-500/20 rounded-lg px-4 py-2 text-sm text-red-400">
            {{ msg.content }}
          </div>
        </div>
      </template>

      <!-- 流式响应中的消息 -->
      <div v-if="isStreaming && (streamingContent || streamingReasoning)" class="flex justify-start">
        <div class="max-w-[70%] bg-slate-800/50 border border-white/5 rounded-2xl rounded-tl-sm px-4 py-3">
          <div v-if="currentModelName" class="text-xs text-emerald-400 mb-1 flex items-center gap-1">
            <Cpu class="w-3 h-3" /> {{ currentModelName }}
          </div>
          <!-- 思考过程 -->
          <div v-if="streamingReasoning" class="mb-2">
            <div class="text-xs text-amber-400/70 flex items-center gap-1 mb-1">
              <span class="animate-pulse">&#9679;</span> 思考中...
            </div>
            <div class="p-2 bg-slate-900/50 rounded-lg text-xs text-slate-400 whitespace-pre-wrap leading-relaxed max-h-40 overflow-y-auto">{{ streamingReasoning }}</div>
          </div>
          <!-- 回复内容 -->
          <div v-if="streamingContent" class="text-sm text-slate-200 whitespace-pre-wrap leading-relaxed">
            {{ streamingContent }}
            <span class="inline-block w-1.5 h-4 bg-emerald-400 animate-pulse ml-0.5 align-middle"></span>
          </div>
        </div>
      </div>

      <!-- 加载中指示器 -->
      <div v-if="isStreaming && !streamingContent && !streamingReasoning" class="flex justify-start">
        <div class="bg-slate-800/50 border border-white/5 rounded-2xl px-4 py-3 flex items-center gap-2">
          <Loader2 class="w-4 h-4 text-emerald-400 animate-spin" />
          <span class="text-sm text-slate-400">思考中...</span>
        </div>
      </div>
    </div>

    <!-- 输入区域 -->
    <div class="px-6 py-4 border-t border-white/5 bg-slate-900/50">
      <div class="flex items-end gap-3">
        <div class="flex-1 relative">
          <textarea
            v-model="inputText"
            @keydown="handleKeydown"
            rows="1"
            class="input-field w-full resize-none pr-4"
            placeholder="输入测试问题... (Enter 发送, Shift+Enter 换行)"
            :disabled="isStreaming"
            @input="$event.target.style.height = 'auto'; $event.target.style.height = Math.min($event.target.scrollHeight, 120) + 'px'"
          ></textarea>
        </div>
        <button
          @click="sendMessage"
          :disabled="isStreaming || !inputText.trim() || !hasApiKey"
          class="btn-primary flex items-center gap-2 h-10 px-4"
        >
          <Send class="w-4 h-4" />
          发送
        </button>
      </div>
      <div class="flex items-center justify-between mt-2 text-xs text-slate-500">
        <div class="flex items-center gap-3">
          <span class="flex items-center gap-1"><KeyRound class="w-3 h-3 text-amber-400" /> {{ hasApiKey ? '已填入Key' : '未填入Key' }}</span>
          <span v-if="currentModelName" class="flex items-center gap-1"><Cpu class="w-3 h-3 text-emerald-400" /> {{ currentModelName }}</span>
        </div>
        <span>消息数: {{ messages.length }}</span>
      </div>
    </div>
  </div>
</template>
