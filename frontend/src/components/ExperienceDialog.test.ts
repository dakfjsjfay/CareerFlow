import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { describe, expect, it } from 'vitest'

import type { ProfileExperience } from '@/api/profile'

import ExperienceDialog from './ExperienceDialog.vue'

const experience: ProfileExperience = {
  id: 31,
  company: '某某科技',
  position: '后端实习生',
  description: '参与接口开发',
  achievements: '完成 3 个接口',
  startDate: '2025-07-01',
  endDate: '2025-09-30',
}

const ElDialogStub = {
  props: ['modelValue', 'title'],
  template:
    '<div v-if="modelValue"><h3>{{ title }}</h3><slot /><slot name="footer" /></div>',
}

function mountDialog(props: { experience?: ProfileExperience | null; submitting?: boolean } = {}) {
  return mount(ExperienceDialog, {
    props: { modelValue: true, experience, submitting: false, ...props },
    global: {
      plugins: [ElementPlus],
      stubs: { ElDialog: ElDialogStub },
    },
  })
}

function fieldValue(wrapper: ReturnType<typeof mountDialog>, testId: string): string {
  const target = wrapper.get(`[data-testid="${testId}"]`)
  const field =
    target.element instanceof HTMLInputElement ||
    target.element instanceof HTMLTextAreaElement
      ? target.element
      : target.get('input, textarea').element
  return (field as HTMLInputElement).value
}

describe('ExperienceDialog', () => {
  it('prefills an existing experience for editing', () => {
    const wrapper = mountDialog()

    expect(fieldValue(wrapper, 'company')).toBe('某某科技')
    expect(fieldValue(wrapper, 'position')).toBe('后端实习生')
    expect(fieldValue(wrapper, 'experienceDescription')).toBe('参与接口开发')
    expect(fieldValue(wrapper, 'achievements')).toBe('完成 3 个接口')
    expect(wrapper.text()).toContain('编辑工作/实习经历')
  })

  it('shows a fresh dialog when adding an experience', () => {
    const wrapper = mountDialog({ experience: null })

    expect(fieldValue(wrapper, 'company')).toBe('')
    expect(wrapper.text()).toContain('添加工作/实习经历')
  })

  it('blocks saving when the company is blank', async () => {
    const wrapper = mountDialog({ experience: null })

    await wrapper.get('[data-testid="company"]').setValue('  ')
    await wrapper.get('[data-testid="save-experience"]').trigger('click')

    expect(wrapper.get('[data-testid="form-error"]').text()).toContain('公司/单位不能为空')
    expect(wrapper.emitted('save')).toBeUndefined()
  })

  it('emits a trimmed payload with dates for saving', async () => {
    const wrapper = mountDialog()

    await wrapper.get('[data-testid="company"]').setValue(' 某某科技 ')
    await wrapper.get('[data-testid="position"]').setValue(' 后端开发 ')
    await wrapper.get('[data-testid="save-experience"]').trigger('click')

    expect(wrapper.emitted('save')?.[0]?.[0]).toMatchObject({
      company: '某某科技',
      position: '后端开发',
      description: '参与接口开发',
      achievements: '完成 3 个接口',
      startDate: '2025-07-01',
      endDate: '2025-09-30',
    })
  })

  it('disables saving while submitting', () => {
    const wrapper = mountDialog({ submitting: true })
    const button = wrapper.get('[data-testid="save-experience"]').element as HTMLButtonElement

    expect(button.disabled).toBe(true)
  })
})
