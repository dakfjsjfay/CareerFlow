<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { login, register } from '@/api/auth'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({
  username: '',
  email: '',
  password: '',
})

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 50, message: '用户名长度需在 3~50 之间', trigger: 'blur' },
  ],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, message: '密码至少 8 个字符', trigger: 'blur' },
  ],
}

async function handleSubmit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  loading.value = true
  try {
    // 1. 注册
    await register({
      username: form.username,
      password: form.password,
      email: form.email.trim() || undefined,
    })
    // 2. 注册成功后自动登录，直接进入系统
    const res = await login({ username: form.username, password: form.password })
    userStore.setLogin(res.data.token, res.data.user.username)
    ElMessage.success('注册成功，欢迎使用！')
    router.push('/dashboard')
  } catch (e) {
    const err = e as { response?: { data?: { message?: string } } }
    ElMessage.error(err.response?.data?.message || '注册失败，请稍后重试')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <el-card class="auth-card">
      <div class="auth-brand">
        <span class="auth-mark">C</span>
        <span class="auth-name">CareerFlow-AI</span>
      </div>
      <h2>注册</h2>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @keyup.enter="handleSubmit">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="3~50 个字符" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="选填" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" placeholder="至少 8 个字符" show-password />
        </el-form-item>
        <el-button type="primary" class="auth-btn" :loading="loading" @click="handleSubmit">
          {{ loading ? '注册中…' : '注册' }}
        </el-button>
      </el-form>
      <p class="auth-tip">
        已有账号？
        <router-link to="/login" class="auth-link">去登录</router-link>
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
