<script setup>
import { ref, computed, onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useApiStore } from '@/stores/api'
import {
  User, Mail, Save, Lock, KeyRound, Calendar, Hash,
  ShieldCheck, Eye, EyeOff, Building2, Phone, BadgeCheck, Wallet,
  Settings, Bell, Globe, Clock
} from 'lucide-vue-next'

const authStore = useAuthStore()
const api = useApiStore()

const profile = ref({ username: '', email: '', phoneNumber: '', companyName: '' })
const saving = ref(false)
const userId = ref('')
const createdAt = ref('')

const passwordForm = ref({ oldPassword: '', newPassword: '', confirmPassword: '' })
const changingPassword = ref(false)
const showOldPassword = ref(false)
const showNewPassword = ref(false)
const showConfirmPassword = ref(false)

const passwordStrength = computed(() => {
  const pw = passwordForm.value.newPassword
  if (!pw) return { level: 0, label: '', color: '' }
  let score = 0
  if (pw.length >= 6) score++
  if (pw.length >= 10) score++
  if (/[a-z]/.test(pw) && /[A-Z]/.test(pw)) score++
  if (/\d/.test(pw)) score++
  if (/[^a-zA-Z0-9]/.test(pw)) score++
  if (score <= 1) return { level: 1, label: '弱', color: 'bg-red-500' }
  if (score <= 3) return { level: 2, label: '中', color: 'bg-yellow-500' }
  return { level: 3, label: '强', color: 'bg-emerald-500' }
})

onMounted(async () => {
  const res = await api.get('/users/me')
  if (res.code === 200) {
    const u = res.data
    profile.value = { username: u.username, email: u.email, phoneNumber: u.phoneNumber || '', companyName: u.companyName || '' }
    userId.value = u.userId || ''
    createdAt.value = u.createdAt || ''
  }
})

async function saveProfile() {
  saving.value = true
  const res = await api.put(`/users/${authStore.currentUser?.userId}`, {
    phoneNumber: profile.value.phoneNumber,
    companyName: profile.value.companyName
  })
  if (res.code === 200) {
    alert('保存成功')
  } else {
    alert(res.message || '保存失败')
  }
  saving.value = false
}

async function changePassword() {
  const { oldPassword, newPassword, confirmPassword } = passwordForm.value
  if (!oldPassword || !newPassword || !confirmPassword) {
    alert('请填写所有密码字段')
    return
  }
  if (newPassword !== confirmPassword) {
    alert('两次输入的新密码不一致')
    return
  }
  if (newPassword.length < 6) {
    alert('新密码长度不能少于6位')
    return
  }
  changingPassword.value = true
  try {
    const res = await api.put(`/users/${authStore.currentUser?.userId}/password`, {
      oldPassword, newPassword
    })
    if (res.code === 200) {
      alert('密码修改成功')
      passwordForm.value = { oldPassword: '', newPassword: '', confirmPassword: '' }
    } else {
      alert(res.message || '密码修改失败')
    }
  } catch (e) {
    alert('密码修改失败')
  } finally {
    changingPassword.value = false
  }
}

function formatDate(dateStr) {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric' })
}

function daysSince(dateStr) {
  if (!dateStr) return 0
  const d = new Date(dateStr)
  const now = new Date()
  return Math.floor((now - d) / 86400000)
}
</script>

<template>
  <div class="space-y-6 animate-fade-in">
    <!-- Profile Header -->
    <div class="relative rounded-2xl overflow-hidden">
      <div class="absolute inset-0 bg-gradient-to-br from-emerald-600/15 via-cyan-600/8 to-transparent"></div>
      <div class="relative p-6 md:p-8">
        <div class="flex items-center gap-5">
          <div class="relative flex-shrink-0">
            <div class="w-16 h-16 md:w-20 md:h-20 rounded-2xl bg-gradient-to-br from-emerald-500 to-cyan-500 flex items-center justify-center text-white text-2xl md:text-3xl font-bold shadow-lg shadow-emerald-500/20">
              {{ profile.username[0] || 'U' }}
            </div>
            <div v-if="authStore.isAdmin" class="absolute -bottom-1 -right-1 w-6 h-6 rounded-full bg-emerald-500 flex items-center justify-center ring-2 ring-[#0a0f1c]">
              <BadgeCheck class="w-3.5 h-3.5 text-white" />
            </div>
          </div>
          <div class="flex-1 min-w-0">
            <div class="flex items-center gap-3 flex-wrap">
              <h1 class="text-xl md:text-2xl font-bold text-white">{{ profile.username }}</h1>
              <span class="px-2.5 py-0.5 rounded-md text-[11px] font-medium flex items-center gap-1"
                :class="authStore.isAdmin ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20' : 'bg-blue-500/10 text-blue-400 border border-blue-500/20'">
                <ShieldCheck class="w-3 h-3" />
                {{ authStore.isAdmin ? '管理员' : '普通用户' }}
              </span>
            </div>
            <p class="text-sm text-slate-400 mt-1">{{ profile.email }}</p>
            <div class="flex items-center gap-4 mt-2.5 flex-wrap">
              <span v-if="userId" class="text-[11px] text-slate-500 flex items-center gap-1">
                <Hash class="w-3 h-3" />{{ userId }}
              </span>
              <span v-if="createdAt" class="text-[11px] text-slate-500 flex items-center gap-1">
                <Clock class="w-3 h-3" />注册 {{ daysSince(createdAt) }} 天
              </span>
              <span class="text-[11px] text-emerald-400 flex items-center gap-1">
                <Wallet class="w-3 h-3" />¥{{ authStore.currentUser?.balance?.toFixed(2) || '0.00' }}
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Main Content -->
    <div class="grid grid-cols-1 lg:grid-cols-2 gap-5">
      <!-- Edit Profile -->
      <div class="glass-card rounded-2xl overflow-hidden">
        <div class="px-5 pt-4 pb-3 border-b border-white/5">
          <div class="flex items-center gap-2">
            <Settings class="w-4 h-4 text-cyan-400" />
            <h3 class="text-sm font-semibold text-white">编辑资料</h3>
          </div>
        </div>
        <div class="p-5 space-y-5">
          <!-- Read-only -->
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-[11px] text-slate-500 mb-1.5">用户名</label>
              <div class="relative">
                <User class="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-600" />
                <input v-model="profile.username" class="w-full pl-9 pr-3 py-2.5 rounded-lg bg-white/[0.03] border border-white/[0.06] text-sm text-slate-400 cursor-not-allowed" readonly />
              </div>
            </div>
            <div>
              <label class="block text-[11px] text-slate-500 mb-1.5">邮箱</label>
              <div class="relative">
                <Mail class="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-600" />
                <input v-model="profile.email" class="w-full pl-9 pr-3 py-2.5 rounded-lg bg-white/[0.03] border border-white/[0.06] text-sm text-slate-400 cursor-not-allowed" readonly />
              </div>
            </div>
          </div>

          <div class="h-px bg-white/5"></div>

          <!-- Editable -->
          <div class="space-y-3">
            <div>
              <label class="block text-[11px] text-slate-500 mb-1.5">手机号</label>
              <div class="relative">
                <Phone class="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-500" />
                <input v-model="profile.phoneNumber" class="w-full pl-9 pr-3 py-2.5 rounded-lg bg-white/[0.04] border border-white/[0.08] text-sm text-white placeholder-slate-600 focus:outline-none focus:border-emerald-500/40 focus:ring-1 focus:ring-emerald-500/20 transition-all" placeholder="请输入手机号" maxlength="11" />
              </div>
            </div>
            <div>
              <label class="block text-[11px] text-slate-500 mb-1.5">公司</label>
              <div class="relative">
                <Building2 class="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-500" />
                <input v-model="profile.companyName" class="w-full pl-9 pr-3 py-2.5 rounded-lg bg-white/[0.04] border border-white/[0.08] text-sm text-white placeholder-slate-600 focus:outline-none focus:border-emerald-500/40 focus:ring-1 focus:ring-emerald-500/20 transition-all" placeholder="请输入公司名称" maxlength="50" />
              </div>
            </div>
          </div>

          <button @click="saveProfile" :disabled="saving"
            class="w-full py-2.5 text-sm font-medium rounded-xl bg-gradient-to-r from-emerald-500 to-cyan-500 text-white shadow-lg shadow-emerald-500/20 hover:shadow-emerald-500/35 transition-all disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2">
            <Save class="w-4 h-4" :class="{ 'animate-spin': saving }" />
            {{ saving ? '保存中...' : '保存修改' }}
          </button>
        </div>
      </div>

      <!-- Change Password -->
      <div class="glass-card rounded-2xl overflow-hidden">
        <div class="px-5 pt-4 pb-3 border-b border-white/5">
          <div class="flex items-center gap-2">
            <Lock class="w-4 h-4 text-amber-400" />
            <h3 class="text-sm font-semibold text-white">修改密码</h3>
          </div>
        </div>
        <div class="p-5 space-y-4">
          <div>
            <label class="block text-[11px] text-slate-500 mb-1.5">旧密码</label>
            <div class="relative">
              <KeyRound class="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-500" />
              <input v-model="passwordForm.oldPassword"
                :type="showOldPassword ? 'text' : 'password'"
                class="w-full pl-9 pr-10 py-2.5 rounded-lg bg-white/[0.04] border border-white/[0.08] text-sm text-white placeholder-slate-600 focus:outline-none focus:border-emerald-500/40 focus:ring-1 focus:ring-emerald-500/20 transition-all"
                placeholder="请输入旧密码" />
              <button type="button" @click="showOldPassword = !showOldPassword"
                class="absolute right-3 top-1/2 -translate-y-1/2 text-slate-500 hover:text-slate-300 transition-colors">
                <Eye v-if="!showOldPassword" class="w-4 h-4" />
                <EyeOff v-else class="w-4 h-4" />
              </button>
            </div>
          </div>

          <div>
            <label class="block text-[11px] text-slate-500 mb-1.5">新密码</label>
            <div class="relative">
              <KeyRound class="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-500" />
              <input v-model="passwordForm.newPassword"
                :type="showNewPassword ? 'text' : 'password'"
                class="w-full pl-9 pr-10 py-2.5 rounded-lg bg-white/[0.04] border border-white/[0.08] text-sm text-white placeholder-slate-600 focus:outline-none focus:border-emerald-500/40 focus:ring-1 focus:ring-emerald-500/20 transition-all"
                placeholder="请输入新密码（至少6位）" />
              <button type="button" @click="showNewPassword = !showNewPassword"
                class="absolute right-3 top-1/2 -translate-y-1/2 text-slate-500 hover:text-slate-300 transition-colors">
                <Eye v-if="!showNewPassword" class="w-4 h-4" />
                <EyeOff v-else class="w-4 h-4" />
              </button>
            </div>
            <!-- Password strength -->
            <div v-if="passwordForm.newPassword" class="mt-2 flex items-center gap-2">
              <div class="flex-1 flex gap-1">
                <div class="h-1 flex-1 rounded-full transition-colors duration-300" :class="passwordStrength.level >= 1 ? passwordStrength.color : 'bg-white/10'"></div>
                <div class="h-1 flex-1 rounded-full transition-colors duration-300" :class="passwordStrength.level >= 2 ? passwordStrength.color : 'bg-white/10'"></div>
                <div class="h-1 flex-1 rounded-full transition-colors duration-300" :class="passwordStrength.level >= 3 ? passwordStrength.color : 'bg-white/10'"></div>
              </div>
              <span class="text-[11px] font-medium"
                :class="{ 'text-red-400': passwordStrength.level === 1, 'text-yellow-400': passwordStrength.level === 2, 'text-emerald-400': passwordStrength.level === 3 }">
                {{ passwordStrength.label }}
              </span>
            </div>
          </div>

          <div>
            <label class="block text-[11px] text-slate-500 mb-1.5">确认新密码</label>
            <div class="relative">
              <KeyRound class="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-500" />
              <input v-model="passwordForm.confirmPassword"
                :type="showConfirmPassword ? 'text' : 'password'"
                class="w-full pl-9 pr-10 py-2.5 rounded-lg bg-white/[0.04] border border-white/[0.08] text-sm text-white placeholder-slate-600 focus:outline-none focus:border-emerald-500/40 focus:ring-1 focus:ring-emerald-500/20 transition-all"
                placeholder="请再次输入新密码" />
              <button type="button" @click="showConfirmPassword = !showConfirmPassword"
                class="absolute right-3 top-1/2 -translate-y-1/2 text-slate-500 hover:text-slate-300 transition-colors">
                <Eye v-if="!showConfirmPassword" class="w-4 h-4" />
                <EyeOff v-else class="w-4 h-4" />
              </button>
            </div>
            <p v-if="passwordForm.confirmPassword && passwordForm.newPassword !== passwordForm.confirmPassword"
              class="text-[11px] text-red-400 mt-1">两次输入的密码不一致</p>
          </div>

          <button @click="changePassword" :disabled="changingPassword"
            class="w-full py-2.5 text-sm font-medium rounded-xl bg-white/[0.06] border border-white/[0.08] text-white hover:bg-white/[0.1] transition-all disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2">
            <Lock class="w-4 h-4" :class="{ 'animate-spin': changingPassword }" />
            {{ changingPassword ? '修改中...' : '修改密码' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
