<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ChatDotRound, Document, HomeFilled, Promotion, Suitcase, TrendCharts, User } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const activePath = computed(() => route.path)

// 匹配分析页从岗位中心进入（需要 jobId），不在菜单中直接导航
const menuItems = [
  { path: '/dashboard', title: '首页', icon: HomeFilled },
  { path: '/chat', title: 'AI 对话', icon: ChatDotRound },
  { path: '/profile', title: '个人档案', icon: User },
  { path: '/resume', title: '我的简历', icon: Document },
  { path: '/jobs', title: '岗位中心', icon: Suitcase },
  { path: '/applications', title: '投递管理', icon: Promotion },
  { path: '/analytics', title: '反馈分析', icon: TrendCharts },
]

function handleLogout() {
  userStore.logout()
  router.push('/login')
}
</script>

<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="logo">
        <span class="logo-mark">C</span>
        <span class="logo-text">CareerFlow-AI</span>
      </div>
      <el-menu
        :default-active="activePath"
        router
        class="menu"
        background-color="transparent"
        text-color="#475569"
        active-text-color="#2563eb"
      >
        <el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.title }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-title">{{ route.meta.title ?? '' }}</div>
        <div class="header-user">
          <span class="header-username">{{ userStore.username || '未登录' }}</span>
          <el-button link type="primary" @click="handleLogout">退出</el-button>
        </div>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout {
  height: 100vh;
}

.aside {
  background: var(--cf-card-bg);
  border-right: 1px solid var(--cf-border);
  display: flex;
  flex-direction: column;
}

.logo {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 20px;
}

.logo-mark {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: linear-gradient(135deg, var(--cf-primary), var(--cf-primary-light));
  color: #fff;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.logo-text {
  font-size: 16px;
  font-weight: 700;
  color: var(--cf-text);
}

.menu {
  border-right: none;
  flex: 1;
}

.menu :deep(.el-menu-item.is-active) {
  background: #eff6ff;
  border-radius: 6px;
  margin: 0 8px;
  width: calc(100% - 16px);
}

.header {
  background: var(--cf-card-bg);
  border-bottom: 1px solid var(--cf-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 60px;
}

.header-title {
  font-size: 16px;
  font-weight: 600;
}

.header-user {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-username {
  color: var(--cf-text-secondary);
  font-size: 14px;
}

.main {
  padding: 24px;
}
</style>
