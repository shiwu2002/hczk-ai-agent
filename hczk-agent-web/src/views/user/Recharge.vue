<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useApiStore } from '@/stores/api'
import { Wallet, Zap, Crown, Rocket, Check, Clock, ChevronLeft, ChevronRight, Sparkles, ArrowUpRight, CreditCard, Shield, Gift } from 'lucide-vue-next'

const authStore = useAuthStore()
const api = useApiStore()

const selectedPackage = ref(null)
const paymentMethod = ref('alipay')
const recharging = ref(false)
const showSuccess = ref(false)
const rechargeHistory = ref([])
const customAmount = ref(null)
const isCustom = ref(false)
const currentPage = ref(1)
const pageSize = 5
const polling = ref(false)
let pollTimer = null

const packages = ref([
  { id: 1, name: '入门包', amount: 100, bonus: 0, icon: Zap, popular: false, desc: '适合体验用户' },
  { id: 2, name: '标准包', amount: 500, bonus: 50, icon: Zap, popular: true, desc: '性价比之选' },
  { id: 3, name: '专业包', amount: 1000, bonus: 150, icon: Crown, popular: false, desc: '高频使用推荐' },
  { id: 4, name: '企业包', amount: 5000, bonus: 1000, icon: Rocket, popular: false, desc: '大规模部署' },
  { id: 5, name: '自定义', amount: 0, bonus: 0, icon: Wallet, popular: false, desc: '自由输入金额' }
])

function selectPackage(pkg) {
  if (pkg.id === 5) {
    isCustom.value = true
    selectedPackage.value = pkg
  } else {
    isCustom.value = false
    customAmount.value = null
    selectedPackage.value = pkg
  }
}

async function recharge() {
  const amount = isCustom.value ? Number(customAmount.value) : (selectedPackage.value.amount + selectedPackage.value.bonus)
  if (!amount || amount <= 0) {
    alert('请输入充值金额')
    return
  }
  recharging.value = true
  try {
    if (paymentMethod.value === 'alipay') {
      const res = await api.post('/billing/recharge-alipay', { amount })
      if (res.code === 200 && res.data?.payForm) {
        const win = window.open('', '_blank')
        if (win) {
          win.document.write(res.data.payForm)
          win.document.close()
        }
        pollPaymentResult(res.data.orderNo)
      } else {
        alert(res.message || '创建支付订单失败')
      }
    } else {
      const res = await api.post(`/billing/self-recharge?amount=${amount}&paymentMethod=${paymentMethod.value}`)
      if (res.code === 200) {
        const userRes = await api.get('/users/me')
        if (userRes.code === 200) authStore.user = userRes.data
        showSuccess.value = true
        setTimeout(() => showSuccess.value = false, 3000)
        selectedPackage.value = null
        isCustom.value = false
        customAmount.value = null
        loadRechargeHistory()
      } else {
        alert(res.message || '充值失败')
      }
    }
  } catch (e) {
    alert('充值失败')
  } finally {
    recharging.value = false
  }
}

function pollPaymentResult(orderNo) {
  let attempts = 0
  const maxAttempts = 60
  polling.value = true
  pollTimer = setInterval(async () => {
    attempts++
    if (attempts > maxAttempts) {
      clearInterval(pollTimer)
      pollTimer = null
      polling.value = false
      return
    }
    try {
      const res = await api.get(`/billing/recharge/${orderNo}/status`)
      if (res.code === 200 && res.data?.paid) {
        clearInterval(pollTimer)
        pollTimer = null
        polling.value = false
        const userRes = await api.get('/users/me')
        if (userRes.code === 200) authStore.user = userRes.data
        showSuccess.value = true
        setTimeout(() => showSuccess.value = false, 3000)
        selectedPackage.value = null
        isCustom.value = false
        customAmount.value = null
        loadRechargeHistory()
      }
    } catch (e) {}
  }, 3000)
}

async function loadRechargeHistory() {
  try {
    const res = await api.get('/billing/my-recharges')
    if (res.code === 200) rechargeHistory.value = res.data || []
  } catch (e) {}
}

const paymentMethods = [
  { value: 'alipay', label: '支付宝', shortLabel: '支', color: 'blue', desc: '沙箱支付' },
  { value: 'wechat', label: '微信支付', shortLabel: '微', color: 'green', desc: '即时到账' },
  { value: 'bank', label: '银行转账', shortLabel: '银', color: 'amber', desc: '人工确认' }
]

const paymentColorMap = {
  blue: { bg: 'bg-blue-500/15', text: 'text-blue-400', ring: 'ring-blue-500/30' },
  green: { bg: 'bg-green-500/15', text: 'text-green-400', ring: 'ring-green-500/30' },
  amber: { bg: 'bg-amber-500/15', text: 'text-amber-400', ring: 'ring-amber-500/30' }
}

const totalPages = computed(() => Math.ceil(rechargeHistory.value.length / pageSize))
const paginatedHistory = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return rechargeHistory.value.slice(start, start + pageSize)
})

function goToPage(page) {
  if (page < 1 || page > totalPages.value) return
  currentPage.value = page
}

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

const totalRecharged = computed(() => {
  return rechargeHistory.value
    .filter(r => r.status === 'SUCCESS' || r.status === 'COMPLETED')
    .reduce((sum, r) => sum + Number(r.amount || 0), 0)
})

onMounted(() => { loadRechargeHistory() })
onUnmounted(() => { if (pollTimer) { clearInterval(pollTimer); pollTimer = null } })
</script>

<template>
  <div class="space-y-6 animate-fade-in">
    <!-- Success Toast -->
    <div v-if="showSuccess" class="fixed top-6 left-1/2 -translate-x-1/2 z-50 animate-fade-in">
      <div class="flex items-center gap-3 px-6 py-3 rounded-xl bg-emerald-500/20 border border-emerald-500/30 backdrop-blur-sm shadow-lg shadow-emerald-500/10">
        <div class="w-6 h-6 rounded-full bg-emerald-500 flex items-center justify-center"><Check class="w-4 h-4 text-white" /></div>
        <span class="text-emerald-300 font-medium">充值成功！余额已更新</span>
      </div>
    </div>

    <!-- Top: Balance + Stats -->
    <div class="grid grid-cols-1 lg:grid-cols-3 gap-5">
      <!-- Balance Card -->
      <div class="relative rounded-2xl overflow-hidden">
        <div class="absolute inset-0 bg-gradient-to-br from-emerald-600/20 via-cyan-600/10 to-transparent"></div>
        <div class="relative p-6">
          <div class="flex items-center gap-2 mb-4">
            <Wallet class="w-5 h-5 text-emerald-400" />
            <span class="text-sm text-slate-300">账户余额</span>
          </div>
          <p class="text-3xl font-bold text-white mb-1">¥{{ Number(authStore.currentUser?.balance || 0).toFixed(2) }}</p>
          <p class="text-xs text-slate-500">余额用于 Token 消耗计费</p>
        </div>
      </div>

      <!-- Stats -->
      <div class="lg:col-span-2 grid grid-cols-2 gap-3">
        <div class="glass-card rounded-2xl p-4 flex items-center gap-3">
          <div class="w-10 h-10 rounded-xl bg-emerald-500/10 flex items-center justify-center flex-shrink-0">
            <CreditCard class="w-5 h-5 text-emerald-400" />
          </div>
          <div>
            <p class="text-[11px] text-slate-400">累计充值</p>
            <p class="text-lg font-bold text-emerald-400">¥{{ totalRecharged.toFixed(2) }}</p>
          </div>
        </div>
        <div class="glass-card rounded-2xl p-4 flex items-center gap-3">
          <div class="w-10 h-10 rounded-xl bg-cyan-500/10 flex items-center justify-center flex-shrink-0">
            <Shield class="w-5 h-5 text-cyan-400" />
          </div>
          <div>
            <p class="text-[11px] text-slate-400">充值笔数</p>
            <p class="text-lg font-bold text-white">{{ rechargeHistory.length }} 笔</p>
          </div>
        </div>
        <div class="glass-card rounded-2xl p-4 flex items-center gap-3">
          <div class="w-10 h-10 rounded-xl bg-amber-500/10 flex items-center justify-center flex-shrink-0">
            <Gift class="w-5 h-5 text-amber-400" />
          </div>
          <div>
            <p class="text-[11px] text-slate-400">当前赠送</p>
            <p class="text-lg font-bold text-amber-400">¥{{ packages.find(p => selectedPackage?.id === p.id)?.bonus || 0 }}</p>
          </div>
        </div>
        <div class="glass-card rounded-2xl p-4 flex items-center gap-3">
          <div class="w-10 h-10 rounded-xl bg-purple-500/10 flex items-center justify-center flex-shrink-0">
            <Sparkles class="w-5 h-5 text-purple-400" />
          </div>
          <div>
            <p class="text-[11px] text-slate-400">选择套餐</p>
            <p class="text-lg font-bold text-white">{{ selectedPackage ? selectedPackage.name : '未选择' }}</p>
          </div>
        </div>
      </div>
    </div>

    <!-- Package Selection + Payment -->
    <div class="grid grid-cols-1 lg:grid-cols-5 gap-5">
      <!-- Packages -->
      <div class="lg:col-span-3">
        <div class="flex items-center gap-2 mb-3">
          <Sparkles class="w-4 h-4 text-emerald-400" />
          <h3 class="text-sm font-semibold text-white">选择充值套餐</h3>
        </div>
        <div class="grid grid-cols-2 md:grid-cols-3 gap-3">
          <div
            v-for="pkg in packages"
            :key="pkg.id"
            @click="selectPackage(pkg)"
            :class="[
              'glass-card rounded-xl p-4 cursor-pointer transition-all duration-300 relative',
              selectedPackage?.id === pkg.id
                ? 'ring-2 ring-emerald-500/60 bg-emerald-500/[0.06] shadow-lg shadow-emerald-500/10'
                : 'hover:bg-white/5 hover:-translate-y-0.5',
              pkg.popular && selectedPackage?.id !== pkg.id ? 'ring-1 ring-emerald-500/20' : ''
            ]"
          >
            <div v-if="pkg.popular" class="absolute -top-2.5 left-1/2 -translate-x-1/2 px-2.5 py-0.5 rounded-full bg-gradient-to-r from-emerald-500 to-cyan-500 text-white text-[10px] font-semibold shadow-lg shadow-emerald-500/30 whitespace-nowrap">
              最受欢迎
            </div>
            <div v-if="selectedPackage?.id === pkg.id" class="absolute top-2.5 right-2.5">
              <div class="w-5 h-5 rounded-full bg-emerald-500 flex items-center justify-center"><Check class="w-3 h-3 text-white" /></div>
            </div>
            <div class="flex items-center gap-2.5 mb-3">
              <div :class="['w-9 h-9 rounded-lg flex items-center justify-center', selectedPackage?.id === pkg.id ? 'bg-emerald-500/20' : 'bg-white/5']">
                <component :is="pkg.icon" :class="['w-4 h-4', selectedPackage?.id === pkg.id ? 'text-emerald-400' : 'text-slate-400']" />
              </div>
              <div>
                <p class="text-sm font-semibold text-white">{{ pkg.name }}</p>
                <p class="text-[10px] text-slate-500">{{ pkg.desc }}</p>
              </div>
            </div>
            <template v-if="pkg.id === 5">
              <div v-if="isCustom && selectedPackage?.id === 5" class="mt-1">
                <input v-model="customAmount" type="number" min="1" placeholder="输入金额"
                  class="w-full px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-center text-base font-medium placeholder-slate-500 focus:outline-none focus:border-emerald-500/50 focus:ring-1 focus:ring-emerald-500/30 transition-all" />
              </div>
              <p v-else class="text-xs text-slate-400 text-center mt-1">点击输入金额</p>
            </template>
            <template v-else>
              <div class="flex items-baseline gap-0.5">
                <span class="text-xs text-slate-500">¥</span>
                <span class="text-2xl font-bold text-white">{{ pkg.amount }}</span>
              </div>
              <p v-if="pkg.bonus > 0" class="text-[11px] text-emerald-400 font-medium mt-1">赠送 ¥{{ pkg.bonus }}</p>
            </template>
          </div>
        </div>
      </div>

      <!-- Payment + Summary -->
      <div class="lg:col-span-2 space-y-5">
        <!-- Payment Method -->
        <div class="glass-card rounded-2xl p-4">
          <h4 class="text-sm font-semibold text-white mb-3">支付方式</h4>
          <div class="space-y-2">
            <label
              v-for="pm in paymentMethods"
              :key="pm.value"
              :class="[
                'flex items-center gap-3 p-3 rounded-xl border cursor-pointer transition-all',
                paymentMethod === pm.value
                  ? 'border-emerald-500/30 bg-emerald-500/[0.04]'
                  : 'border-white/5 bg-white/[0.02] hover:bg-white/5'
              ]"
            >
              <input v-model="paymentMethod" type="radio" :value="pm.value" class="hidden" />
              <div :class="['w-8 h-8 rounded-lg flex items-center justify-center text-xs font-bold transition-all',
                paymentMethod === pm.value ? paymentColorMap[pm.color].bg + ' ' + paymentColorMap[pm.color].text : 'bg-white/5 text-slate-400']">
                {{ pm.shortLabel }}
              </div>
              <div class="flex-1">
                <p :class="['text-sm font-medium', paymentMethod === pm.value ? 'text-white' : 'text-slate-300']">{{ pm.label }}</p>
                <p class="text-[10px] text-slate-500">{{ pm.desc }}</p>
              </div>
              <div v-if="paymentMethod === pm.value" class="w-4 h-4 rounded-full bg-emerald-500 flex items-center justify-center">
                <Check class="w-2.5 h-2.5 text-white" />
              </div>
            </label>
          </div>
        </div>

        <!-- Order Summary -->
        <div class="glass-card rounded-2xl p-4">
          <h4 class="text-sm font-semibold text-white mb-3">订单确认</h4>
          <div v-if="!selectedPackage" class="text-center py-6">
            <p class="text-xs text-slate-500">请先选择充值套餐</p>
          </div>
          <template v-else>
            <div class="space-y-2.5 mb-4">
              <div class="flex items-center justify-between">
                <span class="text-xs text-slate-400">套餐</span>
                <span class="text-sm text-white font-medium">{{ selectedPackage.name }}</span>
              </div>
              <div class="flex items-center justify-between">
                <span class="text-xs text-slate-400">充值金额</span>
                <span class="text-sm text-white">¥{{ isCustom ? (customAmount || '0') : selectedPackage.amount }}</span>
              </div>
              <div v-if="!isCustom && selectedPackage.bonus > 0" class="flex items-center justify-between">
                <span class="text-xs text-slate-400">赠送金额</span>
                <span class="text-sm text-emerald-400">+¥{{ selectedPackage.bonus }}</span>
              </div>
              <div class="h-px bg-white/5"></div>
              <div class="flex items-center justify-between">
                <span class="text-sm text-white font-medium">实到账</span>
                <span class="text-xl font-bold text-emerald-400">¥{{ isCustom ? (customAmount || '0') : (selectedPackage.amount + selectedPackage.bonus) }}</span>
              </div>
            </div>
            <button
              @click="recharge"
              :disabled="recharging"
              class="w-full py-3 text-sm font-semibold rounded-xl bg-gradient-to-r from-emerald-500 to-cyan-500 text-white shadow-lg shadow-emerald-500/25 hover:shadow-emerald-500/40 hover:scale-[1.01] active:scale-[0.99] transition-all disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:scale-100"
            >
              <span v-if="recharging" class="flex items-center justify-center gap-2">
                <span class="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin"></span>
                处理中...
              </span>
              <span v-else>立即充值</span>
            </button>
            <p v-if="polling" class="text-xs text-amber-400 mt-2 text-center animate-pulse">
              等待支付确认中...请在弹出的页面完成支付
            </p>
          </template>
        </div>
      </div>
    </div>

    <!-- Recharge History -->
    <div class="glass-card rounded-2xl overflow-hidden">
      <div class="px-5 pt-4 pb-3 border-b border-white/5">
        <div class="flex items-center justify-between">
          <h3 class="text-sm font-semibold text-white">充值记录</h3>
          <span class="text-[11px] text-slate-500">共 {{ rechargeHistory.length }} 笔</span>
        </div>
      </div>
      <div class="p-4">
        <div v-if="rechargeHistory.length === 0" class="text-center py-10">
          <Wallet class="w-8 h-8 text-slate-600 mx-auto mb-2" />
          <p class="text-xs text-slate-500">暂无充值记录</p>
        </div>
        <div v-else>
          <!-- Desktop -->
          <div class="hidden md:block overflow-x-auto">
            <table class="w-full">
              <thead>
                <tr class="border-b border-white/[0.06]">
                  <th class="text-left text-[11px] font-semibold text-slate-500 uppercase tracking-wider pb-3 pl-2">金额</th>
                  <th class="text-left text-[11px] font-semibold text-slate-500 uppercase tracking-wider pb-3">支付方式</th>
                  <th class="text-left text-[11px] font-semibold text-slate-500 uppercase tracking-wider pb-3">状态</th>
                  <th class="text-right text-[11px] font-semibold text-slate-500 uppercase tracking-wider pb-3 pr-2">时间</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-white/[0.03]">
                <tr v-for="record in paginatedHistory" :key="record.id" class="hover:bg-white/[0.03] transition-colors">
                  <td class="py-3 pl-2 text-sm text-emerald-400 font-semibold">+¥{{ Number(record.amount || 0).toFixed(2) }}</td>
                  <td class="py-3 text-sm text-slate-400">{{ record.paymentMethod === 'alipay' ? '支付宝' : record.paymentMethod === 'wechat' ? '微信支付' : record.paymentMethod === 'bank' ? '银行转账' : (record.paymentMethod || '-') }}</td>
                  <td class="py-3">
                    <span :class="['inline-flex items-center gap-1 px-2 py-0.5 text-[11px] rounded-md font-medium', record.status === 'SUCCESS' || record.status === 'COMPLETED' ? 'bg-emerald-500/10 text-emerald-400' : 'bg-amber-500/10 text-amber-400']">
                      {{ record.status === 'SUCCESS' || record.status === 'COMPLETED' ? '成功' : record.status === 'PENDING' ? '处理中' : (record.status || '-') }}
                    </span>
                  </td>
                  <td class="py-3 pr-2 text-sm text-slate-500 text-right">{{ formatTime(record.createdAt) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <!-- Mobile -->
          <div class="md:hidden space-y-2">
            <div v-for="record in paginatedHistory" :key="record.id" class="rounded-xl bg-white/[0.02] border border-white/5 p-3">
              <div class="flex items-center justify-between mb-1.5">
                <span class="text-sm text-emerald-400 font-semibold">+¥{{ Number(record.amount || 0).toFixed(2) }}</span>
                <span :class="['inline-flex items-center gap-1 px-2 py-0.5 text-[11px] rounded-md font-medium', record.status === 'SUCCESS' || record.status === 'COMPLETED' ? 'bg-emerald-500/10 text-emerald-400' : 'bg-amber-500/10 text-amber-400']">
                  {{ record.status === 'SUCCESS' || record.status === 'COMPLETED' ? '成功' : '处理中' }}
                </span>
              </div>
              <div class="flex items-center justify-between text-[11px] text-slate-500">
                <span>{{ record.paymentMethod === 'alipay' ? '支付宝' : record.paymentMethod === 'wechat' ? '微信支付' : '银行转账' }}</span>
                <span>{{ formatTime(record.createdAt) }}</span>
              </div>
            </div>
          </div>
          <!-- Pagination -->
          <div v-if="rechargeHistory.length > pageSize" class="flex items-center justify-between pt-3 mt-3 border-t border-white/[0.04]">
            <span class="text-xs text-slate-500">{{ currentPage }} / {{ totalPages }} 页</span>
            <div class="flex items-center gap-1">
              <button @click="goToPage(currentPage - 1)" :disabled="currentPage === 1" class="p-1.5 rounded-lg bg-white/5 text-slate-400 hover:bg-white/10 disabled:opacity-25 disabled:cursor-not-allowed transition-all"><ChevronLeft class="w-4 h-4" /></button>
              <button v-for="p in totalPages" :key="p" v-show="totalPages <= 5 || Math.abs(p - currentPage) < 2 || p === 1 || p === totalPages" @click="goToPage(p)" :class="['w-7 h-7 rounded-lg text-xs font-medium transition-all', p === currentPage ? 'bg-emerald-500/15 text-emerald-400' : 'bg-white/5 text-slate-400 hover:bg-white/10']">{{ p }}</button>
              <button @click="goToPage(currentPage + 1)" :disabled="currentPage === totalPages" class="p-1.5 rounded-lg bg-white/5 text-slate-400 hover:bg-white/10 disabled:opacity-25 disabled:cursor-not-allowed transition-all"><ChevronRight class="w-4 h-4" /></button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
