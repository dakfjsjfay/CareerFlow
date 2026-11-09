<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { UploadFile } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { analyzeFiles, type AnalysisType, type FileAnalysisResult } from '@/api/files'

defineProps<{ compact?: boolean }>()
const emit = defineEmits<{ analyzed: [result: FileAnalysisResult] }>()

const ACCEPT =
  '.docx,.pptx,.pdf,.png,.jpg,.jpeg,.bmp,.gif,.txt,.md,.markdown,.zip,.java,.kt,.py,.js,.ts,.jsx,.tsx,.vue,.html,.htm,.css,.scss,.less,.sql,.xml,.json,.yml,.yaml,.properties,.sh,.bat,.gradle,.c,.h,.cpp,.hpp,.go,.rs,.php,.rb'
const MAX_SIZE = 10 * 1024 * 1024
const MAX_FILES = 6
const SUPPORTED = new Set(
  ACCEPT.split(',').map((item) => item.replace('.', '').toLowerCase()),
)

const uploadRef = ref()
const selectedFiles = ref<File[]>([])
const analysisType = ref<AnalysisType>('AUTO')
const jobDescription = ref('')
const showJd = ref(false)
const loading = ref(false)
const result = ref<FileAnalysisResult | null>(null)

const extractedJson = computed(() =>
  result.value ? JSON.stringify(result.value.extractedInfo, null, 2) : '',
)

function errorMessage(error: unknown, fallback: string) {
  const err = error as {
    code?: string
    message?: string
    response?: { data?: { message?: string } }
  }
  if (err.code === 'ECONNABORTED' || /timeout/i.test(err.message ?? '')) {
    return '文件分析耗时较长（复杂文件可能需要 1~2 分钟），请稍后重试'
  }
  return err.response?.data?.message || fallback
}

function clearSelected() {
  selectedFiles.value = []
  uploadRef.value?.clearFiles()
}

function syncFiles(uploadFiles: UploadFile[]) {
  const files: File[] = []
  for (const item of uploadFiles) {
    if (item.raw) {
      files.push(item.raw)
    }
  }

  for (const file of files) {
    const ext = file.name.split('.').pop()?.toLowerCase() ?? ''
    if (!SUPPORTED.has(ext)) {
      ElMessage.error(`不支持的文件类型：.${ext || '未知'}`)
      clearSelected()
      return
    }
    if (file.size > MAX_SIZE) {
      ElMessage.error(`${file.name} 超过 10MB`)
      clearSelected()
      return
    }
  }
  selectedFiles.value = files
}

function handleChange(_uploadFile: UploadFile, uploadFiles: UploadFile[]) {
  syncFiles(uploadFiles)
}

function handleRemove(_uploadFile: UploadFile, uploadFiles: UploadFile[]) {
  syncFiles(uploadFiles)
}

function handleExceed() {
  ElMessage.warning(`一次最多上传 ${MAX_FILES} 个文件`)
}

async function handleAnalyze() {
  if (!selectedFiles.value.length) {
    ElMessage.warning('请先选择文件')
    return
  }
  const hasZip = selectedFiles.value.some((file) => file.name.toLowerCase().endsWith('.zip'))
  if (hasZip && selectedFiles.value.length > 1) {
    ElMessage.error('ZIP 请单独上传分析，不要和其他文件混合')
    return
  }

  loading.value = true
  result.value = null
  try {
    const res = await analyzeFiles(
      selectedFiles.value,
      analysisType.value,
      jobDescription.value.trim() || undefined,
    )
    result.value = res.data
    emit('analyzed', res.data)
    ElMessage.success('分析完成，结果需确认后才可写入档案')
  } catch (error) {
    ElMessage.error(errorMessage(error, '文件分析失败，请稍后重试'))
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="document-analyzer" :class="{ compact }">
    <div class="analyzer-head">
      <div>
        <h3>上传资料分析</h3>
        <p>
          支持 Word、PPT、PDF、图片、文本、Markdown、ZIP 与常见核心代码文件；
          文件仅在内存中解析，图片/扫描件使用本地 OCR。
        </p>
      </div>
      <el-select v-model="analysisType" class="type-select">
        <el-option label="自动判断" value="AUTO" />
        <el-option label="个人资料" value="PROFILE" />
        <el-option label="项目资料" value="PROJECT" />
      </el-select>
    </div>

    <el-upload
      ref="uploadRef"
      drag
      multiple
      :auto-upload="false"
      :limit="MAX_FILES"
      :accept="ACCEPT"
      :show-file-list="true"
      :on-change="handleChange"
      :on-remove="handleRemove"
      :on-exceed="handleExceed"
      class="uploader"
    >
      <el-icon class="upload-icon"><UploadFilled /></el-icon>
      <div class="el-upload__text">
        拖拽文件到这里，或 <em>点击选择</em>（最多 {{ MAX_FILES }} 个）
      </div>
      <template #tip>
        <div class="el-upload__tip">
          单文件 ≤10MB；多个文件会合并分析；ZIP 请单独上传
        </div>
      </template>
    </el-upload>

    <div class="jd-row">
      <el-switch v-model="showJd" active-text="按目标岗位 JD 给出差距建议" />
    </div>
    <el-input
      v-if="showJd"
      v-model="jobDescription"
      type="textarea"
      :rows="4"
      resize="none"
      placeholder="粘贴目标岗位 JD（选填）。提供后会输出已满足/部分满足/未体现的差距建议。"
      class="jd-input"
    />

    <div class="analyzer-actions">
      <el-button
        type="primary"
        :loading="loading"
        :disabled="!selectedFiles.length"
        @click="handleAnalyze"
      >
        开始分析{{ selectedFiles.length ? `（${selectedFiles.length} 个文件）` : '' }}
      </el-button>
      <span v-if="loading" class="hint">
        正在解析文件并调用 AI，复杂文件可能需要 1~2 分钟，请勿关闭页面…
      </span>
      <span v-else class="hint">分析结果只是候选信息，不会自动写入档案</span>
    </div>

    <div v-if="result?.files?.length" class="file-summary">
      本次分析：
      <el-tag
        v-for="file in result.files"
        :key="file.fileName"
        size="small"
        effect="plain"
      >
        {{ file.fileName }}
      </el-tag>
    </div>

    <div v-if="result" class="result">
      <div v-if="result.warnings.length" class="warnings">
        <p v-for="warning in result.warnings" :key="warning">{{ warning }}</p>
      </div>

      <div class="summary">{{ result.summary }}</div>

      <section v-if="result.strengths.length">
        <h4>明确的优势</h4>
        <ul>
          <li v-for="item in result.strengths" :key="item">{{ item }}</li>
        </ul>
      </section>

      <section v-if="result.resumeSuggestions.length">
        <h4>可用于简历的描述</h4>
        <ul>
          <li v-for="item in result.resumeSuggestions" :key="item">{{ item }}</li>
        </ul>
      </section>

      <section v-if="result.jobGapSuggestions.length">
        <h4>岗位差距建议</h4>
        <ul>
          <li v-for="item in result.jobGapSuggestions" :key="item">{{ item }}</li>
        </ul>
      </section>

      <section v-if="result.missingFields.length">
        <h4>待补充信息</h4>
        <div class="tags">
          <el-tag
            v-for="field in result.missingFields"
            :key="field"
            type="warning"
            effect="plain"
            size="small"
          >
            {{ field }}
          </el-tag>
        </div>
      </section>

      <el-collapse class="raw">
        <el-collapse-item title="查看结构化抽取结果（JSON）" name="json">
          <pre>{{ extractedJson }}</pre>
        </el-collapse-item>
      </el-collapse>
    </div>
  </div>
</template>

<style scoped>
.document-analyzer {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.document-analyzer.compact {
  gap: 12px;
}

.analyzer-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.analyzer-head h3 {
  font-size: 16px;
  font-weight: 600;
}

.analyzer-head p {
  margin-top: 6px;
  font-size: 12px;
  color: var(--cf-text-secondary);
  line-height: 1.6;
}

.type-select {
  width: 130px;
  flex-shrink: 0;
}

.uploader :deep(.el-upload-dragger) {
  padding: 22px 10px;
}

.upload-icon {
  font-size: 38px;
  color: var(--cf-primary);
}

.jd-row {
  display: flex;
  align-items: center;
}

.jd-input {
  margin-top: -4px;
}

.analyzer-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.hint {
  font-size: 12px;
  color: var(--cf-text-secondary);
}

.file-summary {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--cf-text-secondary);
}

.result {
  display: flex;
  flex-direction: column;
  gap: 14px;
  border-top: 1px solid var(--cf-border);
  padding-top: 16px;
}

.warnings {
  background: #fff7ed;
  border: 1px solid #fed7aa;
  color: #9a3412;
  border-radius: 8px;
  padding: 10px 12px;
  font-size: 12px;
  line-height: 1.7;
}

.summary {
  font-size: 14px;
  line-height: 1.8;
  color: var(--cf-text);
}

.result section h4 {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 6px;
}

.result section ul {
  margin: 0;
  padding-left: 18px;
  font-size: 13px;
  line-height: 1.8;
  color: var(--cf-text);
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.raw :deep(pre) {
  margin: 0;
  max-height: 320px;
  overflow: auto;
  font-size: 12px;
  line-height: 1.6;
  background: #f8fafc;
  border-radius: 6px;
  padding: 10px;
}
</style>
