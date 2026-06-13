<script setup>
import { ref, onMounted } from 'vue'
import { useApiStore } from '@/stores/api'
import { Plug, MessageSquare, CheckCircle2, XCircle, Settings2, Send } from 'lucide-vue-next'

const api = useApiStore()
const loading = ref(false)

const meituanConfig = ref({
  enabled: false,
  appId: '',
  appSecret: '',
  webhookUrl: 'https://api.hczk-ai.com/v1/webhook/meituan',
  autoReply: true,
  agentId: ''
})

const douyinConfig = ref({
  enabled: false,
  appId: '',
  appSecret: '',
  webhookUrl: 'https://api.hczk-ai.com/v1/webhook/douyin',
  autoReply: false,
  agentId: ''
})

const agents = ref([])

onMounted(async () => {
  await loadPlatforms()
  await loadAgents()
})

async function loadPlatforms() {
  loading.value = true
  const res = await api.get('/platforms')
  if (res.code === 200) {
    const data = res.data || []
    data.forEach(p => {
      if (p.type === 'MEITUAN') {
        meituanConfig.value = {
          enabled: p.enabled ?? false,
          appId: p.appId || '',
          appSecret: p.appSecret || '',
          webhookUrl: p.webhookUrl || 'https://api.hczk-ai.com/v1/webhook/meituan',
          autoReply: p.autoReply ?? true,
          agentId: p.agentId || ''
        }
      } else if (p.type === 'DOUYIN') {
        douyinConfig.value = {
          enabled: p.enabled ?? false,
          appId: p.appId || '',
          appSecret: p.appSecret || '',
          webhookUrl: p.webhookUrl || 'https://api.hczk-ai.com/v1/webhook/douyin',
          autoReply: p.autoReply ?? false,
          agentId: p.agentId || ''
        }
      }
    })
  }
  loading.value = false
}

async function loadAgents() {
  const res = await api.get('/agents')
  if (res.code === 200) {
    agents.value = res.data || []
  }
}

async function saveConfig(type) {
  const config = type === 'MEITUAN' ? meituanConfig.value : douyinConfig.value
  const res = await api.post('/platforms', {
    type,
    ...config
  })
  if (res.code === 200) {
    alert(type === 'MEITUAN' ? '美团配置已保存' : '抖音配置已保存')
  } else {
    alert(res.message || '保存失败')
  }
}

async function testConnection(type) {
  const res = await api.post(`/platforms/${type}/test`)
  if (res.code === 200) {
    alert('连接测试成功')
  } else {
    alert(res.message || '连接测试失败')
  }
}
</script>

<template>
  <div class="space-y-6 animate-fade-in">
    <div>
      <h1 class="text-2xl font-bold text-white">平台对接</h1>
      <p class="text-slate-400 mt-1">配置美团、抖音等第三方平台智能客服接入</p>
    </div>

    <div v-if="loading" class="text-slate-500">加载中...</div>

    <!-- Meituan -->
    <div v-if="!loading" class="glass-card p-6">
      <div class="flex items-center justify-between mb-6">
        <div class="flex items-center gap-3">
          <div class="w-10 h-10 rounded-xl bg-gradient-to-br from-yellow-500/20 to-orange-500/20 flex items-center justify-center">
            <MessageSquare class="w-5 h-5 text-yellow-400" />
          </div>
          <div>
            <h3 class="text-lg font-semibold text-white">美团智能客服</h3>
            <p class="text-sm text-slate-400">接入美团商家后台，自动回复用户咨询</p>
          </div>
        </div>
        <div class="flex items-center gap-3">
          <span :class="['px-3 py-1 text-xs rounded-full border flex items-center gap-1.5', meituanConfig.enabled ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20' : 'bg-slate-500/10 text-slate-400 border-slate-500/20']">
            <component :is="meituanConfig.enabled ? CheckCircle2 : XCircle" class="w-3.5 h-3.5" />
            {{ meituanConfig.enabled ? '已启用' : '未启用' }}
          </span>
        </div>
      </div>

      <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div>
          <label class="block text-sm text-slate-300 mb-2">App ID</label>
          <input v-model="meituanConfig.appId" class="input-field" placeholder="美团开放平台 App ID" />
        </div>
        <div>
          <label class="block text-sm text-slate-300 mb-2">App Secret</label>
          <input v-model="meituanConfig.appSecret" type="password" class="input-field" placeholder="App Secret" />
        </div>
        <div class="md:col-span-2">
          <label class="block text-sm text-slate-300 mb-2">Webhook 接收地址</label>
          <div class="flex gap-2">
            <input v-model="meituanConfig.webhookUrl" class="input-field" readonly />
            <button class="btn-secondary" @click="() => navigator.clipboard.writeText(meituanConfig.webhookUrl)">复制</button>
          </div>
        </div>
        <div>
          <label class="block text-sm text-slate-300 mb-2">关联智能体</label>
          <select v-model="meituanConfig.agentId" class="input-field">
            <option value="">选择智能体</option>
            <option v-for="agent in agents" :key="agent.id" :value="agent.id">{{ agent.name }}</option>
          </select>
        </div>
        <div class="flex items-center gap-4">
          <label class="flex items-center gap-2 cursor-pointer">
            <input v-model="meituanConfig.autoReply" type="checkbox" class="w-4 h-4 rounded border-slate-600 bg-slate-700 text-emerald-500 focus:ring-emerald-500" />
            <span class="text-sm text-slate-300">自动回复</span>
          </label>
          <label class="flex items-center gap-2 cursor-pointer">
            <input v-model="meituanConfig.enabled" type="checkbox" class="w-4 h-4 rounded border-slate-600 bg-slate-700 text-emerald-500 focus:ring-emerald-500" />
            <span class="text-sm text-slate-300">启用接入</span>
          </label>
        </div>
      </div>
      <div class="flex gap-3 mt-6">
        <button @click="testConnection('MEITUAN')" class="btn-secondary flex items-center gap-2">
          <Send class="w-4 h-4" /> 测试连接
        </button>
        <button @click="saveConfig('MEITUAN')" class="btn-primary flex items-center gap-2">
          <Settings2 class="w-4 h-4" /> 保存配置
        </button>
      </div>
    </div>

    <!-- Douyin -->
    <div v-if="!loading" class="glass-card p-6">
      <div class="flex items-center justify-between mb-6">
        <div class="flex items-center gap-3">
          <div class="w-10 h-10 rounded-xl bg-gradient-to-br from-cyan-500/20 to-blue-500/20 flex items-center justify-center">
            <MessageSquare class="w-5 h-5 text-cyan-400" />
          </div>
          <div>
            <h3 class="text-lg font-semibold text-white">抖音智能客服</h3>
            <p class="text-sm text-slate-400">接入抖音企业号/小店，自动回复私信与评论</p>
          </div>
        </div>
        <div class="flex items-center gap-3">
          <span :class="['px-3 py-1 text-xs rounded-full border flex items-center gap-1.5', douyinConfig.enabled ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20' : 'bg-slate-500/10 text-slate-400 border-slate-500/20']">
            <component :is="douyinConfig.enabled ? CheckCircle2 : XCircle" class="w-3.5 h-3.5" />
            {{ douyinConfig.enabled ? '已启用' : '未启用' }}
          </span>
        </div>
      </div>

      <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div>
          <label class="block text-sm text-slate-300 mb-2">App ID</label>
          <input v-model="douyinConfig.appId" class="input-field" placeholder="抖音开放平台 App ID" />
        </div>
        <div>
          <label class="block text-sm text-slate-300 mb-2">App Secret</label>
          <input v-model="douyinConfig.appSecret" type="password" class="input-field" placeholder="App Secret" />
        </div>
        <div class="md:col-span-2">
          <label class="block text-sm text-slate-300 mb-2">Webhook 接收地址</label>
          <div class="flex gap-2">
            <input v-model="douyinConfig.webhookUrl" class="input-field" readonly />
            <button class="btn-secondary" @click="() => navigator.clipboard.writeText(douyinConfig.webhookUrl)">复制</button>
          </div>
        </div>
        <div>
          <label class="block text-sm text-slate-300 mb-2">关联智能体</label>
          <select v-model="douyinConfig.agentId" class="input-field">
            <option value="">选择智能体</option>
            <option v-for="agent in agents" :key="agent.id" :value="agent.id">{{ agent.name }}</option>
          </select>
        </div>
        <div class="flex items-center gap-4">
          <label class="flex items-center gap-2 cursor-pointer">
            <input v-model="douyinConfig.autoReply" type="checkbox" class="w-4 h-4 rounded border-slate-600 bg-slate-700 text-emerald-500 focus:ring-emerald-500" />
            <span class="text-sm text-slate-300">自动回复</span>
          </label>
          <label class="flex items-center gap-2 cursor-pointer">
            <input v-model="douyinConfig.enabled" type="checkbox" class="w-4 h-4 rounded border-slate-600 bg-slate-700 text-emerald-500 focus:ring-emerald-500" />
            <span class="text-sm text-slate-300">启用接入</span>
          </label>
        </div>
      </div>
      <div class="flex gap-3 mt-6">
        <button @click="testConnection('DOUYIN')" class="btn-secondary flex items-center gap-2">
          <Send class="w-4 h-4" /> 测试连接
        </button>
        <button @click="saveConfig('DOUYIN')" class="btn-primary flex items-center gap-2">
          <Settings2 class="w-4 h-4" /> 保存配置
        </button>
      </div>
    </div>
  </div>
</template>
