<script setup lang="ts">
import { computed } from 'vue'

import { useResumeDraftStore } from '@/stores/resumeDraft'

/** 第 1 步：基础信息。必填项实时提示，输入即触发页面级自动保存。 */
const store = useResumeDraftStore()

const DEGREE_OPTIONS = ['大专', '本科', '硕士', '博士', '其他']
const EDUCATION_STATUS_OPTIONS = ['在读', '应届毕业生', '已毕业', '其他']
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

const nameError = computed(() =>
  store.content.basic.realName.trim() ? '' : '请填写姓名',
)

const contactError = computed(() =>
  store.content.basic.phone.trim() || store.content.basic.email?.trim()
    ? ''
    : '请至少填写手机号或邮箱',
)

const emailError = computed(() => {
  const email = store.content.basic.email?.trim() ?? ''
  if (!email) {
    return ''
  }
  return EMAIL_PATTERN.test(email) ? '' : '邮箱格式不正确，请检查后重新填写'
})
</script>

<template>
  <section class="step-basic">
    <header class="step-head">
      <h2 class="step-head__title">基础信息</h2>
      <p class="step-head__desc">带星号的字段为必填项，填写后会自动保存草稿。</p>
    </header>

    <el-form label-position="top" @submit.prevent>
      <el-row :gutter="16">
        <el-col :xs="24" :sm="12">
          <el-form-item label="姓名" required>
            <el-input
              v-model="store.content.basic.realName"
              data-testid="basic-real-name"
              maxlength="50"
              placeholder="请输入真实姓名"
            />
            <p v-if="nameError" class="field-error">{{ nameError }}</p>
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item label="手机号">
            <el-input
              v-model="store.content.basic.phone"
              data-testid="basic-phone"
              maxlength="20"
              placeholder="请输入手机号"
            />
            <p v-if="contactError" class="field-error">{{ contactError }}</p>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="16">
        <el-col :xs="24" :sm="12">
          <el-form-item label="邮箱">
            <el-input
              v-model="store.content.basic.email"
              data-testid="basic-email"
              maxlength="100"
              placeholder="请输入常用邮箱"
            />
            <p v-if="emailError" class="field-error">{{ emailError }}</p>
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item label="所在城市">
            <el-input
              v-model="store.content.basic.city"
              maxlength="30"
              placeholder="如 深圳"
            />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="16">
        <el-col :xs="24" :sm="12">
          <el-form-item label="学校">
            <el-input
              v-model="store.content.basic.school"
              maxlength="100"
              placeholder="请输入学校全称"
            />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item label="专业">
            <el-input
              v-model="store.content.basic.major"
              maxlength="100"
              placeholder="请输入专业名称"
            />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="16">
        <el-col :xs="24" :sm="8">
          <el-form-item label="学历">
            <el-select
              v-model="store.content.basic.degree"
              class="full-width"
              clearable
              filterable
              allow-create
              default-first-option
              placeholder="选择或输入学历"
            >
              <el-option
                v-for="option in DEGREE_OPTIONS"
                :key="option"
                :label="option"
                :value="option"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="8">
          <el-form-item label="毕业年份">
            <el-input-number
              v-model="store.content.basic.graduationYear"
              class="full-width"
              :min="1950"
              :max="2100"
              :controls="false"
              placeholder="如 2027"
            />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="8">
          <el-form-item label="教育状态">
            <el-select
              v-model="store.content.basic.educationStatus"
              class="full-width"
              clearable
              placeholder="请选择教育状态"
            >
              <el-option
                v-for="option in EDUCATION_STATUS_OPTIONS"
                :key="option"
                :label="option"
                :value="option"
              />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
  </section>
</template>

<style scoped>
.step-basic {
  max-width: 760px;
}

.step-head {
  margin-bottom: var(--cf-space-4);
}

.step-head__title {
  margin: 0;
  font-size: var(--cf-font-lg);
  font-weight: 600;
  color: var(--cf-text);
}

.step-head__desc {
  margin: var(--cf-space-1) 0 0;
  font-size: var(--cf-font-sm);
  color: var(--cf-text-secondary);
}

.full-width {
  width: 100%;
}

.field-error {
  margin: var(--cf-space-1) 0 0;
  font-size: var(--cf-font-xs);
  line-height: 1.5;
  color: var(--cf-danger);
}
</style>
