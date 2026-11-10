import { enableAutoUnmount, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const { init } = vi.hoisted(() => ({
  init: vi.fn(),
}))

vi.mock('echarts/core', () => ({ init, use: vi.fn() }))
vi.mock('echarts/charts', () => ({ RadarChart: {} }))
vi.mock('echarts/components', () => ({
  AriaComponent: {},
  RadarComponent: {},
  TooltipComponent: {},
}))
vi.mock('echarts/renderers', () => ({ CanvasRenderer: {} }))

import type { DimensionScore } from '@/api/profile'

import CompletenessRadar from './CompletenessRadar.vue'

const dimensions: DimensionScore[] = [
  { name: '基础信息', score: 100 },
  { name: '教育经历', score: 75 },
  { name: '技能信息', score: 50 },
  { name: '项目经历', score: 100 },
  { name: '工作/实习经历', score: 0 },
  { name: '求职意向', score: 80 },
]

const chart = {
  setOption: vi.fn(),
  resize: vi.fn(),
  dispose: vi.fn(),
}

enableAutoUnmount(afterEach)

function mountRadar() {
  return mount(CompletenessRadar, {
    props: { overall: 67, dimensions },
  })
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function lastOption(): any {
  return chart.setOption.mock.calls.at(-1)?.[0]
}

describe('CompletenessRadar', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    init.mockReturnValue(chart)
  })

  it('shows the overall score as a headline number', () => {
    const wrapper = mountRadar()

    expect(wrapper.text()).toContain('67')
    expect(wrapper.text()).toContain('档案完整度')
  })

  it('initializes ECharts on the chart element', () => {
    const wrapper = mountRadar()

    expect(init).toHaveBeenCalledTimes(1)
    expect(init.mock.calls[0][0]).toBe(wrapper.find('[data-testid="radar-chart"]').element)
  })

  it('waits for dimensions before initializing the radar', async () => {
    const wrapper = mount(CompletenessRadar, {
      props: { overall: 0, dimensions: [] },
    })

    expect(init).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('保存档案后生成完整度')

    await wrapper.setProps({ overall: 67, dimensions })

    expect(init).toHaveBeenCalledTimes(1)
    expect(lastOption().series[0].data[0].value).toEqual([100, 75, 50, 100, 0, 80])
    expect(wrapper.text()).not.toContain('保存档案后生成完整度')
  })

  it('sets a six-dimension radar option with the backend scores', () => {
    mountRadar()

    expect(lastOption().radar.indicator).toEqual(
      dimensions.map((item) => ({ name: item.name, max: 100 })),
    )
    expect(lastOption().series[0].data[0].value).toEqual([100, 75, 50, 100, 0, 80])
  })

  it('updates the chart when the dimension scores change', async () => {
    const wrapper = mountRadar()
    const next = dimensions.map((item) =>
      item.name === '技能信息' ? { ...item, score: 100 } : item,
    )

    await wrapper.setProps({ overall: 80, dimensions: next })

    expect(lastOption().series[0].data[0].value).toEqual([100, 75, 100, 100, 0, 80])
    expect(wrapper.text()).toContain('80')
  })

  it('resizes with the window and disposes the chart on unmount', () => {
    const wrapper = mountRadar()

    window.dispatchEvent(new Event('resize'))
    expect(chart.resize).toHaveBeenCalledTimes(1)

    wrapper.unmount()
    expect(chart.dispose).toHaveBeenCalledTimes(1)

    window.dispatchEvent(new Event('resize'))
    expect(chart.resize).toHaveBeenCalledTimes(1)
  })
})
