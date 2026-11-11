import { reactive, ref } from 'vue'

import { listJobs, type JobQuery, type JobSummary } from '@/api/jobs'

/** 岗位列表状态：分页 + keyword/city/salary 筛选。 */
export function useJobs() {
  const jobs = ref<JobSummary[]>([])
  const total = ref(0)
  const page = ref(1)
  const size = ref(10)
  const loading = ref(false)
  const filters = reactive({
    keyword: '',
    city: '',
    salary: '',
  })

  function buildQuery(): JobQuery {
    return {
      page: page.value,
      size: size.value,
      keyword: filters.keyword.trim() || undefined,
      city: filters.city.trim() || undefined,
      salary: filters.salary.trim() || undefined,
    }
  }

  async function load(): Promise<void> {
    loading.value = true
    try {
      const response = await listJobs(buildQuery())
      jobs.value = response.data.records
      total.value = response.data.total
    } finally {
      loading.value = false
    }
  }

  async function search(): Promise<void> {
    page.value = 1
    await load()
  }

  async function reset(): Promise<void> {
    filters.keyword = ''
    filters.city = ''
    filters.salary = ''
    page.value = 1
    await load()
  }

  async function changePage(nextPage: number): Promise<void> {
    page.value = nextPage
    await load()
  }

  async function changeSize(nextSize: number): Promise<void> {
    size.value = nextSize
    page.value = 1
    await load()
  }

  return {
    jobs,
    total,
    page,
    size,
    loading,
    filters,
    load,
    search,
    reset,
    changePage,
    changeSize,
  }
}
