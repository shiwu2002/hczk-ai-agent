<!--
  计费管理页面（管理员）
  功能：展示收入/消耗统计、账单流水列表，支持类型筛选和分页
-->
<script setup>
import { ref, onMounted, computed } from 'vue'
import { useApiStore } from '@/stores/api'
import { Receipt, TrendingUp, Wallet, Zap, ChevronLeft, ChevronRight } from 'lucide-vue-next'

const api = useApiStore()

// ========== 状态变量 ==========
const loading = ref(false)
const billingRecords = ref([])
const users = ref([])
const typeFilter = ref('all')

// ========== 数据加载 ==========
onMounted(loadData)

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

function getUserName(userId) {
  const user = users.value.find(u => u.userId === userId)
  return user ? (user.name || user.username) : `用户${userId}`
}

/** 格式化 Token 数量为 K 单位 */
function formatTokens(count) {
  if (count == null) return '-'
  const k = count / 1000
  return k >= 1 ? `${Math.round(k)}K` : String(count)
}

/** 相对时间格式 */
function relativeTime(createdAt) {
  if (!createdAt) return '-'
  const now = Date.now()
  const diff = now - new Date(createdAt).getTime()
  const seconds = Math.floor(diff / 1000)
  if (seconds < 60) return '刚刚'
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `${minutes}分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}小时前`
  const days = Math.floor(hours / 24)
  if (days < 30) return `${days}天前`
  const months = Math.floor(days / 30)
  if (months < 12) return `${months}个月前`
  return `${Math.floor(months / 12)}年前`
}

// ========== 筛选与分页 ==========

const filteredRecords = computed(() => {
  if (typeFilter.value === 'all') return billingRecords.value
  return billingRecords.value.filter(r => r.type === typeFilter.value)
})

const currentPage = ref(1)
const pageSize = ref(10)
const totalPages = computed(() => Math.max(1, Math.ceil(filteredRecords.value.length / pageSize.value)))
const pagedRecords = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredRecords.value.slice(start, start + pageSize.value)
})
function goToPage(page) {
  if (page >= 1 && page <= totalPages.value) currentPage.value = page
}

/** 筛选切换时重置页码 */
function setFilter(val) {
  typeFilter.value = val
  currentPage.value = 1
}

// ========== 统计数据 ==========

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

/** 筛选按钮配置 */
const filterOptions = [
  { value: 'all', label: '全部' },
  { value: 'RECHARGE', label: '充值' },
  { value: 'TOKEN_USAGE', label: '消耗' }
]
</script>

<template>
  <div class="space-y-5 animate-fade-in">
    <!-- 统计卡片 -->
    <div class="grid grid-cols-2 lg:grid-cols-4 gap-3">
      <div class="glass-card rounded-xl p-3.5">
        <div class="flex items-center gap-3">
          <div class="w-8 h-8 rounded-lg bg-emerald-500/10 flex items-center justify-center shrink-0">
            <Receipt class="w-4 h-4 text-emerald-400" />
          </div>
          <div class="min-w-0">
            <p class="text-xs text-slate-400">今日收入</p>
            <p class="text-base font-bold text-emerald-400 truncate">¥{{ stats.todayIncome.toFixed(2) }}</p>
          </div>
        </div>
      </div>
      <div class="glass-card rounded-xl p-3.5">
        <div class="flex items-center gap-3">
          <div class="w-8 h-8 rounded-lg bg-blue-500/10 flex items-center justify-center shrink-0">
            <TrendingUp class="w-4 h-4 text-blue-400" />
          </div>
          <div class="min-w-0">
            <p class="text-xs text-slate-400">本月收入</p>
            <p class="text-base font-bold text-blue-400 truncate">¥{{ stats.monthIncome.toFixed(2) }}</p>
          </div>
        </div>
      </div>
      <div class="glass-card rounded-xl p-3.5">
        <div class="flex items-center gap-3">
          <div class="w-8 h-8 rounded-lg bg-emerald-500/10 flex items-center justify-center shrink-0">
            <Wallet class="w-4 h-4 text-emerald-400" />
          </div>
          <div class="min-w-0">
            <p class="text-xs text-slate-400">总充值</p>
            <p class="text-base font-bold text-emerald-400 truncate">¥{{ stats.totalRecharge.toFixed(2) }}</p>
          </div>
        </div>
      </div>
      <div class="glass-card rounded-xl p-3.5">
        <div class="flex items-center gap-3">
          <div class="w-8 h-8 rounded-lg bg-orange-500/10 flex items-center justify-center shrink-0">
            <Zap class="w-4 h-4 text-orange-400" />
          </div>
          <div class="min-w-0">
            <p class="text-xs text-slate-400">总消耗</p>
            <p class="text-base font-bold text-orange-400 truncate">¥{{ stats.totalUsage.toFixed(2) }}</p>
          </div>
        </div>
      </div>
    </div>

    <!-- 账单流水 -->
    <div class="glass-card rounded-2xl p-5">
      <!-- 筛选栏 -->
      <div class="flex items-center gap-2 mb-4">
        <button
          v-for="opt in filterOptions" :key="opt.value"
          @click="setFilter(opt.value)"
          :class="[
            'px-3.5 py-1.5 text-xs font-medium rounded-full transition-colors',
            typeFilter === opt.value
              ? 'bg-emerald-500/15 text-emerald-400 ring-1 ring-emerald-500/30'
              : 'bg-white/5 text-slate-400 hover:bg-white/10 hover:text-slate-300'
          ]"
        >
          {{ opt.label }}
        </button>
      </div>

      <!-- 加载中 -->
      <div v-if="loading" class="flex items-center justify-center py-16">
        <div class="w-6 h-6 border-2 border-emerald-500/30 border-t-emerald-500 rounded-full animate-spin"></div>
      </div>

      <!-- 桌面端表格 -->
      <div v-else class="hidden sm:block overflow-x-auto">
        <!-- 表头 -->
        <div class="grid grid-cols-[1fr_64px_80px_80px_64px_1fr_72px] gap-3 px-3 py-2 text-xs font-medium text-slate-400 border-b border-white/5">
          <span>用户</span>
          <span>类型</span>
          <span>Tokens</span>
          <span>金额</span>
          <span>余额</span>
          <span>详情</span>
          <span>时间</span>
        </div>
        <!-- 数据行 -->
        <div
          v-for="record in pagedRecords" :key="record.id"
          class="grid grid-cols-[1fr_64px_80px_80px_64px_1fr_72px] gap-3 items-center px-3 py-3 border-b border-white/5 hover:bg-white/[0.02] transition-colors"
        >
          <span class="text-sm text-white truncate">{{ getUserName(record.userId) }}</span>
          <span>
            <span :class="[
              'inline-block px-2 py-0.5 text-[11px] font-medium rounded-full',
              record.type === 'RECHARGE' ? 'bg-emerald-500/10 text-emerald-400' : 'bg-orange-500/10 text-orange-400'
            ]">
              {{ record.type === 'RECHARGE' ? '充值' : '消耗' }}
            </span>
          </span>
          <span class="text-sm text-slate-300">
            <template v-if="record.type === 'TOKEN_USAGE'">
              {{ formatTokens(record.inputTokens) }} / {{ formatTokens(record.outputTokens) }}
            </template>
            <template v-else>-</template>
          </span>
          <span class="text-sm font-medium" :class="(record.amount || 0) > 0 ? 'text-emerald-400' : 'text-orange-400'">
            {{ (record.amount || 0) > 0 ? '+' : '' }}{{ (record.amount || 0).toFixed(2) }}
          </span>
          <span class="text-sm text-slate-300">¥{{ (record.balanceAfter ?? record.balance ?? 0).toFixed(2) }}</span>
          <span class="text-sm text-slate-400 truncate" :title="record.detail || ''">{{ record.detail || '-' }}</span>
          <span class="text-xs text-slate-500">{{ relativeTime(record.createdAt) }}</span>
        </div>
        <div v-if="filteredRecords.length === 0" class="py-12 text-center text-sm text-slate-500">暂无账单记录</div>
      </div>

      <!-- 移动端卡片列表 -->
      <div v-if="!loading" class="sm:hidden space-y-3">
        <div
          v-for="record in pagedRecords" :key="record.id"
          class="rounded-xl bg-white/[0.03] border border-white/5 p-3.5 space-y-2"
        >
          <div class="flex items-center justify-between">
            <span class="text-sm font-medium text-white">{{ getUserName(record.userId) }}</span>
            <span :class="[
              'px-2 py-0.5 text-[11px] font-medium rounded-full',
              record.type === 'RECHARGE' ? 'bg-emerald-500/10 text-emerald-400' : 'bg-orange-500/10 text-orange-400'
            ]">
              {{ record.type === 'RECHARGE' ? '充值' : '消耗' }}
            </span>
          </div>
          <div class="flex items-center justify-between text-sm">
            <span class="text-slate-400">Tokens</span>
            <span class="text-slate-300">
              <template v-if="record.type === 'TOKEN_USAGE'">
                {{ formatTokens(record.inputTokens) }} / {{ formatTokens(record.outputTokens) }}
              </template>
              <template v-else>-</template>
            </span>
          </div>
          <div class="flex items-center justify-between text-sm">
            <span class="text-slate-400">金额</span>
            <span class="font-medium" :class="(record.amount || 0) > 0 ? 'text-emerald-400' : 'text-orange-400'">
              {{ (record.amount || 0) > 0 ? '+' : '' }}{{ (record.amount || 0).toFixed(2) }}
            </span>
          </div>
          <div class="flex items-center justify-between text-sm">
            <span class="text-slate-400">余额</span>
            <span class="text-slate-300">¥{{ (record.balanceAfter ?? record.balance ?? 0).toFixed(2) }}</span>
          </div>
          <div v-if="record.detail" class="flex items-center justify-between text-sm">
            <span class="text-slate-400">详情</span>
            <span class="text-slate-400 truncate max-w-[60%]" :title="record.detail">{{ record.detail }}</span>
          </div>
          <div class="flex items-center justify-between text-xs">
            <span class="text-slate-500">{{ relativeTime(record.createdAt) }}</span>
          </div>
        </div>
        <div v-if="filteredRecords.length === 0" class="py-12 text-center text-sm text-slate-500">暂无账单记录</div>
      </div>
    </div>

    <!-- 分页 -->
    <div v-if="filteredRecords.length > pageSize" class="flex items-center justify-between px-1">
      <span class="text-xs text-slate-500">共 {{ filteredRecords.length }} 条</span>
      <div class="flex items-center gap-1">
        <button @click="goToPage(currentPage - 1)" :disabled="currentPage === 1"
          class="p-1.5 rounded-lg bg-white/5 text-slate-400 hover:bg-white/10 hover:text-slate-300 disabled:opacity-30 disabled:cursor-not-allowed transition-colors">
          <ChevronLeft class="w-3.5 h-3.5" />
        </button>
        <template v-for="p in totalPages" :key="p">
          <button v-if="totalPages <= 7 || Math.abs(p - currentPage) <= 2 || p === 1 || p === totalPages"
            @click="goToPage(p)"
            :class="[
              'w-7 h-7 rounded-lg text-xs transition-colors',
              p === currentPage ? 'bg-emerald-500/20 text-emerald-400' : 'bg-white/5 text-slate-400 hover:bg-white/10 hover:text-slate-300'
            ]">
            {{ p }}
          </button>
          <span v-else-if="p === currentPage - 3 || p === currentPage + 3" class="text-xs text-slate-600 px-0.5">…</span>
        </template>
        <button @click="goToPage(currentPage + 1)" :disabled="currentPage === totalPages"
          class="p-1.5 rounded-lg bg-white/5 text-slate-400 hover:bg-white/10 hover:text-slate-300 disabled:opacity-30 disabled:cursor-not-allowed transition-colors">
          <ChevronRight class="w-3.5 h-3.5" />
        </button>
      </div>
    </div>
  </div>
</template>
