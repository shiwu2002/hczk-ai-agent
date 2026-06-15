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
const runtimeStatus = ref({ online: false, message: '', url: '', status: '', version: '', uptime: 0, components: null, runtime: null });
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
 const res = await api.get('/platform/runtime/health');
 if (res.code === 200 && res.data) {
 runtimeStatus.value = {
 online: res.data.online,
 status: res.data.status || '',
 message: res.data.message,
 url: res.data.url || '',
 version: res.data.version || '',
 uptime: res.data.uptime || 0,
 components: res.data.components || null,
 runtime: res.data.runtime || null
 };
 }
 else {
 runtimeStatus.value = { online: false, message: '检测接口异常', url: '', status: '', version: '', uptime: 0, components: null, runtime: null };
 }
 }
 catch (error) {
 runtimeStatus.value = { online: false, message: '连接失败: ' + error.message, url: '', status: '', version: '', uptime: 0, components: null, runtime: null };
 }
}
function formatUptime(ms) {
 if (!ms) return '--';
 const s = Math.floor(ms / 1000);
 if (s < 60) return s + '秒';
 if (s < 3600) return Math.floor(s / 60) + '分' + (s % 60) + '秒';
 const h = Math.floor(s / 3600);
 if (h < 24) return h + '时' + Math.floor((s % 3600) / 60) + '分';
 return Math.floor(h / 24) + '天' + (h % 24) + '时';
}
function getComponentStatusColor(status) {
 switch (status) {
 case 'connected':
 case 'available':
 case 'alive':
 case 'ready': return 'text-emerald-400';
 case 'disconnected':
 case 'unavailable': return 'text-red-400';
 default: return 'text-amber-400';
 }
}
function getRuntimeBadgeClass() {
 switch (runtimeStatus.value.status) {
 case 'ok': return 'bg-emerald-500/10 text-emerald-400';
 case 'degraded': return 'bg-amber-500/10 text-amber-400';
 default: return 'bg-red-500/10 text-red-400';
 }
}
function getRuntimeBadgeText() {
 switch (runtimeStatus.value.status) {
 case 'ok': return '在线';
 case 'degraded': return '降级';
 default: return '离线';
 }
}
function calculateStats() {
 stats.value = {
 totalSkills: skills.value.length,
 activeSkills: skills.value.filter(s => s.status === 0).length,
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
  return status === 0 ? CheckCircle : XCircle;
}
function getSkillStatusColor(status) {
  return status === 0 ? 'text-emerald-400' : 'text-red-400';
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
          <div :class="['w-14 h-14 rounded-xl flex items-center justify-center', runtimeStatus.online ? (runtimeStatus.status === 'degraded' ? 'bg-amber-500/20' : 'bg-emerald-500/20') : 'bg-red-500/20']">
            <component :is="runtimeStatus.online ? Wifi : WifiOff" :class="['w-7 h-7', runtimeStatus.online ? (runtimeStatus.status === 'degraded' ? 'text-amber-400' : 'text-emerald-400') : 'text-red-400']" />
          </div>
          <div>
            <h3 class="text-lg font-semibold text-white">通用智能体运行时</h3>
            <div class="flex items-center gap-2 mt-1">
              <span :class="['px-2 py-0.5 text-xs rounded-full', getRuntimeBadgeClass()]">
                {{ getRuntimeBadgeText() }}
              </span>
              <span class="text-sm text-slate-400">{{ runtimeStatus.message }}</span>
              <span v-if="runtimeStatus.version" class="text-xs text-slate-500">v{{ runtimeStatus.version }}</span>
            </div>
          </div>
        </div>
        <div class="flex items-center gap-4 text-sm text-slate-500">
          <span v-if="runtimeStatus.uptime" class="flex items-center gap-1">
            <Clock class="w-3.5 h-3.5" />
            {{ formatUptime(runtimeStatus.uptime) }}
          </span>
          <span>{{ runtimeStatus.url || '--' }}</span>
        </div>
      </div>

      <!-- 组件状态详情（在线时展示） -->
      <div v-if="runtimeStatus.online && runtimeStatus.components" class="mt-4 pt-4 border-t border-white/10">
        <div class="grid grid-cols-3 gap-4">
          <!-- 数据库 -->
          <div class="flex items-center gap-2 text-sm">
            <Server class="w-4 h-4 text-slate-500" />
            <span class="text-slate-400">数据库</span>
            <span :class="getComponentStatusColor(runtimeStatus.components.database?.status)">
              {{ runtimeStatus.components.database?.status === 'connected' ? '已连接' : (runtimeStatus.components.database?.status || '--') }}
            </span>
            <span v-if="runtimeStatus.components.database?.latencyMs" class="text-xs text-slate-600">{{ runtimeStatus.components.database.latencyMs }}ms</span>
          </div>
          <!-- 知识库 -->
          <div class="flex items-center gap-2 text-sm">
            <Bot class="w-4 h-4 text-slate-500" />
            <span class="text-slate-400">知识库</span>
            <span :class="getComponentStatusColor(runtimeStatus.components.knowledge?.status)">
              {{ runtimeStatus.components.knowledge?.status === 'connected' ? '已连接' : (runtimeStatus.components.knowledge?.status || '--') }}
            </span>
          </div>
          <!-- LLM -->
          <div class="flex items-center gap-2 text-sm">
            <TrendingUp class="w-4 h-4 text-slate-500" />
            <span class="text-slate-400">LLM</span>
            <span :class="getComponentStatusColor(runtimeStatus.components.llm?.status)">
              {{ runtimeStatus.components.llm?.status === 'available' ? '可用' : (runtimeStatus.components.llm?.status || '--') }}
            </span>
            <span v-if="runtimeStatus.components.llm?.model" class="text-xs text-slate-600">{{ runtimeStatus.components.llm.model }}</span>
          </div>
        </div>
        <!-- 运行时信息 -->
        <div v-if="runtimeStatus.runtime" class="mt-3 flex items-center gap-4 text-xs text-slate-500">
          <span>Skills: {{ runtimeStatus.runtime.skillsCount ?? 0 }}</span>
          <span>绑定: {{ runtimeStatus.runtime.bindingsCount ?? 0 }}</span>
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
                <div :class="['w-8 h-8 rounded-lg flex items-center justify-center', skill.status === 0 ? 'bg-emerald-500/20' : 'bg-red-500/20']">
                  <component :is="getSkillStatusIcon(skill.status)" :class="['w-4 h-4', getSkillStatusColor(skill.status)]" />
                </div>
                <div>
                  <p class="font-medium text-white">{{ skill.name }}</p>
                  <p class="text-sm text-slate-500">{{ skill.category }} · v{{ skill.version }}</p>
                </div>
              </div>
              <span :class="['px-2 py-0.5 text-xs rounded-full', skill.status === 0 ? 'bg-emerald-500/10 text-emerald-400' : 'bg-red-500/10 text-red-400']">
                {{ skill.status === 0 ? '活跃' : '停用' }}
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