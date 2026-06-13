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
      path: '/',
      component: () => import('@/views/user/Layout.vue'),
      children: [
        { path: '', redirect: '/dashboard' },
        { path: 'dashboard', name: 'user-dashboard', component: () => import('@/views/user/Dashboard.vue') },
        { path: 'agents', name: 'user-agents', component: () => import('@/views/user/Agents.vue') },
        { path: 'usage', name: 'user-usage', component: () => import('@/views/user/Usage.vue') },
        { path: 'billing', name: 'user-billing', component: () => import('@/views/user/Billing.vue') },
        { path: 'recharge', name: 'user-recharge', component: () => import('@/views/user/Recharge.vue') },
        { path: 'apikeys', name: 'user-apikeys', component: () => import('@/views/user/ApiKeys.vue') },
        { path: 'profile', name: 'user-profile', component: () => import('@/views/user/Profile.vue') }
      ]
    },
    {
      path: '/admin',
      component: () => import('@/views/admin/Layout.vue'),
      children: [
        { path: '', redirect: '/admin/dashboard' },
        { path: 'dashboard', name: 'admin-dashboard', component: () => import('@/views/admin/Dashboard.vue') },
        { path: 'models', name: 'admin-models', component: () => import('@/views/admin/Models.vue') },
        { path: 'agents', name: 'admin-agents', component: () => import('@/views/admin/Agents.vue') },
        { path: 'apikeys', name: 'admin-apikeys', component: () => import('@/views/admin/ApiKeys.vue') },
        { path: 'users', name: 'admin-users', component: () => import('@/views/admin/Users.vue') },
        { path: 'billing', name: 'admin-billing', component: () => import('@/views/admin/Billing.vue') },
        { path: 'platforms', name: 'admin-platforms', component: () => import('@/views/admin/Platforms.vue') },
        { path: 'settings', name: 'admin-settings', component: () => import('@/views/admin/Settings.vue') }
      ]
    }
  ]
})

router.beforeEach((to, from) => {
  const authStore = useAuthStore()
  if (!to.meta.public && !authStore.isAuthenticated) {
    return '/login'
  } else if (to.path === '/login' && authStore.isAuthenticated) {
    return authStore.isAdmin ? '/admin' : '/'
  }
})

export default router
