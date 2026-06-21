<script setup>
import { ref, onMounted, computed } from 'vue'
import { useApiStore } from '@/stores/api'
import { Users, Search, BarChart3, Wallet, X, ChevronLeft, ChevronRight, UserCheck, Coins, Cpu, KeyRound } from 'lucide-vue-next'

const api = useApiStore()
const loading = ref(false)
const searchQuery = ref('')

const users = ref([])

// 详情弹窗
const showDetailModal = ref(false)
const detailLoading = ref(false)
const detailUser = ref(null)
const detailUsage = ref([])
const detailApiKeys = ref([])
const detailTab = ref('usage') // 'usage' | 'apikeys'

// 充值弹窗
const showRechargeModal = ref(false)
const rechargeForm = ref({ userId: null, userName: '', amount: null, paymentMethod: 'admin', remark: '' })

onMounted(loadData)

async function loadData() {
  loading.value = true
  const res = await api.get('/users')
  if (res.code === 200) {
    users.value = res.data || []
  }
  loading.value = false
}

const filteredUsers = computed(() => {
  if (!searchQuery.value) return users.value
  const q = searchQuery.value.toLowerCase()
  return users.value.filter(u =>
    (u.name || u.username || '').toLowerCase().includes(q) ||
    (u.email || '').toLowerCase().includes(q)
  )
})

// 分页
const currentPage = ref(1)
const pageSize = ref(10)
const totalPages = computed(() => Math.max(1, Math.ceil(filteredUsers.value.length / pageSize.value)))
const pagedUsers = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredUsers.value.slice(start, start + pageSize.value)
})
function goToPage(page) {
  if (page >= 1 && page <= totalPages.value) currentPage.value = page
}

/** 顶部统计 */
const stats = computed(() => {
  const list = users.value
  const total = list.length
  const active = list.filter(u => u.status === 0).length
  const totalBalance = list.reduce((s, u) => s + (u.balance || 0), 0)
  const totalTokens = list.reduce((s, u) => s + (u.totalUsageTokens || 0), 0)
  return { total, active, totalBalance, totalTokens }
})

/** 相对时间 */
function relativeTime(dateStr) {
  if (!dateStr) return '-'
  const now = Date.now()
  const diff = now - new Date(dateStr).getTime()
  const sec = Math.floor(diff / 1000)
  if (sec < 60) return '刚刚'
  const min = Math.floor(sec / 60)
  if (min < 60) return `${min}分钟前`
  const hr = Math.floor(min / 60)
  if (hr < 24) return `${hr}小时前`
  const day = Math.floor(hr / 24)
  if (day < 30) return `${day}天前`
  const month = Math.floor(day / 30)
  if (month < 12) return `${month}个月前`
  return `${Math.floor(month / 12)}年前`
}

/** 打开用户详情弹窗 */
async function openDetail(user) {
  detailUser.value = user
  detailUsage.value = []
  detailApiKeys.value = []
  detailTab.value = 'usage'
  detailLoading.value = true
  showDetailModal.value = true

  const [usageRes, keysRes] = await Promise.all([
    api.get(`/users/${user.userId}/usage`),
    api.get(`/users/${user.userId}/api-keys`)
  ])
  if (usageRes.code === 200) detailUsage.value = usageRes.data || []
  if (keysRes.code === 200) detailApiKeys.value = keysRes.data || []
  detailLoading.value = false
}

/** 格式化金额 */
function formatAmount(val) {
  if (!val) return '0.00'
  return Math.abs(Number(val)).toFixed(4)
}

/** 计算用户汇总 */
const usageSummary = computed(() => {
  const records = detailUsage.value
  let totalInput = 0, totalOutput = 0, totalCost = 0
  for (const r of records) {
    totalInput += r.inputTokens || 0
    totalOutput += r.outputTokens || 0
    totalCost += Math.abs(Number(r.amount || 0))
  }
  return { totalInput, totalOutput, totalCost, count: records.length }
})

/** 打开充值弹窗 */
function openRecharge(user) {
  rechargeForm.value = { userId: user.userId, userName: user.name || user.username, amount: null, paymentMethod: 'admin', remark: '' }
  showRechargeModal.value = true
}

/** 执行充值 */
async function doRecharge() {
  if (!rechargeForm.value.amount || rechargeForm.value.amount <= 0) {
    alert('请输入充值金额')
    return
  }
  const res = await api.post(`/billing/recharge?userId=${rechargeForm.value.userId}&amount=${rechargeForm.value.amount}&paymentMethod=${rechargeForm.value.paymentMethod}`)
  if (res.code === 200) {
    showRechargeModal.value = false
    loadData()
  } else {
    alert(res.message || '充值失败')
  }
}
</script>

<template>
  <div class="space-y-5 animate-fade-in">
    <!-- 顶部统计 -->
    <div class="grid grid-cols-2 lg:grid-cols-4 gap-3">
      <div class="glass-card rounded-xl p-3.5 flex items-center gap-3">
        <div class="w-9 h-9 rounded-lg bg-blue-500/10 flex items-center justify-center">
          <Users class="w-4.5 h-4.5 text-blue-400" />
        </div>
        <div>
          <p class="text-xs text-slate-400">总用户</p>
          <p class="text-lg font-semibold text-white">{{ stats.total }}</p>
        </div>
      </div>
      <div class="glass-card rounded-xl p-3.5 flex items-center gap-3">
        <div class="w-9 h-9 rounded-lg bg-emerald-500/10 flex items-center justify-center">
          <UserCheck class="w-4.5 h-4.5 text-emerald-400" />
        </div>
        <div>
          <p class="text-xs text-slate-400">活跃用户</p>
          <p class="text-lg font-semibold text-white">{{ stats.active }}</p>
        </div>
      </div>
      <div class="glass-card rounded-xl p-3.5 flex items-center gap-3">
        <div class="w-9 h-9 rounded-lg bg-amber-500/10 flex items-center justify-center">
          <Coins class="w-4.5 h-4.5 text-amber-400" />
        </div>
        <div>
          <p class="text-xs text-slate-400">总余额</p>
          <p class="text-lg font-semibold text-emerald-400">¥{{ stats.totalBalance.toFixed(2) }}</p>
        </div>
      </div>
      <div class="glass-card rounded-xl p-3.5 flex items-center gap-3">
        <div class="w-9 h-9 rounded-lg bg-purple-500/10 flex items-center justify-center">
          <Cpu class="w-4.5 h-4.5 text-purple-400" />
        </div>
        <div>
          <p class="text-xs text-slate-400">总Token</p>
          <p class="text-lg font-semibold text-white">{{ stats.totalTokens.toLocaleString() }}</p>
        </div>
      </div>
    </div>

    <!-- 搜索栏 -->
    <div class="relative">
      <Search class="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-500" />
      <input v-model="searchQuery" class="input-field pl-10" placeholder="搜索用户名称或邮箱..." />
    </div>

    <!-- 用户列表 -->
    <div class="glass-card rounded-2xl overflow-hidden">
      <div v-if="loading" class="flex items-center justify-center py-12">
        <div class="w-6 h-6 border-2 border-emerald-500/30 border-t-emerald-500 rounded-full animate-spin"></div>
        <span class="ml-3 text-slate-400">加载中...</span>
      </div>
      <div v-else-if="filteredUsers.length === 0" class="flex flex-col items-center justify-center py-12 text-slate-500">
        <Users class="w-12 h-12 mb-3 opacity-30" />
        <p>暂无用户</p>
      </div>
      <template v-else>
        <!-- 表头 (桌面端) -->
        <div class="hidden md:grid grid-cols-[2fr_1.5fr_0.8fr_0.8fr_0.8fr_0.8fr_0.8fr_0.6fr] gap-2 px-5 py-3 border-b border-white/5 text-xs font-medium text-slate-400 uppercase tracking-wider">
          <span>用户</span>
          <span>邮箱</span>
          <span>角色</span>
          <span>状态</span>
          <span>余额</span>
          <span>Tokens</span>
          <span>注册时间</span>
          <span class="text-right">操作</span>
        </div>

        <!-- 桌面端行 -->
        <div class="hidden md:block divide-y divide-white/5">
          <div v-for="user in pagedUsers" :key="user.userId" class="grid grid-cols-[2fr_1.5fr_0.8fr_0.8fr_0.8fr_0.8fr_0.8fr_0.6fr] gap-2 px-5 py-3 items-center hover:bg-white/[0.02] transition-colors group">
            <!-- 用户 -->
            <div class="flex items-center gap-2.5 min-w-0">
              <div class="w-8 h-8 rounded-full bg-gradient-to-br from-emerald-500 to-cyan-500 flex items-center justify-center text-white text-xs font-bold shrink-0">
                {{ (user.name || user.username || '?')[0] }}
              </div>
              <span class="text-sm text-white truncate">{{ user.name || user.username }}</span>
            </div>
            <!-- 邮箱 -->
            <span class="text-sm text-slate-400 truncate">{{ user.email || '-' }}</span>
            <!-- 角色 -->
            <span :class="['inline-flex px-2 py-0.5 text-xs rounded-full border w-fit', user.role === 0 ? 'bg-purple-500/10 text-purple-400 border-purple-500/20' : 'bg-blue-500/10 text-blue-400 border-blue-500/20']">
              {{ user.role === 0 ? '管理员' : '用户' }}
            </span>
            <!-- 状态 -->
            <span class="flex items-center gap-1.5 text-sm">
              <span class="w-1.5 h-1.5 rounded-full" :class="user.status === 0 ? 'bg-emerald-400' : 'bg-slate-500'"></span>
              <span :class="user.status === 0 ? 'text-emerald-400' : 'text-slate-500'">{{ user.status === 0 ? '正常' : '已冻结' }}</span>
            </span>
            <!-- 余额 -->
            <span class="text-sm font-medium" :class="(user.balance || 0) > 0 ? 'text-emerald-400' : 'text-slate-500'">
              ¥{{ (user.balance || 0).toFixed(2) }}
            </span>
            <!-- Tokens -->
            <span class="text-sm text-slate-300">{{ (user.totalUsageTokens || 0).toLocaleString() }}</span>
            <!-- 注册时间 -->
            <span class="text-sm text-slate-500">{{ relativeTime(user.createdAt) }}</span>
            <!-- 操作 -->
            <div class="flex items-center justify-end gap-1">
              <button @click="openDetail(user)" class="p-1.5 rounded-lg text-slate-400 hover:text-amber-400 hover:bg-amber-500/10 transition-colors" title="使用明细">
                <BarChart3 class="w-4 h-4" />
              </button>
              <button @click="openRecharge(user)" class="p-1.5 rounded-lg text-slate-400 hover:text-emerald-400 hover:bg-emerald-500/10 transition-colors" title="充值">
                <Wallet class="w-4 h-4" />
              </button>
            </div>
          </div>
        </div>

        <!-- 移动端卡片 -->
        <div class="md:hidden divide-y divide-white/5">
          <div v-for="user in pagedUsers" :key="'m-' + user.userId" class="px-4 py-3.5 space-y-2.5">
            <div class="flex items-center justify-between">
              <div class="flex items-center gap-2.5">
                <div class="w-8 h-8 rounded-full bg-gradient-to-br from-emerald-500 to-cyan-500 flex items-center justify-center text-white text-xs font-bold">
                  {{ (user.name || user.username || '?')[0] }}
                </div>
                <div>
                  <p class="text-sm text-white font-medium">{{ user.name || user.username }}</p>
                  <p class="text-xs text-slate-500">{{ user.email || '-' }}</p>
                </div>
              </div>
              <div class="flex items-center gap-1">
                <button @click="openDetail(user)" class="p-1.5 rounded-lg text-slate-400 hover:text-amber-400 hover:bg-amber-500/10 transition-colors">
                  <BarChart3 class="w-4 h-4" />
                </button>
                <button @click="openRecharge(user)" class="p-1.5 rounded-lg text-slate-400 hover:text-emerald-400 hover:bg-emerald-500/10 transition-colors">
                  <Wallet class="w-4 h-4" />
                </button>
              </div>
            </div>
            <div class="flex items-center gap-3 text-xs">
              <span :class="['px-1.5 py-0.5 rounded-full border', user.role === 0 ? 'bg-purple-500/10 text-purple-400 border-purple-500/20' : 'bg-blue-500/10 text-blue-400 border-blue-500/20']">
                {{ user.role === 0 ? '管理员' : '用户' }}
              </span>
              <span class="flex items-center gap-1">
                <span class="w-1.5 h-1.5 rounded-full" :class="user.status === 0 ? 'bg-emerald-400' : 'bg-slate-500'"></span>
                <span :class="user.status === 0 ? 'text-emerald-400' : 'text-slate-500'">{{ user.status === 0 ? '正常' : '已冻结' }}</span>
              </span>
              <span class="text-slate-400">{{ relativeTime(user.createdAt) }}</span>
            </div>
            <div class="flex items-center gap-4 text-xs">
              <span class="text-slate-400">余额 <span :class="(user.balance || 0) > 0 ? 'text-emerald-400 font-medium' : 'text-slate-500'">¥{{ (user.balance || 0).toFixed(2) }}</span></span>
              <span class="text-slate-400">Token <span class="text-slate-300">{{ (user.totalUsageTokens || 0).toLocaleString() }}</span></span>
            </div>
          </div>
        </div>
      </template>
    </div>

    <!-- 分页 -->
    <div v-if="filteredUsers.length > pageSize" class="flex items-center justify-between">
      <span class="text-xs text-slate-500">共 {{ filteredUsers.length }} 个用户</span>
      <div class="flex items-center gap-1">
        <button @click="goToPage(currentPage - 1)" :disabled="currentPage === 1"
          class="p-1.5 rounded-lg bg-white/5 text-slate-400 hover:bg-white/10 hover:text-white disabled:opacity-30 disabled:cursor-not-allowed transition-colors">
          <ChevronLeft class="w-4 h-4" />
        </button>
        <template v-for="p in totalPages" :key="p">
          <button @click="goToPage(p)"
            :class="['w-7 h-7 rounded-lg text-xs font-medium transition-colors', p === currentPage ? 'bg-emerald-500/20 text-emerald-400' : 'bg-white/5 text-slate-400 hover:bg-white/10 hover:text-white']">
            {{ p }}
          </button>
        </template>
        <button @click="goToPage(currentPage + 1)" :disabled="currentPage === totalPages"
          class="p-1.5 rounded-lg bg-white/5 text-slate-400 hover:bg-white/10 hover:text-white disabled:opacity-30 disabled:cursor-not-allowed transition-colors">
          <ChevronRight class="w-4 h-4" />
        </button>
      </div>
    </div>

    <!-- 用户详情弹窗 -->
    <div v-if="showDetailModal" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm" @click.self="showDetailModal = false">
      <div class="glass-card rounded-2xl w-full max-w-5xl p-6 animate-slide-up flex flex-col max-h-[85vh]">
        <!-- 头部 -->
        <div class="flex items-center justify-between pb-4 border-b border-white/5">
          <div class="flex items-center gap-3">
            <div class="w-10 h-10 rounded-full bg-gradient-to-br from-emerald-500 to-cyan-500 flex items-center justify-center text-white font-bold">
              {{ (detailUser?.name || detailUser?.username || '?')[0] }}
            </div>
            <div>
              <h2 class="text-lg font-bold text-white">{{ detailUser?.name || detailUser?.username }}</h2>
              <p class="text-sm text-slate-400">{{ detailUser?.email || '-' }}</p>
            </div>
          </div>
          <button @click="showDetailModal = false" class="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-white/5 transition-colors">
            <X class="w-5 h-5" />
          </button>
        </div>

        <!-- 汇总统计 -->
        <div class="grid grid-cols-5 gap-3 py-4">
          <div class="glass-card rounded-xl p-3 text-center">
            <p class="text-xs text-slate-400 mb-1">余额</p>
            <p class="text-base font-semibold text-emerald-400">¥{{ (detailUser?.balance || 0).toFixed(2) }}</p>
          </div>
          <div class="glass-card rounded-xl p-3 text-center">
            <p class="text-xs text-slate-400 mb-1">调用次数</p>
            <p class="text-base font-semibold text-white">{{ usageSummary.count.toLocaleString() }}</p>
          </div>
          <div class="glass-card rounded-xl p-3 text-center">
            <p class="text-xs text-slate-400 mb-1">输入 Token</p>
            <p class="text-base font-semibold text-cyan-400">{{ usageSummary.totalInput.toLocaleString() }}</p>
          </div>
          <div class="glass-card rounded-xl p-3 text-center">
            <p class="text-xs text-slate-400 mb-1">输出 Token</p>
            <p class="text-base font-semibold text-amber-400">{{ usageSummary.totalOutput.toLocaleString() }}</p>
          </div>
          <div class="glass-card rounded-xl p-3 text-center">
            <p class="text-xs text-slate-400 mb-1">总费用</p>
            <p class="text-base font-semibold text-emerald-400">¥{{ usageSummary.totalCost.toFixed(4) }}</p>
          </div>
        </div>

        <!-- Tab 切换 -->
        <div class="flex gap-1 border-b border-white/5">
          <button
            @click="detailTab = 'usage'"
            :class="['px-4 py-2.5 text-sm font-medium transition-colors border-b-2 -mb-px', detailTab === 'usage' ? 'text-emerald-400 border-emerald-400' : 'text-slate-400 border-transparent hover:text-slate-300']"
          >调用明细</button>
          <button
            @click="detailTab = 'apikeys'"
            :class="['px-4 py-2.5 text-sm font-medium transition-colors border-b-2 -mb-px', detailTab === 'apikeys' ? 'text-emerald-400 border-emerald-400' : 'text-slate-400 border-transparent hover:text-slate-300']"
          >API Keys</button>
        </div>

        <!-- 内容区 -->
        <div class="flex-1 overflow-y-auto mt-3">
          <div v-if="detailLoading" class="flex items-center justify-center py-8">
            <div class="w-5 h-5 border-2 border-emerald-500/30 border-t-emerald-500 rounded-full animate-spin"></div>
            <span class="ml-2 text-slate-400 text-sm">加载中...</span>
          </div>

          <!-- 调用明细 Tab -->
          <template v-else-if="detailTab === 'usage'">
            <div v-if="detailUsage.length === 0" class="text-center py-8 text-slate-500 text-sm">
              暂无调用记录
            </div>
            <table v-else class="w-full">
              <thead>
                <tr class="border-b border-white/5">
                  <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">时间</th>
                  <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">输入Token</th>
                  <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">输出Token</th>
                  <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">费用</th>
                  <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">详情</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-white/5">
                <tr v-for="record in detailUsage" :key="record.id" class="hover:bg-white/[0.02] transition-colors">
                  <td class="py-3 text-sm text-slate-300">{{ record.createdAt ? new Date(record.createdAt).toLocaleString() : '-' }}</td>
                  <td class="py-3 text-sm text-cyan-400">{{ (record.inputTokens || 0).toLocaleString() }}</td>
                  <td class="py-3 text-sm text-amber-400">{{ (record.outputTokens || 0).toLocaleString() }}</td>
                  <td class="py-3 text-sm text-emerald-400">¥{{ formatAmount(record.amount) }}</td>
                  <td class="py-3 text-sm text-slate-400 max-w-[250px] truncate" :title="record.detail">{{ record.detail || '-' }}</td>
                </tr>
              </tbody>
            </table>
          </template>

          <!-- API Keys Tab -->
          <template v-else-if="detailTab === 'apikeys'">
            <div v-if="detailApiKeys.length === 0" class="text-center py-8 text-slate-500 text-sm">
              该用户暂无 API Key
            </div>
            <table v-else class="w-full">
              <thead>
                <tr class="border-b border-white/5">
                  <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">名称</th>
                  <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">API Key</th>
                  <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">调用次数</th>
                  <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">输入Token</th>
                  <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">输出Token</th>
                  <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">总费用</th>
                  <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">状态</th>
                  <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">最后使用</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-white/5">
                <tr v-for="key in detailApiKeys" :key="key.id" class="hover:bg-white/[0.02] transition-colors">
                  <td class="py-3">
                    <div class="flex items-center gap-2">
                      <KeyRound class="w-4 h-4 text-emerald-400" />
                      <span class="text-sm text-white">{{ key.name }}</span>
                    </div>
                  </td>
                  <td class="py-3">
                    <code class="text-sm text-emerald-400 font-mono bg-emerald-500/5 px-2 py-0.5 rounded">
                      {{ key.apiKey?.slice(0, 12) }}...{{ key.apiKey?.slice(-4) }}
                    </code>
                  </td>
                  <td class="py-3 text-sm text-slate-300">{{ (key.totalCalls || 0).toLocaleString() }}</td>
                  <td class="py-3 text-sm text-cyan-400">{{ (key.totalInputTokens || 0).toLocaleString() }}</td>
                  <td class="py-3 text-sm text-amber-400">{{ (key.totalOutputTokens || 0).toLocaleString() }}</td>
                  <td class="py-3 text-sm text-emerald-400">¥{{ key.totalCost ? Number(key.totalCost).toFixed(4) : '0.0000' }}</td>
                  <td class="py-3">
                    <span :class="['px-2 py-0.5 text-xs rounded-full border', key.status === 0 ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20' : 'bg-slate-500/10 text-slate-400 border-slate-500/20']">
                      {{ key.status === 0 ? '可用' : '禁用' }}
                    </span>
                  </td>
                  <td class="py-3 text-sm text-slate-400">{{ key.lastUsedAt ? new Date(key.lastUsedAt).toLocaleString() : '从未' }}</td>
                </tr>
              </tbody>
            </table>
          </template>
        </div>
      </div>
    </div>

    <!-- 充值弹窗 -->
    <div v-if="showRechargeModal" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm" @click.self="showRechargeModal = false">
      <div class="glass-card rounded-2xl w-full max-w-md p-6 animate-slide-up">
        <!-- 头部 -->
        <div class="flex items-center justify-between pb-4 border-b border-white/5">
          <h2 class="text-lg font-bold text-white">充值 - {{ rechargeForm.userName }}</h2>
          <button @click="showRechargeModal = false" class="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-white/5 transition-colors">
            <X class="w-5 h-5" />
          </button>
        </div>

        <!-- 金额 -->
        <div class="mt-4 mb-4">
          <label class="block text-sm text-slate-400 mb-1.5">充值金额</label>
          <input v-model.number="rechargeForm.amount" type="number" min="1" step="0.01" class="input-field" placeholder="请输入充值金额" />
        </div>

        <!-- 支付方式 -->
        <div class="mb-4">
          <label class="block text-sm text-slate-400 mb-2">支付方式</label>
          <div class="flex gap-3">
            <label class="flex items-center gap-2 cursor-pointer">
              <input v-model="rechargeForm.paymentMethod" type="radio" value="alipay" class="accent-emerald-500" />
              <span class="text-sm text-slate-300">支付宝</span>
            </label>
            <label class="flex items-center gap-2 cursor-pointer">
              <input v-model="rechargeForm.paymentMethod" type="radio" value="wechat" class="accent-emerald-500" />
              <span class="text-sm text-slate-300">微信支付</span>
            </label>
            <label class="flex items-center gap-2 cursor-pointer">
              <input v-model="rechargeForm.paymentMethod" type="radio" value="admin" class="accent-emerald-500" />
              <span class="text-sm text-slate-300">后台充值</span>
            </label>
          </div>
        </div>

        <!-- 备注 -->
        <div class="mb-5">
          <label class="block text-sm text-slate-400 mb-1.5">备注</label>
          <input v-model="rechargeForm.remark" type="text" class="input-field" placeholder="可选，填写备注信息" />
        </div>

        <!-- 按钮 -->
        <div class="flex justify-end gap-3">
          <button @click="showRechargeModal = false" class="btn-secondary">取消</button>
          <button @click="doRecharge" class="px-5 py-2 rounded-lg bg-gradient-to-r from-emerald-500 to-cyan-500 text-white text-sm font-medium hover:from-emerald-400 hover:to-cyan-400 transition-all shadow-lg shadow-emerald-500/20">
            确认充值
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
