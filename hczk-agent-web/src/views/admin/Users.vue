<script setup>
import { ref, onMounted, computed } from 'vue'
import { useApiStore } from '@/stores/api'
import { Users, Search, Filter, Wallet, BarChart3, KeyRound, X, ChevronLeft, ChevronRight } from 'lucide-vue-next'

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

/** 打开用户详情弹窗 */
async function openDetail(user) {
  detailUser.value = user
  detailUsage.value = []
  detailApiKeys.value = []
  detailTab.value = 'usage'
  detailLoading.value = true
  showDetailModal.value = true

  const [usageRes, keysRes] = await Promise.all([
    api.get(`/users/${user.id}/usage`),
    api.get(`/users/${user.id}/api-keys`)
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
  rechargeForm.value = { userId: user.id, userName: user.name || user.username, amount: null, paymentMethod: 'admin', remark: '' }
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
  <div class="space-y-6 animate-fade-in">
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-2xl font-bold text-white">用户管理</h1>
        <p class="text-slate-400 mt-1">管理平台所有注册用户，查看用量与余额</p>
      </div>
    </div>

    <!-- Search & Filter -->
    <div class="flex gap-4">
      <div class="flex-1 relative">
        <Search class="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-500" />
        <input v-model="searchQuery" class="input-field pl-10" placeholder="搜索用户名称或邮箱..." />
      </div>
      <button class="btn-secondary flex items-center gap-2">
        <Filter class="w-4 h-4" /> 筛选
      </button>
    </div>

    <!-- Users Table -->
    <div class="glass-card p-6">
      <div v-if="loading" class="flex items-center justify-center py-12">
        <div class="w-6 h-6 border-2 border-emerald-500/30 border-t-emerald-500 rounded-full animate-spin"></div>
        <span class="ml-3 text-slate-400">加载中...</span>
      </div>
      <div v-else-if="filteredUsers.length === 0" class="flex flex-col items-center justify-center py-12 text-slate-500">
        <Users class="w-12 h-12 mb-3 opacity-30" />
        <p>暂无用户</p>
      </div>
      <div v-else class="overflow-x-auto">
        <table class="w-full">
          <thead>
            <tr class="border-b border-white/5">
              <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">用户</th>
              <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">邮箱</th>
              <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">手机号</th>
              <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">公司</th>
              <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">角色</th>
              <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">状态</th>
              <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">余额</th>
              <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">总Token</th>
              <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">注册时间</th>
              <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">操作</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-white/5">
            <tr v-for="user in pagedUsers" :key="user.id" class="hover:bg-white/5 transition-colors">
              <td class="py-4">
                <div class="flex items-center gap-3">
                  <div class="w-9 h-9 rounded-full bg-gradient-to-br from-emerald-500 to-cyan-500 flex items-center justify-center text-white text-sm font-bold">
                    {{ (user.name || user.username || '?')[0] }}
                  </div>
                  <span class="text-sm text-white">{{ user.name || user.username }}</span>
                </div>
              </td>
              <td class="py-4 text-sm text-slate-300">{{ user.email || '-' }}</td>
              <td class="py-4 text-sm text-slate-300">{{ user.phoneNumber || '-' }}</td>
              <td class="py-4 text-sm text-slate-300">{{ user.companyName || '-' }}</td>
              <td class="py-4">
                <span :class="['px-2 py-0.5 text-xs rounded-full border', user.role === 0 ? 'bg-purple-500/10 text-purple-400 border-purple-500/20' : 'bg-blue-500/10 text-blue-400 border-blue-500/20']">
                  {{ user.role === 0 ? '管理员' : '用户' }}
                </span>
              </td>
              <td class="py-4">
                <span :class="['px-2 py-1 text-xs rounded-full border', user.status === 0 ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20' : 'bg-slate-500/10 text-slate-400 border-slate-500/20']">
                  {{ user.status === 0 ? '正常' : '已冻结' }}
                </span>
              </td>
              <td class="py-4">
                <span class="text-sm font-medium" :class="(user.balance || 0) > 0 ? 'text-emerald-400' : 'text-slate-400'">
                  ¥{{ (user.balance || 0).toFixed(2) }}
                </span>
              </td>
              <td class="py-4 text-sm text-slate-300">{{ (user.totalUsageTokens || 0).toLocaleString() }}</td>
              <td class="py-4 text-sm text-slate-400">{{ user.createdAt ? new Date(user.createdAt).toLocaleDateString() : '-' }}</td>
              <td class="py-4">
                <div class="flex items-center gap-2">
                  <button @click="openDetail(user)" class="p-2 rounded-lg bg-white/5 text-amber-400 hover:bg-amber-500/10" title="使用明细">
                    <BarChart3 class="w-4 h-4" />
                  </button>
                  <button @click="openRecharge(user)" class="p-2 rounded-lg bg-white/5 text-emerald-400 hover:bg-emerald-500/10" title="充值">
                    <Wallet class="w-4 h-4" />
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- 分页控件 -->
    <div v-if="filteredUsers.length > pageSize" class="flex items-center justify-between pt-2">
      <span class="text-sm text-slate-400">共 {{ filteredUsers.length }} 个用户</span>
      <div class="flex items-center gap-1">
        <button @click="goToPage(currentPage - 1)" :disabled="currentPage === 1"
          class="p-2 rounded-lg bg-white/5 text-slate-300 hover:bg-white/10 disabled:opacity-30 disabled:cursor-not-allowed transition-colors">
          <ChevronLeft class="w-4 h-4" />
        </button>
        <template v-for="p in totalPages" :key="p">
          <button @click="goToPage(p)"
            :class="['w-8 h-8 rounded-lg text-sm transition-colors', p === currentPage ? 'bg-emerald-500/20 text-emerald-400' : 'bg-white/5 text-slate-300 hover:bg-white/10']">
            {{ p }}
          </button>
        </template>
        <button @click="goToPage(currentPage + 1)" :disabled="currentPage === totalPages"
          class="p-2 rounded-lg bg-white/5 text-slate-300 hover:bg-white/10 disabled:opacity-30 disabled:cursor-not-allowed transition-colors">
          <ChevronRight class="w-4 h-4" />
        </button>
      </div>
    </div>

    <!-- 用户详情弹窗 -->
    <div v-if="showDetailModal" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm" @click.self="showDetailModal = false">
      <div class="glass-card w-full max-w-5xl p-6 animate-slide-up flex flex-col max-h-[85vh]">
        <!-- 头部 -->
        <div class="flex items-center justify-between mb-4">
          <div class="flex items-center gap-3">
            <div class="w-10 h-10 rounded-full bg-gradient-to-br from-emerald-500 to-cyan-500 flex items-center justify-center text-white font-bold">
              {{ (detailUser?.name || detailUser?.username || '?')[0] }}
            </div>
            <div>
              <h2 class="text-xl font-bold text-white">{{ detailUser?.name || detailUser?.username }}</h2>
              <p class="text-sm text-slate-400">{{ detailUser?.email || '-' }}</p>
            </div>
          </div>
          <button @click="showDetailModal = false" class="p-1 text-slate-400 hover:text-white">
            <X class="w-5 h-5" />
          </button>
        </div>

        <!-- 汇总统计 -->
        <div class="grid grid-cols-5 gap-3 mb-4">
          <div class="bg-slate-800/50 rounded-lg p-3 border border-white/5 text-center">
            <p class="text-xs text-slate-400 mb-1">余额</p>
            <p class="text-base font-semibold text-emerald-400">¥{{ (detailUser?.balance || 0).toFixed(2) }}</p>
          </div>
          <div class="bg-slate-800/50 rounded-lg p-3 border border-white/5 text-center">
            <p class="text-xs text-slate-400 mb-1">调用次数</p>
            <p class="text-base font-semibold text-white">{{ usageSummary.count.toLocaleString() }}</p>
          </div>
          <div class="bg-slate-800/50 rounded-lg p-3 border border-white/5 text-center">
            <p class="text-xs text-slate-400 mb-1">输入 Token</p>
            <p class="text-base font-semibold text-cyan-400">{{ usageSummary.totalInput.toLocaleString() }}</p>
          </div>
          <div class="bg-slate-800/50 rounded-lg p-3 border border-white/5 text-center">
            <p class="text-xs text-slate-400 mb-1">输出 Token</p>
            <p class="text-base font-semibold text-amber-400">{{ usageSummary.totalOutput.toLocaleString() }}</p>
          </div>
          <div class="bg-slate-800/50 rounded-lg p-3 border border-white/5 text-center">
            <p class="text-xs text-slate-400 mb-1">总费用</p>
            <p class="text-base font-semibold text-emerald-400">¥{{ usageSummary.totalCost.toFixed(4) }}</p>
          </div>
        </div>

        <!-- Tab 切换 -->
        <div class="flex gap-1 mb-4 border-b border-white/5 pb-0">
          <button
            @click="detailTab = 'usage'"
            :class="['px-4 py-2 text-sm font-medium transition-colors border-b-2 -mb-px', detailTab === 'usage' ? 'text-emerald-400 border-emerald-400' : 'text-slate-400 border-transparent hover:text-slate-300']"
          >调用明细</button>
          <button
            @click="detailTab = 'apikeys'"
            :class="['px-4 py-2 text-sm font-medium transition-colors border-b-2 -mb-px', detailTab === 'apikeys' ? 'text-emerald-400 border-emerald-400' : 'text-slate-400 border-transparent hover:text-slate-300']"
          >API Keys</button>
        </div>

        <!-- 内容区 -->
        <div class="flex-1 overflow-y-auto">
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
                <tr v-for="record in detailUsage" :key="record.id" class="hover:bg-white/5 transition-colors">
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
                <tr v-for="key in detailApiKeys" :key="key.id" class="hover:bg-white/5 transition-colors">
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
      <div class="glass-card w-full max-w-md p-6 animate-slide-up">
        <!-- 头部 -->
        <div class="flex items-center justify-between mb-5">
          <h2 class="text-lg font-bold text-white">充值 - {{ rechargeForm.userName }}</h2>
          <button @click="showRechargeModal = false" class="p-1 text-slate-400 hover:text-white">
            <X class="w-5 h-5" />
          </button>
        </div>

        <!-- 金额 -->
        <div class="mb-4">
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
          <button @click="doRecharge" class="btn-primary">确认充值</button>
        </div>
      </div>
    </div>
  </div>
</template>
