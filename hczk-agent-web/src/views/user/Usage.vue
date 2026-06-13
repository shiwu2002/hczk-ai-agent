<script setup>
import { ref } from 'vue'
import { BarChart3, TrendingUp, Calendar } from 'lucide-vue-next'

const stats = ref([
  { label: '今日消耗', value: '-', sub: 'tokens' },
  { label: '本月消耗', value: '-', sub: 'tokens' },
  { label: '总消耗', value: '-', sub: 'tokens' },
  { label: '平均每日', value: '-', sub: 'tokens' }
])

const modelBreakdown = ref([])

const dailyData = ref([])

const maxVal = dailyData.value.length ? Math.max(...dailyData.value.map(d => d.input + d.output)) : 1
</script>

<template>
  <div class="space-y-6 animate-fade-in">
    <div>
      <h1 class="text-2xl font-bold text-white">用量统计</h1>
      <p class="text-slate-400 mt-1">查看您的 Token 消耗详情与趋势</p>
    </div>

    <!-- Stats -->
    <div class="grid grid-cols-1 md:grid-cols-4 gap-6">
      <div v-for="stat in stats" :key="stat.label" class="glass-card p-6 text-center">
        <p class="text-sm text-slate-400">{{ stat.label }}</p>
        <p class="text-2xl font-bold text-white mt-2">{{ stat.value }}</p>
        <p class="text-xs text-slate-500">{{ stat.sub }}</p>
      </div>
    </div>

    <!-- Chart -->
    <div class="glass-card p-6">
      <div class="flex items-center justify-between mb-6">
        <div>
          <h3 class="text-lg font-semibold text-white">每日消耗</h3>
          <p class="text-sm text-slate-400 mt-1">Input / Output Token 分布</p>
        </div>
        <div class="flex items-center gap-2">
          <Calendar class="w-4 h-4 text-slate-400" />
          <select class="input-field w-auto text-sm py-1.5">
            <option>最近7天</option>
            <option>最近30天</option>
          </select>
        </div>
      </div>
      <div v-if="dailyData.length" class="h-64 flex items-end justify-between gap-4 px-2">
        <div v-for="day in dailyData" :key="day.date" class="flex-1 flex flex-col items-center gap-2">
          <div class="w-full flex gap-0.5">
            <div class="flex-1 bg-cyan-500/60 rounded-t transition-all hover:bg-cyan-500/80" :style="{ height: (day.input / maxVal * 200) + 'px' }"></div>
            <div class="flex-1 bg-emerald-500/60 rounded-t transition-all hover:bg-emerald-500/80" :style="{ height: (day.output / maxVal * 200) + 'px' }"></div>
          </div>
          <span class="text-xs text-slate-500">{{ day.date }}</span>
        </div>
      </div>
      <div v-else class="h-64 flex items-center justify-center text-slate-500">暂无数据</div>
      <div class="flex items-center justify-center gap-6 mt-4">
        <div class="flex items-center gap-2">
          <div class="w-3 h-3 rounded bg-cyan-500/60"></div>
          <span class="text-xs text-slate-400">Input Tokens</span>
        </div>
        <div class="flex items-center gap-2">
          <div class="w-3 h-3 rounded bg-emerald-500/60"></div>
          <span class="text-xs text-slate-400">Output Tokens</span>
        </div>
      </div>
    </div>

    <!-- Model Breakdown -->
    <div class="glass-card p-6">
      <h3 class="text-lg font-semibold text-white mb-6">模型使用分布</h3>
      <div v-if="modelBreakdown.length" class="space-y-4">
        <div v-for="item in modelBreakdown" :key="item.model" class="flex items-center gap-4">
          <span class="text-sm text-white w-32">{{ item.model }}</span>
          <div class="flex-1 h-2 rounded-full bg-white/5 overflow-hidden">
            <div class="h-full bg-gradient-to-r from-emerald-500 to-cyan-500 rounded-full" :style="{ width: item.percent + '%' }"></div>
          </div>
          <span class="text-sm text-slate-400 w-20 text-right">{{ item.usage }}</span>
          <span class="text-sm text-emerald-400 w-20 text-right">{{ item.cost }}</span>
        </div>
      </div>
      <div v-else class="text-slate-500">暂无数据</div>
    </div>
  </div>
</template>
