<script setup>
import { RouterView } from 'vue-router'
import { useToastStore } from '@/stores/toast'
import { CheckCircle, XCircle, AlertTriangle, Info, X } from 'lucide-vue-next'

const toastStore = useToastStore()

const iconMap = {
  success: CheckCircle,
  error: XCircle,
  warning: AlertTriangle,
  info: Info
}

const colorMap = {
  success: 'text-emerald-400 border-emerald-500/30',
  error: 'text-red-400 border-red-500/30',
  warning: 'text-amber-400 border-amber-500/30',
  info: 'text-blue-400 border-blue-500/30'
}
</script>

<template>
  <RouterView />

  <!-- 全局 Toast 通知容器 -->
  <div class="fixed top-4 right-4 z-[9999] flex flex-col gap-2 pointer-events-none">
    <TransitionGroup name="toast">
      <div
        v-for="toast in toastStore.toasts"
        :key="toast.id"
        class="glass-card flex items-start gap-3 px-4 py-3 min-w-[280px] max-w-[420px] pointer-events-auto shadow-lg"
        :class="colorMap[toast.type]"
      >
        <component :is="iconMap[toast.type]" class="w-5 h-5 flex-shrink-0 mt-0.5" />
        <span class="text-sm text-slate-100 flex-1 break-words">{{ toast.message }}</span>
        <button
          class="text-slate-400 hover:text-slate-200 transition-colors flex-shrink-0"
          @click="toastStore.remove(toast.id)"
        >
          <X class="w-4 h-4" />
        </button>
      </div>
    </TransitionGroup>
  </div>
</template>

<style>
@import url('https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&family=JetBrains+Mono:wght@400;500&display=swap');

.toast-enter-active,
.toast-leave-active {
  transition: all 0.3s ease;
}
.toast-enter-from {
  opacity: 0;
  transform: translateX(100%);
}
.toast-leave-to {
  opacity: 0;
  transform: translateX(100%);
}
</style>
