import { createRouter, createWebHashHistory } from 'vue-router'
import { getToken } from './api'

const routes = [
  { path: '/', redirect: '/today' },
  { path: '/login', component: () => import('./views/Login.vue') },
  { path: '/today', component: () => import('./views/Today.vue') },
  { path: '/calendar', component: () => import('./views/Calendar.vue') },
  { path: '/profile', component: () => import('./views/Profile.vue') },
  { path: '/couple', component: () => import('./views/Couple.vue') },
  { path: '/achievements', component: () => import('./views/Achievements.vue') }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach((to) => {
  const hasToken = !!getToken()
  if (to.path !== '/login' && !hasToken) return '/login'
  if (to.path === '/login' && hasToken) return '/today'
  return true
})

export default router
