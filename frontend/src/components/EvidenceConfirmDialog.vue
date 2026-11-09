<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import type { EvidenceGroup } from '@/utils/evidenceDisplay'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    groups: EvidenceGroup[]
    submitting?: boolean
  }>(),
  { submitting: false },
)

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  confirm: [ids: number[]]
  reject: [ids: number[]]
}>()

const selected = ref<number[]>([])

const allItems = computed(() => props.groups.flatMap((group) => group.items))
const allSelected = computed(
  () => allItems.value.length > 0 && selected.value.length === allItems.value.length,
)
const someSelected = computed(() => selected.value.length > 0)

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) {
      selected.value = []
    }
  },
)

watch(
  () => props.groups,
  (groups) => {
    const validIds = new Set(groups.flatMap((group) => group.items.map((item) => item.id)))
    selected.value = selected.value.filter((id) => validIds.has(id))
  },
)

function targetChecked(event: Event): boolean {
  return (event.target as HTMLInputElement).checked
}

function isSelected(id: number): boolean {
  return selected.value.includes(id)
}

function toggle(id: number, checked: boolean): void {
  if (checked) {
    if (!selected.value.includes(id)) {
      selected.value = [...selected.value, id]
    }
  } else {
    selected.value = selected.value.filter((itemId) => itemId !== id)
  }
}

function toggleAll(checked: boolean): void {
  selected.value = checked ? allItems.value.map((item) => item.id) : []
}

function close(): void {
  emit('update:modelValue', false)
}

function confirm(): void {
  if (someSelected.value && !props.submitting) {
    emit('confirm', [...selected.value])
  }
}

function reject(): void {
  if (someSelected.value && !props.submitting) {
    emit('reject', [...selected.value])
  }
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="待确认信息"
    width="720px"
    destroy-on-close
    @update:model-value="close"
  >
    <div v-if="!allItems.length" class="evidence-empty">暂无待确认信息</div>

    <template v-else>
      <label class="select-all">
        <input
          data-testid="select-all"
          type="checkbox"
          :checked="allSelected"
          @change="toggleAll(targetChecked($event))"
        />
        <span>全选（{{ allItems.length }} 条）</span>
      </label>

      <section v-for="group in groups" :key="group.key" class="evidence-group">
        <h4 class="group-title">{{ group.title }}</h4>
        <div v-for="item in group.items" :key="item.id" class="evidence-item">
          <label class="evidence-main">
            <input
              :data-testid="`evidence-${item.id}`"
              type="checkbox"
              :checked="isSelected(item.id)"
              @change="toggle(item.id, targetChecked($event))"
            />
            <span class="item-label">{{ item.label }}</span>
            <span class="item-value">{{ item.value }}</span>
          </label>
          <p v-if="item.sourceContent" class="item-source">来源：{{ item.sourceContent }}</p>
        </div>
      </section>
    </template>

    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button data-testid="reject" :disabled="!someSelected || submitting" @click="reject">
        忽略选中
      </el-button>
      <el-button
        data-testid="confirm"
        type="primary"
        :disabled="!someSelected || submitting"
        @click="confirm"
      >
        确认选中
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.evidence-empty {
  padding: 24px 0;
  text-align: center;
  color: var(--cf-text-secondary);
  font-size: 13px;
}

.select-all {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.evidence-group {
  margin-bottom: 16px;
}

.group-title {
  margin: 0 0 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--cf-text-secondary);
}

.evidence-item {
  padding: 8px 10px;
  border: 1px solid var(--cf-border);
  border-radius: 8px;
  margin-bottom: 6px;
  background: #fbfcff;
}

.evidence-main {
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-size: 13px;
  cursor: pointer;
}

.item-label {
  flex-shrink: 0;
  color: var(--cf-text-secondary);
}

.item-value {
  color: var(--cf-text);
  font-weight: 500;
  word-break: break-word;
}

.item-source {
  margin: 6px 0 0 24px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--cf-text-secondary);
  word-break: break-word;
}
</style>
