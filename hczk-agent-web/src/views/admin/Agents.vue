<script setup>
import { ref, onMounted, onUnmounted, computed, nextTick } from 'vue';
import { useApiStore, API_BASE } from '@/stores/api';
import { useAuthStore } from '@/stores/auth';
import {
  Bot, Plus, Search, Trash2, Settings2, Power, X, RefreshCw, Wifi, WifiOff,
  Activity, Heart, MessageSquare,
  Send, ChevronDown, Link2, Loader2, Sparkles,
  UserPlus
} from 'lucide-vue-next';

const api = useApiStore();
const auth = useAuthStore();
const loading = ref(false);
const showAddModal = ref(false);
const searchQuery = ref('');
const editingAgent = ref(null);
const agents = ref([]);
const agentHealthMap = ref({});
const agentInfoMap = ref({});
const lastUpdate = ref(null);
const autoRefresh = ref(true);
// 绑定数据
const bindings = ref([]);
const users = ref([]);
// 绑定弹窗
const showBindModal = ref(false);
const bindTargetAgent = ref(null);
const bindForm = ref({ userId: '', enabled: true });
// 试用面板状态: { [agentId]: boolean }
const trialOpenMap = ref({});
// 试用对话状态: { [agentId]: { messages: [], input: '', loading: false } }
const trialStateMap = ref({});
let refreshInterval = null;

const newAgent = ref({
  name: '',
  description: '',
  agentType: '',
  healthEndpoint: '',
  chatEndpoint: '',
  streamEndpoint: '',
  documentEndpoint: '',
  infoEndpoint: '',
  historyEndpoint: '',
  version: ''
});

// 统计概览
const stats = computed(() => {
  const total = agents.value.length;
  const onlineCount = agents.value.filter(a => agentHealthMap.value[a.id]?.online).length;
  const offlineCount = total - onlineCount;
  const totalCalls = agents.value.reduce((sum, a) => sum + (a.totalCalls || 0), 0);
  const boundCount = bindings.value.length;
  return { total, onlineCount, offlineCount, totalCalls, boundCount };
});

// 过滤后的智能体列表
const filteredAgents = computed(() => {
  if (!searchQuery.value) return agents.value;
  const q = searchQuery.value.toLowerCase();
  return agents.value.filter(a =>
    a.name?.toLowerCase().includes(q) || a.agentType?.toLowerCase().includes(q)
  );
});

onMounted(() => {
  loadData();
  if (autoRefresh.value) startAutoRefresh();
});
onUnmounted(() => stopAutoRefresh());

async function loadData() {
  loading.value = true;
  const [agentRes, bindingRes, userRes] = await Promise.all([
    api.get('/agents'),
    api.get('/platform/bindings').catch(() => ({ code: 200, data: [] })),
    api.get('/users').catch(() => ({ code: 200, data: [] }))
  ]);
  if (agentRes.code === 200) agents.value = agentRes.data || [];
  if (bindingRes.code === 200) bindings.value = bindingRes.data || [];
  if (userRes.code === 200) users.value = userRes.data || [];
  loading.value = false;
  await Promise.all([loadHealthStatus(), loadAllAgentInfo()]);
}

async function loadHealthStatus() {
  try {
    const res = await api.get('/agents/health');
    if (res.code === 200 && res.data) {
      agentHealthMap.value = res.data;
    }
    lastUpdate.value = new Date().toLocaleString('zh-CN');
  } catch (e) {
    console.error('加载健康状态失败:', e);
  }
}

async function loadAllAgentInfo() {
  // 并行请求所有 agent 的 info，避免串行 N+1
  const tasks = agents.value
    .filter(agent => agent.infoEndpoint)
    .map(agent => api.get(`/agents/${agent.id}/info`)
      .then(res => ({ id: agent.id, data: res.code === 200 ? res.data : null }))
      .catch(() => ({ id: agent.id, data: null }))
    );
  const results = await Promise.all(tasks);
  for (const { id, data } of results) {
    if (data) agentInfoMap.value[id] = data;
  }
  agentInfoMap.value = { ...agentInfoMap.value };
}

async function checkSingleHealth(agent) {
  try {
    const res = await api.get(`/agents/${agent.id}/health`);
    if (res.code === 200 && res.data) {
      agentHealthMap.value[agent.id] = res.data;
      agentHealthMap.value = { ...agentHealthMap.value };
    }
  } catch (e) {
    console.error('检测智能体健康状态失败:', e);
  }
}

function startAutoRefresh() {
  if (refreshInterval) return;
  refreshInterval = setInterval(() => { loadHealthStatus(); }, 30000);
}

function stopAutoRefresh() {
  if (refreshInterval) {
    clearInterval(refreshInterval);
    refreshInterval = null;
  }
}

function toggleAutoRefresh() {
  autoRefresh.value = !autoRefresh.value;
  if (autoRefresh.value) startAutoRefresh();
  else stopAutoRefresh();
}

async function toggleStatus(agent) {
  const res = await api.post(`/agents/${agent.id}/toggle`);
  if (res.code === 200) {
    agent.status = res.data.status;
  } else {
    alert(res.message || '操作失败');
  }
}

async function deleteAgent(id) {
  if (!confirm('确定注销该智能体？此操作不可恢复。')) return;
  const res = await api.del(`/agents/${id}`);
  if (res.code === 200) { loadData(); }
  else { alert(res.message || '删除失败'); }
}

async function saveAgent() {
  if (!newAgent.value.name) { alert('请输入智能体名称'); return; }
  if (!newAgent.value.healthEndpoint) { alert('请输入健康检测接口地址'); return; }
  if (!newAgent.value.chatEndpoint) { alert('请输入对话接口地址'); return; }

  const body = {
    name: newAgent.value.name,
    description: newAgent.value.description,
    agentType: newAgent.value.agentType || null,
    healthEndpoint: newAgent.value.healthEndpoint,
    chatEndpoint: newAgent.value.chatEndpoint,
    streamEndpoint: newAgent.value.streamEndpoint || null,
    documentEndpoint: newAgent.value.documentEndpoint || null,
    infoEndpoint: newAgent.value.infoEndpoint || null,
    historyEndpoint: newAgent.value.historyEndpoint || null,
    version: newAgent.value.version || null
  };

  if (editingAgent.value) {
    const res = await api.put(`/agents/${editingAgent.value.id}`, body);
    if (res.code === 200) {
      showAddModal.value = false;
      editingAgent.value = null;
      resetForm();
      loadData();
    } else { alert(res.message || '更新失败'); }
  } else {
    const res = await api.post('/agents', body);
    if (res.code === 200) {
      showAddModal.value = false;
      resetForm();
      loadData();
    } else { alert(res.message || '注册失败'); }
  }
}

function editAgent(agent) {
  editingAgent.value = { ...agent };
  newAgent.value = {
    name: agent.name,
    description: agent.description || '',
    agentType: agent.agentType || '',
    healthEndpoint: agent.healthEndpoint || '',
    chatEndpoint: agent.chatEndpoint || '',
    streamEndpoint: agent.streamEndpoint || '',
    documentEndpoint: agent.documentEndpoint || '',
    infoEndpoint: agent.infoEndpoint || '',
    historyEndpoint: agent.historyEndpoint || '',
    version: agent.version || ''
  };
  showAddModal.value = true;
}

function resetForm() {
  newAgent.value = {
    name: '',
    description: '',
    agentType: '',
    healthEndpoint: '',
    chatEndpoint: '',
    streamEndpoint: '',
    documentEndpoint: '',
    infoEndpoint: '',
    historyEndpoint: '',
    version: ''
  };
}

function getHealthInfo(agentId) {
  return agentHealthMap.value[agentId] || null;
}

function getAgentInfo(agentId) {
  return agentInfoMap.value[agentId] || null;
}

function getHealthDotClass(agentId) {
  const health = getHealthInfo(agentId);
  if (!health) return 'bg-slate-500';
  if (health.online) {
    if (health.runtimeStatus === 'degraded') return 'bg-amber-400 animate-pulse';
    return 'bg-emerald-400';
  }
  return 'bg-red-400';
}

function getHealthBadgeClass(agentId) {
  const health = getHealthInfo(agentId);
  if (!health) return 'bg-slate-500/10 text-slate-400 border-slate-500/20';
  if (health.online) {
    if (health.runtimeStatus === 'degraded') return 'bg-amber-500/10 text-amber-400 border-amber-500/20';
    return 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20';
  }
  return 'bg-red-500/10 text-red-400 border-red-500/20';
}

function getHealthText(agentId) {
  const health = getHealthInfo(agentId);
  if (!health) return '未检测';
  if (health.online) {
    if (health.runtimeStatus === 'degraded') return '降级';
    return '在线';
  }
  return '离线';
}

// 能力标签
function getCapabilityTags(agentId) {
  const info = getAgentInfo(agentId);
  if (!info?.capabilities) return [];
  const tags = [];
  const cap = info.capabilities;
  if (cap.knowledge_retrieval) tags.push({ label: '知识库', color: 'bg-blue-500/10 text-blue-400 border-blue-500/20' });
  if (cap.tool_calling) tags.push({ label: '工具调用', color: 'bg-purple-500/10 text-purple-400 border-purple-500/20' });
  if (cap.multi_turn) tags.push({ label: '多轮对话', color: 'bg-cyan-500/10 text-cyan-400 border-cyan-500/20' });
  if (cap.streaming) tags.push({ label: '流式输出', color: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20' });
  if (cap.thinking) tags.push({ label: '思考模式', color: 'bg-amber-500/10 text-amber-400 border-amber-500/20' });
  return tags;
}

function getToolList(agentId) {
  const info = getAgentInfo(agentId);
  return info?.tools || [];
}

// ====== 绑定相关逻辑 ======
function getAgentBindings(agentId) {
  return bindings.value.filter(b => b.agentId === agentId);
}

function getUserName(userId) {
  const user = users.value.find(u => u.userId === userId);
  return user ? user.username : `用户#${userId}`;
}

// 打开绑定弹窗
function openBindModal(agent) {
  bindTargetAgent.value = agent;
  bindForm.value = { userId: '', enabled: true };
  showBindModal.value = true;
}

// 绑定表单中可选的用户（未绑定到当前智能体的用户）
const bindableUsers = computed(() => {
  if (!bindTargetAgent.value) return [];
  const boundUserIds = getAgentBindings(bindTargetAgent.value.id).map(b => b.userId);
  return users.value.filter(u => !boundUserIds.includes(u.userId));
});

// 保存绑定
async function saveBinding() {
  if (!bindForm.value.userId) { alert('请选择用户'); return; }

  const res = await api.post('/platform/bindings', {
    userId: bindForm.value.userId,
    agentId: bindTargetAgent.value.id,
    enabled: bindForm.value.enabled
  });

  if (res.code === 200) {
    showBindModal.value = false;
    loadData();
  } else {
    alert(res.message || '绑定失败');
  }
}

// 解除绑定
async function unbindUser(agentId, userId) {
  if (!confirm('确定解除该用户的绑定？')) return;
  const res = await api.del(`/platform/bindings/${userId}`);
  if (res.code === 200) {
    loadData();
  } else {
    alert(res.message || '解绑失败');
  }
}

// 切换绑定启用状态
async function toggleBindingEnabled(binding) {
  const res = await api.patch(`/platform/bindings/${binding.userId}?enabled=${!binding.enabled}`);
  if (res.code === 200) {
    binding.enabled = !binding.enabled;
  } else {
    alert(res.message || '操作失败');
  }
}

function isAgentOnline(agentId) {
  return agentHealthMap.value[agentId]?.online;
}

// ====== 试用对话相关逻辑 ======
function initTrialState(agentId) {
  if (!trialStateMap.value[agentId]) {
    trialStateMap.value[agentId] = {
      messages: [],
      input: '',
      loading: false,
      sessionId: null,
      error: null
    };
  }
  return trialStateMap.value[agentId];
}

function toggleTrialPanel(agentId) {
  trialOpenMap.value[agentId] = !trialOpenMap.value[agentId];
  if (trialOpenMap.value[agentId]) {
    initTrialState(agentId);
  }
}

function getTrialState(agentId) {
  return trialStateMap.value[agentId] || initTrialState(agentId);
}

async function sendTrialMessage(agent) {
  const state = getTrialState(agent.id);
  if (!state.input.trim() || state.loading) return;

  const userMessage = state.input.trim();
  state.input = '';
  state.error = null;

  // 添加用户消息
  state.messages.push({
    role: 'user',
    content: userMessage,
    timestamp: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
  });
  state.loading = true;

  try {
    // 统一通过后端 /chat/completions 接口代理，避免前端跨域问题
    const useStream = getAgentInfo(agent.id)?.capabilities?.streaming ?? !!agent.streamEndpoint;

    if (useStream) {
      // 流式请求（通过后端代理）
      await sendStreamRequest(agent, state, userMessage);
    } else {
      // 普通请求（通过后端代理）
      await sendNormalRequest(agent, state, userMessage);
    }
  } catch (err) {
    state.messages.push({
      role: 'assistant',
      content: `[错误] ${err.message || '请求失败，请检查智能体连接'}`,
      timestamp: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' }),
      isError: true
    });
  } finally {
    state.loading = false;
    await nextTick();
    scrollToTrialBottom(agent.id);
  }
}

async function sendStreamRequest(agent, state, message) {
  // 通过后端 /chat/completions 接口进行流式请求（服务端代理）
  const endpoint = `${API_BASE}/chat/completions`;

  const headers = {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${auth.token || ''}`
  };

  const body = {
    agentId: agent.id,
    message,
    stream: true
  };

  // 创建占位消息用于流式更新
  const assistantMsgIndex = state.messages.length;
  state.messages.push({
    role: 'assistant',
    content: '',
    timestamp: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' }),
    isStreaming: true
  });

  try {
    const response = await fetch(endpoint, {
      method: 'POST',
      headers,
      body: JSON.stringify(body)
    });

    if (!response.ok) {
      const errorData = await response.json().catch(() => ({}));
      throw new Error(errorData.error || `HTTP ${response.status}`);
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split('\n');
      buffer = lines.pop() || '';

      for (const line of lines) {
        if (line.startsWith('data:')) {
          const data = line.slice(5).trim();
          if (data === '[DONE]') continue;
          try {
            const parsed = JSON.parse(data);
            if (parsed.content) {
              state.messages[assistantMsgIndex].content += parsed.content;
            }
          } catch (e) {
            // 非JSON数据，直接追加文本
            state.messages[assistantMsgIndex].content += data;
          }
        }
      }
    }

    state.messages[assistantMsgIndex].isStreaming = false;
    if (!state.messages[assistantMsgIndex].content) {
      state.messages[assistantMsgIndex].content = '[智能体返回空响应]';
    }
  } catch (err) {
    state.messages[assistantMsgIndex].content = `[流式请求错误] ${err.message}`;
    state.messages[assistantMsgIndex].isStreaming = false;
    state.messages[assistantMsgIndex].isError = true;
  }
}

async function sendNormalRequest(agent, state, message) {
  // 通过后端 /chat/completions 接口进行普通请求（服务端代理）
  const endpoint = `${API_BASE}/chat/completions`;

  const headers = {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${auth.token || ''}`
  };

  const body = {
    agentId: agent.id,
    message,
    stream: false
  };

  const response = await fetch(endpoint, {
    method: 'POST',
    headers,
    body: JSON.stringify(body)
  });

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}));
    throw new Error(errorData.error || `HTTP ${response.status}`);
  }

  let result;
  const contentType = response.headers.get('content-type') || '';
  if (contentType.includes('application/json')) {
    result = await response.json();
  } else {
    result = { content: await response.text() };
  }

  state.messages.push({
    role: 'assistant',
    content: result?.content || result?.reply || result?.message || JSON.stringify(result),
    timestamp: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
  });
}

function clearTrialChat(agentId) {
  const state = getTrialState(agentId);
  state.messages = [];
  state.sessionId = null;
  state.error = null;
}

function scrollToTrialBottom(agentId) {
  nextTick(() => {
    const el = document.getElementById(`trial-messages-${agentId}`);
    if (el) el.scrollTop = el.scrollHeight;
  });
}
</script>

<template>
  <div class="space-y-6 animate-fade-in">
    <!-- 页面头部 -->
    <div class="flex items-center justify-between">
      <div>
        <div class="flex items-center gap-3">
          <div class="w-10 h-10 rounded-xl bg-gradient-to-br from-cyan-500/20 to-emerald-500/20 flex items-center justify-center">
            <Bot class="w-5 h-5 text-cyan-400" />
          </div>
          <div>
            <h1 class="text-2xl font-bold text-white">智能体管理</h1>
            <p class="text-sm text-slate-400 mt-0.5">注册容器智能体，实时监控运行状态，统一管理对话路由</p>
          </div>
        </div>
      </div>
      <div class="flex items-center gap-3">
        <span class="text-xs text-slate-500 px-3 py-1.5 rounded-lg bg-white/[0.03] border border-white/[0.06]">
          上次更新: {{ lastUpdate || '--' }}
        </span>
        <button @click="toggleAutoRefresh" :class="['px-3 py-2 rounded-lg flex items-center gap-2 transition-all text-sm font-medium', autoRefresh ? 'bg-emerald-500/15 text-emerald-400 ring-1 ring-emerald-500/25' : 'bg-white/5 text-slate-400 hover:bg-white/8']">
          <RefreshCw class="w-3.5 h-3.5" :class="{ 'animate-spin': autoRefresh }" />
          {{ autoRefresh ? '自动刷新' : '手动刷新' }}
        </button>
        <button @click="loadHealthStatus" class="px-3 py-2 rounded-lg bg-white/5 text-slate-300 hover:bg-white/10 hover:text-white flex items-center gap-2 text-sm transition-colors">
          <Activity class="w-3.5 h-3.5" /> 刷新状态
        </button>
        <button @click="showAddModal = true; editingAgent = null; resetForm()" class="group px-3.5 py-2 rounded-lg bg-white/[0.06] border border-white/[0.08] text-slate-300 hover:text-white hover:bg-white/[0.1] hover:border-cyan-500/25 flex items-center gap-2 text-sm font-medium transition-all duration-200">
          <Plus class="w-3.5 h-3.5 transition-transform group-hover:rotate-90 duration-200" /> 新增智能体
        </button>
      </div>
    </div>

    <!-- 统计概览 -->
    <div class="grid grid-cols-2 md:grid-cols-5 gap-3">
      <div class="glass-card p-4 group hover:border-cyan-500/20 transition-colors">
        <div class="flex items-center gap-3">
          <div class="w-9 h-9 rounded-lg bg-cyan-500/15 group-hover:bg-cyan-500/25 flex items-center justify-center transition-colors">
            <Bot class="w-4.5 h-4.5 text-cyan-400" />
          </div>
          <div>
            <p class="text-[11px] text-slate-500 uppercase tracking-wider font-medium">注册总数</p>
            <p class="text-xl font-bold text-white">{{ stats.total }}</p>
          </div>
        </div>
      </div>
      <div class="glass-card p-4 group hover:border-emerald-500/20 transition-colors">
        <div class="flex items-center gap-3">
          <div class="w-9 h-9 rounded-lg bg-emerald-500/15 group-hover:bg-emerald-500/25 flex items-center justify-center transition-colors">
            <Wifi class="w-4.5 h-4.5 text-emerald-400" />
          </div>
          <div>
            <p class="text-[11px] text-slate-500 uppercase tracking-wider font-medium">在线</p>
            <p class="text-xl font-bold text-emerald-400">{{ stats.onlineCount }}</p>
          </div>
        </div>
      </div>
      <div class="glass-card p-4 group hover:border-red-500/20 transition-colors">
        <div class="flex items-center gap-3">
          <div class="w-9 h-9 rounded-lg bg-red-500/15 group-hover:bg-red-500/25 flex items-center justify-center transition-colors">
            <WifiOff class="w-4.5 h-4.5 text-red-400" />
          </div>
          <div>
            <p class="text-[11px] text-slate-500 uppercase tracking-wider font-medium">离线</p>
            <p class="text-xl font-bold text-red-400">{{ stats.offlineCount }}</p>
          </div>
        </div>
      </div>
      <div class="glass-card p-4 group hover:border-purple-500/20 transition-colors">
        <div class="flex items-center gap-3">
          <div class="w-9 h-9 rounded-lg bg-purple-500/15 group-hover:bg-purple-500/25 flex items-center justify-center transition-colors">
            <Activity class="w-4.5 h-4.5 text-purple-400" />
          </div>
          <div>
            <p class="text-[11px] text-slate-500 uppercase tracking-wider font-medium">总调用次数</p>
            <p class="text-xl font-bold text-white">{{ stats.totalCalls }}</p>
          </div>
        </div>
      </div>
      <div class="glass-card p-4 group hover:border-blue-500/20 transition-colors">
        <div class="flex items-center gap-3">
          <div class="w-9 h-9 rounded-lg bg-blue-500/15 group-hover:bg-blue-500/25 flex items-center justify-center transition-colors">
            <Link2 class="w-4.5 h-4.5 text-blue-400" />
          </div>
          <div>
            <p class="text-[11px] text-slate-500 uppercase tracking-wider font-medium">已绑定</p>
            <p class="text-xl font-bold text-white">{{ stats.boundCount }}</p>
          </div>
        </div>
      </div>
    </div>

    <!-- 搜索栏 -->
    <div class="relative">
      <Search class="absolute left-4 top-1/2 -translate-y-1/2 w-4.5 h-4.5 text-slate-500" />
      <input v-model="searchQuery" class="input-field pl-11 pr-4 py-3" placeholder="搜索智能体名称、类型..." />
    </div>

    <!-- 智能体卡片列表 -->
    <div v-if="loading" class="flex items-center justify-center py-20">
      <Loader2 class="w-8 h-8 text-cyan-400 animate-spin" />
      <span class="ml-3 text-slate-400">加载中...</span>
    </div>
    <div v-else-if="filteredAgents.length === 0" class="text-center py-20">
      <Bot class="w-12 h-12 text-slate-600 mx-auto mb-3" />
      <p class="text-slate-500">暂无注册智能体</p>
      <button @click="showAddModal = true; editingAgent = null; resetForm()" class="mt-4 btn-primary text-sm inline-flex items-center gap-2">
        <Plus class="w-3.5 h-3.5" /> 注册第一个智能体
      </button>
    </div>

    <div v-else class="grid grid-cols-1 lg:grid-cols-2 gap-4">
      <div v-for="agent in filteredAgents" :key="agent.id" :class="['glass-card overflow-hidden transition-all duration-300', trialOpenMap[agent.id] ? 'ring-1 ring-cyan-500/30' : '']">
        <!-- 卡片主体 -->
        <div class="p-4">
          <div class="flex items-start justify-between gap-3">
            <!-- 左侧：图标和信息 -->
            <div class="flex items-start gap-3 min-w-0 flex-1">
              <!-- 图标 + 健康指示灯 -->
              <div class="relative shrink-0">
                <div :class="['w-10 h-10 rounded-xl flex items-center justify-center transition-all', isAgentOnline(agent.id) ? 'bg-gradient-to-br from-cyan-500/20 to-emerald-500/20' : 'bg-gradient-to-br from-slate-500/15 to-slate-600/10']">
                  <Bot :class="['w-5 h-5', isAgentOnline(agent.id) ? 'text-cyan-400' : 'text-slate-500']" />
                </div>
                <div :class="['absolute -top-0.5 -right-0.5 w-3 h-3 rounded-full border-2 border-[#0a0f1c]', getHealthDotClass(agent.id)]"></div>
              </div>

              <!-- 信息区 -->
              <div class="min-w-0 flex-1">
                <!-- 标题行 -->
                <div class="flex items-center gap-2 flex-wrap">
                  <h3 class="text-sm font-semibold text-white truncate">{{ agent.name }}</h3>
                  <span v-if="agent.agentType" class="px-1.5 py-px text-[10px] rounded bg-cyan-500/10 text-cyan-400 font-medium border border-cyan-500/15">
                    {{ agent.agentType }}
                  </span>
                  <span :class="['px-1.5 py-px text-[10px] rounded font-medium border', agent.status === 0 ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20' : 'bg-slate-500/10 text-slate-400 border-slate-500/20']">
                    {{ agent.status === 0 ? '已启用' : '已禁用' }}
                  </span>
                  <span :class="['px-1.5 py-px text-[10px] rounded-full font-medium border', getHealthBadgeClass(agent.id)]">
                    <span class="inline-block w-1 h-1 rounded-full mr-1" :class="getHealthDotClass(agent.id)"></span>
                    {{ getHealthText(agent.id) }}
                  </span>
                </div>

                <!-- 描述 -->
                <p class="text-xs text-slate-400 mt-1 line-clamp-1">{{ agent.description || '暂无描述' }}</p>

                <!-- 能力标签 + 工具 + 统计 合并为紧凑行 -->
                <div class="flex items-center gap-2 mt-2 flex-wrap text-[10px]">
                  <template v-if="getCapabilityTags(agent.id).length > 0">
                    <span v-for="tag in getCapabilityTags(agent.id)" :key="tag.label" :class="['px-1.5 py-px rounded font-medium border', tag.color]">
                      {{ tag.label }}
                    </span>
                  </template>
                  <template v-if="getToolList(agent.id).length > 0">
                    <Sparkles class="w-3 h-3 text-amber-400/60 shrink-0" />
                    <span v-for="(tool, i) in getToolList(agent.id).slice(0, 3)" :key="tool.name" class="text-[10px] text-slate-500">{{ tool.name }}{{ i < Math.min(getToolList(agent.id).length, 3) - 1 ? ',' : '' }}</span>
                    <span v-if="getToolList(agent.id).length > 3" class="text-[10px] text-slate-600">+{{ getToolList(agent.id).length - 3 }}</span>
                  </template>
                  <span class="text-slate-700">|</span>
                  <span class="text-slate-500">调用 {{ agent.totalCalls || 0 }}</span>
                  <span v-if="agent.version || getAgentInfo(agent.id)?.version" class="text-cyan-500/60 font-mono">v{{ getAgentInfo(agent.id)?.version || agent.version }}</span>
                </div>

                <!-- 绑定信息展示（紧凑行） -->
                <div class="mt-2 pt-2 border-t border-white/[0.04]">
                  <div class="flex items-center gap-1.5 flex-wrap">
                    <Link2 class="w-3 h-3 text-blue-400/70" />
                    <template v-if="getAgentBindings(agent.id).length > 0">
                      <div v-for="binding in getAgentBindings(agent.id)" :key="binding.userId"
                        :class="['inline-flex items-center gap-1 px-1.5 py-px rounded text-[10px] border transition-colors', binding.enabled ? 'bg-blue-500/8 text-blue-400/80 border-blue-500/12' : 'bg-slate-500/5 text-slate-500 border-slate-500/8']">
                        <span class="cursor-pointer" @click="toggleBindingEnabled(binding)" title="点击切换启用状态">
                          {{ getUserName(binding.userId) }}
                        </span>
                        <button @click="unbindUser(agent.id, binding.userId)" class="ml-0.5 text-slate-600 hover:text-red-400 transition-colors" title="解绑">
                          <X class="w-2.5 h-2.5" />
                        </button>
                      </div>
                    </template>
                    <span v-else class="text-[10px] text-slate-600">未绑定用户</span>
                    <button @click="openBindModal(agent)" class="ml-auto px-1.5 py-px rounded text-[10px] border border-dashed border-white/[0.08] text-slate-500 hover:text-cyan-400 hover:border-cyan-500/25 transition-colors flex items-center gap-0.5">
                      <UserPlus class="w-2.5 h-2.5" /> 绑定
                    </button>
                  </div>
                </div>
              </div>
            </div>

            <!-- 右侧：操作按钮 -->
            <div class="flex flex-col items-end gap-1.5 shrink-0">
              <div class="flex items-center gap-1">
                <!-- 试用按钮 -->
                <button
                  @click="toggleTrialPanel(agent.id)"
                  :class="['p-1.5 rounded-md transition-all', trialOpenMap[agent.id] ? 'bg-cyan-500/15 text-cyan-400 ring-1 ring-cyan-500/25' : 'bg-white/4 text-slate-500 hover:bg-cyan-500/10 hover:text-cyan-400']"
                  title="试用对话"
                >
                  <MessageSquare class="w-3.5 h-3.5" />
                </button>
                <button @click="checkSingleHealth(agent)" class="p-1.5 rounded-md bg-white/4 text-slate-500 hover:bg-emerald-500/10 hover:text-emerald-400 transition-colors" title="检测健康状态">
                  <RefreshCw class="w-3.5 h-3.5" />
                </button>
                <button @click="toggleStatus(agent)" :class="['p-1.5 rounded-md transition-colors', agent.status === 0 ? 'bg-emerald-500/10 text-emerald-400' : 'bg-slate-500/8 text-slate-500']" title="切换启停">
                  <Power class="w-3.5 h-3.5" />
                </button>
                <button @click="editAgent(agent)" class="p-1.5 rounded-md bg-white/4 text-slate-500 hover:bg-white/8 hover:text-white transition-colors" title="编辑配置">
                  <Settings2 class="w-3.5 h-3.5" />
                </button>
                <button @click="deleteAgent(agent.id)" class="p-1.5 rounded-md bg-white/4 text-slate-500 hover:bg-red-500/10 hover:text-red-400 transition-colors" title="注销智能体">
                  <Trash2 class="w-3.5 h-3.5" />
                </button>
              </div>

              <!-- 接口信息 -->
              <div class="mt-0.5 space-y-px text-[9px] w-[180px]">
                <div class="flex items-center gap-1 text-slate-600 truncate" :title="agent.healthEndpoint">
                  <Heart class="w-2.5 h-2.5 text-rose-400/50 shrink-0" />
                  <span class="truncate">{{ agent.healthEndpoint }}</span>
                </div>
                <div class="flex items-center gap-1 text-slate-600 truncate" :title="agent.chatEndpoint">
                  <MessageSquare class="w-2.5 h-2.5 text-cyan-400/50 shrink-0" />
                  <span class="truncate">{{ agent.chatEndpoint }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 试用对话面板 -->
        <div v-if="trialOpenMap[agent.id]" class="border-t border-cyan-500/15 bg-[#080c16]/80">
          <div class="p-3">
            <!-- 面板头部 -->
            <div class="flex items-center justify-between mb-2">
              <div class="flex items-center gap-1.5">
                <div class="w-1.5 h-1.5 rounded-full bg-cyan-400 animate-pulse"></div>
                <span class="text-xs font-medium text-cyan-400">试用 {{ agent.name }}</span>
                <span v-if="getTrialState(agent.id).messages.length > 0" class="text-[10px] text-slate-600">{{ getTrialState(agent.id).messages.length }} 条</span>
              </div>
              <div class="flex items-center gap-1">
                <button
                  v-if="getTrialState(agent.id).messages.length > 0"
                  @click="clearTrialChat(agent.id)"
                  class="text-[10px] text-slate-500 hover:text-slate-300 px-1.5 py-0.5 rounded hover:bg-white/5 transition-colors"
                >
                  清空
                </button>
                <button
                  @click="toggleTrialPanel(agent.id)"
                  class="p-0.5 text-slate-500 hover:text-white rounded hover:bg-white/5 transition-colors"
                >
                  <ChevronDown class="w-3.5 h-3.5" />
                </button>
              </div>
            </div>

            <!-- 对话消息区域 -->
            <div
              :id="`trial-messages-${agent.id}`"
              class="h-[220px] overflow-y-auto rounded-lg bg-black/30 border border-white/[0.04] p-2.5 space-y-2.5 mb-2 scroll-smooth"
            >
              <!-- 空状态 -->
              <div v-if="getTrialState(agent.id).messages.length === 0" class="h-full flex flex-col items-center justify-center text-slate-500">
                <MessageSquare class="w-6 h-6 opacity-25 mb-1.5" />
                <p class="text-xs">发送消息开始试用</p>
              </div>

              <!-- 消息列表 -->
              <div v-else class="space-y-2.5">
                <div
                  v-for="(msg, idx) in getTrialState(agent.id).messages"
                  :key="idx"
                  :class="['flex gap-2', msg.role === 'user' ? 'justify-end' : 'justify-start']"
                >
                  <!-- 用户消息 -->
                  <template v-if="msg.role === 'user'">
                    <div class="max-w-[80%] bg-cyan-500/15 text-cyan-100 px-2.5 py-2 rounded-xl rounded-br-md text-xs leading-relaxed">
                      {{ msg.content }}
                    </div>
                  </template>

                  <!-- 助手消息 -->
                  <template v-else>
                    <div class="shrink-0 w-5 h-5 rounded-md bg-gradient-to-br from-cyan-500/20 to-emerald-500/20 flex items-center justify-center">
                      <Bot class="w-2.5 h-2.5 text-cyan-400" />
                    </div>
                    <div :class="['max-w-[80%] px-2.5 py-2 rounded-xl rounded-bl-md text-xs leading-relaxed', msg.isError ? 'bg-red-500/10 text-red-300 border border-red-500/15' : 'bg-white/[0.05] text-slate-300 border border-white/[0.06]']">
                      <div v-if="msg.isStreaming" class="flex items-center gap-1.5">
                        <span>{{ msg.content }}</span>
                        <span class="inline-flex gap-0.5">
                          <span class="w-1 h-1 rounded-full bg-cyan-400 animate-bounce" style="animation-delay: 0ms"></span>
                          <span class="w-1 h-1 rounded-full bg-cyan-400 animate-bounce" style="animation-delay: 150ms"></span>
                          <span class="w-1 h-1 rounded-full bg-cyan-400 animate-bounce" style="animation-delay: 300ms"></span>
                        </span>
                      </div>
                      <div v-else class="whitespace-pre-wrap">{{ msg.content }}</div>
                      <div class="text-[9px] text-slate-600 mt-1">{{ msg.timestamp }}</div>
                    </div>
                  </template>
                </div>

                <!-- 加载中提示 -->
                <div v-if="getTrialState(agent.id).loading && !getTrialState(agent.id).messages.some(m => m.role === 'assistant' && m.isStreaming)" class="flex gap-2">
                  <div class="shrink-0 w-5 h-5 rounded-md bg-gradient-to-br from-cyan-500/20 to-emerald-500/20 flex items-center justify-center">
                    <Bot class="w-2.5 h-2.5 text-cyan-400" />
                  </div>
                  <div class="bg-white/[0.05] border border-white/[0.06] px-3 py-2 rounded-xl rounded-bl-md">
                    <div class="flex items-center gap-1.5 text-slate-400 text-xs">
                      <Loader2 class="w-3 h-3 animate-spin" />
                      思考中...
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- 输入区域 -->
            <div class="flex items-center gap-1.5">
              <div class="flex-1 relative">
                <input
                  v-model="getTrialState(agent.id).input"
                  @keydown.enter.prevent="sendTrialMessage(agent)"
                  :disabled="!isAgentOnline(agent.id) || getTrialState(agent.id).loading"
                  :placeholder="!isAgentOnline(agent.id) ? '智能体离线' : '输入消息...'"
                  :class="['input-field pr-20 py-2 text-xs', !isAgentOnline(agent.id) ? 'opacity-50 cursor-not-allowed' : '']"
                />
                <div class="absolute right-1.5 top-1/2 -translate-y-1/2 flex items-center gap-1">
                  <button
                    @click="sendTrialMessage(agent)"
                    :disabled="!isAgentOnline(agent.id) || !getTrialState(agent.id).input.trim() || getTrialState(agent.id).loading"
                    :class="['p-1 rounded transition-all', (getTrialState(agent.id).input.trim() && isAgentOnline(agent.id)) ? 'bg-cyan-500 text-white hover:bg-cyan-400' : 'bg-white/5 text-slate-500']"
                  >
                    <Send class="w-3 h-3" />
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 注册/编辑弹窗 -->
    <div v-if="showAddModal" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm" @click.self="showAddModal = false">
      <div class="glass-card w-full max-w-lg p-6 animate-slide-up max-h-[90vh] overflow-y-auto">
        <div class="flex items-center justify-between mb-5">
          <div>
            <h2 class="text-xl font-bold text-white">{{ editingAgent ? '编辑智能体' : '注册新智能体' }}</h2>
            <p class="text-xs text-slate-500 mt-0.5">{{ editingAgent ? '修改智能体的配置信息' : '将外部容器智能体接入平台' }}</p>
          </div>
          <button @click="showAddModal = false" class="p-1.5 text-slate-400 hover:text-white rounded-lg hover:bg-white/5 transition-colors">
            <X class="w-5 h-5" />
          </button>
        </div>
        <div class="space-y-5">
          <!-- 基本信息 -->
          <div class="pb-4 border-b border-white/[0.06]">
            <p class="text-[11px] text-slate-500 mb-3 uppercase tracking-widest font-semibold">基本信息</p>
            <div class="space-y-3">
              <div>
                <label class="block text-sm text-slate-300 mb-1.5 font-medium">智能体名称 <span class="text-red-400">*</span></label>
                <input v-model="newAgent.name" class="input-field" placeholder="如：客服智能体" />
              </div>
              <div>
                <label class="block text-sm text-slate-300 mb-1.5 font-medium">描述</label>
                <textarea v-model="newAgent.description" class="input-field resize-none" rows="2" placeholder="简要描述智能体的功能与用途"></textarea>
              </div>
              <div class="grid grid-cols-2 gap-3">
                <div>
                  <label class="block text-sm text-slate-300 mb-1.5 font-medium">类型标识</label>
                  <input v-model="newAgent.agentType" class="input-field" placeholder="customer_service" />
                </div>
                <div>
                  <label class="block text-sm text-slate-300 mb-1.5 font-medium">版本号</label>
                  <input v-model="newAgent.version" class="input-field" placeholder="1.0.0" />
                </div>
              </div>
            </div>
          </div>

          <!-- 必填接口 -->
          <div class="pb-4 border-b border-white/[0.06]">
            <p class="text-[11px] text-slate-500 mb-3 uppercase tracking-widest font-semibold">必填接口</p>
            <div class="space-y-3">
              <div>
                <label class="block text-sm text-slate-300 mb-1.5 font-medium">健康检测接口 <span class="text-red-400">*</span></label>
                <input v-model="newAgent.healthEndpoint" class="input-field font-mono text-xs" placeholder="http://agent-host:3000/api/health" />
                <p class="text-[11px] text-slate-500 mt-1">GET，返回 {"status": "ok"|"degraded", "version", "components"}</p>
              </div>
              <div>
                <label class="block text-sm text-slate-300 mb-1.5 font-medium">对话接口 <span class="text-red-400">*</span></label>
                <input v-model="newAgent.chatEndpoint" class="input-field font-mono text-xs" placeholder="http://agent-host:3000/api/chat" />
                <p class="text-[11px] text-slate-500 mt-1">POST，接收 {"message", "session_id", "merchant_id"}</p>
              </div>
            </div>
          </div>

          <!-- 可选接口 -->
          <div class="pb-4 border-b border-white/[0.06]">
            <p class="text-[11px] text-slate-500 mb-3 uppercase tracking-widest font-semibold">可选接口（增强功能）</p>
            <div class="space-y-3">
              <div>
                <label class="block text-sm text-slate-300 mb-1.5 font-medium">流式对话接口</label>
                <input v-model="newAgent.streamEndpoint" class="input-field font-mono text-xs" placeholder="http://agent-host:3000/api/chat/stream" />
                <p class="text-[11px] text-slate-500 mt-1">POST SSE，响应 text/event-stream</p>
              </div>
              <div>
                <label class="block text-sm text-slate-300 mb-1.5 font-medium">元信息接口</label>
                <input v-model="newAgent.infoEndpoint" class="input-field font-mono text-xs" placeholder="http://agent-host:3000/api/agent/info" />
                <p class="text-[11px] text-slate-500 mt-1">GET，返回 {"capabilities", "tools", "model"}</p>
              </div>
              <div class="grid grid-cols-2 gap-3">
                <div>
                  <label class="block text-sm text-slate-300 mb-1.5 font-medium">会话历史接口</label>
                  <input v-model="newAgent.historyEndpoint" class="input-field font-mono text-xs" placeholder="/api/chat/history" />
                </div>
                <div>
                  <label class="block text-sm text-slate-300 mb-1.5 font-medium">文档上传接口</label>
                  <input v-model="newAgent.documentEndpoint" class="input-field font-mono text-xs" placeholder="/api/documents" />
                </div>
              </div>
            </div>
          </div>

        </div>
        <div class="flex gap-3 mt-6 pt-4 border-t border-white/[0.06]">
          <button @click="showAddModal = false" class="btn-secondary flex-1">取消</button>
          <button @click="saveAgent" class="btn-primary flex-1">{{ editingAgent ? '保存修改' : '确认注册' }}</button>
        </div>
      </div>
    </div>

    <!-- 绑定用户弹窗 -->
    <div v-if="showBindModal" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm" @click.self="showBindModal = false">
      <div class="glass-card w-full max-w-md p-6 animate-slide-up">
        <div class="flex items-center justify-between mb-5">
          <div>
            <h2 class="text-lg font-bold text-white">绑定用户</h2>
            <p class="text-xs text-slate-500 mt-0.5">将用户绑定到「{{ bindTargetAgent?.name }}」，绑定后该用户的对话请求将自动路由到此智能体</p>
          </div>
          <button @click="showBindModal = false" class="p-1.5 text-slate-400 hover:text-white rounded-lg hover:bg-white/5 transition-colors">
            <X class="w-5 h-5" />
          </button>
        </div>
        <div class="space-y-4">
          <!-- 选择用户 -->
          <div>
            <label class="block text-sm text-slate-300 mb-1.5 font-medium">选择用户 <span class="text-red-400">*</span></label>
            <select v-model="bindForm.userId" class="input-field">
              <option value="">请选择用户</option>
              <option v-for="u in bindableUsers" :key="u.userId" :value="u.userId">{{ u.username }}{{ u.companyName ? ` (${u.companyName})` : '' }}</option>
            </select>
            <p v-if="bindableUsers.length === 0" class="text-[11px] text-amber-400/70 mt-1">所有用户已绑定此智能体</p>
          </div>

          <!-- 启用状态 -->
          <div class="flex items-center gap-2">
            <input id="bind-enabled" v-model="bindForm.enabled" type="checkbox" class="w-4 h-4 rounded border-slate-600 bg-slate-800 text-emerald-500" />
            <label for="bind-enabled" class="text-sm text-slate-300">立即启用绑定</label>
          </div>
        </div>
        <div class="flex gap-3 mt-6 pt-4 border-t border-white/[0.06]">
          <button @click="showBindModal = false" class="btn-secondary flex-1">取消</button>
          <button @click="saveBinding" :disabled="!bindForm.userId" :class="['flex-1', !bindForm.userId ? 'btn-secondary opacity-50 cursor-not-allowed' : 'btn-primary']">确认绑定</button>
        </div>
      </div>
    </div>
  </div>
</template>
