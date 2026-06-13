<script setup>
import { ref, onMounted } from 'vue'
import { useApiStore } from '@/stores/api'
import { Receipt, TrendingUp, Download, Calendar } from 'lucide-vue-next'

const api = useApiStore()
const loading = ref(false)

const stats = ref([
  { label: '今日收入', value: '¥0.00', change: '+15%', icon: Receipt },
  { label: '本月收入', value: '¥0.00', change: '+8%', icon: TrendingUp }
])

const billingRecords = ref([])

onMounted(loadData)

async function loadData() {
  loading.value = true
  const res = await api.get('/billing/records')
  if (res.code === 200) {
    billingRecords.value = res.data || []
  }
  loading.value = false
}
</script>

<template>
  <div class="space-y-6 animate-fade-in">
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-2xl font-bold text-white">计费管理</h1>
        <p class="text-slate-400 mt-1">Token 消耗计费与充值记录</p>
      </div>
      <button class="btn-secondary flex items-center gap-2">
        <Download class="w-4 h-4" /> 导出账单
      </button>
    </div>

    <!-- Stats -->
    <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
      <div v-for="stat in stats" :key="stat.label" class="glass-card p-6">
        <div class="flex items-start justify-between">
          <div>
            <p class="text-sm text-slate-400">{{ stat.label }}</p>
            <p class="text-2xl font-bold text-white mt-2">{{ stat.value }}</p>
          </div>
          <div class="w-10 h-10 rounded-lg bg-emerald-500/10 flex items-center justify-center">
            <component :is="stat.icon" class="w-5 h-5 text-emerald-400" />
          </div>
        </div>
      </div>
    </div>

    <!-- Pricing Config -->
    <div class="glass-card p-6">
      <h3 class="text-lg font-semibold text-white mb-4">计费规则配置</h3>
      <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div class="p-4 rounded-lg bg-white/5">
          <p class="text-sm text-slate-400">输入 Token 倍率</p>
          <p class="text-xl font-bold text-white mt-1">1.0x</p>
          <p class="text-xs text-slate-500 mt-1">按模型原价计费</p>
        </div>
        <div class="p-4 rounded-lg bg-white/5">
          <p class="text-sm text-slate-400">输出 Token 倍率</p>
          <p class="text-xl font-bold text-white mt-1">1.0x</p>
          <p class="text-xs text-slate-500 mt-1">按模型原价计费</p>
        </div>
        <div class="p-4 rounded-lg bg-white/5">
          <p class="text-sm text-slate-400">最低充值金额</p>
          <p class="text-xl font-bold text-white mt-1">¥10.00</p>
          <p class="text-xs text-slate-500 mt-1">单次充值下限</p>
        </div>
      </div>
    </div>

    <!-- Records -->
    <div class="glass-card p-6">
      <div class="flex items-center justify-between mb-6">
        <h3 class="text-lg font-semibold text-white">账单流水</h3>
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
      <div v-else class="overflow-x-auto">
        <table class="w-full">
          <thead>
            <tr class="border-b border-white/5">
              <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">用户</th>
              <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">类型</th>
              <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">金额</th>
              <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">余额</th>
              <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">详情</th>
              <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">时间</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-white/5">
            <tr v-for="record in billingRecords" :key="record.id" class="hover:bg-white/5 transition-colors">
              <td class="py-4 text-sm text-white">{{ record.user?.name || record.user?.username || record.user || '-' }}</td>
              <td class="py-4">
                <span :class="['px-2 py-1 text-xs rounded-full', record.type === '充值' || record.type === 'RECHARGE' ? 'bg-emerald-500/10 text-emerald-400' : 'bg-orange-500/10 text-orange-400']">
                  {{ record.type === 'RECHARGE' ? '充值' : record.type }}
                </span>
              </td>
              <td class="py-4 text-sm font-medium" :class="(record.amount || 0) > 0 ? 'text-emerald-400' : 'text-orange-400'">
                {{ (record.amount || 0) > 0 ? '+' : '' }}{{ (record.amount || 0).toFixed(2) }}
              </td>
              <td class="py-4 text-sm text-slate-300">¥{{ (record.balance || 0).toFixed(2) }}</td>
              <td class="py-4 text-sm text-slate-400">{{ record.detail || '-' }}</td>
              <td class="py-4 text-sm text-slate-500">{{ record.time || record.createdAt || '-' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>
