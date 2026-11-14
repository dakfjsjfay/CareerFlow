<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { ref } from 'vue'

import type { DraftEducation, DraftExperience, DraftProject } from '@/api/resumeDraft'
import { useResumeDraftStore } from '@/stores/resumeDraft'
import { moveItem, newId } from '@/utils/resumeWizard'

/** 第 3 步：教育、工作/实习、项目经历的增删改与拖拽排序。 */
const store = useResumeDraftStore()

type DragSection = 'educations' | 'experiences' | 'projects'

const DEGREE_OPTIONS = ['大专', '本科', '硕士', '博士', '其他']
const KIND_OPTIONS = [
  { label: '工作', value: 'WORK' },
  { label: '实习', value: 'INTERNSHIP' },
  { label: '项目', value: 'PROJECT' },
]
const SKILL_SUGGESTIONS = [
  'Java',
  'Spring Boot',
  'MySQL',
  'Redis',
  'Vue 3',
  'TypeScript',
  'Python',
  'Docker',
  '数据分析',
  '项目管理',
]

const openEducation = ref<string[]>([])
const openExperience = ref<string[]>([])
const openProject = ref<string[]>([])
const dragState = ref<{ section: DragSection; index: number } | null>(null)
const overState = ref<{ section: DragSection; index: number } | null>(null)

function addEducation(): void {
  const item: DraftEducation = {
    id: newId(),
    school: '',
    major: '',
    degree: '',
    graduationYear: null,
    startDate: null,
    endDate: null,
    fieldSources: {},
  }
  store.content.educations.push(item)
  openEducation.value = [...openEducation.value, item.id]
}

function addExperience(): void {
  const item: DraftExperience = {
    id: newId(),
    kind: 'WORK',
    company: '',
    position: '',
    type: 'WORK',
    startDate: null,
    endDate: null,
    description: '',
    achievements: '',
    star: { situation: '', task: '', action: '', result: '' },
    skills: [],
    fieldSources: {},
  }
  store.content.experiences.push(item)
  openExperience.value = [...openExperience.value, item.id]
}

function addProject(): void {
  const item: DraftProject = {
    id: newId(),
    projectName: '',
    role: '',
    description: '',
    techStack: [],
    highlights: [],
    startDate: null,
    endDate: null,
    star: { situation: '', task: '', action: '', result: '' },
    fieldSources: {},
  }
  store.content.projects.push(item)
  openProject.value = [...openProject.value, item.id]
}

function mark(item: { fieldSources: Record<string, string> }, field: string): void {
  item.fieldSources[field] = 'MANUAL'
}

function dragStart(section: DragSection, index: number, event: DragEvent): void {
  dragState.value = { section, index }
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
    event.dataTransfer.setData('text/plain', String(index))
  }
}

function dragOver(section: DragSection, index: number): void {
  if (dragState.value?.section === section) {
    overState.value = { section, index }
  }
}

function clearOver(): void {
  overState.value = null
}

function endDrag(): void {
  dragState.value = null
  overState.value = null
}

function drop(section: DragSection, index: number): void {
  const state = dragState.value
  endDrag()
  if (!state || state.section !== section || state.index === index) {
    return
  }
  if (section === 'educations') {
    store.content.educations = moveItem(store.content.educations, state.index, index)
  } else if (section === 'experiences') {
    store.content.experiences = moveItem(store.content.experiences, state.index, index)
  } else {
    store.content.projects = moveItem(store.content.projects, state.index, index)
  }
}

function isOver(section: DragSection, index: number): boolean {
  return overState.value?.section === section && overState.value.index === index
}

async function confirmRemove(): Promise<boolean> {
  try {
    await ElMessageBox.confirm('删除后无法恢复，确定删除这条记录吗？', '确认删除', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
    return true
  } catch {
    return false
  }
}

async function removeEducation(index: number): Promise<void> {
  if (!(await confirmRemove())) {
    return
  }
  const removed = store.content.educations[index]
  store.content.educations.splice(index, 1)
  if (removed) {
    openEducation.value = openEducation.value.filter((id) => id !== removed.id)
  }
  ElMessage.success('已删除该教育经历')
}

async function removeExperience(index: number): Promise<void> {
  if (!(await confirmRemove())) {
    return
  }
  const removed = store.content.experiences[index]
  store.content.experiences.splice(index, 1)
  if (removed) {
    openExperience.value = openExperience.value.filter((id) => id !== removed.id)
  }
  ElMessage.success('已删除该经历')
}

async function removeProject(index: number): Promise<void> {
  if (!(await confirmRemove())) {
    return
  }
  const removed = store.content.projects[index]
  store.content.projects.splice(index, 1)
  if (removed) {
    openProject.value = openProject.value.filter((id) => id !== removed.id)
  }
  ElMessage.success('已删除该项目经历')
}

function kindLabel(kind: DraftExperience['kind']): string {
  return KIND_OPTIONS.find((option) => option.value === kind)?.label ?? '工作'
}

function educationTitle(item: DraftEducation): string {
  return item.school || '未命名教育经历'
}

function experienceTitle(item: DraftExperience): string {
  return item.company || item.position || '未命名经历'
}

function projectTitle(item: DraftProject): string {
  return item.projectName || '未命名项目'
}
</script>

<template>
  <section class="step-experience">
    <header class="step-head">
      <h2 class="step-head__title">经历补充</h2>
      <p class="step-head__desc">
        至少填写一条教育经历或项目/实习经历；拖拽左侧手柄可以调整展示顺序。
      </p>
    </header>

    <section class="group">
      <div class="group__head">
        <div>
          <h3 class="group__title">教育经历</h3>
          <p class="group__desc">按时间倒序排列更利于阅读。</p>
        </div>
        <el-button size="small" data-testid="add-education" @click="addEducation">
          添加教育经历
        </el-button>
      </div>

      <p v-if="store.content.educations.length === 0" class="group__empty">
        还没有教育经历，点击右上角按钮添加。
      </p>

      <article
        v-for="(item, index) in store.content.educations"
        :key="item.id"
        class="record"
        :class="{ 'record--over': isOver('educations', index) }"
        @dragover.prevent="dragOver('educations', index)"
        @dragleave="clearOver"
        @drop.prevent="drop('educations', index)"
      >
        <span
          class="record__handle"
          draggable="true"
          title="拖拽调整顺序"
          @dragstart="dragStart('educations', index, $event)"
          @dragend="endDrag"
        >
          ⋮⋮
        </span>
        <el-collapse v-model="openEducation" class="record__collapse">
          <el-collapse-item :name="item.id">
            <template #title>
              <span class="record__title">{{ educationTitle(item) }}</span>
              <span class="record__meta">{{ item.major || '未填写专业' }}</span>
            </template>

            <el-form label-position="top" @submit.prevent>
            <el-row :gutter="16">
              <el-col :xs="24" :sm="12">
                <el-form-item label="学校">
                  <el-input
                    v-model="item.school"
                    maxlength="100"
                    placeholder="请输入学校全称"
                    @input="mark(item, 'school')"
                  />
                </el-form-item>
              </el-col>
              <el-col :xs="24" :sm="12">
                <el-form-item label="专业">
                  <el-input
                    v-model="item.major"
                    maxlength="100"
                    placeholder="请输入专业名称"
                    @input="mark(item, 'major')"
                  />
                </el-form-item>
              </el-col>
            </el-row>

            <el-row :gutter="16">
              <el-col :xs="24" :sm="8">
                <el-form-item label="学历">
                  <el-select
                    v-model="item.degree"
                    class="full-width"
                    clearable
                    filterable
                    allow-create
                    placeholder="请选择学历"
                    @change="mark(item, 'degree')"
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
                    v-model="item.graduationYear"
                    class="full-width"
                    :min="1950"
                    :max="2100"
                    :controls="false"
                    placeholder="如 2027"
                    @change="mark(item, 'graduationYear')"
                  />
                </el-form-item>
              </el-col>
              <el-col :xs="12" :sm="4">
                <el-form-item label="开始时间">
                  <el-date-picker
                    v-model="item.startDate"
                    class="full-width"
                    type="month"
                    value-format="YYYY-MM"
                    placeholder="开始月份"
                    @change="mark(item, 'startDate')"
                  />
                </el-form-item>
              </el-col>
              <el-col :xs="12" :sm="4">
                <el-form-item label="结束时间">
                  <el-date-picker
                    v-model="item.endDate"
                    class="full-width"
                    type="month"
                    value-format="YYYY-MM"
                    placeholder="结束月份"
                    @change="mark(item, 'endDate')"
                  />
                </el-form-item>
              </el-col>
            </el-row>

            <el-button link type="danger" size="small" @click="removeEducation(index)">
              删除这条教育经历
            </el-button>
            </el-form>
          </el-collapse-item>
        </el-collapse>
      </article>
    </section>

    <section class="group">
      <div class="group__head">
        <div>
          <h3 class="group__title">工作 / 实习经历</h3>
          <p class="group__desc">描述职责与量化成果，第 4 步会帮你整理成 STAR 结构。</p>
        </div>
        <el-button size="small" data-testid="add-experience" @click="addExperience">
          添加工作/实习经历
        </el-button>
      </div>

      <p v-if="store.content.experiences.length === 0" class="group__empty">
        还没有工作或实习经历，点击右上角按钮添加。
      </p>

      <article
        v-for="(item, index) in store.content.experiences"
        :key="item.id"
        class="record"
        :class="{ 'record--over': isOver('experiences', index) }"
        @dragover.prevent="dragOver('experiences', index)"
        @dragleave="clearOver"
        @drop.prevent="drop('experiences', index)"
      >
        <span
          class="record__handle"
          draggable="true"
          title="拖拽调整顺序"
          @dragstart="dragStart('experiences', index, $event)"
          @dragend="endDrag"
        >
          ⋮⋮
        </span>
        <el-collapse v-model="openExperience" class="record__collapse">
          <el-collapse-item :name="item.id">
            <template #title>
              <span class="record__title">{{ experienceTitle(item) }}</span>
              <span class="record__meta">
                {{ kindLabel(item.kind) }}{{ item.position ? ` · ${item.position}` : '' }}
              </span>
            </template>

            <el-form label-position="top" @submit.prevent>
            <el-row :gutter="16">
              <el-col :xs="24" :sm="8">
                <el-form-item label="经历类型">
                  <el-select
                    v-model="item.kind"
                    class="full-width"
                    @change="mark(item, 'kind')"
                  >
                    <el-option
                      v-for="option in KIND_OPTIONS"
                      :key="option.value"
                      :label="option.label"
                      :value="option.value"
                    />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :xs="24" :sm="8">
                <el-form-item label="公司 / 单位">
                  <el-input
                    v-model="item.company"
                    maxlength="100"
                    placeholder="如 某某科技有限公司"
                    @input="mark(item, 'company')"
                  />
                </el-form-item>
              </el-col>
              <el-col :xs="24" :sm="8">
                <el-form-item label="职位">
                  <el-input
                    v-model="item.position"
                    maxlength="60"
                    placeholder="如 后端开发实习生"
                    @input="mark(item, 'position')"
                  />
                </el-form-item>
              </el-col>
            </el-row>

            <el-row :gutter="16">
              <el-col :xs="12" :sm="8">
                <el-form-item label="开始时间">
                  <el-date-picker
                    v-model="item.startDate"
                    class="full-width"
                    type="month"
                    value-format="YYYY-MM"
                    placeholder="开始月份"
                    @change="mark(item, 'startDate')"
                  />
                </el-form-item>
              </el-col>
              <el-col :xs="12" :sm="8">
                <el-form-item label="结束时间">
                  <el-date-picker
                    v-model="item.endDate"
                    class="full-width"
                    type="month"
                    value-format="YYYY-MM"
                    placeholder="结束月份"
                    @change="mark(item, 'endDate')"
                  />
                </el-form-item>
              </el-col>
              <el-col :xs="24" :sm="8">
                <el-form-item label="相关技能">
                  <el-select
                    v-model="item.skills"
                    class="full-width"
                    multiple
                    filterable
                    allow-create
                    default-first-option
                    :reserve-keyword="false"
                    placeholder="输入技能后回车添加"
                    @change="mark(item, 'skills')"
                  >
                    <el-option
                      v-for="option in SKILL_SUGGESTIONS"
                      :key="option"
                      :label="option"
                      :value="option"
                    />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>

            <el-form-item label="职责描述">
              <el-input
                v-model="item.description"
                type="textarea"
                :rows="3"
                maxlength="1000"
                show-word-limit
                placeholder="负责哪些工作、参与了哪些模块"
                @input="mark(item, 'description')"
              />
            </el-form-item>

            <el-form-item label="成果与数据">
              <el-input
                v-model="item.achievements"
                type="textarea"
                :rows="2"
                maxlength="500"
                show-word-limit
                placeholder="尽量量化，如：接口平均响应时间降低 30%"
                @input="mark(item, 'achievements')"
              />
            </el-form-item>

            <el-button link type="danger" size="small" @click="removeExperience(index)">
              删除这条经历
            </el-button>
            </el-form>
          </el-collapse-item>
        </el-collapse>
      </article>
    </section>

    <section class="group">
      <div class="group__head">
        <div>
          <h3 class="group__title">项目经历</h3>
          <p class="group__desc">突出你在项目中的角色、技术方案和最终结果。</p>
        </div>
        <el-button size="small" data-testid="add-project" @click="addProject">
          添加项目经历
        </el-button>
      </div>

      <p v-if="store.content.projects.length === 0" class="group__empty">
        还没有项目经历，点击右上角按钮添加。
      </p>

      <article
        v-for="(item, index) in store.content.projects"
        :key="item.id"
        class="record"
        :class="{ 'record--over': isOver('projects', index) }"
        @dragover.prevent="dragOver('projects', index)"
        @dragleave="clearOver"
        @drop.prevent="drop('projects', index)"
      >
        <span
          class="record__handle"
          draggable="true"
          title="拖拽调整顺序"
          @dragstart="dragStart('projects', index, $event)"
          @dragend="endDrag"
        >
          ⋮⋮
        </span>
        <el-collapse v-model="openProject" class="record__collapse">
          <el-collapse-item :name="item.id">
            <template #title>
              <span class="record__title">{{ projectTitle(item) }}</span>
              <span class="record__meta">{{ item.role || '未填写角色' }}</span>
            </template>

            <el-form label-position="top" @submit.prevent>
            <el-row :gutter="16">
              <el-col :xs="24" :sm="12">
                <el-form-item label="项目名称">
                  <el-input
                    v-model="item.projectName"
                    maxlength="100"
                    placeholder="请输入项目名称"
                    @input="mark(item, 'projectName')"
                  />
                </el-form-item>
              </el-col>
              <el-col :xs="24" :sm="12">
                <el-form-item label="担任角色">
                  <el-input
                    v-model="item.role"
                    maxlength="60"
                    placeholder="如 后端负责人"
                    @input="mark(item, 'role')"
                  />
                </el-form-item>
              </el-col>
            </el-row>

            <el-row :gutter="16">
              <el-col :xs="12" :sm="8">
                <el-form-item label="开始时间">
                  <el-date-picker
                    v-model="item.startDate"
                    class="full-width"
                    type="month"
                    value-format="YYYY-MM"
                    placeholder="开始月份"
                    @change="mark(item, 'startDate')"
                  />
                </el-form-item>
              </el-col>
              <el-col :xs="12" :sm="8">
                <el-form-item label="结束时间">
                  <el-date-picker
                    v-model="item.endDate"
                    class="full-width"
                    type="month"
                    value-format="YYYY-MM"
                    placeholder="结束月份"
                    @change="mark(item, 'endDate')"
                  />
                </el-form-item>
              </el-col>
              <el-col :xs="24" :sm="8">
                <el-form-item label="技术栈">
                  <el-select
                    v-model="item.techStack"
                    class="full-width"
                    multiple
                    filterable
                    allow-create
                    default-first-option
                    :reserve-keyword="false"
                    placeholder="输入技术后回车添加"
                    @change="mark(item, 'techStack')"
                  >
                    <el-option
                      v-for="option in SKILL_SUGGESTIONS"
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
                v-model="item.description"
                type="textarea"
                :rows="3"
                maxlength="1000"
                show-word-limit
                placeholder="项目背景、你负责的部分和实现思路"
                @input="mark(item, 'description')"
              />
            </el-form-item>

            <el-form-item label="项目亮点">
              <el-select
                v-model="item.highlights"
                class="full-width"
                multiple
                filterable
                allow-create
                default-first-option
                :reserve-keyword="false"
                placeholder="输入一条亮点后回车，可添加多条"
                @change="mark(item, 'highlights')"
              >
                <el-option
                  v-for="option in item.highlights"
                  :key="option"
                  :label="option"
                  :value="option"
                />
              </el-select>
            </el-form-item>

            <el-button link type="danger" size="small" @click="removeProject(index)">
              删除这个项目经历
            </el-button>
            </el-form>
          </el-collapse-item>
        </el-collapse>
      </article>
    </section>
  </section>
</template>

<style scoped>
.step-experience {
  max-width: 860px;
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

.group {
  padding: var(--cf-space-4);
  border: 1px solid var(--cf-border);
  border-radius: var(--cf-radius-md);
  background: var(--cf-bg);
}

.group + .group {
  margin-top: var(--cf-space-4);
}

.group__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--cf-space-3);
  margin-bottom: var(--cf-space-3);
}

.group__title {
  margin: 0;
  font-size: var(--cf-font-base);
  font-weight: 600;
  color: var(--cf-text);
}

.group__desc {
  margin: var(--cf-space-1) 0 0;
  font-size: var(--cf-font-xs);
  color: var(--cf-text-secondary);
}

.group__empty {
  margin: 0;
  padding: var(--cf-space-4);
  border: 1px dashed var(--cf-border-strong);
  border-radius: var(--cf-radius-sm);
  text-align: center;
  font-size: var(--cf-font-sm);
  color: var(--cf-text-tertiary);
  background: var(--cf-card-bg);
}

.record {
  display: flex;
  align-items: stretch;
  gap: var(--cf-space-2);
  border: 1px solid var(--cf-border);
  border-radius: var(--cf-radius-sm);
  background: var(--cf-card-bg);
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.record + .record {
  margin-top: var(--cf-space-2);
}

.record--over {
  border-color: var(--cf-primary);
  box-shadow: 0 0 0 2px rgb(37 99 235 / 12%);
}

.record__handle {
  display: flex;
  align-items: center;
  padding: 0 var(--cf-space-2);
  border-right: 1px solid var(--cf-border);
  color: var(--cf-text-tertiary);
  font-size: var(--cf-font-xs);
  letter-spacing: 1px;
  cursor: grab;
  user-select: none;
}

.record__handle:active {
  cursor: grabbing;
}

.record__collapse {
  flex: 1;
  min-width: 0;
  border: none;
}

.record__collapse :deep(.el-collapse-item__header) {
  padding: var(--cf-space-2) var(--cf-space-3);
  border-bottom: none;
  height: auto;
  min-height: 40px;
  line-height: 1.4;
}

.record__collapse :deep(.el-collapse-item__wrap) {
  border-bottom: none;
}

.record__collapse :deep(.el-collapse-item__content) {
  padding: var(--cf-space-3);
  border-top: 1px solid var(--cf-border);
}

.record__title {
  font-size: var(--cf-font-sm);
  font-weight: 600;
  color: var(--cf-text);
}

.record__meta {
  margin-left: var(--cf-space-2);
  font-size: var(--cf-font-xs);
  color: var(--cf-text-secondary);
}

.full-width {
  width: 100%;
}

@media (max-width: 767px) {
  .group__head {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
