<script setup lang="ts">
import {
  ArrowLeft,
  Clock,
  Download,
  Files,
  Grid,
  MagicStick,
  RefreshLeft,
  Tickets,
} from '@element-plus/icons-vue'

import DraftSaveIndicator from '@/components/resume-wizard/DraftSaveIndicator.vue'

defineProps<{
  mode: 'CREATE' | 'EDIT'
  draftName: string
  progress: number
  generating: boolean
  exporting: boolean
  canUndo: boolean
  canExport: boolean
  versionLabel: string
}>()

const emit = defineEmits<{
  back: []
  'update:draftName': [value: string]
  generate: []
  undo: []
  export: [format: 'PDF' | 'DOCX']
  templates: []
  history: []
  'form-mode': []
}>()
</script>

<template>
  <header class="toolbar">
    <div class="toolbar__left">
      <el-tooltip content="返回简历中心" placement="bottom">
        <el-button link class="toolbar__back" data-testid="workspace-back" @click="emit('back')">
          <el-icon><ArrowLeft /></el-icon>
        </el-button>
      </el-tooltip>
      <el-input
        :model-value="draftName"
        class="toolbar__name"
        size="small"
        maxlength="40"
        placeholder="未命名简历"
        @update:model-value="emit('update:draftName', $event)"
      />
      <el-tag size="small" :type="mode === 'CREATE' ? 'primary' : 'warning'" effect="plain">
        {{ mode === 'CREATE' ? '生成模式' : '修改模式' }}
      </el-tag>
      <el-tag v-if="versionLabel" size="small" effect="plain" class="toolbar__version">
        {{ versionLabel }}
      </el-tag>
    </div>

    <div class="toolbar__status">
      <div class="toolbar__progress" :title="`信息完整度 ${progress}%`">
        <el-progress
          :percentage="progress"
          :show-text="false"
          :stroke-width="4"
          class="toolbar__progress-bar"
        />
        <span class="toolbar__progress-text">{{ progress }}%</span>
      </div>
      <DraftSaveIndicator />
    </div>

    <div class="toolbar__right">
      <el-tooltip content="切换到完整表单模式（八步向导）" placement="bottom">
        <el-button size="small" @click="emit('form-mode')">
          <el-icon><Files /></el-icon>
          <span class="btn-label">表单模式</span>
        </el-button>
      </el-tooltip>
      <el-tooltip content="选择简历模板" placement="bottom">
        <el-button size="small" data-testid="workspace-templates" @click="emit('templates')">
          <el-icon><Grid /></el-icon>
          <span class="btn-label">模板库</span>
        </el-button>
      </el-tooltip>
      <el-tooltip content="历史版本（不可覆盖）" placement="bottom">
        <el-button
          size="small"
          data-testid="workspace-history"
          :disabled="mode === 'CREATE'"
          @click="emit('history')"
        >
          <el-icon><Clock /></el-icon>
          <span class="btn-label">版本历史</span>
        </el-button>
      </el-tooltip>
      <el-tooltip :content="canExport ? '导出 PDF' : '生成简历后可导出'" placement="bottom">
        <el-button
          size="small"
          data-testid="workspace-export-pdf"
          :disabled="!canExport"
          :loading="exporting"
          @click="emit('export', 'PDF')"
        >
          <el-icon><Download /></el-icon>
          <span class="btn-label">PDF</span>
        </el-button>
      </el-tooltip>
      <el-tooltip :content="canExport ? '导出 DOCX' : '生成简历后可导出'" placement="bottom">
        <el-button
          size="small"
          data-testid="workspace-export-docx"
          :disabled="!canExport"
          :loading="exporting"
          @click="emit('export', 'DOCX')"
        >
          <el-icon><Tickets /></el-icon>
          <span class="btn-label">DOCX</span>
        </el-button>
      </el-tooltip>
      <el-tooltip :content="canUndo ? '撤销上一次 AI 修改' : '暂无可撤销的修改'" placement="bottom">
        <el-button size="small" :disabled="!canUndo" @click="emit('undo')">
          <el-icon><RefreshLeft /></el-icon>
          <span class="btn-label">撤销</span>
        </el-button>
      </el-tooltip>
      <el-button
        type="primary"
        size="small"
        data-testid="workspace-generate"
        :loading="generating"
        @click="emit('generate')"
      >
        <el-icon><MagicStick /></el-icon>
        <span>{{ mode === 'CREATE' ? '生成简历' : '保存为新版本' }}</span>
      </el-button>
    </div>
  </header>
</template>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: var(--cf-space-4);
  flex-shrink: 0;
  height: 56px;
  padding: 0 var(--cf-space-4);
  background: var(--cf-card-bg);
  border-bottom: 1px solid var(--cf-border);
}

.toolbar__left {
  display: flex;
  align-items: center;
  gap: var(--cf-space-2);
  min-width: 0;
}

.toolbar__back {
  color: var(--cf-text-secondary);
}

.toolbar__name {
  width: 210px;
}

.toolbar__name :deep(.el-input__wrapper) {
  box-shadow: none;
  background: transparent;
}

.toolbar__name :deep(.el-input__wrapper:hover) {
  background: var(--cf-bg);
}

.toolbar__name :deep(.el-input__inner) {
  font-weight: 600;
}

.toolbar__version {
  font-variant-numeric: tabular-nums;
}

.toolbar__status {
  display: flex;
  align-items: center;
  gap: var(--cf-space-3);
  margin-left: auto;
  min-width: 0;
}

.toolbar__progress {
  display: flex;
  align-items: center;
  gap: 6px;
}

.toolbar__progress-bar {
  width: 72px;
}

.toolbar__progress-text {
  font-size: var(--cf-font-xs);
  color: var(--cf-text-tertiary);
  font-variant-numeric: tabular-nums;
}

.toolbar__right {
  display: flex;
  align-items: center;
  gap: var(--cf-space-2);
  flex-shrink: 0;
}

@media (max-width: 1439px) {
  .btn-label {
    display: none;
  }
}

@media (max-width: 1023px) {
  .toolbar {
    gap: var(--cf-space-2);
    padding: 0 var(--cf-space-2);
    height: auto;
    min-height: 52px;
    flex-wrap: wrap;
    padding-top: var(--cf-space-2);
    padding-bottom: var(--cf-space-2);
  }

  .toolbar__name {
    width: 130px;
  }

  .toolbar__status {
    margin-left: 0;
  }

  .toolbar__progress {
    display: none;
  }
}
</style>
