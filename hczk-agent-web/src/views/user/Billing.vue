<script setup>
import { ref, onMounted, computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useApiStore } from '@/stores/api'
import { Wallet, Zap, CreditCard, Receipt, ChevronLeft, ChevronRight } from 'lucide-vue-next'

const authStore = useAuthStore()
const api = useApiStore()
const bills = ref([])
const loading = ref(false)
const typeFilter = ref('all')

// Pagination
const currentPage = ref(1)
const pageSize = 15

onMounted(async () => {
  loading.value = true
  const res = await api.get('/billing/my-records')
  if (res.code === 200) bills.value = res.data || []
  loading.value = false
})

const billStats = computed(() => {
  let totalRecharge = 0, totalUsage = 0
  for (const b of bills.value) {
    const amt = Number(b.amount || 0)
    if (b.type === 'RECHARGE') totalRecharge += amt
    else totalUsage += Math.abs(amt)
  }
  return {
    totalRecharge,
    totalUsage,
    balance: authStore.currentUser?.balance || 0,
    count: bills.value.length
  }
})

const statCards = computed(() => [
  { label: '总充值', value: '¥' + billStats.value.totalRecharge.toFixed(2), icon: Wallet, color: 'emerald' },
  { label: '总消耗', value: '¥' + billStats.value.totalUsage.toFixed(2), icon: Zap, color: 'orange' },
  { label: '当前余额', value: '¥' + Number(billStats.value.balance).toFixed(2), icon: CreditCard, color: 'cyan' },
  { label: '交易笔数', value: billStats.value.count.toLocaleString(), sub: '笔', icon: Receipt, color: 'purple' }
])

const colorMap = {
  emerald: { bg: 'bg-emerald-500/10', text: 'text-emerald-400' },
  orange: { bg: 'bg-orange-500/10', text: 'text-orange-400' },
  cyan: { bg: 'bg-cyan-500/10', text: 'text-cyan-400' },
  purple: { bg: 'bg-purple-500/10', text: 'text-purple-400' }
}

const filteredBills = computed(() => {
  if (typeFilter.value === 'all') return bills.value
  return bills.value.filter(b => b.type === typeFilter.value)
})

const filterOptions = [
  { value: 'all', label: '全部' },
  { value: 'RECHARGE', label: '充值' },
  { value: 'TOKEN_USAGE', label: '消耗' }
]

// Pagination computed
const totalPages = computed(() => Math.ceil(filteredBills.value.length / pageSize))
const pagedBills = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return filteredBills.value.slice(start, start + pageSize)
})

function goToPage(page) {
  if (page < 1 || page > totalPages.value) return
  currentPage.value = page
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
      <div>
        <h1 class="text-2xl font-bold text-white">账单明细</h1>
        <p class="text-slate-400 mt-1">查看您的消费与充值记录</p>
      </div>

      <!-- Summary Stats Cards -->
      <div class="grid grid-cols-2 md:grid-cols-2 lg:grid-cols-4 gap-3 md:gap-6">
        <div v-for="card in statCards" :key="card.label" class="glass-card p-4 md:p-6 glow-border">
          <div class="flex items-start justify-between">
            <div>
              <p class="text-xs md:text-sm text-slate-400">{{ card.label }}</p>
              <p class="text-lg md:text-2xl font-bold text-white mt-1 md:mt-2">{{ card.value }}</p>
              <p v-if="card.sub" class="text-xs text-slate-500 mt-0.5 md:mt-1">{{ card.sub }}</p>
            </div>
            <div :class="['w-8 h-8 md:w-10 md:h-10 rounded-lg flex items-center justify-center', colorMap[card.color].bg]">
              <component :is="card.icon" :class="['w-4 h-4 md:w-5 md:h-5', colorMap[card.color].text]" />
            </div>
          </div>
        </div>
      </div>

      <!-- Bill Records -->
      <div class="glass-card p-4 md:p-6">
        <div class="flex items-center justify-between mb-4 md:mb-6">
          <h3 class="text-base md:text-lg font-semibold text-white">流水记录</h3>
        </div>

        <!-- Type Filter -->
        <div class="flex gap-2 mb-4 md:mb-6">
          <button
            v-for="opt in filterOptions"
            :key="opt.value"
            @click="typeFilter = opt.value; currentPage = 1"
            :class="['px-3 md:px-4 py-1.5 text-xs md:text-sm rounded-full transition-all', typeFilter === opt.value ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30' : 'bg-white/5 text-slate-400 border border-white/5 hover:bg-white/10']"
          >
            {{ opt.label }}
          </button>
        </div>

        <div v-if="loading" class="flex items-center justify-center py-8">
          <div class="w-8 h-8 border-2 border-emerald-400/30 border-t-emerald-400 rounded-full animate-spin"></div>
        </div>
        <div v-else-if="filteredBills.length === 0" class="flex flex-col items-center justify-center py-12">
          <Receipt class="w-12 h-12 text-slate-600 mb-3" />
          <p class="text-sm text-slate-500">暂无记录</p>
        </div>
        <div v-else>
          <div class="overflow-x-auto">
            <table class="w-full">
              <thead>
                <tr class="border-b border-white/5">
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
                <tr v-for="bill in pagedBills" :key="bill.id" class="hover:bg-white/5 transition-colors">
                  <td class="py-4">
                    <span :class="['px-2 py-1 text-xs rounded-full', bill.type === 'RECHARGE' ? 'bg-emerald-500/10 text-emerald-400' : 'bg-orange-500/10 text-orange-400']">
                      {{ bill.type === 'RECHARGE' ? '充值' : '消耗' }}
                    </span>
                  </td>
                  <td class="py-4 text-sm text-cyan-400 font-medium">{{ bill.type === 'TOKEN_USAGE' ? (bill.inputTokens?.toLocaleString() || '0') : '-' }}</td>
                  <td class="py-4 text-sm text-amber-400 font-medium">{{ bill.type === 'TOKEN_USAGE' ? (bill.outputTokens?.toLocaleString() || '0') : '-' }}</td>
                  <td class="py-4 text-sm font-medium" :class="bill.amount > 0 ? 'text-emerald-400' : 'text-orange-400'">
                    {{ bill.amount > 0 ? '+' : '' }}{{ Number(bill.amount || 0).toFixed(2) }}
                  </td>
                  <td class="py-4 text-sm text-slate-300">¥{{ Number(bill.balanceAfter || 0).toFixed(2) }}</td>
                  <td class="py-4 text-sm text-slate-400 max-w-xs truncate" :title="bill.detail">{{ bill.detail || '-' }}</td>
                  <td class="py-4 text-sm text-slate-500">{{ bill.createdAt ? new Date(bill.createdAt).toLocaleString() : '-' }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <!-- Pagination -->
          <div v-if="filteredBills.length > pageSize" class="flex items-center justify-between pt-2">
            <span class="text-sm text-slate-400">共 {{ filteredBills.length }} 条记录</span>
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
      </div>
    </template>
  </div>
</template>
