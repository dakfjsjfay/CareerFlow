import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { describe, expect, it } from 'vitest'

import type { ProfileProject } from '@/api/profile'

import ProjectDialog from './ProjectDialog.vue'

const project: ProfileProject = {
  id: 21,
  projectName: '学生管理系统',
  role: '后端开发',
  description: '增删改查',
  techStack: ['Java', 'MySQL'],
  startDate: '2025-03-01',
  endDate: '2025-06-30',
}

const ElDialogStub = {
  props: ['modelValue', 'title'],
  template:
    '<div v-if="modelValue"><h3>{{ title }}</h3><slot /><slot name="footer" /></div>',
}

function mountDialog(props: { project?: ProfileProject | null; submitting?: boolean } = {}) {
  return mount(ProjectDialog, {
    props: { modelValue: true, project, submitting: false, ...props },
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

describe('ProjectDialog', () => {
  it('prefills an existing project for editing', () => {
    const wrapper = mountDialog()

    expect(fieldValue(wrapper, 'projectName')).toBe('学生管理系统')
    expect(fieldValue(wrapper, 'projectRole')).toBe('后端开发')
    expect(fieldValue(wrapper, 'projectDescription')).toBe('增删改查')
    expect(wrapper.text()).toContain('编辑项目经历')
  })

  it('shows a fresh dialog when adding a project', () => {
    const wrapper = mountDialog({ project: null })

    expect(fieldValue(wrapper, 'projectName')).toBe('')
    expect(wrapper.text()).toContain('添加项目经历')
  })

  it('blocks saving and explains why when the project name is blank', async () => {
    const wrapper = mountDialog({ project: null })

    await wrapper.get('[data-testid="projectName"]').setValue('   ')
    await wrapper.get('[data-testid="save-project"]').trigger('click')

    expect(wrapper.get('[data-testid="form-error"]').text()).toContain('项目名称不能为空')
    expect(wrapper.emitted('save')).toBeUndefined()
  })

  it('emits a trimmed payload with dates for saving', async () => {
    const wrapper = mountDialog()

    await wrapper.get('[data-testid="projectName"]').setValue('  校园二手交易平台 ')
    await wrapper.get('[data-testid="projectRole"]').setValue(' 全栈 ')
    await wrapper.get('[data-testid="save-project"]').trigger('click')

    expect(wrapper.emitted('save')?.[0]?.[0]).toMatchObject({
      projectName: '校园二手交易平台',
      role: '全栈',
      description: '增删改查',
      techStack: ['Java', 'MySQL'],
      startDate: '2025-03-01',
      endDate: '2025-06-30',
    })
  })

  it('disables saving while submitting', () => {
    const wrapper = mountDialog({ submitting: true })
    const button = wrapper.get('[data-testid="save-project"]').element as HTMLButtonElement

    expect(button.disabled).toBe(true)
  })
})
