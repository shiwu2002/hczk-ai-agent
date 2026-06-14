<script setup>import { ref, onMounted } from 'vue';
import { useApiStore } from '@/stores/api';
import { Plus, Search, Trash2, Settings2, Power, X, Server } from 'lucide-vue-next';
const api = useApiStore();
const loading = ref(false);
const showAddModal = ref(false);
const searchQuery = ref('');
const editingSkill = ref(null);
const skills = ref([]);
const newSkill = ref({
 id: '',
 name: '',
 category: '',
 version: '1.0',
 persona: '',
 capabilities: '',
 workflow: '',
 config: '',
 status: 'active'
});
onMounted(loadData);
async function loadData() {
 loading.value = true;
 const res = await api.get('/platform/skills');
 if (res.code === 200)
 skills.value = res.data || [];
 loading.value = false;
}
async function toggleStatus(skill) {
 const res = await api.post(`/platform/skills/${skill.id}/toggle`);
 if (res.code === 200) {
 skill.status = res.data.status;
 }
 else {
 alert(res.message || '操作失败');
 }
}
async function deleteSkill(id) {
 if (!confirm('确定删除该 Skill？'))
 return;
 const res = await api.del(`/platform/skills/${id}`);
 if (res.code === 200) {
 loadData();
 }
 else {
 alert(res.message || '删除失败');
 }
}
async function saveSkill() {
 if (!newSkill.value.id) {
 alert('请输入 Skill ID');
 return;
 }
 if (!newSkill.value.name) {
 alert('请输入 Skill 名称');
 return;
 }
 if (!newSkill.value.category) {
 alert('请选择分类');
 return;
 }
 const body = {
 id: newSkill.value.id,
 name: newSkill.value.name,
 category: newSkill.value.category,
 version: newSkill.value.version,
 status: newSkill.value.status
 };
 if (newSkill.value.persona) {
 try {
 body.persona = JSON.parse(newSkill.value.persona);
 }
 catch (e) {
 alert('Persona 不是有效的 JSON');
 return;
 }
 }
 if (newSkill.value.capabilities) {
 try {
 body.capabilities = JSON.parse(newSkill.value.capabilities);
 }
 catch (e) {
 alert('Capabilities 不是有效的 JSON');
 return;
 }
 }
 if (newSkill.value.workflow) {
 try {
 body.workflow = JSON.parse(newSkill.value.workflow);
 }
 catch (e) {
 alert('Workflow 不是有效的 JSON');
 return;
 }
 }
 if (newSkill.value.config) {
 try {
 body.config = JSON.parse(newSkill.value.config);
 }
 catch (e) {
 alert('Config 不是有效的 JSON');
 return;
 }
 }
 if (editingSkill.value) {
 const res = await api.put(`/platform/skills/${editingSkill.value.id}`, body);
 if (res.code === 200) {
 showAddModal.value = false;
 editingSkill.value = null;
 resetForm();
 loadData();
 }
 else {
 alert(res.message || '更新失败');
 }
 }
 else {
 const res = await api.post('/platform/skills', body);
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
function editSkill(skill) {
 editingSkill.value = { ...skill };
 newSkill.value = {
 id: skill.id,
 name: skill.name,
 category: skill.category,
 version: skill.version || '1.0',
 persona: skill.persona ? JSON.stringify(skill.persona, null, 2) : '',
 capabilities: skill.capabilities ? JSON.stringify(skill.capabilities, null, 2) : '',
 workflow: skill.workflow ? JSON.stringify(skill.workflow, null, 2) : '',
 config: skill.config ? JSON.stringify(skill.config, null, 2) : '',
 status: skill.status || 'active'
 };
 showAddModal.value = true;
}
function resetForm() {
 newSkill.value = {
 id: '',
 name: '',
 category: '',
 version: '1.0',
 persona: '',
 capabilities: '',
 workflow: '',
 config: '',
 status: 'active'
 };
}
const categories = ['customer_service', 'marketing', 'sales', 'support', 'finance', 'other'];
function getCategoryLabel(category) {
 const labels = {
 'customer_service': '客服',
 'marketing': '营销',
 'sales': '销售',
 'support': '技术支持',
 'finance': '财务',
 'other': '其他'
 };
 return labels[category] || category;
}
</script>

<template>
  <div class="space-y-6 animate-fade-in">
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-2xl font-bold text-white">Skill 管理</h1>
        <p class="text-slate-400 mt-1">创建和管理平台 Skill 配置包，用于定制智能体行为</p>
      </div>
      <button @click="showAddModal = true; editingSkill = null; resetForm()" class="btn-primary flex items-center gap-2">
        <Plus class="w-4 h-4" /> 创建 Skill
      </button>
    </div>

    <div class="flex gap-4">
      <div class="flex-1 relative">
        <Search class="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-500" />
        <input v-model="searchQuery" class="input-field pl-10" placeholder="搜索 Skill 名称..." />
      </div>
    </div>

    <div v-if="loading" class="text-slate-500">加载中...</div>
    <div v-else-if="skills.length === 0" class="text-slate-500">暂无 Skill</div>
    <div v-else class="grid grid-cols-1 lg:grid-cols-2 gap-4">
      <div v-for="skill in skills" :key="skill.id" class="glass-card p-6 glow-border">
        <div class="flex items-start justify-between">
          <div class="flex items-start gap-4">
            <div class="w-12 h-12 rounded-xl bg-gradient-to-br from-purple-500/20 to-pink-500/20 flex items-center justify-center">
              <Server class="w-6 h-6 text-purple-400" />
            </div>
            <div>
              <div class="flex items-center gap-3">
                <h3 class="text-lg font-semibold text-white">{{ skill.name }}</h3>
                <span :class="['px-2 py-0.5 text-xs rounded-full', skill.status === 'active' ? 'bg-emerald-500/10 text-emerald-400' : 'bg-slate-500/10 text-slate-400']">
                  {{ skill.status === 'active' ? '活跃' : '停用' }}
                </span>
              </div>
              <div class="flex items-center gap-4 mt-1">
                <span class="text-sm text-slate-400">ID: {{ skill.id }}</span>
                <span class="text-sm text-purple-400">{{ getCategoryLabel(skill.category) }}</span>
                <span class="text-sm text-slate-500">v{{ skill.version }}</span>
              </div>
              <div class="mt-2">
                <div v-if="skill.persona" class="text-xs text-slate-500">
                  Persona: {{ typeof skill.persona === 'object' ? '配置中' : skill.persona }}
                </div>
                <div v-if="skill.capabilities" class="text-xs text-slate-500 mt-1">
                  Capabilities: {{ skill.capabilities.tools?.length || skill.capabilities?.length || '已配置' }} 项
                </div>
              </div>
            </div>
          </div>
          <div class="flex items-center gap-2">
            <button @click="toggleStatus(skill)" :class="['p-2 rounded-lg transition-colors', skill.status === 'active' ? 'bg-emerald-500/10 text-emerald-400' : 'bg-slate-500/10 text-slate-400']">
              <Power class="w-4 h-4" />
            </button>
            <button @click="editSkill(skill)" class="p-2 rounded-lg bg-white/5 text-slate-300 hover:bg-white/10">
              <Settings2 class="w-4 h-4" />
            </button>
            <button @click="deleteSkill(skill.id)" class="p-2 rounded-lg bg-white/5 text-red-400 hover:bg-red-500/10">
              <Trash2 class="w-4 h-4" />
            </button>
          </div>
        </div>
      </div>
    </div>

    <div v-if="showAddModal" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm">
      <div class="glass-card w-full max-w-2xl p-6 animate-slide-up">
        <div class="flex items-center justify-between mb-4">
          <h2 class="text-xl font-bold text-white">{{ editingSkill ? '编辑 Skill' : '创建 Skill' }}</h2>
          <button @click="showAddModal = false" class="p-1 text-slate-400 hover:text-white">
            <X class="w-5 h-5" />
          </button>
        </div>
        <div class="space-y-4">
          <div class="grid grid-cols-2 gap-4">
            <div>
              <label class="block text-sm text-slate-300 mb-2">Skill ID *</label>
              <input v-model="newSkill.id" :disabled="!!editingSkill" class="input-field" placeholder="如: sk_customer_001" />
            </div>
            <div>
              <label class="block text-sm text-slate-300 mb-2">版本</label>
              <input v-model="newSkill.version" class="input-field" placeholder="1.0" />
            </div>
          </div>
          <div>
            <label class="block text-sm text-slate-300 mb-2">Skill 名称 *</label>
            <input v-model="newSkill.name" class="input-field" placeholder="如: 客服助手" />
          </div>
          <div>
            <label class="block text-sm text-slate-300 mb-2">分类 *</label>
            <select v-model="newSkill.category" class="input-field">
              <option value="">请选择分类</option>
              <option v-for="cat in categories" :key="cat" :value="cat">{{ getCategoryLabel(cat) }}</option>
            </select>
          </div>
          <div>
            <label class="block text-sm text-slate-300 mb-2">Persona（JSON）</label>
            <textarea v-model="newSkill.persona" class="input-field" rows="3" placeholder='{"name": "客服助手", "description": "专业的客服人员"}'></textarea>
          </div>
          <div>
            <label class="block text-sm text-slate-300 mb-2">Capabilities（JSON）</label>
            <textarea v-model="newSkill.capabilities" class="input-field" rows="4" placeholder='{"tools": ["search", "calculator"], "collectionName": "products"}'></textarea>
          </div>
          <div>
            <label class="block text-sm text-slate-300 mb-2">Workflow（JSON）</label>
            <textarea v-model="newSkill.workflow" class="input-field" rows="3" placeholder='{"steps": ["step1", "step2"]}'></textarea>
          </div>
          <div>
            <label class="block text-sm text-slate-300 mb-2">Config（JSON）</label>
            <textarea v-model="newSkill.config" class="input-field" rows="3" placeholder='{"maxTokens": 4096, "temperature": 0.7}'></textarea>
          </div>
        </div>
        <div class="flex gap-3 mt-6">
          <button @click="showAddModal = false" class="btn-secondary flex-1">取消</button>
          <button @click="saveSkill" class="btn-primary flex-1">{{ editingSkill ? '保存' : '创建' }}</button>
        </div>
      </div>
    </div>
  </div>
</template>