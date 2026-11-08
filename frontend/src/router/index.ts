import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login/index.vue'),
    meta: { public: true, title: '登录' },
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/Register/index.vue'),
    meta: { public: true, title: '注册' },
  },
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'Dashboard', component: () => import('@/views/Dashboard/index.vue'), meta: { title: '首页' } },
      { path: 'chat', name: 'Chat', component: () => import('@/views/Chat/index.vue'), meta: { title: 'AI 对话' } },
      { path: 'profile', name: 'Profile', component: () => import('@/views/Profile/index.vue'), meta: { title: '个人档案' } },
      { path: 'resume', name: 'Resume', component: () => import('@/views/Resume/index.vue'), meta: { title: '我的简历' } },
      { path: 'jobs', name: 'Jobs', component: () => import('@/views/Jobs/index.vue'), meta: { title: '岗位中心' } },
      { path: 'match/:jobId', name: 'Match', component: () => import('@/views/Match/index.vue'), meta: { title: '匹配分析' } },
      { path: 'applications', name: 'Applications', component: () => import('@/views/Applications/index.vue'), meta: { title: '投递管理' } },
      { path: 'analytics', name: 'Analytics', component: () => import('@/views/Analytics/index.vue'), meta: { title: '反馈分析' } },
    ],
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 路由守卫骨架：无 token 跳登录页（D2 接入真实登录后完善）
router.beforeEach((to) => {
  const token = localStorage.getItem('token')
  if (!to.meta.public && !token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  return true
})

export default router
