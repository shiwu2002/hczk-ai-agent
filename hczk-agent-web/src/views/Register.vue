<!-- 注册页面：用户通过邮箱验证码注册新账号，注册成功后自动登录并跳转 -->
<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore, publicFetch } from '@/stores/auth'
import { BrainCircuit, Lock, User, Mail, ShieldCheck, ArrowLeft, ArrowRight } from 'lucide-vue-next'

const router = useRouter()
const authStore = useAuthStore()

// 表单字段
const username = ref('')
const password = ref('')
const confirmPassword = ref('')
const email = ref('')
const code = ref('')

// UI 状态
const isLoading = ref(false)       // 注册请求进行中
const isSendingCode = ref(false)   // 验证码发送进行中
const error = ref('')              // 错误提示信息
const countdown = ref(0)           // 验证码重发倒计时（秒）

let countdownTimer = null

// 是否满足提交条件：所有字段已填写且密码至少6位
const canSubmit = computed(() =>
  username.value && password.value.length >= 6 && confirmPassword.value && email.value && code.value
)

// 两次密码是否不一致（仅在确认密码有值时判断）
const passwordMismatch = computed(() =>
  confirmPassword.value && password.value !== confirmPassword.value
)

/**
 * 发送邮箱验证码
 * 调用后端 /auth/send-code 接口，成功后启动 60 秒倒计时防止重复发送
 */
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
    // 启动 60 秒倒计时，倒计时期间按钮禁用
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

/**
 * 处理注册提交
 * 前端校验通过后调用 /auth/register，成功后自动登录并跳转到对应首页
 */
async function handleRegister() {
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
    const data = await publicFetch('/auth/register', {
      method: 'POST',
      body: JSON.stringify({
        username: username.value,
        password: password.value,
        email: email.value,
        code: code.value
      })
    })
    if (data.code !== 200) {
      throw new Error(data.message || '注册失败')
    }
    // 注册成功后自动登录
    await authStore.login({ username: username.value, password: password.value })
    router.push(authStore.isAdmin ? '/admin' : '/')
  } catch (e) {
    error.value = e.message || '注册失败'
  } finally {
    isLoading.value = false
  }
}
</script>

<template>
  <div class="min-h-screen flex items-center justify-center relative overflow-hidden">
    <!-- 背景层：深色底色 + 发光装饰 + 点阵纹理 -->
    <div class="absolute inset-0 bg-[#0a0f1c]">
      <div class="absolute top-1/4 left-1/4 w-96 h-96 bg-emerald-500/10 rounded-full blur-3xl animate-pulse-slow"></div>
      <div class="absolute bottom-1/4 right-1/4 w-80 h-80 bg-cyan-500/10 rounded-full blur-3xl animate-pulse-slow" style="animation-delay: 1.5s;"></div>
      <div class="absolute inset-0" style="background-image: radial-gradient(circle at 1px 1px, rgba(255,255,255,0.03) 1px, transparent 0); background-size: 40px 40px;"></div>
    </div>

    <div class="relative z-10 w-full max-w-md px-6">
      <!-- 品牌标识区域 -->
      <div class="text-center mb-10">
        <div class="inline-flex items-center justify-center w-16 h-16 rounded-2xl bg-gradient-to-br from-emerald-500 to-cyan-500 mb-4 shadow-lg shadow-emerald-500/20">
          <BrainCircuit class="w-8 h-8 text-white" />
        </div>
        <h1 class="text-3xl font-bold text-white mb-2">桓宸智科AI平台</h1>
        <p class="text-slate-400">注册新账号</p>
      </div>

      <!-- 注册表单卡片 -->
      <div class="glass-card p-8">
        <h2 class="text-xl font-semibold text-white mb-6">创建账号</h2>

        <form @submit.prevent="handleRegister" class="space-y-4">
          <!-- 用户名输入 -->
          <div>
            <label class="block text-sm font-medium text-slate-300 mb-2">用户名</label>
            <div class="relative">
              <User class="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-500" />
              <input v-model="username" type="text" class="input-field pl-10" placeholder="输入用户名" />
            </div>
          </div>

          <!-- 邮箱输入 -->
          <div>
            <label class="block text-sm font-medium text-slate-300 mb-2">邮箱</label>
            <div class="relative">
              <Mail class="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-500" />
              <input v-model="email" type="email" class="input-field pl-10" placeholder="输入邮箱地址" />
            </div>
          </div>

          <!-- 验证码输入 + 发送按钮 -->
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

          <!-- 密码输入 -->
          <div>
            <label class="block text-sm font-medium text-slate-300 mb-2">密码</label>
            <div class="relative">
              <Lock class="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-500" />
              <input v-model="password" type="password" class="input-field pl-10" placeholder="6-20位密码" />
            </div>
          </div>

          <!-- 确认密码输入 -->
          <div>
            <label class="block text-sm font-medium text-slate-300 mb-2">确认密码</label>
            <div class="relative">
              <Lock class="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-500" />
              <input v-model="confirmPassword" type="password" class="input-field pl-10" placeholder="再次输入密码" />
            </div>
            <p v-if="passwordMismatch" class="text-red-400 text-xs mt-1">两次密码不一致</p>
          </div>

          <!-- 错误提示 -->
          <div v-if="error" class="text-red-400 text-sm">{{ error }}</div>

          <!-- 提交按钮 -->
          <button type="submit" class="btn-primary w-full flex items-center justify-center gap-2 py-3" :disabled="isLoading">
            <span v-if="isLoading">注册中...</span>
            <span v-else class="flex items-center gap-2">注册 <ArrowRight class="w-4 h-4" /></span>
          </button>
        </form>

        <!-- 底部链接：返回登录 -->
        <div class="mt-6 pt-6 border-t border-white/5 text-center">
          <router-link to="/login" class="text-sm text-emerald-400 hover:text-emerald-300 inline-flex items-center gap-1">
            <ArrowLeft class="w-4 h-4" /> 已有账号？返回登录
          </router-link>
        </div>
      </div>
    </div>
  </div>
</template>
