<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'

import type { ProfileProject, ProjectPayload } from '@/api/profile'
import {
  emptyProjectForm,
  formToProjectPayload,
  projectToForm,
  type ProjectFormState,
} from '@/utils/profileForms'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    project?: ProfileProject | null
    submitting?: boolean
  }>(),
  { project: null, submitting: false },
)

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  save: [payload: ProjectPayload]
}>()

const TECH_SUGGESTIONS = [
  'Java',
  'Spring Boot',
  'MySQL',
  'Redis',
  'Vue 3',
  'TypeScript',
  'Python',
  'Docker',
]

const form = reactive<ProjectFormState>(emptyProjectForm())
const error = ref('')

const title = computed(() => (props.project ? '编辑项目经历' : '添加项目经历'))

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) {
      Object.assign(form, projectToForm(props.project))
      error.value = ''
    }
  },
  { immediate: true },
)

function close(): void {
  emit('update:modelValue', false)
}

function submit(): void {
  if (!form.projectName.trim()) {
    error.value = '项目名称不能为空'
    return
  }
  error.value = ''
  emit('save', formToProjectPayload(form))
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
      <el-form-item label="项目名称" required>
        <el-input
          v-model="form.projectName"
          data-testid="projectName"
          maxlength="200"
          placeholder="例如：学生管理系统"
        />
      </el-form-item>

      <el-row :gutter="16">
        <el-col :xs="24" :sm="12">
          <el-form-item label="个人角色">
            <el-input
              v-model="form.role"
              data-testid="projectRole"
              maxlength="100"
              placeholder="例如：后端开发"
            />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item label="技术栈">
            <el-select
              v-model="form.techStack"
              class="full-width"
              multiple
              filterable
              allow-create
              default-first-option
              :reserve-keyword="false"
              placeholder="输入技术后按回车添加"
            >
              <el-option
                v-for="option in TECH_SUGGESTIONS"
                :key="option"
                :label="option"
                :value="option"
              />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="项目描述">
        <el-input
          v-model="form.description"
          data-testid="projectDescription"
          type="textarea"
          :rows="3"
          placeholder="做了什么、解决了什么问题、产生了什么结果"
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
        data-testid="save-project"
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
.full-width {
  width: 100%;
}

.form-error {
  margin: 0;
  font-size: 12px;
  color: #d03b3b;
}
</style>
