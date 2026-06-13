<script setup>
import { ref, onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useApiStore } from '@/stores/api'
import { Save, Bell, Shield, Globe } from 'lucide-vue-next'

const authStore = useAuthStore()
const api = useApiStore()
const loading = ref(false)
const saving = ref(false)

const settings = ref({
  siteName: 'AI 智能体平台',
  registerOpen: true,
  defaultModel: '',
  notifyEmail: true,
  notifyWebhook: false,
  securityLog: true
})

const models = ref([])

const profile = ref({
  id: null,
  name: '',
  username: '',
  email: ''
})

onMounted(() => {
  loadData()
  loadModels()
})

async function loadData() {
  loading.value = true
  const res = await api.get('/users/me')
  if (res.code === 200) {
    const data = res.data || {}
    profile.value = {
      id: data.id,
      name: data.name || '',
      username: data.username || '',
      email: data.email || ''
    }
  }
  loading.value = false
}

async function loadModels() {
  const res = await api.get('/models')
  if (res.code === 200) {
    models.value = res.data || []
  }
}

async function saveSettings() {
  if (!profile.value.id) return
  saving.value = true
  const res = await api.put(`/users/${profile.value.id}`, {
    name: profile.value.name,
    username: profile.value.username,
    email: profile.value.email
  })
  if (res.code === 200) {
    alert('信息已更新')
    // 更新本地存储的用户信息
    if (authStore.user) {
      authStore.user.name = profile.value.name
      authStore.user.username = profile.value.username
      authStore.user.email = profile.value.email
      localStorage.setItem('user', JSON.stringify(authStore.user))
    }
  } else {
    alert(res.message || '更新失败')
  }
  saving.value = false
}
</script>

<template>
  <div class="space-y-6 animate-fade-in">
    <div>
      <h1 class="text-2xl font-bold text-white">系统设置</h1>
      <p class="text-slate-400 mt-1">平台基础配置与运行参数</p>
    </div>

    <div v-if="loading" class="text-slate-500">加载中...</div>
    <div v-else class="grid grid-cols-1 lg:grid-cols-3 gap-6">
      <div class="lg:col-span-2 space-y-6">
        <!-- Profile -->
        <div class="glass-card p-6">
          <div class="flex items-center gap-3 mb-6">
            <Globe class="w-5 h-5 text-emerald-400" />
            <h3 class="text-lg font-semibold text-white">管理员信息</h3>
          </div>
          <div class="space-y-4">
            <div>
              <label class="block text-sm text-slate-300 mb-2">昵称</label>
              <input v-model="profile.name" class="input-field" placeholder="您的昵称" />
            </div>
            <div>
              <label class="block text-sm text-slate-300 mb-2">用户名</label>
              <input v-model="profile.username" class="input-field" placeholder="用户名" />
            </div>
            <div>
              <label class="block text-sm text-slate-300 mb-2">邮箱</label>
              <input v-model="profile.email" class="input-field" placeholder="邮箱地址" />
            </div>
          </div>
        </div>

        <!-- Basic -->
        <div class="glass-card p-6">
          <div class="flex items-center gap-3 mb-6">
            <Globe class="w-5 h-5 text-emerald-400" />
            <h3 class="text-lg font-semibold text-white">基础设置</h3>
          </div>
          <div class="space-y-4">
            <div>
              <label class="block text-sm text-slate-300 mb-2">平台名称</label>
              <input v-model="settings.siteName" class="input-field" />
            </div>
            <div>
              <label class="block text-sm text-slate-300 mb-2">默认模型</label>
              <select v-model="settings.defaultModel" class="input-field">
                <option value="">请选择默认模型</option>
                <option v-for="m in models" :key="m.id" :value="m.name">{{ m.name }}</option>
              </select>
            </div>
            <label class="flex items-center gap-2 cursor-pointer">
              <input v-model="settings.registerOpen" type="checkbox" class="w-4 h-4 rounded border-slate-600 bg-slate-700 text-emerald-500" />
              <span class="text-sm text-slate-300">开放用户注册</span>
            </label>
          </div>
        </div>

        <!-- Notification -->
        <div class="glass-card p-6">
          <div class="flex items-center gap-3 mb-6">
            <Bell class="w-5 h-5 text-emerald-400" />
            <h3 class="text-lg font-semibold text-white">通知设置</h3>
          </div>
          <div class="space-y-4">
            <label class="flex items-center gap-2 cursor-pointer">
              <input v-model="settings.notifyEmail" type="checkbox" class="w-4 h-4 rounded border-slate-600 bg-slate-700 text-emerald-500" />
              <span class="text-sm text-slate-300">邮件通知</span>
            </label>
            <label class="flex items-center gap-2 cursor-pointer">
              <input v-model="settings.notifyWebhook" type="checkbox" class="w-4 h-4 rounded border-slate-600 bg-slate-700 text-emerald-500" />
              <span class="text-sm text-slate-300">Webhook 通知</span>
            </label>
          </div>
        </div>
      </div>

      <div class="space-y-6">
        <!-- Security -->
        <div class="glass-card p-6">
          <div class="flex items-center gap-3 mb-6">
            <Shield class="w-5 h-5 text-emerald-400" />
            <h3 class="text-lg font-semibold text-white">安全</h3>
          </div>
          <div class="space-y-4">
            <label class="flex items-center gap-2 cursor-pointer">
              <input v-model="settings.securityLog" type="checkbox" class="w-4 h-4 rounded border-slate-600 bg-slate-700 text-emerald-500" />
              <span class="text-sm text-slate-300">记录安全日志</span>
            </label>
            <div class="pt-4 border-t border-white/5">
              <button class="btn-secondary w-full">查看操作日志</button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="flex justify-end">
      <button @click="saveSettings" :disabled="saving" class="btn-primary flex items-center gap-2">
        <Save class="w-4 h-4" /> {{ saving ? '保存中...' : '保存设置' }}
      </button>
    </div>
  </div>
</template>
