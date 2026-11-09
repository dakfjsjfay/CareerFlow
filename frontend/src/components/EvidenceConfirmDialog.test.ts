import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import type { EvidenceGroup } from '@/utils/evidenceDisplay'

import EvidenceConfirmDialog from './EvidenceConfirmDialog.vue'

const groups: EvidenceGroup[] = [
  {
    key: 'basic',
    title: '基础信息',
    items: [
      { id: 1, label: '姓名', value: '张三', sourceContent: '我叫张三' },
      {
        id: 2,
        label: '电话',
        value: '13800000000',
        sourceContent: '电话 13800000000',
      },
    ],
  },
  {
    key: 'skills',
    title: '技能',
    items: [{ id: 3, label: '技能', value: 'Java', sourceContent: '我学过 Java' }],
  },
]

const stubs = {
  ElDialog: {
    props: ['modelValue'],
    template: '<div v-if="modelValue"><slot /><slot name="footer" /></div>',
  },
  ElButton: {
    inheritAttrs: false,
    props: ['disabled'],
    emits: ['click'],
    template:
      '<button :disabled="disabled" :data-testid="$attrs[\'data-testid\']" @click="$emit(\'click\')"><slot /></button>',
  },
}

function mountDialog(visible = true) {
  return mount(EvidenceConfirmDialog, {
    props: { modelValue: visible, groups },
    global: { stubs },
  })
}

function buttonByText(wrapper: ReturnType<typeof mountDialog>, text: string) {
  const button = wrapper.findAll('button').find((candidate) => candidate.text().includes(text))
  if (!button) {
    throw new Error(`button not found: ${text}`)
  }
  return button
}

describe('EvidenceConfirmDialog', () => {
  it('renders grouped evidence with its source text', () => {
    const wrapper = mountDialog()

    expect(wrapper.text()).toContain('基础信息')
    expect(wrapper.text()).toContain('姓名')
    expect(wrapper.text()).toContain('张三')
    expect(wrapper.text()).toContain('来源：我叫张三')
    expect(wrapper.text()).toContain('技能')
    expect(wrapper.text()).toContain('Java')
  })

  it('confirms every selected evidence id after select all', async () => {
    const wrapper = mountDialog()

    await wrapper.find('[data-testid="select-all"]').setValue(true)
    await buttonByText(wrapper, '确认选中').trigger('click')

    expect(wrapper.emitted('confirm')?.[0]?.[0]).toEqual([1, 2, 3])
  })

  it('rejects only the checked evidence ids', async () => {
    const wrapper = mountDialog()

    await wrapper.find('[data-testid="evidence-2"]').setValue(true)
    await buttonByText(wrapper, '忽略选中').trigger('click')

    expect(wrapper.emitted('reject')?.[0]?.[0]).toEqual([2])
    expect(wrapper.emitted('confirm')).toBeUndefined()
  })

  it('disables confirm and reject while nothing is selected', () => {
    const wrapper = mountDialog()

    expect((buttonByText(wrapper, '确认选中').element as HTMLButtonElement).disabled).toBe(true)
    expect((buttonByText(wrapper, '忽略选中').element as HTMLButtonElement).disabled).toBe(true)
  })

  it('clears the previous selection when reopened', async () => {
    const wrapper = mountDialog()

    await wrapper.find('[data-testid="select-all"]').setValue(true)
    await wrapper.setProps({ modelValue: false })
    await wrapper.setProps({ modelValue: true })

    expect(
      (wrapper.find('[data-testid="evidence-1"]').element as HTMLInputElement).checked,
    ).toBe(false)
    expect((buttonByText(wrapper, '确认选中').element as HTMLButtonElement).disabled).toBe(true)
  })
})
