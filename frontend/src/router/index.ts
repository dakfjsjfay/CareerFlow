import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

import { me } from '@/api/auth'
import { useUserStore } from '@/stores/user'

const routes: RouteRecordRaw[] = [
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
    path: '/workspace/new',
    name: 'WorkspaceNew',
    component: () => import('@/views/Workspace/index.vue'),
    meta: { title: '新建简历工作区', workspace: true },
  },
  {
    path: '/workspace/:resumeId(\\d+)',
    name: 'WorkspaceEdit',
    component: () => import('@/views/Workspace/index.vue'),
    meta: { title: '简历编辑工作区', workspace: true },
  },
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/Dashboard/index.vue'),
        meta: { title: '工作台' },
      },
      {
        path: 'chat',
        name: 'Chat',
        component: () => import('@/views/Chat/index.vue'),
        meta: { title: 'AI 对话建档' },
      },
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('@/views/Profile/index.vue'),
        meta: { title: '个人档案' },
      },
      {
        path: 'resumes',
        name: 'Resumes',
        component: () => import('@/views/Resume/index.vue'),
        meta: { title: '简历中心' },
      },
      {
        path: 'resumes/create',
        name: 'ResumeCreate',
        component: () => import('@/views/Resumes/Create/index.vue'),
        meta: { title: '新建简历', wizard: true },
      },
      // 兼容旧书签：/resume 统一跳到 /resumes
      { path: 'resume', redirect: '/resumes' },
      {
        path: 'jobs',
        name: 'Jobs',
        component: () => import('@/views/Jobs/index.vue'),
        meta: { title: '岗位中心' },
      },
      {
        path: 'match/:jobId',
        name: 'Match',
        component: () => import('@/views/Match/index.vue'),
        meta: { title: '匹配分析' },
      },
      {
        path: 'applications',
        name: 'Applications',
        component: () => import('@/views/Applications/index.vue'),
        meta: { title: '投递管理' },
      },
      {
        path: 'analytics',
        name: 'Analytics',
        component: () => import('@/views/Analytics/index.vue'),
        meta: { title: '数据分析' },
      },
    ],
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

/**
 * 登录态校验：localStorage 里只存 token 与用户名，进入受保护页面时
 * 首次调用 /api/auth/me 验证 token 是否仍然有效（过期/被改则回登录页）。
 */
let sessionVerified = false

router.beforeEach(async (to) => {
  const token = localStorage.getItem('token')
  if (to.meta.public) {
    return true
  }
  if (!token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (sessionVerified) {
    return true
  }
  try {
    const result = await me()
    const userStore = useUserStore()
    userStore.username = result.data.username || userStore.username
    sessionVerified = true
    return true
  } catch {
    sessionVerified = false
    useUserStore().logout()
    return { path: '/login', query: { redirect: to.fullPath } }
  }
})

router.afterEach((to) => {
  const title = typeof to.meta.title === 'string' ? to.meta.title : ''
  document.title = title ? `${title} · CareerFlow-AI` : 'CareerFlow-AI 智能求职助手'
})

export default router
