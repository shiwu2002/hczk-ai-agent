<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useApiStore } from '@/stores/api'
import { Wallet, Zap, Crown, Rocket, Check, Clock, ChevronLeft, ChevronRight } from 'lucide-vue-next'

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
const pageSize = 10
const polling = ref(false)
let pollTimer = null

const packages = ref([
  { id: 1, name: '入门包', amount: 100, bonus: 0, icon: Zap, popular: false },
  { id: 2, name: '标准包', amount: 500, bonus: 50, icon: Zap, popular: true },
  { id: 3, name: '专业包', amount: 1000, bonus: 150, icon: Crown, popular: false },
  { id: 4, name: '企业包', amount: 5000, bonus: 1000, icon: Rocket, popular: false },
  { id: 5, name: '自定义', amount: 0, bonus: 0, icon: Wallet, popular: false }
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
      // 支付宝沙箱支付
      const res = await api.post('/billing/recharge-alipay', { amount })
      if (res.code === 200 && res.data?.payForm) {
        // 新窗口打开支付表单
        const win = window.open('', '_blank')
        if (win) {
          win.document.write(res.data.payForm)
          win.document.close()
        }
        // 开始轮询支付状态
        pollPaymentResult(res.data.orderNo)
      } else {
        alert(res.message || '创建支付订单失败')
      }
    } else {
      // 其他支付方式：直接到账
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
        // 刷新余额
        const userRes = await api.get('/users/me')
        if (userRes.code === 200) authStore.user = userRes.data
        showSuccess.value = true
        setTimeout(() => showSuccess.value = false, 3000)
        selectedPackage.value = null
        isCustom.value = false
        customAmount.value = null
        loadRechargeHistory()
      }
    } catch (e) {
      // 忽略轮询错误
    }
  }, 3000)
}

async function loadRechargeHistory() {
  try {
    const res = await api.get('/billing/my-recharges')
    if (res.code === 200) rechargeHistory.value = res.data || []
  } catch (e) {
    // ignore
  }
}

const paymentMethods = [
  { value: 'alipay', label: '支付宝', shortLabel: '支', color: 'blue', desc: '沙箱支付' },
  { value: 'wechat', label: '微信支付', shortLabel: '微', color: 'green', desc: '即时到账' },
  { value: 'bank', label: '银行转账', shortLabel: '银', color: 'amber', desc: '人工确认' }
]

const paymentColorMap = {
  blue: 'bg-blue-500/20 text-blue-400',
  green: 'bg-green-500/20 text-green-400',
  amber: 'bg-amber-500/20 text-amber-400'
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

onMounted(() => {
  loadRechargeHistory()
})

onUnmounted(() => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
})
</script>

<template>
  <div class="space-y-6 animate-fade-in">
    <!-- Success Toast -->
    <div v-if="showSuccess" class="fixed top-6 left-1/2 -translate-x-1/2 z-50 animate-fade-in">
      <div class="flex items-center gap-3 px-6 py-3 rounded-xl bg-emerald-500/20 border border-emerald-500/30 backdrop-blur-sm shadow-lg shadow-emerald-500/10">
        <div class="w-6 h-6 rounded-full bg-emerald-500 flex items-center justify-center">
          <Check class="w-4 h-4 text-white" />
        </div>
        <span class="text-emerald-300 font-medium">充值成功！余额已更新</span>
      </div>
    </div>

    <!-- Current Balance -->
    <div class="glass-card p-6 glow-border relative overflow-hidden">
      <div class="absolute inset-0 bg-gradient-to-br from-emerald-500/10 via-cyan-500/5 to-transparent pointer-events-none"></div>
      <div class="relative flex items-center justify-between">
        <div>
          <p class="text-sm text-slate-400">当前余额</p>
          <p class="text-4xl font-bold text-white mt-2">¥{{ Number(authStore.currentUser?.balance || 0).toFixed(2) }}</p>
          <p class="text-sm text-slate-500 mt-1">选择套餐充值，余额用于 Token 消耗计费</p>
        </div>
        <div class="w-16 h-16 rounded-2xl bg-emerald-500/10 flex items-center justify-center">
          <Wallet class="w-8 h-8 text-emerald-400" />
        </div>
      </div>
    </div>

    <!-- Package Selection -->
    <div>
      <h2 class="text-lg font-semibold text-white mb-4">选择充值套餐</h2>
      <div class="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-4">
        <div v-for="pkg in packages" :key="pkg.id" @click="selectPackage(pkg)"
          :class="['glass-card p-6 cursor-pointer transition-all glow-border relative', selectedPackage?.id === pkg.id ? 'ring-2 ring-emerald-500 bg-emerald-500/5' : 'hover:bg-white/5']">
          <div v-if="pkg.popular" class="absolute -top-3 left-1/2 -translate-x-1/2 px-3 py-1 rounded-full bg-gradient-to-r from-emerald-500 to-cyan-500 text-white text-xs font-medium">最受欢迎</div>
          <div class="flex items-center justify-center mb-4">
            <div :class="['w-14 h-14 rounded-2xl flex items-center justify-center', selectedPackage?.id === pkg.id ? 'bg-emerald-500/20' : 'bg-white/5']">
              <component :is="pkg.icon" :class="['w-7 h-7', selectedPackage?.id === pkg.id ? 'text-emerald-400' : 'text-slate-400']" />
            </div>
          </div>
          <h3 class="text-lg font-semibold text-white text-center">{{ pkg.name }}</h3>
          <template v-if="pkg.id === 5">
            <div v-if="isCustom && selectedPackage?.id === 5" class="mt-3">
              <input
                v-model="customAmount"
                type="number"
                min="1"
                placeholder="输入金额"
                class="w-full px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-center text-lg font-medium placeholder-slate-500 focus:outline-none focus:border-emerald-500/50 focus:ring-1 focus:ring-emerald-500/30"
              />
            </div>
            <p v-else class="text-sm text-slate-400 text-center mt-3">点击输入金额</p>
          </template>
          <template v-else>
            <p class="text-3xl font-bold text-white text-center mt-3"><span class="text-lg">¥</span>{{ pkg.amount }}</p>
            <p v-if="pkg.bonus > 0" class="text-sm text-emerald-400 text-center mt-2">赠送 ¥{{ pkg.bonus }}</p>
            <p v-else class="text-sm text-slate-500 text-center mt-2">无赠送</p>
          </template>
          <div v-if="selectedPackage?.id === pkg.id" class="mt-4 flex items-center justify-center">
            <div class="w-6 h-6 rounded-full bg-emerald-500 flex items-center justify-center"><Check class="w-4 h-4 text-white" /></div>
          </div>
        </div>
      </div>
    </div>

    <!-- Payment Method & Summary -->
    <div class="glass-card p-6">
      <h3 class="text-lg font-semibold text-white mb-4">支付方式</h3>
      <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
        <label
          v-for="pm in paymentMethods"
          :key="pm.value"
          :class="['flex items-center gap-3 p-4 rounded-lg border cursor-pointer transition-all', paymentMethod === pm.value ? 'border-emerald-500/50 bg-emerald-500/5' : 'border-white/5 bg-white/5 hover:bg-white/10']"
        >
          <input v-model="paymentMethod" type="radio" :value="pm.value" class="hidden" />
          <div :class="['w-10 h-10 rounded-lg flex items-center justify-center font-bold text-sm', paymentColorMap[pm.color]]">{{ pm.shortLabel }}</div>
          <div><p class="text-white font-medium">{{ pm.label }}</p><p class="text-xs text-slate-400">{{ pm.desc }}</p></div>
        </label>
      </div>

      <div v-if="selectedPackage" class="mt-6 pt-6 border-t border-white/5">
        <template v-if="isCustom && selectedPackage.id === 5">
          <div class="flex items-center justify-between mb-4"><span class="text-slate-400">充值金额</span><span class="text-white font-medium">¥{{ customAmount || '0' }}</span></div>
          <div class="flex items-center justify-between mb-6"><span class="text-white font-medium">实到账</span><span class="text-2xl font-bold text-emerald-400">¥{{ customAmount || '0' }}</span></div>
        </template>
        <template v-else>
          <div class="flex items-center justify-between mb-4"><span class="text-slate-400">充值金额</span><span class="text-white font-medium">¥{{ selectedPackage.amount }}</span></div>
          <div class="flex items-center justify-between mb-4"><span class="text-slate-400">赠送金额</span><span class="text-emerald-400 font-medium">+¥{{ selectedPackage.bonus }}</span></div>
          <div class="flex items-center justify-between mb-6"><span class="text-white font-medium">实到账</span><span class="text-2xl font-bold text-emerald-400">¥{{ selectedPackage.amount + selectedPackage.bonus }}</span></div>
        </template>
        <button @click="recharge" :disabled="recharging" class="btn-primary w-full py-3 text-lg">
          <span v-if="recharging" class="flex items-center justify-center gap-2">
            <span class="w-5 h-5 border-2 border-white/30 border-t-white rounded-full animate-spin"></span>
            处理中...
          </span>
          <span v-else>立即充值</span>
        </button>
        <p v-if="polling" class="text-sm text-amber-400 mt-3 text-center animate-pulse">
          等待支付确认中...请在弹出的支付宝页面完成支付
        </p>
      </div>
    </div>

    <!-- Recharge History -->
    <div class="glass-card p-6">
      <div class="flex items-center justify-between mb-4">
        <h3 class="text-lg font-semibold text-white">充值记录</h3>
        <Clock class="w-4 h-4 text-slate-400" />
      </div>
      <div v-if="rechargeHistory.length === 0" class="flex flex-col items-center justify-center py-8">
        <Wallet class="w-10 h-10 text-slate-600 mb-2" />
        <p class="text-sm text-slate-500">暂无充值记录</p>
      </div>
      <div v-else>
        <div class="overflow-x-auto">
          <table class="w-full">
            <thead>
              <tr class="border-b border-white/5">
                <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">时间</th>
                <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">金额</th>
                <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">支付方式</th>
                <th class="text-left text-xs font-medium text-slate-400 uppercase tracking-wider pb-3">状态</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-white/5">
              <tr v-for="record in paginatedHistory" :key="record.id" class="hover:bg-white/5 transition-colors">
                <td class="py-3 text-sm text-slate-500">{{ record.createdAt ? new Date(record.createdAt).toLocaleString() : '-' }}</td>
                <td class="py-3 text-sm text-emerald-400 font-medium">+¥{{ Number(record.amount || 0).toFixed(2) }}</td>
                <td class="py-3 text-sm text-slate-300">{{ record.paymentMethod === 'alipay' ? '支付宝' : record.paymentMethod === 'wechat' ? '微信支付' : record.paymentMethod === 'bank' ? '银行转账' : (record.paymentMethod || '-') }}</td>
                <td class="py-3">
                  <span :class="['px-2 py-0.5 text-xs rounded-full', record.status === 'SUCCESS' || record.status === 'COMPLETED' ? 'bg-emerald-500/10 text-emerald-400' : 'bg-amber-500/10 text-amber-400']">
                    {{ record.status === 'SUCCESS' || record.status === 'COMPLETED' ? '成功' : record.status === 'PENDING' ? '处理中' : (record.status || '-') }}
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-if="rechargeHistory.length > pageSize" class="flex items-center justify-between pt-4 mt-2 border-t border-white/5">
          <span class="text-sm text-slate-400">共 {{ rechargeHistory.length }} 条记录</span>
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
  </div>
</template>
