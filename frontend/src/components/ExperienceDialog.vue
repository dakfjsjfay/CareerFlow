<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'

import type { ExperiencePayload, ProfileExperience } from '@/api/profile'
import {
  emptyExperienceForm,
  experienceToForm,
  formToExperiencePayload,
  type ExperienceFormState,
} from '@/utils/profileForms'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    experience?: ProfileExperience | null
    submitting?: boolean
  }>(),
  { experience: null, submitting: false },
)

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  save: [payload: ExperiencePayload]
}>()

const form = reactive<ExperienceFormState>(emptyExperienceForm())
const error = ref('')

const title = computed(() =>
  props.experience ? '编辑工作/实习经历' : '添加工作/实习经历',
)

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) {
      Object.assign(form, experienceToForm(props.experience))
      error.value = ''
    }
  },
  { immediate: true },
)

function close(): void {
  emit('update:modelValue', false)
}

function submit(): void {
  if (!form.company.trim()) {
    error.value = '公司/单位不能为空'
    return
  }
  error.value = ''
  emit('save', formToExperiencePayload(form))
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="title"
    width="620px"
    destroy-on-close
    @update:model-value="close"
  >
    <el-form label-position="top" @submit.prevent="submit">
      <el-row :gutter="16">
        <el-col :xs="24" :sm="12">
          <el-form-item label="公司/单位" required>
            <el-input
              v-model="form.company"
              data-testid="company"
              maxlength="200"
              placeholder="例如：某某科技"
            />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item label="职位">
            <el-input
              v-model="form.position"
              data-testid="position"
              maxlength="100"
              placeholder="例如：后端实习生"
            />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="职责描述">
        <el-input
          v-model="form.description"
          data-testid="experienceDescription"
          type="textarea"
          :rows="3"
          placeholder="负责什么工作、参与了哪些模块"
        />
      </el-form-item>

      <el-form-item label="成果">
        <el-input
          v-model="form.achievements"
          data-testid="achievements"
          type="textarea"
          :rows="2"
          placeholder="量化结果，例如：完成 3 个接口，响应时间降低 30%"
        />
      </el-form-item>

      <el-row :gutter="16">
        <el-col :xs="24" :sm="12">
          <el-form-item label="开始日期">
            <el-date-picker
              v-model="form.startDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="选择开始日期"
            />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item label="结束日期">
            <el-date-picker
              v-model="form.endDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="选择结束日期"
            />
          </el-form-item>
        </el-col>
      </el-row>

      <p v-if="error" class="form-error" data-testid="form-error">{{ error }}</p>
    </el-form>

    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button
        data-testid="save-experience"
        type="primary"
        :loading="submitting"
        :disabled="submitting"
        @click="submit"
      >
        保存
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.form-error {
  margin: 0;
  font-size: 12px;
  color: #d03b3b;
}
</style>
