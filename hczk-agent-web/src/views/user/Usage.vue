<script setup>
import { ref, onMounted, computed } from 'vue'
import { useApiStore } from '@/stores/api'
import { BarChart3, TrendingUp, Zap, Wallet } from 'lucide-vue-next'

const api = useApiStore()
const loading = ref(false)
const usageRecords = ref([])

onMounted(async () => {
  loading.value = true
  const res = await api.get('/billing/my-usage')
  if (res.code === 200) usageRecords.value = res.data || []
  loading.value = false
})

const stats = computed(() => {
  let totalInput = 0, totalOutput = 0, totalCost = 0
  for (const r of usageRecords.value) {
    totalInput += r.inputTokens || 0
    totalOutput += r.outputTokens || 0
    totalCost += Math.abs(Number(r.amount || 0))
  }
  return { totalInput, totalOutput, totalCost, count: usageRecords.value.length }
})

const statCards = computed(() => [
  { label: '总调用次数', value: stats.value.count.toLocaleString(), sub: '次', icon: Zap, color: 'cyan' },
  { label: '输入Token', value: stats.value.totalInput.toLocaleString(), sub: 'tokens', icon: BarChart3, color: 'cyan' },
  { label: '输出Token', value: stats.value.totalOutput.toLocaleString(), sub: 'tokens', icon: TrendingUp, color: 'amber' },
  { label: '总费用', value: '¥' + stats.value.totalCost.toFixed(2), sub: '元', icon: Wallet, color: 'emerald' }
])

const colorMap = {
  cyan: { bg: 'bg-cyan-500/10', text: 'text-cyan-400' },
  amber: { bg: 'bg-amber-500/10', text: 'text-amber-400' },
  emerald: { bg: 'bg-emerald-500/10', text: 'text-emerald-400' }
}

const dailyData = computed(() => {
  const days = []
  const now = new Date()
  for (let i = 6; i >= 0; i--) {
    const d = new Date(now)
    d.setDate(d.getDate() - i)
    const dateStr = d.toDateString()
    const label = `${d.getMonth() + 1}/${d.getDate()}`
    let input = 0, output = 0
    for (const r of usageRecords.value) {
      if (r.createdAt && new Date(r.createdAt).toDateString() === dateStr) {
        input += r.inputTokens || 0
        output += r.outputTokens || 0
      }
    }
    days.push({ label, input, output, total: input + output })
  }
  return days
})

const maxDaily = computed(() => Math.max(...dailyData.value.map(d => d.total)) || 1)
</script>

<template>
  <div class="space-y-6 animate-fade-in">
    <!-- Loading -->
    <div v-if="loading" class="flex items-center justify-center py-32">
      <div class="flex flex-col items-center gap-4">
        <div class="w-10 h-10 border-2 border-cyan-400/30 border-t-cyan-400 rounded-full animate-spin"></div>
        <p class="text-sm text-slate-400">加载中...</p>
      </div>
    </div>

    <template v-else>
      <div>
        <h1 class="text-2xl font-bold text-white">用量统计</h1>
        <p class="text-slate-400 mt-1">查看您的 Token 消耗详情与趋势</p>
      </div>

      <!-- Stats Cards -->
      <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        <div v-for="card in statCards" :key="card.label" class="glass-card p-6 glow-border">
          <div class="flex items-start justify-between">
            <div>
              <p class="text-sm text-slate-400">{{ card.label }}</p>
              <p class="text-2xl font-bold text-white mt-2">{{ card.value }}</p>
              <p v-if="card.sub" class="text-xs text-slate-500 mt-1">{{ card.sub }}</p>
            </div>
            <div :class="['w-10 h-10 rounded-lg flex items-center justify-center', colorMap[card.color].bg]">
              <component :is="card.icon" :class="['w-5 h-5', colorMap[card.color].text]" />
            </div>
          </div>
        </div>
      </div>

      <!-- Daily Trend Chart -->
      <div class="glass-card p-6">
        <div class="flex items-center justify-between mb-6">
          <div>
            <h3 class="text-lg font-semibold text-white">每日消耗趋势</h3>
            <p class="text-sm text-slate-400 mt-1">近7天 Token 消耗</p>
          </div>
          <div class="flex items-center gap-4">
            <div class="flex items-center gap-1.5">
              <span class="w-3 h-3 rounded-sm bg-cyan-500/60"></span>
              <span class="text-xs text-slate-400">输入</span>
            </div>
            <div class="flex items-center gap-1.5">
              <span class="w-3 h-3 rounded-sm bg-emerald-500/60"></span>
              <span class="text-xs text-slate-400">输出</span>
            </div>
          </div>
        </div>
        <div v-if="dailyData.every(d => d.total === 0)" class="flex items-center justify-center py-12">
          <p class="text-sm text-slate-500">暂无消耗数据</p>
        </div>
        <div v-else class="h-56 flex items-end justify-between gap-3 px-2">
          <div v-for="day in dailyData" :key="day.label" class="flex-1 flex flex-col items-center gap-2">
            <div class="w-full flex flex-col" :style="{ height: Math.max((day.total / maxDaily) * 100, 2) + '%' }">
              <div
                v-if="day.output > 0"
                class="w-full bg-gradient-to-t from-emerald-500/30 to-emerald-500/60 rounded-t-lg"
                :style="{ height: (day.output / day.total * 100) + '%' }"
              ></div>
              <div
                v-if="day.input > 0"
                class="w-full bg-gradient-to-t from-cyan-500/30 to-cyan-500/60"
                :class="{ 'rounded-t-lg': day.output === 0 }"
                :style="{ height: (day.input / day.total * 100) + '%' }"
              ></div>
            </div>
            <span class="text-xs text-slate-500">{{ day.label }}</span>
          </div>
        </div>
      </div>

      <!-- Usage Detail Table -->
      <div class="glass-card p-6">
        <h3 class="text-lg font-semibold text-white mb-6">用量明细</h3>
        <div v-if="loading" class="flex items-center justify-center py-8">
          <div class="w-8 h-8 border-2 border-cyan-400/30 border-t-cyan-400 rounded-full animate-spin"></div>
        </div>
        <div v-else-if="usageRecords.length === 0" class="text-slate-500 text-center py-8">暂无数据</div>
        <div v-else class="overflow-x-auto">
          <table class="w-full">
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
              <tr v-for="record in usageRecords" :key="record.id" class="hover:bg-white/5 transition-colors">
                <td class="py-4 text-sm text-slate-500">{{ record.createdAt ? new Date(record.createdAt).toLocaleString() : '-' }}</td>
                <td class="py-4 text-sm text-cyan-400 font-medium">{{ record.inputTokens?.toLocaleString() || '0' }}</td>
                <td class="py-4 text-sm text-amber-400 font-medium">{{ record.outputTokens?.toLocaleString() || '0' }}</td>
                <td class="py-4 text-sm font-medium text-emerald-400">¥{{ Math.abs(Number(record.amount || 0)).toFixed(4) }}</td>
                <td class="py-4 text-sm text-slate-400 max-w-xs truncate" :title="record.detail">{{ record.detail || '-' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </template>
  </div>
</template>
