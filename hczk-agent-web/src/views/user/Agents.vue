<script setup>
import { ref, onMounted, nextTick, computed } from 'vue'
import { useApiStore, API_BASE } from '@/stores/api'
import { useAuthStore } from '@/stores/auth'
import { Bot, Send, Trash2, Loader2, Copy, Check, Plus, MessageSquare, Search, X, ChevronDown } from 'lucide-vue-next'

const api = useApiStore()
const authStore = useAuthStore()

// ====== 智能体列表 ======
const agents = ref([])
const loading = ref(true)

// ====== 会话管理 ======
// 每个会话: { id, agentId, agentName, title, messages: [], createdAt, updatedAt }
const chatSessions = ref([])
const activeSessionId = ref(null)

const activeSession = computed(() => {
  return chatSessions.value.find(s => s.id === activeSessionId.value) || null
})

const activeAgent = computed(() => {
  if (!activeSession.value) return null
  return agents.value.find(a => a.id === activeSession.value.agentId) || null
})

// 按智能体分组会话
const groupedSessions = computed(() => {
  const groups = {}
  for (const session of chatSessions.value) {
    const key = session.agentName || '未知智能体'
    if (!groups[key]) groups[key] = []
    groups[key].push(session)
  }
  return groups
})

function generateId() {
  return Date.now().toString(36) + Math.random().toString(36).substring(2, 8)
}

function createSession(agentId) {
  const agent = agents.value.find(a => a.id === agentId)
  const session = {
    id: generateId(),
    agentId,
    agentName: agent?.name || '智能体',
    title: '新对话',
    messages: [],
    createdAt: new Date(),
    updatedAt: new Date()
  }
  chatSessions.value.unshift(session)
  activeSessionId.value = session.id
  return session
}

function selectSession(sessionId) {
  activeSessionId.value = sessionId
  nextTick(() => scrollToBottom())
}

function deleteSession(sessionId) {
  const idx = chatSessions.value.findIndex(s => s.id === sessionId)
  if (idx === -1) return
  chatSessions.value.splice(idx, 1)
  if (activeSessionId.value === sessionId) {
    activeSessionId.value = chatSessions.value.length > 0 ? chatSessions.value[0].id : null
  }
}

// ====== 聊天状态 ======
const inputText = ref('')
const isStreaming = ref(false)
const streamingContent = ref('')
const streamingReasoning = ref('')
const chatContainer = ref(null)
const sidebarOpen = ref(true)
const showAgentPicker = ref(false)

// ====== 生命周期 ======
onMounted(async () => {
  const res = await api.get('/agents')
  if (res.code === 200) {
    agents.value = (res.data || []).filter(a => a.status === 0 || a.status === 'ACTIVE')
  }
  loading.value = false
})

// ====== 方法 ======

async function scrollToBottom() {
  await nextTick()
  if (chatContainer.value) {
    chatContainer.value.scrollTop = chatContainer.value.scrollHeight
  }
}

/** 选择智能体并创建新会话 */
function pickAgent(agentId) {
  createSession(agentId)
  showAgentPicker.value = false
  nextTick(() => scrollToBottom())
}

/** 新建对话 */
function handleNewChat() {
  if (agents.value.length === 1) {
    pickAgent(agents.value[0].id)
  } else {
    showAgentPicker.value = true
  }
}

/** 发送消息 */
async function sendMessage() {
  const text = inputText.value.trim()
  if (!text || isStreaming.value || !activeSession.value) return

  const session = activeSession.value

  // 更新标题
  if (session.messages.length === 0) {
    session.title = text.length > 20 ? text.substring(0, 20) + '...' : text
  }

  // 添加用户消息
  session.messages.push({
    role: 'user',
    content: text,
    timestamp: new Date().toLocaleTimeString()
  })
  inputText.value = ''
  session.updatedAt = new Date()
  await scrollToBottom()

  // 开始流式请求
  isStreaming.value = true
  streamingContent.value = ''
  streamingReasoning.value = ''

  try {
    const res = await fetch(`${API_BASE}/chat/completions`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${authStore.token}`
      },
      body: JSON.stringify({
        agentId: session.agentId,
        messages: session.messages.filter(m => m.role === 'user' || m.role === 'assistant').map(m => ({
          role: m.role,
          content: m.content
        })),
        stream: true
      })
    })

    if (!res.ok) {
      const err = await res.json().catch(() => ({}))
      session.messages.push({
        role: 'error',
        content: `请求失败: ${err.message || res.statusText}`,
        timestamp: new Date().toLocaleTimeString()
      })
      isStreaming.value = false
      return
    }

    // 读取 SSE 流
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
            const reasoningDelta = json.choices?.[0]?.delta?.reasoning_content
            if (reasoningDelta) {
              streamingReasoning.value += reasoningDelta
              await scrollToBottom()
            }
            const delta = json.choices?.[0]?.delta?.content
            if (delta) {
              streamingContent.value += delta
              await scrollToBottom()
            }
          } catch {
            if (data) {
              streamingContent.value += data
              await scrollToBottom()
            }
          }
        }
      }
    }

    // 流式结束
    if (streamingContent.value || streamingReasoning.value) {
      session.messages.push({
        role: 'assistant',
        content: streamingContent.value,
        reasoning: streamingReasoning.value,
        timestamp: new Date().toLocaleTimeString()
      })
      session.updatedAt = new Date()
    }
  } catch (e) {
    session.messages.push({
      role: 'error',
      content: `请求异常: ${e.message}`,
      timestamp: new Date().toLocaleTimeString()
    })
  } finally {
    isStreaming.value = false
    streamingContent.value = ''
    streamingReasoning.value = ''
    await scrollToBottom()
  }
}

/** 复制消息 */
const copiedIndex = ref(-1)
async function copyMessage(content, index) {
  try {
    await navigator.clipboard.writeText(content)
    copiedIndex.value = index
    setTimeout(() => { copiedIndex.value = -1 }, 2000)
  } catch {
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

/** 键盘事件 */
function handleKeydown(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    sendMessage()
  }
}

/** 时间格式 */
function formatTime(date) {
  if (!date) return ''
  const d = new Date(date)
  const now = new Date()
  const diff = now - d
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'
  if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'
  return d.toLocaleDateString()
}

/** 快捷提问 */
const quickPrompts = [
  '你好，请介绍一下你自己',
  '你能帮我做什么？',
  '帮我写一段代码',
  '解释一下这个概念'
]
</script>

<template>
  <div class="h-[calc(100vh-64px)] flex animate-fade-in -m-6">
    <!-- 左侧：聊天记录列表 -->
    <div :class="[
      'flex flex-col border-r border-white/5 bg-[#0a0f1c]/80 transition-all duration-300 shrink-0',
      sidebarOpen ? 'w-72' : 'w-0 overflow-hidden border-r-0'
    ]">
      <!-- 新建对话按钮 -->
      <div class="p-3 border-b border-white/5">
        <button
          @click="handleNewChat"
          class="w-full flex items-center justify-center gap-2 px-3 py-2.5 rounded-xl bg-cyan-500/10 border border-cyan-500/20 text-cyan-300 hover:bg-cyan-500/20 hover:border-cyan-500/30 transition-all text-sm font-medium"
        >
          <Plus class="w-4 h-4" />
          新建对话
        </button>
      </div>

      <!-- 会话列表 -->
      <div class="flex-1 overflow-y-auto p-2 space-y-0.5">
        <!-- 加载中 -->
        <div v-if="loading" class="flex items-center justify-center py-12">
          <div class="w-6 h-6 border-2 border-cyan-400/30 border-t-cyan-400 rounded-full animate-spin"></div>
        </div>

        <template v-else>
          <!-- 按智能体分组显示 -->
          <template v-for="(sessions, agentName) in groupedSessions" :key="agentName">
            <div class="px-2 pt-3 pb-1">
              <div class="flex items-center gap-1.5">
                <Bot class="w-3 h-3 text-slate-500" />
                <span class="text-[11px] font-medium text-slate-500 uppercase tracking-wider">{{ agentName }}</span>
              </div>
            </div>
            <button
              v-for="session in sessions"
              :key="session.id"
              @click="selectSession(session.id)"
              :class="[
                'w-full flex items-center gap-2.5 px-3 py-2 rounded-lg text-left transition-all duration-200 group',
                activeSessionId === session.id
                  ? 'bg-cyan-500/10 border border-cyan-500/15'
                  : 'hover:bg-white/5 border border-transparent'
              ]"
            >
              <MessageSquare :class="['w-3.5 h-3.5 shrink-0', activeSessionId === session.id ? 'text-cyan-400' : 'text-slate-500']" />
              <div class="flex-1 min-w-0">
                <p :class="['text-xs font-medium truncate', activeSessionId === session.id ? 'text-white' : 'text-slate-300']">
                  {{ session.title }}
                </p>
                <p class="text-[10px] text-slate-600 truncate">{{ session.messages.length }}条消息 · {{ formatTime(session.updatedAt) }}</p>
              </div>
              <button
                @click.stop="deleteSession(session.id)"
                class="opacity-0 group-hover:opacity-100 p-1 rounded text-slate-500 hover:text-red-400 hover:bg-red-500/10 transition-all"
              >
                <X class="w-3 h-3" />
              </button>
            </button>
          </template>

          <!-- 空状态 -->
          <div v-if="chatSessions.length === 0" class="text-center py-12 px-4">
            <MessageSquare class="w-8 h-8 text-slate-600 mx-auto mb-2" />
            <p class="text-xs text-slate-500 mb-1">暂无对话记录</p>
            <p class="text-[11px] text-slate-600">点击上方按钮开始新对话</p>
          </div>
        </template>
      </div>
    </div>

    <!-- 右侧：聊天区域 -->
    <div class="flex-1 flex flex-col min-w-0 relative">
      <!-- 顶部栏 -->
      <div class="flex items-center justify-between px-4 py-3 border-b border-white/5 bg-[#0a0f1c]/50 shrink-0">
        <div class="flex items-center gap-3">
          <button @click="sidebarOpen = !sidebarOpen" class="text-slate-400 hover:text-white transition-colors p-1">
            <MessageSquare class="w-5 h-5" />
          </button>
          <template v-if="activeSession">
            <div class="w-8 h-8 rounded-lg bg-cyan-500/15 flex items-center justify-center">
              <Bot class="w-4 h-4 text-cyan-400" />
            </div>
            <div>
              <h2 class="text-sm font-semibold text-white">{{ activeSession.agentName }}</h2>
              <p class="text-[11px] text-slate-500">{{ activeSession.title }}</p>
            </div>
          </template>
          <template v-else>
            <p class="text-sm text-slate-400">开始新对话</p>
          </template>
        </div>
        <div v-if="activeSession" class="flex items-center gap-2">
          <button
            @click="handleNewChat"
            class="p-2 rounded-lg text-slate-400 hover:text-white hover:bg-white/5 transition-all"
            title="新建对话"
          >
            <Plus class="w-4 h-4" />
          </button>
          <button
            @click="deleteSession(activeSession.id)"
            class="p-2 rounded-lg text-slate-400 hover:text-red-400 hover:bg-red-500/5 transition-all"
            title="删除对话"
          >
            <Trash2 class="w-4 h-4" />
          </button>
        </div>
      </div>

      <!-- 智能体选择弹窗 -->
      <div v-if="showAgentPicker" class="absolute inset-0 z-30 flex items-center justify-center bg-black/40" @click.self="showAgentPicker = false">
        <div class="bg-[#0e1629] border border-white/10 rounded-2xl p-6 w-96 max-h-[80vh] overflow-y-auto shadow-2xl">
          <div class="flex items-center justify-between mb-4">
            <h3 class="text-base font-semibold text-white">选择智能体</h3>
            <button @click="showAgentPicker = false" class="text-slate-400 hover:text-white transition-colors">
              <X class="w-5 h-5" />
            </button>
          </div>
          <div class="space-y-2">
            <button
              v-for="agent in agents"
              :key="agent.id"
              @click="pickAgent(agent.id)"
              class="w-full flex items-center gap-3 px-4 py-3 rounded-xl text-left hover:bg-white/5 border border-transparent hover:border-white/10 transition-all"
            >
              <div class="w-10 h-10 rounded-lg bg-cyan-500/10 flex items-center justify-center shrink-0">
                <Bot class="w-5 h-5 text-cyan-400" />
              </div>
              <div class="flex-1 min-w-0">
                <p class="text-sm font-medium text-white truncate">{{ agent.name }}</p>
                <p class="text-xs text-slate-500 truncate">{{ agent.description || '智能体' }}</p>
              </div>
            </button>
          </div>
          <div v-if="agents.length === 0" class="text-center py-8">
            <Bot class="w-8 h-8 text-slate-600 mx-auto mb-2" />
            <p class="text-xs text-slate-500">暂无可用智能体</p>
          </div>
        </div>
      </div>

      <!-- 未选择会话 -->
      <div v-if="!activeSession && !loading" class="flex-1 flex items-center justify-center">
        <div class="text-center">
          <div class="w-16 h-16 rounded-2xl bg-gradient-to-br from-cyan-500/15 to-emerald-500/15 flex items-center justify-center mx-auto mb-4">
            <Bot class="w-8 h-8 text-cyan-400" />
          </div>
          <h3 class="text-lg font-semibold text-white mb-2">智能体对话</h3>
          <p class="text-sm text-slate-400 mb-6">选择一个智能体，开始对话</p>
          <button
            @click="handleNewChat"
            class="px-5 py-2.5 rounded-xl bg-cyan-500/10 border border-cyan-500/20 text-cyan-300 hover:bg-cyan-500/20 transition-all text-sm font-medium"
          >
            <Plus class="w-4 h-4 inline mr-1.5 -mt-0.5" />
            新建对话
          </button>
        </div>
      </div>

      <!-- 对话区域 -->
      <div v-if="activeSession" ref="chatContainer" class="flex-1 overflow-y-auto px-6 py-4 space-y-4">
        <!-- 空状态 -->
        <div v-if="activeSession.messages.length === 0 && !isStreaming" class="flex flex-col items-center justify-center h-full text-center">
          <div class="w-14 h-14 rounded-2xl bg-gradient-to-br from-cyan-500/15 to-emerald-500/15 flex items-center justify-center mb-4">
            <Bot class="w-7 h-7 text-cyan-400" />
          </div>
          <h3 class="text-base font-semibold text-white mb-1">{{ activeAgent?.name || activeSession.agentName }}</h3>
          <p class="text-slate-400 text-sm mb-6">{{ activeAgent?.description || '开始对话吧' }}</p>
          <div class="flex flex-wrap gap-2 max-w-lg justify-center">
            <button
              v-for="prompt in quickPrompts"
              :key="prompt"
              @click="inputText = prompt"
              class="px-3 py-1.5 text-xs rounded-lg bg-white/5 text-slate-300 border border-white/10 hover:bg-white/10 hover:border-cyan-500/30 transition-colors"
            >
              {{ prompt }}
            </button>
          </div>
        </div>

        <!-- 消息列表 -->
        <template v-for="(msg, index) in activeSession.messages" :key="index">
          <div v-if="msg.role === 'user'" class="flex justify-end">
            <div class="max-w-[70%] bg-cyan-500/10 border border-cyan-500/20 rounded-2xl rounded-tr-sm px-4 py-3">
              <div class="text-sm text-white whitespace-pre-wrap leading-relaxed">{{ msg.content }}</div>
              <div class="text-[10px] text-slate-500 mt-1 text-right">{{ msg.timestamp }}</div>
            </div>
          </div>

          <div v-else-if="msg.role === 'assistant'" class="flex justify-start">
            <div class="max-w-[70%] bg-slate-800/50 border border-white/5 rounded-2xl rounded-tl-sm px-4 py-3">
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
                <div class="text-[10px] text-slate-500">{{ msg.timestamp }}</div>
                <button @click="copyMessage(msg.content, index)" class="text-slate-500 hover:text-slate-300 transition-colors">
                  <Check v-if="copiedIndex === index" class="w-3.5 h-3.5 text-emerald-400" />
                  <Copy v-else class="w-3.5 h-3.5" />
                </button>
              </div>
            </div>
          </div>

          <div v-else-if="msg.role === 'error'" class="flex justify-center">
            <div class="bg-red-500/10 border border-red-500/20 rounded-lg px-4 py-2 text-sm text-red-400">
              {{ msg.content }}
            </div>
          </div>
        </template>

        <!-- 流式响应 -->
        <div v-if="isStreaming && (streamingContent || streamingReasoning)" class="flex justify-start">
          <div class="max-w-[70%] bg-slate-800/50 border border-white/5 rounded-2xl rounded-tl-sm px-4 py-3">
            <div v-if="streamingReasoning" class="mb-2">
              <div class="text-xs text-amber-400/70 flex items-center gap-1 mb-1">
                <span class="animate-pulse">&#9679;</span> 思考中...
              </div>
              <div class="p-2 bg-slate-900/50 rounded-lg text-xs text-slate-400 whitespace-pre-wrap leading-relaxed max-h-40 overflow-y-auto">{{ streamingReasoning }}</div>
            </div>
            <div v-if="streamingContent" class="text-sm text-slate-200 whitespace-pre-wrap leading-relaxed">
              {{ streamingContent }}
              <span class="inline-block w-1.5 h-4 bg-cyan-400 animate-pulse ml-0.5 align-middle"></span>
            </div>
          </div>
        </div>

        <div v-if="isStreaming && !streamingContent && !streamingReasoning" class="flex justify-start">
          <div class="bg-slate-800/50 border border-white/5 rounded-2xl px-4 py-3 flex items-center gap-2">
            <Loader2 class="w-4 h-4 text-cyan-400 animate-spin" />
            <span class="text-sm text-slate-400">思考中...</span>
          </div>
        </div>
      </div>

      <!-- 输入区域 -->
      <div v-if="activeSession" class="px-4 py-3 border-t border-white/5 bg-[#0a0f1c]/50 shrink-0">
        <div class="flex items-end gap-3 max-w-4xl mx-auto">
          <div class="flex-1 relative">
            <textarea
              v-model="inputText"
              @keydown="handleKeydown"
              rows="1"
              class="w-full resize-none px-4 py-2.5 rounded-xl bg-white/5 border border-white/10 text-sm text-white placeholder-slate-500 focus:outline-none focus:border-cyan-500/40 focus:bg-white/[0.07] transition-all pr-4"
              placeholder="输入消息... (Enter 发送, Shift+Enter 换行)"
              :disabled="isStreaming"
              @input="$event.target.style.height = 'auto'; $event.target.style.height = Math.min($event.target.scrollHeight, 120) + 'px'"
            ></textarea>
          </div>
          <button
            @click="sendMessage"
            :disabled="isStreaming || !inputText.trim()"
            :class="[
              'flex items-center gap-2 h-10 px-4 rounded-xl text-sm font-medium transition-all',
              isStreaming || !inputText.trim()
                ? 'bg-white/5 text-slate-500 cursor-not-allowed'
                : 'bg-cyan-500/15 text-cyan-300 border border-cyan-500/25 hover:bg-cyan-500/25 hover:shadow-lg hover:shadow-cyan-500/10'
            ]"
          >
            <Send class="w-4 h-4" />
            发送
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.overflow-y-auto::-webkit-scrollbar {
  width: 4px;
}
.overflow-y-auto::-webkit-scrollbar-track {
  background: transparent;
}
.overflow-y-auto::-webkit-scrollbar-thumb {
  background: rgba(148, 163, 184, 0.15);
  border-radius: 2px;
}
.overflow-y-auto::-webkit-scrollbar-thumb:hover {
  background: rgba(148, 163, 184, 0.3);
}
</style>
