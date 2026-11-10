import { ref } from 'vue'

import {
  createResume,
  exportResumeDocx,
  generateResume,
  getResume,
  getResumeVersion,
  listResumeVersions,
  listResumes,
  saveResumeVersion,
  type ResumeDetail,
  type ResumeGenerateResult,
  type ResumeSummary,
  type ResumeVersion,
  type ResumeVersionSummary,
} from '@/api/resume'
import type { ResumeContent } from '@/components/resume-template/types'

function cloneContent(content: ResumeContent): ResumeContent {
  return JSON.parse(JSON.stringify(content)) as ResumeContent
}

export function downloadBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  URL.revokeObjectURL(url)
}

export function useResume() {
  const resumes = ref<ResumeSummary[]>([])
  const active = ref<ResumeDetail | null>(null)
  const versions = ref<ResumeVersionSummary[]>([])
  const activeVersion = ref<ResumeVersion | null>(null)
  const editingContent = ref<ResumeContent | null>(null)
  const loading = ref(false)
  const generating = ref(false)
  const saving = ref(false)

  async function loadResumes(): Promise<void> {
    loading.value = true
    try {
      const result = await listResumes()
      resumes.value = result.data
      if (result.data.length > 0 && !active.value) {
        await selectResume(result.data[0].id)
      }
    } finally {
      loading.value = false
    }
  }

  async function create(name?: string): Promise<ResumeSummary> {
    const result = await createResume(name ? { resumeName: name } : {})
    resumes.value = [result.data, ...resumes.value]
    await selectResume(result.data.id)
    return result.data
  }

  async function selectResume(id: number): Promise<void> {
    loading.value = true
    try {
      const detail = await getResume(id)
      active.value = detail.data
      versions.value = (await listResumeVersions(id)).data
      const targetId = detail.data.currentVersionId ?? versions.value[0]?.versionId ?? null
      if (targetId) {
        await selectVersion(targetId)
      } else {
        activeVersion.value = null
        editingContent.value = null
      }
    } finally {
      loading.value = false
    }
  }

  async function selectVersion(versionId: number): Promise<void> {
    if (!active.value) {
      return
    }
    const result = await getResumeVersion(active.value.id, versionId)
    activeVersion.value = result.data
    editingContent.value = cloneContent(result.data.content)
  }

  async function refreshActive(): Promise<void> {
    if (!active.value) {
      return
    }
    await selectResume(active.value.id)
  }

  async function generate(
    templateId: string,
    generationReason = 'INITIAL',
  ): Promise<ResumeGenerateResult> {
    if (!active.value) {
      throw new Error('请先创建简历')
    }
    generating.value = true
    try {
      const result = await generateResume(active.value.id, { templateId, generationReason })
      await selectResume(active.value.id)
      await selectVersion(result.data.versionId)
      return result.data
    } finally {
      generating.value = false
    }
  }

  async function saveVersion(
    generationReason: string,
    templateId?: string,
  ): Promise<ResumeGenerateResult> {
    if (!active.value || !editingContent.value) {
      throw new Error('没有可保存的简历内容')
    }
    const targetTemplate =
      templateId ?? activeVersion.value?.templateId ?? editingContent.value.meta?.templateId
    if (!targetTemplate) {
      throw new Error('请选择模板')
    }
    saving.value = true
    try {
      const result = await saveResumeVersion(active.value.id, {
        content: editingContent.value,
        templateId: targetTemplate,
        generationReason,
      })
      await selectResume(active.value.id)
      await selectVersion(result.data.versionId)
      return result.data
    } finally {
      saving.value = false
    }
  }

  async function exportDocx(versionId: number): Promise<void> {
    if (!active.value) {
      throw new Error('请先创建简历')
    }
    const result = await exportResumeDocx(active.value.id, versionId)
    downloadBlob(result.blob, result.filename)
  }

  return {
    resumes,
    active,
    versions,
    activeVersion,
    editingContent,
    loading,
    generating,
    saving,
    loadResumes,
    create,
    selectResume,
    selectVersion,
    refreshActive,
    generate,
    saveVersion,
    exportDocx,
  }
}
