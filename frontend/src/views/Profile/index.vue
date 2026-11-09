<script setup lang="ts">
import { ref } from 'vue'
import DocumentAnalyzer from '@/components/DocumentAnalyzer.vue'
import type { FileAnalysisResult } from '@/api/files'

const result = ref<FileAnalysisResult | null>(null)
</script>

<template>
  <div class="profile-page">
    <div class="page-head">
      <h2>个人档案</h2>
      <p>
        上传个人简历、项目资料、Word/PPT/PDF、图片或代码包，最多 6 个文件一起分析（ZIP 请单独上传）。
        AI 帮你抽取基础信息、教育、技能、项目经历，并生成可用于简历的描述。
        图片与扫描版 PDF 使用本地 OCR。分析结果只是待确认候选，不会自动写入档案。
      </p>
    </div>

    <el-card shadow="never" class="import-card">
      <DocumentAnalyzer @analyzed="result = $event" />
    </el-card>

    <el-alert
      v-if="result"
      class="tip"
      type="info"
      :closable="false"
      show-icon
      title="档案编辑、证据确认与完整度雷达将在 D4/D5 完成；本次分析结果目前不会自动保存。"
    />
  </div>
</template>

<style scoped>
.profile-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-head h2 {
  font-size: 20px;
  font-weight: 600;
}

.page-head p {
  margin-top: 8px;
  max-width: 860px;
  font-size: 13px;
  line-height: 1.8;
  color: var(--cf-text-secondary);
}

.import-card {
  border-radius: 10px;
}

.tip {
  border-radius: 8px;
}
</style>
