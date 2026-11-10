import { ref } from 'vue'

import {
  createExperience,
  createProject,
  deleteExperience,
  deleteProject,
  getCompleteness,
  getProfile,
  updateExperience,
  updateProfile,
  updateProject,
  type CompletenessResult,
  type ExperiencePayload,
  type ProfileData,
  type ProfileUpdatePayload,
  type ProjectPayload,
} from '@/api/profile'

/**
 * 档案页数据流：
 * - load 同时拉取已确认档案与完整度；
 * - 任何写操作成功后重新读取，保证页面与后端完整度一致；
 * - loading/saving 只负责界面状态，异常继续向上抛给页面提示。
 */
export function useProfile() {
  const profile = ref<ProfileData | null>(null)
  const completeness = ref<CompletenessResult | null>(null)
  const loading = ref(false)
  const saving = ref(false)

  async function load(): Promise<void> {
    loading.value = true
    try {
      const [profileRes, completenessRes] = await Promise.all([getProfile(), getCompleteness()])
      profile.value = profileRes.data
      completeness.value = completenessRes.data
    } finally {
      loading.value = false
    }
  }

  async function refreshCompleteness(): Promise<void> {
    const res = await getCompleteness()
    completeness.value = res.data
  }

  async function saveProfile(payload: ProfileUpdatePayload): Promise<void> {
    saving.value = true
    try {
      const res = await updateProfile(payload)
      profile.value = res.data
      await refreshCompleteness()
    } finally {
      saving.value = false
    }
  }

  async function saveProject(id: number | null, payload: ProjectPayload): Promise<void> {
    saving.value = true
    try {
      if (id === null) {
        await createProject(payload)
      } else {
        await updateProject(id, payload)
      }
      await load()
    } finally {
      saving.value = false
    }
  }

  async function removeProject(id: number): Promise<void> {
    saving.value = true
    try {
      await deleteProject(id)
      await load()
    } finally {
      saving.value = false
    }
  }

  async function saveExperience(
    id: number | null,
    payload: ExperiencePayload,
  ): Promise<void> {
    saving.value = true
    try {
      if (id === null) {
        await createExperience(payload)
      } else {
        await updateExperience(id, payload)
      }
      await load()
    } finally {
      saving.value = false
    }
  }

  async function removeExperience(id: number): Promise<void> {
    saving.value = true
    try {
      await deleteExperience(id)
      await load()
    } finally {
      saving.value = false
    }
  }

  return {
    profile,
    completeness,
    loading,
    saving,
    load,
    saveProfile,
    saveProject,
    removeProject,
    saveExperience,
    removeExperience,
  }
}
