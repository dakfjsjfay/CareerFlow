<script setup lang="ts">
import { reactive } from 'vue'

// 登录表单状态（D2 接入 /api/auth/login 时使用）
const form = reactive({
  username: '',
  password: '',
})
</script>

<template>
  <div class="auth-page">
    <el-card class="auth-card">
      <div class="auth-brand">
        <span class="auth-mark">C</span>
        <span class="auth-name">CareerFlow-AI</span>
      </div>
      <h2>登录</h2>
      <el-form label-position="top">
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" show-password />
        </el-form-item>
        <el-button type="primary" class="auth-btn">登录（D2 接入）</el-button>
      </el-form>
      <p class="auth-tip">
        还没有账号？
        <router-link to="/register" class="auth-link">去注册</router-link>
      </p>
    </el-card>
  </div>
</template>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #eff6ff 0%, var(--cf-bg) 60%);
}

.auth-card {
  width: 380px;
  padding: 8px;
}

.auth-brand {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  margin-bottom: 8px;
}

.auth-mark {
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

.auth-name {
  font-weight: 700;
  font-size: 16px;
}

h2 {
  text-align: center;
  font-size: 20px;
  margin-bottom: 20px;
}

.auth-btn {
  width: 100%;
}

.auth-tip {
  margin-top: 14px;
  text-align: center;
  font-size: 13px;
  color: var(--cf-text-secondary);
}

.auth-link {
  color: var(--cf-primary);
}
</style>
