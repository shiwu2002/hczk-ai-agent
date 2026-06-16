<!--
  计费管理页面（管理员）
  功能：展示收入/消耗统计、账单流水列表，支持类型筛选和分页
-->
<script setup>
import { ref, onMounted, computed } from 'vue'
import { useApiStore } from '@/stores/api'
import { Receipt, TrendingUp, ArrowDownUp, Wallet, Zap, ChevronLeft, ChevronRight } from 'lucide-vue-next'

const api = useApiStore()

// ========== 状态变量 ==========
const loading = ref(false)              // 加载状态
const billingRecords = ref([])          // 所有账单记录
const users = ref([])                   // 用户列表（用于显示用户名）
const typeFilter = ref('all')           // 类型筛选：'all' | 'RECHARGE' | 'TOKEN_USAGE'

// ========== 数据加载 ==========
onMounted(loadData)

/** 并行加载账单记录和用户列表 */
async function loadData() {
  loading.value = true
  const [billRes, usersRes] = await Promise.all([
    api.get('/billing/records'),
    api.get('/users')
  ])
  if (billRes.code === 200) billingRecords.value = billRes.data || []
  if (usersRes.code === 200) users.value = usersRes.data || []
  loading.value = false
}

// ========== 辅助函数 ==========

/** 根据用户ID获取用户名 */
function getUserName(userId) {
  const user = users.value.find(u => u.userId === userId)
  return user ? (user.name || user.username) : `用户${userId}`
}

/** 格式化时间为 YYYY-MM-DD HH:mm:ss */
function formatTime(createdAt) {
  if (!createdAt) return '-'
  const d = new Date(createdAt)
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

// ========== 筛选与分页 ==========

/** 按类型筛选后的记录 */
const filteredRecords = computed(() => {
  if (typeFilter.value === 'all') return billingRecords.value
  return billingRecords.value.filter(r => r.type === typeFilter.value)
})

const currentPage = ref(1)
const pageSize = ref(15)
const totalPages = computed(() => Math.max(1, Math.ceil(filteredRecords.value.length / pageSize.value)))
/** 当前页的记录列表 */
const pagedRecords = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredRecords.value.slice(start, start + pageSize.value)
})
function goToPage(page) {
  if (page >= 1 && page <= totalPages.value) currentPage.value = page
}

// ========== 统计数据 ==========

/** 计算今日收入、本月收入、总充值、总消耗 */
const stats = computed(() => {
  const now = new Date()
  const today = now.toDateString()
  const thisMonth = now.getMonth()
  const thisYear = now.getFullYear()

  let todayIncome = 0, monthIncome = 0, totalRecharge = 0, totalUsage = 0
  for (const r of billingRecords.value) {
    const amt = Number(r.amount || 0)
    const d = r.createdAt ? new Date(r.createdAt) : null
    if (r.type === 'RECHARGE') {
      totalRecharge += amt
      if (d) {
        if (d.toDateString() === today) todayIncome += amt
        if (d.getMonth() === thisMonth && d.getFullYear() === thisYear) monthIncome += amt
      }
    } else {
      totalUsage += Math.abs(amt)
    }
  }
  return { todayIncome, monthIncome, totalRecharge, totalUsage }
})
</script>

<template>
  <div class="space-y-6 animate-fade-in">
    <!-- 页面标题 -->
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-2xl font-bold text-white">计费管理</h1>
        <p class="text-slate-400 mt-1">Token 消耗计费与充值记录</p>
      </div>
    </div>

    <!-- 统计卡片：今日收入、本月收入、总充值、总消耗 -->
    <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
      <div class="glass-card p-6">
        <div class="flex items-start justify-between">
          <div>
            <p class="text-sm text-slate-400">今日收入</p>
            <p class="text-2xl font-bold text-white mt-2">¥{{ stats.todayIncome.toFixed(2) }}</p>
          </div>
          <div class="w-10 h-10 rounded-lg bg-emerald-500/10 flex items-center justify-center">
            <Receipt class="w-5 h-5 text-emerald-400" />
          </div>
        </div>
      </div>
      <div class="glass-card p-6">
        <div class="flex items-start justify-between">
          <div>
            <p class="text-sm text-slate-400">本月收入</p>
            <p class="text-2xl font-bold text-white mt-2">¥{{ stats.monthIncome.toFixed(2) }}</p>
          </div>
          <div class="w-10 h-10 rounded-lg bg-blue-500/10 flex items-center justify-center">
            <TrendingUp class="w-5 h-5 text-blue-400" />
          </div>
        </div>
      </div>
      <div class="glass-card p-6">
        <div class="flex items-start justify-between">
          <div>
            <p class="text-sm text-slate-400">总充值</p>
            <p class="text-2xl font-bold text-white mt-2">¥{{ stats.totalRecharge.toFixed(2) }}</p>
          </div>
          <div class="w-10 h-10 rounded-lg bg-emerald-500/10 flex items-center justify-center">
            <Wallet class="w-5 h-5 text-emerald-400" />
          </div>
        </div>
      </div>
      <div class="glass-card p-6">
        <div class="flex items-start justify-between">
          <div>
            <p class="text-sm text-slate-400">总消耗</p>
            <p class="text-2xl font-bold text-white mt-2">¥{{ stats.totalUsage.toFixed(2) }}</p>
          </div>
          <div class="w-10 h-10 rounded-lg bg-orange-500/10 flex items-center justify-center">
            <Zap class="w-5 h-5 text-orange-400" />
          </div>
        </div>
      </div>
    </div>

    <!-- 账单流水表格 -->
    <div class="glass-card p-6">
      <div class="flex items-center justify-between mb-6">
        <h3 class="text-lg font-semibold text-white">账单流水</h3>
        <!-- 类型筛选 -->
        <div class="flex items-center gap-2">
          <ArrowDownUp class="w-4 h-4 text-slate-400" />
          <select v-model="typeFilter" class="input-field w-auto text-sm py-1.5">
            <option value="all">全部</option>
            <option value="RECHARGE">充值</option>
            <option value="TOKEN_USAGE">消耗</option>
          </select>
        </div>
      </div>

      <!-- 加载中 -->
      <div v-if="loading" class="flex items-center justify-center py-16">
        <div class="w-8 h-8 border-2 border-emerald-500/30 border-t-emerald-500 rounded-full animate-spin"></div>
        <span class="ml-3 text-slate-400">加载中...</span>
      </div>

      <!-- 账单表格 -->
      <div v-else class="overflow-x-auto">
        <table class="w-full">
          <thead>
            <tr class="border-b border-white/5">
              <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">用户</th>
              <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">类型</th>
              <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">输入Token</th>
              <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">输出Token</th>
              <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">金额</th>
              <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">余额</th>
              <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">详情</th>
              <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">时间</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-white/5">
            <tr v-for="record in pagedRecords" :key="record.id" class="hover:bg-white/5 transition-colors">
              <td class="py-4 text-sm text-white">{{ getUserName(record.userId) }}</td>
              <td class="py-4">
                <span :class="['px-2 py-1 text-xs rounded-full', record.type === 'RECHARGE' ? 'bg-emerald-500/10 text-emerald-400' : 'bg-orange-500/10 text-orange-400']">
                  {{ record.type === 'RECHARGE' ? '充值' : '消耗' }}
                </span>
              </td>
              <td class="py-4 text-sm text-slate-300">{{ record.type === 'TOKEN_USAGE' ? (record.inputTokens ?? '-') : '-' }}</td>
              <td class="py-4 text-sm text-slate-300">{{ record.type === 'TOKEN_USAGE' ? (record.outputTokens ?? '-') : '-' }}</td>
              <td class="py-4 text-sm font-medium" :class="(record.amount || 0) > 0 ? 'text-emerald-400' : 'text-orange-400'">
                {{ (record.amount || 0) > 0 ? '+' : '' }}{{ (record.amount || 0).toFixed(2) }}
              </td>
              <td class="py-4 text-sm text-slate-300">¥{{ (record.balanceAfter ?? record.balance ?? 0).toFixed(2) }}</td>
              <td class="py-4 text-sm text-slate-400">{{ record.detail || '-' }}</td>
              <td class="py-4 text-sm text-slate-500">{{ formatTime(record.createdAt) }}</td>
            </tr>
            <tr v-if="filteredRecords.length === 0">
              <td colspan="8" class="py-12 text-center text-slate-500">暂无账单记录</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- 分页控件 -->
    <div v-if="filteredRecords.length > pageSize" class="flex items-center justify-between pt-2">
      <span class="text-sm text-slate-400">共 {{ filteredRecords.length }} 条记录</span>
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
  </div>
</template>
