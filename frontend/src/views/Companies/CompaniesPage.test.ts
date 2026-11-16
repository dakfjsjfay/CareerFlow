import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({
  listCompanies: vi.fn(),
}))

vi.mock('@/api/companies', () => ({
  listCompanies: api.listCompanies,
}))

import CompaniesPage from './index.vue'

const company = {
  id: 1,
  name: '阿里巴巴集团',
  shortName: '阿里',
  industry: '互联网',
  companyType: '民企',
  scale: '5000人以上',
  city: '杭州',
  website: 'https://www.alibabagroup.com',
  recruitmentUrl: 'https://talent.alibaba.com',
  campusRecruitUrl: 'https://talent.alibaba.com/campus',
  source: 'SEED',
  verificationStatus: 'PENDING',
  lastVerifiedAt: null,
}

const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/companies', name: 'Companies', component: { template: '<div />' } },
    { path: '/companies/:id', name: 'CompanyDetail', component: { template: '<div />' } },
  ],
})

function mountPage() {
  return mount(CompaniesPage, {
    global: {
      plugins: [ElementPlus, router],
    },
  })
}

describe('CompaniesPage', () => {
  beforeEach(() => {
    api.listCompanies.mockReset()
  })

  it('renders the company list from the api', async () => {
    api.listCompanies.mockResolvedValue({
      code: 200,
      message: 'success',
      data: { items: [company], total: 1, page: 1, size: 12 },
    })

    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.text()).toContain('阿里')
    expect(wrapper.text()).toContain('阿里巴巴集团')
    expect(wrapper.text()).toContain('互联网')
    expect(wrapper.text()).toContain('校招入口')
    expect(wrapper.text()).toContain('待核实')
  })

  it('searches with keyword and resets filters', async () => {
    api.listCompanies.mockResolvedValue({
      code: 200,
      message: 'success',
      data: { items: [], total: 0, page: 1, size: 12 },
    })

    const wrapper = mountPage()
    await flushPromises()
    api.listCompanies.mockClear()

    await wrapper.find('[data-testid="companies-keyword"]').setValue('腾讯')
    await wrapper.find('[data-testid="companies-search"]').trigger('click')
    await flushPromises()

    expect(api.listCompanies).toHaveBeenLastCalledWith(
      expect.objectContaining({ keyword: '腾讯', page: 1, size: 12 }),
    )

    await wrapper.find('[data-testid="companies-reset"]').trigger('click')
    await flushPromises()

    expect(api.listCompanies).toHaveBeenLastCalledWith(
      expect.objectContaining({ keyword: undefined }),
    )
  })

  it('navigates to the company detail page', async () => {
    api.listCompanies.mockResolvedValue({
      code: 200,
      message: 'success',
      data: { items: [company], total: 1, page: 1, size: 12 },
    })

    const wrapper = mountPage()
    await flushPromises()

    await wrapper.find('[data-testid="company-detail"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/companies/1')
  })
})
