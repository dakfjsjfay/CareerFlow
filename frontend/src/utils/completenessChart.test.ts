import { describe, expect, it } from 'vitest'

import type { DimensionScore } from '@/api/profile'

import { buildCompletenessRadarOption } from './completenessChart'

const dimensions: DimensionScore[] = [
  { name: '基础信息', score: 100 },
  { name: '教育经历', score: 75 },
  { name: '技能信息', score: 50 },
  { name: '项目经历', score: 100 },
  { name: '工作/实习经历', score: 0 },
  { name: '求职意向', score: 80 },
]

// ECharts option 是嵌套很深的联合类型，测试里只取用到的字段做断言
// eslint-disable-next-line @typescript-eslint/no-explicit-any
function optionFor(scores: DimensionScore[]): any {
  return buildCompletenessRadarOption(scores)
}

describe('buildCompletenessRadarOption', () => {
  it('maps every dimension to a radar indicator capped at 100', () => {
    const option = optionFor(dimensions)

    expect(option.radar.indicator).toEqual([
      { name: '基础信息', max: 100 },
      { name: '教育经历', max: 100 },
      { name: '技能信息', max: 100 },
      { name: '项目经历', max: 100 },
      { name: '工作/实习经历', max: 100 },
      { name: '求职意向', max: 100 },
    ])
  })

  it('draws one radar series with scores in dimension order', () => {
    const option = optionFor(dimensions)

    expect(option.series).toHaveLength(1)
    expect(option.series[0].type).toBe('radar')
    expect(option.series[0].data[0].value).toEqual([100, 75, 50, 100, 0, 80])
  })

  it('keeps a single series without a legend', () => {
    const option = optionFor(dimensions)

    expect(option.series[0].name).toBe('完整度')
    expect(option.legend).toBeUndefined()
  })

  it('uses the project primary blue with a thin 2px line', () => {
    const option = optionFor(dimensions)

    expect(option.series[0].lineStyle.color).toBe('#2563eb')
    expect(option.series[0].lineStyle.width).toBe(2)
    expect(option.series[0].symbolSize).toBeGreaterThanOrEqual(8)
  })

  it('shows a per-dimension tooltip on hover', () => {
    const option = optionFor(dimensions)

    expect(option.tooltip.trigger).toBe('item')
  })

  it('wraps long axis names so they are not clipped', () => {
    const option = optionFor(dimensions)

    expect(option.radar.axisName.formatter('工作/实习经历')).toBe('工作/实习\n经历')
    expect(option.radar.axisName.formatter('基础信息')).toBe('基础信息')
  })

  it('handles an empty dimension list without throwing', () => {
    const option = optionFor([])

    expect(option.radar.indicator).toEqual([])
    expect(option.series[0].data[0].value).toEqual([])
  })
})
