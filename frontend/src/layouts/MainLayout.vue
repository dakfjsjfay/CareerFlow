<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Bell,
  ChatDotRound,
  Document,
  Expand,
  Fold,
  HomeFilled,
  OfficeBuilding,
  Plus,
  Promotion,
  Suitcase,
  TrendCharts,
  User,
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

/**
 * 应用主框架（docs/2.0/ui-redesign.md §3）：
 * 侧边分组导航 + 顶部面包屑 + 内容区；<1024px 侧边栏改为抽屉。
 */
interface MenuItem {
  path: string
  title: string
  icon: unknown
}

interface MenuGroup {
  title: string
  items: MenuItem[]
}

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const COLLAPSE_KEY = 'cf.sidebar.collapsed'

const menuGroups: MenuGroup[] = [
  {
    title: '求职工作台',
    items: [{ path: '/dashboard', title: '工作台', icon: HomeFilled }],
  },
  {
    title: '招聘',
    items: [
      { path: '/jobs', title: '岗位中心', icon: Suitcase },
      { path: '/companies', title: '企业库', icon: OfficeBuilding },
      { path: '/applications', title: '投递管理', icon: Promotion },
    ],
  },
  {
    title: '我的简历',
    items: [
      { path: '/resumes', title: '简历中心', icon: Document },
      { path: '/chat', title: 'AI 对话建档', icon: ChatDotRound },
      { path: '/profile', title: '个人档案', icon: User },
    ],
  },
  {
    title: '数据与反馈',
    items: [{ path: '/analytics', title: '数据分析', icon: TrendCharts }],
  },
]

const collapsed = ref(localStorage.getItem(COLLAPSE_KEY) === '1')
const drawerOpen = ref(false)
const isMobile = ref(false)

function syncViewport() {
  isMobile.value = typeof window !== 'undefined' && window.innerWidth < 1024
  if (!isMobile.value) {
    drawerOpen.value = false
  }
}

onMounted(() => {
  syncViewport()
  window.addEventListener('resize', syncViewport)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', syncViewport)
})

watch(collapsed, (value) => {
  localStorage.setItem(COLLAPSE_KEY, value ? '1' : '0')
})

const currentGroup = computed<MenuGroup | undefined>(() =>
  menuGroups.find((group) => group.items.some((item) => isActive(item.path))),
)

function isActive(path: string): boolean {
  if (path === '/jobs') {
    return route.path === '/jobs' || route.path.startsWith('/match')
  }
  if (path === '/resumes') {
    return route.path.startsWith('/resume')
  }
  return route.path === path || route.path.startsWith(`${path}/`)
}

const activePath = computed(() => {
  for (const group of menuGroups) {
    const hit = group.items.find((item) => isActive(item.path))
    if (hit) {
      return hit.path
    }
  }
  return route.path
})

const pageTitle = computed(() => String(route.meta.title ?? ''))

function handleSelect(index: string) {
  drawerOpen.value = false
  router.push(index)
}

function handleLogout() {
  userStore.logout()
  router.push('/login')
}
</script>

<template>
  <div class="shell">
    <aside v-if="!isMobile" class="sidebar" :class="{ 'sidebar--collapsed': collapsed }">
      <div class="brand">
        <span class="brand__mark">C</span>
        <span v-if="!collapsed" class="brand__text">CareerFlow-AI</span>
      </div>

      <el-menu
        :default-active="activePath"
        :collapse="collapsed"
        :collapse-transition="false"
        class="menu"
        background-color="transparent"
        text-color="#475569"
        active-text-color="var(--cf-primary)"
        @select="handleSelect"
      >
        <template v-for="group in menuGroups" :key="group.title">
          <div v-if="!collapsed" class="menu__group">{{ group.title }}</div>
          <el-menu-item v-for="item in group.items" :key="item.path" :index="item.path">
            <el-icon><component :is="item.icon" /></el-icon>
            <template #title>{{ item.title }}</template>
          </el-menu-item>
        </template>
      </el-menu>

      <div class="sidebar__footer">
        <el-tooltip :content="collapsed ? '展开导航' : '收起导航'" placement="right">
          <el-button link class="collapse-btn" @click="collapsed = !collapsed">
            <el-icon><component :is="collapsed ? Expand : Fold" /></el-icon>
          </el-button>
        </el-tooltip>
      </div>
    </aside>

    <div v-if="isMobile" class="drawer" :class="{ 'drawer--open': drawerOpen }">
      <div class="drawer__mask" @click="drawerOpen = false" />
      <aside class="drawer__panel">
        <div class="brand">
          <span class="brand__mark">C</span>
          <span class="brand__text">CareerFlow-AI</span>
        </div>
        <el-menu
          :default-active="activePath"
          class="menu"
          background-color="transparent"
          text-color="#475569"
          active-text-color="var(--cf-primary)"
          @select="handleSelect"
        >
          <template v-for="group in menuGroups" :key="group.title">
            <div class="menu__group">{{ group.title }}</div>
            <el-menu-item v-for="item in group.items" :key="item.path" :index="item.path">
              <el-icon><component :is="item.icon" /></el-icon>
              <template #title>{{ item.title }}</template>
            </el-menu-item>
          </template>
        </el-menu>
      </aside>
    </div>

    <div class="shell__body">
      <header class="topbar">
        <div class="topbar__left">
          <el-button v-if="isMobile" link class="topbar__menu-btn" @click="drawerOpen = true">
            <el-icon><Expand /></el-icon>
          </el-button>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item v-if="currentGroup">{{ currentGroup.title }}</el-breadcrumb-item>
            <el-breadcrumb-item>{{ pageTitle }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <div class="topbar__right">
          <el-button class="topbar__new" type="primary" plain size="small" @click="router.push('/workspace/new')">
            <el-icon><Plus /></el-icon>
            <span>新建简历</span>
          </el-button>
          <el-tooltip content="待跟进提醒" placement="bottom">
            <el-button link class="topbar__bell" @click="router.push('/applications')">
              <el-icon><Bell /></el-icon>
            </el-button>
          </el-tooltip>
          <el-dropdown trigger="click">
            <span class="topbar__user">
              <span class="topbar__avatar">{{ (userStore.username || '访').slice(0, 1).toUpperCase() }}</span>
              <span class="topbar__name">{{ userStore.username || '未登录' }}</span>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="router.push('/profile')">个人档案</el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<style scoped>
.shell {
  display: flex;
  height: 100vh;
  overflow: hidden;
  background: var(--cf-bg);
}

.sidebar {
  width: var(--cf-sidebar-width);
  flex-shrink: 0;
  background: var(--cf-card-bg);
  border-right: 1px solid var(--cf-border);
  display: flex;
  flex-direction: column;
  transition: width 0.18s ease;
}

.sidebar--collapsed {
  width: var(--cf-sidebar-collapsed-width);
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 18px 20px;
  min-height: var(--cf-header-height);
}

.brand__mark {
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  border-radius: 8px;
  background: linear-gradient(135deg, var(--cf-primary), var(--cf-primary-light));
  color: #fff;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.brand__text {
  font-size: var(--cf-font-lg);
  font-weight: 700;
  color: var(--cf-text);
  white-space: nowrap;
}

.menu {
  border-right: none;
  flex: 1;
  overflow-y: auto;
  padding-bottom: var(--cf-space-3);
}

.menu__group {
  padding: var(--cf-space-3) 20px var(--cf-space-1);
  font-size: var(--cf-font-xs);
  color: var(--cf-text-tertiary);
  letter-spacing: 0.04em;
}

.menu :deep(.el-menu-item) {
  height: 40px;
  line-height: 40px;
  margin: 2px 8px;
  border-radius: var(--cf-radius-sm);
}

.menu :deep(.el-menu-item.is-active) {
  background: var(--cf-primary-soft);
  color: var(--cf-primary);
  font-weight: 600;
}

.sidebar__footer {
  border-top: 1px solid var(--cf-border);
  padding: var(--cf-space-2);
  display: flex;
  justify-content: flex-end;
}

.collapse-btn {
  color: var(--cf-text-secondary);
}

.shell__body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.topbar {
  height: var(--cf-header-height);
  flex-shrink: 0;
  background: var(--cf-card-bg);
  border-bottom: 1px solid var(--cf-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--cf-space-5);
  gap: var(--cf-space-3);
}

.topbar__left {
  display: flex;
  align-items: center;
  gap: var(--cf-space-2);
  min-width: 0;
}

.topbar__menu-btn {
  font-size: 18px;
}

.topbar__right {
  display: flex;
  align-items: center;
  gap: var(--cf-space-2);
}

.topbar__bell {
  font-size: 18px;
  color: var(--cf-text-secondary);
}

.topbar__user {
  display: flex;
  align-items: center;
  gap: var(--cf-space-2);
  cursor: pointer;
  color: var(--cf-text);
}

.topbar__avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: var(--cf-primary-soft);
  color: var(--cf-primary);
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
}

.topbar__name {
  font-size: var(--cf-font-sm);
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.content {
  flex: 1;
  overflow-y: auto;
  padding: var(--cf-space-5);
}

.drawer {
  position: fixed;
  inset: 0;
  z-index: 2000;
  pointer-events: none;
}

.drawer--open {
  pointer-events: auto;
}

.drawer__mask {
  position: absolute;
  inset: 0;
  background: rgba(15, 23, 42, 0.35);
  opacity: 0;
  transition: opacity 0.18s ease;
}

.drawer--open .drawer__mask {
  opacity: 1;
}

.drawer__panel {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 240px;
  background: var(--cf-card-bg);
  transform: translateX(-100%);
  transition: transform 0.2s ease;
  display: flex;
  flex-direction: column;
}

.drawer--open .drawer__panel {
  transform: translateX(0);
}

@media (max-width: 1023px) {
  .topbar {
    padding: 0 var(--cf-space-4);
  }

  .content {
    padding: var(--cf-space-4);
  }

  .topbar__new span {
    display: none;
  }
}
</style>
