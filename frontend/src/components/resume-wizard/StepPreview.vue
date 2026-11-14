<script setup lang="ts">
import { computed, ref } from 'vue'

import TemplateGalleryDialog from '@/components/resume/TemplateGalleryDialog.vue'
import { TEMPLATES } from '@/components/resume-template/manifest'
import { useResumeDraftStore } from '@/stores/resumeDraft'
import { moveItem } from '@/utils/resumeWizard'

/** 第 7 步：预览微调（排序、模板、字体、内容溢出估算）。 */
type SortSection = 'experiences' | 'projects'

const store = useResumeDraftStore()

const FONT_OPTIONS = [
  { label: '默认无衬线', value: 'Helvetica Neue' },
  { label: '苹方', value: 'PingFang SC' },
  { label: '微软雅黑', value: 'Microsoft YaHei' },
  { label: '宋体', value: 'Songti SC' },
  { label: '楷体', value: 'KaiTi' },
]
const FONT_SIZE_OPTIONS = [12, 13, 14, 15, 16]
const LINE_HEIGHT_OPTIONS = [1.4, 1.5, 1.6, 1.8]
const SPACING_OPTIONS = [12, 16, 20, 28]

const galleryVisible = ref(false)
const dragState = ref<{ section: SortSection; index: number } | null>(null)
const overState = ref<{ section: SortSection; index: number } | null>(null)

const templateName = computed(
  () =>
    store.content.preview.templateName ||
    TEMPLATES.find((item) => item.id === store.content.preview.templateId)?.name ||
    '默认模板',
)
const targetPosition = computed(() => store.content.direction.targetPosition ?? null)

const totalChars = computed(() => {
  const content = store.content
  let total = 0
  total += content.basic.realName.length
  total += content.basic.school?.length ?? 0
  total += content.basic.major?.length ?? 0
  total += content.direction.jobGoal?.length ?? 0
  total += content.soft.selfEvaluation.length
  total += content.soft.strengths.join('').length
  total += content.skills.join('').length
  content.educations.forEach((item) => {
    total += item.school.length + item.major.length + 30
  })
  content.experiences.forEach((item) => {
    total += item.description.length + item.achievements.length + 60
    total += Object.values(item.star).join('').length
  })
  content.projects.forEach((item) => {
    total += item.description.length + 60
    total += Object.values(item.star).join('').length
    total += item.highlights.join('').length
  })
  return total
})

const estimatedPages = computed(() => {
  const entries =
    store.content.experiences.length +
    store.content.projects.length +
    store.content.educations.length
  const weighted = totalChars.value + entries * 80
  return Math.max(1, Math.ceil(weighted / 1000))
})

function confirmTemplate(templateId: string): void {
  store.content.preview.templateId = templateId
  store.content.preview.templateName =
    TEMPLATES.find((item) => item.id === templateId)?.name ?? null
  galleryVisible.value = false
}

function dragStart(section: SortSection, index: number, event: DragEvent): void {
  dragState.value = { section, index }
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
    event.dataTransfer.setData('text/plain', String(index))
  }
}

function dragOver(section: SortSection, index: number): void {
  if (dragState.value?.section === section) {
    overState.value = { section, index }
  }
}

function endDrag(): void {
  dragState.value = null
  overState.value = null
}

function drop(section: SortSection, index: number): void {
  const state = dragState.value
  endDrag()
  if (!state || state.section !== section || state.index === index) {
    return
  }
  if (section === 'experiences') {
    store.content.experiences = moveItem(store.content.experiences, state.index, index)
  } else {
    store.content.projects = moveItem(store.content.projects, state.index, index)
  }
}

function isOver(section: SortSection, index: number): boolean {
  return overState.value?.section === section && overState.value.index === index
}

function experienceTitle(index: number): string {
  const item = store.content.experiences[index]
  return item?.company || item?.position || `工作/实习经历 ${index + 1}`
}

function projectTitle(index: number): string {
  const item = store.content.projects[index]
  return item?.projectName || `项目经历 ${index + 1}`
}
</script>

<template>
  <section class="step-preview">
    <header class="step-head">
      <h2 class="step-head__title">预览微调</h2>
      <p class="step-head__desc">调整经历顺序、模板和字体，右侧预览会同步更新。</p>
    </header>

    <section class="panel">
      <div class="panel__head">
        <h3 class="panel__title">模板</h3>
        <el-button size="small" data-testid="step-switch-template" @click="galleryVisible = true">
          切换模板
        </el-button>
      </div>
      <p class="panel__value">当前模板：{{ templateName }}</p>
    </section>

    <section class="panel">
      <h3 class="panel__title">经历排序</h3>
      <p class="panel__hint">拖拽手柄调整顺序，顺序会保存到草稿并影响最终简历。</p>

      <div class="sort-group">
        <h4 class="sort-group__title">工作 / 实习经历</h4>
        <p v-if="store.content.experiences.length === 0" class="sort-empty">暂无工作或实习经历</p>
        <div
          v-for="(item, index) in store.content.experiences"
          :key="item.id"
          class="sort-item"
          :class="{ 'sort-item--over': isOver('experiences', index) }"
          @dragover.prevent="dragOver('experiences', index)"
          @dragleave="endDrag"
          @drop.prevent="drop('experiences', index)"
        >
          <span
            class="sort-item__handle"
            draggable="true"
            title="拖拽调整顺序"
            @dragstart="dragStart('experiences', index, $event)"
            @dragend="endDrag"
          >
            ⋮⋮
          </span>
          <div class="sort-item__text">
            <p class="sort-item__title">{{ experienceTitle(index) }}</p>
            <p class="sort-item__meta">
              {{ item.position || '未填写职位' }} · {{ item.startDate || '开始时间待补充' }} 至
              {{ item.endDate || '至今' }}
            </p>
          </div>
          <el-tag size="small" type="info">{{ index + 1 }}</el-tag>
        </div>
      </div>

      <div class="sort-group">
        <h4 class="sort-group__title">项目经历</h4>
        <p v-if="store.content.projects.length === 0" class="sort-empty">暂无项目经历</p>
        <div
          v-for="(item, index) in store.content.projects"
          :key="item.id"
          class="sort-item"
          :class="{ 'sort-item--over': isOver('projects', index) }"
          @dragover.prevent="dragOver('projects', index)"
          @dragleave="endDrag"
          @drop.prevent="drop('projects', index)"
        >
          <span
            class="sort-item__handle"
            draggable="true"
            title="拖拽调整顺序"
            @dragstart="dragStart('projects', index, $event)"
            @dragend="endDrag"
          >
            ⋮⋮
          </span>
          <div class="sort-item__text">
            <p class="sort-item__title">{{ projectTitle(index) }}</p>
            <p class="sort-item__meta">{{ item.role || '未填写角色' }}</p>
          </div>
          <el-tag size="small" type="info">{{ index + 1 }}</el-tag>
        </div>
      </div>
    </section>

    <section class="panel">
      <h3 class="panel__title">字体与排版</h3>
      <el-form label-position="top" @submit.prevent>
      <el-row :gutter="16">
        <el-col :xs="24" :sm="12">
          <el-form-item label="字体">
            <el-select v-model="store.content.preview.fontFamily" class="full-width">
              <el-option
                v-for="option in FONT_OPTIONS"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :xs="12" :sm="6">
          <el-form-item label="字号">
            <el-select v-model="store.content.preview.fontSize" class="full-width">
              <el-option
                v-for="option in FONT_SIZE_OPTIONS"
                :key="option"
                :label="`${option} 磅`"
                :value="option"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :xs="12" :sm="6">
          <el-form-item label="行距">
            <el-select v-model="store.content.preview.lineHeight" class="full-width">
              <el-option
                v-for="option in LINE_HEIGHT_OPTIONS"
                :key="option"
                :label="String(option)"
                :value="option"
              />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="段落间距">
        <el-select v-model="store.content.preview.spacing" class="full-width">
          <el-option
            v-for="option in SPACING_OPTIONS"
            :key="option"
            :label="`${option} 像素`"
            :value="option"
          />
        </el-select>
      </el-form-item>
      </el-form>
      <p class="panel__hint">字体设置会随草稿保存，生成简历时一并写入模板。</p>
    </section>

    <section class="panel">
      <h3 class="panel__title">版面估算</h3>
      <el-alert
        v-if="estimatedPages > 1"
        type="warning"
        :closable="false"
        title="内容可能超出一页，建议精简"
        data-testid="overflow-warning"
      >
        <p class="overflow-text">
          按当前字符数（约 {{ totalChars }} 字）和经历条数估算，简历约
          {{ estimatedPages }} 页。建议压缩职责描述、合并同类经历或减少亮点条数。
        </p>
      </el-alert>
      <p v-else class="panel__value">
        当前估算约 1 页，版面比较紧凑；如果继续补充内容，请留意是否超出一页。
      </p>
    </section>

    <TemplateGalleryDialog
      v-model="galleryVisible"
      mode="switch"
      :target-position="targetPosition"
      :current-template-id="store.content.preview.templateId"
      @confirm="confirmTemplate"
    />
  </section>
</template>

<style scoped>
.step-preview {
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

.panel {
  padding: var(--cf-space-4);
  border: 1px solid var(--cf-border);
  border-radius: var(--cf-radius-md);
  background: var(--cf-card-bg);
}

.panel + .panel {
  margin-top: var(--cf-space-4);
}

.panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--cf-space-3);
}

.panel__title {
  margin: 0 0 var(--cf-space-2);
  font-size: var(--cf-font-base);
  font-weight: 600;
  color: var(--cf-text);
}

.panel__head .panel__title {
  margin-bottom: 0;
}

.panel__value {
  margin: var(--cf-space-1) 0 0;
  font-size: var(--cf-font-sm);
  color: var(--cf-text-secondary);
}

.panel__hint {
  margin: var(--cf-space-1) 0 var(--cf-space-2);
  font-size: var(--cf-font-xs);
  color: var(--cf-text-tertiary);
}

.sort-group + .sort-group {
  margin-top: var(--cf-space-3);
}

.sort-group__title {
  margin: 0 0 var(--cf-space-2);
  font-size: var(--cf-font-sm);
  font-weight: 600;
  color: var(--cf-text);
}

.sort-empty {
  margin: 0;
  font-size: var(--cf-font-xs);
  color: var(--cf-text-tertiary);
}

.sort-item {
  display: flex;
  align-items: center;
  gap: var(--cf-space-2);
  padding: var(--cf-space-2) var(--cf-space-3);
  border: 1px solid var(--cf-border);
  border-radius: var(--cf-radius-sm);
  background: var(--cf-bg);
}

.sort-item + .sort-item {
  margin-top: var(--cf-space-2);
}

.sort-item--over {
  border-color: var(--cf-primary);
  box-shadow: 0 0 0 2px rgb(37 99 235 / 12%);
}

.sort-item__handle {
  color: var(--cf-text-tertiary);
  font-size: var(--cf-font-xs);
  letter-spacing: 1px;
  cursor: grab;
  user-select: none;
}

.sort-item__handle:active {
  cursor: grabbing;
}

.sort-item__text {
  flex: 1;
  min-width: 0;
}

.sort-item__title {
  margin: 0;
  font-size: var(--cf-font-sm);
  font-weight: 600;
  color: var(--cf-text);
}

.sort-item__meta {
  margin: 2px 0 0;
  font-size: var(--cf-font-xs);
  color: var(--cf-text-secondary);
}

.full-width {
  width: 100%;
}

.overflow-text {
  margin: var(--cf-space-1) 0 0;
  font-size: var(--cf-font-sm);
  line-height: 1.7;
}
</style>
