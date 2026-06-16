<script setup>import { ref, onMounted, computed } from 'vue';
import { useApiStore } from '@/stores/api';
import { Plus, Search, Trash2, Settings2, Power, X, Wrench, Lock, Unlock, ChevronDown, ChevronRight, Database, Globe } from 'lucide-vue-next';

const api = useApiStore();
const loading = ref(false);
const showSkillModal = ref(false);
const showToolModal = ref(false);
const searchQuery = ref('');
const editingSkill = ref(null);
const editingTool = ref(null);
const currentSkillId = ref(null); // tool 创建时所属的工具组
const expandedSkills = ref({});
const skills = ref([]);

const newSkill = ref({ name: '', displayName: '', category: 'custom', icon: 'Wrench', version: '1.0.0', description: '', status: 'active' });
const newTool = ref({ skillId: '', name: '', displayName: '', description: '', inputSchema: '{"type":"object","properties":{},"required":[]}', endpoint: '', type: 'builtin', status: 'active' });

onMounted(loadData);

async function loadData() {
  loading.value = true;
  const res = await api.get('/platform/skills');
  if (res.code === 200) skills.value = res.data || [];
  loading.value = false;
}

async function toggleExpand(skill) {
  const wasExpanded = expandedSkills.value[skill.id];
  expandedSkills.value[skill.id] = !wasExpanded;
  // 展开时按需加载工具列表（首次加载后由缓存命中）
  if (!wasExpanded) {
    if (!skill.tools || skill.tools.length === 0) {
      const res = await api.get(`/platform/skills/${skill.id}/tools`);
      if (res.code === 200) {
        skill.tools = res.data || [];
        skill.toolCount = skill.tools.length;
      }
    }
  }
}

// ===== 工具组 CRUD =====
async function saveSkill() {
  if (!newSkill.value.name) { alert('请输入名称'); return; }
  const body = { ...newSkill.value };
  let res;
  if (editingSkill.value) {
    res = await api.put(`/platform/skills/${editingSkill.value.id}`, body);
  } else {
    res = await api.post('/platform/skills', body);
  }
  if (res.code === 200) { closeSkillModal(); loadData(); }
  else alert(res.message || '失败');
}
async function deleteSkill(id) {
  if (!confirm('删除工具组将同时删除其下所有工具，确定？')) return;
  const res = await api.del(`/platform/skills/${id}`);
  if (res.code === 200) loadData();
  else alert(res.message || '失败');
}
async function toggleSkill(skill) {
  const res = await api.post(`/platform/skills/${skill.id}/toggle`);
  if (res.code === 200) skill.status = res.data.status;
}
function editSkill(skill) {
  editingSkill.value = skill;
  newSkill.value = { name: skill.name, displayName: skill.displayName, category: skill.category, icon: skill.icon || 'Wrench', version: skill.version, description: skill.description || '', status: skill.status };
  showSkillModal.value = true;
}
function closeSkillModal() { showSkillModal.value = false; editingSkill.value = null; newSkill.value = { name: '', displayName: '', category: 'custom', icon: 'Wrench', version: '1.0.0', description: '', status: 'active' }; }

// ===== 工具 CRUD =====
function openCreateTool(skillId) {
  currentSkillId.value = skillId;
  editingTool.value = null;
  newTool.value = { skillId, name: '', displayName: '', description: '', inputSchema: '{"type":"object","properties":{},"required":[]}', endpoint: '', type: 'builtin', status: 'active' };
  showToolModal.value = true;
}
function editTool(tool, skillId) {
  currentSkillId.value = skillId;
  editingTool.value = tool;
  newTool.value = {
    skillId, name: tool.name, displayName: tool.displayName,
    description: tool.description || '',
    inputSchema: tool.inputSchema ? (typeof tool.inputSchema === 'string' ? tool.inputSchema : JSON.stringify(tool.inputSchema, null, 2)) : '{}',
    endpoint: tool.endpoint || '', type: tool.type || 'builtin', status: tool.status
  };
  showToolModal.value = true;
}
async function saveTool() {
  if (!newTool.value.name || !newTool.value.displayName || !newTool.value.description) { alert('请填写完整'); return; }
  try { JSON.parse(newTool.value.inputSchema); } catch (e) { alert('Schema 不是有效 JSON'); return; }
  const body = { ...newTool.value };
  let res;
  if (editingTool.value) {
    res = await api.put(`/platform/tools/${editingTool.value.id}`, body);
  } else {
    res = await api.post('/platform/tools', body);
  }
  if (res.code === 200) { closeToolModal(); loadData(); }
  else alert(res.message || '失败');
}
async function deleteTool(toolId) {
  if (!confirm('删除该工具？')) return;
  const res = await api.del(`/platform/tools/${toolId}`);
  if (res.code === 200) loadData();
  else alert(res.message || '失败');
}
async function toggleTool(toolId) {
  const res = await api.post(`/platform/tools/${toolId}/toggle`);
  if (res.code === 200) loadData();
}
function closeToolModal() { showToolModal.value = false; editingTool.value = null; }

const categoryLabels = { knowledge: '知识库', utility: '工具', custom: '自定义' };
const categories = ['knowledge', 'utility', 'custom'];
const iconOptions = ['Database', 'Wrench', 'Globe', 'Server', 'MessageSquare', 'FileText', 'Search'];

function formatSchemaParams(schema) {
  try {
    const obj = typeof schema === 'string' ? JSON.parse(schema) : schema;
    if (obj && obj.properties) return Object.keys(obj.properties).join(', ');
    return '—';
  } catch { return '—'; }
}
</script>

<template>
  <div class="space-y-6 animate-fade-in">
    <!-- 头部 -->
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-2xl font-bold text-white">工具管理（MCP Tools）</h1>
        <p class="text-slate-400 mt-1">管理工具组和工具定义，这些工具会自动传递给智能体进行 function calling</p>
      </div>
      <button @click="closeSkillModal(); showSkillModal = true" class="btn-primary flex items-center gap-2">
        <Plus class="w-4 h-4" /> 创建工具组
      </button>
    </div>

    <!-- 搜索 -->
    <div class="relative">
      <Search class="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-500" />
      <input v-model="searchQuery" class="input-field pl-10" placeholder="搜索工具组或工具名称..." />
    </div>

    <!-- 工具组列表（渐进披露） -->
    <div v-if="loading" class="text-slate-500 text-center py-12">加载中...</div>
    <div v-else-if="skills.length === 0" class="text-slate-500 text-center py-12">暂无工具组</div>
    <div v-else class="space-y-3">
      <div v-for="skill in skills" :key="skill.id" class="glass-card overflow-hidden">
        <!-- 工具组头部 -->
        <div class="flex items-center gap-4 p-4 cursor-pointer hover:bg-white/[0.02] transition-colors"
             @click="toggleExpand(skill)">
          <button class="text-slate-400 flex-shrink-0">
            <ChevronRight v-if="!expandedSkills[skill.id]" class="w-5 h-5 transition-transform" />
            <ChevronDown v-else class="w-5 h-5 transition-transform" />
          </button>
          <component :is="skill.icon === 'Database' ? Database : skill.icon === 'Globe' ? Globe : Wrench"
            class="w-6 h-6 text-purple-400 flex-shrink-0" />
          <div class="flex-1 min-w-0">
            <div class="flex items-center gap-2">
              <h3 class="text-lg font-semibold text-white">{{ skill.displayName || skill.name }}</h3>
              <span class="px-2 py-0.5 text-xs rounded-full bg-slate-500/10 text-slate-400">{{ categoryLabels[skill.category] || skill.category }}</span>
              <span class="px-2 py-0.5 text-xs rounded-full" :class="skill.status === 'active' ? 'bg-emerald-500/10 text-emerald-400' : 'bg-slate-500/10 text-slate-400'">
                {{ skill.status === 'active' ? '启用' : '停用' }}
              </span>
              <span class="text-xs text-slate-600">v{{ skill.version }}</span>
            </div>
            <p class="text-sm text-slate-500 mt-0.5">{{ skill.description }}</p>
          </div>
          <span class="text-sm text-slate-500 flex-shrink-0">{{ skill.toolCount || 0 }} 个工具</span>
          <div class="flex items-center gap-1 flex-shrink-0" @click.stop>
            <button @click="toggleSkill(skill)" class="p-1.5 rounded hover:bg-white/5 text-slate-400"><Power class="w-3.5 h-3.5" /></button>
            <button @click="editSkill(skill)" class="p-1.5 rounded hover:bg-white/5 text-slate-400"><Settings2 class="w-3.5 h-3.5" /></button>
            <button @click="deleteSkill(skill.id)" class="p-1.5 rounded hover:bg-white/5 text-red-400"><Trash2 class="w-3.5 h-3.5" /></button>
          </div>
        </div>

        <!-- 工具列表（展开时） -->
        <div v-if="expandedSkills[skill.id]" class="border-t border-white/5">
          <div class="p-2 px-4 text-xs text-slate-500 flex items-center justify-between">
            <span>工具列表</span>
            <button @click="openCreateTool(skill.id)" class="text-purple-400 hover:text-purple-300 flex items-center gap-1">
              <Plus class="w-3 h-3" /> 添加工具
            </button>
          </div>
          <div v-if="!skill.tools || skill.tools.length === 0" class="px-4 pb-3 text-xs text-slate-600">暂无工具</div>
          <div v-else class="px-4 pb-3 space-y-2">
            <div v-for="tool in skill.tools" :key="tool.id"
              :class="['flex items-center gap-3 p-3 rounded-lg transition-colors', tool.status !== 'active' ? 'opacity-50' : '', tool.type === 'builtin' ? 'bg-amber-500/[0.03] border border-amber-500/10' : 'bg-slate-500/[0.03] border border-slate-500/10']">
              <div class="flex-1 min-w-0">
                <div class="flex items-center gap-2">
                  <code class="text-purple-400 text-xs font-mono bg-purple-500/5 px-1 py-0.5 rounded">{{ tool.name }}</code>
                  <span class="text-sm text-white">{{ tool.displayName }}</span>
                  <span v-if="tool.type === 'builtin'" class="flex items-center gap-0.5 px-1.5 py-0.5 text-xs rounded bg-amber-500/10 text-amber-400"><Lock class="w-2.5 h-2.5" />内置</span>
                  <span v-else class="flex items-center gap-0.5 px-1.5 py-0.5 text-xs rounded bg-blue-500/10 text-blue-400"><Unlock class="w-2.5 h-2.5" />自定义</span>
                </div>
                <p class="text-xs text-slate-500 mt-0.5 line-clamp-1">{{ tool.description }}</p>
                <div class="text-xs text-slate-600 mt-0.5">参数：<span class="font-mono">{{ formatSchemaParams(tool.inputSchema) }}</span></div>
              </div>
              <div class="flex items-center gap-1 flex-shrink-0">
                <button @click="toggleTool(tool.id)" class="p-1 rounded hover:bg-white/5 text-slate-500"><Power class="w-3 h-3" /></button>
                <button @click="editTool(tool, skill.id)" class="p-1 rounded hover:bg-white/5 text-slate-500"><Settings2 class="w-3 h-3" /></button>
                <button v-if="tool.type !== 'builtin'" @click="deleteTool(tool.id)" class="p-1 rounded hover:bg-white/5 text-red-400"><Trash2 class="w-3 h-3" /></button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 工具组弹窗 -->
    <div v-if="showSkillModal" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm">
      <div class="glass-card w-full max-w-lg p-6 animate-slide-up">
        <div class="flex items-center justify-between mb-4">
          <h2 class="text-xl font-bold text-white">{{ editingSkill ? '编辑工具组' : '创建工具组' }}</h2>
          <button @click="closeSkillModal()" class="p-1 text-slate-400 hover:text-white"><X class="w-5 h-5" /></button>
        </div>
        <div class="space-y-4">
          <div>
            <label class="block text-sm text-slate-300 mb-2">名称（标识）*</label>
            <input v-model="newSkill.name" :disabled="!!editingSkill" class="input-field" placeholder="如: knowledge" />
          </div>
          <div>
            <label class="block text-sm text-slate-300 mb-2">显示名称 *</label>
            <input v-model="newSkill.displayName" class="input-field" placeholder="如: 知识库" />
          </div>
          <div>
            <label class="block text-sm text-slate-300 mb-2">描述</label>
            <textarea v-model="newSkill.description" class="input-field" rows="2" placeholder="工具组描述..." />
          </div>
          <div class="grid grid-cols-2 gap-4">
            <div>
              <label class="block text-sm text-slate-300 mb-2">分类</label>
              <select v-model="newSkill.category" class="input-field">
                <option v-for="c in categories" :key="c" :value="c">{{ categoryLabels[c] }}</option>
              </select>
            </div>
            <div>
              <label class="block text-sm text-slate-300 mb-2">图标</label>
              <select v-model="newSkill.icon" class="input-field">
                <option v-for="i in iconOptions" :key="i" :value="i">{{ i }}</option>
              </select>
            </div>
          </div>
        </div>
        <div class="flex gap-3 mt-6">
          <button @click="closeSkillModal()" class="btn-secondary flex-1">取消</button>
          <button @click="saveSkill" class="btn-primary flex-1">{{ editingSkill ? '保存' : '创建' }}</button>
        </div>
      </div>
    </div>

    <!-- 工具弹窗 -->
    <div v-if="showToolModal" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm">
      <div class="glass-card w-full max-w-2xl max-h-[90vh] overflow-y-auto p-6 animate-slide-up">
        <div class="flex items-center justify-between mb-4">
          <h2 class="text-xl font-bold text-white">{{ editingTool ? '编辑工具' : '添加工具' }}</h2>
          <button @click="closeToolModal()" class="p-1 text-slate-400 hover:text-white"><X class="w-5 h-5" /></button>
        </div>
        <div class="space-y-4">
          <div class="grid grid-cols-2 gap-4">
            <div>
              <label class="block text-sm text-slate-300 mb-2">工具名称（function name）*</label>
              <input v-model="newTool.name" :disabled="!!editingTool" class="input-field" placeholder="如: search_database" />
            </div>
            <div>
              <label class="block text-sm text-slate-300 mb-2">显示名称 *</label>
              <input v-model="newTool.displayName" class="input-field" placeholder="如: 数据库检索" />
            </div>
          </div>
          <div>
            <label class="block text-sm text-slate-300 mb-2">描述（传给LLM）*</label>
            <textarea v-model="newTool.description" class="input-field" rows="2" placeholder="工具功能描述..." />
          </div>
          <div class="grid grid-cols-2 gap-4">
            <div>
              <label class="block text-sm text-slate-300 mb-2">类型</label>
              <select v-model="newTool.type" class="input-field">
                <option value="builtin">内置（平台执行）</option>
                <option value="api">外部API</option>
              </select>
            </div>
            <div>
              <label class="block text-sm text-slate-300 mb-2">执行端点</label>
              <input v-model="newTool.endpoint" :disabled="newTool.type === 'builtin'" class="input-field" :placeholder="newTool.type === 'builtin' ? '内置工具自动处理' : 'https://api.example.com/tool'" />
              <p class="text-xs text-slate-500 mt-1">{{ newTool.type === 'builtin' ? '通过平台 /api/tools/execute 执行' : '平台代理转发到此端点' }}</p>
            </div>
          </div>
          <div>
            <label class="block text-sm text-slate-300 mb-2">输入参数 JSON Schema *</label>
            <textarea v-model="newTool.inputSchema" class="input-field font-mono text-xs" rows="8"
              placeholder='{"type":"object","properties":{"query":{"type":"string"}},"required":["query"]}' />
          </div>
        </div>
        <div class="flex gap-3 mt-6">
          <button @click="closeToolModal()" class="btn-secondary flex-1">取消</button>
          <button @click="saveTool" class="btn-primary flex-1">{{ editingTool ? '保存' : '创建' }}</button>
        </div>
      </div>
    </div>
  </div>
</template>
