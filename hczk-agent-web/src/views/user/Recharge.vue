<script setup>
import { ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useApiStore } from '@/stores/api'
import { Wallet, Zap, Crown, Rocket, Check } from 'lucide-vue-next'

const authStore = useAuthStore()
const api = useApiStore()

const selectedPackage = ref(null)
const paymentMethod = ref('alipay')
const recharging = ref(false)

const packages = ref([
  { id: 1, name: '入门包', amount: 100, bonus: 0, icon: Zap, popular: false },
  { id: 2, name: '标准包', amount: 500, bonus: 50, icon: Zap, popular: true },
  { id: 3, name: '专业包', amount: 1000, bonus: 150, icon: Crown, popular: false },
  { id: 4, name: '企业包', amount: 5000, bonus: 1000, icon: Rocket, popular: false }
])

function selectPackage(pkg) {
  selectedPackage.value = pkg
}

async function recharge() {
  if (!selectedPackage.value) {
    alert('请选择充值套餐')
    return
  }
  recharging.value = true
  try {
    const res = await api.post(`/billing/recharge?userId=${authStore.user?.id}&amount=${selectedPackage.value.amount}&paymentMethod=${paymentMethod.value}`)
    if (res.code === 200) {
      alert('充值成功！')
      selectedPackage.value = null
    } else {
      alert(res.message || '充值失败')
    }
  } catch (e) {
    alert('充值失败')
  } finally {
    recharging.value = false
  }
}
</script>

<template>
  <div class="space-y-6 animate-fade-in">
    <div>
      <h1 class="text-2xl font-bold text-white">充值中心</h1>
      <p class="text-slate-400 mt-1">选择套餐充值，余额用于 Token 消耗计费</p>
    </div>

    <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
      <div v-for="pkg in packages" :key="pkg.id" @click="selectPackage(pkg)"
        :class="['glass-card p-6 cursor-pointer transition-all glow-border relative', selectedPackage?.id === pkg.id ? 'ring-2 ring-emerald-500 bg-emerald-500/5' : 'hover:bg-white/5']">
        <div v-if="pkg.popular" class="absolute -top-3 left-1/2 -translate-x-1/2 px-3 py-1 rounded-full bg-gradient-to-r from-emerald-500 to-cyan-500 text-white text-xs font-medium">最受欢迎</div>
        <div class="flex items-center justify-center mb-4">
          <div :class="['w-14 h-14 rounded-2xl flex items-center justify-center', selectedPackage?.id === pkg.id ? 'bg-emerald-500/20' : 'bg-white/5']">
            <component :is="pkg.icon" :class="['w-7 h-7', selectedPackage?.id === pkg.id ? 'text-emerald-400' : 'text-slate-400']" />
          </div>
        </div>
        <h3 class="text-lg font-semibold text-white text-center">{{ pkg.name }}</h3>
        <p class="text-3xl font-bold text-white text-center mt-3"><span class="text-lg">¥</span>{{ pkg.amount }}</p>
        <p v-if="pkg.bonus > 0" class="text-sm text-emerald-400 text-center mt-2">赠送 ¥{{ pkg.bonus }}</p>
        <p v-else class="text-sm text-slate-500 text-center mt-2">无赠送</p>
        <div v-if="selectedPackage?.id === pkg.id" class="mt-4 flex items-center justify-center">
          <div class="w-6 h-6 rounded-full bg-emerald-500 flex items-center justify-center"><Check class="w-4 h-4 text-white" /></div>
        </div>
      </div>
    </div>

    <div class="glass-card p-6">
      <h3 class="text-lg font-semibold text-white mb-4">支付方式</h3>
      <div class="flex gap-4">
        <label :class="['flex items-center gap-3 p-4 rounded-lg border cursor-pointer transition-all flex-1', paymentMethod === 'alipay' ? 'border-emerald-500/50 bg-emerald-500/5' : 'border-white/5 bg-white/5 hover:bg-white/10']">
          <input v-model="paymentMethod" type="radio" value="alipay" class="hidden" />
          <div class="w-10 h-10 rounded-lg bg-blue-500/20 flex items-center justify-center text-blue-400 font-bold text-sm">支</div>
          <div><p class="text-white font-medium">支付宝</p><p class="text-xs text-slate-400">即时到账</p></div>
        </label>
        <label :class="['flex items-center gap-3 p-4 rounded-lg border cursor-pointer transition-all flex-1', paymentMethod === 'wechat' ? 'border-emerald-500/50 bg-emerald-500/5' : 'border-white/5 bg-white/5 hover:bg-white/10']">
          <input v-model="paymentMethod" type="radio" value="wechat" class="hidden" />
          <div class="w-10 h-10 rounded-lg bg-green-500/20 flex items-center justify-center text-green-400 font-bold text-sm">微</div>
          <div><p class="text-white font-medium">微信支付</p><p class="text-xs text-slate-400">即时到账</p></div>
        </label>
      </div>

      <div v-if="selectedPackage" class="mt-6 pt-6 border-t border-white/5">
        <div class="flex items-center justify-between mb-4"><span class="text-slate-400">充值金额</span><span class="text-white font-medium">¥{{ selectedPackage.amount }}</span></div>
        <div class="flex items-center justify-between mb-4"><span class="text-slate-400">赠送金额</span><span class="text-emerald-400 font-medium">+¥{{ selectedPackage.bonus }}</span></div>
        <div class="flex items-center justify-between mb-6"><span class="text-white font-medium">实到账</span><span class="text-2xl font-bold text-emerald-400">¥{{ selectedPackage.amount + selectedPackage.bonus }}</span></div>
        <button @click="recharge" :disabled="recharging" class="btn-primary w-full py-3 text-lg">{{ recharging ? '处理中...' : '立即充值' }}</button>
      </div>
    </div>
  </div>
</template>
