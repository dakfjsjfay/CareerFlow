import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { describe, expect, it } from 'vitest'

import type { ProfileData } from '@/api/profile'

import ProfileInfoForm from './ProfileInfoForm.vue'

const profile: ProfileData = {
  id: 1,
  realName: '张三',
  phone: '13800000000',
  school: '某某大学',
  major: '计算机科学与技术',
  degree: '本科',
  graduationYear: 2027,
  skills: ['Java', 'MySQL'],
  targetPosition: 'Java 后端',
  targetCity: '杭州',
  expectedSalary: '8-12K',
  projects: [],
  experiences: [],
}

function mountForm(props: { profile?: ProfileData | null; saving?: boolean } = {}) {
  return mount(ProfileInfoForm, {
    props: { profile, saving: false, ...props },
    global: { plugins: [ElementPlus] },
  })
}

function inputValue(wrapper: ReturnType<typeof mountForm>, testId: string): string {
  const target = wrapper.get(`[data-testid="${testId}"]`)
  const input =
    target.element instanceof HTMLInputElement ? target.element : target.get('input').element
  return (input as HTMLInputElement).value
}

describe('ProfileInfoForm', () => {
  it('prefills fields from the confirmed profile', () => {
    const wrapper = mountForm()

    expect(inputValue(wrapper, 'realName')).toBe('张三')
    expect(inputValue(wrapper, 'phone')).toBe('13800000000')
    expect(inputValue(wrapper, 'school')).toBe('某某大学')
    expect(inputValue(wrapper, 'major')).toBe('计算机科学与技术')
    expect(inputValue(wrapper, 'graduationYear')).toBe('2027')
    expect(inputValue(wrapper, 'targetPosition')).toBe('Java 后端')
  })

  it('renders an empty form when no profile exists', () => {
    const wrapper = mountForm({ profile: null })

    expect(inputValue(wrapper, 'realName')).toBe('')
    expect(inputValue(wrapper, 'school')).toBe('')
  })

  it('emits trimmed values on save while preserving untouched fields', async () => {
    const wrapper = mountForm()

    await wrapper.get('[data-testid="realName"]').setValue('  李四  ')
    await wrapper.get('[data-testid="phone"]').setValue(' 13900000000 ')
    await wrapper.get('[data-testid="save-profile"]').trigger('click')

    expect(wrapper.emitted('save')?.[0]?.[0]).toMatchObject({
      realName: '李四',
      phone: '13900000000',
      school: '某某大学',
      major: '计算机科学与技术',
      graduationYear: 2027,
      skills: ['Java', 'MySQL'],
      targetPosition: 'Java 后端',
      targetCity: '杭州',
      expectedSalary: '8-12K',
    })
  })

  it('refills the form when the profile changes after evidence confirmation', async () => {
    const wrapper = mountForm()

    await wrapper.setProps({ profile: { ...profile, realName: '王五' } })

    expect(inputValue(wrapper, 'realName')).toBe('王五')
  })

  it('disables the save button while saving', () => {
    const wrapper = mountForm({ saving: true })
    const button = wrapper.get('[data-testid="save-profile"]').element as HTMLButtonElement

    expect(button.disabled).toBe(true)
  })
})
