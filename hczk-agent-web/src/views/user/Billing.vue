<script setup>
import { ref, onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useApiStore } from '@/stores/api'
import { CreditCard, Download, Calendar } from 'lucide-vue-next'

const authStore = useAuthStore()
const api = useApiStore()

const bills = ref([])
const loading = ref(false)

onMounted(async () => {
  loading.value = true
  const res = await api.get(`/billing/records/${authStore.user?.id}`)
  if (res.code === 200) bills.value = res.data || []
  loading.value = false
})
</script>

<template>
  <div class="space-y-6 animate-fade-in">
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-2xl font-bold text-white">账单明细</h1>
        <p class="text-slate-400 mt-1">查看您的消费与充值记录</p>
      </div>
      <button class="btn-secondary flex items-center gap-2">
        <Download class="w-4 h-4" /> 导出账单
      </button>
    </div>

    <div class="glass-card p-6">
      <div class="flex items-center justify-between mb-6">
        <h3 class="text-lg font-semibold text-white">流水记录</h3>
        <div class="flex items-center gap-2">
          <Calendar class="w-4 h-4 text-slate-400" />
          <select class="input-field w-auto text-sm py-1.5">
            <option>最近7天</option>
            <option>最近30天</option>
            <option>本月</option>
          </select>
        </div>
      </div>
      <div v-if="loading" class="text-slate-500">加载中...</div>
      <div v-else-if="bills.length === 0" class="text-slate-500">暂无记录</div>
      <div v-else class="overflow-x-auto">
        <table class="w-full">
          <thead>
            <tr class="border-b border-white/5">
              <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">ID</th>
              <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">类型</th>
              <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">金额</th>
              <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">余额</th>
              <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">详情</th>
              <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">时间</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-white/5">
            <tr v-for="bill in bills" :key="bill.id" class="hover:bg-white/5 transition-colors">
              <td class="py-4 text-sm text-slate-400 font-mono">#{{ bill.id }}</td>
              <td class="py-4">
                <span :class="['px-2 py-1 text-xs rounded-full', bill.type === 'RECHARGE' ? 'bg-emerald-500/10 text-emerald-400' : 'bg-orange-500/10 text-orange-400']">
                  {{ bill.type === 'RECHARGE' ? '充值' : '消耗' }}
                </span>
              </td>
              <td class="py-4 text-sm font-medium" :class="bill.amount > 0 ? 'text-emerald-400' : 'text-orange-400'">
                {{ bill.amount > 0 ? '+' : '' }}{{ bill.amount?.toFixed(2) }}
              </td>
              <td class="py-4 text-sm text-slate-300">¥{{ bill.balanceAfter?.toFixed(2) }}</td>
              <td class="py-4 text-sm text-slate-400">{{ bill.detail || '-' }}</td>
              <td class="py-4 text-sm text-slate-500">{{ bill.createdAt ? new Date(bill.createdAt).toLocaleString() : '-' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>
