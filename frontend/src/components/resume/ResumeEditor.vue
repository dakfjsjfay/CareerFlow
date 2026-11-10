<template>
  <div class="resume-editor">
    <section>
      <h3 class="section-title">基本信息</h3>
      <el-form label-position="top" size="small">
        <div class="grid-2">
          <el-form-item label="姓名">
            <el-input v-model="content.personalInfo.realName" placeholder="姓名" />
          </el-form-item>
          <el-form-item label="电话">
            <el-input v-model="content.personalInfo.phone" placeholder="联系电话" />
          </el-form-item>
          <el-form-item label="邮箱">
            <el-input v-model="content.personalInfo.email" placeholder="邮箱（可选）" />
          </el-form-item>
          <el-form-item label="城市">
            <el-input v-model="content.personalInfo.city" placeholder="所在城市（可选）" />
          </el-form-item>
          <el-form-item label="学校">
            <el-input v-model="content.personalInfo.school" placeholder="学校" />
          </el-form-item>
          <el-form-item label="专业">
            <el-input v-model="content.personalInfo.major" placeholder="专业" />
          </el-form-item>
          <el-form-item label="学历">
            <el-input v-model="content.personalInfo.degree" placeholder="本科 / 硕士" />
          </el-form-item>
          <el-form-item label="毕业年份">
            <el-input-number
              v-model="content.personalInfo.graduationYear"
              :min="1990"
              :max="2100"
              controls-position="right"
              class="full-width"
            />
          </el-form-item>
        </div>
      </el-form>
    </section>

    <section>
      <h3 class="section-title">求职意向</h3>
      <el-form label-position="top" size="small">
        <div class="grid-2">
          <el-form-item label="目标岗位">
            <el-input v-model="content.jobIntention.targetPosition" placeholder="Java 开发实习生" />
          </el-form-item>
          <el-form-item label="意向城市">
            <el-input v-model="content.jobIntention.targetCity" placeholder="深圳" />
          </el-form-item>
          <el-form-item label="期望薪资">
            <el-input v-model="content.jobIntention.expectedSalary" placeholder="面议" />
          </el-form-item>
        </div>
      </el-form>
    </section>

    <section>
      <h3 class="section-title">教育背景</h3>
      <div v-if="content.education.length === 0" class="empty-row">
        <span>暂无教育经历</span>
        <el-button size="small" @click="addEducation">添加</el-button>
      </div>
      <div v-for="(item, index) in content.education" :key="index" class="edit-card">
        <div class="grid-2">
          <el-form-item label="学校">
            <el-input v-model="item.school" />
          </el-form-item>
          <el-form-item label="专业">
            <el-input v-model="item.major" />
          </el-form-item>
          <el-form-item label="学历">
            <el-input v-model="item.degree" />
          </el-form-item>
          <el-form-item label="毕业年份">
            <el-input-number v-model="item.graduationYear" :min="1990" :max="2100" />
          </el-form-item>
        </div>
        <el-button link type="danger" size="small" @click="content.education.splice(index, 1)">
          删除
        </el-button>
      </div>
    </section>

    <section>
      <h3 class="section-title">技能特长</h3>
      <el-input
        v-model="skillsText"
        type="textarea"
        :rows="2"
        placeholder="用顿号或逗号分隔，例如：Java、MySQL、Vue"
      />
    </section>

    <section>
      <div class="section-head">
        <h3 class="section-title">项目经历</h3>
        <el-button size="small" @click="addProject">添加项目</el-button>
      </div>
      <div v-for="(project, index) in content.projects" :key="index" class="edit-card">
        <div class="grid-2">
          <el-form-item label="项目名称">
            <el-input v-model="project.projectName" />
          </el-form-item>
          <el-form-item label="担任角色">
            <el-input v-model="project.role" />
          </el-form-item>
        </div>
        <el-form-item label="项目描述">
          <el-input v-model="project.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="技术栈">
          <el-input
            :model-value="(project.techStack ?? []).join('、')"
            placeholder="Java、MySQL"
            @update:model-value="(value) => updateTechStack(project, String(value))"
          />
        </el-form-item>
        <div class="grid-2">
          <el-form-item label="开始时间">
            <el-date-picker
              v-model="project.startDate"
              type="date"
              value-format="YYYY-MM-DD"
              class="full-width"
            />
          </el-form-item>
          <el-form-item label="结束时间">
            <el-date-picker
              v-model="project.endDate"
              type="date"
              value-format="YYYY-MM-DD"
              class="full-width"
            />
          </el-form-item>
        </div>
        <el-button link type="danger" size="small" @click="content.projects.splice(index, 1)">
          删除
        </el-button>
      </div>
    </section>

    <section>
      <div class="section-head">
        <h3 class="section-title">工作 / 实习经历</h3>
        <el-button size="small" @click="addExperience">添加经历</el-button>
      </div>
      <div v-for="(experience, index) in content.experiences" :key="index" class="edit-card">
        <div class="grid-2">
          <el-form-item label="公司 / 单位">
            <el-input v-model="experience.company" />
          </el-form-item>
          <el-form-item label="职位">
            <el-input v-model="experience.position" />
          </el-form-item>
          <el-form-item label="类型">
            <el-select v-model="experience.type" class="full-width">
              <el-option label="工作" value="WORK" />
              <el-option label="实习" value="INTERNSHIP" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="工作内容">
          <el-input v-model="experience.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="成果">
          <el-input v-model="experience.achievements" type="textarea" :rows="2" />
        </el-form-item>
        <div class="grid-2">
          <el-form-item label="开始时间">
            <el-date-picker
              v-model="experience.startDate"
              type="date"
              value-format="YYYY-MM-DD"
              class="full-width"
            />
          </el-form-item>
          <el-form-item label="结束时间">
            <el-date-picker
              v-model="experience.endDate"
              type="date"
              value-format="YYYY-MM-DD"
              class="full-width"
            />
          </el-form-item>
        </div>
        <el-button link type="danger" size="small" @click="content.experiences.splice(index, 1)">
          删除
        </el-button>
      </div>
    </section>

    <section>
      <h3 class="section-title">自我评价</h3>
      <el-input
        v-model="content.selfEvaluation"
        data-testid="self-evaluation"
        type="textarea"
        :rows="4"
      />
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import type { ResumeContent, ResumeProject } from '@/components/resume-template/types'

const props = defineProps<{ content: ResumeContent }>()

const skillsText = computed({
  get: () => (props.content.skills ?? []).join('、'),
  set: (value: string) => {
    props.content.skills = value
      .split(/[,，、\s]+/)
      .map((item) => item.trim())
      .filter(Boolean)
  },
})

function splitTags(value: string): string[] {
  return value
    .split(/[,，、\s]+/)
    .map((item) => item.trim())
    .filter(Boolean)
}

function updateTechStack(project: ResumeProject, value: string): void {
  project.techStack = splitTags(value)
}

function addEducation(): void {
  props.content.education.push({
    school: '',
    major: '',
    degree: '',
    graduationYear: null,
    startDate: null,
    endDate: null,
  })
}

function addProject(): void {
  props.content.projects.push({
    projectName: '',
    role: '',
    description: '',
    techStack: [],
    highlights: [],
    startDate: null,
    endDate: null,
  })
}

function addExperience(): void {
  props.content.experiences.push({
    company: '',
    position: '',
    type: 'INTERNSHIP',
    description: '',
    achievements: '',
    highlights: [],
    startDate: null,
    endDate: null,
  })
}
</script>

<style scoped>
.resume-editor {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

section {
  background: var(--cf-card-bg);
  border: 1px solid var(--cf-border);
  border-radius: 10px;
  padding: 16px;
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--cf-text);
  margin-bottom: 12px;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.grid-2 {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}

.full-width {
  width: 100%;
}

.edit-card {
  border: 1px dashed var(--cf-border);
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 12px;
}

.empty-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--cf-text-secondary);
  font-size: 13px;
  padding: 8px 0;
}
</style>
