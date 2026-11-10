<script setup lang="ts">
import { reactive, watch } from 'vue'

import type { ProfileData, ProfileUpdatePayload } from '@/api/profile'
import {
  emptyProfileForm,
  formToProfilePayload,
  profileToForm,
  type ProfileFormState,
} from '@/utils/profileForms'

const props = withDefaults(
  defineProps<{
    profile: ProfileData | null
    saving?: boolean
  }>(),
  { saving: false },
)

const emit = defineEmits<{ save: [payload: ProfileUpdatePayload] }>()

const DEGREE_OPTIONS = ['大专', '本科', '硕士', '博士', '其他']
const SKILL_SUGGESTIONS = [
  'Java',
  'Spring Boot',
  'MySQL',
  'Redis',
  'Vue 3',
  'TypeScript',
  'Python',
  'Docker',
]

const form = reactive<ProfileFormState>(emptyProfileForm())

watch(
  () => props.profile,
  (profile) => {
    Object.assign(form, profileToForm(profile))
  },
  { immediate: true },
)

function submit(): void {
  emit('save', formToProfilePayload(form))
}
</script>

<template>
  <el-form class="profile-form" label-position="top" @submit.prevent="submit">
    <section class="form-section">
      <h3 class="section-title">基础信息</h3>
      <el-row :gutter="16">
        <el-col :xs="24" :sm="12">
          <el-form-item label="姓名">
            <el-input v-model="form.realName" data-testid="realName" maxlength="50" />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item label="电话">
            <el-input v-model="form.phone" data-testid="phone" maxlength="11" />
          </el-form-item>
        </el-col>
      </el-row>
    </section>

    <section class="form-section">
      <h3 class="section-title">教育经历</h3>
      <el-row :gutter="16">
        <el-col :xs="24" :sm="12">
          <el-form-item label="学校">
            <el-input v-model="form.school" data-testid="school" maxlength="100" />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item label="专业">
            <el-input v-model="form.major" data-testid="major" maxlength="100" />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item label="学历">
            <el-select
              v-model="form.degree"
              data-testid="degree"
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
        <el-col :xs="24" :sm="12">
          <el-form-item label="毕业年份">
            <el-input-number
              v-model="form.graduationYear"
              data-testid="graduationYear"
              :min="1900"
              :max="2100"
              :controls="false"
              placeholder="如 2027"
            />
          </el-form-item>
        </el-col>
      </el-row>
    </section>

    <section class="form-section">
      <h3 class="section-title">技能</h3>
      <el-form-item label="技能列表">
        <el-select
          v-model="form.skills"
          class="full-width"
          data-testid="skills"
          multiple
          filterable
          allow-create
          default-first-option
          :reserve-keyword="false"
          placeholder="输入技能后按回车添加，例如 Java"
        >
          <el-option
            v-for="option in SKILL_SUGGESTIONS"
            :key="option"
            :label="option"
            :value="option"
          />
        </el-select>
      </el-form-item>
    </section>

    <section class="form-section">
      <h3 class="section-title">求职意向</h3>
      <el-row :gutter="16">
        <el-col :xs="24" :sm="8">
          <el-form-item label="目标岗位">
            <el-input
              v-model="form.targetPosition"
              data-testid="targetPosition"
              maxlength="10"
            />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="8">
          <el-form-item label="意向城市">
            <el-input v-model="form.targetCity" data-testid="targetCity" maxlength="4" />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="8">
          <el-form-item label="期望薪资">
            <el-input
              v-model="form.expectedSalary"
              data-testid="expectedSalary"
              maxlength="50"
              placeholder="如 8-12K"
            />
          </el-form-item>
        </el-col>
      </el-row>
    </section>

    <footer class="form-footer">
      <p class="footer-hint">保存后，每个实际变更的字段都会生成一条可追溯的手动证据。</p>
      <el-button
        data-testid="save-profile"
        type="primary"
        :loading="saving"
        :disabled="saving"
        @click="submit"
      >
        保存修改
      </el-button>
    </footer>
  </el-form>
</template>

<style scoped>
.profile-form {
  display: flex;
  flex-direction: column;
}

.form-section + .form-section {
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid var(--cf-border);
}

.section-title {
  margin: 0 0 14px;
  font-size: 14px;
  font-weight: 600;
  color: var(--cf-text);
}

.full-width {
  width: 100%;
}

.form-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 24px;
  padding-top: 18px;
  border-top: 1px solid var(--cf-border);
}

.footer-hint {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--cf-text-secondary);
}
</style>
