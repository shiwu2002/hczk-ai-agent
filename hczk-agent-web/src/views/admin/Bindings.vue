<script setup>import { ref, onMounted } from 'vue';
import { useApiStore } from '@/stores/api';
import { Link, Plus, Search, Trash2, Power, X, Bot, ExternalLink, Cpu } from 'lucide-vue-next';
const api = useApiStore();
const loading = ref(false);
const showAddModal = ref(false);
const searchQuery = ref('');
const users = ref([]);
const skills = ref([]);
const agents = ref([]);
const bindings = ref([]);
const newBinding = ref({
 userId: '',
 bindingType: 'agent',
 agentId: '',
 skillId: '',
 agentEndpoint: '',
 agentAuthHeader: '',
 personaOverride: '',
 capabilitiesOverride: '',
 configOverride: '',
 enabled: true
});
onMounted(loadData);
async function loadData() {
 loading.value = true;
 const [userRes, skillRes, agentRes, bindingRes] = await Promise.all([
 api.get('/users'),
 api.get('/platform/skills'),
 api.get('/platform/agents'),
 api.get('/platform/bindings')
 ]);
 if (userRes.code === 200)
 users.value = userRes.data || [];
 if (skillRes.code === 200)
 skills.value = skillRes.data || [];
 if (agentRes.code === 200)
 agents.value = agentRes.data || [];
 if (bindingRes.code === 200)
 bindings.value = bindingRes.data || [];
 loading.value = false;
}
async function toggleBinding(binding) {
 const res = await api.patch(`/platform/bindings/${binding.userId}?enabled=${!binding.enabled}`);
 if (res.code === 200) {
 binding.enabled = !binding.enabled;
 }
 else {
 alert(res.message || '操作失败');
 }
}
async function deleteBinding(userId) {
 if (!confirm('确定解绑该用户的智能体？'))
 return;
 const res = await api.del(`/platform/bindings/${userId}`);
 if (res.code === 200) {
 loadData();
 }
 else {
 alert(res.message || '解绑失败');
 }
}
async function saveBinding() {
 if (!newBinding.value.userId) {
 alert('请选择用户');
 return;
 }
 const body = {
 userId: parseInt(newBinding.value.userId),
 enabled: newBinding.value.enabled
 };
 if (newBinding.value.bindingType === 'agent') {
 if (!newBinding.value.agentId) {
 alert('请选择智能体');
 return;
 }
 body.agentId = parseInt(newBinding.value.agentId);
 }
 else if (newBinding.value.bindingType === 'skill') {
 if (!newBinding.value.skillId) {
 alert('请选择 Skill');
 return;
 }
 body.skillId = newBinding.value.skillId;
 if (newBinding.value.personaOverride) {
 body.personaOverride = JSON.parse(newBinding.value.personaOverride);
 }
 if (newBinding.value.capabilitiesOverride) {
 body.capabilitiesOverride = JSON.parse(newBinding.value.capabilitiesOverride);
 }
 }
 else {
 if (!newBinding.value.agentEndpoint) {
 alert('请输入智能体地址');
 return;
 }
 body.agentEndpoint = newBinding.value.agentEndpoint;
 body.agentAuthHeader = newBinding.value.agentAuthHeader;
 }
 const res = await api.post('/platform/bindings', body);
 if (res.code === 200) {
 showAddModal.value = false;
 resetForm();
 loadData();
 }
 else {
 alert(res.message || '绑定失败');
 }
}
function resetForm() {
 newBinding.value = {
 userId: '',
 bindingType: 'agent',
 agentId: '',
 skillId: '',
 agentEndpoint: '',
 agentAuthHeader: '',
 personaOverride: '',
 capabilitiesOverride: '',
 configOverride: '',
 enabled: true
 };
}
function getUserName(id) {
 return users.value.find(u => u.id === id)?.username || id;
}
function getSkillName(id) {
 return skills.value.find(s => s.id === id)?.name || id;
}
function getAgentName(id) {
 return agents.value.find(a => a.id === id)?.name || id;
}
function getAgentType(type) {
  return type || '自定义';
}
</script>

<template>
  <div class="space-y-6 animate-fade-in">
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-2xl font-bold text-white">用户智能体绑定</h1>
        <p class="text-slate-400 mt-1">管理用户与智能体的绑定关系，支持平台智能体、Skill 和 Endpoint 三种模式</p>
      </div>
      <button @click="showAddModal = true; resetForm()" class="btn-primary flex items-center gap-2">
        <Plus class="w-4 h-4" /> 新增绑定
      </button>
    </div>

    <div class="flex gap-4">
      <div class="flex-1 relative">
        <Search class="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-500" />
        <input v-model="searchQuery" class="input-field pl-10" placeholder="搜索用户名..." />
      </div>
    </div>

    <div v-if="loading" class="text-slate-500">加载中...</div>
    <div v-else-if="bindings.length === 0" class="text-slate-500">暂无绑定</div>
    <div v-else class="grid grid-cols-1 lg:grid-cols-2 gap-4">
      <div v-for="binding in bindings" :key="binding.userId" class="glass-card p-6 glow-border">
        <div class="flex items-start justify-between">
          <div class="flex items-start gap-4">
            <div :class="['w-12 h-12 rounded-xl flex items-center justify-center', binding.agentId ? 'bg-gradient-to-br from-cyan-500/20 to-blue-500/20' : binding.skillId ? 'bg-gradient-to-br from-emerald-500/20 to-cyan-500/20' : 'bg-gradient-to-br from-amber-500/20 to-orange-500/20']">
              <component :is="binding.agentId ? Bot : binding.skillId ? Cpu : ExternalLink" :class="['w-6 h-6', binding.agentId ? 'text-cyan-400' : binding.skillId ? 'text-emerald-400' : 'text-amber-400']" />
            </div>
            <div>
              <div class="flex items-center gap-3">
                <h3 class="text-lg font-semibold text-white">{{ getUserName(binding.userId) }}</h3>
                <span :class="['px-2 py-0.5 text-xs rounded-full border', binding.enabled ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20' : 'bg-slate-500/10 text-slate-400 border-slate-500/20']">
                  {{ binding.enabled ? '已启用' : '已禁用' }}
                </span>
                <span :class="['px-2 py-0.5 text-xs rounded-full', binding.agentId ? 'bg-cyan-500/10 text-cyan-400' : binding.skillId ? 'bg-emerald-500/10 text-emerald-400' : 'bg-amber-500/10 text-amber-400']">
                  {{ binding.agentId ? '平台智能体' : binding.skillId ? 'Skill 模式' : 'Endpoint 模式' }}
                </span>
              </div>
              <p class="text-sm text-slate-400 mt-1">
                <template v-if="binding.agentId">
                  智能体: {{ getAgentName(binding.agentId) }}
                  <span v-if="getAgentType(binding.agentType)" class="ml-2 text-xs text-cyan-400">({{ getAgentType(binding.agentType) }})</span>
                </template>
                <template v-else-if="binding.skillId">Skill: {{ getSkillName(binding.skillId) }}</template>
                <template v-else>Endpoint: {{ binding.agentEndpoint }}</template>
              </p>
            </div>
          </div>
          <div class="flex items-center gap-2">
            <button @click="toggleBinding(binding)" :class="['p-2 rounded-lg transition-colors', binding.enabled ? 'bg-emerald-500/10 text-emerald-400' : 'bg-slate-500/10 text-slate-400']">
              <Power class="w-4 h-4" />
            </button>
            <button @click="deleteBinding(binding.userId)" class="p-2 rounded-lg bg-white/5 text-red-400 hover:bg-red-500/10">
              <Trash2 class="w-4 h-4" />
            </button>
          </div>
        </div>
      </div>
    </div>

    <div v-if="showAddModal" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm">
      <div class="glass-card w-full max-w-lg p-6 animate-slide-up">
        <div class="flex items-center justify-between mb-4">
          <h2 class="text-xl font-bold text-white">新增智能体绑定</h2>
          <button @click="showAddModal = false" class="p-1 text-slate-400 hover:text-white">
            <X class="w-5 h-5" />
          </button>
        </div>
        <div class="space-y-4">
          <div>
            <label class="block text-sm text-slate-300 mb-2">选择用户 *</label>
            <select v-model="newBinding.userId" class="input-field">
              <option value="">请选择用户</option>
              <option v-for="u in users" :key="u.id" :value="u.id">{{ u.username }}</option>
            </select>
          </div>
          <div>
            <label class="block text-sm text-slate-300 mb-2">绑定模式</label>
            <div class="flex gap-3">
              <label class="flex items-center gap-2 cursor-pointer">
                <input v-model="newBinding.bindingType" type="radio" value="agent" class="accent-cyan-500" />
                <div class="flex items-center gap-2">
                  <Bot class="w-4 h-4 text-cyan-400" />
                  <span class="text-sm text-slate-300">平台智能体</span>
                </div>
              </label>
              <label class="flex items-center gap-2 cursor-pointer">
                <input v-model="newBinding.bindingType" type="radio" value="skill" class="accent-emerald-500" />
                <div class="flex items-center gap-2">
                  <Cpu class="w-4 h-4 text-emerald-400" />
                  <span class="text-sm text-slate-300">Skill 模式</span>
                </div>
              </label>
              <label class="flex items-center gap-2 cursor-pointer">
                <input v-model="newBinding.bindingType" type="radio" value="endpoint" class="accent-amber-500" />
                <div class="flex items-center gap-2">
                  <ExternalLink class="w-4 h-4 text-amber-400" />
                  <span class="text-sm text-slate-300">Endpoint 模式</span>
                </div>
              </label>
            </div>
          </div>
          <div v-if="newBinding.bindingType === 'agent'">
            <label class="block text-sm text-slate-300 mb-2">选择平台智能体 *</label>
            <select v-model="newBinding.agentId" class="input-field">
              <option value="">请选择智能体</option>
              <option v-for="a in agents" :key="a.id" :value="a.id">{{ a.name }} ({{ getAgentType(a.agentType) }})</option>
            </select>
          </div>
          <div v-else-if="newBinding.bindingType === 'skill'">
            <label class="block text-sm text-slate-300 mb-2">选择 Skill *</label>
            <select v-model="newBinding.skillId" class="input-field">
              <option value="">请选择 Skill</option>
              <option v-for="s in skills" :key="s.id" :value="s.id">{{ s.name }} ({{ s.category }})</option>
            </select>
            <div class="mt-3">
              <label class="block text-sm text-slate-300 mb-2">Persona Override（JSON，可选）</label>
              <textarea v-model="newBinding.personaOverride" class="input-field" rows="2" placeholder='{"name": "XX客服"}'></textarea>
            </div>
            <div class="mt-3">
              <label class="block text-sm text-slate-300 mb-2">Capabilities Override（JSON，可选）</label>
              <textarea v-model="newBinding.capabilitiesOverride" class="input-field" rows="2" placeholder='{"collectionName": "merchant_001_products"}'></textarea>
            </div>
          </div>
          <div v-else>
            <div>
              <label class="block text-sm text-slate-300 mb-2">智能体地址 *</label>
              <input v-model="newBinding.agentEndpoint" class="input-field" placeholder="http://custom-agent:4000/api/chat" />
            </div>
            <div>
              <label class="block text-sm text-slate-300 mb-2">认证头（可选）</label>
              <input v-model="newBinding.agentAuthHeader" class="input-field" placeholder="Bearer sk-xxx" />
            </div>
          </div>
          <div class="flex items-center gap-2">
            <input id="enabled" v-model="newBinding.enabled" type="checkbox" class="w-4 h-4 rounded border-slate-600 bg-slate-800 text-emerald-500" />
            <label for="enabled" class="text-sm text-slate-300">启用绑定</label>
          </div>
        </div>
        <div class="flex gap-3 mt-6">
          <button @click="showAddModal = false" class="btn-secondary flex-1">取消</button>
          <button @click="saveBinding" class="btn-primary flex-1">绑定</button>
        </div>
      </div>
    </div>
  </div>
</template>