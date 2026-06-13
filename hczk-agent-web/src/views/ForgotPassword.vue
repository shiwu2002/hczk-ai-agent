<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { publicFetch } from '@/stores/auth'
import { BrainCircuit, Lock, Mail, ShieldCheck, ArrowLeft, ArrowRight, CheckCircle } from 'lucide-vue-next'

const router = useRouter()

const email = ref('')
const code = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const isLoading = ref(false)
const isSendingCode = ref(false)
const error = ref('')
const success = ref(false)
const countdown = ref(0)

let countdownTimer = null

const canSubmit = computed(() =>
  email.value && code.value && newPassword.value.length >= 6 && confirmPassword.value
)

const passwordMismatch = computed(() =>
  confirmPassword.value && newPassword.value !== confirmPassword.value
)

async function sendCode() {
  if (!email.value) {
    error.value = '请输入邮箱'
    return
  }
  isSendingCode.value = true
  error.value = ''
  try {
    const data = await publicFetch('/auth/send-code', {
      method: 'POST',
      body: JSON.stringify({ email: email.value })
    })
    if (data.code !== 200) {
      throw new Error(data.message || '发送失败')
    }
    countdown.value = 60
    countdownTimer = setInterval(() => {
      countdown.value--
      if (countdown.value <= 0) {
        clearInterval(countdownTimer)
      }
    }, 1000)
  } catch (e) {
    error.value = e.message || '发送验证码失败'
  } finally {
    isSendingCode.value = false
  }
}

async function handleResetPassword() {
  if (!canSubmit.value) {
    error.value = '请填写所有字段'
    return
  }
  if (passwordMismatch.value) {
    error.value = '两次密码不一致'
    return
  }
  isLoading.value = true
  error.value = ''

  try {
    const data = await publicFetch('/auth/forgot-password', {
      method: 'POST',
      body: JSON.stringify({
        email: email.value,
        code: code.value,
        newPassword: newPassword.value
      })
    })
    if (data.code !== 200) {
      throw new Error(data.message || '重置密码失败')
    }
    success.value = true
  } catch (e) {
    error.value = e.message || '重置密码失败'
  } finally {
    isLoading.value = false
  }
}
</script>

<template>
  <div class="min-h-screen flex items-center justify-center relative overflow-hidden">
    <div class="absolute inset-0 bg-[#0a0f1c]">
      <div class="absolute top-1/4 left-1/4 w-96 h-96 bg-emerald-500/10 rounded-full blur-3xl animate-pulse-slow"></div>
      <div class="absolute bottom-1/4 right-1/4 w-80 h-80 bg-cyan-500/10 rounded-full blur-3xl animate-pulse-slow" style="animation-delay: 1.5s;"></div>
      <div class="absolute inset-0" style="background-image: radial-gradient(circle at 1px 1px, rgba(255,255,255,0.03) 1px, transparent 0); background-size: 40px 40px;"></div>
    </div>

    <div class="relative z-10 w-full max-w-md px-6">
      <div class="text-center mb-10">
        <div class="inline-flex items-center justify-center w-16 h-16 rounded-2xl bg-gradient-to-br from-emerald-500 to-cyan-500 mb-4 shadow-lg shadow-emerald-500/20">
          <BrainCircuit class="w-8 h-8 text-white" />
        </div>
        <h1 class="text-3xl font-bold text-white mb-2">桓宸智科AI平台</h1>
        <p class="text-slate-400">找回密码</p>
      </div>

      <div class="glass-card p-8">
        <!-- 重置成功提示 -->
        <div v-if="success" class="text-center py-8">
          <CheckCircle class="w-16 h-16 text-emerald-400 mx-auto mb-4" />
          <h2 class="text-xl font-semibold text-white mb-2">密码重置成功</h2>
          <p class="text-slate-400 mb-6">请使用新密码登录</p>
          <router-link to="/login" class="btn-primary inline-flex items-center gap-2 px-6 py-3">
            <ArrowLeft class="w-4 h-4" /> 返回登录
          </router-link>
        </div>

        <!-- 重置密码表单 -->
        <template v-else>
          <h2 class="text-xl font-semibold text-white mb-6">重置密码</h2>

          <form @submit.prevent="handleResetPassword" class="space-y-4">
            <div>
              <label class="block text-sm font-medium text-slate-300 mb-2">邮箱</label>
              <div class="relative">
                <Mail class="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-500" />
                <input v-model="email" type="email" class="input-field pl-10" placeholder="输入注册时的邮箱地址" />
              </div>
            </div>

            <div>
              <label class="block text-sm font-medium text-slate-300 mb-2">验证码</label>
              <div class="flex gap-3">
                <div class="relative flex-1">
                  <ShieldCheck class="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-500" />
                  <input v-model="code" type="text" class="input-field pl-10" placeholder="输入验证码" maxlength="6" />
                </div>
                <button
                  type="button"
                  @click="sendCode"
                  :disabled="countdown > 0 || isSendingCode"
                  class="btn-secondary whitespace-nowrap px-4 disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  <span v-if="isSendingCode">发送中...</span>
                  <span v-else-if="countdown > 0">{{ countdown }}s</span>
                  <span v-else>发送验证码</span>
                </button>
              </div>
            </div>

            <div>
              <label class="block text-sm font-medium text-slate-300 mb-2">新密码</label>
              <div class="relative">
                <Lock class="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-500" />
                <input v-model="newPassword" type="password" class="input-field pl-10" placeholder="6-20位新密码" />
              </div>
            </div>

            <div>
              <label class="block text-sm font-medium text-slate-300 mb-2">确认新密码</label>
              <div class="relative">
                <Lock class="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-500" />
                <input v-model="confirmPassword" type="password" class="input-field pl-10" placeholder="再次输入新密码" />
              </div>
              <p v-if="passwordMismatch" class="text-red-400 text-xs mt-1">两次密码不一致</p>
            </div>

            <div v-if="error" class="text-red-400 text-sm">{{ error }}</div>

            <button type="submit" class="btn-primary w-full flex items-center justify-center gap-2 py-3" :disabled="isLoading">
              <span v-if="isLoading">重置中...</span>
              <span v-else class="flex items-center gap-2">重置密码 <ArrowRight class="w-4 h-4" /></span>
            </button>
          </form>

          <div class="mt-6 pt-6 border-t border-white/5 text-center">
            <router-link to="/login" class="text-sm text-emerald-400 hover:text-emerald-300 inline-flex items-center gap-1">
              <ArrowLeft class="w-4 h-4" /> 返回登录
            </router-link>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>
