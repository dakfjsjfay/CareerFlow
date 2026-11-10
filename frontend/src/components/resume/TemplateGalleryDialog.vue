<template>
  <el-dialog
    :model-value="modelValue"
    :title="mode === 'generate' ? '选择模板并生成' : '切换简历模板'"
    width="920px"
    :append-to-body="false"
    @update:model-value="close"
  >
    <section>
      <h4 class="group-title">为你推荐</h4>
      <div class="template-grid recommended">
        <button
          v-for="template in recommended"
          :key="template.id"
          type="button"
          class="template-card"
          :class="{ active: selectedId === template.id }"
          :data-testid="`template-card-${template.id}`"
          @click="selectedId = template.id"
        >
          <img :src="template.cover" :alt="template.name" class="cover" />
          <span class="name">{{ template.name }}</span>
          <span class="meta">{{ template.style || '通用' }} · {{ template.post || '通用岗位' }}</span>
        </button>
      </div>
    </section>

    <el-scrollbar max-height="420px">
      <section v-for="group in grouped" :key="group.style">
        <h4 class="group-title">{{ group.style }}</h4>
        <div class="template-grid">
          <button
            v-for="template in group.templates"
            :key="template.id"
            type="button"
            class="template-card"
            :class="{ active: selectedId === template.id }"
            :data-testid="`template-card-${template.id}`"
            @click="selectedId = template.id"
          >
            <img :src="template.cover" :alt="template.name" class="cover" />
            <span class="name">{{ template.name }}</span>
            <span class="meta">{{ template.post || template.industry || '通用' }}</span>
          </button>
        </div>
      </section>
    </el-scrollbar>

    <template #footer>
      <el-button @click="close(false)">取消</el-button>
      <el-button
        type="primary"
        data-testid="confirm-template"
        :disabled="!selectedId"
        @click="confirm"
      >
        {{ mode === 'generate' ? '用此模板生成' : '应用此模板' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import { TEMPLATES } from '@/components/resume-template/manifest'
import { recommendTemplates } from '@/components/resume-template/recommend'
import type { TemplateMeta } from '@/components/resume-template/types'

const props = defineProps<{
  modelValue: boolean
  mode: 'generate' | 'switch'
  targetPosition?: string | null
  currentTemplateId?: string | null
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'confirm', templateId: string): void
}>()

const selectedId = ref('')
const recommended = computed(() => recommendTemplates(props.targetPosition))

const grouped = computed(() => {
  const map = new Map<string, TemplateMeta[]>()
  for (const template of TEMPLATES) {
    const style = template.style || '其他'
    const list = map.get(style) ?? []
    list.push(template)
    map.set(style, list)
  }
  return [...map.entries()].map(([style, templates]) => ({ style, templates }))
})

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      selectedId.value =
        props.currentTemplateId ?? recommended.value[0]?.id ?? TEMPLATES[0]?.id ?? ''
    }
  },
)

function close(value = false): void {
  emit('update:modelValue', value)
}

function confirm(): void {
  if (!selectedId.value) {
    return
  }
  emit('confirm', selectedId.value)
  close(false)
}
</script>

<style scoped>
.group-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--cf-text);
  margin: 12px 0 10px;
}

.template-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.recommended {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.template-card {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 6px;
  padding: 8px;
  border: 1px solid var(--cf-border);
  border-radius: 10px;
  background: #fff;
  cursor: pointer;
  text-align: left;
  transition: all 0.2s ease;
}

.template-card:hover {
  border-color: var(--cf-primary-light);
  box-shadow: 0 6px 18px rgb(37 99 235 / 12%);
}

.template-card.active {
  border-color: var(--cf-primary);
  box-shadow: 0 0 0 2px rgb(37 99 235 / 18%);
}

.cover {
  width: 100%;
  aspect-ratio: 3 / 4;
  object-fit: cover;
  border-radius: 6px;
  background: #f3f4f6;
}

.name {
  font-size: 13px;
  font-weight: 600;
  color: var(--cf-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.meta {
  font-size: 12px;
  color: var(--cf-text-secondary);
}
</style>
