<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue';
import { useApiStore } from '@/stores/api';
import { Bot, Plus, Search, Trash2, Settings2, Power, X, RefreshCw, Wifi, WifiOff, Activity, Zap, Heart, MessageSquare, FileUp, Info, Radio, History } from 'lucide-vue-next';
const api = useApiStore();
const loading = ref(false);
const showAddModal = ref(false);
const searchQuery = ref('');
const editingAgent = ref(null);
const agents = ref([]);
const agentHealthMap = ref({});
const agentInfoMap = ref({});
const lastUpdate = ref(null);
const autoRefresh = ref(true);
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
  authHeader: '',
  version: ''
});

// 统计概览
const stats = computed(() => {
  const total = agents.value.length;
  const onlineCount = agents.value.filter(a => agentHealthMap.value[a.id]?.online).length;
  const offlineCount = total - onlineCount;
  const totalCalls = agents.value.reduce((sum, a) => sum + (a.totalCalls || 0), 0);
  return { total, onlineCount, offlineCount, totalCalls };
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
  const agentRes = await api.get('/platform/agents');
  if (agentRes.code === 200) agents.value = agentRes.data || [];
  loading.value = false;
  await Promise.all([loadHealthStatus(), loadAllAgentInfo()]);
}

async function loadHealthStatus() {
  try {
    const res = await api.get('/agents/health');
    if (res.code === 200 && res.data) {
      agentHealthMap.value = res.data;
    }
    lastUpdate.value = new Date().toLocaleString();
  } catch (e) {
    console.error('加载健康状态失败:', e);
  }
}

async function loadAllAgentInfo() {
  for (const agent of agents.value) {
    if (agent.infoEndpoint) {
      try {
        const res = await api.get(`/agents/${agent.id}/info`);
        if (res.code === 200 && res.data) {
          agentInfoMap.value[agent.id] = res.data;
        }
      } catch (e) { /* ignore */ }
    }
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
  const res = await api.post(`/platform/agents/${agent.id}/toggle`);
  if (res.code === 200) {
    agent.status = res.data.status;
  } else {
    alert(res.message || '操作失败');
  }
}

async function deleteAgent(id) {
  if (!confirm('确定注销该智能体？')) return;
  const res = await api.del(`/platform/agents/${id}`);
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
    authHeader: newAgent.value.authHeader || null,
    version: newAgent.value.version || null
  };

  if (editingAgent.value) {
    const res = await api.put(`/platform/agents/${editingAgent.value.id}`, body);
    if (res.code === 200) {
      showAddModal.value = false;
      editingAgent.value = null;
      resetForm();
      loadData();
    } else { alert(res.message || '更新失败'); }
  } else {
    const res = await api.post('/platform/agents', body);
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
    authHeader: agent.authHeader || '',
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
    authHeader: '',
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
  if (!health) return 'bg-slate-500/10 text-slate-400';
  if (health.online) {
    if (health.runtimeStatus === 'degraded') return 'bg-amber-500/10 text-amber-400';
    return 'bg-emerald-500/10 text-emerald-400';
  }
  return 'bg-red-500/10 text-red-400';
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
  if (cap.knowledge_retrieval) tags.push({ label: '知识库', color: 'bg-blue-500/10 text-blue-400' });
  if (cap.tool_calling) tags.push({ label: '工具调用', color: 'bg-purple-500/10 text-purple-400' });
  if (cap.multi_turn) tags.push({ label: '多轮对话', color: 'bg-cyan-500/10 text-cyan-400' });
  if (cap.streaming) tags.push({ label: '流式输出', color: 'bg-emerald-500/10 text-emerald-400' });
  if (cap.thinking) tags.push({ label: '思考模式', color: 'bg-amber-500/10 text-amber-400' });
  return tags;
}

function getToolList(agentId) {
  const info = getAgentInfo(agentId);
  return info?.tools || [];
}
</script>

<template>
  <div class="space-y-6 animate-fade-in">
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-2xl font-bold text-white">智能体管理</h1>
        <p class="text-slate-400 mt-1">注册容器智能体，实时监控运行状态，统一管理对话路由</p>
      </div>
      <div class="flex items-center gap-3">
        <span class="text-sm text-slate-400">上次更新: {{ lastUpdate || '--' }}</span>
        <button @click="toggleAutoRefresh" :class="['px-3 py-2 rounded-lg flex items-center gap-2 transition-colors text-sm', autoRefresh ? 'bg-emerald-500/20 text-emerald-400' : 'bg-white/5 text-slate-400']">
          <RefreshCw class="w-3.5 h-3.5" :class="{ 'animate-spin': autoRefresh }" />
          {{ autoRefresh ? '自动' : '手动' }}
        </button>
        <button @click="loadHealthStatus" class="px-3 py-2 rounded-lg bg-white/5 text-slate-300 hover:bg-white/10 flex items-center gap-2 text-sm">
          <RefreshCw class="w-3.5 h-3.5" /> 刷新状态
        </button>
        <button @click="showAddModal = true; editingAgent = null; resetForm()" class="btn-primary flex items-center gap-2">
          <Plus class="w-4 h-4" /> 注册智能体
        </button>
      </div>
    </div>

    <!-- 统计概览 -->
    <div class="grid grid-cols-2 md:grid-cols-4 gap-4">
      <div class="glass-card p-4">
        <div class="flex items-center gap-3">
          <div class="w-10 h-10 rounded-lg bg-cyan-500/20 flex items-center justify-center">
            <Bot class="w-5 h-5 text-cyan-400" />
          </div>
          <div>
            <p class="text-sm text-slate-400">注册总数</p>
            <p class="text-xl font-bold text-white">{{ stats.total }}</p>
          </div>
        </div>
      </div>
      <div class="glass-card p-4">
        <div class="flex items-center gap-3">
          <div class="w-10 h-10 rounded-lg bg-emerald-500/20 flex items-center justify-center">
            <Wifi class="w-5 h-5 text-emerald-400" />
          </div>
          <div>
            <p class="text-sm text-slate-400">在线</p>
            <p class="text-xl font-bold text-white">{{ stats.onlineCount }}</p>
          </div>
        </div>
      </div>
      <div class="glass-card p-4">
        <div class="flex items-center gap-3">
          <div class="w-10 h-10 rounded-lg bg-red-500/20 flex items-center justify-center">
            <WifiOff class="w-5 h-5 text-red-400" />
          </div>
          <div>
            <p class="text-sm text-slate-400">离线</p>
            <p class="text-xl font-bold text-white">{{ stats.offlineCount }}</p>
          </div>
        </div>
      </div>
      <div class="glass-card p-4">
        <div class="flex items-center gap-3">
          <div class="w-10 h-10 rounded-lg bg-purple-500/20 flex items-center justify-center">
            <Activity class="w-5 h-5 text-purple-400" />
          </div>
          <div>
            <p class="text-sm text-slate-400">总调用次数</p>
            <p class="text-xl font-bold text-white">{{ stats.totalCalls }}</p>
          </div>
        </div>
      </div>
    </div>

    <!-- 搜索 -->
    <div class="flex gap-4">
      <div class="flex-1 relative">
        <Search class="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-500" />
        <input v-model="searchQuery" class="input-field pl-10" placeholder="搜索智能体名称或类型..." />
      </div>
    </div>

    <!-- 智能体卡片列表 -->
    <div v-if="loading" class="text-slate-500">加载中...</div>
    <div v-else-if="filteredAgents.length === 0" class="text-slate-500">暂无注册智能体</div>
    <div v-else class="grid grid-cols-1 lg:grid-cols-2 gap-4">
      <div v-for="agent in filteredAgents" :key="agent.id" class="glass-card p-6 glow-border">
        <div class="flex items-start justify-between">
          <div class="flex items-start gap-4">
            <!-- 图标 + 健康状态指示灯 -->
            <div class="relative">
              <div class="w-12 h-12 rounded-xl bg-gradient-to-br from-cyan-500/20 to-emerald-500/20 flex items-center justify-center">
                <Bot class="w-6 h-6 text-cyan-400" />
              </div>
              <div :class="['absolute -top-1 -right-1 w-3.5 h-3.5 rounded-full border-2 border-[#0a0f1c]', getHealthDotClass(agent.id)]"></div>
            </div>
            <div class="flex-1 min-w-0">
              <div class="flex items-center gap-3 flex-wrap">
                <h3 class="text-lg font-semibold text-white">{{ agent.name }}</h3>
                <span v-if="agent.agentType" class="px-2 py-0.5 text-xs rounded-full bg-cyan-500/10 text-cyan-400">
                  {{ agent.agentType }}
                </span>
                <span :class="['px-2 py-0.5 text-xs rounded-full border', agent.status === 0 ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20' : 'bg-slate-500/10 text-slate-400 border-slate-500/20']">
                  {{ agent.status === 0 ? '启用' : '禁用' }}
                </span>
                <span :class="['px-2 py-0.5 text-xs rounded-full', getHealthBadgeClass(agent.id)]">
                  {{ getHealthText(agent.id) }}
                </span>
              </div>
              <p class="text-sm text-slate-400 mt-1">{{ agent.description || '无描述' }}</p>

              <!-- 能力标签 -->
              <div v-if="getCapabilityTags(agent.id).length > 0" class="flex items-center gap-1.5 mt-2 flex-wrap">
                <span v-for="tag in getCapabilityTags(agent.id)" :key="tag.label" :class="['px-2 py-0.5 text-xs rounded-full', tag.color]">
                  {{ tag.label }}
                </span>
              </div>

              <!-- 工具列表 -->
              <div v-if="getToolList(agent.id).length > 0" class="mt-1.5">
                <span class="text-xs text-slate-500">工具: </span>
                <span v-for="(tool, i) in getToolList(agent.id).slice(0, 4)" :key="tool.name" class="text-xs text-slate-400">
                  {{ tool.name }}{{ i < Math.min(getToolList(agent.id).length, 4) - 1 ? ', ' : '' }}
                </span>
                <span v-if="getToolList(agent.id).length > 4" class="text-xs text-slate-500">+{{ getToolList(agent.id).length - 4 }}</span>
              </div>

              <!-- 接口信息 -->
              <div class="mt-2 space-y-1">
                <div class="flex items-center gap-2 text-xs">
                  <Heart class="w-3 h-3 text-rose-400" />
                  <span class="text-slate-500 truncate">{{ agent.healthEndpoint }}</span>
                </div>
                <div class="flex items-center gap-2 text-xs">
                  <MessageSquare class="w-3 h-3 text-cyan-400" />
                  <span class="text-slate-500 truncate">{{ agent.chatEndpoint }}</span>
                </div>
                <div v-if="agent.streamEndpoint" class="flex items-center gap-2 text-xs">
                  <Radio class="w-3 h-3 text-emerald-400" />
                  <span class="text-slate-500 truncate">{{ agent.streamEndpoint }}</span>
                </div>
                <div v-if="agent.documentEndpoint" class="flex items-center gap-2 text-xs">
                  <FileUp class="w-3 h-3 text-amber-400" />
                  <span class="text-slate-500 truncate">{{ agent.documentEndpoint }}</span>
                </div>
                <div v-if="agent.infoEndpoint" class="flex items-center gap-2 text-xs">
                  <Info class="w-3 h-3 text-purple-400" />
                  <span class="text-slate-500 truncate">{{ agent.infoEndpoint }}</span>
                </div>
                <div v-if="agent.historyEndpoint" class="flex items-center gap-2 text-xs">
                  <History class="w-3 h-3 text-indigo-400" />
                  <span class="text-slate-500 truncate">{{ agent.historyEndpoint }}</span>
                </div>
              </div>

              <!-- 统计和健康信息 -->
              <div class="flex items-center gap-4 mt-2 flex-wrap">
                <span class="text-xs text-slate-500">调用: {{ agent.totalCalls || 0 }}</span>
                <span class="text-xs text-slate-500">Token: {{ agent.totalTokens || 0 }}</span>
                <span v-if="agent.version || getAgentInfo(agent.id)?.version" class="text-xs text-slate-500">
                  v{{ getAgentInfo(agent.id)?.version || agent.version }}
                </span>
                <span v-if="getAgentInfo(agent.id)?.model" class="text-xs text-slate-500">
                  {{ getAgentInfo(agent.id).model }}
                </span>
              </div>

              <!-- 健康状态详情 -->
              <div v-if="getHealthInfo(agent.id)" class="flex items-center gap-4 mt-2 text-xs text-slate-500">
                <span v-if="getHealthInfo(agent.id).latencyMs != null" class="flex items-center gap-1">
                  <Zap class="w-3 h-3" />
                  {{ getHealthInfo(agent.id).latencyMs }}ms
                </span>
                <span v-if="getHealthInfo(agent.id).message">{{ getHealthInfo(agent.id).message }}</span>
              </div>

              <!-- 组件状态 -->
              <div v-if="getHealthInfo(agent.id)?.online && getHealthInfo(agent.id)?.components" class="mt-2 pt-2 border-t border-white/5">
                <div class="flex items-center gap-3 text-xs text-slate-500">
                  <span :class="getHealthInfo(agent.id).components.database?.status === 'connected' ? 'text-emerald-400' : 'text-red-400'">
                    DB: {{ getHealthInfo(agent.id).components.database?.status === 'connected' ? '已连接' : '异常' }}
                  </span>
                  <span :class="getHealthInfo(agent.id).components.knowledge?.status === 'connected' ? 'text-emerald-400' : 'text-red-400'">
                    知识库: {{ getHealthInfo(agent.id).components.knowledge?.status === 'connected' ? '已连接' : '异常' }}
                  </span>
                  <span :class="getHealthInfo(agent.id).components.llm?.status === 'available' ? 'text-emerald-400' : 'text-red-400'">
                    LLM: {{ getHealthInfo(agent.id).components.llm?.status === 'available' ? '可用' : '异常' }}
                  </span>
                </div>
              </div>
            </div>
          </div>
          <div class="flex items-center gap-2 shrink-0">
            <button @click="checkSingleHealth(agent)" class="p-2 rounded-lg bg-white/5 text-cyan-400 hover:bg-cyan-500/10" title="检测健康状态">
              <RefreshCw class="w-4 h-4" />
            </button>
            <button @click="toggleStatus(agent)" :class="['p-2 rounded-lg transition-colors', agent.status === 0 ? 'bg-emerald-500/10 text-emerald-400' : 'bg-slate-500/10 text-slate-400']">
              <Power class="w-4 h-4" />
            </button>
            <button @click="editAgent(agent)" class="p-2 rounded-lg bg-white/5 text-slate-300 hover:bg-white/10">
              <Settings2 class="w-4 h-4" />
            </button>
            <button @click="deleteAgent(agent.id)" class="p-2 rounded-lg bg-white/5 text-red-400 hover:bg-red-500/10">
              <Trash2 class="w-4 h-4" />
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- 注册/编辑弹窗 -->
    <div v-if="showAddModal" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm">
      <div class="glass-card w-full max-w-lg p-6 animate-slide-up max-h-[90vh] overflow-y-auto">
        <div class="flex items-center justify-between mb-4">
          <h2 class="text-xl font-bold text-white">{{ editingAgent ? '编辑智能体' : '注册智能体' }}</h2>
          <button @click="showAddModal = false" class="p-1 text-slate-400 hover:text-white">
            <X class="w-5 h-5" />
          </button>
        </div>
        <div class="space-y-4">
          <!-- 基本信息 -->
          <div class="pb-3 border-b border-white/5">
            <p class="text-xs text-slate-500 mb-3 uppercase tracking-wider">基本信息</p>
            <div class="space-y-3">
              <div>
                <label class="block text-sm text-slate-300 mb-1">智能体名称 *</label>
                <input v-model="newAgent.name" class="input-field" placeholder="如：客服智能体" />
              </div>
              <div>
                <label class="block text-sm text-slate-300 mb-1">描述</label>
                <textarea v-model="newAgent.description" class="input-field" rows="2" placeholder="智能体功能描述"></textarea>
              </div>
              <div class="grid grid-cols-2 gap-3">
                <div>
                  <label class="block text-sm text-slate-300 mb-1">类型标识</label>
                  <input v-model="newAgent.agentType" class="input-field" placeholder="customer_service" />
                </div>
                <div>
                  <label class="block text-sm text-slate-300 mb-1">版本号</label>
                  <input v-model="newAgent.version" class="input-field" placeholder="1.0.0" />
                </div>
              </div>
            </div>
          </div>

          <!-- 必填接口 -->
          <div class="pb-3 border-b border-white/5">
            <p class="text-xs text-slate-500 mb-3 uppercase tracking-wider">必填接口</p>
            <div class="space-y-3">
              <div>
                <label class="block text-sm text-slate-300 mb-1">健康检测接口 *</label>
                <input v-model="newAgent.healthEndpoint" class="input-field" placeholder="http://agent-host:3000/api/health" />
                <p class="text-xs text-slate-500 mt-1">GET，返回 { "status": "ok"|"degraded", "version", "components" }</p>
              </div>
              <div>
                <label class="block text-sm text-slate-300 mb-1">对话接口 *</label>
                <input v-model="newAgent.chatEndpoint" class="input-field" placeholder="http://agent-host:3000/api/chat" />
                <p class="text-xs text-slate-500 mt-1">POST，接收 { "message", "session_id", "merchant_id" }</p>
              </div>
            </div>
          </div>

          <!-- 可选接口 -->
          <div class="pb-3 border-b border-white/5">
            <p class="text-xs text-slate-500 mb-3 uppercase tracking-wider">可选接口（增强功能）</p>
            <div class="space-y-3">
              <div>
                <label class="block text-sm text-slate-300 mb-1">流式对话接口</label>
                <input v-model="newAgent.streamEndpoint" class="input-field" placeholder="http://agent-host:3000/api/chat/stream" />
                <p class="text-xs text-slate-500 mt-1">POST SSE，请求格式同对话接口，响应 text/event-stream</p>
              </div>
              <div>
                <label class="block text-sm text-slate-300 mb-1">智能体元信息接口</label>
                <input v-model="newAgent.infoEndpoint" class="input-field" placeholder="http://agent-host:3000/api/agent/info" />
                <p class="text-xs text-slate-500 mt-1">GET，返回 { "capabilities", "tools", "model" }</p>
              </div>
              <div>
                <label class="block text-sm text-slate-300 mb-1">会话历史接口</label>
                <input v-model="newAgent.historyEndpoint" class="input-field" placeholder="http://agent-host:3000/api/chat/history" />
                <p class="text-xs text-slate-500 mt-1">GET {url}/{session_id}，DELETE {url}/{session_id}</p>
              </div>
              <div>
                <label class="block text-sm text-slate-300 mb-1">文档上传接口</label>
                <input v-model="newAgent.documentEndpoint" class="input-field" placeholder="http://agent-host:3000/api/documents" />
                <p class="text-xs text-slate-500 mt-1">POST multipart/form-data，接收 file + collection_name</p>
              </div>
            </div>
          </div>

          <!-- 认证 -->
          <div>
            <p class="text-xs text-slate-500 mb-3 uppercase tracking-wider">认证配置</p>
            <div>
              <label class="block text-sm text-slate-300 mb-1">认证头（可选）</label>
              <input v-model="newAgent.authHeader" class="input-field" placeholder="Bearer sk-xxx" />
              <p class="text-xs text-slate-500 mt-1">平台调用所有接口时携带 Authorization 头</p>
            </div>
          </div>
        </div>
        <div class="flex gap-3 mt-6">
          <button @click="showAddModal = false" class="btn-secondary flex-1">取消</button>
          <button @click="saveAgent" class="btn-primary flex-1">{{ editingAgent ? '保存' : '注册' }}</button>
        </div>
      </div>
    </div>
  </div>
</template>
