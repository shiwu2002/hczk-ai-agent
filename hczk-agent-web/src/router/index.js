import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/Login.vue'),
      meta: { public: true }
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/views/Register.vue'),
      meta: { public: true }
    },
    {
      path: '/forgot-password',
      name: 'forgot-password',
      component: () => import('@/views/ForgotPassword.vue'),
      meta: { public: true }
    },
    {
      path: '/',
      component: () => import('@/views/user/Layout.vue'),
      children: [
        { path: '', redirect: '/dashboard' },
        { path: 'dashboard', name: 'user-dashboard', component: () => import('@/views/user/Dashboard.vue') },
        { path: 'agents', name: 'user-agents', component: () => import('@/views/user/Agents.vue') },
        { path: 'usage', name: 'user-usage', component: () => import('@/views/user/Usage.vue') },
        { path: 'billing', name: 'user-billing', component: () => import('@/views/user/Billing.vue') },
        { path: 'recharge', name: 'user-recharge', component: () => import('@/views/user/Recharge.vue') },
        { path: 'profile', name: 'user-profile', component: () => import('@/views/user/Profile.vue') }
      ]
    },
    {
      path: '/admin',
      component: () => import('@/views/admin/Layout.vue'),
      children: [
        { path: '', redirect: '/admin/dashboard' },
        { path: 'dashboard', name: 'admin-dashboard', component: () => import('@/views/admin/Dashboard.vue') },
        { path: 'monitor', name: 'admin-monitor', component: () => import('@/views/admin/Monitor.vue') },
        { path: 'agents', name: 'admin-agents', component: () => import('@/views/admin/Agents.vue') },
        { path: 'skills', name: 'admin-skills', component: () => import('@/views/admin/Skills.vue') },
        { path: 'knowledge', name: 'admin-knowledge', component: () => import('@/views/admin/Knowledge.vue') },
        { path: 'knowledge/:collectionName/chunks', name: 'admin-knowledge-chunks', component: () => import('@/views/admin/KnowledgeChunks.vue') },
        { path: 'models', name: 'admin-models', component: () => import('@/views/admin/Models.vue') },
        { path: 'bindings', name: 'admin-bindings', component: () => import('@/views/admin/Bindings.vue') },
        { path: 'apikeys', name: 'admin-apikeys', component: () => import('@/views/admin/ApiKeys.vue') },
        { path: 'users', name: 'admin-users', component: () => import('@/views/admin/Users.vue') },
        { path: 'billing', name: 'admin-billing', component: () => import('@/views/admin/Billing.vue') },
        { path: 'chat-logs', name: 'admin-chat-logs', component: () => import('@/views/admin/ChatLogs.vue') },
        { path: 'platforms', name: 'admin-platforms', component: () => import('@/views/admin/Platforms.vue') },
        { path: 'settings', name: 'admin-settings', component: () => import('@/views/admin/Settings.vue') }
      ]
    }
  ]
})

router.beforeEach((to, from) => {
  const authStore = useAuthStore()

  // 未登录访问受保护页面 → 跳转登录页
  if (!to.meta.public && !authStore.isAuthenticated) {
    return '/login'
  }

  // 已登录访问登录页 → 按角色跳转对应首页
  if (to.meta.public && authStore.isAuthenticated) {
    return authStore.isAdmin ? '/admin/dashboard' : '/dashboard'
  }

  // 管理员访问用户页面(/) → 跳转管理后台
  if (to.path.startsWith('/') && !to.path.startsWith('/admin') && authStore.isAdmin) {
    return '/admin/dashboard'
  }

  // 普通用户访问管理页面(/admin) → 跳转用户首页
  if (to.path.startsWith('/admin') && !authStore.isAdmin) {
    return '/dashboard'
  }
})

export default router
