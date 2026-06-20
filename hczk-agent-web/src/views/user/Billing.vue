<script setup>
import { ref, onMounted, computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useApiStore } from '@/stores/api'
import { Wallet, Zap, CreditCard, Receipt, ChevronLeft, ChevronRight, FileText, ArrowUpRight, ArrowDownRight, TrendingUp, Calendar } from 'lucide-vue-next'

const authStore = useAuthStore()
const api = useApiStore()
const bills = ref([])
const loading = ref(false)
const typeFilter = ref('all')

const currentPage = ref(1)
const pageSize = 5

onMounted(async () => {
  loading.value = true
  const res = await api.get('/billing/my-records')
  if (res.code === 200) bills.value = res.data || []
  loading.value = false
})

// 统计
const billStats = computed(() => {
  let totalRecharge = 0, totalUsage = 0, rechargeCount = 0, usageCount = 0
  for (const b of bills.value) {
    const amt = Number(b.amount || 0)
    if (b.type === 'RECHARGE') { totalRecharge += amt; rechargeCount++ }
    else { totalUsage += Math.abs(amt); usageCount++ }
  }
  return { totalRecharge, totalUsage, rechargeCount, usageCount, balance: authStore.currentUser?.balance || 0 }
})

// 本月统计
const monthlyStats = computed(() => {
  const now = new Date()
  const monthStr = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
  let recharge = 0, usage = 0
  for (const b of bills.value) {
    if (b.createdAt && b.createdAt.startsWith(monthStr)) {
      const amt = Number(b.amount || 0)
      if (b.type === 'RECHARGE') recharge += amt
      else usage += Math.abs(amt)
    }
  }
  return { recharge, usage, net: recharge - usage }
})

// 筛选
const filteredBills = computed(() => {
  if (typeFilter.value === 'all') return bills.value
  return bills.value.filter(b => b.type === typeFilter.value)
})

const filterOptions = [
  { value: 'all', label: '全部' },
  { value: 'RECHARGE', label: '充值' },
  { value: 'TOKEN_USAGE', label: '消耗' }
]

// 分页
const totalPages = computed(() => Math.ceil(filteredBills.value.length / pageSize))
const pagedBills = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return filteredBills.value.slice(start, start + pageSize)
})

function goToPage(page) {
  if (page < 1 || page > totalPages.value) return
  currentPage.value = page
}

// 时间格式
function formatTime(dateStr) {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  const now = new Date()
  const diff = now - d
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'
  if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'
  if (diff < 172800000) return '昨天'
  return d.toLocaleDateString()
}
</script>

<template>
  <div class="space-y-6 animate-fade-in">
    <!-- Loading -->
    <div v-if="loading" class="flex items-center justify-center py-32">
      <div class="flex flex-col items-center gap-4">
        <div class="w-10 h-10 border-2 border-emerald-400/30 border-t-emerald-400 rounded-full animate-spin"></div>
        <p class="text-sm text-slate-400">加载中...</p>
      </div>
    </div>

    <template v-else>
      <!-- 余额卡片 + 本月概览 -->
      <div class="grid grid-cols-1 lg:grid-cols-3 gap-5">
        <!-- 余额卡片 -->
        <div class="relative rounded-2xl overflow-hidden">
          <div class="absolute inset-0 bg-gradient-to-br from-emerald-600/20 via-cyan-600/10 to-transparent"></div>
          <div class="relative p-6">
            <div class="flex items-center gap-2 mb-4">
              <Wallet class="w-5 h-5 text-emerald-400" />
              <span class="text-sm text-slate-300">账户余额</span>
            </div>
            <p class="text-3xl font-bold text-white mb-1">¥{{ Number(billStats.balance).toFixed(2) }}</p>
            <div class="flex items-center gap-4 mt-4">
              <div class="flex items-center gap-1.5">
                <ArrowUpRight class="w-3.5 h-3.5 text-emerald-400" />
                <span class="text-xs text-slate-400">充值 ¥{{ billStats.totalRecharge.toFixed(2) }}</span>
              </div>
              <div class="flex items-center gap-1.5">
                <ArrowDownRight class="w-3.5 h-3.5 text-orange-400" />
                <span class="text-xs text-slate-400">消耗 ¥{{ billStats.totalUsage.toFixed(2) }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 本月概览 -->
        <div class="lg:col-span-2 glass-card rounded-2xl p-5">
          <div class="flex items-center gap-2 mb-4">
            <Calendar class="w-4 h-4 text-cyan-400" />
            <h3 class="text-sm font-semibold text-white">本月概览</h3>
          </div>
          <div class="grid grid-cols-3 gap-4">
            <div class="p-3.5 rounded-xl bg-emerald-500/[0.06] border border-emerald-500/10">
              <p class="text-[11px] text-slate-400 mb-1">本月充值</p>
              <p class="text-xl font-bold text-emerald-400">¥{{ monthlyStats.recharge.toFixed(2) }}</p>
            </div>
            <div class="p-3.5 rounded-xl bg-orange-500/[0.06] border border-orange-500/10">
              <p class="text-[11px] text-slate-400 mb-1">本月消耗</p>
              <p class="text-xl font-bold text-orange-400">¥{{ monthlyStats.usage.toFixed(2) }}</p>
            </div>
            <div :class="['p-3.5 rounded-xl border', monthlyStats.net >= 0 ? 'bg-cyan-500/[0.06] border-cyan-500/10' : 'bg-red-500/[0.06] border-red-500/10']">
              <p class="text-[11px] text-slate-400 mb-1">本月净额</p>
              <p :class="['text-xl font-bold', monthlyStats.net >= 0 ? 'text-cyan-400' : 'text-red-400']">
                {{ monthlyStats.net >= 0 ? '+' : '' }}¥{{ monthlyStats.net.toFixed(2) }}
              </p>
            </div>
          </div>
          <!-- 交易笔数 -->
          <div class="flex items-center gap-6 mt-3 pt-3 border-t border-white/5">
            <div class="flex items-center gap-2">
              <div class="w-2 h-2 rounded-full bg-emerald-400"></div>
              <span class="text-xs text-slate-400">充值 {{ billStats.rechargeCount }} 笔</span>
            </div>
            <div class="flex items-center gap-2">
              <div class="w-2 h-2 rounded-full bg-orange-400"></div>
              <span class="text-xs text-slate-400">消耗 {{ billStats.usageCount }} 笔</span>
            </div>
            <div class="flex items-center gap-2">
              <div class="w-2 h-2 rounded-full bg-slate-400"></div>
              <span class="text-xs text-slate-400">共 {{ billStats.rechargeCount + billStats.usageCount }} 笔</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 流水记录 -->
      <div class="glass-card rounded-2xl overflow-hidden">
        <div class="px-5 md:px-6 pt-5 pb-3 border-b border-white/5">
          <div class="flex items-center justify-between">
            <h3 class="text-sm font-semibold text-white">流水记录</h3>
            <!-- Type Filter -->
            <div class="inline-flex bg-white/5 rounded-lg p-0.5">
              <button
                v-for="opt in filterOptions"
                :key="opt.value"
                @click="typeFilter = opt.value; currentPage = 1"
                :class="['px-3 py-1 text-xs rounded-md transition-all', typeFilter === opt.value ? 'bg-emerald-500/15 text-emerald-400' : 'text-slate-400 hover:text-slate-300']"
              >
                {{ opt.label }}
              </button>
            </div>
          </div>
        </div>

        <div class="p-4 md:p-6">
          <!-- Empty -->
          <div v-if="filteredBills.length === 0" class="flex flex-col items-center justify-center py-16">
            <FileText class="w-10 h-10 text-slate-600 mb-3" />
            <p class="text-sm text-slate-500">暂无记录</p>
          </div>

          <div v-else>
            <!-- Desktop Table -->
            <div class="hidden md:block overflow-x-auto">
              <table class="w-full">
                <thead>
                  <tr class="border-b border-white/[0.06]">
                    <th class="text-left text-[11px] font-semibold text-slate-500 uppercase tracking-wider pb-3 pl-2">类型</th>
                    <th class="text-left text-[11px] font-semibold text-slate-500 uppercase tracking-wider pb-3">金额</th>
                    <th class="text-left text-[11px] font-semibold text-slate-500 uppercase tracking-wider pb-3">余额</th>
                    <th class="text-left text-[11px] font-semibold text-slate-500 uppercase tracking-wider pb-3">Token</th>
                    <th class="text-left text-[11px] font-semibold text-slate-500 uppercase tracking-wider pb-3">详情</th>
                    <th class="text-right text-[11px] font-semibold text-slate-500 uppercase tracking-wider pb-3 pr-2">时间</th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-white/[0.03]">
                  <tr v-for="(bill, idx) in pagedBills" :key="bill.id" class="hover:bg-white/[0.03] transition-colors" :class="idx % 2 ? 'bg-white/[0.01]' : ''">
                    <td class="py-3 pl-2">
                      <span :class="['inline-flex items-center gap-1.5 px-2 py-0.5 text-[11px] rounded-md font-medium', bill.type === 'RECHARGE' ? 'bg-emerald-500/10 text-emerald-400' : 'bg-orange-500/10 text-orange-400']">
                        {{ bill.type === 'RECHARGE' ? '充值' : '消耗' }}
                      </span>
                    </td>
                    <td class="py-3 text-sm font-semibold" :class="bill.amount > 0 ? 'text-emerald-400' : 'text-orange-400'">
                      {{ bill.amount > 0 ? '+' : '' }}¥{{ Math.abs(Number(bill.amount || 0)).toFixed(2) }}
                    </td>
                    <td class="py-3 text-sm text-slate-400">¥{{ Number(bill.balanceAfter || 0).toFixed(2) }}</td>
                    <td class="py-3 text-sm text-slate-500">
                      <template v-if="bill.type === 'TOKEN_USAGE'">
                        <span class="text-cyan-400">{{ bill.inputTokens?.toLocaleString() || '0' }}</span>
                        <span class="text-slate-600 mx-0.5">/</span>
                        <span class="text-emerald-400">{{ bill.outputTokens?.toLocaleString() || '0' }}</span>
                      </template>
                      <span v-else class="text-slate-600">-</span>
                    </td>
                    <td class="py-3 text-sm text-slate-500 max-w-[200px] truncate" :title="bill.detail">{{ bill.detail || '-' }}</td>
                    <td class="py-3 pr-2 text-sm text-slate-500 text-right whitespace-nowrap">{{ formatTime(bill.createdAt) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>

            <!-- Mobile Cards -->
            <div class="md:hidden space-y-2">
              <div v-for="bill in pagedBills" :key="bill.id" class="rounded-xl bg-white/[0.02] border border-white/5 p-3">
                <div class="flex items-center justify-between mb-2">
                  <div class="flex items-center gap-2">
                    <span :class="['inline-flex items-center gap-1 px-2 py-0.5 text-[11px] rounded-md font-medium', bill.type === 'RECHARGE' ? 'bg-emerald-500/10 text-emerald-400' : 'bg-orange-500/10 text-orange-400']">
                      {{ bill.type === 'RECHARGE' ? '充值' : '消耗' }}
                    </span>
                    <span class="text-[11px] text-slate-500">{{ formatTime(bill.createdAt) }}</span>
                  </div>
                  <span class="text-sm font-semibold" :class="bill.amount > 0 ? 'text-emerald-400' : 'text-orange-400'">
                    {{ bill.amount > 0 ? '+' : '' }}¥{{ Math.abs(Number(bill.amount || 0)).toFixed(2) }}
                  </span>
                </div>
                <div v-if="bill.type === 'TOKEN_USAGE'" class="flex items-center gap-3 text-[11px] text-slate-500">
                  <span>输入 <span class="text-cyan-400">{{ bill.inputTokens?.toLocaleString() || '0' }}</span></span>
                  <span>输出 <span class="text-emerald-400">{{ bill.outputTokens?.toLocaleString() || '0' }}</span></span>
                </div>
              </div>
            </div>

            <!-- Pagination -->
            <div v-if="filteredBills.length > pageSize" class="flex items-center justify-between pt-4 mt-3 border-t border-white/[0.04]">
              <span class="text-xs text-slate-500">{{ currentPage }} / {{ totalPages }} 页</span>
              <div class="flex items-center gap-1">
                <button @click="goToPage(currentPage - 1)" :disabled="currentPage === 1" class="p-1.5 rounded-lg bg-white/5 text-slate-400 hover:bg-white/10 disabled:opacity-25 disabled:cursor-not-allowed transition-all"><ChevronLeft class="w-4 h-4" /></button>
                <template v-for="p in totalPages" :key="p">
                  <button v-if="totalPages <= 7 || Math.abs(p - currentPage) < 3 || p === 1 || p === totalPages" @click="goToPage(p)" :class="['w-8 h-8 rounded-lg text-xs font-medium transition-all', p === currentPage ? 'bg-emerald-500/15 text-emerald-400' : 'bg-white/5 text-slate-400 hover:bg-white/10']">{{ p }}</button>
                  <span v-else-if="p === currentPage - 2 || p === currentPage + 2" class="text-slate-600 text-xs">...</span>
                </template>
                <button @click="goToPage(currentPage + 1)" :disabled="currentPage === totalPages" class="p-1.5 rounded-lg bg-white/5 text-slate-400 hover:bg-white/10 disabled:opacity-25 disabled:cursor-not-allowed transition-all"><ChevronRight class="w-4 h-4" /></button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>
