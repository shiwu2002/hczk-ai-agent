<script setup>
import { ref, onMounted, computed } from 'vue';
import { useApiStore } from '@/stores/api';
import { useToastStore } from '@/stores/toast';
import {
  RefreshCw, Power, PowerOff, Search, Terminal, AlertCircle, CheckCircle2,
  Clock, XCircle, ChevronDown, ChevronRight, ExternalLink, RotateCcw, Package, Copy
} from 'lucide-vue-next';

const api = useApiStore();
const toast = useToastStore();
const loading = ref(false);
const syncing = ref(false);
const searchQuery = ref('');
const categoryFilter = ref('');
const clis = ref([]);
const expandedClis = ref({});
const cliCommands = ref({});

onMounted(loadData);

async function loadData() {
  loading.value = true;
  const res = await api.get('/platform/cli-anything/clis');
  if (res.code === 200) clis.value = res.data || [];
  loading.value = false;
}

async function manualSync() {
  syncing.value = true;
  const res = await api.post('/platform/cli-anything/sync');
  syncing.value = false;
  if (res.code === 200) {
    toast.success(`同步完成：注册表 ${res.data.registry_synced} 项，SKILL.md ${res.data.skill_md_synced} 项`);
    loadData();
  }
}

// 启用 CLI 工具
async function enableCli(cli) {
  const res = await api.post(`/platform/cli-anything/clis/${cli.name}/enable`);
  if (res.code === 200) {
    toast.success(`${cli.displayName || cli.name} 已启用，智能体可发现该工具`);
    loadData();
  }
}

// 禁用 CLI 工具
async function disableCli(cli) {
  if (!confirm(`确定禁用 ${cli.displayName || cli.name}？智能体将不再发现该工具。`)) return;
  const res = await api.post(`/platform/cli-anything/clis/${cli.name}/disable`);
  if (res.code === 200) {
    toast.success(`${cli.displayName || cli.name} 已禁用`);
    loadData();
  }
}

async function resetSync(cli) {
  const res = await api.post(`/platform/cli-anything/clis/${cli.name}/reset-sync`);
  if (res.code === 200) {
    toast.success('已重置，将在下次定时同步时重试');
    loadData();
  }
}

async function toggleExpand(cli) {
  const wasExpanded = expandedClis.value[cli.name];
  expandedClis.value[cli.name] = !wasExpanded;
  if (!wasExpanded && !cliCommands.value[cli.name]) {
    const res = await api.get(`/platform/cli-anything/clis/${cli.name}/commands`);
    if (res.code === 200) cliCommands.value[cli.name] = res.data || [];
  }
}

function copyInstallCmd(cli) {
  if (cli.installCmd) {
    navigator.clipboard.writeText(cli.installCmd);
    toast.success('安装命令已复制到剪贴板');
  }
}

const filteredClis = computed(() => {
  let list = clis.value;
  if (categoryFilter.value) {
    list = list.filter(c => c.category === categoryFilter.value);
  }
  if (searchQuery.value.trim()) {
    const kw = searchQuery.value.trim().toLowerCase();
    list = list.filter(c =>
      (c.name || '').toLowerCase().includes(kw) ||
      (c.displayName || '').toLowerCase().includes(kw) ||
      (c.description || '').toLowerCase().includes(kw)
    );
  }
  return list;
});

const categories = computed(() => {
  const set = new Set(clis.value.map(c => c.category).filter(Boolean));
  return [...set].sort();
});

function syncStatusStyle(status) {
  switch (status) {
    case 'synced': return 'bg-emerald-500/10 text-emerald-400';
    case 'pending': return 'bg-amber-500/10 text-amber-400';
    case 'failed': return 'bg-red-500/10 text-red-400';
    default: return 'bg-slate-500/10 text-slate-400';
  }
}
function syncStatusLabel(status) {
  switch (status) {
    case 'synced': return '已同步';
    case 'pending': return '待同步';
    case 'failed': return '同步失败';
    default: return status || '未知';
  }
}
function enableStatusStyle(enabled) {
  return enabled ? 'bg-emerald-500/10 text-emerald-400' : 'bg-slate-500/10 text-slate-400';
}
function enableStatusLabel(enabled) {
  return enabled ? '已启用' : '未启用';
}
function syncStatusIcon(status) {
  switch (status) {
    case 'synced': return CheckCircle2;
    case 'pending': return Clock;
    case 'failed': return XCircle;
    default: return AlertCircle;
  }
}
</script>

<template>
  <div class="space-y-6 animate-fade-in">
    <!-- 头部 -->
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-2xl font-bold text-white">CLI-Anything 工具市场</h1>
        <p class="text-slate-400 mt-1">发现 CLI 工具，启用后智能体可发现并在本地安装执行</p>
      </div>
      <button @click="manualSync" :disabled="syncing" class="btn-primary flex items-center gap-2">
        <RefreshCw :class="['w-4 h-4', syncing ? 'animate-spin' : '']" />
        {{ syncing ? '同步中...' : '手动拉取' }}
      </button>
    </div>

    <!-- 统计卡片 -->
    <div class="grid grid-cols-2 md:grid-cols-4 gap-4">
      <div class="glass-card p-4">
        <div class="text-sm text-slate-400">注册表总数</div>
        <div class="text-2xl font-bold text-white mt-1">{{ clis.length }}</div>
      </div>
      <div class="glass-card p-4">
        <div class="text-sm text-slate-400">已同步</div>
        <div class="text-2xl font-bold text-emerald-400 mt-1">{{ clis.filter(c => c.syncStatus === 'synced').length }}</div>
      </div>
      <div class="glass-card p-4">
        <div class="text-sm text-slate-400">已启用</div>
        <div class="text-2xl font-bold text-blue-400 mt-1">{{ clis.filter(c => c.isEnabled).length }}</div>
      </div>
      <div class="glass-card p-4">
        <div class="text-sm text-slate-400">同步失败</div>
        <div class="text-2xl font-bold text-red-400 mt-1">{{ clis.filter(c => c.syncStatus === 'failed').length }}</div>
      </div>
    </div>

    <!-- 搜索和筛选 -->
    <div class="flex gap-3">
      <div class="relative flex-1">
        <Search class="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-500" />
        <input v-model="searchQuery" class="input-field pl-10" placeholder="搜索工具名称或描述..." />
      </div>
      <select v-model="categoryFilter" class="input-field w-40">
        <option value="">全部分类</option>
        <option v-for="cat in categories" :key="cat" :value="cat">{{ cat }}</option>
      </select>
    </div>

    <!-- CLI 工具列表 -->
    <div v-if="loading" class="text-slate-500 text-center py-12">加载中...</div>
    <div v-else-if="filteredClis.length === 0" class="text-slate-500 text-center py-12">
      <Package class="w-12 h-12 mx-auto mb-3 text-slate-600" />
      <p>暂无 CLI 工具</p>
      <p class="text-sm mt-1">点击右上角"手动拉取"从远程注册表同步</p>
    </div>
    <div v-else class="space-y-3">
      <div v-for="cli in filteredClis" :key="cli.name" class="glass-card overflow-hidden">
        <!-- CLI 头部 -->
        <div class="flex items-center gap-4 p-4 cursor-pointer hover:bg-white/[0.02] transition-colors"
             @click="toggleExpand(cli)">
          <button class="text-slate-400 flex-shrink-0">
            <ChevronRight v-if="!expandedClis[cli.name]" class="w-5 h-5 transition-transform" />
            <ChevronDown v-else class="w-5 h-5 transition-transform" />
          </button>
          <Terminal class="w-6 h-6 text-purple-400 flex-shrink-0" />
          <div class="flex-1 min-w-0">
            <div class="flex items-center gap-2 flex-wrap">
              <h3 class="text-lg font-semibold text-white">{{ cli.displayName || cli.name }}</h3>
              <span class="px-2 py-0.5 text-xs rounded-full bg-purple-500/10 text-purple-400">{{ cli.category }}</span>
              <span class="px-2 py-0.5 text-xs rounded-full" :class="syncStatusStyle(cli.syncStatus)">
                <component :is="syncStatusIcon(cli.syncStatus)" class="w-2.5 h-2.5 inline -mt-0.5" />
                {{ syncStatusLabel(cli.syncStatus) }}
              </span>
              <span class="px-2 py-0.5 text-xs rounded-full" :class="enableStatusStyle(cli.isEnabled)">
                {{ enableStatusLabel(cli.isEnabled) }}
              </span>
              <span v-if="cli.version" class="text-xs text-slate-600">v{{ cli.version }}</span>
            </div>
            <p class="text-sm text-slate-500 mt-0.5 line-clamp-1">{{ cli.description }}</p>
          </div>
          <span class="text-sm text-slate-500 flex-shrink-0">{{ cli.commandCount || 0 }} 个命令</span>
          <div class="flex items-center gap-1 flex-shrink-0" @click.stop>
            <!-- 启用/禁用按钮 -->
            <template v-if="cli.isEnabled">
              <button @click="disableCli(cli)" class="p-1.5 rounded hover:bg-white/5 text-red-400" title="禁用">
                <PowerOff class="w-3.5 h-3.5" />
              </button>
            </template>
            <template v-else>
              <button @click="enableCli(cli)" :disabled="cli.syncStatus !== 'synced'"
                      class="p-1.5 rounded hover:bg-white/5 text-emerald-400 disabled:text-slate-600 disabled:cursor-not-allowed"
                      :title="cli.syncStatus !== 'synced' ? '需先同步完成' : '启用'">
                <Power class="w-3.5 h-3.5" />
              </button>
            </template>
            <!-- 复制安装命令 -->
            <button v-if="cli.installCmd" @click="copyInstallCmd(cli)"
                    class="p-1.5 rounded hover:bg-white/5 text-slate-400" title="复制安装命令">
              <Copy class="w-3.5 h-3.5" />
            </button>
            <!-- 重置同步失败 -->
            <button v-if="cli.syncStatus === 'failed'" @click="resetSync(cli)"
                    class="p-1.5 rounded hover:bg-white/5 text-amber-400" title="重置同步">
              <RotateCcw class="w-3.5 h-3.5" />
            </button>
            <!-- 主页链接 -->
            <a v-if="cli.homepage" :href="cli.homepage" target="_blank"
               class="p-1.5 rounded hover:bg-white/5 text-slate-400" title="项目主页">
              <ExternalLink class="w-3.5 h-3.5" />
            </a>
          </div>
        </div>

        <!-- 展开详情 -->
        <div v-if="expandedClis[cli.name]" class="border-t border-white/5">
          <!-- 安装命令 -->
          <div v-if="cli.installCmd" class="px-4 pt-3 pb-2">
            <div class="text-xs text-slate-400 mb-1">安装命令（智能体本地执行）</div>
            <div class="flex items-center gap-2 p-2 rounded bg-slate-500/[0.05] border border-slate-500/10">
              <code class="text-xs font-mono text-emerald-400 flex-1 break-all">{{ cli.installCmd }}</code>
              <button @click="copyInstallCmd(cli)" class="p-1 rounded hover:bg-white/5 text-slate-400 flex-shrink-0">
                <Copy class="w-3 h-3" />
              </button>
            </div>
          </div>
          <!-- 入口命令 -->
          <div v-if="cli.entryPoint" class="px-4 pb-2">
            <div class="text-xs text-slate-400 mb-1">入口命令</div>
            <code class="text-xs font-mono text-purple-400 bg-purple-500/5 px-2 py-1 rounded">{{ cli.entryPoint }}</code>
          </div>

          <!-- 命令列表 -->
          <div class="p-2 px-4 text-xs text-slate-500">命令列表</div>
          <div v-if="!cliCommands[cli.name] || cliCommands[cli.name].length === 0"
               class="px-4 pb-3 text-xs text-slate-600">
            {{ cli.syncStatus === 'synced' ? '暂无命令（SKILL.md 未解析或无命令定义）' : '需先同步 SKILL.md' }}
          </div>
          <div v-else class="px-4 pb-3 space-y-2 max-h-80 overflow-y-auto">
            <div v-for="cmd in cliCommands[cli.name]" :key="cmd.id"
                 class="flex items-start gap-3 p-3 rounded-lg bg-slate-500/[0.03] border border-slate-500/10">
              <div class="flex-1 min-w-0">
                <div class="flex items-center gap-2">
                  <code class="text-purple-400 text-xs font-mono bg-purple-500/5 px-1 py-0.5 rounded">{{ cmd.commandGroup }}</code>
                  <span class="text-sm text-white">{{ cmd.commandName }}</span>
                </div>
                <p class="text-xs text-slate-500 mt-0.5">{{ cmd.commandDescription || cmd.groupDescription }}</p>
                <div class="text-xs text-slate-600 mt-1">
                  <code class="font-mono bg-slate-500/5 px-1 py-0.5 rounded">{{ cmd.fullCommand }}</code>
                </div>
              </div>
            </div>
          </div>

          <!-- 安装失败信息 -->
          <div v-if="cli.installError" class="px-4 pb-3">
            <div class="p-3 rounded-lg bg-red-500/5 border border-red-500/10 text-xs text-red-400">
              <AlertCircle class="w-3.5 h-3.5 inline -mt-0.5" /> 安装失败：{{ cli.installError }}
            </div>
          </div>
          <!-- 同步错误信息 -->
          <div v-if="cli.syncError" class="px-4 pb-3">
            <div class="p-3 rounded-lg bg-amber-500/5 border border-amber-500/10 text-xs text-amber-400">
              <AlertCircle class="w-3.5 h-3.5 inline -mt-0.5" /> 同步失败：{{ cli.syncError }}
              <span v-if="cli.syncRetryCount" class="ml-2">（已重试 {{ cli.syncRetryCount }} 次）</span>
            </div>
          </div>
          <!-- 依赖说明 -->
          <div v-if="cli.requires" class="px-4 pb-3">
            <div class="text-xs text-slate-500">
              <span class="text-slate-400">运行依赖：</span>{{ cli.requires }}
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
