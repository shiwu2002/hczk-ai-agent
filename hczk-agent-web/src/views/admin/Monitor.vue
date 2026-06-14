<script setup>import { ref, onMounted, onUnmounted } from 'vue';
import { useApiStore } from '@/stores/api';
import { Activity, Bot, Server, CheckCircle, XCircle, RefreshCw, Clock, TrendingUp, AlertTriangle, Wifi, WifiOff } from 'lucide-vue-next';
const api = useApiStore();
const loading = ref(false);
const lastUpdate = ref(null);
const autoRefresh = ref(true);
let refreshInterval = null;
// 监控数据
const skills = ref([]);
const bindings = ref([]);
const runtimeStatus = ref({ online: false, message: '' });
const stats = ref({
 totalSkills: 0,
 activeSkills: 0,
 totalBindings: 0,
 activeBindings: 0,
 skillModeCount: 0,
 endpointModeCount: 0
});
async function loadData() {
 loading.value = true;
 try {
 const [skillRes, bindingRes] = await Promise.all([
 api.get('/platform/skills'),
 api.get('/platform/bindings')
 ]);
 skills.value = skillRes.code === 200 ? skillRes.data || [] : [];
 bindings.value = bindingRes.code === 200 ? bindingRes.data || [] : [];
 // 检查运行时状态
 await checkRuntimeStatus();
 // 计算统计
 calculateStats();
 lastUpdate.value = new Date().toLocaleString();
 }
 catch (error) {
 console.error('加载监控数据失败:', error);
 }
 loading.value = false;
}
async function checkRuntimeStatus() {
 try {
 const res = await fetch('http://localhost:3000/api/health');
 if (res.ok) {
 const data = await res.json();
 runtimeStatus.value = { online: true, message: data.status === 'ok' ? '运行正常' : '状态异常' };
 }
 else {
 runtimeStatus.value = { online: false, message: '服务不可达' };
 }
 }
 catch (error) {
 runtimeStatus.value = { online: false, message: '连接失败: ' + error.message };
 }
}
function calculateStats() {
 stats.value = {
 totalSkills: skills.value.length,
 activeSkills: skills.value.filter(s => s.status === 'active').length,
 totalBindings: bindings.value.length,
 activeBindings: bindings.value.filter(b => b.enabled).length,
 skillModeCount: bindings.value.filter(b => b.skillId).length,
 endpointModeCount: bindings.value.filter(b => b.agentEndpoint).length
 };
}
async function refresh() {
 await loadData();
}
function toggleAutoRefresh() {
 autoRefresh.value = !autoRefresh.value;
 if (autoRefresh.value) {
 startAutoRefresh();
 }
 else {
 stopAutoRefresh();
 }
}
function startAutoRefresh() {
 if (refreshInterval)
 return;
 refreshInterval = setInterval(() => {
 loadData();
 }, 10000); // 每10秒刷新
}
function stopAutoRefresh() {
 if (refreshInterval) {
 clearInterval(refreshInterval);
 refreshInterval = null;
 }
}
onMounted(() => {
 loadData();
 if (autoRefresh.value) {
 startAutoRefresh();
 }
});
onUnmounted(() => {
 stopAutoRefresh();
});
function getSkillStatusIcon(status) {
 return status === 'active' ? CheckCircle : XCircle;
}
function getSkillStatusColor(status) {
 return status === 'active' ? 'text-emerald-400' : 'text-red-400';
}
function getBindingMode(binding) {
 return binding.skillId ? 'Skill 模式' : 'Endpoint 模式';
}
function getBindingModeColor(binding) {
 return binding.skillId ? 'bg-cyan-500/10 text-cyan-400' : 'bg-amber-500/10 text-amber-400';
}
</script>

<template>
  <div class="space-y-6 animate-fade-in">
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-2xl font-bold text-white">智能体监控</h1>
        <p class="text-slate-400 mt-1">实时监控智能体和 Skills 的运行状态</p>
      </div>
      <div class="flex items-center gap-3">
        <span class="text-sm text-slate-400">上次更新: {{ lastUpdate || '--' }}</span>
        <button @click="toggleAutoRefresh" :class="['px-4 py-2 rounded-lg flex items-center gap-2 transition-colors', autoRefresh ? 'bg-emerald-500/20 text-emerald-400' : 'bg-white/5 text-slate-400']">
          <RefreshCw class="w-4 h-4" :class="{ 'animate-spin': autoRefresh }" />
          {{ autoRefresh ? '自动刷新中' : '启用自动刷新' }}
        </button>
        <button @click="refresh" class="btn-primary flex items-center gap-2">
          <RefreshCw class="w-4 h-4" /> 刷新
        </button>
      </div>
    </div>

    <!-- 运行时状态卡片 -->
    <div class="glass-card p-6">
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-4">
          <div :class="['w-14 h-14 rounded-xl flex items-center justify-center', runtimeStatus.online ? 'bg-emerald-500/20' : 'bg-red-500/20']">
            <component :is="runtimeStatus.online ? Wifi : WifiOff" :class="['w-7 h-7', runtimeStatus.online ? 'text-emerald-400' : 'text-red-400']" />
          </div>
          <div>
            <h3 class="text-lg font-semibold text-white">通用智能体运行时</h3>
            <div class="flex items-center gap-2 mt-1">
              <span :class="['px-2 py-0.5 text-xs rounded-full', runtimeStatus.online ? 'bg-emerald-500/10 text-emerald-400' : 'bg-red-500/10 text-red-400']">
                {{ runtimeStatus.online ? '在线' : '离线' }}
              </span>
              <span class="text-sm text-slate-400">{{ runtimeStatus.message }}</span>
            </div>
          </div>
        </div>
        <div class="flex items-center gap-2">
          <Clock class="w-4 h-4 text-slate-500" />
          <span class="text-sm text-slate-500">http://localhost:3000</span>
        </div>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-4">
      <div class="glass-card p-4">
        <div class="flex items-center gap-3">
          <div class="w-10 h-10 rounded-lg bg-cyan-500/20 flex items-center justify-center">
            <Server class="w-5 h-5 text-cyan-400" />
          </div>
          <div>
            <p class="text-sm text-slate-400">Skills 总数</p>
            <p class="text-xl font-bold text-white">{{ stats.totalSkills }}</p>
          </div>
        </div>
      </div>
      <div class="glass-card p-4">
        <div class="flex items-center gap-3">
          <div class="w-10 h-10 rounded-lg bg-emerald-500/20 flex items-center justify-center">
            <CheckCircle class="w-5 h-5 text-emerald-400" />
          </div>
          <div>
            <p class="text-sm text-slate-400">活跃 Skills</p>
            <p class="text-xl font-bold text-white">{{ stats.activeSkills }}</p>
          </div>
        </div>
      </div>
      <div class="glass-card p-4">
        <div class="flex items-center gap-3">
          <div class="w-10 h-10 rounded-lg bg-purple-500/20 flex items-center justify-center">
            <Bot class="w-5 h-5 text-purple-400" />
          </div>
          <div>
            <p class="text-sm text-slate-400">绑定总数</p>
            <p class="text-xl font-bold text-white">{{ stats.totalBindings }}</p>
          </div>
        </div>
      </div>
      <div class="glass-card p-4">
        <div class="flex items-center gap-3">
          <div class="w-10 h-10 rounded-lg bg-emerald-500/20 flex items-center justify-center">
            <Activity class="w-5 h-5 text-emerald-400" />
          </div>
          <div>
            <p class="text-sm text-slate-400">活跃绑定</p>
            <p class="text-xl font-bold text-white">{{ stats.activeBindings }}</p>
          </div>
        </div>
      </div>
      <div class="glass-card p-4">
        <div class="flex items-center gap-3">
          <div class="w-10 h-10 rounded-lg bg-cyan-500/20 flex items-center justify-center">
            <Bot class="w-5 h-5 text-cyan-400" />
          </div>
          <div>
            <p class="text-sm text-slate-400">Skill 模式</p>
            <p class="text-xl font-bold text-white">{{ stats.skillModeCount }}</p>
          </div>
        </div>
      </div>
      <div class="glass-card p-4">
        <div class="flex items-center gap-3">
          <div class="w-10 h-10 rounded-lg bg-amber-500/20 flex items-center justify-center">
            <Server class="w-5 h-5 text-amber-400" />
          </div>
          <div>
            <p class="text-sm text-slate-400">Endpoint 模式</p>
            <p class="text-xl font-bold text-white">{{ stats.endpointModeCount }}</p>
          </div>
        </div>
      </div>
    </div>

    <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
      <!-- Skills 列表 -->
      <div class="glass-card">
        <div class="p-4 border-b border-white/10">
          <h2 class="text-lg font-semibold text-white flex items-center gap-2">
            <Bot class="w-5 h-5 text-purple-400" />
            Skills 列表
            <span class="ml-auto text-sm font-normal text-slate-400">{{ skills.length }} 个</span>
          </h2>
        </div>
        <div class="p-4">
          <div v-if="loading" class="text-center py-8 text-slate-500">加载中...</div>
          <div v-else-if="skills.length === 0" class="text-center py-8 text-slate-500">暂无 Skills</div>
          <div v-else class="space-y-3">
            <div v-for="skill in skills" :key="skill.id" class="flex items-center justify-between p-3 rounded-lg bg-white/5">
              <div class="flex items-center gap-3">
                <div :class="['w-8 h-8 rounded-lg flex items-center justify-center', skill.status === 'active' ? 'bg-emerald-500/20' : 'bg-red-500/20']">
                  <component :is="getSkillStatusIcon(skill.status)" :class="['w-4 h-4', getSkillStatusColor(skill.status)]" />
                </div>
                <div>
                  <p class="font-medium text-white">{{ skill.name }}</p>
                  <p class="text-sm text-slate-500">{{ skill.category }} · v{{ skill.version }}</p>
                </div>
              </div>
              <span :class="['px-2 py-0.5 text-xs rounded-full', skill.status === 'active' ? 'bg-emerald-500/10 text-emerald-400' : 'bg-red-500/10 text-red-400']">
                {{ skill.status === 'active' ? '活跃' : '停用' }}
              </span>
            </div>
          </div>
        </div>
      </div>

      <!-- 绑定列表 -->
      <div class="glass-card">
        <div class="p-4 border-b border-white/10">
          <h2 class="text-lg font-semibold text-white flex items-center gap-2">
            <Activity class="w-5 h-5 text-emerald-400" />
            智能体绑定
            <span class="ml-auto text-sm font-normal text-slate-400">{{ bindings.length }} 个</span>
          </h2>
        </div>
        <div class="p-4">
          <div v-if="loading" class="text-center py-8 text-slate-500">加载中...</div>
          <div v-else-if="bindings.length === 0" class="text-center py-8 text-slate-500">暂无绑定</div>
          <div v-else class="space-y-3">
            <div v-for="binding in bindings" :key="binding.userId" class="flex items-center justify-between p-3 rounded-lg bg-white/5">
              <div class="flex items-center gap-3">
                <div :class="['w-8 h-8 rounded-lg flex items-center justify-center', binding.enabled ? 'bg-emerald-500/20' : 'bg-slate-500/20']">
                  <component :is="binding.enabled ? CheckCircle : XCircle" :class="['w-4 h-4', binding.enabled ? 'text-emerald-400' : 'text-slate-400']" />
                </div>
                <div>
                  <p class="font-medium text-white">用户 {{ binding.userId }}</p>
                  <div class="flex items-center gap-2 mt-1">
                    <span :class="['px-2 py-0.5 text-xs rounded-full', getBindingModeColor(binding)]">
                      {{ getBindingMode(binding) }}
                    </span>
                    <span class="text-xs text-slate-500">
                      {{ binding.skillId ? 'Skill: ' + binding.skillId : 'Endpoint' }}
                    </span>
                  </div>
                </div>
              </div>
              <span :class="['px-2 py-0.5 text-xs rounded-full border', binding.enabled ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20' : 'bg-slate-500/10 text-slate-400 border-slate-500/20']">
                {{ binding.enabled ? '已启用' : '已禁用' }}
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>