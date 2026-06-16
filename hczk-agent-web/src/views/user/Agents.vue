<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useApiStore } from '@/stores/api'
import { Bot, RefreshCw, Wifi, WifiOff } from 'lucide-vue-next'

const api = useApiStore()
const agents = ref([])
const agentHealthMap = ref({})
const loading = ref(false)
let refreshInterval = null

onMounted(() => {
  loadData()
  refreshInterval = setInterval(loadHealthStatus, 30000)
})
onUnmounted(() => {
  if (refreshInterval) clearInterval(refreshInterval)
})

async function loadData() {
  loading.value = true
  const res = await api.get('/agents')
  if (res.code === 200) agents.value = res.data || []
  loading.value = false
  await loadHealthStatus()
}

async function loadHealthStatus() {
  try {
    const res = await api.get('/agents/health')
    if (res.code === 200 && res.data) {
      agentHealthMap.value = res.data
    }
  } catch (e) {
    console.error('加载健康状态失败:', e)
  }
}

function getHealthDotClass(agentId) {
  const health = agentHealthMap.value[agentId]
  if (!health) return 'bg-slate-500'
  if (health.online) return health.runtimeStatus === 'degraded' ? 'bg-amber-400' : 'bg-emerald-400'
  return 'bg-red-400'
}

function getHealthText(agentId) {
  const health = agentHealthMap.value[agentId]
  if (!health) return '未检测'
  if (health.online) return health.runtimeStatus === 'degraded' ? '降级' : '在线'
  return '离线'
}
</script>

<template>
  <div class="space-y-6 animate-fade-in">
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-2xl font-bold text-white">平台智能体</h1>
        <p class="text-slate-400 mt-1">查看平台注册的智能体运行状态</p>
      </div>
      <button @click="loadData" class="px-3 py-2 rounded-lg bg-white/5 text-slate-300 hover:bg-white/10 flex items-center gap-2 text-sm">
        <RefreshCw class="w-3.5 h-3.5" /> 刷新
      </button>
    </div>

    <div v-if="loading" class="text-slate-500">加载中...</div>
    <div v-else-if="agents.length === 0" class="text-slate-500">暂无智能体</div>
    <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
      <div v-for="agent in agents" :key="agent.id" class="glass-card p-5 glow-border">
        <div class="flex items-start gap-3">
          <div class="relative">
            <div class="w-10 h-10 rounded-lg bg-gradient-to-br from-cyan-500/20 to-emerald-500/20 flex items-center justify-center">
              <Bot class="w-5 h-5 text-cyan-400" />
            </div>
            <div :class="['absolute -top-1 -right-1 w-3 h-3 rounded-full border-2 border-[#0a0f1c]', getHealthDotClass(agent.id)]"></div>
          </div>
          <div class="flex-1 min-w-0">
            <div class="flex items-center gap-2">
              <h3 class="text-base font-semibold text-white truncate">{{ agent.name }}</h3>
              <span :class="['text-xs', agentHealthMap[agent.id]?.online ? 'text-emerald-400' : 'text-red-400']">
                {{ getHealthText(agent.id) }}
              </span>
            </div>
            <p class="text-xs text-slate-400 mt-1 truncate">{{ agent.description || '无描述' }}</p>
            <div class="flex items-center gap-3 mt-2 text-xs text-slate-500">
              <span v-if="agent.agentType" class="bg-white/5 px-1.5 py-0.5 rounded">{{ agent.agentType }}</span>
              <span v-if="agent.version">v{{ agent.version }}</span>
              <span>{{ agent.totalCalls || 0 }} 次调用</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
