<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'

import type { DraftCompleteResult } from '@/api/resumeDraft'
import { exportResumeDocx, exportResumePdf } from '@/api/resume'
import { useResumeDraftStore } from '@/stores/resumeDraft'

/** 第 8 步：生成简历并按勾选格式导出。 */
type ExportFormat = 'DOCX' | 'PDF'

const store = useResumeDraftStore()
const router = useRouter()

const resumeName = ref(
  store.draftName ||
    (store.content.basic.realName ? `${store.content.basic.realName}的简历` : '我的简历'),
)
const formats = ref<ExportFormat[]>(['DOCX', 'PDF'])
const generating = ref(false)
const errorMessage = ref('')
const result = ref<DraftCompleteResult | null>(null)
const downloaded = ref<string[]>([])

const nameError = computed(() => (resumeName.value.trim() ? '' : '请填写简历名称'))

function errorText(error: unknown, fallback: string): string {
  const response = (error as { response?: { status?: number; data?: { message?: string } } })
    ?.response
  if (response?.status === 502) {
    return 'AI 服务暂时不可用，请稍后重试'
  }
  return response?.data?.message || fallback
}

function triggerDownload(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.style.display = 'none'
  document.body.appendChild(link)
  link.click()
  link.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 1000)
}

async function generate(): Promise<void> {
  if (!resumeName.value.trim()) {
    ElMessage.warning('请填写简历名称')
    return
  }
  if (formats.value.length === 0) {
    ElMessage.warning('请至少选择一种导出格式')
    return
  }
  generating.value = true
  errorMessage.value = ''
  result.value = null
  downloaded.value = []
  try {
    const completed = await store.complete({
      resumeName: resumeName.value.trim(),
      templateId: store.content.preview.templateId,
      generationReason: 'INITIAL',
    })
    result.value = completed

    const finished: string[] = []
    let failed = 0
    for (const format of formats.value) {
      try {
        const file =
          format === 'DOCX'
            ? await exportResumeDocx(completed.resumeId, completed.versionId)
            : await exportResumePdf(completed.resumeId, completed.versionId)
        triggerDownload(file.blob, file.filename)
        finished.push(format)
      } catch {
        failed += 1
      }
    }
    downloaded.value = finished

    if (failed > 0) {
      ElMessage.warning('简历已生成，但部分格式导出失败，可稍后到简历中心重新导出')
    } else {
      ElMessage.success('简历生成成功，已开始下载')
    }
  } catch (error) {
    errorMessage.value = errorText(error, '简历生成失败，请稍后重试')
    ElMessage.error(errorMessage.value)
  } finally {
    generating.value = false
  }
}

function backToCenter(): void {
  void router.push('/resumes')
}
</script>

<template>
  <section class="step-generate">
    <header class="step-head">
      <h2 class="step-head__title">生成简历</h2>
      <p class="step-head__desc">
        确认简历名称和导出格式后生成正式版本；生成成功后草稿会自动归档。
      </p>
    </header>

    <template v-if="!result">
      <section class="panel">
        <el-form label-position="top" @submit.prevent>
          <el-form-item label="简历名称" required>
            <el-input
              v-model="resumeName"
              data-testid="generate-resume-name"
              maxlength="60"
              placeholder="如 张三的 Java 开发简历"
            />
            <p v-if="nameError" class="field-error">{{ nameError }}</p>
          </el-form-item>

          <el-form-item label="导出格式">
            <el-checkbox-group v-model="formats" data-testid="generate-formats">
              <el-checkbox label="DOCX" value="DOCX">Word 文档（DOCX）</el-checkbox>
              <el-checkbox label="PDF" value="PDF">PDF 文档</el-checkbox>
            </el-checkbox-group>
            <p class="field-hint">至少选择一种格式，生成后可随时到简历中心重新导出。</p>
          </el-form-item>
        </el-form>

        <el-alert
          v-if="errorMessage"
          class="generate-error"
          type="error"
          :closable="false"
          :title="errorMessage"
        />

        <div class="generate-actions">
          <el-button
            type="primary"
            size="large"
            data-testid="generate-resume"
            :loading="generating"
            :disabled="generating"
            @click="generate"
          >
            {{ generating ? '正在生成…' : '生成简历' }}
          </el-button>
          <span class="generate-hint">使用当前模板：{{ store.content.preview.templateName || '默认模板' }}</span>
        </div>
      </section>
    </template>

    <section v-else class="panel result">
      <div class="result__icon">✓</div>
      <h3 class="result__title">简历生成成功</h3>
      <p class="result__desc">
        已生成第 {{ result.versionNumber }} 版简历，可到简历中心查看、继续编辑或切换模板。
      </p>
      <p v-if="downloaded.length > 0" class="result__formats">
        已下载：{{ downloaded.join('、') }}
      </p>
      <p v-else class="result__formats">本次没有完成文件下载，可到简历中心重新导出。</p>
      <div class="result__actions">
        <el-button type="primary" @click="backToCenter">返回简历中心</el-button>
        <router-link class="result__link" to="/resumes">查看我的简历列表</router-link>
      </div>
    </section>
  </section>
</template>

<style scoped>
.step-generate {
  max-width: 760px;
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

.field-error {
  margin: var(--cf-space-1) 0 0;
  font-size: var(--cf-font-xs);
  color: var(--cf-danger);
}

.field-hint {
  margin: var(--cf-space-1) 0 0;
  font-size: var(--cf-font-xs);
  color: var(--cf-text-tertiary);
}

.generate-error {
  margin-top: var(--cf-space-3);
}

.generate-actions {
  display: flex;
  align-items: center;
  gap: var(--cf-space-3);
  margin-top: var(--cf-space-4);
}

.generate-hint {
  font-size: var(--cf-font-xs);
  color: var(--cf-text-tertiary);
}

.result {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--cf-space-2);
  padding: var(--cf-space-6) var(--cf-space-4);
  text-align: center;
}

.result__icon {
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--cf-success-soft);
  color: var(--cf-success);
  font-size: 22px;
}

.result__title {
  margin: 0;
  font-size: var(--cf-font-lg);
  font-weight: 600;
  color: var(--cf-text);
}

.result__desc,
.result__formats {
  margin: 0;
  font-size: var(--cf-font-sm);
  color: var(--cf-text-secondary);
}

.result__actions {
  display: flex;
  align-items: center;
  gap: var(--cf-space-3);
  margin-top: var(--cf-space-3);
}

.result__link {
  font-size: var(--cf-font-sm);
  color: var(--cf-primary);
}

.result__link:hover {
  text-decoration: underline;
}
</style>
