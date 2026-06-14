<script setup>import { ref, onMounted } from 'vue';
import { useApiStore } from '@/stores/api';
import { Bot, Plus, Search, Trash2, Settings2, Power, X, ExternalLink, Cpu } from 'lucide-vue-next';
const api = useApiStore();
const loading = ref(false);
const showAddModal = ref(false);
const searchQuery = ref('');
const editingAgent = ref(null);
const agents = ref([]);
const models = ref([]);
const skills = ref([]);
const newAgent = ref({
 name: '',
 description: '',
 agentType: 'SKILL',
 modelId: '',
 skillId: '',
 endpoint: '',
 endpointAuthHeader: ''
});
onMounted(loadData);
async function loadData() {
 loading.value = true;
 const [agentRes, modelRes, skillRes] = await Promise.all([
 api.get('/platform/agents'),
 api.get('/models'),
 api.get('/platform/skills')
 ]);
 if (agentRes.code === 200)
 agents.value = agentRes.data || [];
 if (modelRes.code === 200)
 models.value = modelRes.data || [];
 if (skillRes.code === 200)
 skills.value = skillRes.data || [];
 loading.value = false;
}
async function toggleStatus(agent) {
 const res = await api.post(`/platform/agents/${agent.id}/toggle`);
 if (res.code === 200) {
 agent.status = res.data.status;
 }
 else {
 alert(res.message || '操作失败');
 }
}
async function deleteAgent(id) {
 if (!confirm('确定删除该智能体？'))
 return;
 const res = await api.del(`/platform/agents/${id}`);
 if (res.code === 200) {
 loadData();
 }
 else {
 alert(res.message || '删除失败');
 }
}
async function saveAgent() {
 if (!newAgent.value.name) {
 alert('请输入智能体名称');
 return;
 }
 const body = {
 name: newAgent.value.name,
 description: newAgent.value.description,
 agentType: newAgent.value.agentType
 };
 if (newAgent.value.agentType === 'MODEL') {
 if (!newAgent.value.modelId) {
 alert('请选择模型');
 return;
 }
 body.modelId = parseInt(newAgent.value.modelId);
 }
 else if (newAgent.value.agentType === 'SKILL') {
 if (!newAgent.value.skillId) {
 alert('请选择 Skill');
 return;
 }
 body.skillId = newAgent.value.skillId;
 }
 else if (newAgent.value.agentType === 'ENDPOINT') {
 if (!newAgent.value.endpoint) {
 alert('请输入 Endpoint 地址');
 return;
 }
 body.endpoint = newAgent.value.endpoint;
 body.endpointAuthHeader = newAgent.value.endpointAuthHeader;
 }
 if (editingAgent.value) {
 const res = await api.put(`/platform/agents/${editingAgent.value.id}`, body);
 if (res.code === 200) {
 showAddModal.value = false;
 editingAgent.value = null;
 resetForm();
 loadData();
 }
 else {
 alert(res.message || '更新失败');
 }
 }
 else {
 const res = await api.post('/platform/agents', body);
 if (res.code === 200) {
 showAddModal.value = false;
 resetForm();
 loadData();
 }
 else {
 alert(res.message || '创建失败');
 }
 }
}
function editAgent(agent) {
 editingAgent.value = { ...agent };
 newAgent.value = {
 name: agent.name,
 description: agent.description || '',
 agentType: agent.agentType || 'SKILL',
 modelId: agent.modelId || '',
 skillId: agent.skillId || '',
 endpoint: agent.endpoint || '',
 endpointAuthHeader: agent.endpointAuthHeader || ''
 };
 showAddModal.value = true;
}
function resetForm() {
 newAgent.value = {
 name: '',
 description: '',
 agentType: 'SKILL',
 modelId: '',
 skillId: '',
 endpoint: '',
 endpointAuthHeader: ''
 };
}
function getAgentTypeLabel(type) {
 const labels = {
 'MODEL': '模型模式',
 'SKILL': 'Skill 模式',
 'ENDPOINT': 'Endpoint 模式'
 };
 return labels[type] || type;
}
function getAgentTypeIcon(type) {
 const icons = {
 'MODEL': Cpu,
 'SKILL': Bot,
 'ENDPOINT': ExternalLink
 };
 return icons[type] || Bot;
}
function getAgentTypeColor(type) {
 const colors = {
 'MODEL': 'bg-cyan-500/10 text-cyan-400',
 'SKILL': 'bg-emerald-500/10 text-emerald-400',
 'ENDPOINT': 'bg-amber-500/10 text-amber-400'
 };
 return colors[type] || 'bg-slate-500/10 text-slate-400';
}
function getModelName(id) {
 return models.value.find(m => m.id === id)?.name || id;
}
function getSkillName(id) {
 return skills.value.find(s => s.id === id)?.name || id;
}
</script>

<template>
  <div class="space-y-6 animate-fade-in">
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-2xl font-bold text-white">智能体管理</h1>
        <p class="text-slate-400 mt-1">创建和管理平台智能体，支持模型模式、Skill 模式和 Endpoint 模式</p>
      </div>
      <button @click="showAddModal = true; editingAgent = null; resetForm()" class="btn-primary flex items-center gap-2">
        <Plus class="w-4 h-4" /> 创建智能体
      </button>
    </div>

    <div class="flex gap-4">
      <div class="flex-1 relative">
        <Search class="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-500" />
        <input v-model="searchQuery" class="input-field pl-10" placeholder="搜索智能体名称..." />
      </div>
    </div>

    <div v-if="loading" class="text-slate-500">加载中...</div>
    <div v-else-if="agents.length === 0" class="text-slate-500">暂无智能体</div>
    <div v-else class="grid grid-cols-1 lg:grid-cols-2 gap-4">
      <div v-for="agent in agents" :key="agent.id" class="glass-card p-6 glow-border">
        <div class="flex items-start justify-between">
          <div class="flex items-start gap-4">
            <div :class="['w-12 h-12 rounded-xl flex items-center justify-center', getAgentTypeColor(agent.agentType).replace('text-', 'bg-gradient-to-br from-').replace('/10', '/20 to-') + '500/20']">
              <component :is="getAgentTypeIcon(agent.agentType)" :class="['w-6 h-6', getAgentTypeColor(agent.agentType).split(' ')[1]]" />
            </div>
            <div>
              <div class="flex items-center gap-3">
                <h3 class="text-lg font-semibold text-white">{{ agent.name }}</h3>
                <span :class="['px-2 py-0.5 text-xs rounded-full', getAgentTypeColor(agent.agentType)]">
                  {{ getAgentTypeLabel(agent.agentType) }}
                </span>
                <span :class="['px-2 py-0.5 text-xs rounded-full border', agent.status === 'ACTIVE' ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20' : 'bg-slate-500/10 text-slate-400 border-slate-500/20']">
                  {{ agent.status === 'ACTIVE' ? '启用' : '禁用' }}
                </span>
              </div>
              <p class="text-sm text-slate-400 mt-1">{{ agent.description || '无描述' }}</p>
              <div class="flex items-center gap-4 mt-2">
                <span class="text-xs text-slate-500">调用次数: {{ agent.totalCalls || 0 }}</span>
                <span class="text-xs text-slate-500">Token: {{ agent.totalTokens || 0 }}</span>
                <span v-if="agent.modelId" class="text-xs text-cyan-400">模型: {{ getModelName(agent.modelId) }}</span>
                <span v-if="agent.skillId" class="text-xs text-emerald-400">Skill: {{ getSkillName(agent.skillId) }}</span>
                <span v-if="agent.endpoint" class="text-xs text-amber-400">Endpoint</span>
              </div>
            </div>
          </div>
          <div class="flex items-center gap-2">
            <button @click="toggleStatus(agent)" :class="['p-2 rounded-lg transition-colors', agent.status === 'ACTIVE' ? 'bg-emerald-500/10 text-emerald-400' : 'bg-slate-500/10 text-slate-400']">
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

    <div v-if="showAddModal" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm">
      <div class="glass-card w-full max-w-lg p-6 animate-slide-up">
        <div class="flex items-center justify-between mb-4">
          <h2 class="text-xl font-bold text-white">{{ editingAgent ? '编辑智能体' : '创建智能体' }}</h2>
          <button @click="showAddModal = false" class="p-1 text-slate-400 hover:text-white">
            <X class="w-5 h-5" />
          </button>
        </div>
        <div class="space-y-4">
          <div>
            <label class="block text-sm text-slate-300 mb-2">智能体名称 *</label>
            <input v-model="newAgent.name" class="input-field" placeholder="请输入智能体名称" />
          </div>
          <div>
            <label class="block text-sm text-slate-300 mb-2">描述</label>
            <textarea v-model="newAgent.description" class="input-field" rows="2" placeholder="智能体功能描述"></textarea>
          </div>
          <div>
            <label class="block text-sm text-slate-300 mb-2">智能体类型 *</label>
            <div class="grid grid-cols-3 gap-2">
              <button @click="newAgent.agentType = 'MODEL'" :class="['p-3 rounded-lg border flex flex-col items-center gap-1 transition-colors', newAgent.agentType === 'MODEL' ? 'bg-cyan-500/10 border-cyan-500/30 text-cyan-400' : 'bg-white/5 border-white/10 text-slate-400']">
                <Cpu class="w-5 h-5" />
                <span class="text-xs">Model</span>
              </button>
              <button @click="newAgent.agentType = 'SKILL'" :class="['p-3 rounded-lg border flex flex-col items-center gap-1 transition-colors', newAgent.agentType === 'SKILL' ? 'bg-emerald-500/10 border-emerald-500/30 text-emerald-400' : 'bg-white/5 border-white/10 text-slate-400']">
                <Bot class="w-5 h-5" />
                <span class="text-xs">Skill</span>
              </button>
              <button @click="newAgent.agentType = 'ENDPOINT'" :class="['p-3 rounded-lg border flex flex-col items-center gap-1 transition-colors', newAgent.agentType === 'ENDPOINT' ? 'bg-amber-500/10 border-amber-500/30 text-amber-400' : 'bg-white/5 border-white/10 text-slate-400']">
                <ExternalLink class="w-5 h-5" />
                <span class="text-xs">Endpoint</span>
              </button>
            </div>
          </div>
          
          <div v-if="newAgent.agentType === 'MODEL'">
            <label class="block text-sm text-slate-300 mb-2">选择模型 *</label>
            <select v-model="newAgent.modelId" class="input-field">
              <option value="">请选择模型</option>
              <option v-for="m in models" :key="m.id" :value="m.id">{{ m.name }}</option>
            </select>
          </div>
          
          <div v-if="newAgent.agentType === 'SKILL'">
            <label class="block text-sm text-slate-300 mb-2">选择 Skill *</label>
            <select v-model="newAgent.skillId" class="input-field">
              <option value="">请选择 Skill</option>
              <option v-for="s in skills" :key="s.id" :value="s.id">{{ s.name }} ({{ s.category }})</option>
            </select>
          </div>
          
          <div v-if="newAgent.agentType === 'ENDPOINT'">
            <div>
              <label class="block text-sm text-slate-300 mb-2">Endpoint 地址 *</label>
              <input v-model="newAgent.endpoint" class="input-field" placeholder="http://custom-agent:4000/api/chat" />
            </div>
            <div>
              <label class="block text-sm text-slate-300 mb-2">认证头（可选）</label>
              <input v-model="newAgent.endpointAuthHeader" class="input-field" placeholder="Bearer sk-xxx" />
            </div>
          </div>
        </div>
        <div class="flex gap-3 mt-6">
          <button @click="showAddModal = false" class="btn-secondary flex-1">取消</button>
          <button @click="saveAgent" class="btn-primary flex-1">{{ editingAgent ? '保存' : '创建' }}</button>
        </div>
      </div>
    </div>
  </div>
</template>