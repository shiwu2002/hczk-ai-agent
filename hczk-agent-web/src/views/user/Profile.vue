<script setup>
import { ref, onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useApiStore } from '@/stores/api'
import { User, Mail, Save, Lock, KeyRound, Calendar, Hash } from 'lucide-vue-next'

const authStore = useAuthStore()
const api = useApiStore()

const profile = ref({ username: '', email: '', phoneNumber: '', companyName: '' })
const saving = ref(false)
const userId = ref('')
const createdAt = ref('')

// Password change
const passwordForm = ref({ oldPassword: '', newPassword: '', confirmPassword: '' })
const changingPassword = ref(false)

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
      oldPassword,
      newPassword
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
</script>

<template>
  <div class="space-y-6 animate-fade-in">
    <div>
      <h1 class="text-2xl font-bold text-white">个人中心</h1>
      <p class="text-slate-400 mt-1">管理您的个人信息与账户设置</p>
    </div>

    <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
      <div class="glass-card p-6">
        <div class="flex flex-col items-center">
          <div class="w-20 h-20 rounded-full bg-gradient-to-br from-emerald-500 to-cyan-500 flex items-center justify-center text-white text-2xl font-bold mb-4">
            {{ profile.username[0] || 'U' }}
          </div>
          <h3 class="text-xl font-semibold text-white">{{ profile.username }}</h3>
          <p class="text-sm text-slate-400 mt-1">{{ profile.email }}</p>
          <div class="mt-4 px-3 py-1 rounded-full bg-emerald-500/10 text-emerald-400 text-xs border border-emerald-500/20">
            {{ authStore.isAdmin ? '管理员' : '普通用户' }}
          </div>
        </div>
        <div class="mt-6 pt-6 border-t border-white/5 space-y-3">
          <div class="flex items-center justify-between">
            <span class="text-sm text-slate-400">账户余额</span>
            <span class="text-sm text-emerald-400">¥{{ authStore.currentUser?.balance?.toFixed(2) || '0.00' }}</span>
          </div>
          <div v-if="userId" class="flex items-center justify-between">
            <span class="flex items-center gap-1.5 text-sm text-slate-400"><Hash class="w-3.5 h-3.5" />用户ID</span>
            <span class="text-sm text-slate-300 font-mono truncate max-w-[160px]">{{ userId }}</span>
          </div>
          <div v-if="createdAt" class="flex items-center justify-between">
            <span class="flex items-center gap-1.5 text-sm text-slate-400"><Calendar class="w-3.5 h-3.5" />注册时间</span>
            <span class="text-sm text-slate-300">{{ new Date(createdAt).toLocaleDateString() }}</span>
          </div>
        </div>
      </div>

      <div class="lg:col-span-2 space-y-6">
        <div class="glass-card p-6">
          <h3 class="text-lg font-semibold text-white mb-6">编辑资料</h3>
          <div class="space-y-4">
            <div>
              <label class="block text-sm text-slate-300 mb-2">用户名</label>
              <div class="relative">
                <User class="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-500" />
                <input v-model="profile.username" class="input-field pl-10" readonly />
              </div>
            </div>
            <div>
              <label class="block text-sm text-slate-300 mb-2">邮箱</label>
              <div class="relative">
                <Mail class="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-500" />
                <input v-model="profile.email" class="input-field pl-10" readonly />
              </div>
            </div>
            <div>
              <label class="block text-sm text-slate-300 mb-2">手机号</label>
              <input v-model="profile.phoneNumber" class="input-field" placeholder="请输入手机号" />
            </div>
            <div>
              <label class="block text-sm text-slate-300 mb-2">公司</label>
              <input v-model="profile.companyName" class="input-field" placeholder="请输入公司名称" />
            </div>
          </div>
          <div class="mt-6">
            <button @click="saveProfile" :disabled="saving" class="btn-primary flex items-center gap-2">
              <Save class="w-4 h-4" /> {{ saving ? '保存中...' : '保存修改' }}
            </button>
          </div>
        </div>

        <!-- Password Change -->
        <div class="glass-card p-6">
          <h3 class="text-lg font-semibold text-white mb-6 flex items-center gap-2">
            <Lock class="w-5 h-5 text-slate-400" />修改密码
          </h3>
          <div class="space-y-4">
            <div>
              <label class="block text-sm text-slate-300 mb-2">旧密码</label>
              <div class="relative">
                <KeyRound class="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-500" />
                <input v-model="passwordForm.oldPassword" type="password" class="input-field pl-10" placeholder="请输入旧密码" />
              </div>
            </div>
            <div>
              <label class="block text-sm text-slate-300 mb-2">新密码</label>
              <div class="relative">
                <KeyRound class="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-500" />
                <input v-model="passwordForm.newPassword" type="password" class="input-field pl-10" placeholder="请输入新密码（至少6位）" />
              </div>
            </div>
            <div>
              <label class="block text-sm text-slate-300 mb-2">确认新密码</label>
              <div class="relative">
                <KeyRound class="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-500" />
                <input v-model="passwordForm.confirmPassword" type="password" class="input-field pl-10" placeholder="请再次输入新密码" />
              </div>
            </div>
          </div>
          <div class="mt-6">
            <button @click="changePassword" :disabled="changingPassword" class="btn-primary flex items-center gap-2">
              <Lock class="w-4 h-4" /> {{ changingPassword ? '修改中...' : '修改密码' }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
